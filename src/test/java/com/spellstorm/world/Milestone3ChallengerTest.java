package com.spellstorm.world;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical challenger test suite for Milestone 3:
 * World Generation, Bridges, Safe-Zone, Solid Spacing & Frustum Culling.
 *
 * Authored by m3_challenger_1 to independently and rigorously stress-test:
 * 1. Sinusoidal river continuity across y in [0, 127] and river center bounds [30, 55].
 * 2. 5-crossing bridge network at y in [11, 15], [28, 32], [62, 66], [85, 89], [109, 113].
 * 3. Flared dirt approach landings (4 tiles inland, 7 rows high per bridge).
 * 4. Colossal Boss (r=50.0) continuous lateral traversability across all 5 bridges.
 * 5. Player safe-zone clearance (zero solid obstacles within 384px of (2048, 2048)).
 * 6. Bridge obstacle clearance (zero solid obstacles within 4 tiles of any bridge).
 * 7. Anti-clumping solid spacing >= 44.0px and total decoration budget <= 300.
 * 8. Frustum culling margin (+-2 tiles) across extreme zooms [0.75, 2.25] and corner positions.
 */
@DisplayName("Milestone 3 Challenger: WorldGen & Frustum Culling Empirical Stress Tests")
public class Milestone3ChallengerTest {

    private static final int GRID_SIZE = 128;
    private static final float TILE_SIZE = 32.0f;
    private static final float WORLD_SIZE = GRID_SIZE * TILE_SIZE; // 4096.0f
    private static final Vector2D WORLD_CENTER = new Vector2D(2048.0f, 2048.0f);

    private static final int[][] BRIDGE_RANGES = {
        {11, 15}, // Far North
        {28, 32}, // Northern
        {62, 66}, // Central Highway
        {85, 89}, // Southern
        {109, 113} // Far South
    };

    @BeforeAll
    static void initHeadless() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Challenge 1: River Channel Continuity & Center Bounds
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: River Channel Continuity & Bounds")
    class RiverChannelChallenge {

        @Test
        @DisplayName("1.1 Mathematical river center stays strictly within [30, 55] for all y in [0, 127]")
        void testRiverCenterBounds() {
            float minRc = Float.MAX_VALUE;
            float maxRc = -Float.MAX_VALUE;

            for (int y = 0; y < GRID_SIZE; y++) {
                float fy = (float) y;
                float riverCenter = 42.0f + (float) Math.sin(fy * 0.08f) * 7.5f + (float) Math.cos(fy * 0.16f) * 3.0f;
                minRc = Math.min(minRc, riverCenter);
                maxRc = Math.max(maxRc, riverCenter);

                assertTrue(riverCenter >= 30.0f, "River center below 30.0 at y=" + y + ": " + riverCenter);
                assertTrue(riverCenter <= 55.0f, "River center above 55.0 at y=" + y + ": " + riverCenter);
            }

            // Verify observed bounds are well within the margin
            assertTrue(minRc > 31.0f, "Observed min river center: " + minRc);
            assertTrue(maxRc < 53.0f, "Observed max river center: " + maxRc);
        }

        @Test
        @DisplayName("1.2 Raw river channel has continuous water across all 128 rows without breaks")
        void testRawRiverContinuity() {
            TileMap map = new TileMap(42);
            // Clear and only place river
            for (int y = 0; y < GRID_SIZE; y++) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }
            WorldGenerator.placeRiver(map, 42);

            for (int y = 0; y < GRID_SIZE; y++) {
                int waterCount = 0;
                int firstWaterX = -1;
                int lastWaterX = -1;

                for (int x = 0; x < GRID_SIZE; x++) {
                    if (map.tileAt(x, y) == TileType.WATER) {
                        waterCount++;
                        if (firstWaterX == -1) firstWaterX = x;
                        lastWaterX = x;
                    }
                }

                // Exactly 4 water tiles per row
                assertEquals(4, waterCount, "Row y=" + y + " must contain exactly 4 water tiles");
                // Contiguous water channel: lastWaterX - firstWaterX + 1 == 4
                assertEquals(4, lastWaterX - firstWaterX + 1, "Water channel on row y=" + y + " must be contiguous");

                // Flanking sandbanks: 2 sand tiles on west, 2 sand tiles on east
                assertEquals(TileType.SAND, map.tileAt(firstWaterX - 1, y), "West sandbank 1 on row y=" + y);
                assertEquals(TileType.SAND, map.tileAt(firstWaterX - 2, y), "West sandbank 2 on row y=" + y);
                assertEquals(TileType.SAND, map.tileAt(lastWaterX + 1, y), "East sandbank 1 on row y=" + y);
                assertEquals(TileType.SAND, map.tileAt(lastWaterX + 2, y), "East sandbank 2 on row y=" + y);
            }
        }

        @Test
        @DisplayName("1.3 Fully generated map preserves continuous water or bridge across all 128 rows")
        void testFullMapRiverChannelIntegrity() {
            TileMap map = new TileMap(42);

            for (int y = 0; y < GRID_SIZE; y++) {
                boolean hasWaterOrBridge = false;
                for (int x = 20; x <= 65; x++) {
                    TileType t = map.tileAt(x, y);
                    if (t == TileType.WATER || t == TileType.BRIDGE_H) {
                        hasWaterOrBridge = true;
                        break;
                    }
                }
                assertTrue(hasWaterOrBridge, "River channel must contain water or bridge on row y=" + y);
            }
        }
    }

    // =========================================================================
    // Challenge 2: 5 Bridges & Flared Dirt Landings Verification
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: 5 Bridges & Flared Landings")
    class BridgesAndLandingsChallenge {

        @Test
        @DisplayName("2.1 All 5 bridges exist at exact designated rows as walkable BRIDGE_H")
        void testBridgesStructure() {
            TileMap map = new TileMap(42);

            for (int i = 0; i < BRIDGE_RANGES.length; i++) {
                int startY = BRIDGE_RANGES[i][0];
                int endY = BRIDGE_RANGES[i][1];

                for (int y = startY; y <= endY; y++) {
                    int bridgeTileCount = 0;
                    for (int x = 20; x <= 65; x++) {
                        if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                            bridgeTileCount++;
                            assertTrue(map.isWalkable(x, y), "Bridge tile at (" + x + "," + y + ") must be walkable");
                            assertTrue(map.isBridgeTile(x, y), "Bridge tile at (" + x + "," + y + ") must report isBridgeTile");
                            assertFalse(map.isWaterTile(x, y), "Bridge tile at (" + x + "," + y + ") must not be water");
                            assertFalse(map.isSolidTile(x, y), "Bridge tile at (" + x + "," + y + ") must not be solid");
                        }
                    }
                    assertTrue(bridgeTileCount >= 4, "Bridge " + i + " on row y=" + y + " must span >= 4 tiles (was " + bridgeTileCount + ")");
                }
            }
        }

        @Test
        @DisplayName("2.2 Flared dirt landings extend 4 tiles inland across 7 rows per bridge without holes")
        void testFlaredDirtLandingsIntegrity() {
            TileMap map = new TileMap(42);

            for (int i = 0; i < BRIDGE_RANGES.length; i++) {
                int startY = BRIDGE_RANGES[i][0];
                int endY = BRIDGE_RANGES[i][1];

                // Find bridge span bounds
                int minBridgeX = 999;
                int maxBridgeX = -1;
                for (int y = startY; y <= endY; y++) {
                    for (int x = 20; x <= 65; x++) {
                        if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                            minBridgeX = Math.min(minBridgeX, x);
                            maxBridgeX = Math.max(maxBridgeX, x);
                        }
                    }
                }
                assertTrue(minBridgeX <= maxBridgeX && minBridgeX >= 20);

                // Flared landings: y in [startY - 1, endY + 1], d in [1, 4]
                for (int y = startY - 1; y <= endY + 1; y++) {
                    for (int d = 1; d <= 4; d++) {
                        int westX = minBridgeX - d;
                        int eastX = maxBridgeX + d;

                        TileType westTile = map.tileAt(westX, y);
                        TileType eastTile = map.tileAt(eastX, y);

                        assertEquals(TileType.DIRT, westTile,
                                "Bridge " + i + " flared west landing at (" + westX + "," + y + ") must be DIRT");
                        assertEquals(TileType.DIRT, eastTile,
                                "Bridge " + i + " flared east landing at (" + eastX + "," + y + ") must be DIRT");
                        assertTrue(map.isWalkable(westX, y));
                        assertTrue(map.isWalkable(eastX, y));
                    }
                }
            }
        }
    }

    // =========================================================================
    // Challenge 3: Colossal Boss Bridge Traversal & Continuous Passage
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: Boss Bridge Traversal & Continuous Passage")
    class BossBridgeTraversalChallenge {

        @Test
        @DisplayName("3.1 Boss (r=50.0) is traversable at bridge geometric center across all 5 crossings")
        void testBossCenterTraversability() {
            TileMap map = new TileMap(42);
            float bossRadius = 50.0f;

            for (int i = 0; i < BRIDGE_RANGES.length; i++) {
                int startY = BRIDGE_RANGES[i][0];
                int endY = BRIDGE_RANGES[i][1];

                int minBridgeX = 999;
                int maxBridgeX = -1;
                for (int y = startY; y <= endY; y++) {
                    for (int x = 20; x <= 65; x++) {
                        if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                            minBridgeX = Math.min(minBridgeX, x);
                            maxBridgeX = Math.max(maxBridgeX, x);
                        }
                    }
                }

                float bridgeCenterX = (minBridgeX + maxBridgeX + 1) * 0.5f * TILE_SIZE;
                float bridgeCenterY = (startY + endY + 1) * 0.5f * TILE_SIZE;
                Vector2D center = new Vector2D(bridgeCenterX, bridgeCenterY);

                assertTrue(map.isTraversable(center, bossRadius),
                        "Boss (r=50) must be traversable at center of bridge " + i + " " + center);
            }
        }

        @Test
        @DisplayName("3.2 Boss (r=50.0) can continuously traverse west-to-east across entire bridge span")
        void testBossContinuousTraversal() {
            TileMap map = new TileMap(42);
            float bossRadius = 50.0f;

            for (int i = 0; i < BRIDGE_RANGES.length; i++) {
                int startY = BRIDGE_RANGES[i][0];
                int endY = BRIDGE_RANGES[i][1];

                int minBridgeX = 999;
                int maxBridgeX = -1;
                for (int y = startY; y <= endY; y++) {
                    for (int x = 20; x <= 65; x++) {
                        if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                            minBridgeX = Math.min(minBridgeX, x);
                            maxBridgeX = Math.max(maxBridgeX, x);
                        }
                    }
                }

                float bridgeCenterY = (startY + endY + 1) * 0.5f * TILE_SIZE;

                // Start from west flared landing inland (minBridgeX - 3) to east flared landing (maxBridgeX + 3)
                float startX = (minBridgeX - 3) * TILE_SIZE;
                float endX = (maxBridgeX + 3) * TILE_SIZE;

                // Step every 4 pixels across the entire bridge crossing
                for (float x = startX; x <= endX; x += 4.0f) {
                    Vector2D pos = new Vector2D(x, bridgeCenterY);
                    assertTrue(map.isTraversable(pos, bossRadius),
                            "Boss (r=50) blocked during continuous traversal of bridge " + i + " at (" + x + ", " + bridgeCenterY + ")");
                }
            }
        }

        @Test
        @DisplayName("3.3 Multi-archetype traversal (Melee r=16, Ranged r=15, Tank r=26, Boss r=50)")
        void testMultiArchetypeTraversability() {
            TileMap map = new TileMap(42);
            float[] archetypes = {15.0f, 16.0f, 26.0f, 50.0f};

            for (int i = 0; i < BRIDGE_RANGES.length; i++) {
                int startY = BRIDGE_RANGES[i][0];
                int endY = BRIDGE_RANGES[i][1];

                int minBridgeX = 999;
                int maxBridgeX = -1;
                for (int y = startY; y <= endY; y++) {
                    for (int x = 20; x <= 65; x++) {
                        if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                            minBridgeX = Math.min(minBridgeX, x);
                            maxBridgeX = Math.max(maxBridgeX, x);
                        }
                    }
                }

                float bridgeCenterX = (minBridgeX + maxBridgeX + 1) * 0.5f * TILE_SIZE;
                float bridgeCenterY = (startY + endY + 1) * 0.5f * TILE_SIZE;

                for (float radius : archetypes) {
                    assertTrue(map.isTraversable(bridgeCenterX, bridgeCenterY, radius),
                            "Radius " + radius + " must be traversable on bridge " + i);
                }
            }
        }

        @Test
        @DisplayName("3.4 Vertical tolerance: Boss maintains traversability within +-25px of bridge centerline")
        void testBossVerticalTolerance() {
            TileMap map = new TileMap(42);
            float bossRadius = 50.0f;

            for (int i = 0; i < BRIDGE_RANGES.length; i++) {
                int startY = BRIDGE_RANGES[i][0];
                int endY = BRIDGE_RANGES[i][1];

                int minBridgeX = 999;
                int maxBridgeX = -1;
                for (int y = startY; y <= endY; y++) {
                    for (int x = 20; x <= 65; x++) {
                        if (map.tileAt(x, y) == TileType.BRIDGE_H) {
                            minBridgeX = Math.min(minBridgeX, x);
                            maxBridgeX = Math.max(maxBridgeX, x);
                        }
                    }
                }

                float bridgeCenterX = (minBridgeX + maxBridgeX + 1) * 0.5f * TILE_SIZE;
                float bridgeCenterY = (startY + endY + 1) * 0.5f * TILE_SIZE;

                // 5 tiles = 160px height. Boss diameter = 100px. Available slack = 60px (+-30px).
                // Test +-25px vertical offset
                assertTrue(map.isTraversable(bridgeCenterX, bridgeCenterY - 25.0f, bossRadius),
                        "Boss traversable at -25px offset on bridge " + i);
                assertTrue(map.isTraversable(bridgeCenterX, bridgeCenterY + 25.0f, bossRadius),
                        "Boss traversable at +25px offset on bridge " + i);
            }
        }
    }

    // =========================================================================
    // Challenge 4: Safe Zone Clearance & Center Openness
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: Safe Zone Clearance")
    class SafeZoneChallenge {

        @ParameterizedTest(name = "Seed {0}: Safe zone zero solid obstacles")
        @ValueSource(longs = {1, 42, 100, 555, 9999, 123456})
        @DisplayName("4.1 Zero solid obstacles within 384px of world center across multiple seeds")
        void testSafeZoneMultiSeed(long seed) {
            TileMap map = new TileMap(seed);
            float safeRadius = 384.0f;

            for (Obstacle o : map.getObstacles()) {
                if (!o.isSolid()) continue;

                float anchorDist = o.getPosition().dst(WORLD_CENTER);
                assertTrue(anchorDist >= safeRadius,
                        "Seed " + seed + ": Solid obstacle " + o.getType() + " anchor at " + o.getPosition() +
                                " is within safe zone (dist=" + anchorDist + " < " + safeRadius + ")");

                float colDist = o.getCollisionCenter().dst(WORLD_CENTER);
                assertTrue(colDist >= safeRadius - o.getCollisionRadius(),
                        "Seed " + seed + ": Solid obstacle " + o.getType() + " collision circle overlaps safe zone");
            }

            // World center must be traversable for all entity sizes
            assertTrue(map.isTraversable(WORLD_CENTER, 16.0f));
            assertTrue(map.isTraversable(WORLD_CENTER, 26.0f));
            assertTrue(map.isTraversable(WORLD_CENTER, 50.0f));
        }
    }

    // =========================================================================
    // Challenge 5: Bridge Clearance Zone (4 Tiles Around Bridges)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 5: Bridge Clearance Zone")
    class BridgeClearanceChallenge {

        @ParameterizedTest(name = "Seed {0}: Bridge clearance zone >= 4 tiles")
        @ValueSource(longs = {1, 42, 100, 555, 9999, 123456})
        @DisplayName("5.1 Zero solid obstacles within 4 tiles of any bridge tile")
        void testBridgeClearanceMultiSeed(long seed) {
            TileMap map = new TileMap(seed);

            List<int[]> bridgeTiles = new ArrayList<>();
            for (int y = 0; y < GRID_SIZE; y++) {
                for (int x = 0; x < GRID_SIZE; x++) {
                    if (map.isBridgeTile(x, y)) {
                        bridgeTiles.add(new int[]{x, y});
                    }
                }
            }
            assertFalse(bridgeTiles.isEmpty());

            for (Obstacle o : map.getObstacles()) {
                if (!o.isSolid()) continue;
                int[] decoTile = map.worldToTile(o.getPosition());

                for (int[] bt : bridgeTiles) {
                    int dx = Math.abs(decoTile[0] - bt[0]);
                    int dy = Math.abs(decoTile[1] - bt[1]);
                    int chebyshev = Math.max(dx, dy);

                    assertTrue(chebyshev > 4,
                            "Seed " + seed + ": Solid obstacle " + o.getType() + " at tile (" + decoTile[0] + "," + decoTile[1] +
                                    ") is too close to bridge tile (" + bt[0] + "," + bt[1] + ") (chebyshev=" + chebyshev + " <= 4)");
                }
            }
        }
    }

    // =========================================================================
    // Challenge 6: Solid Spacing & Decoration Budget
    // =========================================================================
    @Nested
    @DisplayName("Challenge 6: Solid Spacing & Decoration Budget")
    class SolidSpacingAndBudgetChallenge {

        @ParameterizedTest(name = "Seed {0}: Decoration budget and spacing")
        @ValueSource(longs = {1, 42, 100, 555, 9999, 123456})
        @DisplayName("6.1 Total decorations <= 300 and solid spacing >= 44.0px")
        void testDecorationBudgetAndSpacingMultiSeed(long seed) {
            TileMap map = new TileMap(seed);
            List<Obstacle> obstacles = map.getObstacles();

            // Total decorations threshold
            assertTrue(obstacles.size() <= 300,
                    "Seed " + seed + ": Total decorations (" + obstacles.size() + ") exceeds 300");
            assertTrue(obstacles.size() >= 100,
                    "Seed " + seed + ": Total decorations (" + obstacles.size() + ") below 100");

            // Filter natural solid obstacles (excluding landmark props)
            List<Obstacle> naturalSolids = obstacles.stream()
                    .filter(Obstacle::isSolid)
                    .filter(o -> o.getType() != DecoType.RUINS &&
                                 o.getType() != DecoType.TENT &&
                                 o.getType() != DecoType.CAMPFIRE &&
                                 !(o.getType() == DecoType.ROCK_LARGE &&
                                         (map.worldToTile(o.getPosition())[0] == 88 &&
                                                 (map.worldToTile(o.getPosition())[1] == 29 || map.worldToTile(o.getPosition())[1] == 41))))
                    .toList();

            for (int i = 0; i < naturalSolids.size(); i++) {
                Obstacle a = naturalSolids.get(i);
                for (int j = i + 1; j < naturalSolids.size(); j++) {
                    Obstacle b = naturalSolids.get(j);
                    float dist = a.getPosition().dst(b.getPosition());
                    assertTrue(dist >= 44.0f - 1e-2f,
                            "Seed " + seed + ": Natural solids too close (dist=" + dist + " < 44.0): " + a + " vs " + b);
                }
            }
        }

        @Test
        @DisplayName("6.2 Conversion of clumped obstacles into passable BUSH succeeds")
        void testPassableBushConversion() {
            TileMap map = new TileMap(42);
            long bushCount = map.getObstacles().stream()
                    .filter(o -> o.getType() == DecoType.BUSH)
                    .count();

            // Bush count must be non-zero and passable
            assertTrue(bushCount > 10, "Bushes must be populated from random roll and anti-clumping conversion");
            for (Obstacle o : map.getObstacles()) {
                if (o.getType() == DecoType.BUSH) {
                    assertFalse(o.isSolid(), "Bush must be non-solid");
                    assertEquals(0.0f, o.getCollisionRadius(), 1e-4f);
                }
            }
        }

        @Test
        @DisplayName("6.3 Natural decorations are placed only on GRASS tiles")
        void testNaturalDecorationsSubstrate() {
            TileMap map = new TileMap(42);
            for (Obstacle o : map.getObstacles()) {
                // Landmark props are on stone/dirt by design
                if (o.getType() == DecoType.RUINS || o.getType() == DecoType.TENT ||
                    o.getType() == DecoType.CAMPFIRE ||
                    o.getType() == DecoType.LOG) {
                    continue;
                }
                int[] tile = map.worldToTile(o.getPosition());
                // Rock large at ruins is also landmark
                if (o.getType() == DecoType.ROCK_LARGE && tile[0] == 88) {
                    continue;
                }

                TileType substrate = map.tileAt(tile[0], tile[1]);
                assertEquals(TileType.GRASS, substrate,
                        "Natural decoration " + o.getType() + " at (" + tile[0] + "," + tile[1] + ") was placed on " + substrate);
            }
        }
    }

    // =========================================================================
    // Challenge 7: Frustum Culling Margin & Corner Clamping
    // =========================================================================
    @Nested
    @DisplayName("Challenge 7: Frustum Culling Margin & Clamping")
    class FrustumCullingChallenge {

        private final float[] TEST_ZOOMS = {0.75f, 1.0f, 1.25f, 1.35f, 1.5f, 2.0f, 2.25f};
        private final Vector2D[] TEST_POSITIONS = {
            new Vector2D(0.0f, 0.0f),               // Top-Left corner
            new Vector2D(4096.0f, 0.0f),            // Top-Right corner
            new Vector2D(0.0f, 4096.0f),            // Bottom-Left corner
            new Vector2D(4096.0f, 4096.0f),         // Bottom-Right corner
            new Vector2D(2048.0f, 2048.0f),         // World center
            new Vector2D(1024.0f, 3072.0f),         // Arbitrary interior point
            new Vector2D(-500.0f, -500.0f),         // Negative extreme out-of-bounds
            new Vector2D(5000.0f, 5000.0f)          // Positive extreme out-of-bounds
        };

        @Test
        @DisplayName("7.1 Visible tile range stays strictly within [0, 127] across all corner positions and zoom levels")
        void testTileRangeBounds() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);

            for (Vector2D pos : TEST_POSITIONS) {
                for (float zoom : TEST_ZOOMS) {
                    camera.setZoom(zoom);
                    camera.setCenter(pos.x, pos.y);

                    int[] range = map.getVisibleTileRange(camera);
                    int minTx = range[0];
                    int maxTx = range[1];
                    int minTy = range[2];
                    int maxTy = range[3];

                    assertTrue(minTx >= 0, "minTx < 0 at pos=" + pos + " zoom=" + zoom + ": " + minTx);
                    assertTrue(maxTx <= 127, "maxTx > 127 at pos=" + pos + " zoom=" + zoom + ": " + maxTx);
                    assertTrue(minTy >= 0, "minTy < 0 at pos=" + pos + " zoom=" + zoom + ": " + minTy);
                    assertTrue(maxTy <= 127, "maxTy > 127 at pos=" + pos + " zoom=" + zoom + ": " + maxTy);
                    assertTrue(minTx <= maxTx, "minTx > maxTx at pos=" + pos + " zoom=" + zoom);
                    assertTrue(minTy <= maxTy, "minTy > maxTy at pos=" + pos + " zoom=" + zoom);
                }
            }
        }

        @Test
        @DisplayName("7.2 Visible tile range provides +-2 tile margin expansion over camera visibleBounds")
        void testTileRangeMarginExpansion() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);

            // Interior position where margin is not clamped by world border
            camera.setCenter(2048.0f, 2048.0f);
            camera.setZoom(1.0f);

            Rectangle vis = camera.getVisibleBounds();
            int[] range = map.getVisibleTileRange(camera);

            int expectedMinTx = (int) Math.floor(vis.x / TILE_SIZE) - 2;
            int expectedMaxTx = (int) Math.ceil((vis.x + vis.width) / TILE_SIZE) + 2;
            int expectedMinTy = (int) Math.floor(vis.y / TILE_SIZE) - 2;
            int expectedMaxTy = (int) Math.ceil((vis.y + vis.height) / TILE_SIZE) + 2;

            assertEquals(expectedMinTx, range[0], "minTx margin expansion mismatch");
            assertEquals(expectedMaxTx, range[1], "maxTx margin expansion mismatch");
            assertEquals(expectedMinTy, range[2], "minTy margin expansion mismatch");
            assertEquals(expectedMaxTy, range[3], "maxTy margin expansion mismatch");
        }

        @Test
        @DisplayName("7.3 Zero black flashing: Visible viewport is 100% covered by culled tile range")
        void testZeroBlackFlashingCoverage() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);

            for (Vector2D pos : TEST_POSITIONS) {
                for (float zoom : TEST_ZOOMS) {
                    camera.setZoom(zoom);
                    camera.setCenter(pos.x, pos.y);

                    Rectangle vis = camera.getVisibleBounds();
                    int[] range = map.getVisibleTileRange(camera);

                    float rangeLeftWorld = range[0] * TILE_SIZE;
                    float rangeRightWorld = (range[1] + 1) * TILE_SIZE;
                    float rangeTopWorld = range[2] * TILE_SIZE;
                    float rangeBottomWorld = (range[3] + 1) * TILE_SIZE;

                    // Whenever camera visible bounds overlaps [0, 4096], culled tile range must cover it completely
                    float visLeftClamped = Math.max(0.0f, vis.x);
                    float visRightClamped = Math.min(WORLD_SIZE, vis.x + vis.width);
                    float visTopClamped = Math.max(0.0f, vis.y);
                    float visBottomClamped = Math.min(WORLD_SIZE, vis.y + vis.height);

                    if (visRightClamped > visLeftClamped && visBottomClamped > visTopClamped) {
                        assertTrue(rangeLeftWorld <= visLeftClamped,
                                "Culled tiles do not cover left edge: rangeLeft=" + rangeLeftWorld + " visLeft=" + visLeftClamped);
                        assertTrue(rangeRightWorld >= visRightClamped,
                                "Culled tiles do not cover right edge: rangeRight=" + rangeRightWorld + " visRight=" + visRightClamped);
                        assertTrue(rangeTopWorld <= visTopClamped,
                                "Culled tiles do not cover top edge: rangeTop=" + rangeTopWorld + " visTop=" + visTopClamped);
                        assertTrue(rangeBottomWorld >= visBottomClamped,
                                "Culled tiles do not cover bottom edge: rangeBottom=" + rangeBottomWorld + " visBottom=" + visBottomClamped);
                    }
                }
            }
        }
    }

    // =========================================================================
    // Challenge 8: Collision Sliding Resolution Stress Tests
    // =========================================================================
    @Nested
    @DisplayName("Challenge 8: Collision Engine & Sliding Invariants")
    class CollisionSlidingChallenge {

        @Test
        @DisplayName("8.1 Sliding collision against water: sliding resolution preserves unblocked velocity")
        void testWaterSliding() {
            TileMap map = new TileMap(42);
            float r = 10.0f;

            // Find an X column where row 10 is WATER and row 11 is BRIDGE_H
            int waterCol = -1;
            for (int x = 20; x <= 65; x++) {
                if (map.tileAt(x, 10) == TileType.WATER && map.tileAt(x, 11) == TileType.BRIDGE_H) {
                    waterCol = x;
                    break;
                }
            }
            assertTrue(waterCol > 0, "Must find water tile directly above bridge");

            // Bridge top edge is at y = 11 * 32 = 352.0f.
            // Entity centered at (waterCol + 0.5, 367.0) is on bridge with distance 15.0f > 10.0f from water.
            float entityX = (waterCol + 0.5f) * TILE_SIZE;
            float entityY = 367.0f;
            Vector2D oldPos = new Vector2D(entityX, entityY);
            assertTrue(map.isTraversable(oldPos, r), "Initial position on bridge must be traversable");

            // Desired move: diagonally up into water at row 10 (Y=355, blocked) and right (X+10, clear)
            Vector2D desiredPos = new Vector2D(entityX + 10.0f, 355.0f);
            assertFalse(map.isTraversable(desiredPos, r), "Desired position must penetrate water");

            Vector2D resolved = map.resolveCollision(oldPos, desiredPos, r);

            // Resolved position must be traversable
            assertTrue(map.isTraversable(resolved, r), "Resolved position must be traversable");
            // Y movement into water must be blocked (retaining oldPos.y = 367.0f)
            assertEquals(oldPos.y, resolved.y, 1e-2f, "Y movement into water should be blocked");
            // If X+10 is also on the bridge, X movement slides freely
            if (map.isTraversable(desiredPos.x, oldPos.y, r)) {
                assertEquals(desiredPos.x, resolved.x, 1e-2f, "X movement along bridge should slide freely");
            }
        }

        @Test
        @DisplayName("8.2 Invariant: resolveCollision always produces a traversable position")
        void testResolveCollisionInvariant() {
            TileMap map = new TileMap(42);
            float r = 20.0f;
            Vector2D oldPos = new Vector2D(2048.0f, 2048.0f);

            // Stress test 100 random directions and distances
            for (int angle = 0; angle < 360; angle += 15) {
                float rad = (float) Math.toRadians(angle);
                float dist = 50.0f;
                Vector2D candidate = new Vector2D(
                        oldPos.x + (float) Math.cos(rad) * dist,
                        oldPos.y + (float) Math.sin(rad) * dist
                );

                Vector2D resolved = map.resolveCollision(oldPos, candidate, r);
                assertTrue(map.isTraversable(resolved, r),
                        "Invariant violated: resolved position " + resolved + " is not traversable!");
            }
        }
    }
}
