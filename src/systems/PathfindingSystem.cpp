#include "systems/PathfindingSystem.hpp"
#include "environment/TileMap.hpp"
#include "core/MathUtils.hpp"

#include <algorithm>
#include <cmath>
#include <cstdint>
#include <queue>

namespace ss {

PathfindingSystem::PathfindingSystem(const TileMap& tileMap)
    : m_tileMap(tileMap)
{
}

bool PathfindingSystem::isCellWalkable(int tx, int ty, float entityRadius) const
{
    if (!m_tileMap.isInsideGrid(tx, ty)) {
        return false;
    }
    // Check traversability at tile center
    const sf::Vector2f center = m_tileMap.tileToWorld(tx, ty);
    return m_tileMap.isTraversable(center, entityRadius);
}

bool PathfindingSystem::hasLineOfSight(sf::Vector2f startPos, sf::Vector2f endPos, float entityRadius) const
{
    const sf::Vector2f diff = endPos - startPos;
    const float dist = math::length(diff);
    if (dist < 1e-3f) {
        return m_tileMap.isTraversable(startPos, entityRadius);
    }

    // Step size equal to half a tile (16 world units)
    constexpr float kStep = TileMap::kTileSize * 0.5f;
    const int numSteps = static_cast<int>(std::ceil(dist / kStep));
    const sf::Vector2f stepVec = diff / static_cast<float>(numSteps);

    sf::Vector2f cur = startPos;
    for (int i = 0; i <= numSteps; ++i) {
        if (!m_tileMap.isTraversable(cur, entityRadius)) {
            return false;
        }
        cur += stepVec;
    }

    return m_tileMap.isTraversable(endPos, entityRadius);
}

namespace {

struct AStarNode {
    int x = 0;
    int y = 0;
    float g = 0.f;
    float f = 0.f;

    bool operator>(const AStarNode& other) const {
        return f > other.f;
    }
};

struct CellState {
    float g = 1e9f;
    int parentX = -1;
    int parentY = -1;
    uint32_t runId = 0;
};

// 8 neighbor directions: 4 cardinal, 4 diagonal
constexpr int kDx[8] = { 0,  0,  1, -1,  1, -1,  1, -1};
constexpr int kDy[8] = { 1, -1,  0,  0,  1,  1, -1, -1};
constexpr float kCostOrthogonal = TileMap::kTileSize;
constexpr float kCostDiagonal   = TileMap::kTileSize * 1.41421356f;

inline float octileHeuristic(int x1, int y1, int x2, int y2)
{
    const float dx = static_cast<float>(std::abs(x1 - x2));
    const float dy = static_cast<float>(std::abs(y1 - y2));
    return (std::max(dx, dy) + 0.41421356f * std::min(dx, dy)) * TileMap::kTileSize;
}

} // namespace

std::vector<sf::Vector2f> PathfindingSystem::findPath(sf::Vector2f startPos, sf::Vector2f targetPos,
                                                     float entityRadius, int maxExpansions) const
{
    // Fast path: if start and target have clear direct line of sight, return single segment
    if (hasLineOfSight(startPos, targetPos, entityRadius)) {
        return { targetPos };
    }

    const int mapW = m_tileMap.width();
    const int mapH = m_tileMap.height();

    const sf::Vector2i startTile = m_tileMap.worldToTile(startPos);
    sf::Vector2i targetTile = m_tileMap.worldToTile(targetPos);

    targetTile.x = std::clamp(targetTile.x, 0, mapW - 1);
    targetTile.y = std::clamp(targetTile.y, 0, mapH - 1);

    // If target tile itself is not walkable (e.g. inside tree or water), find closest walkable adjacent tile
    if (!isCellWalkable(targetTile.x, targetTile.y, entityRadius)) {
        float bestDistSq = 1e9f;
        sf::Vector2i bestAdj = targetTile;
        for (int dy = -2; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                const int nx = targetTile.x + dx;
                const int ny = targetTile.y + dy;
                if (isCellWalkable(nx, ny, entityRadius)) {
                    const float dSq = static_cast<float>(dx * dx + dy * dy);
                    if (dSq < bestDistSq) {
                        bestDistSq = dSq;
                        bestAdj = {nx, ny};
                    }
                }
            }
        }
        targetTile = bestAdj;
    }

    if (startTile.x == targetTile.x && startTile.y == targetTile.y) {
        return { targetPos };
    }

    // Static reusable grid buffer to avoid allocations per search
    static thread_local std::vector<CellState> s_grid(TileMap::kWidth * TileMap::kHeight);
    static thread_local uint32_t s_currentRunId = 0;
    ++s_currentRunId;
    if (s_currentRunId == 0) {
        // Handle 32-bit overflow by clearing runIds
        for (auto& cell : s_grid) {
            cell.runId = 0;
        }
        s_currentRunId = 1;
    }

    std::priority_queue<AStarNode, std::vector<AStarNode>, std::greater<AStarNode>> openSet;

    const int startIdx = startTile.y * mapW + startTile.x;
    s_grid[startIdx].g = 0.f;
    s_grid[startIdx].parentX = -1;
    s_grid[startIdx].parentY = -1;
    s_grid[startIdx].runId = s_currentRunId;

    openSet.push({startTile.x, startTile.y, 0.f, octileHeuristic(startTile.x, startTile.y, targetTile.x, targetTile.y)});

    int bestX = startTile.x;
    int bestY = startTile.y;
    float bestH = octileHeuristic(startTile.x, startTile.y, targetTile.x, targetTile.y);
    bool reachedGoal = false;
    int expansions = 0;

    while (!openSet.empty() && expansions < maxExpansions) {
        const AStarNode cur = openSet.top();
        openSet.pop();

        const int curIdx = cur.y * mapW + cur.x;
        if (s_grid[curIdx].runId == s_currentRunId && cur.g > s_grid[curIdx].g) {
            continue; // Suboptimal entry already superseded
        }

        ++expansions;

        // Check if goal reached
        if (cur.x == targetTile.x && cur.y == targetTile.y) {
            bestX = cur.x;
            bestY = cur.y;
            reachedGoal = true;
            break;
        }

        // Track closest point reached in case target is unreachable
        const float curH = octileHeuristic(cur.x, cur.y, targetTile.x, targetTile.y);
        if (curH < bestH) {
            bestH = curH;
            bestX = cur.x;
            bestY = cur.y;
        }

        for (int i = 0; i < 8; ++i) {
            const int nx = cur.x + kDx[i];
            const int ny = cur.y + kDy[i];

            if (nx < 0 || nx >= mapW || ny < 0 || ny >= mapH) {
                continue;
            }

            // Diagonal corner-cutting prevention:
            // Both orthogonal neighbors must be walkable to step diagonally
            const bool isDiagonal = (kDx[i] != 0 && kDy[i] != 0);
            if (isDiagonal) {
                if (!isCellWalkable(cur.x + kDx[i], cur.y, entityRadius) ||
                    !isCellWalkable(cur.x, cur.y + kDy[i], entityRadius)) {
                    continue; // Prohibit cutting through corner of solid obstacle or water
                }
            }

            // Cell must be walkable for entity size
            if (!isCellWalkable(nx, ny, entityRadius)) {
                continue;
            }

            const float stepCost = isDiagonal ? kCostDiagonal : kCostOrthogonal;
            const float newG = cur.g + stepCost;

            const int nIdx = ny * mapW + nx;
            if (s_grid[nIdx].runId != s_currentRunId || newG < s_grid[nIdx].g) {
                s_grid[nIdx].g = newG;
                s_grid[nIdx].parentX = cur.x;
                s_grid[nIdx].parentY = cur.y;
                s_grid[nIdx].runId = s_currentRunId;

                const float h = octileHeuristic(nx, ny, targetTile.x, targetTile.y);
                openSet.push({nx, ny, newG, newG + h});
            }
        }
    }

    // Reconstruct raw tile path backwards from bestX, bestY
    std::vector<sf::Vector2f> rawPath;
    int currX = bestX;
    int currY = bestY;

    while (currX >= 0 && currY >= 0) {
        rawPath.push_back(m_tileMap.tileToWorld(currX, currY));
        const int idx = currY * mapW + currX;
        if (s_grid[idx].parentX == currX && s_grid[idx].parentY == currY) {
            break;
        }
        const int px = s_grid[idx].parentX;
        const int py = s_grid[idx].parentY;
        currX = px;
        currY = py;
    }

    if (rawPath.size() <= 1) {
        return { targetPos };
    }

    std::reverse(rawPath.begin(), rawPath.end());
    // Replace final tile center with actual target position if goal was reached
    if (reachedGoal) {
        rawPath.back() = targetPos;
    }

    // Path string-pulling smoothing: remove intermediate zig-zag waypoints with clear LOS
    std::vector<sf::Vector2f> smoothed;
    smoothed.push_back(rawPath.front());

    std::size_t currentIdx = 0;
    while (currentIdx < rawPath.size() - 1) {
        std::size_t nextIdx = currentIdx + 1;
        // Look ahead as far as possible
        for (std::size_t checkIdx = rawPath.size() - 1; checkIdx > currentIdx + 1; --checkIdx) {
            if (hasLineOfSight(rawPath[currentIdx], rawPath[checkIdx], entityRadius)) {
                nextIdx = checkIdx;
                break;
            }
        }
        smoothed.push_back(rawPath[nextIdx]);
        currentIdx = nextIdx;
    }

    // Drop the very first waypoint if it's the start tile and already behind or at current position
    if (smoothed.size() > 1 && math::distanceSq(startPos, smoothed[0]) < (TileMap::kTileSize * TileMap::kTileSize * 0.5f)) {
        smoothed.erase(smoothed.begin());
    }

    return smoothed;
}

} // namespace ss
