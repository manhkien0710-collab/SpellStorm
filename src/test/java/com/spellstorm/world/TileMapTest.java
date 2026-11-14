package com.spellstorm.world;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3: TileMap System, Traversability & Collision Tests")
public class TileMapTest {

    // =========================================================================
    // Tier 1: Equivalence Partitioning (Void Fallback, Coordinates, Traversability)
    // =========================================================================
    @Nested
    @DisplayName("Tier 1: Equivalence Partitioning")
    class Tier1EquivalenceTests {

        @Test
        @DisplayName("1.1 Out-of-bounds grid queries safely fallback to impassable WATER")
        void testOutOfBoundsVoidFallback() {
            TileMap map = new TileMap(42);

            // Negative coordinates
            assertEquals(TileType.WATER, map.tileAt(-1, 0));
            assertEquals(TileType.WATER, map.tileAt(0, -1));
            assertEquals(TileType.WATER, map.tileAt(-10, -10));

            // Coordinates >= width or height
            assertEquals(TileType.WATER, map.tileAt(128, 50));
            assertEquals(TileType.WATER, map.tileAt(50, 128));
            assertEquals(TileType.WATER, map.tileAt(200, 200));

            // Predicates for out-of-bounds
            assertTrue(map.isSolidTile(-5, 50));
            assertTrue(map.isWaterTile(-5, 50));
            assertFalse(map.isWalkable(-5, 50));
            assertFalse(map.isBridgeTile(-5, 50));
        }

        @Test
        @DisplayName("1.2 Coordinate transformations round-trip consistency")
        void testCoordinateTransformations() {
            TileMap map = new TileMap(42);

            // Tile (10, 20) -> world center should be ((10 + 0.5) * 32, (20 + 0.5) * 32) = (336, 656)
            Vector2D worldCenter = map.tileToWorld(10, 20);
            assertEquals(336.0f, worldCenter.x);
            assertEquals(656.0f, worldCenter.y);

            // World coordinates (336, 656) -> tile (10, 20)
            int[] tile = map.worldToTile(worldCenter);
            assertEquals(10, tile[0]);
            assertEquals(20, tile[1]);

            // Top-left corner of tile (10, 20) is (320, 640)
            assertEquals(10, map.worldToTileX(320.0f));
            assertEquals(20, map.worldToTileY(640.0f));

            // Bottom-right edge inside tile (10, 20) is (351.9f, 671.9f)
            assertEquals(10, map.worldToTileX(351.9f));
            assertEquals(20, map.worldToTileY(671.9f));
        }

        @Test
        @DisplayName("1.3 Passable decorations (Bush, Log) do not block traversal")
        void testPassableDecorationsDoNotBlock() {
            TileMap map = new TileMap(42);

            // Clear obstacles and add a Bush and a Log
            Vector2D testPos = new Vector2D(2000.0f, 2000.0f);
            map.addDecoration(DecoType.BUSH, testPos, false);
            map.addDecoration(DecoType.LOG, new Vector2D(2050.0f, 2000.0f), false);

            assertTrue(map.isTraversable(testPos, 16.0f), "Passable BUSH must not block traversal");
            assertTrue(map.isTraversable(new Vector2D(2050.0f, 2000.0f), 16.0f), "Passable LOG must not block traversal");
        }
    }

    // =========================================================================
    // Tier 2: Boundary Value Analysis (World Boundaries, Water Edges, Obstacles)
    // =========================================================================
    @Nested
    @DisplayName("Tier 2: Boundary Value Analysis")
    class Tier2BoundaryTests {

        @Test
        @DisplayName("2.1 Entities crossing outside world bounds [0, 4096] are rejected by isTraversable")
        void testWorldBoundaryTraversability() {
            TileMap map = new TileMap(42);
            float r = 16.0f;

            // Inside bounds
            assertTrue(map.isTraversable(r, 2048.0f, r));
            assertTrue(map.isTraversable(4096.0f - r, 2048.0f, r));
            assertTrue(map.isTraversable(2048.0f, r, r));
            assertTrue(map.isTraversable(2048.0f, 4096.0f - r, r));

            // Circle overflows left border
            assertFalse(map.isTraversable(r - 0.1f, 2048.0f, r));
            // Circle overflows right border
            assertFalse(map.isTraversable(4096.0f - r + 0.1f, 2048.0f, r));
            // Circle overflows top border
            assertFalse(map.isTraversable(2048.0f, r - 0.1f, r));
            // Circle overflows bottom border
            assertFalse(map.isTraversable(2048.0f, 4096.0f - r + 0.1f, r));

            // Completely outside bounds
            assertFalse(map.isTraversable(-10.0f, 2048.0f, r));
            assertFalse(map.isTraversable(4100.0f, 2048.0f, r));
            assertFalse(map.isTraversable(2048.0f, -10.0f, r));
            assertFalse(map.isTraversable(2048.0f, 4100.0f, r));
        }

        @Test
        @DisplayName("2.2 Water tile circle-AABB collision accurately blocks overlapping circles")
        void testWaterTileCollision() {
            TileMap map = new TileMap(42);

            // Locate a known water tile, e.g. at y = 46
            int waterTx = -1;
            int testTy = 46;
            for (int x = 30; x <= 60; x++) {
                if (map.tileAt(x, testTy) == TileType.WATER) {
                    waterTx = x;
                    break;
                }
            }
            assertTrue(waterTx > 0);

            float tileLeft = waterTx * 32.0f;
            float tileTop = testTy * 32.0f;
            Vector2D waterCenter = new Vector2D(tileLeft + 16.0f, tileTop + 16.0f);

            // Entity centered inside water tile
            assertFalse(map.isTraversable(waterCenter, 16.0f));

            // Entity just outside the left edge of water tile: x = tileLeft - 10, radius = 12
            // Circle overlaps tile by 2 units -> should block
            assertFalse(map.isTraversable(new Vector2D(tileLeft - 10.0f, tileTop + 16.0f), 12.0f));

            // Entity completely clear of water tile: x = tileLeft - 25, radius = 10
            // Distance to tile is 25 > 10 -> clear
            // Note: need to make sure the tile at tileLeft - 25 is also walkable (e.g. sand/grass)
            if (map.isWalkable(waterTx - 2, testTy)) {
                Vector2D clearPos = new Vector2D(tileLeft - 48.0f, tileTop + 16.0f);
                assertTrue(map.isTraversable(clearPos, 10.0f));
            }
        }

        @Test
        @DisplayName("2.3 Solid obstacle circle-circle collision blocks penetration")
        void testSolidObstacleCollision() {
            TileMap map = new TileMap(42);
            // Large rock at (2000, 2000): solid=true, radius=14, offset=(0, -6)
            // Collision center = (2000, 1994)
            Obstacle rock = new Obstacle(DecoType.ROCK_LARGE, 2000.0f, 2000.0f, true);
            map.addObstacle(rock);

            Vector2D colCenter = rock.getCollisionCenter();
            assertEquals(2000.0f, colCenter.x);
            assertEquals(1994.0f, colCenter.y);
            assertEquals(14.0f, rock.getCollisionRadius());

            float entityRadius = 10.0f;
            float totalRadius = rock.getCollisionRadius() + entityRadius; // 24.0f

            // Entity inside obstacle collision circle
            assertFalse(map.isTraversable(colCenter, entityRadius));

            // Entity overlapping obstacle: center at (colCenter.x + 20, colCenter.y), dist = 20 < 24
            assertFalse(map.isTraversable(new Vector2D(colCenter.x + 20.0f, colCenter.y), entityRadius));

            // Entity clear of obstacle: center at (colCenter.x + 30, colCenter.y), dist = 30 > 24
            assertTrue(map.isTraversable(new Vector2D(colCenter.x + 30.0f, colCenter.y), entityRadius));
        }
    }

    // =========================================================================
    // Tier 3: 2-Pass Sliding Resolution & Frustum Culling
    // =========================================================================
    @Nested
    @DisplayName("Tier 3: Sliding Collision & Frustum Culling")
    class Tier3CollisionAndCullingTests {

        @Test
        @DisplayName("3.1 2-pass sliding collision resolution against solid obstacle")
        void testSlidingCollisionResolution() {
            TileMap map = new TileMap(42);

            // Add Large Rock at (2000, 2000): colCenter = (2000, 1994), radius = 14
            map.addDecoration(DecoType.ROCK_LARGE, 2000.0f, 2000.0f, true);
            float r = 10.0f;

            // Entity positioned to the right of rock at (2030, 1994) (dist = 30 > 24, clear)
            Vector2D oldPos = new Vector2D(2030.0f, 1994.0f);
            assertTrue(map.isTraversable(oldPos, r));

            // Desired diagonal movement: left towards rock (blocked) and slightly down (clear)
            Vector2D desiredPos = new Vector2D(2010.0f, 1999.0f);
            Vector2D resolved = map.resolveCollision(oldPos, desiredPos, r);

            // X must slide / hold at oldPos.x (2030.0f), Y must advance to desiredPos.y (1999.0f)
            assertEquals(oldPos.x, resolved.x, 1e-3f);
            assertEquals(desiredPos.y, resolved.y, 1e-3f);
            assertTrue(map.isTraversable(resolved, r));

            // If both axes are blocked, position must not change
            Vector2D completelyBlocked = new Vector2D(2000.0f, 1994.0f);
            Vector2D resolvedBlocked = map.resolveCollision(oldPos, completelyBlocked, r);
            assertEquals(oldPos.x, resolvedBlocked.x, 1e-3f);
            assertEquals(oldPos.y, resolvedBlocked.y, 1e-3f);
        }

        @Test
        @DisplayName("3.2 getVisibleTileRange computes +/- 2 tile margin expansion and clamps within [0, 127]")
        void testVisibleTileRangeMarginExpansion() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.setCenter(2048.0f, 2048.0f);
            camera.setZoom(1.0f);

            // Viewport is 1280 x 720, visible bounds centered at (2048, 2048):
            // left = 2048 - 640 = 1408, top = 2048 - 360 = 1688
            // width = 1280, height = 720
            // minTx = floor(1408 / 32) - 2 = 44 - 2 = 42
            // maxTx = ceil((1408 + 1280) / 32) + 2 = ceil(2688 / 32) + 2 = 84 + 2 = 86
            // minTy = floor(1688 / 32) - 2 = 52 - 2 = 50
            // maxTy = ceil((1688 + 720) / 32) + 2 = ceil(2408 / 32) + 2 = 76 + 2 = 78
            int[] range = map.getVisibleTileRange(camera);
            assertEquals(42, range[0]);
            assertEquals(86, range[1]);
            assertEquals(50, range[2]);
            assertEquals(78, range[3]);

            // Test clamping at top-left corner
            camera.setCenter(0.0f, 0.0f);
            int[] cornerRange = map.getVisibleTileRange(camera);
            assertEquals(0, cornerRange[0]);
            assertEquals(0, cornerRange[2]);
            assertTrue(cornerRange[1] < 128);
            assertTrue(cornerRange[3] < 128);

            // Test clamping at bottom-right corner
            camera.setCenter(4096.0f, 4096.0f);
            int[] brRange = map.getVisibleTileRange(camera);
            assertEquals(127, brRange[1]);
            assertEquals(127, brRange[3]);
        }

        @Test
        @DisplayName("3.3 Camera frustum culls offscreen obstacles")
        void testObstacleFrustumCulling() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.setCenter(2048.0f, 2048.0f);
            camera.setZoom(1.35f);

            // An obstacle at the world center is within the camera view
            Obstacle centerObstacle = new Obstacle(DecoType.TREE_OAK, 2048.0f, 2048.0f, true);
            assertTrue(camera.isVisible(centerObstacle.getVisualBounds()));

            // An obstacle near the far corner (100, 100) is culled
            Obstacle cornerObstacle = new Obstacle(DecoType.TREE_OAK, 100.0f, 100.0f, true);
            assertFalse(camera.isVisible(cornerObstacle.getVisualBounds()));
        }

        @Test
        @DisplayName("3.4 (Tier 2, Feature 11): Visible tile range clamping across extreme zoom levels [0.75, 2.25]")
        void testVisibleTileRangeExtremeZoomClamping() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.snapTo(2048.0f, 2048.0f);

            float[] extremeZooms = {0.75f, 2.25f};
            for (float zoom : extremeZooms) {
                camera.setZoom(zoom);
                int[] range = map.getVisibleTileRange(camera);
                assertTrue(range[0] >= 0, "minTx must be >= 0 at zoom " + zoom);
                assertTrue(range[1] <= 127, "maxTx must be <= 127 at zoom " + zoom);
                assertTrue(range[2] >= 0, "minTy must be >= 0 at zoom " + zoom);
                assertTrue(range[3] <= 127, "maxTy must be <= 127 at zoom " + zoom);
                assertTrue(range[0] <= range[1], "minTx must be <= maxTx");
                assertTrue(range[2] <= range[3], "minTy must be <= maxTy");
            }
        }

        @Test
        @DisplayName("3.5 (Tier 2, Feature 11): Visible tile range when camera is positioned at map corners")
        void testVisibleTileRangeCornerCameraPositions() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.setZoom(1.0f);

            // Top-Left corner (0, 0)
            camera.setCenter(0.0f, 0.0f);
            int[] tlRange = map.getVisibleTileRange(camera);
            assertEquals(0, tlRange[0]);
            assertEquals(0, tlRange[2]);
            assertTrue(tlRange[1] <= 127);
            assertTrue(tlRange[3] <= 127);

            // Bottom-Right corner (4096, 4096)
            camera.setCenter(4096.0f, 4096.0f);
            int[] brRange = map.getVisibleTileRange(camera);
            assertTrue(brRange[0] >= 0);
            assertTrue(brRange[2] >= 0);
            assertEquals(127, brRange[1]);
            assertEquals(127, brRange[3]);
        }

        @Test
        @DisplayName("3.6 (Tier 2, Feature 11): Camera obstacle culling handles zero-size bounds safely")
        void testObstacleCullingZeroSizeBounds() {
            GameCamera camera = new GameCamera(1280.0f, 720.0f);
            camera.snapTo(2048.0f, 2048.0f);
            camera.setZoom(1.0f);

            // Point inside frustum
            Rectangle insideZero = new Rectangle(2048.0f, 2048.0f, 0.0f, 0.0f);
            assertTrue(camera.isVisible(insideZero));

            // Point outside frustum
            Rectangle outsideZero = new Rectangle(-500.0f, -500.0f, 0.0f, 0.0f);
            assertFalse(camera.isVisible(outsideZero));
        }
    }

    // =========================================================================
    // Tier 4: Headless Safety & Depth Sorting
    // =========================================================================
    @Nested
    @DisplayName("Tier 4: Headless Safety & Depth Sorting")
    class Tier4HeadlessAndDepthTests {

        @Test
        @DisplayName("4.1 Pre-sorted obstacles depth order ascending by anchor Y")
        void testObstacleDepthOrder() {
            TileMap map = new TileMap(42);
            map.sortObstaclesByDepth();

            List<Obstacle> obstacles = map.getObstacles();
            for (int i = 1; i < obstacles.size(); i++) {
                float prevY = obstacles.get(i - 1).getPosition().y;
                float currY = obstacles.get(i).getPosition().y;
                assertTrue(currY >= prevY,
                        "Obstacles must be sorted in ascending order of anchor Y (prev=" + prevY + ", curr=" + currY + ")");
            }
        }

        @Test
        @DisplayName("4.2 Rendering methods safely no-op in headless environment without crashing")
        void testHeadlessRenderingSafeNoOp() {
            TileMap map = new TileMap(42);
            GameCamera camera = new GameCamera();

            // Calling drawTerrain and drawDecorations with null batch / null TextureManager
            assertDoesNotThrow(() -> map.drawTerrain(null, null, camera));
            assertDoesNotThrow(() -> map.drawDecorations(null, null, camera));
        }
    }
}
