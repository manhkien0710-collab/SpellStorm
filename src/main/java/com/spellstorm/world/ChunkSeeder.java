package com.spellstorm.world;

/**
 * Deterministic chunk coordinate PRNG seeding utilities using SplitMix64.
 * Guarantees zero PRNG coupling between chunks and bitwise repeatable decor streams.
 */
public final class ChunkSeeder {

    private ChunkSeeder() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    /**
     * Hashes a 64-bit world seed and signed 32-bit chunk coordinates into an independent 64-bit chunk seed.
     * Uses SplitMix64 avalanching with golden ratio and MurmurHash constants.
     */
    public static long hashChunkSeed(long worldSeed, int cx, int cy) {
        long h = worldSeed + (long) cx * 0x9E3779B97F4A7C15L + (long) cy * 0xC6A4A7935BD1E995L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * Hashes a 64-bit seed with an integer salt (e.g., candidate decor index or layer ID).
     */
    public static long hashSalt(long seed, int salt) {
        long h = seed + (long) salt * 0x9E3779B97F4A7C15L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /**
     * Advances a SplitMix64 PRNG state and returns an avalanched 64-bit long.
     */
    public static long splitMix64(long state) {
        long z = state + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }
}
