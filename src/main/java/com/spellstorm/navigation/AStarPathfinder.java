package com.spellstorm.navigation;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileMapAdapter;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGrid;

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
 * 4. Direct line-of-sight shortcutting (<650 units) for near-instantaneous fast-path steering,
 *    plus ranged projectile line-of-sight across water.
 * 5. Supports both bounded TileMaps and unbounded/negative InfiniteWorld coordinates via
 *    a sliding 128x128 search window with zero per-search cell buffer allocations.
 * 6. Two-tiered graceful fallback:
 *    - Layer 1: 5x5 neighborhood kernel search around blocked targets (water/obstacles).
 *    - Layer 2: Lowest-heuristic open-set tracking leading to the closest reachable shoreline vantage point.
 * 7. Look-ahead path string-pulling (smoothing).
 */
public class AStarPathfinder {

    public static final int DEFAULT_MAX_EXPANSIONS = 2500;
    public static final int GRID_W = TileMap.WIDTH;
    public static final int GRID_H = TileMap.HEIGHT;
    private static final int START_MARGIN_TILES = 32;

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
    private final WorldGrid worldGrid;
    private final float tileSize;
    private final CornerCuttingRule cornerRule;

    // Bounded window extents (used when worldGrid.isBounded() is true)
    private final int boundedMinTx;
    private final int boundedMinTy;
    private final int boundedMaxTx;
    private final int boundedMaxTy;
    private final int boundedWidth;
    private final int boundedHeight;

    // Reusable cell state buffer indexed by (ty - windowOriginY) * windowW + (tx - windowOriginX)
    private final CellState[] cellStates;
    private int currentRunId = 0;
    private int maxExpansions = DEFAULT_MAX_EXPANSIONS;
    private boolean lastSearchReachedGoal = false;

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
        int parentX = 0;
        int parentY = 0;
        boolean hasParent = false;
        int runId = 0;
    }

    public AStarPathfinder(TileMap tileMap) {
        this(tileMap, CornerCuttingRule.STRICT);
    }

    public AStarPathfinder(TileMap tileMap, CornerCuttingRule cornerRule) {
        TileMap resolved = tileMap != null ? tileMap : new TileMap();
        this.tileMap = resolved;
        this.worldGrid = new TileMapAdapter(resolved);
        this.tileSize = resolved.getTileSize();
        this.cornerRule = cornerRule != null ? cornerRule : CornerCuttingRule.STRICT;

        this.boundedMinTx = 0;
        this.boundedMinTy = 0;
        this.boundedWidth = Math.max(1, resolved.getWidth());
        this.boundedHeight = Math.max(1, resolved.getHeight());
        this.boundedMaxTx = this.boundedMinTx + this.boundedWidth - 1;
        this.boundedMaxTy = this.boundedMinTy + this.boundedHeight - 1;

        int totalCells = Math.max(GRID_W * GRID_H, this.boundedWidth * this.boundedHeight);
        this.cellStates = allocateCellStates(totalCells);
    }

    private AStarPathfinder(WorldGrid worldGrid, CornerCuttingRule cornerRule) {
        this.cornerRule = cornerRule != null ? cornerRule : CornerCuttingRule.STRICT;
        if (worldGrid == null) {
            TileMap fallback = new TileMap();
            this.tileMap = fallback;
            this.worldGrid = new TileMapAdapter(fallback);
            this.tileSize = fallback.getTileSize();
            this.boundedMinTx = 0;
            this.boundedMinTy = 0;
            this.boundedWidth = fallback.getWidth();
            this.boundedHeight = fallback.getHeight();
            this.boundedMaxTx = this.boundedWidth - 1;
            this.boundedMaxTy = this.boundedHeight - 1;
        } else {
            this.worldGrid = worldGrid;
            this.tileMap = (worldGrid instanceof TileMapAdapter)
                    ? ((TileMapAdapter) worldGrid).getTileMap()
                    : null;
            this.tileSize = (this.tileMap != null) ? this.tileMap.getTileSize() : TileMap.TILE_SIZE;

            if (worldGrid.isBounded()) {
                Rectangle bounds = worldGrid.getWorldBounds();
                if (this.tileMap != null) {
                    this.boundedMinTx = 0;
                    this.boundedMinTy = 0;
                    this.boundedWidth = Math.max(1, this.tileMap.getWidth());
                    this.boundedHeight = Math.max(1, this.tileMap.getHeight());
                } else if (bounds != null) {
                    this.boundedMinTx = (int) Math.floor(bounds.x / this.tileSize);
                    this.boundedMinTy = (int) Math.floor(bounds.y / this.tileSize);
                    this.boundedWidth = Math.max(1, (int) Math.ceil(bounds.width / this.tileSize));
                    this.boundedHeight = Math.max(1, (int) Math.ceil(bounds.height / this.tileSize));
                } else {
                    this.boundedMinTx = 0;
                    this.boundedMinTy = 0;
                    this.boundedWidth = GRID_W;
                    this.boundedHeight = GRID_H;
                }
                this.boundedMaxTx = this.boundedMinTx + this.boundedWidth - 1;
                this.boundedMaxTy = this.boundedMinTy + this.boundedHeight - 1;
            } else {
                this.boundedMinTx = 0;
                this.boundedMinTy = 0;
                this.boundedWidth = GRID_W;
                this.boundedHeight = GRID_H;
                this.boundedMaxTx = GRID_W - 1;
                this.boundedMaxTy = GRID_H - 1;
            }
        }

        int totalCells = Math.max(GRID_W * GRID_H, this.boundedWidth * this.boundedHeight);
        this.cellStates = allocateCellStates(totalCells);
    }

    public static AStarPathfinder forWorldGrid(WorldGrid worldGrid) {
        return new AStarPathfinder(worldGrid, CornerCuttingRule.STRICT);
    }

    public static AStarPathfinder forWorldGrid(WorldGrid worldGrid, CornerCuttingRule cornerRule) {
        return new AStarPathfinder(worldGrid, cornerRule);
    }

    private static CellState[] allocateCellStates(int totalCells) {
        CellState[] buffer = new CellState[totalCells];
        for (int i = 0; i < totalCells; i++) {
            buffer[i] = new CellState();
        }
        return buffer;
    }

    public TileMap getTileMap() {
        return tileMap;
    }

    public WorldGrid getWorldGrid() {
        return worldGrid;
    }

    public CornerCuttingRule getCornerRule() {
        return cornerRule;
    }

    public int getMaxExpansions() {
        return maxExpansions;
    }

    public void setMaxExpansions(int maxExpansions) {
        this.maxExpansions = Math.max(1, maxExpansions);
    }

    public boolean didLastSearchReachGoal() {
        return lastSearchReachedGoal;
    }

    // =========================================================================
    // Heuristic & Walkability Queries
    // =========================================================================

    /**
     * Exact Octile heuristic distance scaled to world units.
     * h(dx, dy) = (max(dx, dy) + (sqrt(2) - 1) * min(dx, dy)) * tileSize
     */
    public float octileHeuristic(int x1, int y1, int x2, int y2) {
        float dx = Math.abs((float) x1 - (float) x2);
        float dy = Math.abs((float) y1 - (float) y2);
        return (Math.max(dx, dy) + (SQRT2 - 1.0f) * Math.min(dx, dy)) * tileSize;
    }

    /**
     * Checks if a grid cell center is traversable for an entity with given collision radius.
     */
    public boolean isCellWalkable(int tx, int ty, float entityRadius) {
        if (worldGrid.isBounded()
                && (tx < boundedMinTx || tx > boundedMaxTx || ty < boundedMinTy || ty > boundedMaxTy)) {
            return false;
        }
        TileType tile = worldGrid.getTile(tx, ty);
        if (tile == null || !tile.isWalkable()) {
            return false;
        }
        return worldGrid.isTraversable(worldGrid.tileToWorldX(tx), worldGrid.tileToWorldY(ty), entityRadius);
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
        return traceTraversableRay(startX, startY, endX, endY, entityRadius, false);
    }

    /**
     * Raycasts from start to end stepping at half-tile intervals (16 world units) for projectiles.
     * Evaluates bounded map edges and solid obstacles while ignoring TileType.WATER.
     */
    public boolean hasRangedLineOfSight(Vector2D startPos, Vector2D endPos, float projectileRadius) {
        if (startPos == null || endPos == null) {
            return false;
        }
        return hasRangedLineOfSight(startPos.x, startPos.y, endPos.x, endPos.y, projectileRadius);
    }

    public boolean hasRangedLineOfSight(float startX, float startY, float endX, float endY, float projectileRadius) {
        return traceTraversableRay(startX, startY, endX, endY, Math.max(0.0f, projectileRadius), true);
    }

    private boolean traceTraversableRay(float startX, float startY, float endX, float endY,
                                        float radius, boolean projectileOnly) {
        float diffX = endX - startX;
        float diffY = endY - startY;
        float dist = (float) Math.hypot(diffX, diffY);
        if (dist < 1e-3f) {
            return isRayPointClear(startX, startY, radius, projectileOnly);
        }

        float stepSize = tileSize * 0.5f; // 16.0f
        int numSteps = (int) Math.ceil(dist / stepSize);
        float stepX = diffX / numSteps;
        float stepY = diffY / numSteps;

        float curX = startX;
        float curY = startY;
        for (int i = 0; i <= numSteps; i++) {
            if (!isRayPointClear(curX, curY, radius, projectileOnly)) {
                return false;
            }
            curX += stepX;
            curY += stepY;
        }

        return isRayPointClear(endX, endY, radius, projectileOnly);
    }

    private boolean isRayPointClear(float x, float y, float radius, boolean projectileOnly) {
        return projectileOnly
                ? worldGrid.isProjectileTraversable(x, y, radius)
                : worldGrid.isTraversable(x, y, radius);
    }

    // =========================================================================
    // Core 8-Way A* Pathfinding with Fallback & Smoothing
    // =========================================================================

    public List<Vector2D> findPath(Vector2D startPos, Vector2D targetPos, float entityRadius) {
        if (startPos == null || targetPos == null) {
            lastSearchReachedGoal = false;
            return Collections.emptyList();
        }
        return findPath(startPos.x, startPos.y, targetPos.x, targetPos.y, entityRadius, maxExpansions);
    }

    public List<Vector2D> findPath(float startX, float startY, float targetX, float targetY,
                                   float entityRadius) {
        return findPath(startX, startY, targetX, targetY, entityRadius, maxExpansions);
    }

    public List<Vector2D> findPath(float startX, float startY, float targetX, float targetY,
                                   float entityRadius, int maxExpansions) {
        lastSearchReachedGoal = false;

        float clearanceProbeRadius = Math.min(entityRadius + 2.0f, entityRadius * 1.15f);

        // Fast path: direct clear line of sight (with subtle clearance margin against razor-edge water corners)
        if (hasLineOfSight(startX, startY, targetX, targetY, clearanceProbeRadius)) {
            lastSearchReachedGoal = true;
            List<Vector2D> direct = new ArrayList<>(1);
            direct.add(new Vector2D(targetX, targetY));
            return direct;
        }

        int rawStartTx = worldGrid.worldToTileX(startX);
        int rawStartTy = worldGrid.worldToTileY(startY);
        int rawGoalTx = worldGrid.worldToTileX(targetX);
        int rawGoalTy = worldGrid.worldToTileY(targetY);

        final boolean bounded = worldGrid.isBounded();
        final int windowW = bounded ? boundedWidth : GRID_W;
        final int windowH = bounded ? boundedHeight : GRID_H;
        final int windowOriginX;
        final int windowOriginY;

        if (bounded) {
            windowOriginX = boundedMinTx;
            windowOriginY = boundedMinTy;
        } else {
            int midX = (int) Math.floorDiv((long) rawStartTx + (long) rawGoalTx, 2L);
            int midY = (int) Math.floorDiv((long) rawStartTy + (long) rawGoalTy, 2L);
            int ox = midX - (GRID_W / 2);
            int oy = midY - (GRID_H / 2);

            // Guarantee at least 32 tiles of margin around start tile inside the 128x128 window
            if (ox > rawStartTx - START_MARGIN_TILES) {
                ox = rawStartTx - START_MARGIN_TILES;
            }
            if (rawStartTx + START_MARGIN_TILES > ox + GRID_W - 1) {
                ox = rawStartTx + START_MARGIN_TILES - (GRID_W - 1);
            }
            if (oy > rawStartTy - START_MARGIN_TILES) {
                oy = rawStartTy - START_MARGIN_TILES;
            }
            if (rawStartTy + START_MARGIN_TILES > oy + GRID_H - 1) {
                oy = rawStartTy + START_MARGIN_TILES - (GRID_H - 1);
            }

            windowOriginX = ox;
            windowOriginY = oy;
        }

        int startX_t = bounded
                ? Math.max(windowOriginX, Math.min(windowOriginX + windowW - 1, rawStartTx))
                : rawStartTx;
        int startY_t = bounded
                ? Math.max(windowOriginY, Math.min(windowOriginY + windowH - 1, rawStartTy))
                : rawStartTy;

        int clampMinX = bounded ? windowOriginX : windowOriginX + 1;
        int clampMaxX = bounded ? (windowOriginX + windowW - 1) : (windowOriginX + windowW - 2);
        int clampMinY = bounded ? windowOriginY : windowOriginY + 1;
        int clampMaxY = bounded ? (windowOriginY + windowH - 1) : (windowOriginY + windowH - 2);

        int targetTx = Math.max(clampMinX, Math.min(clampMaxX, rawGoalTx));
        int targetTy = Math.max(clampMinY, Math.min(clampMaxY, rawGoalTy));
        boolean targetClampedToWindow = !bounded && (targetTx != rawGoalTx || targetTy != rawGoalTy);

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
                    if (isInWindow(nx, ny, windowOriginX, windowOriginY, windowW, windowH)
                            && isCellWalkable(nx, ny, entityRadius)) {
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
        if (startX_t == targetTx && startY_t == targetTy) {
            lastSearchReachedGoal = !targetClampedToWindow;
            List<Vector2D> direct = new ArrayList<>(1);
            direct.add((targetAdjusted || targetClampedToWindow)
                    ? worldGrid.tileToWorld(targetTx, targetTy)
                    : new Vector2D(targetX, targetY));
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

        int startIdx = toWindowIndex(startX_t, startY_t, windowOriginX, windowOriginY, windowW);
        cellStates[startIdx].g = 0.0f;
        cellStates[startIdx].parentX = startX_t;
        cellStates[startIdx].parentY = startY_t;
        cellStates[startIdx].hasParent = false;
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
            int curIdx = toWindowIndex(cur.x, cur.y, windowOriginX, windowOriginY, windowW);

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

                if (!isInWindow(nx, ny, windowOriginX, windowOriginY, windowW, windowH)) {
                    continue;
                }

                boolean isDiagonal = (DX[i] != 0 && DY[i] != 0);
                if (isDiagonal) {
                    int orthX1 = cur.x + DX[i];
                    int orthY1 = cur.y;
                    int orthX2 = cur.x;
                    int orthY2 = cur.y + DY[i];
                    if (cornerRule == CornerCuttingRule.STRICT) {
                        // Corner-cutting prevention: both orthogonal neighbors must be walkable
                        if (!isCellWalkable(orthX1, orthY1, entityRadius)
                                || !isCellWalkable(orthX2, orthY2, entityRadius)) {
                            continue;
                        }
                    } else if (cornerRule == CornerCuttingRule.LENIENT) {
                        boolean w1 = isCellWalkable(orthX1, orthY1, entityRadius);
                        boolean w2 = isCellWalkable(orthX2, orthY2, entityRadius);
                        if (!w1 && !w2) {
                            continue;
                        }
                        // Never clip a water corner diagonally (e.g., entering/exiting bridges)
                        if (worldGrid.isWaterTile(orthX1, orthY1) || worldGrid.isWaterTile(orthX2, orthY2)) {
                            continue;
                        }
                    }
                }

                // Target cell must be walkable for entity size
                if (!isCellWalkable(nx, ny, entityRadius)) {
                    continue;
                }

                float baseCost = isDiagonal ? costDiagonal : costOrthogonal;
                float stepCost = baseCost + computeWaterAdjacencyPenalty(nx, ny);
                float newG = cur.g + stepCost;

                int nIdx = toWindowIndex(nx, ny, windowOriginX, windowOriginY, windowW);
                if (cellStates[nIdx].runId != currentRunId || newG < cellStates[nIdx].g) {
                    cellStates[nIdx].g = newG;
                    cellStates[nIdx].parentX = cur.x;
                    cellStates[nIdx].parentY = cur.y;
                    cellStates[nIdx].hasParent = true;
                    cellStates[nIdx].runId = currentRunId;

                    float h = octileHeuristic(nx, ny, targetTx, targetTy);
                    openSet.add(new PathNode(nx, ny, newG, newG + h));
                }
            }
        }

        lastSearchReachedGoal = reachedGoal && !targetClampedToWindow;

        // Reconstruct raw tile path backwards from bestX, bestY
        List<Vector2D> rawPath = new ArrayList<>();
        int currX = bestX;
        int currY = bestY;
        int maxTraceSteps = windowW * windowH;
        int traced = 0;

        while (isInWindow(currX, currY, windowOriginX, windowOriginY, windowW, windowH)
                && traced++ < maxTraceSteps) {
            rawPath.add(worldGrid.tileToWorld(currX, currY));
            int idx = toWindowIndex(currX, currY, windowOriginX, windowOriginY, windowW);
            CellState st = cellStates[idx];
            if (!st.hasParent || (st.parentX == currX && st.parentY == currY)) {
                break;
            }
            currX = st.parentX;
            currY = st.parentY;
        }

        if (rawPath.size() <= 1) {
            List<Vector2D> fallback = new ArrayList<>(1);
            fallback.add((!reachedGoal || targetAdjusted || targetClampedToWindow)
                    ? worldGrid.tileToWorld(bestX, bestY)
                    : new Vector2D(targetX, targetY));
            return fallback;
        }

        Collections.reverse(rawPath);
        if (reachedGoal && !targetAdjusted && !targetClampedToWindow
                && worldGrid.isTraversable(targetX, targetY, entityRadius)) {
            rawPath.set(rawPath.size() - 1, new Vector2D(targetX, targetY));
        }

        // Path smoothing (string-pulling): remove intermediate zig-zags with clear LOS
        List<Vector2D> smoothed = new ArrayList<>();
        smoothed.add(rawPath.get(0));

        int currentIdx = 0;
        while (currentIdx < rawPath.size() - 1) {
            int nextIdx = currentIdx + 1;
            for (int checkIdx = rawPath.size() - 1; checkIdx > currentIdx + 1; checkIdx--) {
                if (hasLineOfSight(rawPath.get(currentIdx), rawPath.get(checkIdx), clearanceProbeRadius)) {
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

    private float computeWaterAdjacencyPenalty(int tx, int ty) {
        float penalty = 0.0f;
        for (int i = 0; i < 8; i++) {
            if (worldGrid.isWaterTile(tx + DX[i], ty + DY[i])) {
                penalty += (i < 4) ? (tileSize * 0.5f) : (tileSize * 0.25f);
            }
        }
        return penalty;
    }

    private static boolean isInWindow(int tx, int ty, int originX, int originY, int width, int height) {
        return tx >= originX && tx < originX + width && ty >= originY && ty < originY + height;
    }

    private static int toWindowIndex(int tx, int ty, int originX, int originY, int width) {
        return (ty - originY) * width + (tx - originX);
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
