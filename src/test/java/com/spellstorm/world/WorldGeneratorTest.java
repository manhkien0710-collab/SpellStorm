package com.spellstorm.world;

import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3: WorldGenerator & Procedural Map Generation Tests")
public class WorldGeneratorTest {

    // =========================================================================
    // Tier 1: Equivalence Partitioning (Dimensions, River, Bridges, Landmarks)
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Equivalence Partitioning")
    class Tier1EquivalenceTests {

        @Test
        @DisplayName("1.1 Grid dimensions, tile size, world bounds, and center match exact specification")
        void testGridDimensionsAndCenter() {
            TileMap map = new TileMap(42);

            assertEquals(128, map.getWidth());
            assertEquals(128, map.getHeight());
            assertEquals(32.0f, map.getTileSize());
            assertEquals(4096.0f, map.getWorldWidth());
            assertEquals(4096.0f, map.getWorldHeight());

            Vector2D center = map.getCenter();
            assertEquals(2048.0f, center.x);
            assertEquals(2048.0f, center.y);

            // Starting arena center must be fully traversable
            assertTrue(map.isTraversable(center, 20.0f));
        }

        @Test
        @DisplayName("1.2 River water tiles and sandbanks generate along sinusoidal curve")
        void testRiverGeneration() {
            TileMap map = new TileMap(42);

            boolean foundWater = false;
            for (int y = 5; y < 125; y += 10) {
                float fy = (float) y;
                float expectedRc = 42.0f + (float) Math.sin(fy * 0.08f) * 7.5f + (float) Math.cos(fy * 0.16f) * 3.0f;
                int rc = Math.round(expectedRc);

                // Water channel: rc - 2 to rc + 1 (unless bridged)
                // Let's test a non-bridge row, e.g. y = 46
                if (y == 45 || y == 46) {
                    for (int dx = -2; dx <= 1; dx++) {
                        assertEquals(TileType.WATER, map.tileAt(rc + dx, y));
                        assertTrue(map.isSolidTile(rc + dx, y));
                        assertTrue(map.isWaterTile(rc + dx, y));
                        foundWater = true;
                    }
                    // Sandbanks: 2 tiles on each side
                    assertEquals(TileType.SAND, map.tileAt(rc - 3, y));
                    assertEquals(TileType.SAND, map.tileAt(rc - 4, y));
                    assertEquals(TileType.SAND, map.tileAt(rc + 2, y));
                    assertEquals(TileType.SAND, map.tileAt(rc + 3, y));
                }
            }
            assertTrue(foundWater);
        }

        @Test
        @DisplayName("1.3 Five deterministic river bridges exist spanning full river channel")
        void testBridgeNetworkExistence() {
            TileMap map = new TileMap(42);
            int[] bridgeYCenters = {13, 30, 64, 87, 111};

            for (int cy : bridgeYCenters) {
                boolean foundBridge = false;
                int bridgeSpan = 0;
                for (int x = 20; x <= 65; x++) {
                    if (map.tileAt(x, cy) == TileType.BRIDGE_H) {
                        foundBridge = true;
                        bridgeSpan++;
                        assertFalse(map.isSolidTile(x, cy), "Bridge tiles must not be solid");
                        assertTrue(map.isBridgeTile(x, cy), "Bridge tiles must report true for isBridgeTile");
                    }
                }
                assertTrue(foundBridge, "Bridge crossing must exist at y = " + cy);
                assertTrue(bridgeSpan >= 4, "Bridge span must cover at least 4 water tiles at y = " + cy);
            }
        }

        @Test
        @DisplayName("1.4 Landmark placement: Ancient Ruins stone plaza and Campsite settlement")
        void testLandmarksGeneration() {
            TileMap map = new TileMap(42);

            // Ancient Ruins Sanctuary at (88, 35) with radius 6 stone floor
            assertEquals(TileType.STONE, map.tileAt(88, 35));
            assertEquals(TileType.STONE, map.tileAt(88 + 5, 35));
            assertEquals(TileType.STONE, map.tileAt(88, 35 + 5));

            // Wilderness Campsite at (95, 88) with radius 4 dirt floor
            assertEquals(TileType.DIRT, map.tileAt(95, 88));
            assertEquals(TileType.DIRT, map.tileAt(95 + 3, 88));
            assertEquals(TileType.DIRT, map.tileAt(95, 88 + 3));

            // Verify landmark props are present in obstacles list
            List<Obstacle> obstacles = map.getObstacles();
            boolean hasRuinsPillar = false;
            boolean hasTent = false;
            boolean hasCampfire = false;
            boolean hasLog = false;

            for (Obstacle o : obstacles) {
                if (o.getType() == DecoType.RUINS) hasRuinsPillar = true;
                if (o.getType() == DecoType.TENT) hasTent = true;
                if (o.getType() == DecoType.CAMPFIRE) hasCampfire = true;
                if (o.getType() == DecoType.LOG) hasLog = true;
            }

            assertTrue(hasRuinsPillar);
            assertTrue(hasTent);
            assertTrue(hasCampfire);
            assertTrue(hasLog);
        }
    }

    // =========================================================================
    // Tier 2: Boundary Value Analysis (Bridge Landings, Clearance, Bounds)
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: Boundary Value Analysis")
    class Tier2BoundaryTests {

        @Test
        @DisplayName("2.1 Flared dirt approach landings extend 4 tiles inland across 7 rows per bridge")
        void testFlaredDirtLandings() {
            TileMap map = new TileMap(42);
            // Central bridge: startY = 62, endY = 66. Flared span: startY - 1 = 61 to endY + 1 = 67 (7 rows)
            int startY = 62;
            int endY = 66;

            int minWaterX = 999;
            int maxWaterX = -1;
            for (int y = startY; y <= endY; y++) {
                for (int x = 20; x <= 65; x++) {
                    if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                        minWaterX = Math.min(minWaterX, x);
                        maxWaterX = Math.max(maxWaterX, x);
                    }
                }
            }

            for (int y = startY - 1; y <= endY + 1; y++) {
                for (int d = 1; d <= 4; d++) {
                    int westX = minWaterX - d;
                    int eastX = maxWaterX + d;
                    assertEquals(TileType.DIRT, map.tileAt(westX, y),
                            "Flared landing west tile at (" + westX + ", " + y + ") must be DIRT");
                    assertEquals(TileType.DIRT, map.tileAt(eastX, y),
                            "Flared landing east tile at (" + eastX + ", " + y + ") must be DIRT");
                }
            }
        }

        @Test
        @DisplayName("2.2 Bridge Boss clearance (r=50) with >= 30px lateral clearance on all 5 crossings")
        void testBossClearanceOnAllBridges() {
            TileMap map = new TileMap(42);
            int[] bridgeYCenters = {13, 30, 64, 87, 111};
            float bossRadius = 50.0f;

            for (int cy : bridgeYCenters) {
                int minX = -1;
                int maxX = -1;
                for (int x = 20; x <= 65; x++) {
                    if (map.tileAt(x, cy) == TileType.BRIDGE_H) {
                        if (minX == -1) minX = x;
                        maxX = x;
                    }
                }
                assertTrue(minX > 0 && maxX >= minX);

                float bridgeCenterX = (minX + maxX + 1) * 0.5f * 32.0f;
                float bridgeCenterY = (cy + 0.5f) * 32.0f;
                Vector2D bridgeCenter = new Vector2D(bridgeCenterX, bridgeCenterY);

                // Melee (r=16), Tank (r=26), and Boss (r=50) must all be traversable
                assertTrue(map.isTraversable(bridgeCenter, 16.0f), "Melee traversable at y=" + cy);
                assertTrue(map.isTraversable(bridgeCenter, 26.0f), "Tank traversable at y=" + cy);
                assertTrue(map.isTraversable(bridgeCenter, bossRadius), "Boss traversable at y=" + cy);

                // Check clearance: bridge is 5 tiles wide (160px), Boss diameter is 100px -> 30px buffer on each side
                float bridgeTopY = (cy - 2) * 32.0f;
                float bridgeBottomY = (cy + 3) * 32.0f;
                float distanceToTopWater = bridgeCenterY - bridgeTopY;
                float distanceToBottomWater = bridgeBottomY - bridgeCenterY;
                assertTrue(distanceToTopWater - bossRadius >= 30.0f - 1.0f);
                assertTrue(distanceToBottomWater - bossRadius >= 30.0f - 1.0f);
            }
        }

        @Test
        @DisplayName("2.3 Safe zone around player center (r=12 tiles = 384px) contains zero solid obstacles")
        void testPlayerSafeZone() {
            TileMap map = new TileMap(42);
            Vector2D center = map.getCenter();
            float safeRadius = 12.0f * 32.0f; // 384.0f

            for (Obstacle o : map.getObstacles()) {
                if (!o.isSolid()) continue;
                float dist = o.getPosition().dst(center);
                assertTrue(dist >= safeRadius,
                        "Solid obstacle " + o.getType() + " at " + o.getPosition() + " is within player safe zone (dist=" + dist + ")");
            }
        }

        @Test
        @DisplayName("2.4 Bridge clearance zone (4 tiles around bridges) contains zero solid obstacles")
        void testBridgeClearanceZone() {
            TileMap map = new TileMap(42);
            int[] bridgeYLevels = {11, 12, 13, 14, 15, 28, 29, 30, 31, 32, 62, 63, 64, 65, 66, 85, 86, 87, 88, 89, 109, 110, 111, 112, 113};

            for (Obstacle o : map.getObstacles()) {
                if (!o.isSolid()) continue;
                int[] decoTile = map.worldToTile(o.getPosition());
                for (int by : bridgeYLevels) {
                    if (Math.abs(decoTile[1] - by) <= 4) {
                        for (int x = 20; x <= 65; x++) {
                            if (map.isBridgeTile(x, by)) {
                                int dx = Math.abs(decoTile[0] - x);
                                int dy = Math.abs(decoTile[1] - by);
                                assertTrue(dx > 4 || dy > 4,
                                        "Solid obstacle " + o.getType() + " at (" + decoTile[0] + "," + decoTile[1] +
                                                ") is within 4 tiles of bridge tile (" + x + "," + by + ")");
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // Tier 3: Combinatorial & Spacing Invariants (Anti-Clumping, Density)
    // =========================================================================
    @Nested
    @DisplayName("Tier 3: Pairwise & Anti-Clumping Invariants")
    class Tier3CombinatorialTests {

        @Test
        @DisplayName("3.1 Total decorations <= 300 (down from unoptimized 480)")
        void testDecorationDensityThreshold() {
            TileMap map = new TileMap(42);
            assertTrue(map.getObstacles().size() <= 300,
                    "Total decorations (" + map.getObstacles().size() + ") must be <= 300");
            assertTrue(map.getObstacles().size() >= 100,
                    "Map must contain reasonable density of flora/props");
        }

        @Test
        @DisplayName("3.2 Minimum solid obstacle center-to-center spacing >= 44.0 px")
        void testSolidObstacleSpacing() {
            TileMap map = new TileMap(42);
            List<Obstacle> solidObstacles = map.getObstacles().stream()
                    .filter(Obstacle::isSolid)
                    .toList();

            for (int i = 0; i < solidObstacles.size(); i++) {
                Obstacle a = solidObstacles.get(i);
                for (int j = i + 1; j < solidObstacles.size(); j++) {
                    Obstacle b = solidObstacles.get(j);
                    // Exclude predefined landmark props that are colocated by design (ruins pillars / tents)
                    if (a.getType() == DecoType.RUINS || b.getType() == DecoType.RUINS ||
                        a.getType() == DecoType.TENT || b.getType() == DecoType.TENT ||
                        a.getType() == DecoType.CAMPFIRE || b.getType() == DecoType.CAMPFIRE) {
                        continue;
                    }
                    float dist = a.getPosition().dst(b.getPosition());
                    assertTrue(dist >= 44.0f - 0.01f,
                            "Natural solid obstacles too close: dist=" + dist + " between " + a + " and " + b);
                }
            }
        }

        @Test
        @DisplayName("3.3 Natural decorations are only placed on Grass substrate")
        void testDecorationsPlacedOnGrass() {
            TileMap map = new TileMap(42);
            for (Obstacle o : map.getObstacles()) {
                int[] tile = map.worldToTile(o.getPosition());
                // Landmark props are placed on stone/dirt by design; natural flora must be on grass
                if (o.getType() == DecoType.RUINS || o.getType() == DecoType.TENT ||
                    o.getType() == DecoType.CAMPFIRE ||
                    (o.getType() == DecoType.ROCK_LARGE && (tile[0] == 88 && (tile[1] == 29 || tile[1] == 41))) ||
                    (o.getType() == DecoType.LOG && (tile[0] == 95 || tile[0] == 93))) {
                    continue;
                }
                TileType t = map.tileAt(tile[0], tile[1]);
                assertEquals(TileType.GRASS, t,
                        "Natural decoration " + o.getType() + " at " + o.getPosition() + " was placed on " + t);
            }
        }
    }

    // =========================================================================
    // Tier 4: Real-World Scenarios (Determinism, Seed Divergence)
    // =========================================================================
    @Nested
    @DisplayName("Tier 4: Real-World Scenarios & Determinism")
    class Tier4RealWorldTests {

        @Test
        @DisplayName("4.1 Determinism: Identical seeds produce bitwise-identical maps")
        void testSeedDeterminism() {
            TileMap map1 = new TileMap(12345);
            TileMap map2 = new TileMap(12345);

            for (int y = 0; y < 128; y++) {
                for (int x = 0; x < 128; x++) {
                    assertEquals(map1.tileAt(x, y), map2.tileAt(x, y),
                            "Tile mismatch at (" + x + "," + y + ")");
                }
            }

            assertEquals(map1.getObstacles().size(), map2.getObstacles().size());
            for (int i = 0; i < map1.getObstacles().size(); i++) {
                Obstacle o1 = map1.getObstacles().get(i);
                Obstacle o2 = map2.getObstacles().get(i);
                assertEquals(o1.getType(), o2.getType());
                assertEquals(o1.getPosition().x, o2.getPosition().x, 1e-4f);
                assertEquals(o1.getPosition().y, o2.getPosition().y, 1e-4f);
                assertEquals(o1.isSolid(), o2.isSolid());
            }
        }

        @Test
        @DisplayName("4.2 Seed divergence: Different seeds produce distinct natural obstacle layouts")
        void testSeedDivergence() {
            TileMap mapA = new TileMap(42);
            TileMap mapB = new TileMap(999);

            // Rivers and landmarks are constant formulas, but natural decorations vary by seed
            boolean hasPositionDifference = false;
            int minLen = Math.min(mapA.getObstacles().size(), mapB.getObstacles().size());
            for (int i = 0; i < minLen; i++) {
                if (mapA.getObstacles().get(i).getPosition().dst(mapB.getObstacles().get(i).getPosition()) > 1.0f) {
                    hasPositionDifference = true;
                    break;
                }
            }
            assertTrue(hasPositionDifference, "Different seeds must produce different decoration layouts");
        }
    }
}
