package com.spellstorm.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1: ChunkKey & Coordinate Transformation Math Tests")
public class ChunkKeyTest {

    // =========================================================================
    // Tier 1: Bitwise Round-Trip & Extreme Boundary Tests
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Bitwise Packing & Unpacking")
    class BitwiseRoundTripTests {

        @Test
        @DisplayName("1.1 Extreme signed 32-bit boundary round-trip matrix")
        void testExtremeBoundaryRoundTrip() {
            int[] testCoords = {
                Integer.MIN_VALUE, Integer.MIN_VALUE + 1, -100_000, -33, -32, -31, -2, -1,
                0, 1, 2, 31, 32, 33, 100_000, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
            };

            for (int cx : testCoords) {
                for (int cy : testCoords) {
                    long key = ChunkKey.of(cx, cy);

                    // Primary getters
                    assertEquals(cx, ChunkKey.getChunkX(key), "Chunk X mismatch for (" + cx + ", " + cy + ")");
                    assertEquals(cy, ChunkKey.getChunkY(key), "Chunk Y mismatch for (" + cx + ", " + cy + ")");

                    // Alias getters
                    assertEquals(cx, ChunkKey.getX(key), "Alias X mismatch for (" + cx + ", " + cy + ")");
                    assertEquals(cy, ChunkKey.getY(key), "Alias Y mismatch for (" + cx + ", " + cy + ")");
                }
            }
        }

        @Test
        @DisplayName("1.2 Specific quadrant bit patterns and uniqueness")
        void testQuadrantBitPatterns() {
            // (0, 0)
            long keyZero = ChunkKey.of(0, 0);
            assertEquals(0L, keyZero);

            // (0, -1)
            long keyZeroNegOne = ChunkKey.of(0, -1);
            assertEquals(0x00000000FFFFFFFFL, keyZeroNegOne);
            assertEquals(0, ChunkKey.getChunkX(keyZeroNegOne));
            assertEquals(-1, ChunkKey.getChunkY(keyZeroNegOne));

            // (-1, 0)
            long keyNegOneZero = ChunkKey.of(-1, 0);
            assertEquals(0xFFFFFFFF00000000L, keyNegOneZero);
            assertEquals(-1, ChunkKey.getChunkX(keyNegOneZero));
            assertEquals(0, ChunkKey.getChunkY(keyNegOneZero));

            // (-1, -1)
            long keyNegOneNegOne = ChunkKey.of(-1, -1);
            assertEquals(-1L, keyNegOneNegOne);
            assertEquals(0xFFFFFFFFFFFFFFFFL, keyNegOneNegOne);
            assertEquals(-1, ChunkKey.getChunkX(keyNegOneNegOne));
            assertEquals(-1, ChunkKey.getChunkY(keyNegOneNegOne));

            // Opposite pairs must never collide
            assertNotEquals(ChunkKey.of(1, 0), ChunkKey.of(0, 1));
            assertNotEquals(ChunkKey.of(-1, 1), ChunkKey.of(1, -1));
            assertNotEquals(ChunkKey.of(Integer.MIN_VALUE, 0), ChunkKey.of(0, Integer.MIN_VALUE));
        }

        @Test
        @DisplayName("1.3 Randomized 10,000 round-trip fuzz test")
        void testRandomizedRoundTripFuzz() {
            Random rng = new Random(42);
            for (int i = 0; i < 10_000; i++) {
                int cx = rng.nextInt();
                int cy = rng.nextInt();
                long key = ChunkKey.of(cx, cy);
                assertEquals(cx, ChunkKey.getChunkX(key));
                assertEquals(cy, ChunkKey.getChunkY(key));
            }
        }
    }

    // =========================================================================
    // Tier 2: World to Tile Negative Boundary Tests
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: World-to-Tile Transformations")
    class WorldToTileTests {

        @ParameterizedTest(name = "wx = {0} -> expected tx = {1}")
        @CsvSource({
            "0.0, 0",
            "15.0, 0",
            "31.99, 0",
            "32.0, 1",
            "32.01, 1",
            "-0.00001, -1",
            "-0.01, -1",
            "-1.0, -1",
            "-31.99, -1",
            "-32.0, -1",
            "-32.01, -2",
            "-64.0, -2",
            "-64.01, -3"
        })
        void testWorldToTileContinuousBoundaries(float wx, int expectedTx) {
            assertEquals(expectedTx, ChunkKey.worldToTileX(wx));
            assertEquals(expectedTx, ChunkKey.worldToTileY(wx));
        }
    }

    // =========================================================================
    // Tier 3: Tile-to-Chunk & Local Tile Euclidean Transformations
    // =========================================================================
    @Nested
    @DisplayName("Tier 3: Tile-to-Chunk & Local Tile Math")
    class TileToChunkTests {

        @ParameterizedTest(name = "tx = {0} -> cx = {1}, lx = {2}")
        @CsvSource({
            "0, 0, 0",
            "15, 0, 15",
            "31, 0, 31",
            "32, 1, 0",
            "33, 1, 1",
            "63, 1, 31",
            "64, 2, 0",
            "-1, -1, 31",
            "-2, -1, 30",
            "-31, -1, 1",
            "-32, -1, 0",
            "-33, -2, 31",
            "-64, -2, 0",
            "-65, -3, 31"
        })
        void testTileToChunkAndLocalTileBoundaries(int tx, int expectedCx, int expectedLx) {
            int cx = ChunkKey.tileToChunkX(tx);
            int lx = ChunkKey.localTileX(tx);

            assertEquals(expectedCx, cx, "Chunk X mismatch for tx = " + tx);
            assertEquals(expectedLx, lx, "Local tile X mismatch for tx = " + tx);

            // Reconstructed global tile coordinate must equal tx
            long reconstructed = ChunkKey.chunkLocalToGlobalTileX(cx, lx);
            assertEquals(tx, reconstructed, "Reconstruction failed for tx = " + tx);
        }

        @Test
        @DisplayName("3.2 Local tile indices strictly bounded within [0, 1023]")
        void testLocalTileIndexBoundsAndUniqueness() {
            boolean[] visited = new boolean[1024];
            for (int ly = 0; ly < 32; ly++) {
                for (int lx = 0; lx < 32; lx++) {
                    int idx = ChunkKey.localTileIndex(lx, ly);
                    assertTrue(idx >= 0 && idx < 1024, "Index out of bounds: " + idx);
                    assertFalse(visited[idx], "Index collision at " + idx);
                    visited[idx] = true;

                    // Reverse extraction
                    assertEquals(lx, idx % 32);
                    assertEquals(ly, idx / 32);
                }
            }
        }
    }

    // =========================================================================
    // Tier 4: End-to-End Coordinate Pipeline & Convenience Methods
    // =========================================================================
    @Nested
    @DisplayName("Tier 4: End-to-End Pipeline & Overflow Safety")
    class EndToEndAndOverflowTests {

        @Test
        @DisplayName("4.1 Negative boundary pipeline: wx = -0.01f -> tx = -1 -> cx = -1, lx = 31")
        void testNegativeBoundaryEndToEndPipeline() {
            float wx = -0.01f;
            float wy = -0.01f;

            int tx = ChunkKey.worldToTileX(wx);
            int ty = ChunkKey.worldToTileY(wy);
            assertEquals(-1, tx);
            assertEquals(-1, ty);

            int cx = ChunkKey.tileToChunkX(tx);
            int cy = ChunkKey.tileToChunkY(ty);
            assertEquals(-1, cx);
            assertEquals(-1, cy);

            int lx = ChunkKey.localTileX(tx);
            int ly = ChunkKey.localTileY(ty);
            assertEquals(31, lx);
            assertEquals(31, ly);

            int idx = ChunkKey.localTileIndex(lx, ly);
            assertEquals(1023, idx);

            long key = ChunkKey.of(cx, cy);
            assertEquals(key, ChunkKey.fromTile(tx, ty));
            assertEquals(key, ChunkKey.fromWorld(wx, wy));
            assertEquals("(-1, -1)", ChunkKey.toString(key));
        }

        @Test
        @DisplayName("4.2 Tile-to-World Center produces exact tile centers")
        void testTileToWorldCenter() {
            assertEquals(16.0f, ChunkKey.tileToWorldCenterX(0));
            assertEquals(48.0f, ChunkKey.tileToWorldCenterX(1));
            assertEquals(-16.0f, ChunkKey.tileToWorldCenterX(-1));
            assertEquals(-48.0f, ChunkKey.tileToWorldCenterX(-2));
        }

        @Test
        @DisplayName("4.3 Overflow safety: Extreme 32-bit chunk coordinates widened to long")
        void testChunkToWorldOriginOverflowSafety() {
            // Integer.MAX_VALUE = 2,147,483,647
            int maxCx = Integer.MAX_VALUE;
            long expectedMaxPx = ((long) maxCx) * 1024L;
            assertEquals(2_199_023_254_528L, expectedMaxPx);
            assertEquals(expectedMaxPx, ChunkKey.chunkToWorldOriginX(maxCx));

            // Integer.MIN_VALUE = -2,147,483,648
            int minCx = Integer.MIN_VALUE;
            long expectedMinPx = ((long) minCx) * 1024L;
            assertEquals(-2_199_023_255_552L, expectedMinPx);
            assertEquals(expectedMinPx, ChunkKey.chunkToWorldOriginX(minCx));

            // Global tile index widening
            long maxTileX = ChunkKey.chunkLocalToGlobalTileX(maxCx, 31);
            assertEquals(68_719_476_735L, maxTileX);

            long minTileX = ChunkKey.chunkLocalToGlobalTileX(minCx, 0);
            assertEquals(-68_719_476_736L, minTileX);
        }
    }
}
