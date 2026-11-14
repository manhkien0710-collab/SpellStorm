package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkManager Bounded LRU Cache & Memory Stress Tests")
public class ChunkManagerBoundedCacheAndMemoryTest {

    @Test
    @DisplayName("Strict LRU eviction and protection of active chunks")
    public void testStrictLRUEvictionAndProtectionOfActiveChunks() {
        int capacity = 32;
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(0.0f, 0.0f);
        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        manager.updateStreaming(playerPos, camera);
        int initialActiveCount = manager.getLoadedChunkCount();
        assertTrue(initialActiveCount <= capacity);

        // Query distant chunks (State D) to fill cache beyond capacity
        for (int i = 100; i < 200; i++) {
            manager.getChunk(i, i);
        }

        // Trigger update to prune inactive chunks
        manager.updateStreaming(playerPos, camera);

        // Cache count must not exceed capacity
        assertTrue(manager.getLoadedChunkCount() <= capacity, "Cache size must be strictly bounded to capacity");

        // Active visible chunks must still be resident in cache
        for (Chunk v : manager.getVisibleChunks(camera)) {
            assertNotNull(manager.getChunk(v.getChunkKey()), "Visible chunks must be protected from eviction");
        }
    }

    @Test
    @DisplayName("10,000-chunk exploration stress: bounded cache size and resident heap < 60 MB")
    public void test10000ChunkExplorationBoundedMemoryStress() {
        int capacity = 64;
        AtomicInteger generatedChunks = new AtomicInteger(0);
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> {
            generatedChunks.incrementAndGet();
            return new ChunkImpl(cx, cy);
        }, capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-10_000_000f, -10_000_000f, 20_000_000f, 20_000_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);

        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        // Simulate exploring 10,000 chunks along diagonal
        for (int step = 0; step < 10_000; step++) {
            playerPos.set(step * 1024.0f, step * 1024.0f);
            camera.setCenter(playerPos.x, playerPos.y);

            if (step % 50 == 0) {
                manager.updateStreaming(playerPos, camera);
                assertTrue(manager.getLoadedChunkCount() <= capacity,
                        "Loaded chunk count must never exceed " + capacity + " at step " + step);
            }
        }

        manager.updateStreaming(playerPos, camera);
        assertTrue(manager.getLoadedChunkCount() <= capacity);
        assertTrue(generatedChunks.get() >= 2_000, "Must have generated thousands of chunks (was " + generatedChunks.get() + ")");

        // Force GC and measure heap
        System.gc();
        long usedMemory = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long usedMB = usedMemory / (1024 * 1024);
        System.out.println("[Memory Stress Test] Used resident heap: " + usedMB + " MB (Limit: < 60 MB)");
        assertTrue(usedMB < 60, "Resident heap must remain < 60 MB after 10,000-chunk exploration (was " + usedMB + " MB)");
    }

    @Test
    @DisplayName("ChunkDelta preservation across eviction and reload")
    public void testChunkDeltaPreservationAcrossEvictionAndReload() {
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), 32);

        int targetCx = 50;
        int targetCy = 50;
        long targetKey = ChunkKey.of(targetCx, targetCy);

        Chunk chunk = manager.getChunk(targetCx, targetCy);
        assertNotNull(chunk);

        // Record a delta
        ChunkDelta delta = new ChunkDelta(targetKey);
        delta.recordTileChange(10, 10, (byte) TileType.STONE.getId());
        manager.saveDelta(delta);

        // Flood cache to evict target chunk
        for (int i = 1000; i < 1100; i++) {
            manager.getChunk(i, i);
        }
        manager.updateStreaming(new Vector2D(0, 0), null);

        // Reload target chunk
        Chunk reloaded = manager.getChunk(targetCx, targetCy);
        assertNotNull(reloaded);
        assertEquals(TileType.STONE, reloaded.getLocalTile(10, 10), "Reloaded chunk must reflect preserved delta");

        ChunkDelta savedDelta = manager.getDelta(targetKey);
        assertNotNull(savedDelta);
        assertEquals((byte) TileType.STONE.getId(), savedDelta.getTileModifications().get((short) (10 * 32 + 10)));
    }

    @Test
    @DisplayName("Cache capacity adjustment dynamically prunes inactive chunks")
    public void testCapacityAdjustmentPrunesImmediately() {
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), 64);

        // Fill with 50 chunks
        for (int i = 0; i < 50; i++) {
            manager.getChunk(i, 0);
        }
        assertEquals(50, manager.getLoadedChunkCount());

        // Lower capacity to 32
        manager.setCacheCapacity(32);
        assertEquals(32, manager.getCacheCapacity());
        assertTrue(manager.getLoadedChunkCount() <= 32, "Loaded chunk count should be pruned to <= 32");
    }
}
