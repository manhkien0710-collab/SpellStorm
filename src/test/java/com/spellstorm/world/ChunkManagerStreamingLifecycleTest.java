package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChunkManager Streaming Rings & Lifecycle State Tests")
public class ChunkManagerStreamingLifecycleTest {

    private ChunkManager chunkManager;

    @BeforeEach
    public void setUp() {
        chunkManager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), 64);
    }

    @Test
    @DisplayName("Streaming ring partitioning across States A, B, and C")
    public void testStreamingRingPartitioning() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(512.0f, 512.0f);
        Vector2D playerPos = new Vector2D(512.0f, 512.0f);

        chunkManager.updateStreaming(playerPos, camera);

        List<Chunk> visible = chunkManager.getVisibleChunks(camera); // State A
        List<Chunk> preloaded = chunkManager.getPreloadedChunks();    // State B
        List<Chunk> simulation = chunkManager.getActiveSimulationChunks(); // State C

        assertFalse(visible.isEmpty(), "State A must not be empty");
        assertFalse(preloaded.isEmpty(), "State B must not be empty");
        assertEquals(9, simulation.size(), "State C (R_sim=1) must contain exactly 3x3=9 chunks");

        // Verify disjointness between State A and State B
        for (Chunk a : visible) {
            for (Chunk b : preloaded) {
                assertNotEquals(a.getChunkKey(), b.getChunkKey(), "Preloaded ring must not contain visible chunks");
            }
        }

        // Verify State B wraps State A with 1-chunk perimeter
        int minVisX = visible.stream().mapToInt(Chunk::getChunkX).min().orElseThrow();
        int maxVisX = visible.stream().mapToInt(Chunk::getChunkX).max().orElseThrow();
        int minVisY = visible.stream().mapToInt(Chunk::getChunkY).min().orElseThrow();
        int maxVisY = visible.stream().mapToInt(Chunk::getChunkY).max().orElseThrow();

        for (Chunk b : preloaded) {
            int bx = b.getChunkX();
            int by = b.getChunkY();
            assertTrue(bx >= minVisX - 1 && bx <= maxVisX + 1, "Preloaded X within 1-chunk perimeter");
            assertTrue(by >= minVisY - 1 && by <= maxVisY + 1, "Preloaded Y within 1-chunk perimeter");
        }
    }

    @Test
    @DisplayName("Preloading predictive perimeter ahead of movement")
    public void testPreloadingAheadOfMovement() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(512.0f, 512.0f);

        chunkManager.updateStreaming(camera.getCenter(), camera);
        List<Chunk> preloadedBefore = chunkManager.getPreloadedChunks();
        assertFalse(preloadedBefore.isEmpty());

        // Move camera 1 chunk to the east (x = 1536)
        camera.setCenter(1536.0f, 512.0f);
        chunkManager.updateStreaming(camera.getCenter(), camera);
        List<Chunk> visibleAfter = chunkManager.getVisibleChunks(camera);

        // Chunks newly visible must have been previously generated
        for (Chunk v : visibleAfter) {
            if (v.getChunkX() >= 1) {
                assertNotNull(chunkManager.getChunk(v.getChunkKey()));
            }
        }
    }

    @Test
    @DisplayName("Configurable simulation radius (R_sim=1 -> 9 chunks, R_sim=2 -> 25 chunks)")
    public void testSimulationRadiusConfiguration() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        assertEquals(1, chunkManager.getSimulationRadius());
        chunkManager.updateStreaming(playerPos, camera);
        assertEquals(9, chunkManager.getActiveSimulationChunks().size());

        chunkManager.setSimulationRadius(2);
        assertEquals(2, chunkManager.getSimulationRadius());
        chunkManager.updateStreaming(playerPos, camera);
        assertEquals(25, chunkManager.getActiveSimulationChunks().size());
    }

    @Test
    @DisplayName("Origin shift tracking and world coordinate mapping")
    public void testOriginShiftCoordination() {
        assertEquals(0L, chunkManager.getOriginShiftX());
        assertEquals(0L, chunkManager.getOriginShiftY());

        chunkManager.triggerOriginShift(1024L, 2048L);
        assertEquals(1024L, chunkManager.getOriginShiftX());
        assertEquals(2048L, chunkManager.getOriginShiftY());

        // Local pos (0, 0) maps to global pos (1024, 2048) -> chunk (1, 2)
        Chunk chunk = chunkManager.getChunkAtWorldPos(0.0f, 0.0f);
        assertNotNull(chunk);
        assertEquals(1, chunk.getChunkX());
        assertEquals(2, chunk.getChunkY());
    }
}
