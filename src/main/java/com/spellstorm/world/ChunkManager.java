package com.spellstorm.world;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;

import java.util.List;

/**
 * Spatial chunk streaming manager and LRU cache controller for SpellStorm.
 *
 * <p>Governs chunk lifecycles across 4 rings:
 * <ul>
 *   <li><b>State A (Visible)</b>: Directly intersecting the effective camera frustum.</li>
 *   <li><b>State B (Preloaded)</b>: 1-chunk predictive perimeter ring around visible frustum.</li>
 *   <li><b>State C (Active Simulation)</b>: Chebyshev distance &le; R_sim around player.</li>
 *   <li><b>State D (Cached Inactive)</b>: Retained in bounded LRU cache up to capacity.</li>
 * </ul>
 */
public interface ChunkManager extends FloatingOriginAware {

    int DEFAULT_CACHE_CAPACITY = 64;
    int MIN_CACHE_CAPACITY = 32;
    int MAX_CACHE_CAPACITY = 128;
    int DEFAULT_SIMULATION_RADIUS = 1; // 3x3 chunks = 3072x3072 px

    /**
     * Retrieves chunk at signed chunk coordinates (cx, cy), generating on demand if absent.
     */
    Chunk getChunk(int cx, int cy);

    /**
     * Fast retrieval by 64-bit packed chunk key.
     */
    Chunk getChunk(long chunkKey);

    /**
     * Retrieves chunk enclosing continuous local world coordinates (worldX, worldY).
     */
    Chunk getChunkAtWorldPos(float worldX, float worldY);

    /**
     * Updates streaming rings, preloading, simulation active sets, and LRU pruning.
     */
    void updateStreaming(Vector2D playerPos, GameCamera camera);

    /**
     * Returns chunks currently visible in GameCamera frustum (expanded by 64px halo margin).
     */
    List<Chunk> getVisibleChunks(GameCamera camera);

    /**
     * Authoritative visibility query directly from LibGDX OrthographicCamera frustum.
     */
    List<Chunk> getVisibleChunks(OrthographicCamera gdxCamera);

    /**
     * Returns preloaded 1-chunk perimeter chunks (State B).
     */
    List<Chunk> getPreloadedChunks();

    /**
     * Returns active simulation chunks (State C, Chebyshev distance &le; R_sim).
     */
    List<Chunk> getActiveSimulationChunks();

    /**
     * Active simulation radius in chunks (default 1).
     */
    int getSimulationRadius();

    void setSimulationRadius(int radius);

    /**
     * Global origin shift offsets.
     */
    long getOriginShiftX();

    long getOriginShiftY();

    /**
     * Updates world origin offsets during atomic floating-origin rebasing.
     */
    void triggerOriginShift(long deltaX, long deltaY);

    @Override
    default void onOriginShift(long deltaX, long deltaY) {
        triggerOriginShift(deltaX, deltaY);
    }

    /**
     * Cache metrics.
     */
    int getLoadedChunkCount();

    int getCacheCapacity();

    void setCacheCapacity(int capacity);

    /**
     * Mutable state delta management.
     */
    ChunkDelta getDelta(long chunkKey);

    void saveDelta(ChunkDelta delta);

    /**
     * Clears all cached chunks and buffers.
     */
    void clear();
}
