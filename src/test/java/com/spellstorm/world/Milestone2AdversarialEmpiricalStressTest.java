package com.spellstorm.world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2 Adversarial Empirical Stress Tests")
public class Milestone2AdversarialEmpiricalStressTest {

    private static final long PRIMARY_SEED = 123456789L;
    private static final long ADVERSARIAL_SEED_1 = 0xCAFEBABE1337L;
    private static final long ADVERSARIAL_SEED_2 = 0x9E3779B97F4A7C15L;

    // =========================================================================
    // TASK 1: River Continuity Stress Test Across 10,000 Continuous River Tiles
    // =========================================================================

    @Nested
    @DisplayName("Task 1: River Continuity & Seam Integrity Across 10,000 River Tiles")
    class RiverContinuityStressTests {

        private record RiverTile(int gx, int gy, TileType type, int cx, int cy, int lx, int ly) {}

        @Test
        @DisplayName("1.1 Water channel continuity across 10,000 tiles and chunk seams without single-tile cuts or breaks")
        void testRiverWaterChannelContinuityAndChunkSeams() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);

            // Locate an initial river water tile
            int startGx = 0, startGy = 0;
            boolean foundStart = false;
            for (int y = -500; y <= 500 && !foundStart; y++) {
                for (int x = -500; x <= 500 && !foundStart; x++) {
                    if (gen.sampleTileType(x, y) == TileType.WATER) {
                        startGx = x;
                        startGy = y;
                        foundStart = true;
                    }
                }
            }
            assertTrue(foundStart, "Must locate initial river water tile to begin trace");

            // BFS exploration collecting continuous river tiles (WATER or BRIDGE)
            Queue<long[]> queue = new ArrayDeque<>(16384);
            Set<Long> visited = new HashSet<>(32768);

            long startKey = packTile(startGx, startGy);
            queue.add(new long[]{startGx, startGy});
            visited.add(startKey);

            List<RiverTile> tracedTiles = new ArrayList<>(12000);
            Set<Long> crossedChunkSeams = new HashSet<>();
            int chunkBorderTileCount = 0;

            int[][] dirs = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

            while (!queue.isEmpty() && tracedTiles.size() < 10000) {
                long[] curr = queue.poll();
                int gx = (int) curr[0];
                int gy = (int) curr[1];

                TileType type = gen.sampleTileType(gx, gy);
                int cx = ChunkKey.tileToChunkX(gx);
                int cy = ChunkKey.tileToChunkY(gy);
                int lx = ChunkKey.localTileX(gx);
                int ly = ChunkKey.localTileY(gy);

                tracedTiles.add(new RiverTile(gx, gy, type, cx, cy, lx, ly));

                boolean onBorder = (lx == 0 || lx == 31 || ly == 0 || ly == 31);
                if (onBorder) {
                    chunkBorderTileCount++;
                    crossedChunkSeams.add(ChunkKey.of(cx, cy));
                }

                // Explore 8-connected river tiles (WATER or BRIDGE)
                for (int[] d : dirs) {
                    int nx = gx + d[0];
                    int ny = gy + d[1];
                    long neighborKey = packTile(nx, ny);

                    if (!visited.contains(neighborKey)) {
                        TileType nType = gen.sampleTileType(nx, ny);
                        if (nType == TileType.WATER || nType.isBridge()) {
                            visited.add(neighborKey);
                            queue.add(new long[]{nx, ny});
                        }
                    }
                }
            }

            assertTrue(tracedTiles.size() >= 10000,
                    "Must trace at least 10,000 continuous river tiles (traced: " + tracedTiles.size() + ")");
            assertTrue(crossedChunkSeams.size() >= 30,
                    "River must cross multiple chunk seams (crossed: " + crossedChunkSeams.size() + " distinct chunks)");
            assertTrue(chunkBorderTileCount >= 500,
                    "Must verify hundreds of chunk border crossings (tested: " + chunkBorderTileCount + ")");

            System.out.printf("[River Stress] Successfully traced %d continuous river tiles across %d distinct chunks (%d boundary tiles)\n",
                    tracedTiles.size(), crossedChunkSeams.size(), chunkBorderTileCount);

            // 1. ChunkImpl vs Procedural Generator exact parity
            Map<Long, Chunk> generatedChunks = new HashMap<>();
            for (RiverTile rt : tracedTiles) {
                long cKey = ChunkKey.of(rt.cx, rt.cy);
                Chunk chunk = generatedChunks.computeIfAbsent(cKey, k -> {
                    Chunk c = new ChunkImpl(rt.cx, rt.cy);
                    gen.generateChunk(rt.cx, rt.cy, c);
                    return c;
                });
                TileType chunkTile = chunk.getLocalTile(rt.lx, rt.ly);
                assertEquals(rt.type, chunkTile,
                        "Chunk storage must match sampleTileType at (" + rt.gx + ", " + rt.gy + ") in chunk (" + rt.cx + ", " + rt.cy + ")");
            }

            // 2. Check 4-connected topology of water channel: zero isolated water tiles, zero diagonal pinches
            int isolatedCount = 0;
            int diagonalPinches = 0;
            for (RiverTile rt : tracedTiles) {
                if (rt.type == TileType.WATER) {
                    boolean n = isWaterOrBridge(gen.sampleTileType(rt.gx, rt.gy - 1));
                    boolean s = isWaterOrBridge(gen.sampleTileType(rt.gx, rt.gy + 1));
                    boolean e = isWaterOrBridge(gen.sampleTileType(rt.gx + 1, rt.gy));
                    boolean w = isWaterOrBridge(gen.sampleTileType(rt.gx - 1, rt.gy));

                    if (!n && !s && !e && !w) {
                        isolatedCount++;
                    }

                    if (!e && !s && isWaterOrBridge(gen.sampleTileType(rt.gx + 1, rt.gy + 1))) {
                        diagonalPinches++;
                    }
                    if (!w && !s && isWaterOrBridge(gen.sampleTileType(rt.gx - 1, rt.gy + 1))) {
                        diagonalPinches++;
                    }
                }
            }
            assertEquals(0, isolatedCount, "Water channel must never contain isolated 1-tile water drops");
            assertEquals(0, diagonalPinches, "Water channel must never pinch diagonally without cardinal connectivity");
        }

        @Test
        @DisplayName("1.2 Adversarial verification: Sand riverbank continuity with DEFAULT_SAND_WIDTH = 0.035f")
        void testSandRiverbankContinuityAndGradientBreachAudit() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);

            // Audit 10,000 tiles along riverbanks
            int waterTilesChecked = 0;
            int sandbankBreaches = 0;
            List<String> breachExamples = new ArrayList<>();

            for (int y = -250; y <= 250; y++) {
                for (int x = -250; x <= 250; x++) {
                    if (gen.sampleTileType(x, y) == TileType.WATER) {
                        waterTilesChecked++;
                        int[][] cardinal = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                        for (int[] d : cardinal) {
                            int nx = x + d[0];
                            int ny = y + d[1];
                            TileType nType = gen.sampleTileType(nx, ny);
                            // If neighbor is land (not water, not bridge, not road DIRT)
                            if (nType != TileType.WATER && !nType.isBridge() && nType != TileType.DIRT) {
                                if (nType != TileType.SAND) {
                                    sandbankBreaches++;
                                    if (breachExamples.size() < 5) {
                                        float rWater = gen.getRiverNoise(x, y);
                                        float rLand = gen.getRiverNoise(nx, ny);
                                        breachExamples.add(String.format("Water at (%d, %d) [r=%.5f] directly touches %s at (%d, %d) [r=%.5f], diff=%.5f",
                                                x, y, rWater, nType, nx, ny, rLand, (rLand - rWater)));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            System.out.printf("[Sandbank Audit] Checked %d water tiles. Confirmed %d sandbank breach cuts (Water touching Grass/Stone directly).\n",
                    waterTilesChecked, sandbankBreaches);
            for (String example : breachExamples) {
                System.out.println("  -> " + example);
            }

            // Empirical verification: breaches eliminated when DEFAULT_SAND_WIDTH = 0.035f
            assertEquals(0, sandbankBreaches,
                    "Adversarial verification: With DEFAULT_SAND_WIDTH = 0.035f, noise gradient steps do not produce single-tile cuts in sand riverbank!");

            // Mathematical verification: Increasing sand width to 0.035 eliminates 100% of breaches
            float requiredSandWidth = 0.035f;
            int repairedBreaches = 0;
            for (int y = -250; y <= 250; y++) {
                for (int x = -250; x <= 250; x++) {
                    float r = gen.getRiverNoise(x, y);
                    if (r < TerrainGenerator.DEFAULT_WATER_WIDTH) {
                        int[][] cardinal = {{0, 1}, {0, -1}, {1, 0}, {-1, 0}};
                        for (int[] d : cardinal) {
                            float rn = gen.getRiverNoise(x + d[0], y + d[1]);
                            if (rn >= TerrainGenerator.DEFAULT_WATER_WIDTH + requiredSandWidth) {
                                repairedBreaches++;
                            }
                        }
                    }
                }
            }
            assertEquals(0, repairedBreaches,
                    "With sand width >= 0.035f, sand riverbank cuts are 100% eliminated by construction");
        }

        private boolean isWaterOrBridge(TileType t) {
            return t == TileType.WATER || t.isBridge();
        }
    }

    // =========================================================================
    // TASK 2: Bridge Alignment Test
    // =========================================================================

    @Nested
    @DisplayName("Task 2: Bridge Alignment Across Water & Seam Cleanliness")
    class BridgeAlignmentStressTests {

        @Test
        @DisplayName("2.1 Highway bridges align cleanly across water without floating bridge tiles")
        void testHighwayBridgeSpansAndCleanLandings() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);

            int[] testHighwaysY = {-512, -256, 0, 256, 512};
            int bridgesVerified = 0;

            for (int highwayY : testHighwaysY) {
                int startX = -2000;
                int endX = 2000;

                boolean inBridge = false;
                int bridgeSpanStart = 0;

                for (int x = startX; x <= endX; x++) {
                    TileType centerTile = gen.sampleTileType(x, highwayY);
                    if (centerTile.isBridge()) {
                        if (!inBridge) {
                            inBridge = true;
                            bridgeSpanStart = x;
                        }
                    } else {
                        if (inBridge) {
                            inBridge = false;
                            int bridgeSpanEnd = x - 1;

                            // Skip spans clipped by the scan bounds
                            if (bridgeSpanStart > startX && bridgeSpanEnd < endX) {
                                int spanLength = bridgeSpanEnd - bridgeSpanStart + 1;
                                assertTrue(spanLength >= 1, "Bridge span must be >= 1 tile");

                                // 1. Clean Landings: Both ends of East-West bridge MUST connect to walkable DIRT
                                TileType westEnd = gen.sampleTileType(bridgeSpanStart - 1, highwayY);
                                TileType eastEnd = gen.sampleTileType(bridgeSpanEnd + 1, highwayY);

                                assertEquals(TileType.DIRT, westEnd,
                                        "West end of highway bridge must land on DIRT at x=" + (bridgeSpanStart - 1));
                                assertEquals(TileType.DIRT, eastEnd,
                                        "East end of highway bridge must land on DIRT at x=" + (bridgeSpanEnd + 1));

                                // 2. Highway corridor tiles must be BRIDGE_H or DIRT (no WATER holes, no BRIDGE_V)
                                for (int bx = bridgeSpanStart; bx <= bridgeSpanEnd; bx++) {
                                    for (int dy = -TerrainGenerator.HIGHWAY_HALF_WIDTH; dy <= TerrainGenerator.HIGHWAY_HALF_WIDTH; dy++) {
                                        int hy = highwayY + dy;
                                        TileType rowTile = gen.sampleTileType(bx, hy);
                                        assertTrue(rowTile == TileType.BRIDGE_H || rowTile == TileType.DIRT,
                                                "Highway tile at (" + bx + ", " + hy + ") must be BRIDGE_H or DIRT, but was " + rowTile);
                                        assertNotEquals(TileType.WATER, rowTile, "Highway corridor must NEVER have water holes");
                                        assertNotEquals(TileType.BRIDGE_V, rowTile, "East-West highway must not have BRIDGE_V tiles");
                                    }
                                }

                                bridgesVerified++;
                            }
                        }
                    }
                }
            }

            assertTrue(bridgesVerified >= 5, "Must locate and verify multiple river bridges across sampled highways");
            System.out.printf("[Bridge Stress] Verified %d East-West highway bridges: 100%% cleanly anchored to DIRT landings with zero floating tiles.\n",
                    bridgesVerified);
        }

        @Test
        @DisplayName("2.2 North-South trail bridges align cleanly across water without floating bridge tiles")
        void testTrailBridgeSpansAndCleanLandings() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);

            int[] testTrailsX = {-512, -256, 0, 256, 512};
            int bridgesVerified = 0;

            for (int trailX : testTrailsX) {
                int startY = -2000;
                int endY = 2000;

                boolean inBridge = false;
                int bridgeSpanStart = 0;

                for (int y = startY; y <= endY; y++) {
                    if (gen.isEastWestHighway(trailX, y)) {
                        inBridge = false; // Highway takes precedence at intersections
                        continue;
                    }

                    TileType centerTile = gen.sampleTileType(trailX, y);
                    if (centerTile == TileType.BRIDGE_V) {
                        if (!inBridge) {
                            inBridge = true;
                            bridgeSpanStart = y;
                        }
                    } else {
                        if (inBridge) {
                            inBridge = false;
                            int bridgeSpanEnd = y - 1;

                            if (bridgeSpanStart > startY && bridgeSpanEnd < endY) {
                                int spanLength = bridgeSpanEnd - bridgeSpanStart + 1;
                                assertTrue(spanLength >= 1, "Trail bridge span must be >= 1 tile");

                                // Clean Landings: North and South ends must land on walkable DIRT or meet Highway bridge
                                TileType northEnd = gen.sampleTileType(trailX, bridgeSpanStart - 1);
                                TileType southEnd = gen.sampleTileType(trailX, bridgeSpanEnd + 1);

                                assertTrue(northEnd == TileType.DIRT || northEnd.isBridge(),
                                        "North end of trail bridge must land on DIRT or Bridge at y=" + (bridgeSpanStart - 1));
                                assertTrue(southEnd == TileType.DIRT || southEnd.isBridge(),
                                        "South end of trail bridge must land on DIRT or Bridge at y=" + (bridgeSpanEnd + 1));

                                // Trail corridor tiles must be BRIDGE_V or DIRT
                                for (int by = bridgeSpanStart; by <= bridgeSpanEnd; by++) {
                                    for (int dx = -TerrainGenerator.TRAIL_HALF_WIDTH; dx <= TerrainGenerator.TRAIL_HALF_WIDTH; dx++) {
                                        int tx = trailX + dx;
                                        if (gen.isEastWestHighway(tx, by)) continue; // Intersection tile
                                        TileType colTile = gen.sampleTileType(tx, by);
                                        assertTrue(colTile == TileType.BRIDGE_V || colTile == TileType.DIRT,
                                                "Trail tile at (" + tx + ", " + by + ") must be BRIDGE_V or DIRT, but was " + colTile);
                                        assertNotEquals(TileType.WATER, colTile, "Trail corridor must NEVER have water holes");
                                    }
                                }

                                bridgesVerified++;
                            }
                        }
                    }
                }
            }

            assertTrue(bridgesVerified >= 5, "Must locate and verify multiple river bridges across sampled trails");
            System.out.printf("[Bridge Stress] Verified %d North-South trail bridges: 100%% cleanly anchored to DIRT landings with zero floating tiles.\n",
                    bridgesVerified);
        }

        @Test
        @DisplayName("2.3 Autotiler shore transition mask excludes bridge tiles cleanly")
        void testAutotilerShoreTransitionMaskExcludesBridges() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);
            Autotiler autotiler = new Autotiler(gen);

            int mask1 = autotiler.getShoreTransitionMask(TileType.WATER, TileType.BRIDGE_H, TileType.WATER, TileType.WATER, TileType.WATER);
            assertEquals(0, mask1, "Bridge north should NOT trigger shore transition mask on water");

            int mask2 = autotiler.getShoreTransitionMask(TileType.WATER, TileType.WATER, TileType.BRIDGE_V, TileType.WATER, TileType.WATER);
            assertEquals(0, mask2, "Bridge east should NOT trigger shore transition mask on water");

            int mask3 = autotiler.getShoreTransitionMask(TileType.WATER, TileType.SAND, TileType.BRIDGE_H, TileType.WATER, TileType.BRIDGE_V);
            assertEquals(Autotiler.DIR_N, mask3, "Only SAND should trigger shore transition; bridges must be ignored");
        }
    }

    // =========================================================================
    // TASK 3: Poisson Obstacle Spacing Test Across 1,000 Generated Chunks
    // =========================================================================

    @Nested
    @DisplayName("Task 3: Poisson Obstacle Spacing Stress Test Across 1,000 Chunks")
    class PoissonObstacleSpacingStressTests {

        @Test
        @DisplayName("3.1 Strict Euclidean distance >= 44.0 px across all pairs in 1,024 continuous chunks")
        void testStrictPoissonSpacingAcross1000Chunks() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);

            int chunkMin = -16;
            int chunkMax = 15;
            int totalChunks = (chunkMax - chunkMin + 1) * (chunkMax - chunkMin + 1); // 1,024 chunks

            List<Obstacle> allObstacles = new ArrayList<>(totalChunks * 25);
            Map<Long, List<Obstacle>> spatialGrid = new HashMap<>(totalChunks * 64);

            int chunkCount = 0;
            for (int cy = chunkMin; cy <= chunkMax; cy++) {
                for (int cx = chunkMin; cx <= chunkMax; cx++) {
                    List<Obstacle> chunkObstacles = gen.generateDecorations(cx, cy);
                    chunkCount++;

                    for (Obstacle o : chunkObstacles) {
                        assertTrue(o.getPosition().x >= 0.0f && o.getPosition().x < 1024.0f, "Obstacle X must be chunk-local");
                        assertTrue(o.getPosition().y >= 0.0f && o.getPosition().y < 1024.0f, "Obstacle Y must be chunk-local");
                        Obstacle globalObs = new Obstacle(o.getType(), cx * 1024.0f + o.getPosition().x, cy * 1024.0f + o.getPosition().y, o.isSolid());
                        allObstacles.add(globalObs);

                        int bucketX = (int) Math.floor(globalObs.getPosition().x / 128.0f);
                        int bucketY = (int) Math.floor(globalObs.getPosition().y / 128.0f);
                        long bucketKey = packTile(bucketX, bucketY);
                        spatialGrid.computeIfAbsent(bucketKey, k -> new ArrayList<>(2)).add(globalObs);
                    }
                }
            }

            assertEquals(1024, chunkCount, "Must generate exactly 1,024 chunks");
            assertTrue(allObstacles.size() > 10000,
                    "Must generate substantive obstacle population (generated: " + allObstacles.size() + ")");

            System.out.printf("[Poisson Stress] Generated %d obstacles across %d chunks. Executing pairwise spatial proximity audit...\n",
                    allObstacles.size(), chunkCount);

            long pairsChecked = 0;
            float minObservedDistance = Float.MAX_VALUE;

            for (Obstacle o1 : allObstacles) {
                int bx = (int) Math.floor(o1.getPosition().x / 128.0f);
                int by = (int) Math.floor(o1.getPosition().y / 128.0f);

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        long neighborBucketKey = packTile(bx + dx, by + dy);
                        List<Obstacle> cellObstacles = spatialGrid.get(neighborBucketKey);
                        if (cellObstacles == null) continue;

                        for (Obstacle o2 : cellObstacles) {
                            if (System.identityHashCode(o1) >= System.identityHashCode(o2)) {
                                continue;
                            }

                            float diffX = o1.getPosition().x - o2.getPosition().x;
                            float diffY = o1.getPosition().y - o2.getPosition().y;
                            float dist = (float) Math.sqrt(diffX * diffX + diffY * diffY);
                            pairsChecked++;

                            if (dist < minObservedDistance) {
                                minObservedDistance = dist;
                            }

                            assertTrue(dist >= 44.0f - 1e-4f,
                                    String.format("Poisson distance violation! Obstacles at (%.2f, %.2f) and (%.2f, %.2f) have dist=%.4f px (< 44.0 px)",
                                            o1.getPosition().x, o1.getPosition().y, o2.getPosition().x, o2.getPosition().y, dist));
                        }
                    }
                }
            }

            System.out.printf("[Poisson Stress] Checked %d candidate pairs. Global minimum observed distance: %.4f px (Strict Limit >= 44.0 px). Zero violations!\n",
                    pairsChecked, minObservedDistance);
            assertTrue(minObservedDistance >= 44.0f - 1e-4f);
        }

        @Test
        @DisplayName("3.2 Poisson spacing holds across chunk seams with negative coordinates and extreme seeds")
        void testPoissonSpacingAcrossSeamsInNegativeCoordinates() {
            TerrainGenerator gen = new TerrainGenerator(ADVERSARIAL_SEED_1);

            List<Obstacle> obstacles = new ArrayList<>(3000);
            Map<Long, List<Obstacle>> spatialGrid = new HashMap<>(6400);

            for (int cy = -110; cy <= -101; cy++) {
                for (int cx = -110; cx <= -101; cx++) {
                    List<Obstacle> chunkObstacles = gen.generateDecorations(cx, cy);
                    for (Obstacle o : chunkObstacles) {
                        assertTrue(o.getPosition().x >= 0.0f && o.getPosition().x < 1024.0f, "Obstacle X must be chunk-local");
                        assertTrue(o.getPosition().y >= 0.0f && o.getPosition().y < 1024.0f, "Obstacle Y must be chunk-local");
                        Obstacle globalObs = new Obstacle(o.getType(), cx * 1024.0f + o.getPosition().x, cy * 1024.0f + o.getPosition().y, o.isSolid());
                        obstacles.add(globalObs);

                        int bx = (int) Math.floor(globalObs.getPosition().x / 128.0f);
                        int by = (int) Math.floor(globalObs.getPosition().y / 128.0f);
                        spatialGrid.computeIfAbsent(packTile(bx, by), k -> new ArrayList<>(2)).add(globalObs);
                    }
                }
            }

            for (Obstacle o1 : obstacles) {
                int bx = (int) Math.floor(o1.getPosition().x / 128.0f);
                int by = (int) Math.floor(o1.getPosition().y / 128.0f);

                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        List<Obstacle> cellObstacles = spatialGrid.get(packTile(bx + dx, by + dy));
                        if (cellObstacles == null) continue;

                        for (Obstacle o2 : cellObstacles) {
                            if (System.identityHashCode(o1) >= System.identityHashCode(o2)) continue;
                            float diffX = o1.getPosition().x - o2.getPosition().x;
                            float diffY = o1.getPosition().y - o2.getPosition().y;
                            float dist = (float) Math.sqrt(diffX * diffX + diffY * diffY);
                            assertTrue(dist >= 44.0f - 1e-4f, "Poisson violation in distant negative quadrant");
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // TASK 4: Autotiling Procedural Fallback Test (Isolated Chunk vs 3x3 Cluster)
    // =========================================================================

    @Nested
    @DisplayName("Task 4: Autotiling Procedural Fallback Bit-Identical Verification")
    class AutotilingProceduralFallbackStressTests {

        private static class ClusterWorldGrid implements WorldGrid {
            private final Map<Long, Chunk> chunks = new HashMap<>();

            public void addChunk(Chunk chunk) {
                chunks.put(chunk.getChunkKey(), chunk);
            }

            @Override
            public TileType getTile(int tx, int ty) {
                int cx = ChunkKey.tileToChunkX(tx);
                int cy = ChunkKey.tileToChunkY(ty);
                Chunk c = chunks.get(ChunkKey.of(cx, cy));
                if (c == null) return null;
                int lx = ChunkKey.localTileX(tx);
                int ly = ChunkKey.localTileY(ty);
                return c.getLocalTile(lx, ly);
            }

            @Override public boolean isSolidTile(int tx, int ty) { return false; }
            @Override public boolean isWaterTile(int tx, int ty) { return false; }
            @Override public boolean isTraversable(float x, float y, float radius) { return false; }
            @Override public Vector2D resolveCollision(float oldX, float oldY, float newX, float newY, float radius) { return null; }
            @Override public Vector2D resolveCollision(Vector2D oldPos, Vector2D newPos, float radius) { return null; }
            @Override public int worldToTileX(float worldX) { return ChunkKey.worldToTileX(worldX); }
            @Override public int worldToTileY(float worldY) { return ChunkKey.worldToTileY(worldY); }
            @Override public float tileToWorldX(int tx) { return tx * 32.0f; }
            @Override public float tileToWorldY(int ty) { return ty * 32.0f; }
            @Override public Vector2D tileToWorld(int tx, int ty) { return new Vector2D(tx * 32.0f, ty * 32.0f); }
            @Override public Rectangle getWorldBounds() { return null; }
            @Override public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera, float elapsed) {}
            @Override public List<Obstacle> getVisibleObstacles(GameCamera camera) { return Collections.emptyList(); }
        }

        @Test
        @DisplayName("4.1 Bitmask computation on isolated chunks is 100% bit-identical to full 3x3 cluster across 10 regions")
        void testAutotilingIsolatedChunkVsClusterEquivalence() {
            TerrainGenerator gen = new TerrainGenerator(PRIMARY_SEED);
            Autotiler autotiler = new Autotiler(gen);

            int[][] testCenters = {
                    {0, 0},         // Origin
                    {-1, -1},       // Negative seam
                    {1, 0},         // East chunk
                    {0, 1},         // South chunk
                    {-10, 5},       // Distant mixed
                    {15, -20},      // Distant mixed
                    {8, 8},         // Road highway intersection
                    {7, 7},         // Near highway
                    {-100, -100},   // Far negative
                    {100, 100}      // Far positive
            };

            int totalTilesTested = 0;
            int borderTilesTested = 0;

            for (int[] center : testCenters) {
                int cx = center[0];
                int cy = center[1];

                // Mode 1: Isolated Chunk (No neighbors, world = null)
                Chunk isolatedChunk = new ChunkImpl(cx, cy);
                gen.generateChunk(cx, cy, isolatedChunk);

                // Mode 2: Full 3x3 Cluster loaded in WorldGrid
                ClusterWorldGrid clusterGrid = new ClusterWorldGrid();
                Chunk clusterCenterChunk = null;
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        int ncx = cx + dx;
                        int ncy = cy + dy;
                        Chunk nc = new ChunkImpl(ncx, ncy);
                        gen.generateChunk(ncx, ncy, nc);
                        clusterGrid.addChunk(nc);
                        if (dx == 0 && dy == 0) {
                            clusterCenterChunk = nc;
                        }
                    }
                }
                assertNotNull(clusterCenterChunk);

                // Compare all 1024 tiles bitmask-for-bitmask
                for (int ly = 0; ly < Chunk.CHUNK_TILES; ly++) {
                    for (int lx = 0; lx < Chunk.CHUNK_TILES; lx++) {
                        boolean isBorder = (lx == 0 || lx == 31 || ly == 0 || ly == 31);
                        if (isBorder) borderTilesTested++;
                        totalTilesTested++;

                        // Grass Overhang Mask
                        int grassMaskIsolated = autotiler.getGrassOverhangMask(isolatedChunk, lx, ly, null);
                        int grassMaskCluster = autotiler.getGrassOverhangMask(clusterCenterChunk, lx, ly, clusterGrid);
                        assertEquals(grassMaskCluster, grassMaskIsolated,
                                String.format("Grass overhang mask diverged at chunk (%d, %d) local (%d, %d). Isolated: %d, Cluster: %d",
                                        cx, cy, lx, ly, grassMaskIsolated, grassMaskCluster));

                        // Shore Transition Mask
                        int shoreMaskIsolated = autotiler.getShoreTransitionMask(isolatedChunk, lx, ly, null);
                        int shoreMaskCluster = autotiler.getShoreTransitionMask(clusterCenterChunk, lx, ly, clusterGrid);
                        assertEquals(shoreMaskCluster, shoreMaskIsolated,
                                String.format("Shore transition mask diverged at chunk (%d, %d) local (%d, %d). Isolated: %d, Cluster: %d",
                                        cx, cy, lx, ly, shoreMaskIsolated, shoreMaskCluster));
                    }
                }
            }

            System.out.printf("[Autotiler Fallback] Verified %d tiles (%d chunk border tiles across 10 regions): 100%% bit-identical between isolated chunks and 3x3 clusters!\n",
                    totalTilesTested, borderTilesTested);
            assertEquals(10 * 1024, totalTilesTested);
        }
    }

    // =========================================================================
    // Utilities
    // =========================================================================

    private static long packTile(int tx, int ty) {
        return (((long) tx) << 32) | (((long) ty) & 0xFFFFFFFFL);
    }
}
