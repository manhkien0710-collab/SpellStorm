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

/**
 * Empirical Adversarial Challenge Test Suite for TileMapAdapter and WorldGrid interface contract.
 *
 * Authored by teamwork_challenger to rigorously stress-test:
 * 1. Every method of WorldGrid on TileMapAdapter verified for 100% equivalence against TileMap.
 * 2. Hostile numerical inputs: Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, subnormals, negative zero.
 * 3. Out-of-bounds tile queries: negative coordinates (-1, -100, -2^31) and beyond-map (128, 1000, 2^31-1).
 * 4. Boundary sliding collision resolution at all 4 borders (West, East, South, North) and all 4 corners.
 * 5. Extreme camera frustum culling, unmodifiable collections, and null-safety across all methods.
 */
@DisplayName("Milestone 1 Challenger: TileMapAdapter Hostile Input & Boundary Challenge Tests")
public class TileMapAdapterHostileChallengeTest {

    private static final long SEED = 42L;

    @BeforeAll
    static void initHeadless() {
        HeadlessTestRunner.init();
    }

    @Nested
    @DisplayName("1. Constructor & Structural Invariants")
    class ConstructorAndStructuralInvariants {

        @Test
        @DisplayName("1.1 Null TileMap constructor throws NullPointerException")
        void testNullConstructorThrows() {
            assertThrows(NullPointerException.class, () -> new TileMapAdapter(null));
        }

        @Test
        @DisplayName("1.2 getTileMap returns identical instance")
        void testGetTileMapExposesInstance() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);
            assertSame(map, adapter.getTileMap());
        }

        @Test
        @DisplayName("1.3 isBounded and getWorldBounds return valid bounded rectangle")
        void testBoundsInvariants() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertTrue(adapter.isBounded());
            Rectangle bounds = adapter.getWorldBounds();
            assertNotNull(bounds);
            assertEquals(0.0f, bounds.x);
            assertEquals(0.0f, bounds.y);
            assertEquals(4096.0f, bounds.width);
            assertEquals(4096.0f, bounds.height);
            assertEquals(map.getWorldBounds(), bounds);
        }
    }

    @Nested
    @DisplayName("2. Tile Queries with Hostile Coordinates")
    class TileQueriesHostile {

        @ParameterizedTest(name = "Hostile tile query at ({0}, {1})")
        @CsvSource({
            "-1, -1",
            "-1000, 50",
            "50, -1000",
            "128, 50",
            "50, 128",
            "256, 256",
            "-2147483648, 0",
            "0, -2147483648",
            "-2147483648, -2147483648",
            "2147483647, 0",
            "0, 2147483647",
            "2147483647, 2147483647"
        })
        void testOutOfBoundsTileQueriesEquivalence(int tx, int ty) {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            // Out-of-bounds must strictly yield WATER and solid
            assertEquals(map.getTile(tx, ty), adapter.getTile(tx, ty));
            assertEquals(TileType.WATER, adapter.getTile(tx, ty));

            assertEquals(map.isSolidTile(tx, ty), adapter.isSolidTile(tx, ty));
            assertTrue(adapter.isSolidTile(tx, ty));

            assertEquals(map.isWaterTile(tx, ty), adapter.isWaterTile(tx, ty));
            assertTrue(adapter.isWaterTile(tx, ty));
        }

        @Test
        @DisplayName("2.2 Exhaustive grid comparison: All 16,384 tiles match bitwise identically")
        void testAllTilesBitwiseParity() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            for (int ty = 0; ty < TileMap.HEIGHT; ty++) {
                for (int tx = 0; tx < TileMap.WIDTH; tx++) {
                    assertEquals(map.getTile(tx, ty), adapter.getTile(tx, ty));
                    assertEquals(map.isSolidTile(tx, ty), adapter.isSolidTile(tx, ty));
                    assertEquals(map.isWaterTile(tx, ty), adapter.isWaterTile(tx, ty));
                }
            }
        }
    }

    @Nested
    @DisplayName("3. Coordinate Transformations with Hostile Numbers")
    class CoordinateTransformationsHostile {

        @Test
        @DisplayName("3.1 worldToTileX/Y with NaN, Infinities, extreme floats, and zero")
        void testWorldToTileHostileFloats() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            float[] hostileFloats = {
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
                Float.MAX_VALUE,
                -Float.MAX_VALUE,
                Float.MIN_VALUE,
                -Float.MIN_VALUE,
                -0.0f, 0.0f,
                -1.0f, -32.0f, -32.0001f,
                4096.0f, 4096.0001f,
                100_000.0f, -100_000.0f
            };

            for (float f : hostileFloats) {
                assertEquals(map.worldToTileX(f), adapter.worldToTileX(f), "Mismatch at worldToTileX(" + f + ")");
                assertEquals(map.worldToTileY(f), adapter.worldToTileY(f), "Mismatch at worldToTileY(" + f + ")");

                int[] mapArr = map.worldToTile(f, f);
                int[] adapterArr = adapter.worldToTile(f, f);
                assertArrayEquals(mapArr, adapterArr, "Mismatch at worldToTile(" + f + ", " + f + ")");

                Vector2D pos = new Vector2D(f, f);
                assertArrayEquals(map.worldToTile(pos), adapter.worldToTile(pos), "Mismatch at worldToTile(Vector2D(" + f + "))");
            }
        }

        @Test
        @DisplayName("3.2 worldToTile(Vector2D null) safely returns [0, 0] without throwing")
        void testWorldToTileNullVector() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            int[] mapNull = map.worldToTile((Vector2D) null);
            int[] adapterNull = adapter.worldToTile((Vector2D) null);

            assertNotNull(adapterNull);
            assertArrayEquals(new int[]{0, 0}, adapterNull);
            assertArrayEquals(mapNull, adapterNull);
        }

        @ParameterizedTest(name = "tileToWorld hostile tile index ({0}, {1})")
        @CsvSource({
            "0, 0",
            "-1, -1",
            "127, 127",
            "128, 128",
            "-1000, 1000",
            "-2147483648, 2147483647"
        })
        void testTileToWorldHostileIndices(int tx, int ty) {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertEquals(map.tileToWorldX(tx), adapter.tileToWorldX(tx), 1e-5f);
            assertEquals(map.tileToWorldY(ty), adapter.tileToWorldY(ty), 1e-5f);

            Vector2D mapVec = map.tileToWorld(tx, ty);
            Vector2D adapterVec = adapter.tileToWorld(tx, ty);
            assertEquals(mapVec.x, adapterVec.x, 1e-5f);
            assertEquals(mapVec.y, adapterVec.y, 1e-5f);
        }
    }

    @Nested
    @DisplayName("4. Traversability Predicate with Hostile Inputs")
    class TraversabilityHostile {

        @Test
        @DisplayName("4.1 isTraversable handles NaN, Infinity, negative radius, and null Vector2D")
        void testTraversabilityHostileFloats() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            // Test cases: (x, y, radius)
            float[][] hostileTuples = {
                { Float.NaN, 2048.0f, 18.0f },
                { 2048.0f, Float.NaN, 18.0f },
                { Float.NaN, Float.NaN, 18.0f },
                { Float.POSITIVE_INFINITY, 2048.0f, 18.0f },
                { Float.NEGATIVE_INFINITY, 2048.0f, 18.0f },
                { 2048.0f, Float.POSITIVE_INFINITY, 18.0f },
                { 2048.0f, Float.NEGATIVE_INFINITY, 18.0f },
                { 2048.0f, 2048.0f, Float.NaN },
                { 2048.0f, 2048.0f, -10.0f },
                { 2048.0f, 2048.0f, 0.0f },
                { 2048.0f, 2048.0f, 10_000.0f },
                { -10.0f, 2048.0f, 18.0f },
                { 2048.0f, -10.0f, 18.0f },
                { 4106.0f, 2048.0f, 18.0f },
                { 2048.0f, 4106.0f, 18.0f },
                { -1000.0f, -1000.0f, 18.0f },
                { 10_000.0f, 10_000.0f, 18.0f }
            };

            for (float[] tuple : hostileTuples) {
                float x = tuple[0];
                float y = tuple[1];
                float r = tuple[2];

                boolean mapResult = map.isTraversable(x, y, r);
                boolean adapterResult = adapter.isTraversable(x, y, r);

                assertEquals(mapResult, adapterResult,
                        String.format("isTraversable mismatch at (x=%f, y=%f, r=%f): map=%b, adapter=%b",
                                x, y, r, mapResult, adapterResult));

                Vector2D pos = new Vector2D(x, y);
                assertEquals(map.isTraversable(pos, r), adapter.isTraversable(pos, r));
            }

            // null Vector2D
            assertFalse(adapter.isTraversable((Vector2D) null, 18.0f));
            assertEquals(map.isTraversable((Vector2D) null, 18.0f), adapter.isTraversable((Vector2D) null, 18.0f));
        }

        @Test
        @DisplayName("4.2 Boundary edge traversability precisely at [radius, 4096 - radius]")
        void testBoundaryEdgeTraversability() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);
            float r = 18.0f;

            // Exactly on bounds
            assertEquals(map.isTraversable(r, 2048.0f, r), adapter.isTraversable(r, 2048.0f, r));
            assertEquals(map.isTraversable(4096.0f - r, 2048.0f, r), adapter.isTraversable(4096.0f - r, 2048.0f, r));
            assertEquals(map.isTraversable(2048.0f, r, r), adapter.isTraversable(2048.0f, r, r));
            assertEquals(map.isTraversable(2048.0f, 4096.0f - r, r), adapter.isTraversable(2048.0f, 4096.0f - r, r));

            // Epsilon outside bounds -> strictly rejected
            assertFalse(adapter.isTraversable(r - 0.01f, 2048.0f, r));
            assertFalse(adapter.isTraversable(4096.0f - r + 0.01f, 2048.0f, r));
            assertFalse(adapter.isTraversable(2048.0f, r - 0.01f, r));
            assertFalse(adapter.isTraversable(2048.0f, 4096.0f - r + 0.01f, r));
        }
    }

    @Nested
    @DisplayName("5. Sliding Collision Resolution Near Borders & Corners")
    class SlidingCollisionBordersAndCorners {

        @Test
        @DisplayName("5.1 West border sliding: candidate moving outside x < 0 slides along Y")
        void testWestBorderSliding() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            float r = 18.0f;
            float oldX = 20.0f;
            float oldY = 2048.0f;
            float newX = -100.0f; // Penetrating West border
            float newY = 2100.0f; // Moving upward

            Vector2D mapRes = map.resolveCollision(oldX, oldY, newX, newY, r);
            Vector2D adapterRes = adapter.resolveCollision(oldX, oldY, newX, newY, r);

            assertEquals(mapRes.x, adapterRes.x, 1e-5f);
            assertEquals(mapRes.y, adapterRes.y, 1e-5f);

            // Clamped to radius at boundary
            assertEquals(r, adapterRes.x, 1e-5f);
            assertEquals(newY, adapterRes.y, 1e-5f);
        }

        @Test
        @DisplayName("5.2 East border sliding: candidate moving outside x > 4096 slides along Y")
        void testEastBorderSliding() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            float r = 18.0f;
            float oldX = 4076.0f;
            float oldY = 2048.0f;
            float newX = 4200.0f; // Penetrating East border
            float newY = 2100.0f; // Moving upward

            Vector2D mapRes = map.resolveCollision(oldX, oldY, newX, newY, r);
            Vector2D adapterRes = adapter.resolveCollision(oldX, oldY, newX, newY, r);

            assertEquals(mapRes.x, adapterRes.x, 1e-5f);
            assertEquals(mapRes.y, adapterRes.y, 1e-5f);

            assertEquals(4096.0f - r, adapterRes.x, 1e-5f);
            assertEquals(newY, adapterRes.y, 1e-5f);
        }

        @Test
        @DisplayName("5.3 South border sliding: candidate moving outside y < 0 slides along X")
        void testSouthBorderSliding() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            float r = 18.0f;
            float oldX = 2048.0f;
            float oldY = 20.0f;
            float newX = 2100.0f; // Moving right
            float newY = -100.0f; // Penetrating South border

            Vector2D mapRes = map.resolveCollision(oldX, oldY, newX, newY, r);
            Vector2D adapterRes = adapter.resolveCollision(oldX, oldY, newX, newY, r);

            assertEquals(mapRes.x, adapterRes.x, 1e-5f);
            assertEquals(mapRes.y, adapterRes.y, 1e-5f);

            assertEquals(newX, adapterRes.x, 1e-5f);
            assertEquals(r, adapterRes.y, 1e-5f);
        }

        @Test
        @DisplayName("5.4 North border sliding: candidate moving outside y > 4096 slides along X")
        void testNorthBorderSliding() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            float r = 18.0f;
            float oldX = 2048.0f;
            float oldY = 4076.0f;
            float newX = 2100.0f; // Moving right
            float newY = 4200.0f; // Penetrating North border

            Vector2D mapRes = map.resolveCollision(oldX, oldY, newX, newY, r);
            Vector2D adapterRes = adapter.resolveCollision(oldX, oldY, newX, newY, r);

            assertEquals(mapRes.x, adapterRes.x, 1e-5f);
            assertEquals(mapRes.y, adapterRes.y, 1e-5f);

            assertEquals(newX, adapterRes.x, 1e-5f);
            assertEquals(4096.0f - r, adapterRes.y, 1e-5f);
        }

        @Test
        @DisplayName("5.5 All 4 corner collisions: diagonal movement into corner halts both axes")
        void testCornerCollisions() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);
            float r = 18.0f;

            // SW corner: moving from (r, r) to (-100, -100)
            Vector2D swRes = adapter.resolveCollision(r, r, -100.0f, -100.0f, r);
            assertEquals(r, swRes.x, 1e-5f);
            assertEquals(r, swRes.y, 1e-5f);

            // NW corner: moving from (r, 4096-r) to (-100, 4200)
            Vector2D nwRes = adapter.resolveCollision(r, 4096.0f - r, -100.0f, 4200.0f, r);
            assertEquals(r, nwRes.x, 1e-5f);
            assertEquals(4096.0f - r, nwRes.y, 1e-5f);

            // SE corner: moving from (4096-r, r) to (4200, -100)
            Vector2D seRes = adapter.resolveCollision(4096.0f - r, r, 4200.0f, -100.0f, r);
            assertEquals(4096.0f - r, seRes.x, 1e-5f);
            assertEquals(r, seRes.y, 1e-5f);

            // NE corner: moving from (4096-r, 4096-r) to (4200, 4200)
            Vector2D neRes = adapter.resolveCollision(4096.0f - r, 4096.0f - r, 4200.0f, 4200.0f, r);
            assertEquals(4096.0f - r, neRes.x, 1e-5f);
            assertEquals(4096.0f - r, neRes.y, 1e-5f);
        }

        @Test
        @DisplayName("5.6 resolveCollision with null Vector2Ds falls back safely without NPE")
        void testNullVectorResolveCollision() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            Vector2D mapRes = map.resolveCollision((Vector2D) null, (Vector2D) null, 18.0f);
            Vector2D adapterRes = adapter.resolveCollision((Vector2D) null, (Vector2D) null, 18.0f);

            assertNotNull(adapterRes);
            assertEquals(mapRes.x, adapterRes.x, 1e-5f);
            assertEquals(mapRes.y, adapterRes.y, 1e-5f);
        }
    }

    @Nested
    @DisplayName("6. Frustum Culling & Unmodifiable Collections")
    class FrustumCullingAndSafety {

        @Test
        @DisplayName("6.1 getVisibleObstacles returns unmodifiable list that throws on modification")
        void testVisibleObstaclesUnmodifiable() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.setCenter(2048.0f, 2048.0f);

            List<Obstacle> visible = adapter.getVisibleObstacles(camera);
            assertNotNull(visible);

            assertThrows(UnsupportedOperationException.class, () ->
                    visible.add(new Obstacle(DecoType.ROCK_LARGE, 100.0f, 100.0f, true)));
        }

        @Test
        @DisplayName("6.2 Extreme camera positions and zoom levels")
        void testExtremeCameraQueries() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            GameCamera camera = new GameCamera(1280.0f, 720.0f);

            // Extreme zoom out (very wide frustum)
            camera.setZoom(0.01f);
            camera.setCenter(2048.0f, 2048.0f);
            List<Obstacle> wideList = adapter.getVisibleObstacles(camera);
            assertNotNull(wideList);

            // Extreme zoom in (very narrow frustum)
            camera.setZoom(100.0f);
            List<Obstacle> narrowList = adapter.getVisibleObstacles(camera);
            assertNotNull(narrowList);
            assertTrue(wideList.size() >= narrowList.size(), "Wider frustum must contain at least as many obstacles");

            // Camera in negative void (expand world bounds so camera does not clamp to [0, 4096])
            camera.setWorldBounds(-100_000.0f, -100_000.0f, 200_000.0f, 200_000.0f);
            camera.setCenter(-50_000.0f, -50_000.0f);
            List<Obstacle> voidList = adapter.getVisibleObstacles(camera);
            assertTrue(voidList.isEmpty());
        }

        @Test
        @DisplayName("6.3 drawTerrain null parameters do not throw unhandled exception")
        void testDrawTerrainNullSafety() {
            TileMap map = new TileMap(SEED);
            TileMapAdapter adapter = new TileMapAdapter(map);

            assertDoesNotThrow(() -> adapter.drawTerrain(null, null, null, 0.0f));
            assertDoesNotThrow(() -> adapter.drawTerrain(null, null, null));
        }
    }
}
