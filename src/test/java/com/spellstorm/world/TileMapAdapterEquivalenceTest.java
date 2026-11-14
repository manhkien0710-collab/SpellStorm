package com.spellstorm.world;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 1: TileMapAdapter WorldGrid Equivalence Verification")
public class TileMapAdapterEquivalenceTest {

    @BeforeAll
    static void initHeadless() {
        HeadlessTestRunner.init();
    }

    @Nested
    @DisplayName("1. Constructor & Wrapper Invariants")
    class ConstructorInvariants {

        @Test
        @DisplayName("1.1 Null TileMap constructor throws NullPointerException")
        void testNullConstructorThrows() {
            assertThrows(NullPointerException.class, () -> new TileMapAdapter(null));
        }

        @Test
        @DisplayName("1.2 getTileMap returns the exact delegated instance")
        void testGetTileMapReturnsExactInstance() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);
            assertSame(map, adapter.getTileMap(), "Adapter must expose underlying TileMap instance");
        }

        @Test
        @DisplayName("1.3 isBounded always returns true for TileMapAdapter")
        void testIsBoundedInvariant() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);
            assertTrue(adapter.isBounded(), "TileMapAdapter must report bounded grid");
            assertNotNull(adapter.getWorldBounds(), "getWorldBounds must not be null");
            assertEquals(map.getWorldBounds(), adapter.getWorldBounds());
        }
    }

    @Nested
    @DisplayName("2. Tile Query & Out-of-Bounds Void Fallback Parity")
    class TileQueryParity {

        @Test
        @DisplayName("2.1 All 16,384 tiles match between TileMap and TileMapAdapter")
        void testAllTilesParity() {
            TileMap map = new TileMap(12345L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            for (int ty = 0; ty < TileMap.HEIGHT; ty++) {
                for (int tx = 0; tx < TileMap.WIDTH; tx++) {
                    assertEquals(map.getTile(tx, ty), adapter.getTile(tx, ty));
                    assertEquals(map.isSolidTile(tx, ty), adapter.isSolidTile(tx, ty));
                    assertEquals(map.isWaterTile(tx, ty), adapter.isWaterTile(tx, ty));
                }
            }
        }

        @ParameterizedTest
        @CsvSource({
            "-1, 0", "0, -1", "-10, -10", "128, 50", "50, 128", "256, 256", "-100, 200"
        })
        @DisplayName("2.2 Out-of-bounds coordinates return WATER and solid across both")
        void testOutOfBoundsParity(int tx, int ty) {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertEquals(map.getTile(tx, ty), adapter.getTile(tx, ty));
            assertEquals(TileType.WATER, adapter.getTile(tx, ty));
            assertTrue(adapter.isSolidTile(tx, ty));
            assertTrue(adapter.isWaterTile(tx, ty));
        }
    }

    @Nested
    @DisplayName("3. Coordinate Transformation Parity")
    class CoordinateTransformParity {

        @ParameterizedTest
        @CsvSource({
            "0.0, 0.0", "-1.0, -1.0", "-32.0, -32.0", "16.0, 16.0", "31.9, 31.9",
            "32.0, 32.0", "2048.0, 2048.0", "4095.9, 4095.9", "4096.0, 4096.0"
        })
        @DisplayName("3.1 World-to-tile coordinate conversion matches bit-identically")
        void testWorldToTileParity(float wx, float wy) {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertEquals(map.worldToTileX(wx), adapter.worldToTileX(wx));
            assertEquals(map.worldToTileY(wy), adapter.worldToTileY(wy));

            int[] mapTile = map.worldToTile(wx, wy);
            int[] adapterTile = adapter.worldToTile(wx, wy);
            assertArrayEquals(mapTile, adapterTile);

            Vector2D pos = new Vector2D(wx, wy);
            assertArrayEquals(map.worldToTile(pos), adapter.worldToTile(pos));
        }

        @ParameterizedTest
        @CsvSource({
            "0, 0", "-1, -1", "10, 20", "64, 64", "127, 127", "128, 128"
        })
        @DisplayName("3.2 Tile-to-world center coordinate conversion matches bit-identically")
        void testTileToWorldParity(int tx, int ty) {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertEquals(map.tileToWorldX(tx), adapter.tileToWorldX(tx));
            assertEquals(map.tileToWorldY(ty), adapter.tileToWorldY(ty));

            Vector2D mapCenter = map.tileToWorld(tx, ty);
            Vector2D adapterCenter = adapter.tileToWorld(tx, ty);
            assertEquals(mapCenter.x, adapterCenter.x, 1e-6f);
            assertEquals(mapCenter.y, adapterCenter.y, 1e-6f);
        }
    }

    @Nested
    @DisplayName("4. Traversability & Sliding Collision Parity")
    class TraversabilityAndCollisionParity {

        @Test
        @DisplayName("4.1 Traversability queries evaluate bitwise identically")
        void testTraversabilityParity() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            float[] testPoints = {
                16.0f, 32.0f, 100.0f, 500.0f, 1024.0f, 2048.0f, 3000.0f, 4080.0f, -5.0f, 4100.0f
            };

            for (float x : testPoints) {
                for (float y : testPoints) {
                    for (float r : new float[]{10.0f, 18.0f, 50.0f}) {
                        boolean mapTrav = map.isTraversable(x, y, r);
                        boolean adapterTrav = adapter.isTraversable(x, y, r);
                        assertEquals(mapTrav, adapterTrav, "Traversability mismatch at (" + x + ", " + y + ", r=" + r + ")");

                        Vector2D v = new Vector2D(x, y);
                        assertEquals(map.isTraversable(v, r), adapter.isTraversable(v, r));
                    }
                }
            }
        }

        @Test
        @DisplayName("4.2 Sliding collision resolution produces bitwise identical candidate vectors")
        void testSlidingCollisionParity() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            Vector2D[] startPositions = {
                new Vector2D(2048.0f, 2048.0f),
                new Vector2D(100.0f, 100.0f),
                new Vector2D(50.0f, 50.0f),
                new Vector2D(4050.0f, 4050.0f)
            };

            Vector2D[] stepDeltas = {
                new Vector2D(10.0f, 0.0f),
                new Vector2D(0.0f, -10.0f),
                new Vector2D(-15.0f, -15.0f),
                new Vector2D(25.0f, 25.0f)
            };

            for (Vector2D start : startPositions) {
                for (Vector2D delta : stepDeltas) {
                    Vector2D target = start.cpy().add(delta);
                    float radius = 18.0f;

                    Vector2D mapRes = map.resolveCollision(start.x, start.y, target.x, target.y, radius);
                    Vector2D adapterRes = adapter.resolveCollision(start.x, start.y, target.x, target.y, radius);
                    assertEquals(mapRes.x, adapterRes.x, 1e-5f);
                    assertEquals(mapRes.y, adapterRes.y, 1e-5f);

                    Vector2D mapResVec = map.resolveCollision(start, target, radius);
                    Vector2D adapterResVec = adapter.resolveCollision(start, target, radius);
                    assertEquals(mapResVec.x, adapterResVec.x, 1e-5f);
                    assertEquals(mapResVec.y, adapterResVec.y, 1e-5f);
                }
            }
        }
    }

    @Nested
    @DisplayName("5. Frustum Culling & Visible Obstacles Parity")
    class FrustumCullingParity {

        @Test
        @DisplayName("5.1 null camera returns 100% of placed obstacles")
        void testNullCameraReturnsAllObstacles() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            List<Obstacle> all = adapter.getVisibleObstacles(null);
            assertEquals(map.getObstacles().size(), all.size());
            assertEquals(map.getObstacles(), all);
        }

        @Test
        @DisplayName("5.2 camera returns only obstacles whose visual bounds are visible")
        void testCameraFrustumCulling() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            Rectangle bounds = map.getWorldBounds();
            camera.setWorldBounds(bounds.x, bounds.y, bounds.width, bounds.height);

            // Center camera directly on the first obstacle so at least one obstacle is guaranteed inside frustum
            assertFalse(map.getObstacles().isEmpty(), "Map must contain placed obstacles");
            Obstacle targetObs = map.getObstacles().get(0);
            camera.setCenter(targetObs.getPosition().x, targetObs.getPosition().y);

            List<Obstacle> visible = adapter.getVisibleObstacles(camera);
            assertTrue(visible.size() > 0 && visible.size() <= map.getObstacles().size());
            assertTrue(visible.contains(targetObs), "Target obstacle centered on camera must be in visible list");

            for (Obstacle obs : visible) {
                assertTrue(camera.isVisible(obs.getVisualBounds()), "Returned obstacle must be visible");
            }

            for (Obstacle obs : map.getObstacles()) {
                if (!visible.contains(obs)) {
                    assertFalse(camera.isVisible(obs.getVisualBounds()), "Omitted obstacle must not be visible");
                }
            }
        }

        @Test
        @DisplayName("5.3 Camera far away returns empty obstacle list")
        void testCameraFarAwayReturnsEmpty() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.setWorldBounds(-100_000f, -100_000f, 200_000f, 200_000f);
            camera.setCenter(50_000.0f, 50_000.0f);

            List<Obstacle> visible = adapter.getVisibleObstacles(camera);
            assertTrue(visible.isEmpty(), "Offscreen camera must yield empty obstacles");
        }
    }

    @Nested
    @DisplayName("6. Headless Rendering Safety")
    class HeadlessSafety {

        @Test
        @DisplayName("6.1 drawTerrain safely no-ops without throwing NPE or GL errors")
        void testDrawTerrainHeadlessSafe() {
            TileMap map = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertDoesNotThrow(() -> adapter.drawTerrain(null, null, null, 0.0f));
            assertDoesNotThrow(() -> adapter.drawTerrain(null, null, null));
        }
    }
}
