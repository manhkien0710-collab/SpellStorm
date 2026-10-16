#pragma once

#include <SFML/System/Vector2.hpp>
#include <vector>

namespace ss {

class TileMap;

// Robust grid-based A* pathfinding and line-of-sight navigation system.
// Features:
// - 8-directional A* search over the procedural tile map
// - Corner-cutting prevention on diagonal steps
// - Enemy radius-aware traversability checks (small, medium, boss)
// - Line-of-sight raycasting for direct pursuit and path smoothing
// - Graceful fallback to closest reachable vantage point for unreachable targets
class PathfindingSystem {
public:
    explicit PathfindingSystem(const TileMap& tileMap);

    // Fast Line-of-Sight test between two world positions for a given entity collision radius.
    // Samples points along the segment to verify unobstructed clearance through water and solid obstacles.
    bool hasLineOfSight(sf::Vector2f startPos, sf::Vector2f endPos, float entityRadius) const;

    // 8-directional A* grid search on the TileMap.
    // Returns a smoothed list of world-space waypoints leading from startPos toward targetPos.
    std::vector<sf::Vector2f> findPath(sf::Vector2f startPos, sf::Vector2f targetPos,
                                       float entityRadius, int maxExpansions = 2500) const;

    // Checks whether a specific tile cell center can accommodate an entity with given radius
    bool isCellWalkable(int tx, int ty, float entityRadius) const;

    const TileMap& tileMap() const { return m_tileMap; }

private:
    const TileMap& m_tileMap;
};

} // namespace ss
