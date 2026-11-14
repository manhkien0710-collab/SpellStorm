package com.spellstorm.world;

import com.spellstorm.world.noise.FractalNoise;
import com.spellstorm.world.noise.SimplexNoise;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1 Adversarial Empirical Stress Tests")
public class Milestone1AdversarialEmpiricalStressTest {

    // =========================================================================
    // TASK 1: ChunkSeeder Empirical Verification
    // =========================================================================

    /**
     * Test record representing simulated procedural decor output for a chunk.
     */
    private record ChunkDecorOutput(int chunkX, int chunkY, long[] decorTokens) {}

    private ChunkDecorOutput generateSimulatedDecor(long worldSeed, int cx, int cy, int decorCount) {
        long chunkSeed = ChunkSeeder.hashChunkSeed(worldSeed, cx, cy);
        long[] tokens = new long[decorCount];
        for (int i = 0; i < decorCount; i++) {
            long saltHash = ChunkSeeder.hashSalt(chunkSeed, i);
            long roll1 = ChunkSeeder.splitMix64(saltHash);
            long roll2 = ChunkSeeder.splitMix64(roll1);
            long roll3 = ChunkSeeder.splitMix64(roll2);
            // Combine rolls into a 64-bit verification token
            tokens[i] = roll1 ^ (roll2 << 17) ^ (roll3 >>> 19);
        }
        return new ChunkDecorOutput(cx, cy, tokens);
    }

    @Test
    @DisplayName("Task 1.1: PRNG independence across random order permutations (1,000 chunks, 10 shuffled runs)")
    public void testPRNGIndependenceAcrossRandomOrderPermutations() {
        long worldSeed = 0x2718281828459045L;
        int numChunks = 1000;
        int decorPerChunk = 50;

        // Generate 1,000 distinct chunk coordinate pairs across diverse regions
        List<int[]> chunkCoords = new ArrayList<>(numChunks);
        Random coordRng = new Random(424242L);
        for (int i = 0; i < numChunks; i++) {
            // Span positive, negative, zero, and large coordinates
            int cx, cy;
            if (i < 100) {
                cx = (i % 10) - 5;
                cy = (i / 10) - 5;
            } else if (i < 300) {
                cx = coordRng.nextInt(-100_000, 100_000);
                cy = coordRng.nextInt(-100_000, 100_000);
            } else if (i < 600) {
                cx = coordRng.nextInt(-10_000_000, 10_000_000);
                cy = coordRng.nextInt(-10_000_000, 10_000_000);
            } else {
                cx = coordRng.nextInt(Integer.MIN_VALUE + 1000, Integer.MAX_VALUE - 1000);
                cy = coordRng.nextInt(Integer.MIN_VALUE + 1000, Integer.MAX_VALUE - 1000);
            }
            chunkCoords.add(new int[]{cx, cy});
        }

        // Sequential baseline generation
        Map<Long, ChunkDecorOutput> sequentialBaseline = new HashMap<>(numChunks);
        for (int[] coord : chunkCoords) {
            long key = ChunkKey.of(coord[0], coord[1]);
            ChunkDecorOutput output = generateSimulatedDecor(worldSeed, coord[0], coord[1], decorPerChunk);
            sequentialBaseline.put(key, output);
        }
        assertEquals(numChunks, sequentialBaseline.size(), "All 1,000 chunk keys must be distinct");

        // Execute 10 separate randomized permutations and verify bitwise identical decor
        for (int permutation = 1; permutation <= 10; permutation++) {
            List<int[]> shuffledCoords = new ArrayList<>(chunkCoords);
            Collections.shuffle(shuffledCoords, new Random(1000L + permutation * 31337L));

            for (int[] coord : shuffledCoords) {
                long key = ChunkKey.of(coord[0], coord[1]);
                ChunkDecorOutput shuffledOutput = generateSimulatedDecor(worldSeed, coord[0], coord[1], decorPerChunk);
                ChunkDecorOutput baselineOutput = sequentialBaseline.get(key);

                assertNotNull(baselineOutput, "Shuffled chunk must exist in baseline");
                assertEquals(baselineOutput.chunkX(), shuffledOutput.chunkX());
                assertEquals(baselineOutput.chunkY(), shuffledOutput.chunkY());
                assertArrayEquals(baselineOutput.decorTokens(), shuffledOutput.decorTokens(),
                        "Decor generation for chunk (" + coord[0] + ", " + coord[1]
                                + ") must be bitwise identical regardless of generation order in permutation " + permutation);
            }
        }
    }

    @Test
    @DisplayName("Task 1.2: Avalanche behavior and bit distribution across signed 32-bit limits")
    public void testAvalancheAndBitDistributionAcrossSigned32BitLimits() {
        int[] limitCoords = {
                Integer.MIN_VALUE,
                Integer.MIN_VALUE + 1,
                -1_000_000,
                -65536,
                -1,
                0,
                1,
                65536,
                1_000_000,
                Integer.MAX_VALUE - 1,
                Integer.MAX_VALUE
        };

        long[] testWorldSeeds = {
                Long.MIN_VALUE,
                -1L,
                0L,
                1L,
                133742L,
                0x5555555555555555L,
                0xAAAAAAAAAAAAAAAAL,
                Long.MAX_VALUE
        };

        // 1. Avalanche: Single bit-flip test on cx, cy, and worldSeed
        int totalBitFlipTests = 0;
        long totalHammingDistance = 0;
        int minHammingDistance = Integer.MAX_VALUE;
        int maxHammingDistance = Integer.MIN_VALUE;

        for (long worldSeed : testWorldSeeds) {
            for (int cx : limitCoords) {
                for (int cy : limitCoords) {
                    long baseHash = ChunkSeeder.hashChunkSeed(worldSeed, cx, cy);

                    // Flip each of the 32 bits of cx
                    for (int bit = 0; bit < 32; bit++) {
                        int flippedCx = cx ^ (1 << bit);
                        long flippedHash = ChunkSeeder.hashChunkSeed(worldSeed, flippedCx, cy);
                        int hamming = Long.bitCount(baseHash ^ flippedHash);
                        totalHammingDistance += hamming;
                        totalBitFlipTests++;
                        minHammingDistance = Math.min(minHammingDistance, hamming);
                        maxHammingDistance = Math.max(maxHammingDistance, hamming);

                        assertTrue(hamming >= 16,
                                "Hamming distance must be >= 16 for cx bit-flip (observed " + hamming
                                        + " for cx=" + cx + ", cy=" + cy + ", bit=" + bit + ")");
                    }

                    // Flip each of the 32 bits of cy
                    for (int bit = 0; bit < 32; bit++) {
                        int flippedCy = cy ^ (1 << bit);
                        long flippedHash = ChunkSeeder.hashChunkSeed(worldSeed, cx, flippedCy);
                        int hamming = Long.bitCount(baseHash ^ flippedHash);
                        totalHammingDistance += hamming;
                        totalBitFlipTests++;
                        minHammingDistance = Math.min(minHammingDistance, hamming);
                        maxHammingDistance = Math.max(maxHammingDistance, hamming);

                        assertTrue(hamming >= 16,
                                "Hamming distance must be >= 16 for cy bit-flip (observed " + hamming
                                        + " for cx=" + cx + ", cy=" + cy + ", bit=" + bit + ")");
                    }
                }
            }
        }

        double avgHamming = (double) totalHammingDistance / totalBitFlipTests;
        System.out.println("[ChunkSeeder Avalanche Test] Tested " + totalBitFlipTests + " bit-flips across extreme limits.");
        System.out.println("  Min Hamming: " + minHammingDistance + ", Max Hamming: " + maxHammingDistance + ", Avg: " + String.format("%.2f", avgHamming));
        assertTrue(avgHamming >= 30.0 && avgHamming <= 34.0,
                "Average Hamming distance must be centered near 32.0 (observed: " + avgHamming + ")");

        // 2. Bit distribution: Test frequency of 1s in each of the 64 bit positions across 100,000 samples
        int sampleCount = 100_000;
        int[] bitCounts = new int[64];
        Random rand = new Random(987654321L);

        for (int i = 0; i < sampleCount; i++) {
            long wSeed = rand.nextLong();
            int cx = rand.nextInt();
            int cy = rand.nextInt();
            long hash = ChunkSeeder.hashChunkSeed(wSeed, cx, cy);

            for (int bit = 0; bit < 64; bit++) {
                if (((hash >>> bit) & 1L) == 1L) {
                    bitCounts[bit]++;
                }
            }
        }

        // Each bit position should be set ~50% of the time (e.g. 50,000 +/- 1,500 = [48.5%, 51.5%])
        for (int bit = 0; bit < 64; bit++) {
            double ratio = (double) bitCounts[bit] / sampleCount;
            assertTrue(ratio >= 0.485 && ratio <= 0.515,
                    "Bit " + bit + " distribution biased: observed ratio " + ratio + " (" + bitCounts[bit] + "/" + sampleCount + ")");
        }
    }

    // =========================================================================
    // TASK 2: SimplexNoise & FractalNoise Empirical Stress Testing
    // =========================================================================

    @Test
    @DisplayName("Task 2.1: Stress test 1,000,000 samples across extreme ranges [-10^7, +10^7] with strict bounds and zero NaN/Inf")
    public void testSimplexAndFractalNoise1MillionSamplesExtremeRange() {
        SimplexNoise simplex = new SimplexNoise(0x3141592653589793L);
        FractalNoise fractal = new FractalNoise(0x3141592653589793L, 4, 0.5f, 2.0f, 0.005f);

        int totalSamples = 1_000_000;
        Random rng = new Random(1234567890L);

        float minSimplex = Float.MAX_VALUE;
        float maxSimplex = -Float.MAX_VALUE;
        float minFractal = Float.MAX_VALUE;
        float maxFractal = -Float.MAX_VALUE;
        float minFractalNorm = Float.MAX_VALUE;
        float maxFractalNorm = -Float.MAX_VALUE;
        float minRidge = Float.MAX_VALUE;
        float maxRidge = -Float.MAX_VALUE;

        // Structured edge-case coordinates
        double[] criticalValues = {
                -10_000_000.0, -9_999_999.5, -5_000_000.0, -1_000_000.0,
                -65536.0, -1000.0, -256.0, -32.0, -1.0, -0.5, -0.0001, 0.0,
                0.0001, 0.5, 1.0, 32.0, 256.0, 1000.0, 65536.0,
                1_000_000.0, 5_000_000.0, 9_999_999.5, 10_000_000.0
        };

        int sampleIndex = 0;

        // 1. Systematic critical combinations
        for (double cxVal : criticalValues) {
            for (double cyVal : criticalValues) {
                if (sampleIndex >= totalSamples) break;
                sampleIndex++;

                float sVal = simplex.eval(cxVal, cyVal);
                assertFalse(Float.isNaN(sVal), "Simplex value must not be NaN at (" + cxVal + ", " + cyVal + ")");
                assertFalse(Float.isInfinite(sVal), "Simplex value must not be Infinite at (" + cxVal + ", " + cyVal + ")");
                assertTrue(sVal >= -1.0f && sVal <= 1.0f, "Simplex value out of bounds: " + sVal);

                float fVal = fractal.getNoise(cxVal, cyVal);
                assertFalse(Float.isNaN(fVal), "Fractal value must not be NaN at (" + cxVal + ", " + cyVal + ")");
                assertFalse(Float.isInfinite(fVal), "Fractal value must not be Infinite at (" + cxVal + ", " + cyVal + ")");
                assertTrue(fVal >= -1.0f && fVal <= 1.0f, "Fractal value out of bounds: " + fVal);

                float fnVal = fractal.getNoiseNormalized(cxVal, cyVal);
                assertFalse(Float.isNaN(fnVal), "Fractal normalized must not be NaN at (" + cxVal + ", " + cyVal + ")");
                assertTrue(fnVal >= 0.0f && fnVal <= 1.0f, "Fractal normalized out of bounds: " + fnVal);

                float rVal = fractal.getRidgeNoise(cxVal, cyVal);
                assertFalse(Float.isNaN(rVal), "Ridge noise must not be NaN at (" + cxVal + ", " + cyVal + ")");
                assertTrue(rVal >= 0.0f && rVal <= 1.0f, "Ridge noise out of bounds: " + rVal);

                minSimplex = Math.min(minSimplex, sVal);
                maxSimplex = Math.max(maxSimplex, sVal);
                minFractal = Math.min(minFractal, fVal);
                maxFractal = Math.max(maxFractal, fVal);
                minFractalNorm = Math.min(minFractalNorm, fnVal);
                maxFractalNorm = Math.max(maxFractalNorm, fnVal);
                minRidge = Math.min(minRidge, rVal);
                maxRidge = Math.max(maxRidge, rVal);
            }
        }

        // 2. Uniform and perturbed random sampling across [-10^7, +10^7]
        while (sampleIndex < totalSamples) {
            sampleIndex++;
            double x = (rng.nextDouble() * 2.0 - 1.0) * 10_000_000.0;
            double y = (rng.nextDouble() * 2.0 - 1.0) * 10_000_000.0;

            float sVal = simplex.eval(x, y);
            assertFalse(Float.isNaN(sVal), "Simplex value must not be NaN at (" + x + ", " + y + ")");
            assertFalse(Float.isInfinite(sVal), "Simplex value must not be Infinite at (" + x + ", " + y + ")");
            assertTrue(sVal >= -1.0f && sVal <= 1.0f, "Simplex value out of bounds: " + sVal);

            float fVal = fractal.getNoise(x, y);
            assertFalse(Float.isNaN(fVal), "Fractal value must not be NaN at (" + x + ", " + y + ")");
            assertFalse(Float.isInfinite(fVal), "Fractal value must not be Infinite at (" + x + ", " + y + ")");
            assertTrue(fVal >= -1.0f && fVal <= 1.0f, "Fractal value out of bounds: " + fVal);

            float fnVal = fractal.getNoiseNormalized(x, y);
            assertFalse(Float.isNaN(fnVal), "Fractal normalized must not be NaN at (" + x + ", " + y + ")");
            assertTrue(fnVal >= 0.0f && fnVal <= 1.0f, "Fractal normalized out of bounds: " + fnVal);

            float rVal = fractal.getRidgeNoise(x, y);
            assertFalse(Float.isNaN(rVal), "Ridge noise must not be NaN at (" + x + ", " + y + ")");
            assertTrue(rVal >= 0.0f && rVal <= 1.0f, "Ridge noise out of bounds: " + rVal);

            minSimplex = Math.min(minSimplex, sVal);
            maxSimplex = Math.max(maxSimplex, sVal);
            minFractal = Math.min(minFractal, fVal);
            maxFractal = Math.max(maxFractal, fVal);
            minFractalNorm = Math.min(minFractalNorm, fnVal);
            maxFractalNorm = Math.max(maxFractalNorm, fnVal);
            minRidge = Math.min(minRidge, rVal);
            maxRidge = Math.max(maxRidge, rVal);
        }

        System.out.println("[Noise 1M Stress Test] Successfully tested " + totalSamples + " samples in [-10^7, +10^7].");
        System.out.println("  Simplex Range:  [" + minSimplex + ", " + maxSimplex + "]");
        System.out.println("  Fractal Range:  [" + minFractal + ", " + maxFractal + "]");
        System.out.println("  FractalNorm:    [" + minFractalNorm + ", " + maxFractalNorm + "]");
        System.out.println("  Ridge Range:    [" + minRidge + ", " + maxRidge + "]");

        assertTrue(minSimplex <= -0.80f, "Simplex must cover negative range (min: " + minSimplex + ")");
        assertTrue(maxSimplex >= 0.80f, "Simplex must cover positive range (max: " + maxSimplex + ")");
        assertTrue(minFractalNorm <= 0.15f, "FractalNorm must cover lower range (min: " + minFractalNorm + ")");
        assertTrue(maxFractalNorm >= 0.85f, "FractalNorm must cover upper range (max: " + maxFractalNorm + ")");
        assertTrue(minRidge >= 0.0f && maxRidge >= 0.70f, "Ridge noise range check");
    }

    @Test
    @DisplayName("Task 2.2: Verify C^1 continuity across chunk seams (lx=31 -> lx=0) at 10,000 random chunk borders")
    public void testC1ContinuityAcross10000RandomChunkBorders() {
        long worldSeed = 0xCAFEBABE1337BEEFL;
        BiomeClassifier classifier = new BiomeClassifier(worldSeed);
        SimplexNoise noise = new SimplexNoise(worldSeed);

        int totalBorders = 10_000;
        Random rng = new Random(5432109876L);

        float maxTempSeamDelta = 0.0f;
        float maxMoistSeamDelta = 0.0f;
        float maxSeamVsInternalDiff = 0.0f;
        double maxContinuousDerivDiff = 0.0;

        for (int i = 0; i < totalBorders; i++) {
            // Coordinate partitioning across various scales
            int cx, cy;
            if (i < 500) {
                // Near quadrant borders
                cx = (i % 20) - 10;
                cy = (i / 20) - 10;
            } else if (i < 2000) {
                cx = rng.nextInt(-1000, 1000);
                cy = rng.nextInt(-1000, 1000);
            } else {
                cx = rng.nextInt(-100_000, 100_000);
                cy = rng.nextInt(-100_000, 100_000);
            }

            boolean isEastWest = (i % 2 == 0);

            if (isEastWest) {
                // Seam between chunk cx (lx=31) and chunk cx+1 (lx=0)
                int ly = rng.nextInt(32);
                int gy = cy * 32 + ly;

                int gxSeamLeft = cx * 32 + 31;
                int gxSeamRight = (cx + 1) * 32 + 0;
                int gxInternalLeft = cx * 32 + 30;

                assertEquals(gxSeamLeft + 1, gxSeamRight, "Global tile coordinate must advance by exactly 1 tile across seam");

                float tLeft = classifier.getTemperature(gxSeamLeft, gy);
                float tRight = classifier.getTemperature(gxSeamRight, gy);
                float tInternal = classifier.getTemperature(gxInternalLeft, gy);

                float mLeft = classifier.getMoisture(gxSeamLeft, gy);
                float mRight = classifier.getMoisture(gxSeamRight, gy);
                float mInternal = classifier.getMoisture(gxInternalLeft, gy);

                float dTempSeam = Math.abs(tRight - tLeft);
                float dTempInternal = Math.abs(tLeft - tInternal);

                float dMoistSeam = Math.abs(mRight - mLeft);
                float dMoistInternal = Math.abs(mLeft - mInternal);

                maxTempSeamDelta = Math.max(maxTempSeamDelta, dTempSeam);
                maxMoistSeamDelta = Math.max(maxMoistSeamDelta, dMoistSeam);
                maxSeamVsInternalDiff = Math.max(maxSeamVsInternalDiff, Math.abs(dTempSeam - dTempInternal));
                maxSeamVsInternalDiff = Math.max(maxSeamVsInternalDiff, Math.abs(dMoistSeam - dMoistInternal));

                // Strict assertion: Seam step must be bounded (< 0.035 for 0.005 scale)
                assertTrue(dTempSeam < 0.035f, "Temperature seam step too large at border " + i + ": " + dTempSeam);
                assertTrue(dMoistSeam < 0.035f, "Moisture seam step too large at border " + i + ": " + dMoistSeam);

                // Continuous C^1 derivative test at the physical seam boundary (in tile coordinates * scale)
                double borderX = (cx + 1) * 32.0; // border line in tile units
                double eps = 0.0005;
                float vMinus = noise.eval((borderX - eps) * 0.005, gy * 0.005);
                float vMid = noise.eval(borderX * 0.005, gy * 0.005);
                float vPlus = noise.eval((borderX + eps) * 0.005, gy * 0.005);

                double derivLeft = (vMid - vMinus) / eps;
                double derivRight = (vPlus - vMid) / eps;
                double derivDiff = Math.abs(derivRight - derivLeft);
                maxContinuousDerivDiff = Math.max(maxContinuousDerivDiff, derivDiff);

                assertTrue(derivDiff < 0.02, "Derivative jump at border " + i + ": " + derivDiff);
            } else {
                // Seam between chunk cy (ly=31) and chunk cy+1 (ly=0)
                int lx = rng.nextInt(32);
                int gx = cx * 32 + lx;

                int gySeamBottom = cy * 32 + 31;
                int gySeamTop = (cy + 1) * 32 + 0;
                int gyInternalBottom = cy * 32 + 30;

                assertEquals(gySeamBottom + 1, gySeamTop, "Global tile coordinate must advance by exactly 1 tile across seam");

                float tBottom = classifier.getTemperature(gx, gySeamBottom);
                float tTop = classifier.getTemperature(gx, gySeamTop);
                float tInternal = classifier.getTemperature(gx, gyInternalBottom);

                float mBottom = classifier.getMoisture(gx, gySeamBottom);
                float mTop = classifier.getMoisture(gx, gySeamTop);
                float mInternal = classifier.getMoisture(gx, gyInternalBottom);

                float dTempSeam = Math.abs(tTop - tBottom);
                float dTempInternal = Math.abs(tBottom - tInternal);

                float dMoistSeam = Math.abs(mTop - mBottom);
                float dMoistInternal = Math.abs(mBottom - mInternal);

                maxTempSeamDelta = Math.max(maxTempSeamDelta, dTempSeam);
                maxMoistSeamDelta = Math.max(maxMoistSeamDelta, dMoistSeam);
                maxSeamVsInternalDiff = Math.max(maxSeamVsInternalDiff, Math.abs(dTempSeam - dTempInternal));
                maxSeamVsInternalDiff = Math.max(maxSeamVsInternalDiff, Math.abs(dMoistSeam - dMoistInternal));

                assertTrue(dTempSeam < 0.035f, "Temperature seam step too large at border " + i + ": " + dTempSeam);
                assertTrue(dMoistSeam < 0.035f, "Moisture seam step too large at border " + i + ": " + dMoistSeam);

                // Continuous C^1 derivative test at physical seam boundary
                double borderY = (cy + 1) * 32.0;
                double eps = 0.0005;
                float vMinus = noise.eval(gx * 0.005, (borderY - eps) * 0.005);
                float vMid = noise.eval(gx * 0.005, borderY * 0.005);
                float vPlus = noise.eval(gx * 0.005, (borderY + eps) * 0.005);

                double derivBottom = (vMid - vMinus) / eps;
                double derivTop = (vPlus - vMid) / eps;
                double derivDiff = Math.abs(derivTop - derivBottom);
                maxContinuousDerivDiff = Math.max(maxContinuousDerivDiff, derivDiff);

                assertTrue(derivDiff < 0.02, "Derivative jump at border " + i + ": " + derivDiff);
            }
        }

        System.out.println("[Seam Continuity Test] Tested " + totalBorders + " random chunk borders.");
        System.out.println("  Max Temperature Seam Delta: " + String.format("%.5f", maxTempSeamDelta));
        System.out.println("  Max Moisture Seam Delta:    " + String.format("%.5f", maxMoistSeamDelta));
        System.out.println("  Max Seam vs Internal Diff:  " + String.format("%.5f", maxSeamVsInternalDiff));
        System.out.println("  Max Continuous Deriv Diff:  " + String.format("%.6f", maxContinuousDerivDiff));
    }

    @Test
    @DisplayName("Adversarial Boundary Investigation: Numerical stability and boundary limits of fastFloor in SimplexNoise")
    public void testAdversarialNoiseBoundaryLimits() {
        SimplexNoise noise = new SimplexNoise(42L);

        // Within specified range [-10^7, +10^7], noise is fully functional
        float v1e7 = noise.eval(10_000_000.0, 10_000_000.0);
        assertFalse(Float.isNaN(v1e7));
        assertFalse(Float.isInfinite(v1e7));

        // Test up to 10^8
        float v1e8 = noise.eval(100_000_000.0, 100_000_000.0);
        assertFalse(Float.isNaN(v1e8));
        assertFalse(Float.isInfinite(v1e8));

        // Test up to 10^9
        float v1e9 = noise.eval(1_000_000_000.0, 1_000_000_000.0);
        assertFalse(Float.isNaN(v1e9));
        assertFalse(Float.isInfinite(v1e9));

        // Test beyond 2^31 / 1.732 (~ 1.24 x 10^9) where fastFloor int-casting saturates
        float v2e9 = noise.eval(2_000_000_000.0, 2_000_000_000.0);
        System.out.println("[Adversarial Limit Probe] eval(2.0e9, 2.0e9) = " + v2e9);
        // Note: Even if int-cast saturates, does it throw an exception or produce NaN?
        assertFalse(Float.isNaN(v2e9), "Must never produce NaN even at saturation");
        assertFalse(Float.isInfinite(v2e9), "Must never produce Inf even at saturation");
    }
}
