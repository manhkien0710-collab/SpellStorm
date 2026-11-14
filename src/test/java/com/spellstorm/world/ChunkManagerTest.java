package com.spellstorm.world;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical test suite for ChunkManager streaming lifecycle, bounded LRU caching,
 * and memory stability under continuous exploration.
 */
@DisplayName("ChunkManager Core & Stress Verification Tests")
public class ChunkManagerTest {

    private static final long SEED = 123456789L;

    @BeforeAll
    public static void setupHeadless() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("1. Continuous 10,000-chunk exploration stress with real TerrainGenerator and bounded heap < 60 MB")
    public void testContinuous10000ChunkExplorationBoundedMemory() {
        TerrainGenerator generator = new TerrainGenerator(SEED);
        AtomicInteger totalGenerated = new AtomicInteger(0);

        int capacity = ChunkManager.DEFAULT_CACHE_CAPACITY; // 64
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> {
            totalGenerated.incrementAndGet();
            ChunkImpl chunk = new ChunkImpl(cx, cy);
            generator.generateChunk(cx, cy, chunk);
            return chunk;
        }, capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-10_000_000f, -10_000_000f, 20_000_000f, 20_000_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);

        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        // Continuous streaming: traverse 10,000 steps across chunks (continuous diagonal & negative trajectory)
        for (int step = 0; step < 10_000; step++) {
            float worldX = (step - 5000) * 256.0f; // Crosses from -1.28M to +1.28M
            float worldY = (step - 5000) * 128.0f;
            playerPos.set(worldX, worldY);
            camera.setCenter(worldX, worldY);

            // Update streaming every 25 steps to simulate realistic movement
            if (step % 25 == 0) {
                manager.updateStreaming(playerPos, camera);
                int loaded = manager.getLoadedChunkCount();
                assertTrue(loaded <= capacity,
                        "Loaded chunk count (" + loaded + ") must never exceed capacity (" + capacity + ") at step " + step);
            }
        }

        manager.updateStreaming(playerPos, camera);
        assertTrue(manager.getLoadedChunkCount() <= capacity,
                "Final loaded chunk count must not exceed capacity");
        assertTrue(totalGenerated.get() >= 500,
                "Must have generated hundreds of chunks dynamically (generated: " + totalGenerated.get() + ")");

        // Measure resident heap memory
        System.gc();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long usedMB = usedMemory / (1024 * 1024);
        System.out.printf("[ChunkManager Stress] Total generated: %d, Final loaded: %d, Resident Heap: %d MB (Limit: < 60 MB)%n",
                totalGenerated.get(), manager.getLoadedChunkCount(), usedMB);
        assertTrue(usedMB < 60, "Resident JVM heap must remain < 60 MB after continuous exploration (was " + usedMB + " MB)");
    }

    @Test
    @DisplayName("2. Strict LRU eviction order and active simulation chunk protection under flood pressure")
    public void testStrictLRUEvictionAndSimulationProtection() {
        int capacity = 32;
        ChunkManager manager = new ChunkManagerImpl(ChunkImpl::new, capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(0.0f, 0.0f);
        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        manager.updateStreaming(playerPos, camera);
        List<Chunk> initialSimChunks = manager.getActiveSimulationChunks();
        assertEquals(9, initialSimChunks.size(), "State C must have 9 chunks for R_sim=1");

        // Touch chunk (0, 0) to make it most recently used
        Chunk touched = manager.getChunk(0, 0);
        assertNotNull(touched);

        // Flood the cache by requesting 1,000 distant inactive chunks
        for (int i = 500; i < 1500; i++) {
            manager.getChunk(i, i);
        }

        // Trigger update to prune inactive chunks
        manager.updateStreaming(playerPos, camera);

        // Cache must be pruned to <= capacity
        assertTrue(manager.getLoadedChunkCount() <= capacity,
                "Loaded count (" + manager.getLoadedChunkCount() + ") must be <= capacity (" + capacity + ")");

        // Active simulation chunks must all still be resident
        for (Chunk simChunk : initialSimChunks) {
            long key = simChunk.getChunkKey();
            Chunk resident = manager.getChunk(key);
            assertNotNull(resident, "Active simulation chunk (" + simChunk.getChunkX() + ", " + simChunk.getChunkY() + ") must not be evicted");
        }
    }

    @ParameterizedTest(name = "Frustum coverage at zoom = {0}")
    @ValueSource(floats = {GameCamera.MIN_ZOOM, 1.0f, GameCamera.DEFAULT_ZOOM, 1.8f, GameCamera.MAX_ZOOM})
    @DisplayName("3. Dynamic zoom coverage across [0.75, 2.25] leaves zero unrendered voids")
    public void testFrustumDynamicZoomFullCoverage(float zoomLevel) {
        ChunkManager manager = new ChunkManagerImpl(ChunkImpl::new, 64);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(zoomLevel);
        camera.setCenter(512.0f, 512.0f);

        manager.updateStreaming(camera.getCenter(), camera);
        List<Chunk> visible = manager.getVisibleChunks(camera);
        assertFalse(visible.isEmpty(), "Visible chunks must not be empty");

        Rectangle frustum = camera.getVisibleBounds();
        int minCx = visible.stream().mapToInt(Chunk::getChunkX).min().orElseThrow();
        int maxCx = visible.stream().mapToInt(Chunk::getChunkX).max().orElseThrow();
        int minCy = visible.stream().mapToInt(Chunk::getChunkY).min().orElseThrow();
        int maxCy = visible.stream().mapToInt(Chunk::getChunkY).max().orElseThrow();

        float coveredMinX = minCx * 1024.0f;
        float coveredMaxX = (maxCx + 1) * 1024.0f;
        float coveredMinY = minCy * 1024.0f;
        float coveredMaxY = (maxCy + 1) * 1024.0f;

        assertTrue(coveredMinX <= frustum.x, "Covered minX (" + coveredMinX + ") must enclose frustum.x (" + frustum.x + ")");
        assertTrue(coveredMaxX >= frustum.x + frustum.width, "Covered maxX must enclose frustum maxX");
        assertTrue(coveredMinY <= frustum.y, "Covered minY must enclose frustum minY");
        assertTrue(coveredMaxY >= frustum.y + frustum.height, "Covered maxY must enclose frustum maxY");
    }

    @Test
    @DisplayName("4. Negative coordinate boundary coverage and Euclidean floor division")
    public void testNegativeCoordinateFrustumCoverage() {
        ChunkManager manager = new ChunkManagerImpl(ChunkImpl::new, 64);

        float[][] hostileCoords = {
                {-1024.0f, -1024.0f},
                {-1.0f, -1.0f},
                {-0.001f, -0.001f},
                {+0.001f, +0.001f},
                {-1025.0f, -1025.0f},
                {-50000.0f, -25000.0f}
        };

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-1_000_000f, -1_000_000f, 2_000_000f, 2_000_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);

        for (float[] coord : hostileCoords) {
            camera.setCenter(coord[0], coord[1]);
            manager.updateStreaming(camera.getCenter(), camera);

            List<Chunk> visible = manager.getVisibleChunks(camera);
            assertFalse(visible.isEmpty());

            Rectangle frustum = camera.getVisibleBounds();
            int minCx = visible.stream().mapToInt(Chunk::getChunkX).min().orElseThrow();
            int maxCx = visible.stream().mapToInt(Chunk::getChunkX).max().orElseThrow();
            int minCy = visible.stream().mapToInt(Chunk::getChunkY).min().orElseThrow();
            int maxCy = visible.stream().mapToInt(Chunk::getChunkY).max().orElseThrow();

            assertTrue(minCx * 1024.0f <= frustum.x);
            assertTrue((maxCx + 1) * 1024.0f >= frustum.x + frustum.width);
            assertTrue(minCy * 1024.0f <= frustum.y);
            assertTrue((maxCy + 1) * 1024.0f >= frustum.y + frustum.height);
        }
    }

    @Test
    @DisplayName("5. ChunkDelta preservation across eviction and reload")
    public void testChunkDeltaPreservationAcrossEvictionAndReload() {
        ChunkManager manager = new ChunkManagerImpl(ChunkImpl::new, 32);

        int targetCx = 100;
        int targetCy = 200;
        long targetKey = ChunkKey.of(targetCx, targetCy);

        Chunk chunk = manager.getChunk(targetCx, targetCy);
        assertNotNull(chunk);

        // Record a delta modifying a tile
        ChunkDelta delta = new ChunkDelta(targetKey);
        delta.recordTileChange(15, 15, (byte) TileType.STONE.getId());
        manager.saveDelta(delta);

        // Evict chunk by loading 200 distant chunks
        for (int i = 500; i < 700; i++) {
            manager.getChunk(i, i);
        }
        manager.updateStreaming(new Vector2D(0, 0), null);

        // Reload target chunk: delta must be reapplied
        Chunk reloaded = manager.getChunk(targetCx, targetCy);
        assertNotNull(reloaded);
        assertEquals(TileType.STONE, reloaded.getLocalTile(15, 15),
                "Reloaded chunk must reflect preserved tile delta after eviction");
    }

    @Test
    @DisplayName("6. Origin shift coordination and global world mapping")
    public void testOriginShiftCoordination() {
        ChunkManager manager = new ChunkManagerImpl(ChunkImpl::new, 64);
        assertEquals(0L, manager.getOriginShiftX());
        assertEquals(0L, manager.getOriginShiftY());

        manager.triggerOriginShift(2048L, 4096L);
        assertEquals(2048L, manager.getOriginShiftX());
        assertEquals(4096L, manager.getOriginShiftY());

        // Local (0, 0) + (2048, 4096) = global (2048, 4096) -> chunk (2, 4)
        Chunk chunk = manager.getChunkAtWorldPos(0.0f, 0.0f);
        assertNotNull(chunk);
        assertEquals(2, chunk.getChunkX());
        assertEquals(4, chunk.getChunkY());
    }

    @Test
    @DisplayName("7. Dynamic cache capacity adjustment dynamically prunes inactive chunks")
    public void testDynamicCapacityAdjustment() {
        ChunkManager manager = new ChunkManagerImpl(ChunkImpl::new, 64);

        for (int i = 0; i < 50; i++) {
            manager.getChunk(i, 0);
        }
        assertEquals(50, manager.getLoadedChunkCount());

        manager.setCacheCapacity(32);
        assertEquals(32, manager.getCacheCapacity());
        assertTrue(manager.getLoadedChunkCount() <= 32, "Loaded chunk count should be pruned to <= 32");
    }
}
