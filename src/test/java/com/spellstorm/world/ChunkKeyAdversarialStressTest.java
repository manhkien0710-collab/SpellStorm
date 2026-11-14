package com.spellstorm.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Stress Test Suite for ChunkKey and Euclidean Coordinate Math.
 *
 * Authored by teamwork_challenger to rigorously stress-test:
 * 1. 2,000,000-iteration pseudo-random fuzzing for bitwise round-trip fidelity.
 * 2. Boundary pairs Cartesian product: Integer.MIN_VALUE, Integer.MAX_VALUE, -1, 0, 1, and powers of 2.
 * 3. Bit collision verification: distinct (cx, cy) pairs must produce distinct 64-bit keys.
 * 4. Sign-extension robustness: negative cy must never pollute cx bits.
 * 5. Long-widening overflow safety: chunkToWorldOriginX(Integer.MAX_VALUE) must not overflow to negative.
 * 6. Euclidean division and modulo invariance: Math.floorDiv / Math.floorMod contracts across -2^31 to 2^31-1.
 * 7. Hostile float inputs to worldToTileX/Y and fromWorld (NaN, Infinity, subnormals, extreme values).
 */
@DisplayName("Milestone 1 Challenger: ChunkKey Adversarial Stress Tests")
public class ChunkKeyAdversarialStressTest {

    // Boundary coordinates set covering all bit positions, sign transitions, and arithmetic limits
    private static final int[] BOUNDARY_COORDS = {
        Integer.MIN_VALUE, Integer.MIN_VALUE + 1, Integer.MIN_VALUE + 2,
        -1073741824, // -2^30
        -536870912,  // -2^29
        -67108864,   // -2^26
        -1048576,    // -2^20
        -65536,      // -2^16
        -32768,      // -2^15
        -1024,       // -2^10
        -512, -256, -128, -64, -33, -32, -31, -16, -8, -4, -2, -1,
        0,
        1, 2, 4, 8, 16, 31, 32, 33, 64, 128, 256, 512,
        1024,        // 2^10
        32768,       // 2^15
        65536,       // 2^16
        1048576,     // 2^20
        67108864,    // 2^26
        536870912,   // 2^29
        1073741824,  // 2^30
        Integer.MAX_VALUE - 2, Integer.MAX_VALUE - 1, Integer.MAX_VALUE
    };

    @Nested
    @DisplayName("1. Multi-Million Fuzz Testing & Bitwise Bijectivity")
    class FuzzTesting {

        @Test
        @DisplayName("1.1 2,000,000 pseudo-random coordinate pairs bitwise round-trip bijectivity")
        void testMultiMillionRandomPairsBijectivity() {
            SplittableRandom rng = new SplittableRandom(0xDEADBEEFCAFEBABEL);
            final int iterations = 2_000_000;

            for (int i = 0; i < iterations; i++) {
                int cx = rng.nextInt();
                int cy = rng.nextInt();

                long key = ChunkKey.of(cx, cy);

                int extractedX = ChunkKey.getX(key);
                int extractedY = ChunkKey.getY(key);
                int extractedChunkX = ChunkKey.getChunkX(key);
                int extractedChunkY = ChunkKey.getChunkY(key);

                if (extractedX != cx || extractedY != cy || extractedChunkX != cx || extractedChunkY != cy) {
                    fail(String.format("Bijectivity violation at iteration %d for (cx=%d, cy=%d): key=0x%016X extracted=(%d, %d)",
                            i, cx, cy, key, extractedX, extractedY));
                }
            }
        }

        @Test
        @DisplayName("1.2 Exhaustive Cartesian product of boundary pairs (1,369 combinations)")
        void testBoundaryCartesianProduct() {
            Set<Long> observedKeys = new HashSet<>(BOUNDARY_COORDS.length * BOUNDARY_COORDS.length);

            for (int cx : BOUNDARY_COORDS) {
                for (int cy : BOUNDARY_COORDS) {
                    long key = ChunkKey.of(cx, cy);

                    // Round-trip check
                    assertEquals(cx, ChunkKey.getX(key), "X coordinate mismatch");
                    assertEquals(cy, ChunkKey.getY(key), "Y coordinate mismatch");
                    assertEquals(cx, ChunkKey.getChunkX(key), "ChunkX mismatch");
                    assertEquals(cy, ChunkKey.getChunkY(key), "ChunkY mismatch");

                    // ToString check
                    String expectedStr = "(" + cx + ", " + cy + ")";
                    assertEquals(expectedStr, ChunkKey.toString(key));

                    // Collision check
                    boolean added = observedKeys.add(key);
                    assertTrue(added, String.format("Collision detected for distinct boundary pair (%d, %d): key=0x%016X", cx, cy, key));
                }
            }
        }
    }

    @Nested
    @DisplayName("2. Bit Collisions & Transposition Isolation")
    class BitCollisionIsolation {

        @Test
        @DisplayName("2.1 Coordinate transpositions (a, b) vs (b, a) must never collide when a != b")
        void testTranspositionCollisions() {
            for (int a : BOUNDARY_COORDS) {
                for (int b : BOUNDARY_COORDS) {
                    if (a != b) {
                        long keyAB = ChunkKey.of(a, b);
                        long keyBA = ChunkKey.of(b, a);
                        assertNotEquals(keyAB, keyBA,
                                String.format("Transposed pairs collided: (%d, %d) vs (%d, %d) both produced 0x%016X", a, b, b, a, keyAB));
                    }
                }
            }
        }

        @Test
        @DisplayName("2.2 Sign-flipped pairs (+-a, +-b) must never collide")
        void testSignFlippedCollisions() {
            int[] magnitudes = { 1, 2, 32, 1024, 65536, 1073741824 };
            for (int m1 : magnitudes) {
                for (int m2 : magnitudes) {
                    long pp = ChunkKey.of(m1, m2);
                    long pn = ChunkKey.of(m1, -m2);
                    long np = ChunkKey.of(-m1, m2);
                    long nn = ChunkKey.of(-m1, -m2);

                    Set<Long> set = Set.of(pp, pn, np, nn);
                    assertEquals(4, set.size(), String.format("Sign flip collision for magnitudes %d and %d", m1, m2));
                }
            }
        }

        @Test
        @DisplayName("2.3 Modifying cy never alters upper 32 bits of key")
        void testUpperBitIsolationUnderVaryingY() {
            int testCx = 0x12345678;
            long expectedHighBits = ((long) testCx) << 32;

            for (int cy : BOUNDARY_COORDS) {
                long key = ChunkKey.of(testCx, cy);
                long highBits = key & 0xFFFFFFFF00000000L;
                assertEquals(expectedHighBits, highBits,
                        String.format("Varying cy=%d mutated upper bits: expected 0x%016X, actual 0x%016X", cy, expectedHighBits, highBits));
            }
        }

        @Test
        @DisplayName("2.4 Modifying cx never alters lower 32 bits of key")
        void testLowerBitIsolationUnderVaryingX() {
            int testCy = 0x76543210;
            long expectedLowBits = ((long) testCy) & 0xFFFFFFFFL;

            for (int cx : BOUNDARY_COORDS) {
                long key = ChunkKey.of(cx, testCy);
                long lowBits = key & 0x00000000FFFFFFFFL;
                assertEquals(expectedLowBits, lowBits,
                        String.format("Varying cx=%d mutated lower bits: expected 0x%016X, actual 0x%016X", cx, expectedLowBits, lowBits));
            }
        }
    }

    @Nested
    @DisplayName("3. Sign-Extension Robustness")
    class SignExtensionRobustness {

        @Test
        @DisplayName("3.1 Negative cy must never corrupt cx (sign-extension prevention)")
        void testNegativeCySignExtension() {
            // In Java, without & 0xFFFFFFFFL, casting negative int to long sign-extends ones into bits 63..32
            int[] testCxs = { 0, 1, -1, 42, -42, Integer.MAX_VALUE, Integer.MIN_VALUE };
            int[] testNegativeCys = { -1, -2, -32, -100, -65536, Integer.MIN_VALUE, Integer.MIN_VALUE + 1 };

            for (int cx : testCxs) {
                for (int cy : testNegativeCys) {
                    long key = ChunkKey.of(cx, cy);

                    // If sign extension leaked from cy, ChunkKey.getX(key) would be corrupted
                    int actualX = ChunkKey.getX(key);
                    assertEquals(cx, actualX,
                            String.format("Sign extension corruption: cx=%d corrupted to %d when cy=%d (key=0x%016X)",
                                    cx, actualX, cy, key));

                    int actualY = ChunkKey.getY(key);
                    assertEquals(cy, actualY,
                            String.format("Y extraction mismatch: expected %d, got %d", cy, actualY));
                }
            }
        }

        @Test
        @DisplayName("3.2 Negative cx must never corrupt cy")
        void testNegativeCxSignExtension() {
            int[] testNegativeCxs = { -1, -2, -32, -100, -65536, Integer.MIN_VALUE, Integer.MIN_VALUE + 1 };
            int[] testCys = { 0, 1, -1, 42, -42, Integer.MAX_VALUE, Integer.MIN_VALUE };

            for (int cx : testNegativeCxs) {
                for (int cy : testCys) {
                    long key = ChunkKey.of(cx, cy);

                    int actualX = ChunkKey.getX(key);
                    assertEquals(cx, actualX);

                    int actualY = ChunkKey.getY(key);
                    assertEquals(cy, actualY,
                            String.format("Corrupted cy=%d to %d when cx=%d (key=0x%016X)", cy, actualY, cx, key));
                }
            }
        }

        @Test
        @DisplayName("3.3 Explicit bit-pattern verification for quadrant corners")
        void testExactCornerBitPatterns() {
            // cx = 0, cy = -1 -> 0x00000000FFFFFFFFL
            assertEquals(0x00000000FFFFFFFFL, ChunkKey.of(0, -1));

            // cx = -1, cy = 0 -> 0xFFFFFFFF00000000L
            assertEquals(0xFFFFFFFF00000000L, ChunkKey.of(-1, 0));

            // cx = -1, cy = -1 -> 0xFFFFFFFFFFFFFFFFL (-1L)
            assertEquals(-1L, ChunkKey.of(-1, -1));

            // cx = 1, cy = -1 -> 0x00000001FFFFFFFFL
            assertEquals(0x00000001FFFFFFFFL, ChunkKey.of(1, -1));

            // cx = -1, cy = 1 -> 0xFFFFFFFF00000001L
            assertEquals(0xFFFFFFFF00000001L, ChunkKey.of(-1, 1));
        }
    }

    @Nested
    @DisplayName("4. Long-Widening Overflow Safety")
    class OverflowSafety {

        @Test
        @DisplayName("4.1 chunkToWorldOriginX/Y(Integer.MAX_VALUE) must not overflow 32-bit int")
        void testChunkToWorldOriginMaxIntOverflow() {
            int maxCx = Integer.MAX_VALUE; // 2,147,483,647

            // If computed with 32-bit int (cx * 1024), 2147483647 * 1024 overflows to -1024!
            int overflowed32Bit = maxCx * 1024;
            assertEquals(-1024, overflowed32Bit, "Proof that 32-bit multiplication overflows to -1024");

            // ChunkKey implementation must compute in 64-bit long
            long originX = ChunkKey.chunkToWorldOriginX(maxCx);
            long originY = ChunkKey.chunkToWorldOriginY(maxCx);

            long expectedLong = 2_147_483_647L * 1024L; // 2,199,023,254,528L
            assertEquals(expectedLong, originX, "chunkToWorldOriginX must match expected 64-bit value");
            assertEquals(expectedLong, originY, "chunkToWorldOriginY must match expected 64-bit value");
            assertTrue(originX > 0, "chunkToWorldOriginX must be strictly positive");
            assertNotEquals(-1024L, originX, "chunkToWorldOriginX must NOT suffer 32-bit integer overflow");
        }

        @Test
        @DisplayName("4.2 chunkToWorldOriginX/Y(Integer.MIN_VALUE) must not overflow 32-bit int")
        void testChunkToWorldOriginMinIntOverflow() {
            int minCx = Integer.MIN_VALUE; // -2,147,483,648

            // In 32-bit int, -2147483648 * 1024 overflows to 0!
            int overflowed32Bit = minCx * 1024;
            assertEquals(0, overflowed32Bit, "Proof that 32-bit multiplication overflows to 0");

            long originX = ChunkKey.chunkToWorldOriginX(minCx);
            long originY = ChunkKey.chunkToWorldOriginY(minCx);

            long expectedLong = -2_147_483_648L * 1024L; // -2,199,023,255,552L
            assertEquals(expectedLong, originX, "chunkToWorldOriginX must match expected 64-bit value");
            assertEquals(expectedLong, originY, "chunkToWorldOriginY must match expected 64-bit value");
            assertTrue(originX < 0, "chunkToWorldOriginX must be strictly negative");
            assertNotEquals(0L, originX, "chunkToWorldOriginX must NOT overflow to 0");
        }

        @Test
        @DisplayName("4.3 chunkLocalToGlobalTileX/Y widening safety at extreme coordinates")
        void testChunkLocalToGlobalTileWidening() {
            int maxCx = Integer.MAX_VALUE;
            // In 32-bit int, 2147483647 * 32 + 31 overflows to -1!
            int overflowed32Bit = maxCx * 32 + 31;
            assertEquals(-1, overflowed32Bit, "Proof that 32-bit tile calculation overflows to -1");

            long globalTileX = ChunkKey.chunkLocalToGlobalTileX(maxCx, 31);
            long expected = 2_147_483_647L * 32L + 31L; // 68,719,476,735L
            assertEquals(expected, globalTileX);
            assertTrue(globalTileX > 0);
            assertNotEquals(-1L, globalTileX);

            int minCx = Integer.MIN_VALUE;
            long globalTileMin = ChunkKey.chunkLocalToGlobalTileX(minCx, 0);
            long expectedMin = -2_147_483_648L * 32L; // -68,719,476,736L
            assertEquals(expectedMin, globalTileMin);
            assertTrue(globalTileMin < 0);
        }
    }

    @Nested
    @DisplayName("5. Euclidean Division & Modulo Invariants")
    class EuclideanInvariants {

        @Test
        @DisplayName("5.1 Mathematical Euclidean identity: tileToChunkX(tx) * 32 + localTileX(tx) == tx across all boundaries")
        void testEuclideanDivisionIdentityAcrossBoundaries() {
            for (int tx : BOUNDARY_COORDS) {
                int cx = ChunkKey.tileToChunkX(tx);
                int lx = ChunkKey.localTileX(tx);

                // localTileX must strictly lie in [0, 31]
                assertTrue(lx >= 0 && lx < 32,
                        String.format("localTileX(%d) = %d is outside [0, 31]", tx, lx));

                // Reconstructed tile coordinate
                long reconstructed = ChunkKey.chunkLocalToGlobalTileX(cx, lx);
                assertEquals(tx, reconstructed,
                        String.format("Euclidean reconstruction failed for tx=%d: cx=%d, lx=%d", tx, cx, lx));
            }
        }

        @ParameterizedTest(name = "tx = {0} -> expected cx = {1}, expected lx = {2}")
        @CsvSource({
            "0, 0, 0",
            "-1, -1, 31",
            "-2, -1, 30",
            "-31, -1, 1",
            "-32, -1, 0",
            "-33, -2, 31",
            "-64, -2, 0",
            "-65, -3, 31",
            "31, 0, 31",
            "32, 1, 0",
            "33, 1, 1",
            "-2147483648, -67108864, 0",
            "2147483647, 67108863, 31"
        })
        void testExactEuclideanTileMapping(int tx, int expectedCx, int expectedLx) {
            assertEquals(expectedCx, ChunkKey.tileToChunkX(tx));
            assertEquals(expectedLx, ChunkKey.localTileX(tx));
            assertEquals(expectedCx, ChunkKey.tileToChunkY(tx));
            assertEquals(expectedLx, ChunkKey.localTileY(tx));
        }

        @Test
        @DisplayName("5.2 localTileIndex bijectivity over [0, 31] x [0, 31] -> [0, 1023]")
        void testLocalTileIndexBijectivity() {
            boolean[] seen = new boolean[1024];
            for (int ly = 0; ly < 32; ly++) {
                for (int lx = 0; lx < 32; lx++) {
                    int idx = ChunkKey.localTileIndex(lx, ly);
                    assertTrue(idx >= 0 && idx < 1024);
                    assertFalse(seen[idx], "Collision at local index " + idx);
                    seen[idx] = true;
                }
            }
        }
    }

    @Nested
    @DisplayName("6. Hostile Floats to Continuous World Pipelines")
    class HostileFloatInputs {

        @Test
        @DisplayName("6.1 worldToTileX/Y with NaN, Infinities, and subnormals")
        void testWorldToTileHostileFloats() {
            // In Java, Math.floor(Float.NaN / 32.0f) is NaN, which casts to (int) 0
            assertEquals(0, ChunkKey.worldToTileX(Float.NaN));
            assertEquals(0, ChunkKey.worldToTileY(Float.NaN));

            // Positive Infinity casts to Integer.MAX_VALUE
            assertEquals(Integer.MAX_VALUE, ChunkKey.worldToTileX(Float.POSITIVE_INFINITY));

            // Negative Infinity casts to Integer.MIN_VALUE
            assertEquals(Integer.MIN_VALUE, ChunkKey.worldToTileX(Float.NEGATIVE_INFINITY));

            // Subnormal floats: Float.MIN_VALUE is 1.4e-45f.
            // In IEEE 754 float, (-Float.MIN_VALUE / 32.0f) underflows to -0.0f, whose floor is -0.0 (cast to int is 0).
            assertEquals(0, ChunkKey.worldToTileX(Float.MIN_VALUE));
            assertEquals(0, ChunkKey.worldToTileX(-Float.MIN_VALUE));

            // Any representable non-underflowing negative float strictly rounds down to -1
            assertEquals(-1, ChunkKey.worldToTileX(-1e-6f));

            // Negative zero
            assertEquals(0, ChunkKey.worldToTileX(-0.0f));
            assertEquals(0, ChunkKey.worldToTileY(-0.0f));
        }

        @Test
        @DisplayName("6.2 fromWorld handles hostile float inputs safely without crashing")
        void testFromWorldHostileFloats() {
            // NaN coordinates safely resolve to chunk (0, 0)
            long keyNaN = ChunkKey.fromWorld(Float.NaN, Float.NaN);
            assertEquals(0, ChunkKey.getX(keyNaN));
            assertEquals(0, ChunkKey.getY(keyNaN));

            // Extreme floats
            assertDoesNotThrow(() -> ChunkKey.fromWorld(Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY));
            assertDoesNotThrow(() -> ChunkKey.fromWorld(1e20f, -1e20f));
        }

        @Test
        @DisplayName("6.3 Fine epsilon transitions across chunk and tile borders")
        void testFineEpsilonTransitions() {
            // Micro-step across world origin (0.0f)
            assertEquals(0, ChunkKey.worldToTileX(0.0f));
            assertEquals(-1, ChunkKey.worldToTileX(-1e-6f));

            // Micro-step across tile border (32.0f)
            assertEquals(0, ChunkKey.worldToTileX(31.99999f));
            assertEquals(1, ChunkKey.worldToTileX(32.0f));

            // Micro-step across negative tile border (-32.0f)
            assertEquals(-1, ChunkKey.worldToTileX(-31.99999f));
            assertEquals(-1, ChunkKey.worldToTileX(-32.0f));
            assertEquals(-2, ChunkKey.worldToTileX(-32.00001f));

            // Micro-step across chunk border (1024.0f)
            long keyJustInside0 = ChunkKey.fromWorld(1023.9999f, 1023.9999f);
            assertEquals(0, ChunkKey.getX(keyJustInside0));
            assertEquals(0, ChunkKey.getY(keyJustInside0));

            long keyJustAtChunk1 = ChunkKey.fromWorld(1024.0f, 1024.0f);
            assertEquals(1, ChunkKey.getX(keyJustAtChunk1));
            assertEquals(1, ChunkKey.getY(keyJustAtChunk1));

            // Micro-step across negative chunk border (-1024.0f)
            long keyNegJustInside = ChunkKey.fromWorld(-1024.0f, -1024.0f);
            assertEquals(-1, ChunkKey.getX(keyNegJustInside));
            assertEquals(-1, ChunkKey.getY(keyNegJustInside));

            long keyNegNextChunk = ChunkKey.fromWorld(-1024.0001f, -1024.0001f);
            assertEquals(-2, ChunkKey.getX(keyNegNextChunk));
            assertEquals(-2, ChunkKey.getY(keyNegNextChunk));
        }
    }
}
