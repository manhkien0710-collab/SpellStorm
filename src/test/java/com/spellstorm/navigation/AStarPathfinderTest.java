package com.spellstorm.navigation;

import com.spellstorm.math.Vector2D;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 8: A* Pathfinder & Navigation Tests")
public class AStarPathfinderTest {

    // =========================================================================
    // Tier 1: Equivalence Partitioning (LOS, Straight-Line, Obstacle Avoidance)
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Straight-Line & Obstacle Avoidance")
    class Tier1EquivalenceTests {

        @Test
        @DisplayName("1.1 Straight-line unobstructed path returns direct target waypoint (fast path)")
        void testStraightLineLOSPath() {
            TileMap map = new TileMap(42);
            AStarPathfinder pathfinder = new AStarPathfinder(map);

            // Open area on west plains
            Vector2D start = new Vector2D(500.0f, 500.0f);
            Vector2D target = new Vector2D(500.0f, 650.0f);

            assertTrue(map.isTraversable(start, 16.0f));
            assertTrue(map.isTraversable(target, 16.0f));

            if (pathfinder.hasLineOfSight(start, target, 16.0f)) {
                List<Vector2D> path = pathfinder.findPath(start, target, 16.0f);
                assertFalse(path.isEmpty());
                assertEquals(1, path.size());
                assertEquals(target.x, path.get(0).x, 1e-3f);
                assertEquals(target.y, path.get(0).y, 1e-3f);
            }
        }

        @Test
        @DisplayName("1.2 Obstacle avoidance: navigates around solid obstacles to target")
        void testObstacleAvoidance() {
            TileMap map = new TileMap(42);
            AStarPathfinder pathfinder = new AStarPathfinder(map);

            // Clear decorations for an isolated test
            map.clearObstacles();

            Vector2D start = new Vector2D(2000.0f, 2048.0f);
            Vector2D target = new Vector2D(2100.0f, 2048.0f);

            // Place a solid obstacle directly between start and target at (2050, 2048)
            map.addDecoration(DecoType.ROCK_LARGE, new Vector2D(2050.0f, 2048.0f), true);

            // Line of sight must report blocked
            assertFalse(pathfinder.hasLineOfSight(start, target, 16.0f));

            // A* must find path around the rock
            List<Vector2D> path = pathfinder.findPath(start, target, 16.0f);
            assertFalse(path.isEmpty());
            assertTrue(path.size() >= 2);

            // Final waypoint must be at or very close to target
            Vector2D endWp = path.get(path.size() - 1);
            assertEquals(target.x, endWp.x, 32.0f);
            assertEquals(target.y, endWp.y, 32.0f);

            // Every waypoint along the path must be traversable
            for (Vector2D wp : path) {
                assertTrue(map.isTraversable(wp, 16.0f));
            }
        }

        @Test
        @DisplayName("1.3 Octile heuristic calculation adheres to formula")
        void testOctileHeuristicCalculation() {
            TileMap map = new TileMap(42);
            AStarPathfinder pathfinder = new AStarPathfinder(map);

            // Cardinal move of 1 tile (dx = 1, dy = 0) -> cost = 32.0f
            assertEquals(32.0f, pathfinder.octileHeuristic(0, 0, 1, 0), 1e-4f);

            // Diagonal move of 1 tile (dx = 1, dy = 1) -> cost = 32.0 * sqrt(2) ~ 45.254834f
            float expectedDiag = 32.0f * (float) Math.sqrt(2.0);
            assertEquals(expectedDiag, pathfinder.octileHeuristic(0, 0, 1, 1), 1e-4f);

            // dx = 3, dy = 4 -> max=4, min=3 -> (4 + (sqrt(2)-1)*3) * 32.0f
            float expected = (4.0f + ((float) Math.sqrt(2.0) - 1.0f) * 3.0f) * 32.0f;
            assertEquals(expected, pathfinder.octileHeuristic(0, 0, 3, 4), 1e-3f);
        }
    }

    // =========================================================================
    // Tier 2: Boundary Value Analysis (Corner-Cutting & Radius Clearance)
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: Corner-Cutting Prevention & Entity Clearance")
    class Tier2CornerCuttingAndRadiusTests {

        @Test
        @DisplayName("2.1 Corner-cutting prevention rejects diagonal slice between orthogonal obstacles")
        void testCornerCuttingPrevention() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Set up a custom grid pattern:
            // (10, 10) = Walkable Grass
            // (11, 10) = Solid Water
            // (10, 11) = Walkable Grass
            // (11, 11) = Walkable Grass
            // Moving from (10, 10) to (11, 11) diagonally slices the corner of (11, 10).
            for (int y = 9; y <= 12; y++) {
                for (int x = 9; x <= 12; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }
            map.setTile(11, 10, TileType.WATER); // Solid obstacle at (11, 10)

            AStarPathfinder strictPf = new AStarPathfinder(map, AStarPathfinder.CornerCuttingRule.STRICT);

            // Move from center of (10, 10) to center of (11, 11)
            Vector2D start = map.tileToWorld(10, 10);
            Vector2D target = map.tileToWorld(11, 11);

            List<Vector2D> path = strictPf.findPath(start, target, 10.0f);
            assertFalse(path.isEmpty());

            // Because (11, 10) is solid water, the direct diagonal step (10, 10) -> (11, 11)
            // is prohibited. The path must route cardinally via (10, 11)!
            boolean routedViaOrthogonal = false;
            for (Vector2D wp : path) {
                int[] tile = map.worldToTile(wp);
                if (tile[0] == 10 && tile[1] == 11) {
                    routedViaOrthogonal = true;
                    break;
                }
            }
            assertTrue(routedViaOrthogonal, "Path must step cardinally through (10, 11) to avoid cutting corner");
        }

        @Test
        @DisplayName("2.2 Radius clearance differentiates Melee (r=16), Tank (r=26), and Boss (r=50)")
        void testRadiusClearanceDivergence() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Construct a corridor 2 tiles wide (64px) flanked by water walls:
            // Rows y=10 to y=14:
            // x=10: WATER
            // x=11, x=12: GRASS (width = 64px)
            // x=13: WATER
            for (int y = 10; y <= 14; y++) {
                map.setTile(10, y, TileType.WATER);
                map.setTile(11, y, TileType.GRASS);
                map.setTile(12, y, TileType.GRASS);
                map.setTile(13, y, TileType.WATER);
            }

            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D corridorCenter = new Vector2D(12.0f * 32.0f, 12.0f * 32.0f); // (384, 384)
            // Melee (r=16) fits easily in 64px corridor
            assertTrue(map.isTraversable(corridorCenter, 16.0f));

            // Tank (r=26, diameter=52px) fits in 64px corridor
            assertTrue(map.isTraversable(corridorCenter, 26.0f));

            // Boss (r=50, diameter=100px) DOES NOT fit in 64px corridor
            assertFalse(map.isTraversable(corridorCenter, 50.0f));

            // Pathfinding through this corridor:
            Vector2D start = new Vector2D(12.0f * 32.0f, 10.5f * 32.0f);
            Vector2D end = new Vector2D(12.0f * 32.0f, 13.5f * 32.0f);

            // Melee finds path
            List<Vector2D> meleePath = pf.findPath(start, end, 16.0f);
            assertFalse(meleePath.isEmpty());

            // Boss cannot traverse and must fallback or fail to walk the narrow corridor
            assertFalse(pf.isCellWalkable(11, 12, 50.0f));
            assertFalse(pf.isCellWalkable(12, 12, 50.0f));
        }
    }

    // =========================================================================
    // Tier 3: River Bridge Traversal & Boss Crossing
    // =========================================================================
    @Nested
    @DisplayName("Tier 3: River Bridge Traversal & Boss Crossing")
    class Tier3BridgeTraversalTests {

        @Test
        @DisplayName("3.1 Enemy (r=16) navigates across river via bridge from West to East bank")
        void testRiverCrossingViaBridge() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            // River runs North-South around x = 42 (x ~ 1344)
            // West bank at (1000, 1500), East bank at (1700, 1500)
            Vector2D westBank = new Vector2D(1000.0f, 1500.0f);
            Vector2D eastBank = new Vector2D(1700.0f, 1500.0f);

            assertTrue(map.isTraversable(westBank, 16.0f));
            assertTrue(map.isTraversable(eastBank, 16.0f));

            // Line of sight across water must be blocked
            assertFalse(pf.hasLineOfSight(westBank, eastBank, 16.0f));

            // A* must find path across one of the river bridges
            List<Vector2D> path = pf.findPath(westBank, eastBank, 16.0f);
            assertFalse(path.isEmpty());
            assertTrue(path.size() >= 2);

            Vector2D endPoint = path.get(path.size() - 1);
            assertEquals(eastBank.x, endPoint.x, 48.0f);
            assertEquals(eastBank.y, endPoint.y, 48.0f);

            // Verify path actually crossed a bridge tile
            boolean crossedBridge = false;
            for (Vector2D wp : path) {
                int[] tile = map.worldToTile(wp);
                if (map.isBridgeTile(tile[0], tile[1])) {
                    crossedBridge = true;
                    break;
                }
            }
            assertTrue(crossedBridge, "Path must cross at least one bridge tile");
        }

        @Test
        @DisplayName("3.2 Colossus Boss (r=50) successfully traverses bridge to East bank")
        void testBossRiverCrossingViaBridge() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            float bossRadius = 50.0f;
            Vector2D westBank = new Vector2D(1000.0f, 1500.0f);
            Vector2D eastBank = new Vector2D(1700.0f, 1500.0f);

            assertTrue(map.isTraversable(westBank, bossRadius));
            assertTrue(map.isTraversable(eastBank, bossRadius));

            // Boss A* across river
            List<Vector2D> bossPath = pf.findPath(westBank, eastBank, bossRadius);
            assertFalse(bossPath.isEmpty());

            Vector2D endPoint = bossPath.get(bossPath.size() - 1);
            assertEquals(eastBank.x, endPoint.x, 64.0f);
            assertEquals(eastBank.y, endPoint.y, 64.0f);

            // All waypoints in boss path must be traversable with boss radius
            for (Vector2D wp : bossPath) {
                assertTrue(map.isTraversable(wp, bossRadius),
                        "Boss waypoint " + wp + " must be traversable for radius 50");
            }
        }
    }

    // =========================================================================
    // Tier 4: Fallback & Path Caching
    // =========================================================================
    @Nested
    @DisplayName("Tier 4: Unreachable Target Fallback & Path Caching")
    class Tier4FallbackAndCachingTests {

        @Test
        @DisplayName("4.1 Target standing in deep water returns path to closest shoreline vantage point")
        void testUnreachableWaterTargetFallback() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D westBank = new Vector2D(1000.0f, 1500.0f);

            // Find deep water at row y = 46 (between bridges)
            Vector2D deepWater = null;
            for (int x = 30; x <= 60; x++) {
                if (map.tileAt(x, 46) == TileType.WATER) {
                    deepWater = map.tileToWorld(x, 46);
                    break;
                }
            }
            assertNotNull(deepWater);
            assertFalse(map.isTraversable(deepWater, 16.0f));

            // Pathfinder must NOT return empty path; it must return fallback path to shoreline
            List<Vector2D> fallbackPath = pf.findPath(westBank, deepWater, 16.0f);
            assertFalse(fallbackPath.isEmpty());

            Vector2D endPoint = fallbackPath.get(fallbackPath.size() - 1);
            assertTrue(map.isTraversable(endPoint, 16.0f),
                    "Fallback vantage point must be legally traversable");
            // End point should be near the river bank
            assertTrue(endPoint.x > westBank.x, "Entity should advance towards the water");
        }

        @Test
        @DisplayName("4.2 PathCache manages repath intervals, stuck timers, displacement, and look-ahead")
        void testPathCacheLifecycle() {
            AStarPathfinder.PathCache cache = new AStarPathfinder.PathCache();

            // Initial state: empty path needs repath
            assertTrue(cache.needsRepath(new Vector2D(100.0f, 100.0f)));

            List<Vector2D> path = List.of(
                    new Vector2D(10.0f, 10.0f),
                    new Vector2D(50.0f, 50.0f),
                    new Vector2D(100.0f, 100.0f)
            );

            Vector2D targetPos = new Vector2D(100.0f, 100.0f);
            cache.resetAfterRepath(path, targetPos, 0);

            // Staggered repath for entity ID 0: 0.35s
            assertEquals(0.35f, cache.getNavRepathTimer(), 1e-4f);
            assertEquals(0, cache.getNavWaypointIndex());
            assertFalse(cache.needsRepath(targetPos));

            // Target displacement > 64 units triggers repath
            Vector2D movedTarget = new Vector2D(170.0f, 100.0f); // moved 70 units
            assertTrue(cache.needsRepath(movedTarget));

            // Timer expiration triggers repath
            cache.updateTimers(new Vector2D(10.0f, 10.0f), 0.40f);
            assertTrue(cache.getNavRepathTimer() < 0.0f);
            assertTrue(cache.needsRepath(targetPos));

            // Stuck detection: position unchanged for > 0.35s
            cache.resetAfterRepath(path, targetPos, 1); // 0.40s timer
            Vector2D pos = new Vector2D(10.0f, 10.0f);
            cache.updateTimers(pos, 0.20f);
            assertFalse(cache.needsRepath(targetPos));
            cache.updateTimers(pos, 0.20f); // stuck timer = 0.40s > 0.35s
            assertTrue(cache.needsRepath(targetPos));

            // Look-ahead waypoint advancement
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);
            cache.resetAfterRepath(path, targetPos, 2);

            // At (15, 15), within 22 units of waypoint 0 (10, 10) -> advances to waypoint 1 (50, 50)
            Vector2D advanced = cache.advanceWaypoint(new Vector2D(15.0f, 15.0f), pf, 16.0f);
            assertNotNull(advanced);
            assertTrue(cache.getNavWaypointIndex() >= 1);
        }

        @Test
        @DisplayName("4.3 (Tier 1, Feature 14): Raycast line-of-sight checks across 8 distinct angles")
        void testRaycastLineOfSightVaryingAngles() {
            TileMap map = new TileMap(42);
            map.clearObstacles(); // Open grassland with only river
            AStarPathfinder pf = new AStarPathfinder(map);

            // Center on open east grassland (x = 3000, y = 2048) far from river
            Vector2D center = new Vector2D(3000.0f, 2048.0f);
            float radius = 16.0f;
            float rayDistance = 120.0f;

            for (int i = 0; i < 8; i++) {
                double angle = i * (Math.PI / 4.0);
                float tx = center.x + (float) Math.cos(angle) * rayDistance;
                float ty = center.y + (float) Math.sin(angle) * rayDistance;
                Vector2D target = new Vector2D(tx, ty);

                assertTrue(pf.hasLineOfSight(center, target, radius),
                        "Clear unobstructed line of sight must hold at angle " + Math.toDegrees(angle));
            }

            // Obstructed LOS: add rock obstacle along 0-degree angle
            map.addDecoration(com.spellstorm.world.DecoType.ROCK_LARGE, center.x + 60.0f, center.y + 6.0f, true);
            Vector2D eastTarget = new Vector2D(center.x + rayDistance, center.y);
            assertFalse(pf.hasLineOfSight(center, eastTarget, radius),
                    "Line of sight must be blocked by intervening rock obstacle");
        }

        @Test
        @DisplayName("4.4 (Tier 2, Feature 14): Staggered repath cadence formula and stuck timer boundary")
        void testStaggeredRepathCadenceFormula() {
            AStarPathfinder.PathCache cache = new AStarPathfinder.PathCache();
            List<Vector2D> dummyPath = List.of(new Vector2D(100f, 100f), new Vector2D(200f, 200f));
            Vector2D target = new Vector2D(200f, 200f);

            float[] expectedCadences = {0.35f, 0.40f, 0.45f, 0.50f, 0.55f};

            // Test first cycle (id 0-4)
            for (int id = 0; id < 5; id++) {
                cache.resetAfterRepath(dummyPath, target, id);
                assertEquals(expectedCadences[id], cache.getNavRepathTimer(), 1e-4f,
                        "Cadence for entity ID " + id + " must be " + expectedCadences[id]);
            }

            // Test modulo wrap (id 5-9)
            for (int id = 5; id < 10; id++) {
                cache.resetAfterRepath(dummyPath, target, id);
                assertEquals(expectedCadences[id % 5], cache.getNavRepathTimer(), 1e-4f,
                        "Cadence for entity ID " + id + " must wrap to " + expectedCadences[id % 5]);
            }

            // Stuck timer boundary test: 0.35s threshold
            Vector2D pos = new Vector2D(500f, 500f);
            cache.resetAfterRepath(dummyPath, target, 0); // 0.35s timer
            cache.updateTimers(pos, 0.34f);
            assertFalse(cache.needsRepath(target), "Needs repath should be false before 0.35s boundary");

            cache.updateTimers(pos, 0.02f); // total 0.36s >= 0.35s stuck limit
            assertTrue(cache.needsRepath(target), "Needs repath must trigger once stuck timer passes 0.35s");
        }
    }
}
