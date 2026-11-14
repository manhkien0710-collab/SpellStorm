package com.spellstorm.world;

/**
 * Immutable utility contract for chunk coordinate packing, unpacking, and Euclidean coordinate transformations.
 *
 * <p>Packs signed 32-bit chunk coordinates (cx, cy) into a single primitive 64-bit {@code long} key,
 * supporting an exploration range of [-2^31, 2^31 - 1] chunks (+-2.2 x 10^12 pixels) with zero heap allocation.
 *
 * <p>All spatial divisions strictly follow Euclidean arithmetic ({@link Math#floorDiv} and {@link Math#floorMod})
 * guaranteeing smooth, gap-free tiling across positive and negative world quadrants.
 */
public final class ChunkKey {

    /** Tiles per chunk along one axis (32x32 tiles per chunk). */
    public static final int CHUNK_SIZE = 32;

    /** Pixel dimension of a single square tile. */
    public static final float TILE_SIZE = 32.0f;

    /** World pixel span of an entire chunk (32 * 32.0f = 1024.0f px). */
    public static final float CHUNK_SPAN = 1024.0f;

    /** Total tiles per chunk (32 * 32 = 1024). */
    public static final int TILES_PER_CHUNK = CHUNK_SIZE * CHUNK_SIZE;

    private ChunkKey() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }

    // =========================================================================
    // Bitwise Packing & Unpacking
    // =========================================================================

    /**
     * Packs signed 32-bit chunk coordinates into a 64-bit primitive long.
     * High 32 bits: cx, Low 32 bits: cy.
     *
     * @param cx chunk X coordinate [-2^31, 2^31 - 1]
     * @param cy chunk Y coordinate [-2^31, 2^31 - 1]
     * @return 64-bit packed chunk key
     */
    public static long of(int cx, int cy) {
        return (((long) cx) << 32) | (((long) cy) & 0xFFFFFFFFL);
    }

    /**
     * Extracts the signed 32-bit chunk X coordinate from a packed key.
     */
    public static int getChunkX(long key) {
        return (int) (key >> 32);
    }

    /**
     * Extracts the signed 32-bit chunk Y coordinate from a packed key.
     */
    public static int getChunkY(long key) {
        return (int) key;
    }

    /**
     * Alias for {@link #getChunkX(long)} for Project Master Spec compatibility.
     */
    public static int getX(long key) {
        return getChunkX(key);
    }

    /**
     * Alias for {@link #getChunkY(long)} for Project Master Spec compatibility.
     */
    public static int getY(long key) {
        return getChunkY(key);
    }

    // =========================================================================
    // Euclidean Coordinate Transformations
    // =========================================================================

    /**
     * Converts a continuous world X coordinate (in pixels) to a global tile X index.
     * Uses floor division so negative world coordinates correctly round down (e.g. -0.01f -> -1).
     */
    public static int worldToTileX(float worldX) {
        return (int) Math.floor(worldX / TILE_SIZE);
    }

    /**
     * Converts a continuous world Y coordinate (in pixels) to a global tile Y index.
     */
    public static int worldToTileY(float worldY) {
        return (int) Math.floor(worldY / TILE_SIZE);
    }

    /**
     * Converts a global tile X index to its enclosing chunk X coordinate.
     * Strictly uses Euclidean floor division (e.g. -1 -> -1, -32 -> -1, -33 -> -2).
     */
    public static int tileToChunkX(int tx) {
        return Math.floorDiv(tx, CHUNK_SIZE);
    }

    /**
     * Converts a global tile Y index to its enclosing chunk Y coordinate.
     */
    public static int tileToChunkY(int ty) {
        return Math.floorDiv(ty, CHUNK_SIZE);
    }

    /**
     * Computes the non-negative local tile X offset [0, 31] within its enclosing chunk.
     * Strictly uses Euclidean modulo (e.g. -1 -> 31, -32 -> 0, -33 -> 31).
     */
    public static int localTileX(int tx) {
        return Math.floorMod(tx, CHUNK_SIZE);
    }

    /**
     * Computes the non-negative local tile Y offset [0, 31] within its enclosing chunk.
     */
    public static int localTileY(int ty) {
        return Math.floorMod(ty, CHUNK_SIZE);
    }

    /**
     * Computes the flat 1D array index [0, 1023] for a local tile within a chunk.
     *
     * @param lx local tile X [0, 31]
     * @param ly local tile Y [0, 31]
     * @return flat index in range [0, 1023]
     */
    public static int localTileIndex(int lx, int ly) {
        return ly * CHUNK_SIZE + lx;
    }

    /**
     * Reconstructs the global tile X coordinate from chunk coordinate and local tile offset.
     * Uses 64-bit widening to prevent integer overflow for extreme chunk coordinates.
     */
    public static long chunkLocalToGlobalTileX(int cx, int lx) {
        return ((long) cx) * ((long) CHUNK_SIZE) + (long) lx;
    }

    /**
     * Reconstructs the global tile Y coordinate from chunk coordinate and local tile offset.
     */
    public static long chunkLocalToGlobalTileY(int cy, int ly) {
        return ((long) cy) * ((long) CHUNK_SIZE) + (long) ly;
    }

    /**
     * Computes the world center X coordinate (in pixels) for a global tile.
     */
    public static float tileToWorldCenterX(int tx) {
        return (tx + 0.5f) * TILE_SIZE;
    }

    /**
     * Computes the world center Y coordinate (in pixels) for a global tile.
     */
    public static float tileToWorldCenterY(int ty) {
        return (ty + 0.5f) * TILE_SIZE;
    }

    /**
     * Computes the world origin (minimum bound) X coordinate for a chunk.
     * Widens operand to long to guarantee overflow safety up to +-2.2 x 10^12 pixels.
     */
    public static long chunkToWorldOriginX(int cx) {
        return ((long) cx) * 1024L;
    }

    /**
     * Computes the world origin (minimum bound) Y coordinate for a chunk.
     */
    public static long chunkToWorldOriginY(int cy) {
        return ((long) cy) * 1024L;
    }

    /**
     * Fast convenience factory: Packs chunk key directly from global tile coordinates.
     */
    public static long fromTile(int tx, int ty) {
        return of(tileToChunkX(tx), tileToChunkY(ty));
    }

    /**
     * Fast convenience factory: Packs chunk key directly from continuous world coordinates.
     */
    public static long fromWorld(float wx, float wy) {
        return of(tileToChunkX(worldToTileX(wx)), tileToChunkY(worldToTileY(wy)));
    }

    /**
     * Formats packed key as human-readable coordinate string "(cx, cy)".
     */
    public static String toString(long key) {
        return "(" + getChunkX(key) + ", " + getChunkY(key) + ")";
    }
}
