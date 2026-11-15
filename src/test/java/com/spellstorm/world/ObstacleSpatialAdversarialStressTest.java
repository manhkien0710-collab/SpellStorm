package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Adversarial Stress Test: Obstacle Spatial Partitioning & Seam Queries")
public class ObstacleSpatialAdversarialStressTest {

    private FloatingOriginManager originManager;
    private ChunkManager chunkManager;
    private InfiniteWorld world;

    @BeforeEach
    void setUp() {
        originManager = new FloatingOriginManager();
        chunkManager = new ChunkManagerImpl(424242L, 64);
        world = new InfiniteWorld(chunkManager, originManager);
    }

    @Test
    @DisplayName("Challenge 1.1: Obstacle at localX in [960, 1024) in Chunk (1, 0) must collide with entity at same position")
    void testObstacleNearRightSeamInChunk10() {
        ChunkImpl chunk10 = new ChunkImpl(1, 0);
        // Place an obstacle at localX = 980.0f, localY = 500.0f, radius = 14.0f
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 980.0f, 500.0f, true);
        chunk10.addObstacle(rock);

        // Direct chunk method call
        boolean localCollides = chunk10.collidesWithLocalObstacle(980.0f, 500.0f, 10.0f);
        assertTrue(localCollides,
                "Direct chunk query: Entity at (980, 500) directly on top of obstacle in chunk (1,0) MUST collide, but collidesWithLocalObstacle returned false!");

        ChunkManager mockManager = new ChunkManagerImpl((cx, cy) -> {
            if (cx == 1 && cy == 0) return chunk10;
            return new ChunkImpl(cx, cy);
        }, 64);
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        // World coordinates: chunk 1 starts at 1024. Obstacle is at world (2004, 500).
        boolean traversable = testWorld.isTraversable(2004.0f, 500.0f, 10.0f);
        assertFalse(traversable,
                "World traversability: Entity at world (2004, 500) directly on top of obstacle in chunk (1,0) MUST NOT be traversable!");

        // Phantom obstacle test in chunk (0, 0) at world (980, 500)
        boolean phantomTraversable = testWorld.isTraversable(980.0f, 500.0f, 10.0f);
        assertTrue(phantomTraversable,
                "World traversability: Entity at world (980, 500) in chunk (0,0) MUST be traversable, but was blocked by phantom collision!");
    }

    @Test
    @DisplayName("Challenge 1.2: Obstacle at localX in [0, 64) in Chunk (-1, -1) must collide with entity at same position")
    void testObstacleNearLeftSeamInChunkNeg1Neg1() {
        ChunkImpl chunkNeg = new ChunkImpl(-1, -1);
        // Place an obstacle at localX = 30.0f, localY = 30.0f, radius = 14.0f
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 30.0f, 30.0f, true);
        chunkNeg.addObstacle(rock);

        // Direct chunk method call
        boolean localCollides = chunkNeg.collidesWithLocalObstacle(30.0f, 30.0f, 10.0f);
        assertTrue(localCollides,
                "Direct chunk query: Entity at (30, 30) directly on top of obstacle in chunk (-1,-1) MUST collide, but collidesWithLocalObstacle returned false!");

        ChunkManager mockManager = new ChunkManagerImpl((cx, cy) -> {
            if (cx == -1 && cy == -1) return chunkNeg;
            return new ChunkImpl(cx, cy);
        }, 64);
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        // World coordinates: chunk -1 starts at -1024. Obstacle is at world (-994, -994).
        boolean traversable = testWorld.isTraversable(-994.0f, -994.0f, 10.0f);
        assertFalse(traversable,
                "World traversability: Entity at world (-994, -994) directly on top of obstacle in chunk (-1,-1) MUST NOT be traversable!");
    }

    @Test
    @DisplayName("Challenge 1.3: 4-chunk corner junction at vertex (-1024, -1024) with obstacle in chunk (-1, -1) at corner (15, 15)")
    void testCornerJunctionNegativeJunctionObstacle() {
        // Vertex (-1024, -1024) is the junction of chunks (-2, -2), (-1, -2), (-2, -1), and (-1, -1).
        // Chunk (-1, -1) corner at vertex (-1024, -1024) has local coordinates (0, 0).
        ChunkImpl chunkNeg1Neg1 = new ChunkImpl(-1, -1);
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 15.0f, 15.0f, true);
        chunkNeg1Neg1.addObstacle(rock);

        ChunkManager mockManager = new ChunkManagerImpl((cx, cy) -> {
            if (cx == -1 && cy == -1) return chunkNeg1Neg1;
            return new ChunkImpl(cx, cy);
        }, 64);
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        // World coordinates of this obstacle: (-1024 + 15, -1024 + 15) = (-1009, -1009).
        // Entity at world (-1009, -1009) with radius 10 MUST collide!
        boolean traversable = testWorld.isTraversable(-1009.0f, -1009.0f, 10.0f);
        assertFalse(traversable,
                "Entity at (-1009, -1009) directly on top of obstacle in corner of chunk (-1,-1) MUST collide!");
    }

    @Test
    @DisplayName("Challenge 1.4: Obstacles at borders [0, 64) and [960, 1024) across arbitrary negative and positive chunk coordinates")
    void testObstaclesAtBordersAcrossArbitraryQuadrants() {
        int[][] testChunks = {
                {5, -7},
                {-100, -200},
                {12, 34},
                {-45, 88}
        };

        // Positions near borders and corners:
        // [0, 64): 20.0f, 32.0f
        // [960, 1024): 980.0f, 1010.0f
        float[][] testOffsets = {
                {20.0f, 512.0f},   // West border [0, 64)
                {1000.0f, 512.0f}, // East border [960, 1024)
                {512.0f, 25.0f},   // South border [0, 64)
                {512.0f, 995.0f},  // North border [960, 1024)
                {16.0f, 16.0f},    // SW corner
                {1008.0f, 16.0f},  // SE corner
                {16.0f, 1008.0f},  // NW corner
                {1008.0f, 1008.0f} // NE corner
        };

        for (int[] chunkCoord : testChunks) {
            int cx = chunkCoord[0];
            int cy = chunkCoord[1];
            long originX = (long) cx * 1024L;
            long originY = (long) cy * 1024L;

            for (float[] offset : testOffsets) {
                float localX = offset[0];
                float localY = offset[1];

                ChunkImpl chunk = new ChunkImpl(cx, cy);
                Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, localX, localY, true);
                chunk.addObstacle(rock);

                // Direct chunk query
                boolean localCollides = chunk.collidesWithLocalObstacle(localX, localY, 10.0f);
                assertTrue(localCollides,
                        "Direct collision in chunk (" + cx + "," + cy + ") at (" + localX + "," + localY + ") must be true");

                ChunkManager customManager = new ChunkManagerImpl((qcx, qcy) -> {
                    if (qcx == cx && qcy == cy) return chunk;
                    return new ChunkImpl(qcx, qcy);
                }, 64);
                InfiniteWorld customWorld = new InfiniteWorld(customManager, originManager);

                // 1. World collision directly on top of obstacle
                float worldX = (float) originX + localX;
                float worldY = (float) originY + localY;
                assertFalse(customWorld.isTraversable(worldX, worldY, 10.0f),
                        "Entity at world (" + worldX + "," + worldY + ") on top of obstacle must collide");

                // 2. Far away in the same chunk: must NOT collide
                float farLocalX = (localX > 512.0f) ? 200.0f : 800.0f;
                float farLocalY = (localY > 512.0f) ? 200.0f : 800.0f;
                float farWorldX = (float) originX + farLocalX;
                float farWorldY = (float) originY + farLocalY;
                assertTrue(customWorld.isTraversable(farWorldX, farWorldY, 10.0f),
                        "Entity far away at world (" + farWorldX + "," + farWorldY + ") must be traversable");

                // 3. Phantom collision check: In neighboring chunk across border, far from edge
                float neighborWorldX = (float) (originX - 1024L) + 500.0f;
                float neighborWorldY = (float) (originY - 1024L) + 500.0f;
                assertTrue(customWorld.isTraversable(neighborWorldX, neighborWorldY, 10.0f),
                        "Neighboring chunk at world (" + neighborWorldX + "," + neighborWorldY + ") must not have phantom collision");
            }
        }
    }

    @Test
    @DisplayName("Challenge 1.5: Cross-seam obstacle collision detection across adjacent chunks in positive and negative quadrants")
    void testCrossSeamObstacleCollisionDetection() {
        // Obstacle in chunk (5, -7) placed right at west boundary (localX = 4.0f, localY = 500.0f, radius = 14.0f)
        int cx = 5;
        int cy = -7;
        ChunkImpl chunkCenter = new ChunkImpl(cx, cy);
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 4.0f, 500.0f, true);
        chunkCenter.addObstacle(rock);

        ChunkManager mockManager = new ChunkManagerImpl((qcx, qcy) -> {
            if (qcx == cx && qcy == cy) return chunkCenter;
            return new ChunkImpl(qcx, qcy);
        }, 64);
        InfiniteWorld customWorld = new InfiniteWorld(mockManager, originManager);

        long originX = (long) cx * 1024L;
        long originY = (long) cy * 1024L;

        // Entity is in adjacent chunk (4, -7) at worldX = originX - 5.0f (which is localX = 1019 in chunk (4, -7))
        // Distance to obstacle center = |-5.0f - 4.0f| = 9.0f < combined radius (10 + 14 = 24.0f)
        float entityWorldX = (float) originX - 5.0f;
        float entityWorldY = (float) originY + 500.0f;
        assertFalse(customWorld.isTraversable(entityWorldX, entityWorldY, 10.0f),
                "Entity crossing border from chunk (4, -7) MUST collide with obstacle near west boundary of chunk (5, -7)!");

        // Entity in chunk (4, -7) at 40px away from boundary: distance = |-40.0f - 4.0f| = 44.0f > 24.0f: MUST be traversable!
        float safeEntityWorldX = (float) originX - 40.0f;
        assertTrue(customWorld.isTraversable(safeEntityWorldX, entityWorldY, 10.0f),
                "Entity 40px away from boundary in chunk (4, -7) MUST be traversable (no phantom collision)!");
    }

    @Test
    @DisplayName("Challenge 2: Zero heap allocation during continuous spatial queries across non-zero chunks")
    void testZeroAllocationAcrossNonZeroChunks() {
        // Pre-warm chunks around chunk (1, 1)
        for (int cy = 0; cy <= 2; cy++) {
            for (int cx = 0; cx <= 2; cx++) {
                chunkManager.getChunk(cx, cy);
            }
        }

        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            return;
        }

        // Warmup in chunk (1, 1) where chunkKey is non-zero
        for (int i = 0; i < 5000; i++) {
            world.isTraversable(1500.0f + (i % 200), 1500.0f + (i % 200), 16.0f);
        }

        long threadId = Thread.currentThread().threadId();
        long beforeAlloc = bean.getThreadAllocatedBytes(threadId);
        for (int i = 0; i < 10000; i++) {
            world.isTraversable(1500.0f + (i % 200), 1500.0f + (i % 200), 16.0f);
        }
        long afterAlloc = bean.getThreadAllocatedBytes(threadId);
        long allocated = afterAlloc - beforeAlloc;

        assertEquals(0L, allocated,
                "Queries in non-zero chunks must allocate 0 bytes, but allocated: " + allocated + " bytes");
    }

    @Test
    @DisplayName("Challenge 2.2: 50,000 spatial queries across 16 different non-zero chunks strictly allocating 0 heap bytes")
    void testZeroAllocation50kSpatialQueriesAcross16NonZeroChunks() {
        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            return;
        }

        // 16 non-zero chunks distributed across all 4 quadrants
        int[][] sixteenChunks = {
                {1, 1}, {1, 2}, {2, 1}, {2, 2},
                {-1, -1}, {-1, -2}, {-2, -1}, {-2, -2},
                {5, -7}, {-100, -200}, {10, 10}, {10, 11},
                {-5, 5}, {15, -15}, {7, 8}, {-8, -7}
        };

        // Cache capacity 64 easily retains all 16 chunks
        ChunkManager cm = new ChunkManagerImpl(424242L, 64);
        InfiniteWorld testWorld = new InfiniteWorld(cm, originManager);

        // Pre-warm cache: load all 16 chunks and their 8-neighbors
        for (int[] coord : sixteenChunks) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    cm.getChunk(coord[0] + dx, coord[1] + dy);
                }
            }
        }

        // Warmup loop: 10,000 queries cycling through all 16 chunks
        for (int i = 0; i < 10000; i++) {
            int[] c = sixteenChunks[i & 15];
            float wx = (float) ((long) c[0] * 1024L) + 200.0f + (i % 600);
            float wy = (float) ((long) c[1] * 1024L) + 200.0f + (i % 600);
            testWorld.isTraversable(wx, wy, 16.0f);
        }

        long threadId = Thread.currentThread().threadId();
        long beforeAlloc = bean.getThreadAllocatedBytes(threadId);

        // 50,000 queries across the 16 non-zero chunks
        for (int i = 0; i < 50000; i++) {
            int[] c = sixteenChunks[i & 15]; // Rapid alternation forces cache lookup and changes memoization key
            float wx = (float) ((long) c[0] * 1024L) + 150.0f + (i % 700);
            float wy = (float) ((long) c[1] * 1024L) + 150.0f + (i % 700);
            testWorld.isTraversable(wx, wy, 16.0f);
        }

        long afterAlloc = bean.getThreadAllocatedBytes(threadId);
        long allocatedDelta = afterAlloc - beforeAlloc;

        assertEquals(0L, allocatedDelta,
                "50,000 queries across 16 non-zero chunks must allocate strictly 0 heap bytes, but allocated: "
                        + allocatedDelta + " bytes!");
    }

    @Test
    @DisplayName("Challenge 3.1: Rapidly query 500 distinct chunks with capacity 32: cache size stays strictly bounded <= 32")
    void testLruCacheStress500DistinctChunks() {
        int capacity = 32;
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-10_000_000f, -10_000_000f, 20_000_000f, 20_000_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);

        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        // Traverse 500 distinct chunks along positive and negative coordinates
        for (int i = -250; i < 250; i++) {
            float worldX = (float) i * 1024.0f + 512.0f;
            float worldY = (float) i * 512.0f + 512.0f;
            playerPos.set(worldX, worldY);
            camera.setCenter(worldX, worldY);

            manager.updateStreaming(playerPos, camera);

            int loaded = manager.getLoadedChunkCount();
            assertTrue(loaded <= capacity,
                    "Loaded chunk count (" + loaded + ") must never exceed capacity (" + capacity + ") at step " + i);
        }

        // Final check
        assertTrue(manager.getLoadedChunkCount() <= capacity,
                "Final loaded chunk count must remain <= " + capacity);
    }

    @Test
    @DisplayName("Challenge 3.2: Oldest inactive chunks are evicted and memoization does not return stale chunks")
    void testOldestInactiveChunksEvictedAndMemoizationFreshness() {
        int capacity = 32;
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(0.0f, 0.0f);
        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        // 1. Initial streaming at (0, 0)
        manager.updateStreaming(playerPos, camera);
        Chunk originalChunk00 = manager.getChunk(0, 0);
        assertNotNull(originalChunk00);

        // Record a delta modifying tile (5, 5) in chunk (0, 0)
        ChunkDelta delta = new ChunkDelta(ChunkKey.of(0, 0));
        delta.recordTileChange(5, 5, (byte) TileType.STONE.getId());
        manager.saveDelta(delta);
        originalChunk00.applyDelta(delta);
        assertEquals(TileType.STONE, originalChunk00.getLocalTile(5, 5));

        // 2. Move camera and player far away: step through 100 distant chunks to guarantee chunk (0, 0) eviction
        for (int i = 50; i < 150; i++) {
            float x = i * 1024.0f;
            playerPos.set(x, 0.0f);
            camera.setCenter(x, 0.0f);
            manager.updateStreaming(playerPos, camera);
        }

        // Verify chunk (0, 0) is not in visible or active simulation rings
        for (Chunk v : manager.getVisibleChunks(camera)) {
            assertNotEquals(ChunkKey.of(0, 0), v.getChunkKey(), "Chunk (0, 0) must not be visible");
        }
        for (Chunk s : manager.getActiveSimulationChunks()) {
            assertNotEquals(ChunkKey.of(0, 0), s.getChunkKey(), "Chunk (0, 0) must not be in active simulation");
        }

        // 3. Memoization freshness:
        // Query distant chunk (149, 0) to prime memoization fast path with (149, 0)
        Chunk distantChunk = manager.getChunk(149, 0);
        assertNotNull(distantChunk);

        // Now re-query chunk (0, 0). Because chunk (0, 0) was evicted, it must be freshly regenerated
        Chunk reloadedChunk00 = manager.getChunk(0, 0);
        assertNotNull(reloadedChunk00);
        // The reloaded chunk must be a newly constructed instance, not the old reference
        assertNotSame(originalChunk00, reloadedChunk00,
                "Reloaded chunk must be a freshly generated instance after LRU eviction");
        // And the delta must have been preserved and reapplied
        assertEquals(TileType.STONE, reloadedChunk00.getLocalTile(5, 5),
                "Reloaded chunk must preserve and reflect modified delta");

        // Memoized fast path must now return reloadedChunk00
        assertSame(reloadedChunk00, manager.getChunk(0, 0),
                "Consecutive query to (0, 0) must return memoized instance");
        assertSame(reloadedChunk00, manager.getChunk(ChunkKey.of(0, 0)),
                "Query by chunkKey must also return memoized instance");
    }

    @Test
    @DisplayName("Challenge 3.3: Resident heap remains bounded (< 35 MB) after rapid 500-chunk exploration")
    void testHeapRemainsBoundedAfter500ChunkExploration() {
        int capacity = 32;
        ChunkManager manager = new ChunkManagerImpl(12345L, capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-10_000_000f, -10_000_000f, 20_000_000f, 20_000_000f);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);

        Vector2D playerPos = new Vector2D(0.0f, 0.0f);

        // Rapidly traverse 500 distinct chunks with real TerrainGenerator
        for (int i = 0; i < 500; i++) {
            float worldX = (float) i * 1024.0f;
            float worldY = (float) (i % 50) * 1024.0f;
            playerPos.set(worldX, worldY);
            camera.setCenter(worldX, worldY);

            manager.updateStreaming(playerPos, camera);
        }

        System.gc();
        long usedBytes = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long usedMB = usedBytes / (1024 * 1024);
        System.out.println("[Challenge 3.3] Resident heap after 500 chunks: " + usedMB + " MB (Limit: < 35 MB)");

        assertTrue(usedMB < 35,
                "Resident heap must remain bounded < 35 MB after 500 chunks (was " + usedMB + " MB)");
        assertTrue(manager.getLoadedChunkCount() <= capacity,
                "Final loaded chunk count must not exceed capacity (" + capacity + ")");
    }

    @Test
    @DisplayName("Challenge 2.3: Zero heap allocation under 50,000 spatial queries with active non-zero Floating Origin shift")
    void testZeroAllocation50kSpatialQueriesWithFloatingOriginShift() {
        java.lang.management.ThreadMXBean baseBean = ManagementFactory.getThreadMXBean();
        if (!(baseBean instanceof com.sun.management.ThreadMXBean bean) || !bean.isThreadAllocatedMemorySupported()) {
            return;
        }

        ChunkManager cm = new ChunkManagerImpl(999999L, 128);
        InfiniteWorld testWorld = new InfiniteWorld(cm, originManager);

        // Rebase world origin: shift origin by (-10240, 20480)
        originManager.rebaseWorldOrigin(-10240L, 20480L);

        int[][] chunks = {
                {1, 1}, {-2, 3}, {10, -5}, {-8, -8},
                {0, 2}, {-3, 0}, {7, 7}, {-15, 12}
        };

        // In local coordinates, for entity to be in chunk c, localX = c[0] * 1024 - originShiftX
        long shiftX = originManager.getOriginShiftX();
        long shiftY = originManager.getOriginShiftY();

        // Pre-warm cache for all target chunks and their 8 neighbors
        for (int[] c : chunks) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dx = -1; dx <= 1; dx++) {
                    cm.getChunk(c[0] + dx, c[1] + dy);
                }
            }
        }

        // Warmup JIT loop
        for (int i = 0; i < 20000; i++) {
            int[] c = chunks[i & 7];
            float localX = (float) ((long) c[0] * 1024L - shiftX) + 200.0f + (i % 500);
            float localY = (float) ((long) c[1] * 1024L - shiftY) + 200.0f + (i % 500);
            testWorld.isTraversable(localX, localY, 16.0f);
        }

        long threadId = Thread.currentThread().threadId();
        long beforeAlloc = bean.getThreadAllocatedBytes(threadId);

        for (int i = 0; i < 50000; i++) {
            int[] c = chunks[i & 7];
            float localX = (float) ((long) c[0] * 1024L - shiftX) + 200.0f + (i % 500);
            float localY = (float) ((long) c[1] * 1024L - shiftY) + 200.0f + (i % 500);
            testWorld.isTraversable(localX, localY, 16.0f);
        }

        long afterAlloc = bean.getThreadAllocatedBytes(threadId);
        long allocatedDelta = afterAlloc - beforeAlloc;

        assertEquals(0L, allocatedDelta,
                "50,000 spatial queries with active origin shift must allocate strictly 0 heap bytes, but allocated: " + allocatedDelta);
    }

    @Test
    @DisplayName("Challenge 1.6: Sliding collision resolution smoothly slides along seam-crossing obstacle boundary without NaN or stuck states")
    void testSlidingCollisionAcrossSeamWithObstacle() {
        int cx = 1;
        int cy = 0;
        ChunkImpl chunk10 = new ChunkImpl(cx, cy);
        // Obstacle right near west border of chunk (1, 0)
        Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 10.0f, 500.0f, true);
        chunk10.addObstacle(rock);

        ChunkManager mockManager = new ChunkManagerImpl((qcx, qcy) -> {
            if (qcx == 1 && qcy == 0) return chunk10;
            return new ChunkImpl(qcx, qcy);
        }, 64);
        InfiniteWorld testWorld = new InfiniteWorld(mockManager, originManager);

        // Player is at world (1015, 470) [near seam in chunk (0, 0)] moving diagonally towards (1034, 500)
        float startX = 1015.0f;
        float startY = 470.0f;
        float targetX = 1034.0f;
        float targetY = 500.0f;
        float radius = 10.0f;

        Vector2D resolved = testWorld.resolveCollision(startX, startY, targetX, targetY, radius);
        assertNotNull(resolved);
        assertFalse(Float.isNaN(resolved.x), "Resolved X must not be NaN");
        assertFalse(Float.isNaN(resolved.y), "Resolved Y must not be NaN");
        assertTrue(testWorld.isTraversable(resolved.x, resolved.y, radius),
                "Resolved position (" + resolved.x + ", " + resolved.y + ") must be traversable");
        // Entity should not penetrate the obstacle
        float distToRock = (float) Math.hypot(resolved.x - 1034.0f, resolved.y - 500.0f);
        assertTrue(distToRock >= (radius + rock.getCollisionRadius()) - 0.5f,
                "Entity must not penetrate obstacle collision radius (dist=" + distToRock + ")");
    }

    @Test
    @DisplayName("Challenge 3.4: 1,000 distinct chunk traversal with dynamic camera zoom variation")
    void testLruCacheStress1000ChunksWithDynamicZoom() {
        int capacity = 32;
        ChunkManager manager = new ChunkManagerImpl((cx, cy) -> new ChunkImpl(cx, cy), capacity);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(-50_000_000f, -50_000_000f, 100_000_000f, 100_000_000f);

        Vector2D playerPos = new Vector2D(0.0f, 0.0f);
        float[] zoomLevels = {0.5f, 0.75f, 1.0f, 1.35f, 1.8f, 2.25f, 2.5f};

        for (int i = -500; i < 500; i++) {
            float worldX = (float) i * 1024.0f + 256.0f;
            float worldY = (float) -i * 1024.0f + 256.0f;
            playerPos.set(worldX, worldY);
            camera.setCenter(worldX, worldY);
            camera.setZoom(zoomLevels[Math.abs(i) % zoomLevels.length]);

            manager.updateStreaming(playerPos, camera);

            int loaded = manager.getLoadedChunkCount();
            assertTrue(loaded <= capacity,
                    "Loaded chunk count (" + loaded + ") exceeded capacity (" + capacity + ") at step " + i);
        }

        assertEquals(capacity, manager.getLoadedChunkCount(), "Cache should be fully saturated at capacity");
    }
}
