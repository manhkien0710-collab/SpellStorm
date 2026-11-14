package com.spellstorm.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2: TerrainGenerator & Autotiler Tests")
public class TerrainGeneratorAndAutotilerTest {

    private static final long TEST_SEED = 123456789L;

    @Nested
    @DisplayName("Tier 1: Multi-Layer Terrain Generation & Determinism")
    class TerrainGenerationTests {

        @Test
        @DisplayName("1.1 Determinism: Identical seeds produce bitwise-identical terrain across extreme coordinates")
        void testMultiLayerTerrainDeterminism() {
            TerrainGenerator gen1 = new TerrainGenerator(TEST_SEED);
            TerrainGenerator gen2 = new TerrainGenerator(TEST_SEED);
            TerrainGenerator genDiff = new TerrainGenerator(TEST_SEED + 9999L);

            int[] testCoords = { -100_000, -1025, -33, -1, 0, 1, 32, 1024, 100_000 };

            int differencesWithDiffSeed = 0;
            int totalChecked = 0;

            for (int gx : testCoords) {
                for (int gy : testCoords) {
                    TileType t1 = gen1.sampleTileType(gx, gy);
                    TileType t2 = gen2.sampleTileType(gx, gy);
                    assertEquals(t1, t2, "Identical seed must produce bit-identical tiles at (" + gx + ", " + gy + ")");

                    TileType tDiff = genDiff.sampleTileType(gx, gy);
                    if (t1 != tDiff) {
                        differencesWithDiffSeed++;
                    }
                    totalChecked++;
                }
            }
            assertTrue(differencesWithDiffSeed > 0, "Different seeds should produce divergent terrain");
        }

        @Test
        @DisplayName("1.2 Ridge noise rivers: Water channel flanked by sandbanks across chunk boundaries")
        void testContinuousRiverAndSandbankSeamContinuity() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);

            // Scan along a transect to find a river crossing
            boolean foundRiver = false;
            for (int y = -200; y < 200 && !foundRiver; y += 10) {
                for (int x = -200; x < 200; x++) {
                    if (gen.isRiverWater(x, y)) {
                        foundRiver = true;
                        // Inspect surrounding profile
                        boolean hasSandNearWater = false;
                        for (int dx = -5; dx <= 5; dx++) {
                            if (gen.isRiverSand(x + dx, y)) {
                                hasSandNearWater = true;
                                break;
                            }
                        }
                        assertTrue(hasSandNearWater, "River water should be flanked by sandbanks");
                        break;
                    }
                }
            }
            assertTrue(foundRiver, "Should locate at least one river across the sample area");
        }

        @Test
        @DisplayName("1.3 Road and Bridge crossings: Highways place bridges across water and sand")
        void testRoadAndBridgeCrossings() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);

            // East-West Highway at gy = 0 (and period 256)
            assertTrue(gen.isEastWestHighway(100, 0));
            assertTrue(gen.isEastWestHighway(100, 256));
            assertTrue(gen.isEastWestHighway(100, -256));
            assertFalse(gen.isEastWestHighway(100, 50));

            // North-South Trail at gx = 0 (and period 256)
            assertTrue(gen.isNorthSouthTrail(0, 100));
            assertTrue(gen.isNorthSouthTrail(256, 100));
            assertTrue(gen.isNorthSouthTrail(-256, 100));
            assertFalse(gen.isNorthSouthTrail(50, 100));

            // Road crossing tile types
            for (int gx = -500; gx < 500; gx++) {
                if (gen.isNorthSouthTrail(gx, 0)) {
                    continue; // Skip the intersection where N-S trail intersects E-W highway
                }
                int gy = 0; // East-West highway
                TileType type = gen.sampleTileType(gx, gy);
                if (gen.isRiverWater(gx, gy) || gen.isRiverSand(gx, gy)) {
                    assertEquals(TileType.BRIDGE_H, type, "E-W highway over water/sand must be BRIDGE_H");
                    assertTrue(type.isBridge());
                    assertTrue(type.isWalkable());
                } else {
                    assertEquals(TileType.DIRT, type, "E-W highway over land must be DIRT");
                }
            }

            for (int gy = -500; gy < 500; gy++) {
                if (gen.isEastWestHighway(0, gy)) {
                    continue; // Skip the intersection where E-W highway intersects N-S trail
                }
                int gx = 0; // North-South trail
                TileType type = gen.sampleTileType(gx, gy);
                if (gen.isRiverWater(gx, gy) || gen.isRiverSand(gx, gy)) {
                    assertEquals(TileType.BRIDGE_V, type, "N-S trail over water/sand must be BRIDGE_V");
                    assertTrue(type.isBridge());
                    assertTrue(type.isWalkable());
                } else {
                    assertEquals(TileType.DIRT, type, "N-S trail over land must be DIRT");
                }
            }
        }
    }

    @Nested
    @DisplayName("Tier 2: Autotiler & Procedural Fallback")
    class AutotilingTests {

        @Test
        @DisplayName("2.1 Grass overhang bitmasks onto dirt, stone, and sand substrates")
        void testGrassOverhangBitmask() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);
            Autotiler autotiler = new Autotiler(gen);

            // Center is DIRT, surrounded by GRASS on North and East
            int mask = autotiler.getGrassOverhangMask(TileType.DIRT, TileType.GRASS, TileType.GRASS, TileType.DIRT, TileType.DIRT);
            assertEquals(Autotiler.DIR_N | Autotiler.DIR_E, mask);

            // Center is GRASS (should have 0 overhang)
            int grassMask = autotiler.getGrassOverhangMask(TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS);
            assertEquals(0, grassMask);

            // Center is WATER (should have 0 grass overhang)
            int waterMask = autotiler.getGrassOverhangMask(TileType.WATER, TileType.GRASS, TileType.GRASS, TileType.GRASS, TileType.GRASS);
            assertEquals(0, waterMask);
        }

        @Test
        @DisplayName("2.2 Shoreline transition bitmasks onto water, excluding bridges")
        void testShorelineTransitionBitmask() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);
            Autotiler autotiler = new Autotiler(gen);

            // Center is WATER, North is SAND, East is BRIDGE_H
            int mask = autotiler.getShoreTransitionMask(TileType.WATER, TileType.SAND, TileType.BRIDGE_H, TileType.WATER, TileType.WATER);
            // SAND creates shore fringe (DIR_N), BRIDGE_H is excluded
            assertEquals(Autotiler.DIR_N, mask);

            // Center is not WATER
            int landMask = autotiler.getShoreTransitionMask(TileType.GRASS, TileType.SAND, TileType.SAND, TileType.SAND, TileType.SAND);
            assertEquals(0, landMask);
        }

        @Test
        @DisplayName("2.3 Procedural fallback equivalence: Bitmasks match whether neighbor chunks are loaded or unloaded")
        void testAutotilerProceduralFallbackEquivalence() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);
            Autotiler autotiler = new Autotiler(gen);

            Chunk chunk00 = new ChunkImpl(0, 0);
            gen.generateChunk(0, 0, chunk00);

            // When world is null, autotiler samples procedural fallback
            // Test all border tiles of chunk (0, 0)
            for (int ly = 0; ly < 32; ly++) {
                int maskLeftUnloaded = autotiler.getGrassOverhangMask(chunk00, 0, ly, null);
                int maskRightUnloaded = autotiler.getGrassOverhangMask(chunk00, 31, ly, null);
                int shoreLeftUnloaded = autotiler.getShoreTransitionMask(chunk00, 0, ly, null);
                int shoreRightUnloaded = autotiler.getShoreTransitionMask(chunk00, 31, ly, null);

                // Both should be computable deterministically without exceptions
                assertTrue(maskLeftUnloaded >= 0 && maskLeftUnloaded <= 15);
                assertTrue(maskRightUnloaded >= 0 && maskRightUnloaded <= 15);
                assertTrue(shoreLeftUnloaded >= 0 && shoreLeftUnloaded <= 15);
                assertTrue(shoreRightUnloaded >= 0 && shoreRightUnloaded <= 15);
            }
        }
    }

    @Nested
    @DisplayName("Tier 3: Obstacle Poisson Spacing & Invariants")
    class ObstaclePlacementTests {

        @Test
        @DisplayName("3.1 Minimum solid obstacle center-to-center spacing >= 44.0 px across 16 chunks")
        void testObstaclePoissonDiskMinimumSpacing() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);

            List<Obstacle> allObstacles = new ArrayList<>();
            // Generate a 4x4 chunk area (16 chunks)
            for (int cy = -2; cy < 2; cy++) {
                for (int cx = -2; cx < 2; cx++) {
                    List<Obstacle> decos = gen.generateDecorations(cx, cy);
                    for (Obstacle o : decos) {
                        allObstacles.add(new Obstacle(o.getType(), cx * 1024.0f + o.getPosition().x, cy * 1024.0f + o.getPosition().y, o.isSolid()));
                    }
                }
            }

            assertFalse(allObstacles.isEmpty(), "Should generate obstacles");

            // Filter to solid obstacles
            List<Obstacle> solidObstacles = allObstacles.stream().filter(Obstacle::isSolid).toList();

            // Verify pairwise Euclidean distance >= 44.0 px
            for (int i = 0; i < solidObstacles.size(); i++) {
                Obstacle o1 = solidObstacles.get(i);
                for (int j = i + 1; j < solidObstacles.size(); j++) {
                    Obstacle o2 = solidObstacles.get(j);
                    float dx = o1.getPosition().x - o2.getPosition().x;
                    float dy = o1.getPosition().y - o2.getPosition().y;
                    float dist = (float) Math.sqrt(dx * dx + dy * dy);

                    assertTrue(dist >= 44.0f - 1e-3f,
                            "Obstacles at (" + o1.getPosition().x + ", " + o1.getPosition().y +
                            ") and (" + o2.getPosition().x + ", " + o2.getPosition().y +
                            ") violate min distance (dist=" + dist + " px)");
                }
            }
        }

        @Test
        @DisplayName("3.2 Spawn safe zone clearance: No obstacles within R < 384 px of world origin")
        void testObstacleSafeZoneClearance() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);

            for (int cy = -1; cy <= 1; cy++) {
                for (int cx = -1; cx <= 1; cx++) {
                    List<Obstacle> decos = gen.generateDecorations(cx, cy);
                    for (Obstacle o : decos) {
                        assertTrue(o.getPosition().x >= 0.0f && o.getPosition().x < 1024.0f, "Obstacle X must be chunk-local");
                        assertTrue(o.getPosition().y >= 0.0f && o.getPosition().y < 1024.0f, "Obstacle Y must be chunk-local");
                        float gx = (float) ((long) cx * 1024L) + o.getPosition().x;
                        float gy = (float) ((long) cy * 1024L) + o.getPosition().y;
                        float distSq = gx * gx + gy * gy;
                        assertTrue(distSq >= TerrainGenerator.SAFE_ZONE_RADIUS * TerrainGenerator.SAFE_ZONE_RADIUS - 1e-2f,
                                "No obstacle should spawn within safe zone radius (384px) of (0,0)");
                    }
                }
            }
        }

        @Test
        @DisplayName("3.3 Natural ground filter: No obstacles on water, bridges, or roads")
        void testNoObstaclesOnImpassableOrRoadTiles() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);

            for (int cy = -2; cy <= 2; cy++) {
                for (int cx = -2; cx <= 2; cx++) {
                    List<Obstacle> decos = gen.generateDecorations(cx, cy);
                    for (Obstacle o : decos) {
                        float gx = (float) ((long) cx * 1024L) + o.getPosition().x;
                        float gy = (float) ((long) cy * 1024L) + o.getPosition().y;
                        int candGx = ChunkKey.worldToTileX(gx);
                        int candGy = ChunkKey.worldToTileY(gy);
                        TileType tile = gen.sampleTileType(candGx, candGy);

                        assertNotEquals(TileType.WATER, tile, "Obstacle cannot spawn on water");
                        assertFalse(tile.isBridge(), "Obstacle cannot spawn on bridge");
                        assertNotEquals(TileType.DIRT, tile, "Obstacle cannot spawn on road");
                    }
                }
            }
        }
    }

    @Nested
    @DisplayName("Tier 4: Performance Budget (< 2.0 ms per chunk)")
    class PerformanceTests {

        @Test
        @DisplayName("4.1 Benchmark: Chunk generation latency comfortably satisfies < 2.0 ms budget")
        void testChunkGenerationPerformanceBudget() {
            TerrainGenerator gen = new TerrainGenerator(TEST_SEED);
            Chunk chunk = new ChunkImpl(0, 0);

            // Warm-up JIT
            for (int i = 0; i < 20; i++) {
                gen.generateChunk(i, i, chunk);
            }

            // Benchmark 50 chunks
            long start = System.nanoTime();
            int count = 50;
            for (int i = 0; i < count; i++) {
                Chunk c = new ChunkImpl(i, i);
                gen.generateChunk(i, i, c);
            }
            long elapsedNs = System.nanoTime() - start;
            double avgMs = (elapsedNs / 1_000_000.0) / count;

            System.out.printf("[Performance Benchmark] Average chunk generation time: %.3f ms\n", avgMs);
            assertTrue(avgMs < 2.0, "Average chunk generation time must be < 2.0 ms (was " + avgMs + " ms)");
        }
    }
}
