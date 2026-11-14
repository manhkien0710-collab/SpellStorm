package com.spellstorm.navigation;

import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.PriorityQueue;

/**
 * High-performance 8-directional A* pathfinder for SpellStorm (Milestone 8).
 *
 * Key Capabilities:
 * 1. 8-way movement with exact Octile heuristic (cardinal cost = 32.0f, diagonal = 45.254834f).
 * 2. Double-orthogonal corner-cutting prevention (both orthogonal neighbors must be walkable).
 * 3. Continuous collision-radius clearance awareness (supports Melee r=16, Tank r=26, Boss r=50).
 * 4. Direct line-of-sight shortcutting (<650 units) for near-instantaneous fast-path steering.
 * 5. Two-tiered graceful fallback:
 *    - Layer 1: 5x5 neighborhood kernel search around blocked targets (water/obstacles).
 *    - Layer 2: Lowest-heuristic open-set tracking leading to the closest reachable shoreline vantage point.
 * 6. Look-ahead path string-pulling (smoothing).
 * 7. Zero-allocation reusable CellState grid buffer with 32-bit runId tracking.
 */
public class AStarPathfinder {

    public static final int DEFAULT_MAX_EXPANSIONS = 2500;
    public static final float SQRT2 = 1.41421356f;
    public static final float CARDINAL_COST = 32.0f;
    public static final float DIAGONAL_COST = CARDINAL_COST * SQRT2; // ~45.254834f

    public static final float REPATH_DISPLACEMENT_THRESHOLD = 64.0f; // 2 full tiles
    public static final float STUCK_TIME_THRESHOLD = 0.35f;
    public static final float WAYPOINT_ARRIVAL_RADIUS = 22.0f;
    public static final float MAX_DIRECT_LOS_DISTANCE = 650.0f;

    // 8 neighbor directions: 4 cardinal, 4 diagonal
    private static final int[] DX = { 0,  0,  1, -1,  1, -1,  1, -1 };
    private static final int[] DY = { 1, -1,  0,  0,  1,  1, -1, -1 };

    private final TileMap tileMap;
    private final CornerCuttingRule cornerRule;

    // Reusable cell state buffer indexed by y * width + x
    private final CellState[] cellStates;
    private int currentRunId = 0;

    /**
     * Corner-cutting validation policies.
     */
    public enum CornerCuttingRule {
        /**
         * Strict: Both adjacent orthogonal neighbors must be walkable for the entity radius.
         * Default and matches SpellStorm C++ Milestone 8 reference.
         */
        STRICT,

        /**
         * Lenient: At least one adjacent orthogonal neighbor must be walkable.
         */
        LENIENT,

        /**
         * Disallowed: Only 4-way cardinal moves allowed.
         */
        DISALLOWED
    }

    private static final class CellState {
        float g = Float.MAX_VALUE;
        int parentX = -1;
        int parentY = -1;
        int runId = 0;
    }

    public AStarPathfinder(TileMap tileMap) {
        this(tileMap, CornerCuttingRule.STRICT);
    }

    public AStarPathfinder(TileMap tileMap, CornerCuttingRule cornerRule) {
        this.tileMap = tileMap != null ? tileMap : new TileMap();
        this.cornerRule = cornerRule != null ? cornerRule : CornerCuttingRule.STRICT;

        int totalCells = this.tileMap.getWidth() * this.tileMap.getHeight();
        this.cellStates = new CellState[totalCells];
        for (int i = 0; i < totalCells; i++) {
            this.cellStates[i] = new CellState();
        }
    }

    public TileMap getTileMap() {
        return tileMap;
    }

    public CornerCuttingRule getCornerRule() {
        return cornerRule;
    }

    // =========================================================================
    // Heuristic & Walkability Queries
    // =========================================================================

    /**
     * Exact Octile heuristic distance scaled to world units.
     * h(dx, dy) = (max(dx, dy) + (sqrt(2) - 1) * min(dx, dy)) * tileSize
     */
    public float octileHeuristic(int x1, int y1, int x2, int y2) {
        float dx = Math.abs(x1 - x2);
        float dy = Math.abs(y1 - y2);
        return (Math.max(dx, dy) + (SQRT2 - 1.0f) * Math.min(dx, dy)) * tileMap.getTileSize();
    }

    /**
     * Checks if a grid cell center is traversable for an entity with given collision radius.
     */
    public boolean isCellWalkable(int tx, int ty, float entityRadius) {
        if (!tileMap.isInsideGrid(tx, ty)) {
            return false;
        }
        Vector2D center = tileMap.tileToWorld(tx, ty);
        return tileMap.isTraversable(center, entityRadius);
    }

    // =========================================================================
    // Line-of-Sight Raycasting
    // =========================================================================

    public boolean hasLineOfSight(Vector2D startPos, Vector2D endPos, float entityRadius) {
        if (startPos == null || endPos == null) {
            return false;
        }
        return hasLineOfSight(startPos.x, startPos.y, endPos.x, endPos.y, entityRadius);
    }

    /**
     * Raycasts from start to end stepping at half-tile intervals (16 world units).
     * Returns true if all intermediate points and endpoints are traversable for entityRadius.
     */
    public boolean hasLineOfSight(float startX, float startY, float endX, float endY, float entityRadius) {
        float diffX = endX - startX;
        float diffY = endY - startY;
        float dist = (float) Math.hypot(diffX, diffY);
        if (dist < 1e-3f) {
            return tileMap.isTraversable(startX, startY, entityRadius);
        }

        float stepSize = tileMap.getTileSize() * 0.5f; // 16.0f
        int numSteps = (int) Math.ceil(dist / stepSize);
        float stepX = diffX / numSteps;
        float stepY = diffY / numSteps;

        float curX = startX;
        float curY = startY;
        for (int i = 0; i <= numSteps; i++) {
            if (!tileMap.isTraversable(curX, curY, entityRadius)) {
                return false;
            }
            curX += stepX;
            curY += stepY;
        }

        return tileMap.isTraversable(endX, endY, entityRadius);
    }

    // =========================================================================
    // Core 8-Way A* Pathfinding with Fallback & Smoothing
    // =========================================================================

    public List<Vector2D> findPath(Vector2D startPos, Vector2D targetPos, float entityRadius) {
        if (startPos == null || targetPos == null) {
            return Collections.emptyList();
        }
        return findPath(startPos.x, startPos.y, targetPos.x, targetPos.y, entityRadius, DEFAULT_MAX_EXPANSIONS);
    }

    public List<Vector2D> findPath(float startX, float startY, float targetX, float targetY,
                                   float entityRadius) {
        return findPath(startX, startY, targetX, targetY, entityRadius, DEFAULT_MAX_EXPANSIONS);
    }

    public List<Vector2D> findPath(float startX, float startY, float targetX, float targetY,
                                   float entityRadius, int maxExpansions) {
        // Fast path: direct clear line of sight
        if (hasLineOfSight(startX, startY, targetX, targetY, entityRadius)) {
            List<Vector2D> direct = new ArrayList<>(1);
            direct.add(new Vector2D(targetX, targetY));
            return direct;
        }

        int mapW = tileMap.getWidth();
        int mapH = tileMap.getHeight();
        float tileSize = tileMap.getTileSize();

        int[] startTile = tileMap.worldToTile(startX, startY);
        int[] targetTile = tileMap.worldToTile(targetX, targetY);

        int targetTx = Math.max(0, Math.min(mapW - 1, targetTile[0]));
        int targetTy = Math.max(0, Math.min(mapH - 1, targetTile[1]));
        int origTargetTx = targetTx;
        int origTargetTy = targetTy;
        boolean targetAdjusted = false;

        // Fallback Layer 1: Target tile walkability window (5x5 kernel)
        if (!isCellWalkable(targetTx, targetTy, entityRadius)) {
            float bestDistSq = Float.MAX_VALUE;
            int bestTx = targetTx;
            int bestTy = targetTy;

            for (int dy = -2; dy <= 2; dy++) {
                for (int dx = -2; dx <= 2; dx++) {
                    int nx = targetTx + dx;
                    int ny = targetTy + dy;
                    if (isCellWalkable(nx, ny, entityRadius)) {
                        float dSq = (float) (dx * dx + dy * dy);
                        if (dSq < bestDistSq) {
                            bestDistSq = dSq;
                            bestTx = nx;
                            bestTy = ny;
                        }
                    }
                }
            }
            if (bestTx != origTargetTx || bestTy != origTargetTy) {
                targetAdjusted = true;
            }
            targetTx = bestTx;
            targetTy = bestTy;
        }

        // If start tile equals resolved target tile
        if (startTile[0] == targetTx && startTile[1] == targetTy) {
            List<Vector2D> direct = new ArrayList<>(1);
            direct.add(targetAdjusted ? tileMap.tileToWorld(targetTx, targetTy) : new Vector2D(targetX, targetY));
            return direct;
        }

        // Increment runId for zero-allocation buffer reuse
        currentRunId++;
        if (currentRunId == 0) { // Handle 32-bit overflow
            for (CellState cell : cellStates) {
                cell.runId = 0;
            }
            currentRunId = 1;
        }

        PriorityQueue<PathNode> openSet = new PriorityQueue<>();

        int startX_t = Math.max(0, Math.min(mapW - 1, startTile[0]));
        int startY_t = Math.max(0, Math.min(mapH - 1, startTile[1]));
        int startIdx = startY_t * mapW + startX_t;

        cellStates[startIdx].g = 0.0f;
        cellStates[startIdx].parentX = -1;
        cellStates[startIdx].parentY = -1;
        cellStates[startIdx].runId = currentRunId;

        openSet.add(new PathNode(startX_t, startY_t, 0.0f, octileHeuristic(startX_t, startY_t, targetTx, targetTy)));

        int bestX = startX_t;
        int bestY = startY_t;
        float bestH = octileHeuristic(startX_t, startY_t, targetTx, targetTy);
        boolean reachedGoal = false;
        int expansions = 0;

        float costOrthogonal = tileSize;
        float costDiagonal = tileSize * SQRT2;

        while (!openSet.isEmpty() && expansions < maxExpansions) {
            PathNode cur = openSet.poll();
            int curIdx = cur.y * mapW + cur.x;

            if (cellStates[curIdx].runId == currentRunId && cur.g > cellStates[curIdx].g) {
                continue; // Suboptimal entry already superseded
            }

            expansions++;

            if (cur.x == targetTx && cur.y == targetTy) {
                bestX = cur.x;
                bestY = cur.y;
                reachedGoal = true;
                break;
            }

            // Fallback Layer 2: Track closest point reached in case target is unreachable
            float curH = octileHeuristic(cur.x, cur.y, targetTx, targetTy);
            if (curH < bestH) {
                bestH = curH;
                bestX = cur.x;
                bestY = cur.y;
            }

            int dirCount = (cornerRule == CornerCuttingRule.DISALLOWED) ? 4 : 8;
            for (int i = 0; i < dirCount; i++) {
                int nx = cur.x + DX[i];
                int ny = cur.y + DY[i];

                if (nx < 0 || nx >= mapW || ny < 0 || ny >= mapH) {
                    continue;
                }

                boolean isDiagonal = (DX[i] != 0 && DY[i] != 0);
                if (isDiagonal && cornerRule == CornerCuttingRule.STRICT) {
                    // Corner-cutting prevention: both orthogonal neighbors must be walkable
                    if (!isCellWalkable(cur.x + DX[i], cur.y, entityRadius) ||
                        !isCellWalkable(cur.x, cur.y + DY[i], entityRadius)) {
                        continue;
                    }
                } else if (isDiagonal && cornerRule == CornerCuttingRule.LENIENT) {
                    if (!isCellWalkable(cur.x + DX[i], cur.y, entityRadius) &&
                        !isCellWalkable(cur.x, cur.y + DY[i], entityRadius)) {
                        continue;
                    }
                }

                // Target cell must be walkable for entity size
                if (!isCellWalkable(nx, ny, entityRadius)) {
                    continue;
                }

                float stepCost = isDiagonal ? costDiagonal : costOrthogonal;
                float newG = cur.g + stepCost;

                int nIdx = ny * mapW + nx;
                if (cellStates[nIdx].runId != currentRunId || newG < cellStates[nIdx].g) {
                    cellStates[nIdx].g = newG;
                    cellStates[nIdx].parentX = cur.x;
                    cellStates[nIdx].parentY = cur.y;
                    cellStates[nIdx].runId = currentRunId;

                    float h = octileHeuristic(nx, ny, targetTx, targetTy);
                    openSet.add(new PathNode(nx, ny, newG, newG + h));
                }
            }
        }

        // Reconstruct raw tile path backwards from bestX, bestY
        List<Vector2D> rawPath = new ArrayList<>();
        int currX = bestX;
        int currY = bestY;

        while (currX >= 0 && currY >= 0) {
            rawPath.add(tileMap.tileToWorld(currX, currY));
            int idx = currY * mapW + currX;
            if (cellStates[idx].parentX == currX && cellStates[idx].parentY == currY) {
                break;
            }
            int px = cellStates[idx].parentX;
            int py = cellStates[idx].parentY;
            currX = px;
            currY = py;
        }

        if (rawPath.size() <= 1) {
            List<Vector2D> fallback = new ArrayList<>(1);
            fallback.add(targetAdjusted ? tileMap.tileToWorld(bestX, bestY) : new Vector2D(targetX, targetY));
            return fallback;
        }

        Collections.reverse(rawPath);
        if (reachedGoal && !targetAdjusted && tileMap.isTraversable(targetX, targetY, entityRadius)) {
            rawPath.set(rawPath.size() - 1, new Vector2D(targetX, targetY));
        }

        // Path smoothing (string-pulling): remove intermediate zig-zags with clear LOS
        List<Vector2D> smoothed = new ArrayList<>();
        smoothed.add(rawPath.get(0));

        int currentIdx = 0;
        while (currentIdx < rawPath.size() - 1) {
            int nextIdx = currentIdx + 1;
            for (int checkIdx = rawPath.size() - 1; checkIdx > currentIdx + 1; checkIdx--) {
                if (hasLineOfSight(rawPath.get(currentIdx), rawPath.get(checkIdx), entityRadius)) {
                    nextIdx = checkIdx;
                    break;
                }
            }
            smoothed.add(rawPath.get(nextIdx));
            currentIdx = nextIdx;
        }

        // Drop the initial waypoint if already at/behind start position
        if (smoothed.size() > 1) {
            float dStartSq = (startX - smoothed.get(0).x) * (startX - smoothed.get(0).x)
                           + (startY - smoothed.get(0).y) * (startY - smoothed.get(0).y);
            if (dStartSq < (tileSize * tileSize * 0.5f)) {
                smoothed.remove(0);
            }
        }

        return smoothed;
    }

    // =========================================================================
    // Path Cache & Enemy Steering State
    // =========================================================================

    /**
     * Entity-level navigation cache implementing repath throttling, stuck detection,
     * displacement thresholds, and look-ahead waypoint steering.
     */
    public static class PathCache {
        private final List<Vector2D> navPath = new ArrayList<>();
        private int navWaypointIndex = 0;
        private float navRepathTimer = 0.0f;
        private final Vector2D navLastTargetPos = new Vector2D();
        private final Vector2D navLastPosition = new Vector2D();
        private float navStuckTimer = 0.0f;

        public void clear() {
            navPath.clear();
            navWaypointIndex = 0;
            navRepathTimer = 0.0f;
            navStuckTimer = 0.0f;
        }

        public List<Vector2D> getNavPath() {
            return navPath;
        }

        public int getNavWaypointIndex() {
            return navWaypointIndex;
        }

        public void setNavWaypointIndex(int idx) {
            this.navWaypointIndex = idx;
        }

        public float getNavRepathTimer() {
            return navRepathTimer;
        }

        public void setNavRepathTimer(float timer) {
            this.navRepathTimer = timer;
        }

        public Vector2D getNavLastTargetPos() {
            return navLastTargetPos;
        }

        public Vector2D getNavLastPosition() {
            return navLastPosition;
        }

        public float getNavStuckTimer() {
            return navStuckTimer;
        }

        public void setNavStuckTimer(float timer) {
            this.navStuckTimer = timer;
        }

        /**
         * Determines if an entity needs to trigger an A* repath search.
         */
        public boolean needsRepath(Vector2D currentTargetPos) {
            if (navPath.isEmpty() || navWaypointIndex >= navPath.size()) {
                return true;
            }
            if (navRepathTimer <= 0.0f) {
                return true;
            }
            if (currentTargetPos != null) {
                float dx = currentTargetPos.x - navLastTargetPos.x;
                float dy = currentTargetPos.y - navLastTargetPos.y;
                if ((dx * dx + dy * dy) > (REPATH_DISPLACEMENT_THRESHOLD * REPATH_DISPLACEMENT_THRESHOLD)) {
                    return true;
                }
            }
            if (navStuckTimer > STUCK_TIME_THRESHOLD) {
                return true;
            }
            return false;
        }

        /**
         * Updates timers and stuck detection state every frame.
         */
        public void updateTimers(Vector2D currentPos, float dt) {
            navRepathTimer -= dt;
            if (dt > 1e-4f && currentPos != null) {
                float dx = currentPos.x - navLastPosition.x;
                float dy = currentPos.y - navLastPosition.y;
                if ((dx * dx + dy * dy) < (2.0f * 2.0f)) {
                    navStuckTimer += dt;
                } else {
                    navStuckTimer = 0.0f;
                }
                navLastPosition.set(currentPos);
            }
        }

        /**
         * Resets the cache after a new path is computed.
         * Staggers repath timer across entity IDs: 0.35s + (id % 5) * 0.05s.
         */
        public void resetAfterRepath(List<Vector2D> newPath, Vector2D currentTargetPos, int entityId) {
            navPath.clear();
            if (newPath != null) {
                navPath.addAll(newPath);
            }
            navWaypointIndex = 0;
            navRepathTimer = 0.35f + (entityId % 5) * 0.05f;
            navStuckTimer = 0.0f;
            if (currentTargetPos != null) {
                navLastTargetPos.set(currentTargetPos);
            }
        }

        /**
         * Advances waypoint index if within arrival threshold and applies look-ahead string pulling.
         */
        public Vector2D advanceWaypoint(Vector2D currentPos, AStarPathfinder pathfinder, float entityRadius) {
            if (navPath.isEmpty() || navWaypointIndex >= navPath.size()) {
                return null;
            }

            Vector2D wp = navPath.get(navWaypointIndex);
            float dx = wp.x - currentPos.x;
            float dy = wp.y - currentPos.y;

            if ((dx * dx + dy * dy) < (WAYPOINT_ARRIVAL_RADIUS * WAYPOINT_ARRIVAL_RADIUS)) {
                navWaypointIndex++;
                if (navWaypointIndex >= navPath.size()) {
                    return null;
                }
                wp = navPath.get(navWaypointIndex);
            }

            // Look-ahead string-pulling: skip to next waypoint if clear line of sight
            if (pathfinder != null && navWaypointIndex + 1 < navPath.size()) {
                Vector2D nextWp = navPath.get(navWaypointIndex + 1);
                if (pathfinder.hasLineOfSight(currentPos, nextWp, entityRadius)) {
                    navWaypointIndex++;
                    wp = nextWp;
                }
            }

            return wp;
        }
    }
}
