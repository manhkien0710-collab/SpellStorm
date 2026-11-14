package com.spellstorm.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkSeeder Determinism & Decoupling Tests")
public class ChunkSeederTest {

    @Test
    @DisplayName("Identical worldSeed and chunk coords produce bitwise identical seeds across invocations")
    public void testChunkSeedDeterminism() {
        long worldSeed = 1337420999L;
        int cx = 42;
        int cy = -17;

        long seed1 = ChunkSeeder.hashChunkSeed(worldSeed, cx, cy);
        long seed2 = ChunkSeeder.hashChunkSeed(worldSeed, cx, cy);
        long seed3 = ChunkSeeder.hashChunkSeed(worldSeed, cx, cy);

        assertEquals(seed1, seed2, "Chunk seed must be strictly deterministic");
        assertEquals(seed2, seed3, "Chunk seed must not vary across repeated calls");
    }

    @Test
    @DisplayName("Zero PRNG coupling: Generating chunk B decor is completely decoupled from chunk A")
    public void testZeroPRNGCouplingBetweenChunks() {
        long worldSeed = 987654321L;

        // Path 1: Stream chunk (1, 1), consume 10,000 random numbers, then seed chunk (2, 2)
        long chunkSeedA = ChunkSeeder.hashChunkSeed(worldSeed, 1, 1);
        Random rngA = new Random(chunkSeedA);
        for (int i = 0; i < 10_000; i++) {
            rngA.nextFloat();
        }
        long chunkSeedB_path1 = ChunkSeeder.hashChunkSeed(worldSeed, 2, 2);
        Random rngB_path1 = new Random(chunkSeedB_path1);
        int sample1 = rngB_path1.nextInt();

        // Path 2: Directly generate chunk (2, 2) without ever generating chunk (1, 1)
        long chunkSeedB_path2 = ChunkSeeder.hashChunkSeed(worldSeed, 2, 2);
        Random rngB_path2 = new Random(chunkSeedB_path2);
        int sample2 = rngB_path2.nextInt();

        assertEquals(chunkSeedB_path1, chunkSeedB_path2, "Chunk (2, 2) seed must be completely decoupled from chunk (1, 1)");
        assertEquals(sample1, sample2, "PRNG stream in chunk (2, 2) must be bitwise identical regardless of prior chunk generations");
    }

    @Test
    @DisplayName("Avalanche effect: 1-coordinate difference produces high Hamming distance (> 18 bit flips)")
    public void testAvalancheEffectOnCoordinates() {
        long worldSeed = 42L;
        long baseSeed = ChunkSeeder.hashChunkSeed(worldSeed, 10, 10);

        int[][] neighbors = {
            {11, 10}, {9, 10}, {10, 11}, {10, 9}, {11, 11}, {-10, 10}
        };

        for (int[] coord : neighbors) {
            long neighborSeed = ChunkSeeder.hashChunkSeed(worldSeed, coord[0], coord[1]);
            assertNotEquals(baseSeed, neighborSeed, "Neighboring chunk seeds must diverge");

            long diff = baseSeed ^ neighborSeed;
            int bitFlips = Long.bitCount(diff);
            assertTrue(bitFlips >= 18, "Hamming distance must be >= 18 bit flips (observed: " + bitFlips + ")");
        }
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0",
        "-1, -1",
        "-1000, 500",
        "2147483647, -2147483648",
        "-2147483648, 2147483647"
    })
    @DisplayName("Extreme signed 32-bit chunk coordinates hash without errors or negative index crashes")
    public void testExtremeCoordinateSeeding(int cx, int cy) {
        long worldSeed = 0xDEADBEEFCAFEBABEL;
        long seed = ChunkSeeder.hashChunkSeed(worldSeed, cx, cy);
        // Ensure state stepper operates deterministically
        long step = ChunkSeeder.splitMix64(seed);
        assertNotEquals(0L, step);
    }

    @Test
    @DisplayName("HashSalt produces distinct and deterministic salts")
    public void testHashSalt() {
        long seed = 123456789L;
        long salt0 = ChunkSeeder.hashSalt(seed, 0);
        long salt1 = ChunkSeeder.hashSalt(seed, 1);
        long salt2 = ChunkSeeder.hashSalt(seed, 2);

        assertNotEquals(salt0, salt1);
        assertNotEquals(salt1, salt2);
        assertEquals(salt0, ChunkSeeder.hashSalt(seed, 0));
    }
}
