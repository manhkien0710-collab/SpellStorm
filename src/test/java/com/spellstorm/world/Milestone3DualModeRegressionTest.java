package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3: Dual-Mode Regression & TileMap Invariance Tests")
public class Milestone3DualModeRegressionTest {

    private static final long SEED = 42L;

    @Test
    @DisplayName("1. TileMap.java source file is 100% untouched (0 bytes diff against Git base)")
    void testTileMapSourceUntouched() {
        try {
            Process process = new ProcessBuilder("git", "diff", "src/main/java/com/spellstorm/world/TileMap.java")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }
            int exitCode = process.waitFor();
            assertEquals(0, exitCode, "git diff command must exit with 0");
            assertTrue(output.toString().trim().isEmpty(),
                    "src/main/java/com/spellstorm/world/TileMap.java MUST HAVE 0 BYTES DIFF! Output: " + output);
        } catch (Exception e) {
            fail("Failed to verify TileMap git diff: " + e.getMessage());
        }
    }

    @Test
    @DisplayName("2. TileMapAdapter bitwise query parity with legacy TileMap")
    void testTileMapAdapterParityWithTileMap() {
        TileMap map = new TileMap(SEED);
        TileMapAdapter adapter = new TileMapAdapter(map);

        assertEquals(map.getWorldBounds(), adapter.getWorldBounds());
        assertTrue(adapter.isBounded());

        for (int ty = 0; ty < 128; ty += 8) {
            for (int tx = 0; tx < 128; tx += 8) {
                assertEquals(map.getTile(tx, ty), adapter.getTile(tx, ty));
                assertEquals(map.isSolidTile(tx, ty), adapter.isSolidTile(tx, ty));
                assertEquals(map.isWaterTile(tx, ty), adapter.isWaterTile(tx, ty));
            }
        }

        // Test traversability across 50 sample positions
        for (float y = 50.0f; y < 4000.0f; y += 150.0f) {
            for (float x = 50.0f; x < 4000.0f; x += 150.0f) {
                assertEquals(map.isTraversable(x, y, 16.0f), adapter.isTraversable(x, y, 16.0f));
            }
        }
    }

    @Test
    @DisplayName("3. Camera clamping behavior: Clamped in finite mode, unclamped in infinite mode")
    void testCameraClampingModes() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f, 4096.0f, 4096.0f);
        assertTrue(camera.isClampingEnabled(), "Default clamping must be true for baseline compatibility");

        // Attempting to move past left bound (-100, 2048)
        camera.setCenter(-100.0f, 2048.0f);
        assertTrue(camera.getCenterX() > 0.0f, "Finite mode must clamp centerX within world boundaries");

        // Switching to infinite mode: unclamped
        camera.setClampingEnabled(false);
        assertFalse(camera.isClampingEnabled());

        camera.setCenter(-100.0f, 2048.0f);
        assertEquals(-100.0f, camera.getCenterX(), 1e-4f, "Infinite mode must allow negative camera coordinates");

        camera.setCenter(5000.0f, -500.0f);
        assertEquals(5000.0f, camera.getCenterX(), 1e-4f);
        assertEquals(-500.0f, camera.getCenterY(), 1e-4f);
    }

    @Test
    @DisplayName("4. Player movement: Works with TileMap (clamped) and WorldGrid / InfiniteWorld (unbounded)")
    void testPlayerMovementDualMode() {
        TileMap tileMap = new TileMap(SEED);
        Player playerFinite = new Player(new Vector2D(100.0f, 100.0f));

        // Moving left in finite mode: cannot cross boundary x < 18
        playerFinite.setPosition(new Vector2D(20.0f, 500.0f));
        playerFinite.moveAndResolve(new Vector2D(-1.0f, 0.0f), 0.5f, tileMap);
        assertTrue(playerFinite.getPosition().x >= playerFinite.getRadius(),
                "Player must be clamped to world boundary in TileMap mode");

        // Player using WorldGrid with InfiniteWorld
        ChunkManager chunkManager = new ChunkManagerImpl(SEED, 64);
        InfiniteWorld infiniteWorld = new InfiniteWorld(chunkManager);
        Player playerInfinite = new Player(new Vector2D(20.0f, 500.0f));

        playerInfinite.moveAndResolve(new Vector2D(-1.0f, 0.0f), 0.5f, infiniteWorld);
        assertTrue(playerInfinite.getPosition().x < 0.0f,
                "Player must be permitted to cross into negative coordinates in InfiniteWorld mode");
    }

    @Test
    @DisplayName("5. Unbounded sliding collision resolution allows movement into negative coordinates")
    void testNegativeCoordinateSlidingCollision() {
        ChunkManager chunkManager = new ChunkManagerImpl(SEED, 64);
        InfiniteWorld infiniteWorld = new InfiniteWorld(chunkManager);

        // Position on East-West Highway (y=16.0f), guaranteed walkable bridge/road across all X
        Vector2D oldPos = new Vector2D(-50.0f, 16.0f);
        Vector2D newPos = new Vector2D(-60.0f, 16.0f);

        Vector2D resolved = infiniteWorld.resolveCollision(oldPos, newPos, 8.0f);
        assertEquals(-60.0f, resolved.x, 1e-4f,
                "Unbounded sliding must allow continuous negative coordinate traversal");
        assertEquals(16.0f, resolved.y, 1e-4f);
    }

    @Test
    @DisplayName("6. FloatingOriginManager state lifecycle and reset parity")
    void testFloatingOriginManagerLifecycle() {
        FloatingOriginManager manager = new FloatingOriginManager();
        assertEquals(0L, manager.getOriginShiftX());
        assertEquals(0L, manager.getOriginShiftY());
        assertEquals(0L, manager.getTotalRebaseCount());

        manager.rebaseWorldOrigin(1024L, 2048L);
        assertEquals(1024L, manager.getOriginShiftX());
        assertEquals(2048L, manager.getOriginShiftY());
        assertEquals(1, manager.getOriginChunkX());
        assertEquals(2, manager.getOriginChunkY());
        assertEquals(1L, manager.getTotalRebaseCount());

        manager.reset();
        assertEquals(0L, manager.getOriginShiftX());
        assertEquals(0L, manager.getOriginShiftY());
        assertEquals(0, manager.getOriginChunkX());
        assertEquals(0, manager.getOriginChunkY());
        assertEquals(0L, manager.getTotalRebaseCount());
    }
}
