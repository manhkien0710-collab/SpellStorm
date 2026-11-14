package com.spellstorm.world;

import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 2: ChunkImpl Storage, Traversability, Delta & Rendering Tests")
public class ChunkImplTest {

    @Nested
    @DisplayName("Tier 1: Storage Structures & Fast Tile Access")
    class StorageAndTileAccessTests {

        @Test
        @DisplayName("1.1 Chunk initialization: coordinates, precomputed key, and 1024-byte array default to GRASS")
        void testChunkInitialization() {
            Chunk chunk = new ChunkImpl(3, -5);
            assertEquals(3, chunk.getChunkX());
            assertEquals(-5, chunk.getChunkY());
            assertEquals(ChunkKey.of(3, -5), chunk.getChunkKey());

            byte[] data = chunk.getTileData();
            assertNotNull(data);
            assertEquals(Chunk.TILE_COUNT, data.length);
            assertEquals(1024, data.length);

            // All tiles should default to GRASS (id 0)
            for (int ly = 0; ly < 32; ly++) {
                for (int lx = 0; lx < 32; lx++) {
                    assertEquals(TileType.GRASS, chunk.getLocalTile(lx, ly));
                }
            }
        }

        @Test
        @DisplayName("1.2 Fast tile mutation and local tile indexing round-trip across all 1024 tiles")
        void testTileAccessRoundTrip() {
            Chunk chunk = new ChunkImpl(0, 0);
            TileType[] sampleTypes = { TileType.DIRT, TileType.STONE, TileType.WATER, TileType.SAND, TileType.BRIDGE_H };

            for (int ly = 0; ly < 32; ly++) {
                for (int lx = 0; lx < 32; lx++) {
                    TileType expected = sampleTypes[(lx + ly * 7) % sampleTypes.length];
                    chunk.setLocalTile(lx, ly, expected);
                    assertEquals(expected, chunk.getLocalTile(lx, ly));
                    assertEquals((byte) expected.getId(), chunk.getTileData()[ly * 32 + lx]);
                }
            }
        }

        @Test
        @DisplayName("1.3 Defensive out-of-bounds queries safely fallback to WATER without throwing exceptions")
        void testOutOfBoundsTileQueries() {
            Chunk chunk = new ChunkImpl(0, 0);
            assertEquals(TileType.WATER, chunk.getLocalTile(-1, 0));
            assertEquals(TileType.WATER, chunk.getLocalTile(32, 0));
            assertEquals(TileType.WATER, chunk.getLocalTile(0, -1));
            assertEquals(TileType.WATER, chunk.getLocalTile(0, 32));
            assertEquals(TileType.WATER, chunk.getLocalTile(100, 100));

            // Setting out of bounds safely no-ops
            assertDoesNotThrow(() -> chunk.setLocalTile(-1, 0, TileType.STONE));
            assertDoesNotThrow(() -> chunk.setLocalTile(32, 32, TileType.STONE));
            assertDoesNotThrow(() -> chunk.setLocalTile(0, 0, null));
            assertEquals(TileType.GRASS, chunk.getLocalTile(0, 0)); // null safe default
        }

        @Test
        @DisplayName("1.4 Obstacle list management: immutability of getter and null-safety")
        void testObstacleListManagement() {
            Chunk chunk = new ChunkImpl(0, 0);
            assertTrue(chunk.getObstacles().isEmpty());

            Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 100.0f, 100.0f, true);
            chunk.addObstacle(rock);
            assertEquals(1, chunk.getObstacles().size());
            assertEquals(rock, chunk.getObstacles().get(0));

            // Null obstacle safely ignored
            chunk.addObstacle(null);
            assertEquals(1, chunk.getObstacles().size());

            // Getter is unmodifiable
            assertThrows(UnsupportedOperationException.class, () -> chunk.getObstacles().add(rock));
        }
    }

    @Nested
    @DisplayName("Tier 2: Traversability & Local Collision Detection")
    class TraversabilityTests {

        @Test
        @DisplayName("2.1 Flat all-grass chunk is fully traversable across interior positions")
        void testAllGrassChunkTraversable() {
            Chunk chunk = new ChunkImpl(0, 0);
            assertTrue(chunk.isTraversableLocal(16.0f, 16.0f, 10.0f));
            assertTrue(chunk.isTraversableLocal(500.0f, 500.0f, 18.0f));
            assertTrue(chunk.isTraversableLocal(1000.0f, 1000.0f, 12.0f));
        }

        @Test
        @DisplayName("2.2 Water tile circle-AABB collision blocks overlapping entity circles")
        void testWaterTileCollision() {
            Chunk chunk = new ChunkImpl(0, 0);
            int waterLx = 10;
            int waterLy = 10;
            chunk.setLocalTile(waterLx, waterLy, TileType.WATER);

            float tileCenterX = (waterLx + 0.5f) * TileMap.TILE_SIZE; // 336.0f
            float tileCenterY = (waterLy + 0.5f) * TileMap.TILE_SIZE; // 336.0f
            float entityRadius = 10.0f;

            // Direct overlap: obstructed
            assertFalse(chunk.isTraversableLocal(tileCenterX, tileCenterY, entityRadius));

            // Overlap tile corner
            assertFalse(chunk.isTraversableLocal(waterLx * 32.0f - 5.0f, waterLy * 32.0f - 5.0f, 10.0f));

            // Clear of water tile
            assertTrue(chunk.isTraversableLocal(tileCenterX + 50.0f, tileCenterY, entityRadius));
        }

        @Test
        @DisplayName("2.3 Solid obstacle blocks traversal; non-solid obstacle permits traversal")
        void testObstacleCollision() {
            Chunk chunk = new ChunkImpl(0, 0);
            Obstacle tree = new Obstacle(DecoType.TREE_OAK, 200.0f, 200.0f, true);
            Obstacle bush = new Obstacle(DecoType.BUSH, 400.0f, 400.0f, false);
            chunk.addObstacle(tree);
            chunk.addObstacle(bush);

            Vector2D treeCenter = tree.getCollisionCenter();
            float entityRadius = 10.0f;

            // Inside tree collision radius
            assertFalse(chunk.isTraversableLocal(treeCenter.x, treeCenter.y, entityRadius));

            // Tangent/penetrating tree: dist = 15 < treeColRadius (12) + entityRadius (10) = 22
            assertFalse(chunk.isTraversableLocal(treeCenter.x + 15.0f, treeCenter.y, entityRadius));

            // Clear of tree: dist = 30 > 22
            assertTrue(chunk.isTraversableLocal(treeCenter.x + 30.0f, treeCenter.y, entityRadius));

            // Inside bush: passable decoration permits traversal
            assertTrue(chunk.isTraversableLocal(400.0f, 400.0f, entityRadius));
        }

        @Test
        @DisplayName("2.4 Edge and boundary entity positions test local tiles safely without throwing")
        void testEdgeEntityPositions() {
            Chunk chunk = new ChunkImpl(0, 0);
            // Circle touching chunk border lx = 0
            assertTrue(chunk.isTraversableLocal(5.0f, 500.0f, 10.0f));
            // Circle touching chunk border lx = 31
            assertTrue(chunk.isTraversableLocal(1020.0f, 500.0f, 10.0f));
            // Invalid negative radius safely returns false
            assertFalse(chunk.isTraversableLocal(500.0f, 500.0f, -5.0f));
        }
    }

    @Nested
    @DisplayName("Tier 3: Mutable ChunkDelta Integration")
    class DeltaApplicationTests {

        @Test
        @DisplayName("3.1 Modifying tiles via ChunkDelta modifies flat array and getLocalTile")
        void testTileModificationsFromDelta() {
            Chunk chunk = new ChunkImpl(5, 5);
            ChunkDelta delta = new ChunkDelta(chunk.getChunkKey());

            delta.recordTileChange(2, 3, (byte) TileType.STONE.getId());
            delta.recordTileChange(15, 20, (byte) TileType.WATER.getId());

            chunk.applyDelta(delta);

            assertEquals(TileType.STONE, chunk.getLocalTile(2, 3));
            assertEquals(TileType.WATER, chunk.getLocalTile(15, 20));
            assertEquals(TileType.GRASS, chunk.getLocalTile(0, 0)); // untouched
        }

        @Test
        @DisplayName("3.2 Suppressing removed obstacles removes obstacles in reverse order without index shifting corruption")
        void testObstacleSuppressionFromDelta() {
            Chunk chunk = new ChunkImpl(0, 0);
            Obstacle rock0 = new Obstacle(DecoType.ROCK, 100.0f, 100.0f, true);
            Obstacle rock1 = new Obstacle(DecoType.ROCK, 200.0f, 200.0f, true);
            Obstacle rock2 = new Obstacle(DecoType.ROCK, 300.0f, 300.0f, true);
            Obstacle rock3 = new Obstacle(DecoType.ROCK, 400.0f, 400.0f, true);

            chunk.addObstacle(rock0);
            chunk.addObstacle(rock1);
            chunk.addObstacle(rock2);
            chunk.addObstacle(rock3);
            assertEquals(4, chunk.getObstacles().size());

            ChunkDelta delta = new ChunkDelta(chunk.getChunkKey());
            // Remove obstacle 1 and 3
            delta.recordObstacleRemoved(1);
            delta.recordObstacleRemoved(3);

            chunk.applyDelta(delta);

            List<Obstacle> remaining = chunk.getObstacles();
            assertEquals(2, remaining.size());
            assertEquals(rock0, remaining.get(0));
            assertEquals(rock2, remaining.get(1));
        }

        @Test
        @DisplayName("3.3 Restoring persistent pickups and entities from ChunkDelta")
        void testRestorePersistentPickupsAndEntities() {
            ChunkImpl chunk = new ChunkImpl(0, 0);
            ChunkDelta delta = new ChunkDelta(chunk.getChunkKey());

            delta.addPersistentPickup(new ChunkDelta.SavedPickup(120.0f, 340.0f, "EXP_GEM", 5));
            delta.addPersistentEntity(new ChunkDelta.SavedEntityState("BOSS_ORC", 500.0f, 500.0f, 450.0f, "PHASE_2"));

            chunk.applyDelta(delta);

            assertEquals(1, chunk.getPersistentPickups().size());
            assertEquals("EXP_GEM", chunk.getPersistentPickups().get(0).pickupType());
            assertEquals(5, chunk.getPersistentPickups().get(0).value());

            assertEquals(1, chunk.getPersistentEntities().size());
            assertEquals("BOSS_ORC", chunk.getPersistentEntities().get(0).entityId());
            assertEquals(450.0f, chunk.getPersistentEntities().get(0).health());
        }

        @Test
        @DisplayName("3.4 Null and empty deltas safely no-op")
        void testNullAndEmptyDeltas() {
            Chunk chunk = new ChunkImpl(0, 0);
            assertDoesNotThrow(() -> chunk.applyDelta(null));
            assertDoesNotThrow(() -> chunk.applyDelta(new ChunkDelta(chunk.getChunkKey())));
        }
    }

    @Nested
    @DisplayName("Tier 4: Rendering & Headless Safety")
    class RenderingTests {

        @Test
        @DisplayName("4.1 Headless environment safety: draw safely no-ops without OpenGL context")
        void testHeadlessDrawSafety() {
            Chunk chunk = new ChunkImpl(0, 0);
            // With Gdx.gl == null or batch == null, must not throw any exception
            assertDoesNotThrow(() -> chunk.draw(null, null, 0.0f, 0.0f, 0.0f));
        }
    }
}
