package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3: Obstacle Spatial Partitioning & Cross-Chunk Collision Tests")
public class ObstacleSpatialQueryTest {

    private static final long SEED = 133742L;
    private ChunkManager chunkManager;
    private FloatingOriginManager originManager;
    private InfiniteWorld world;

    @BeforeEach
    void setUp() {
        originManager = new FloatingOriginManager();
        chunkManager = new ChunkManagerImpl(SEED, 64);
        world = new InfiniteWorld(chunkManager, originManager);
    }

    @Test
    @DisplayName("1. Cross-chunk obstacle collision: Entity in chunk (1,0) detects obstacle in chunk (0,0)")
    void testObstacleOverlappingChunkBoundaryDetected() {
        // Prepare empty grass chunks
        ChunkImpl chunk0 = new ChunkImpl(0, 0);
        ChunkImpl chunk1 = new ChunkImpl(1, 0);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                chunk0.setLocalTile(x, y, TileType.GRASS);
                chunk1.setLocalTile(x, y, TileType.GRASS);
            }
        }

        // Add solid rock obstacle near right seam of chunk 0: localX = 1015, localY = 500, radius = 14
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 1015.0f, 500.0f, true);
        chunk0.addObstacle(rock);

        // Mock in chunk manager
        chunkManager.saveDelta(new ChunkDelta(ChunkKey.of(0, 0))); // ensure initialized

        // Manual mock world with custom chunk manager for deterministic seam test
        ChunkManager mockManager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), 64) {
            @Override
            public Chunk getChunk(int cx, int cy) {
                if (cx == 0 && cy == 0) return chunk0;
                if (cx == 1 && cy == 0) return chunk1;
                return super.getChunk(cx, cy);
            }
        };
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        // Entity in chunk 1 at worldX = 1024 + 10 = 1034 (localX = 10 in chunk 1), radius = 18
        // Penetration: dist between centers = (1034 - 1015) = 19. Total radius = 18 + 14 = 32 > 19!
        assertFalse(testWorld.isTraversable(1034.0f, 500.0f, 18.0f),
                "Entity in chunk (1,0) must collide with obstacle near boundary in chunk (0,0)");

        // Moving further away (e.g. worldX = 1060) is traversable
        assertTrue(testWorld.isTraversable(1060.0f, 500.0f, 18.0f),
                "Entity out of range should be traversable");
    }

    @Test
    @DisplayName("2. 4-chunk corner seam obstacle collision around junction vertex (1024, 1024)")
    void testObstacleCrossing4ChunkCornerJunction() {
        ChunkImpl c00 = new ChunkImpl(0, 0);
        ChunkImpl c10 = new ChunkImpl(1, 0);
        ChunkImpl c01 = new ChunkImpl(0, 1);
        ChunkImpl c11 = new ChunkImpl(1, 1);

        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                c00.setLocalTile(x, y, TileType.GRASS);
                c10.setLocalTile(x, y, TileType.GRASS);
                c01.setLocalTile(x, y, TileType.GRASS);
                c11.setLocalTile(x, y, TileType.GRASS);
            }
        }

        // Place obstacle in chunk (0,0) near corner: localX = 1020, localY = 1020, radius = 12
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 1020.0f, 1020.0f, true);
        c00.addObstacle(rock);

        ChunkManager mockManager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), 64) {
            @Override
            public Chunk getChunk(int cx, int cy) {
                if (cx == 0 && cy == 0) return c00;
                if (cx == 1 && cy == 0) return c10;
                if (cx == 0 && cy == 1) return c01;
                if (cx == 1 && cy == 1) return c11;
                return super.getChunk(cx, cy);
            }
        };
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        // Query located in diagonally opposite chunk (1, 1) at world (1026, 1026) with radius = 10
        // Collision center dist = sqrt((1026-1020)^2 + (1026-1020)^2) = sqrt(72) ~ 8.49.
        // Radius sum = 12 + 10 = 22 > 8.49. Collision!
        assertFalse(testWorld.isTraversable(1026.0f, 1026.0f, 10.0f),
                "Obstacle in chunk (0,0) must block entity across diagonal corner in chunk (1,1)");
    }

    @Test
    @DisplayName("3. Negative chunk coordinate collision queries with floor division")
    void testNegativeChunkBoundaryObstacleQuery() {
        ChunkImpl cNeg = new ChunkImpl(-1, -1);
        for (int y = 0; y < 32; y++) {
            for (int x = 0; x < 32; x++) {
                cNeg.setLocalTile(x, y, TileType.GRASS);
            }
        }

        // Place rock in chunk (-1, -1) at local (500, 500). World = (-1024 + 500, -1024 + 500) = (-524, -524)
        Obstacle rock = new Obstacle(DecoType.ROCK, 500.0f, 500.0f, true);
        cNeg.addObstacle(rock);

        ChunkManager mockManager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), 64) {
            @Override
            public Chunk getChunk(int cx, int cy) {
                if (cx == -1 && cy == -1) return cNeg;
                return super.getChunk(cx, cy);
            }
        };
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        assertFalse(testWorld.isTraversable(-524.0f, -524.0f, 10.0f),
                "Obstacle in negative chunk (-1, -1) must be detected at world (-524, -524)");
        assertTrue(testWorld.isTraversable(-400.0f, -400.0f, 10.0f),
                "Free space in negative chunk must be traversable");
    }

    @Test
    @DisplayName("4. Zero heap allocation during continuous spatial traversability queries")
    void testZeroAllocationSpatialQueryPerformance() {
        // Preload chunks around origin
        for (int cy = -1; cy <= 1; cy++) {
            for (int cx = -1; cx <= 1; cx++) {
                chunkManager.getChunk(cx, cy);
            }
        }

        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            // JVM does not support thread memory allocation tracking; skip assertion
            return;
        }

        // Warmup JIT
        for (int i = 0; i < 5000; i++) {
            world.isTraversable(100.0f, 100.0f, 16.0f);
        }

        long threadId = Thread.currentThread().threadId();
        long beforeAlloc = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 10000; i++) {
            world.isTraversable(100.0f, 100.0f, 16.0f);
        }
        long afterAlloc = bean.getThreadAllocatedBytes(threadId);
        long allocated = afterAlloc - beforeAlloc;

        assertEquals(0L, allocated,
                "Continuous traversability checks must allocate zero young-gen heap memory (allocated: " + allocated + " bytes)");
    }

    @Test
    @DisplayName("5. Obstacle suppression via ChunkDelta in active chunks")
    void testObstacleSuppressionViaDeltaInActiveChunks() {
        Chunk chunk = chunkManager.getChunk(0, 0);
        int initialCount = chunk.getObstacleCount();
        assertTrue(initialCount > 0, "Chunk (0,0) should have natural decorations");

        Obstacle firstObs = chunk.getObstacle(0);
        float localX = firstObs.getPosition().x;
        float localY = firstObs.getPosition().y;

        // Suppress first obstacle
        ChunkDelta delta = new ChunkDelta(chunk.getChunkKey());
        delta.recordObstacleRemoved(0);
        chunk.applyDelta(delta);

        assertEquals(initialCount - 1, chunk.getObstacleCount(),
                "Obstacle count should decrease by 1 after delta suppression");
    }

    @Test
    @DisplayName("6. Spatial query throughput benchmark (>= 500,000 queries/second)")
    void testSpatialQueryThroughputBenchmark() {
        // Pre-warm chunks
        for (int cy = -1; cy <= 1; cy++) {
            for (int cx = -1; cx <= 1; cx++) {
                chunkManager.getChunk(cx, cy);
            }
        }

        int iterations = 100_000;
        // Warmup
        for (int i = 0; i < 20_000; i++) {
            world.isTraversable(i % 1000, (i * 3) % 1000, 16.0f);
        }

        long startNs = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            world.isTraversable(i % 1000, (i * 3) % 1000, 16.0f);
        }
        long elapsedNs = System.nanoTime() - startNs;
        double seconds = elapsedNs / 1_000_000_000.0;
        double qps = iterations / seconds;

        System.out.printf("[Spatial Throughput] %d queries in %.4f s = %.0f queries/sec\n",
                iterations, seconds, qps);
        assertTrue(qps >= 500_000.0,
                "Spatial queries should exceed 500,000 QPS (actual: " + (long) qps + " QPS)");
    }

    @Test
    @DisplayName("7. Obstacle position float precision strictly bounded in [0.0f, 1024.0f) at extreme chunk coordinates")
    void testObstaclePositionFloatPrecisionAtExtremeChunk() {
        TerrainGenerator gen = new TerrainGenerator(SEED);
        int extremeCx = 1_000_000;
        int extremeCy = -1_000_000;

        List<Obstacle> decos = gen.generateDecorations(extremeCx, extremeCy);
        assertFalse(decos.isEmpty(), "Extreme chunk should generate decorations");

        for (Obstacle o : decos) {
            float x = o.getPosition().x;
            float y = o.getPosition().y;
            assertTrue(x >= 0.0f && x < 1024.0f, "Obstacle X must be strictly chunk-local: " + x);
            assertTrue(y >= 0.0f && y < 1024.0f, "Obstacle Y must be strictly chunk-local: " + y);

            // Sub-pixel spacing ULP test
            float ulpX = Math.ulp(x);
            float ulpY = Math.ulp(y);
            assertTrue(ulpX <= 1.0f / 8192.0f, "ULP X must guarantee high sub-pixel precision: " + ulpX);
            assertTrue(ulpY <= 1.0f / 8192.0f, "ULP Y must guarantee high sub-pixel precision: " + ulpY);
        }
    }

    @Test
    @DisplayName("8. Visible obstacle cache produces zero allocations in normal frames")
    void testVisibleObstacleCacheZeroAllocationInNormalFrames() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setClampingEnabled(false);
        camera.setCenter(500.0f, 500.0f);

        // Prime cache
        List<Obstacle> obs1 = world.getVisibleObstacles(camera);
        assertNotNull(obs1);

        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            return;
        }

        long threadId = Thread.currentThread().threadId();
        // Warmup
        for (int i = 0; i < 100; i++) {
            world.getVisibleObstacles(camera);
        }

        // Repeated queries without origin shift
        long beforeAlloc = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 1000; i++) {
            world.getVisibleObstacles(camera);
        }
        long afterAlloc = bean.getThreadAllocatedBytes(threadId);
        List<Obstacle> obs2 = world.getVisibleObstacles(camera);
        assertSame(obs1, obs2, "Must reuse same unmodifiable wrapper instance");
        assertEquals(0L, afterAlloc - beforeAlloc, "Repeated getVisibleObstacles must allocate zero bytes");
    }
}
