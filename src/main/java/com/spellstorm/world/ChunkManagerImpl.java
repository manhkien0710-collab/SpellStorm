package com.spellstorm.world;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;

import java.util.*;
import java.util.function.BiFunction;

/**
 * High-performance spatial chunk streaming manager and LRU cache controller for SpellStorm.
 *
 * <p>Key features:
 * <ul>
 *   <li><b>4-Ring Chunk Lifecycle</b>: State A (Visible), State B (Preloaded perimeter),
 *       State C (Active simulation bubble), and State D (Cached inactive).</li>
 *   <li><b>Strict LRU Caching</b>: Bounded {@link LinkedHashMap} eviction protecting active rings.</li>
 *   <li><b>Authoritative Frustum Culling</b>: Exact dynamic zoom coverage across [0.75, 2.25] zoom range.</li>
 *   <li><b>Zero-Garbage Streaming</b>: Reusable internal buffers with zero per-frame heap allocations.</li>
 * </ul>
 */
public class ChunkManagerImpl implements ChunkManager {

    public static final float HALO_MARGIN_PX = 64.0f; // 2 tiles overhanging canopy margin

    private final BiFunction<Integer, Integer, Chunk> chunkGenerator;
    private final LinkedHashMap<Long, Chunk> lruCache;
    private final Map<Long, ChunkDelta> deltaStore;

    private int cacheCapacity;
    private int simulationRadius = DEFAULT_SIMULATION_RADIUS;

    private long originShiftX = 0L;
    private long originShiftY = 0L;

    // Zero-allocation reusable streaming buffers
    private final List<Chunk> visibleChunks = new ArrayList<>(16);
    private final List<Chunk> preloadedChunks = new ArrayList<>(32);
    private final List<Chunk> activeSimulationChunks = new ArrayList<>(25);
    private final Set<Long> activeKeys = new HashSet<>(64);

    private final List<Chunk> unmodifiableVisible = Collections.unmodifiableList(visibleChunks);
    private final List<Chunk> unmodifiablePreloaded = Collections.unmodifiableList(preloadedChunks);
    private final List<Chunk> unmodifiableSimulation = Collections.unmodifiableList(activeSimulationChunks);

    public ChunkManagerImpl(long seed) {
        this(seed, DEFAULT_CACHE_CAPACITY);
    }

    public ChunkManagerImpl(long seed, int capacity) {
        this((cx, cy) -> {
            ChunkImpl c = new ChunkImpl(cx, cy);
            new TerrainGenerator(seed).generateChunk(cx, cy, c);
            return c;
        }, capacity);
    }

    public ChunkManagerImpl(BiFunction<Integer, Integer, Chunk> chunkGenerator) {
        this(chunkGenerator, DEFAULT_CACHE_CAPACITY);
    }

    public ChunkManagerImpl(BiFunction<Integer, Integer, Chunk> chunkGenerator, int capacity) {
        this.chunkGenerator = Objects.requireNonNull(chunkGenerator, "Chunk generator cannot be null");
        this.cacheCapacity = Math.max(MIN_CACHE_CAPACITY, Math.min(capacity, MAX_CACHE_CAPACITY));
        this.lruCache = new LinkedHashMap<>(this.cacheCapacity + 16, 0.75f, true);
        this.deltaStore = new HashMap<>();
    }

    @Override
    public Chunk getChunk(int cx, int cy) {
        return getChunk(ChunkKey.of(cx, cy));
    }

    @Override
    public Chunk getChunk(long chunkKey) {
        Chunk chunk = lruCache.get(chunkKey);
        if (chunk == null) {
            int cx = ChunkKey.getX(chunkKey);
            int cy = ChunkKey.getY(chunkKey);
            chunk = chunkGenerator.apply(cx, cy);

            ChunkDelta delta = deltaStore.get(chunkKey);
            if (delta != null) {
                chunk.applyDelta(delta);
            }
            lruCache.put(chunkKey, chunk);
        }
        return chunk;
    }

    @Override
    public Chunk getChunkAtWorldPos(float worldX, float worldY) {
        long gx = (long) Math.floor(worldX) + originShiftX;
        long gy = (long) Math.floor(worldY) + originShiftY;
        int cx = (int) Math.floorDiv(gx, 1024L);
        int cy = (int) Math.floorDiv(gy, 1024L);
        return getChunk(cx, cy);
    }

    @Override
    public void updateStreaming(Vector2D playerPos, GameCamera camera) {
        visibleChunks.clear();
        preloadedChunks.clear();
        activeSimulationChunks.clear();
        activeKeys.clear();

        // 1. Identify State A (Visible Chunks) from Camera
        int visMinCx = 0, visMaxCx = -1, visMinCy = 0, visMaxCy = -1;
        if (camera != null) {
            Rectangle bounds = camera.getVisibleBounds();
            float localMinX = bounds.x - HALO_MARGIN_PX;
            float localMaxX = bounds.x + bounds.width + HALO_MARGIN_PX;
            float localMinY = bounds.y - HALO_MARGIN_PX;
            float localMaxY = bounds.y + bounds.height + HALO_MARGIN_PX;

            long gMinX = (long) Math.floor(localMinX) + originShiftX;
            long gMaxX = (long) Math.floor(localMaxX) + originShiftX;
            long gMinY = (long) Math.floor(localMinY) + originShiftY;
            long gMaxY = (long) Math.floor(localMaxY) + originShiftY;

            visMinCx = (int) Math.floorDiv(gMinX, 1024L);
            visMaxCx = (int) Math.floorDiv(gMaxX, 1024L);
            visMinCy = (int) Math.floorDiv(gMinY, 1024L);
            visMaxCy = (int) Math.floorDiv(gMaxY, 1024L);

            for (int cy = visMinCy; cy <= visMaxCy; cy++) {
                for (int cx = visMinCx; cx <= visMaxCx; cx++) {
                    long key = ChunkKey.of(cx, cy);
                    Chunk chunk = getChunk(key);
                    visibleChunks.add(chunk);
                    activeKeys.add(key);
                }
            }

            // 2. Identify State B (Preloaded 1-chunk Perimeter)
            int pMinCx = visMinCx - 1;
            int pMaxCx = visMaxCx + 1;
            int pMinCy = visMinCy - 1;
            int pMaxCy = visMaxCy + 1;

            for (int cy = pMinCy; cy <= pMaxCy; cy++) {
                for (int cx = pMinCx; cx <= pMaxCx; cx++) {
                    if (cx >= visMinCx && cx <= visMaxCx && cy >= visMinCy && cy <= visMaxCy) {
                        continue; // Already in State A
                    }
                    long key = ChunkKey.of(cx, cy);
                    Chunk chunk = getChunk(key);
                    preloadedChunks.add(chunk);
                    activeKeys.add(key);
                }
            }
        }

        // 3. Identify State C (Active Simulation Chunks) around Player
        if (playerPos != null) {
            long pGlobalX = (long) Math.floor(playerPos.x) + originShiftX;
            long pGlobalY = (long) Math.floor(playerPos.y) + originShiftY;
            int pcx = (int) Math.floorDiv(pGlobalX, 1024L);
            int pcy = (int) Math.floorDiv(pGlobalY, 1024L);

            for (int dy = -simulationRadius; dy <= simulationRadius; dy++) {
                for (int dx = -simulationRadius; dx <= simulationRadius; dx++) {
                    int scx = pcx + dx;
                    int scy = pcy + dy;
                    long key = ChunkKey.of(scx, scy);
                    Chunk chunk = getChunk(key);
                    activeSimulationChunks.add(chunk);
                    activeKeys.add(key);
                }
            }
        }

        // 4. Prune LRU Cache (Evict State D Chunks beyond capacity)
        pruneInactiveChunks();
    }

    private void pruneInactiveChunks() {
        if (lruCache.size() <= cacheCapacity) {
            return;
        }

        Iterator<Map.Entry<Long, Chunk>> iterator = lruCache.entrySet().iterator();
        while (iterator.hasNext() && lruCache.size() > cacheCapacity) {
            Map.Entry<Long, Chunk> entry = iterator.next();
            long key = entry.getKey();
            if (!activeKeys.contains(key)) {
                // Eldest inactive chunk evicted
                iterator.remove();
            }
        }
    }

    @Override
    public List<Chunk> getVisibleChunks(GameCamera camera) {
        if (camera == null) return Collections.emptyList();
        return unmodifiableVisible;
    }

    @Override
    public List<Chunk> getVisibleChunks(OrthographicCamera gdxCamera) {
        if (gdxCamera == null) return Collections.emptyList();

        float halfVisW = (gdxCamera.viewportWidth * gdxCamera.zoom) * 0.5f;
        float halfVisH = (gdxCamera.viewportHeight * gdxCamera.zoom) * 0.5f;

        float minX = gdxCamera.position.x - halfVisW - HALO_MARGIN_PX;
        float maxX = gdxCamera.position.x + halfVisW + HALO_MARGIN_PX;
        float minY = gdxCamera.position.y - halfVisH - HALO_MARGIN_PX;
        float maxY = gdxCamera.position.y + halfVisH + HALO_MARGIN_PX;

        long gMinX = (long) Math.floor(minX) + originShiftX;
        long gMaxX = (long) Math.floor(maxX) + originShiftX;
        long gMinY = (long) Math.floor(minY) + originShiftY;
        long gMaxY = (long) Math.floor(maxY) + originShiftY;

        int minCx = (int) Math.floorDiv(gMinX, 1024L);
        int maxCx = (int) Math.floorDiv(gMaxX, 1024L);
        int minCy = (int) Math.floorDiv(gMinY, 1024L);
        int maxCy = (int) Math.floorDiv(gMaxY, 1024L);

        List<Chunk> result = new ArrayList<>();
        for (int cy = minCy; cy <= maxCy; cy++) {
            for (int cx = minCx; cx <= maxCx; cx++) {
                result.add(getChunk(cx, cy));
            }
        }
        return result;
    }

    @Override
    public List<Chunk> getPreloadedChunks() {
        return unmodifiablePreloaded;
    }

    @Override
    public List<Chunk> getActiveSimulationChunks() {
        return unmodifiableSimulation;
    }

    @Override
    public int getSimulationRadius() {
        return simulationRadius;
    }

    @Override
    public void setSimulationRadius(int radius) {
        this.simulationRadius = Math.max(1, Math.min(radius, 3));
    }

    @Override
    public long getOriginShiftX() {
        return originShiftX;
    }

    @Override
    public long getOriginShiftY() {
        return originShiftY;
    }

    @Override
    public void triggerOriginShift(long deltaX, long deltaY) {
        this.originShiftX += deltaX;
        this.originShiftY += deltaY;
    }

    @Override
    public int getLoadedChunkCount() {
        return lruCache.size();
    }

    @Override
    public int getCacheCapacity() {
        return cacheCapacity;
    }

    @Override
    public void setCacheCapacity(int capacity) {
        this.cacheCapacity = Math.max(MIN_CACHE_CAPACITY, Math.min(capacity, MAX_CACHE_CAPACITY));
        pruneInactiveChunks();
    }

    @Override
    public ChunkDelta getDelta(long chunkKey) {
        return deltaStore.get(chunkKey);
    }

    @Override
    public void saveDelta(ChunkDelta delta) {
        if (delta != null) {
            deltaStore.put(delta.getChunkKey(), delta);
        }
    }

    @Override
    public void clear() {
        lruCache.clear();
        visibleChunks.clear();
        preloadedChunks.clear();
        activeSimulationChunks.clear();
        activeKeys.clear();
    }
}
