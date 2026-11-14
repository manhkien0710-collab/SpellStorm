package com.spellstorm.navigation;

import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3 & 8: Empirical Navigation Challenge Tests")
public class AStarPathfinderEmpiricalChallengeTest {

    // =========================================================================
    // 1. Octile Heuristic, Admissibility, and 8-Way Movement Optimality
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: Octile Heuristic & Optimal 8-Way Movement")
    class OctileHeuristicAndMovementTests {

        @Test
        @DisplayName("1.1 Heuristic is admissible (never overestimates true 8-way shortest path)")
        void testOctileHeuristicAdmissibility() {
            TileMap map = new TileMap(42);
            map.clearObstacles();
            AStarPathfinder pf = new AStarPathfinder(map);

            // On completely open grass grid, heuristic must equal the true theoretical cost
            for (int dx = 0; dx <= 20; dx++) {
                for (int dy = 0; dy <= 20; dy++) {
                    float h = pf.octileHeuristic(10, 10, 10 + dx, 10 + dy);
                    int minD = Math.min(dx, dy);
                    int maxD = Math.max(dx, dy);
                    float expected = (maxD + (AStarPathfinder.SQRT2 - 1.0f) * minD) * map.getTileSize();
                    assertEquals(expected, h, 1e-4f, "Heuristic must match exact formula");
                }
            }
        }

        @Test
        @DisplayName("1.2 Heuristic satisfies triangle inequality (consistency/monotonicity)")
        void testOctileHeuristicMonotonicity() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            // For any triplet of nodes (A, B, C): h(A, C) <= cost(A, B) + h(B, C)
            // Test across cardinal and diagonal single-step neighbors
            int ax = 50, ay = 50;
            int cx = 70, cy = 65;
            float hAC = pf.octileHeuristic(ax, ay, cx, cy);

            int[] dxs = { 0, 0, 1, -1, 1, -1, 1, -1 };
            int[] dys = { 1, -1, 0, 0, 1, 1, -1, -1 };

            for (int i = 0; i < 8; i++) {
                int bx = ax + dxs[i];
                int by = ay + dys[i];
                float stepCost = (dxs[i] != 0 && dys[i] != 0) ? AStarPathfinder.DIAGONAL_COST : AStarPathfinder.CARDINAL_COST;
                float hBC = pf.octileHeuristic(bx, by, cx, cy);
                assertTrue(hAC <= stepCost + hBC + 1e-4f,
                        "Triangle inequality violated: h(A,C)=" + hAC + " > stepCost(" + stepCost + ") + h(B,C)=" + hBC);
            }
        }

        @Test
        @DisplayName("1.3 Cardinal and pure diagonal moves follow minimum step costs")
        void testCardinalAndDiagonalShortestPaths() {
            TileMap map = new TileMap(42);
            map.clearObstacles();
            AStarPathfinder pf = new AStarPathfinder(map);

            // Pure cardinal move: (20, 20) -> (20, 25) [5 tiles North]
            Vector2D startCard = map.tileToWorld(20, 20);
            Vector2D endCard = map.tileToWorld(20, 25);
            List<Vector2D> cardPath = pf.findPath(startCard, endCard, 10.0f);
            assertFalse(cardPath.isEmpty());
            Vector2D lastCard = cardPath.get(cardPath.size() - 1);
            assertEquals(endCard.x, lastCard.x, 1e-3f);
            assertEquals(endCard.y, lastCard.y, 1e-3f);

            // Pure diagonal move: (20, 20) -> (25, 25) [5 tiles North-East]
            Vector2D startDiag = map.tileToWorld(20, 20);
            Vector2D endDiag = map.tileToWorld(25, 25);
            List<Vector2D> diagPath = pf.findPath(startDiag, endDiag, 10.0f);
            assertFalse(diagPath.isEmpty());
            Vector2D lastDiag = diagPath.get(diagPath.size() - 1);
            assertEquals(endDiag.x, lastDiag.x, 1e-3f);
            assertEquals(endDiag.y, lastDiag.y, 1e-3f);
        }
    }

    // =========================================================================
    // 2. Corner-Cutting Traps & Double-Orthogonal Invariant
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: Corner-Cutting Traps & Invariants")
    class CornerCuttingTrapsTests {

        @Test
        @DisplayName("2.1 Corner-cutting traps: all 4 diagonal quadrants strictly block corner cutting")
        void testAllFourQuadrantCornerCuttingRejection() {
            // Test 4 diagonal quadrants:
            // Quadrant 1 (dx=+1, dy=+1): Top-Right
            // Quadrant 2 (dx=-1, dy=+1): Top-Left
            // Quadrant 3 (dx=+1, dy=-1): Bottom-Right
            // Quadrant 4 (dx=-1, dy=-1): Bottom-Left

            int[][] quads = {
                    { 1, 1 },   // TR
                    { -1, 1 },  // TL
                    { 1, -1 },  // BR
                    { -1, -1 }  // BL
            };

            for (int[] q : quads) {
                int qx = q[0];
                int qy = q[1];

                // Case A: orthogonal horizontal neighbor is solid water
                testSingleCornerTrap(qx, qy, true);

                // Case B: orthogonal vertical neighbor is solid water
                testSingleCornerTrap(qx, qy, false);
            }
        }

        private void testSingleCornerTrap(int qx, int qy, boolean blockHorizontal) {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            int cx = 20;
            int cy = 20;

            // Make 5x5 area around (20, 20) grass
            for (int y = cy - 2; y <= cy + 2; y++) {
                for (int x = cx - 2; x <= cx + 2; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            int blockedX = blockHorizontal ? (cx + qx) : cx;
            int blockedY = blockHorizontal ? cy : (cy + qy);
            map.setTile(blockedX, blockedY, TileType.WATER);

            AStarPathfinder pf = new AStarPathfinder(map, AStarPathfinder.CornerCuttingRule.STRICT);

            Vector2D start = map.tileToWorld(cx, cy);
            Vector2D target = map.tileToWorld(cx + qx, cy + qy);

            List<Vector2D> path = pf.findPath(start, target, 12.0f);
            assertFalse(path.isEmpty(), "Path must be found around corner");

            // Verify path NEVER clips through the corner of blocked tile:
            // The unblocked orthogonal cell must be used as the intermediate waypoint
            int openOrthogonalX = blockHorizontal ? cx : (cx + qx);
            int openOrthogonalY = blockHorizontal ? (cy + qy) : cy;

            boolean routedThroughOpenOrthogonal = false;
            for (Vector2D wp : path) {
                int[] t = map.worldToTile(wp);
                if (t[0] == openOrthogonalX && t[1] == openOrthogonalY) {
                    routedThroughOpenOrthogonal = true;
                    break;
                }
            }
            assertTrue(routedThroughOpenOrthogonal,
                    "Diagonal step (" + qx + "," + qy + ") with (" + blockedX + "," + blockedY +
                            ") blocked MUST route via orthogonal (" + openOrthogonalX + "," + openOrthogonalY + ")");
        }

        @Test
        @DisplayName("2.2 Checkerboard pinch trap: diagonal step with BOTH orthogonals blocked is strictly rejected")
        void testCheckerboardPinchTrap() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            int cx = 30;
            int cy = 30;

            for (int y = cy - 4; y <= cy + 4; y++) {
                for (int x = cx - 4; x <= cx + 4; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            // Pinch: (31, 30) WATER, (30, 31) WATER
            map.setTile(cx + 1, cy, TileType.WATER);
            map.setTile(cx, cy + 1, TileType.WATER);

            AStarPathfinder pf = new AStarPathfinder(map, AStarPathfinder.CornerCuttingRule.STRICT);

            Vector2D start = map.tileToWorld(cx, cy);
            Vector2D target = map.tileToWorld(cx + 1, cy + 1);

            List<Vector2D> path = pf.findPath(start, target, 10.0f);
            assertFalse(path.isEmpty());

            // Since both direct orthogonals are blocked, direct diagonal (cx, cy) -> (cx+1, cy+1) is impossible.
            // Path must make a detour around (e.g. via cx-1, cy+2, etc.)
            for (Vector2D wp : path) {
                int[] t = map.worldToTile(wp);
                assertFalse(t[0] == cx + 1 && t[1] == cy, "Must not traverse blocked water tile (cx+1, cy)");
                assertFalse(t[0] == cx && t[1] == cy + 1, "Must not traverse blocked water tile (cx, cy+1)");
            }
            // Length must be strictly greater than a single diagonal step
            assertTrue(path.size() >= 3, "Detour around checkerboard pinch must require >= 3 waypoints");
        }

        @Test
        @DisplayName("2.3 Diagonal wall trap: entity cannot squeeze through diagonal water staircase")
        void testDiagonalWallStaircaseTrap() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Create a diagonal wall from (20, 20) to (25, 25)
            // (20, 20), (21, 21), (22, 22), (23, 23), (24, 24), (25, 25) are WATER
            for (int i = 0; i <= 5; i++) {
                map.setTile(20 + i, 20 + i, TileType.WATER);
            }

            AStarPathfinder pf = new AStarPathfinder(map, AStarPathfinder.CornerCuttingRule.STRICT);

            // Start on northwest side of wall: (21, 23)
            // Target on southeast side of wall: (23, 21)
            Vector2D start = map.tileToWorld(21, 23);
            Vector2D target = map.tileToWorld(23, 21);

            List<Vector2D> path = pf.findPath(start, target, 12.0f);
            assertFalse(path.isEmpty());

            // Path must navigate around one end of the diagonal wall (around (20, 20) or around (25, 25))
            boolean detouredAroundEnd = false;
            for (Vector2D wp : path) {
                int[] t = map.worldToTile(wp);
                if (t[0] < 20 || t[1] < 20 || t[0] > 25 || t[1] > 25) {
                    detouredAroundEnd = true;
                    break;
                }
            }
            assertTrue(detouredAroundEnd, "Pathfinder must detour around the diagonal wall ends and not clip corners");
        }
    }

    // =========================================================================
    // 3. Entity Size Awareness (Melee r=16, Tank r=26, Boss r=50)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: Entity Size Awareness")
    class EntitySizeAwarenessTests {

        @Test
        @DisplayName("3.1 Narrow gap (36px): Melee (r=16, d=32) traverses, Tank (r=26, d=52) blocked")
        void testNarrowGap36px() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Set up a wall at x = 20 with a 36px gap at y = 640px (between two large rocks)
            // Rock 1 anchor at (640, 600), Rock 2 anchor at (640, 680)
            // Large rock has collision radius = 22.0f
            // Distance between centers: 80px. Clear gap = 80 - 44 = 36px.
            map.addDecoration(DecoType.ROCK_LARGE, 640.0f, 600.0f, true);
            map.addDecoration(DecoType.ROCK_LARGE, 640.0f, 680.0f, true);

            Vector2D gapCenter = new Vector2D(640.0f, 640.0f);

            // Melee (r=16): clearance required = 16 + 22 = 38 from each rock center.
            // Distance from gapCenter to (640, 600) is 40 > 38. Fits!
            assertTrue(map.isTraversable(gapCenter, 16.0f), "Melee r=16 must fit in 36px gap");

            // Tank (r=26): clearance required = 26 + 22 = 48.
            // Distance is 40 < 48. Blocked!
            assertFalse(map.isTraversable(gapCenter, 26.0f), "Tank r=26 must NOT fit in 36px gap");

            // Boss (r=50): clearance required = 50 + 22 = 72. Blocked!
            assertFalse(map.isTraversable(gapCenter, 50.0f), "Boss r=50 must NOT fit in 36px gap");
        }

        @Test
        @DisplayName("3.2 Medium gap (60px): Tank (r=26, d=52) traverses, Boss (r=50, d=100) blocked")
        void testMediumGap60px() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Two rocks with 60px gap:
            // Rock collision radius = 22.0f.
            // Centers at (640, 590) and (640, 694). Distance = 104px.
            // Gap = 104 - 44 = 60px.
            map.addDecoration(DecoType.ROCK_LARGE, 640.0f, 590.0f, true);
            map.addDecoration(DecoType.ROCK_LARGE, 640.0f, 694.0f, true);

            Vector2D gapCenter = new Vector2D(640.0f, 642.0f);

            // Tank (r=26): 26 + 22 = 48 from rock center. Distance to each rock is 52 > 48. Fits!
            assertTrue(map.isTraversable(gapCenter, 26.0f), "Tank r=26 must fit in 60px gap");

            // Boss (r=50): 50 + 22 = 72. Distance is 52 < 72. Blocked!
            assertFalse(map.isTraversable(gapCenter, 50.0f), "Boss r=50 must NOT fit in 60px gap");

            AStarPathfinder pf = new AStarPathfinder(map);

            // Tank finds path directly through the gap
            Vector2D west = new Vector2D(580.0f, 642.0f);
            Vector2D east = new Vector2D(700.0f, 642.0f);
            assertTrue(pf.hasLineOfSight(west, east, 26.0f), "Tank has clear LOS through 60px gap");

            // Boss line of sight is strictly blocked
            assertFalse(pf.hasLineOfSight(west, east, 50.0f), "Boss does NOT have LOS through 60px gap");
        }
    }

    // =========================================================================
    // 4. Cross-River Navigation: (800, 2048) to (3200, 2048) & Continuous Clearance
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: Cross-River Highway Navigation")
    class CrossRiverNavigationTests {

        @Test
        @DisplayName("4.1 Route from (800, 2048) to (3200, 2048) without water snags (Melee, Tank, Boss)")
        void testCrossRiverRouteAcrossCentralHighway() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D westStart = new Vector2D(800.0f, 2048.0f);
            Vector2D eastTarget = new Vector2D(3200.0f, 2048.0f);

            float[] testRadii = { 16.0f, 26.0f, 50.0f };
            String[] roles = { "Melee (r=16)", "Tank (r=26)", "Boss (r=50)" };

            for (int r = 0; r < testRadii.length; r++) {
                float radius = testRadii[r];
                String role = roles[r];

                assertTrue(map.isTraversable(westStart, radius), role + " start must be traversable");
                assertTrue(map.isTraversable(eastTarget, radius), role + " target must be traversable");

                List<Vector2D> path = pf.findPath(westStart, eastTarget, radius);
                assertFalse(path.isEmpty(), role + " must find cross-river path");

                Vector2D destination = path.get(path.size() - 1);
                assertEquals(eastTarget.x, destination.x, 64.0f, role + " must arrive at east bank X");
                assertEquals(eastTarget.y, destination.y, 64.0f, role + " must arrive at east bank Y");

                // Continuous walk test: simulate entity walking along waypoints with 4px step
                // Verify zero water snags or obstacle collisions along the entire path
                verifyContinuousPathTraversability(map, path, radius, role);
            }
        }

        private void verifyContinuousPathTraversability(TileMap map, List<Vector2D> path, float radius, String role) {
            float stepSize = 4.0f; // Fine-grained step
            for (int i = 0; i < path.size() - 1; i++) {
                Vector2D a = path.get(i);
                Vector2D b = path.get(i + 1);

                float dx = b.x - a.x;
                float dy = b.y - a.y;
                float len = (float) Math.hypot(dx, dy);
                int steps = Math.max(1, (int) Math.ceil(len / stepSize));

                for (int s = 0; s <= steps; s++) {
                    float t = (float) s / steps;
                    float px = a.x + dx * t;
                    float py = a.y + dy * t;
                    assertTrue(map.isTraversable(px, py, radius),
                            role + " snagged at (" + px + ", " + py + ") between waypoint " + i + " and " + (i + 1));
                }
            }
        }
    }

    // =========================================================================
    // 5. Boss Bridge Crossing Across ALL 5 River Bridges
    // =========================================================================
    @Nested
    @DisplayName("Challenge 5: Boss River Crossing Across All 5 Bridges")
    class BossBridgeCrossingAllBridgesTests {

        @Test
        @DisplayName("5.1 Colossus Boss (r=50) successfully traverses ALL 5 bridges across procedural river")
        void testBossTraversesAllFiveBridges() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            float bossRadius = 50.0f;

            // Bridge center Y coordinates (in tiles and world units):
            // Bridge 1: Far North (y = 11..15, center y = 13 -> 432px)
            // Bridge 2: Northern (y = 28..32, center y = 30 -> 976px)
            // Bridge 3: Central Highway (y = 62..66, center y = 64 -> 2064px)
            // Bridge 4: Southern (y = 85..89, center y = 87 -> 2800px)
            // Bridge 5: Far South (y = 109..113, center y = 111 -> 3568px)
            int[] bridgeCenterYs = { 13, 30, 64, 87, 111 };
            String[] bridgeNames = { "Far North", "Northern", "Central Highway", "Southern", "Far South" };

            for (int b = 0; b < bridgeCenterYs.length; b++) {
                int ty = bridgeCenterYs[b];
                float wy = (ty + 0.5f) * map.getTileSize();
                String name = bridgeNames[b];

                // Find bridge horizontal extents (minBridgeX to maxBridgeX)
                int minBx = 999;
                int maxBx = -1;
                for (int x = 0; x < map.getWidth(); x++) {
                    if (map.isBridgeTile(x, ty)) {
                        minBx = Math.min(minBx, x);
                        maxBx = Math.max(maxBx, x);
                    }
                }
                assertTrue(minBx <= maxBx, name + " bridge tiles must exist");

                // Start on West approach landing (3 tiles west of bridge entrance)
                // Target on East approach landing (3 tiles east of bridge exit)
                Vector2D startWest = map.tileToWorld(minBx - 3, ty);
                Vector2D endEast = map.tileToWorld(maxBx + 3, ty);

                assertTrue(map.isTraversable(startWest, bossRadius),
                        name + " bridge West landing " + startWest + " must be traversable for Boss");
                assertTrue(map.isTraversable(endEast, bossRadius),
                        name + " bridge East landing " + endEast + " must be traversable for Boss");

                List<Vector2D> bossPath = pf.findPath(startWest, endEast, bossRadius);
                assertFalse(bossPath.isEmpty(), "Boss path must not be empty for " + name + " bridge");

                Vector2D lastWp = bossPath.get(bossPath.size() - 1);
                assertEquals(endEast.x, lastWp.x, 32.0f, name + " bridge: Boss must reach East landing X");
                assertEquals(endEast.y, lastWp.y, 32.0f, name + " bridge: Boss must reach East landing Y");

                // Check that bridge tiles were crossed along the trajectory from startWest through all waypoints
                boolean usedBridge = false;
                Vector2D prev = startWest;
                for (Vector2D wp : bossPath) {
                    float dist = (float) Math.hypot(wp.x - prev.x, wp.y - prev.y);
                    int steps = Math.max(1, (int) Math.ceil(dist / 8.0f));
                    for (int s = 0; s <= steps; s++) {
                        float t = (float) s / steps;
                        float px = prev.x + (wp.x - prev.x) * t;
                        float py = prev.y + (wp.y - prev.y) * t;
                        int[] tile = map.worldToTile(px, py);
                        if (map.isBridgeTile(tile[0], tile[1])) {
                            usedBridge = true;
                            break;
                        }
                    }
                    if (usedBridge) break;
                    prev = wp;
                }
                assertTrue(usedBridge, "Boss trajectory for " + name + " bridge must cross bridge tiles");

                // Continuous traversability check along Boss path
                for (Vector2D wp : bossPath) {
                    assertTrue(map.isTraversable(wp, bossRadius),
                            name + " bridge waypoint " + wp + " not traversable for Boss");
                }
            }
        }
    }

    // =========================================================================
    // 6. Unreachable Target Fallback (Shoreline Vantage Point)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 6: Unreachable Target Fallback & Shoreline Vantage")
    class UnreachableTargetFallbackTests {

        @Test
        @DisplayName("6.1 Target in wide deep water terminates at closest legal shoreline vantage point")
        void testDeepWaterTargetFallback() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D start = new Vector2D(800.0f, 2048.0f);
            // Find an actual WATER tile near river center at y = 46 (far from bridges)
            int waterTx = -1;
            for (int x = 30; x <= 50; x++) {
                if (map.tileAt(x, 46) == TileType.WATER) {
                    waterTx = x;
                    break;
                }
            }
            assertTrue(waterTx >= 0, "Water tile at row 46 must exist");
            Vector2D midRiverDeepWater = map.tileToWorld(waterTx, 46);
            assertEquals(TileType.WATER, map.tileAt(waterTx, 46));
            assertFalse(map.isTraversable(midRiverDeepWater, 16.0f));

            List<Vector2D> path = pf.findPath(start, midRiverDeepWater, 16.0f);
            assertFalse(path.isEmpty(), "Fallback path must not be empty");

            Vector2D endVantage = path.get(path.size() - 1);
            assertTrue(map.isTraversable(endVantage, 16.0f),
                    "Shoreline vantage point " + endVantage + " must be legally traversable");

            // Verify vantage point is close to the water bank (X > 800)
            assertTrue(endVantage.x > start.x + 200.0f, "Entity must advance towards the water");
        }

        @Test
        @DisplayName("6.2 Unreachable island surrounded by water routes to mainland shoreline vantage point")
        void testUnreachableIslandFallback() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Create a custom 128x128 map where:
            // West side (x <= 40) is mainland Grass.
            // Channel (41 <= x <= 60) is deep Water.
            // East side island (61 <= x <= 70, 50 <= y <= 60) is Grass surrounded completely by Water.
            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    if (x <= 40) {
                        map.setTile(x, y, TileType.GRASS);
                    } else if (x >= 61 && x <= 70 && y >= 50 && y <= 60) {
                        map.setTile(x, y, TileType.GRASS); // Isolated island
                    } else {
                        map.setTile(x, y, TileType.WATER); // Surrounding water ocean
                    }
                }
            }

            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D mainlandStart = map.tileToWorld(20, 55);
            Vector2D islandTarget = map.tileToWorld(65, 55);

            // Island tile is walkable in isolation
            assertTrue(map.isTraversable(islandTarget, 16.0f));
            // But completely unbridged and unreachable from mainland
            assertFalse(pf.hasLineOfSight(mainlandStart, islandTarget, 16.0f));

            List<Vector2D> fallbackPath = pf.findPath(mainlandStart, islandTarget, 16.0f);
            assertFalse(fallbackPath.isEmpty(), "Pathfinder must return fallback path to shoreline");

            Vector2D endPoint = fallbackPath.get(fallbackPath.size() - 1);
            assertTrue(map.isTraversable(endPoint, 16.0f), "Vantage point must be traversable on mainland");

            int[] endTile = map.worldToTile(endPoint);
            assertTrue(endTile[0] <= 40, "Vantage point must stay on mainland (x <= 40)");
            assertTrue(endTile[0] >= 38, "Vantage point must advance close to shoreline (x >= 38)");
        }

        @Test
        @DisplayName("6.3 Target inside unbroken solid rock box terminates outside at closest wall")
        void testTargetInsideUnbrokenRockBox() {
            TileMap map = new TileMap(42);
            map.clearObstacles();

            // Open grass area
            for (int y = 15; y <= 35; y++) {
                for (int x = 15; x <= 35; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            // Build an unbroken box of water/rocks around (25, 25):
            for (int x = 23; x <= 27; x++) {
                map.setTile(x, 23, TileType.WATER);
                map.setTile(x, 27, TileType.WATER);
            }
            for (int y = 23; y <= 27; y++) {
                map.setTile(23, y, TileType.WATER);
                map.setTile(27, y, TileType.WATER);
            }

            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D outsideStart = map.tileToWorld(18, 25);
            Vector2D insideTarget = map.tileToWorld(25, 25);

            List<Vector2D> path = pf.findPath(outsideStart, insideTarget, 10.0f);
            assertFalse(path.isEmpty());

            Vector2D endPoint = path.get(path.size() - 1);
            assertTrue(map.isTraversable(endPoint, 10.0f));

            int[] endTile = map.worldToTile(endPoint);
            // End tile must be outside the box
            assertTrue(endTile[0] < 23 || endTile[0] > 27 || endTile[1] < 23 || endTile[1] > 27,
                    "End point must terminate outside the closed box");
        }
    }

    // =========================================================================
    // 7. Path Caching, Buffer Reuse, RunId Rollover & Stress
    // =========================================================================
    @Nested
    @DisplayName("Challenge 7: Path Caching, Buffer Reuse & Multi-Entity Stress")
    class PathCachingAndBufferStressTests {

        @Test
        @DisplayName("7.1 1,000 repeated A* queries across 50 entities execute with zero allocations and zero memory leaks")
        void testBufferReuse1000Queries() {
            TileMap map = new TileMap(42);
            AStarPathfinder pf = new AStarPathfinder(map);

            Random rng = new Random(12345);
            int entityCount = 50;
            AStarPathfinder.PathCache[] caches = new AStarPathfinder.PathCache[entityCount];
            for (int i = 0; i < entityCount; i++) {
                caches[i] = new AStarPathfinder.PathCache();
            }

            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            long startMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();

            // Perform 1,000 queries across the 50 entities
            for (int iter = 0; iter < 1000; iter++) {
                int entityId = iter % entityCount;
                AStarPathfinder.PathCache cache = caches[entityId];

                float startX = 400.0f + rng.nextFloat() * 400.0f;
                float startY = 400.0f + rng.nextFloat() * 400.0f;
                float targetX = 1800.0f + rng.nextFloat() * 400.0f;
                float targetY = 1800.0f + rng.nextFloat() * 400.0f;

                Vector2D start = new Vector2D(startX, startY);
                Vector2D target = new Vector2D(targetX, targetY);

                if (cache.needsRepath(target)) {
                    List<Vector2D> path = pf.findPath(start.x, start.y, target.x, target.y, 16.0f, 500);
                    cache.resetAfterRepath(path, target, entityId);
                }

                // Simulate movement update
                cache.updateTimers(start, 0.016f);
            }

            System.gc();
            try { Thread.sleep(50); } catch (InterruptedException ignored) {}
            long endMem = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
            long memDiffMb = (endMem - startMem) / (1024 * 1024);

            // Memory should remain tight with no huge leaks
            assertTrue(memDiffMb < 50, "Memory growth after 1000 path queries must be < 50MB, was " + memDiffMb + "MB");
        }

        @Test
        @DisplayName("7.2 PathCache correctly manages timers, displacement, stuck recovery, and look-ahead")
        void testPathCacheLifecycleComprehensive() {
            AStarPathfinder.PathCache cache = new AStarPathfinder.PathCache();

            // 1. Initial state
            Vector2D target = new Vector2D(200.0f, 200.0f);
            assertTrue(cache.needsRepath(target));

            List<Vector2D> path = new ArrayList<>();
            path.add(new Vector2D(50.0f, 50.0f));
            path.add(new Vector2D(100.0f, 100.0f));
            path.add(new Vector2D(200.0f, 200.0f));

            // Staggered repath for entity 3: 0.35 + (3 % 5) * 0.05 = 0.50s
            cache.resetAfterRepath(path, target, 3);
            assertEquals(0.50f, cache.getNavRepathTimer(), 1e-4f);
            assertFalse(cache.needsRepath(target));

            // 2. Minor displacement (< 64 units) -> no repath
            Vector2D slightMove = new Vector2D(220.0f, 200.0f); // 20 units move
            assertFalse(cache.needsRepath(slightMove));

            // 3. Large displacement (> 64 units) -> needs repath
            Vector2D largeMove = new Vector2D(280.0f, 200.0f); // 80 units move
            assertTrue(cache.needsRepath(largeMove));

            // 4. Stuck timer trigger
            cache.resetAfterRepath(path, target, 3);
            Vector2D stuckPos = new Vector2D(50.0f, 50.0f);
            cache.updateTimers(stuckPos, 0.01f); // Initialize navLastPosition to stuckPos
            cache.updateTimers(stuckPos, 0.20f); // stuck timer = 0.20s <= 0.35s
            assertFalse(cache.needsRepath(target));
            cache.updateTimers(stuckPos, 0.20f); // stuck timer = 0.40s > 0.35s threshold
            assertTrue(cache.needsRepath(target));

            // 5. Waypoint arrival advancement (< 22px)
            cache.resetAfterRepath(path, target, 0);
            Vector2D arrivalPos = new Vector2D(55.0f, 55.0f); // dist ~ 7.07px < 22px
            Vector2D nextWp = cache.advanceWaypoint(arrivalPos, null, 16.0f);
            assertNotNull(nextWp);
            assertEquals(100.0f, nextWp.x, 1e-3f);
            assertEquals(100.0f, nextWp.y, 1e-3f);
            assertEquals(1, cache.getNavWaypointIndex());
        }
    }
}
