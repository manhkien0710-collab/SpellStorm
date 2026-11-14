package com.spellstorm.systems;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for EnemySpawner system.
 * Covers spawn ring positioning outside camera view, traversability verification
 * (free of water & solid obstacles), difficulty scaling over 120s timeline,
 * enemy cap enforcement, and wave progression.
 */
public class EnemySpawnerTest {

    @BeforeAll
    public static void setupHeadless() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Tier 1 & 2: Timers, Caps, and Pruning
    // =========================================================================

    @Test
    @DisplayName("Spawner initial grace period before first spawn (1.5s)")
    public void testSpawnerInitialGracePeriod() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048, 2048);

        // Advance 0.5s -> total 0.5s < 1.5s: 0 spawns
        spawner.update(0.5f, playerPos, activeEnemies);
        assertEquals(0, activeEnemies.size());

        // Advance 0.9s -> total 1.4s < 1.5s: 0 spawns
        spawner.update(0.9f, playerPos, activeEnemies);
        assertEquals(0, activeEnemies.size());

        // Advance 0.2s -> total 1.6s > 1.5s: 1st spawn occurs
        spawner.update(0.2f, playerPos, activeEnemies);
        assertEquals(1, activeEnemies.size());
    }

    @Test
    @DisplayName("Spawner maximum enemy cap enforcement")
    public void testSpawnerMaximumEnemyCapEnforcement() {
        EnemySpawner spawner = new EnemySpawner();
        spawner.setMaxEnemies(4);
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048, 2048);

        // Update for 30 seconds to test regular enemy cap enforcement before 60s boss encounter
        for (int i = 0; i < 60; i++) {
            spawner.update(0.5f, playerPos, activeEnemies);
        }

        assertEquals(4, activeEnemies.size());
    }

    @Test
    @DisplayName("Dead enemy removal and pruning from active list")
    public void testDeadEnemyRemovalAndPruning() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048, 2048);

        // Spawn 3 enemies
        for (int i = 0; i < 3; i++) {
            activeEnemies.add(spawner.spawnEnemy(playerPos));
        }
        assertEquals(3, activeEnemies.size());

        // Kill the 2nd enemy
        activeEnemies.get(1).takeDamage(9999.0f);
        assertFalse(activeEnemies.get(1).isAlive());

        // Safe removal
        activeEnemies.removeIf(e -> !e.isAlive());
        assertEquals(2, activeEnemies.size());
        assertTrue(activeEnemies.get(0).isAlive());
        assertTrue(activeEnemies.get(1).isAlive());
    }

    // =========================================================================
    // Tier 3: Spawn Ring & Traversability Validation
    // =========================================================================

    @Test
    @DisplayName("Spawn ring positioning outside camera visible bounds with safety margin")
    public void testSpawnerRingPositioningOutsideCameraBounds() {
        TileMap tileMap = new TileMap(42);
        EnemySpawner spawner = new EnemySpawner(tileMap);

        GameCamera camera = new GameCamera(1280, 720);
        camera.setTargetZoom(1.35f);
        camera.update(2048.0f, 2048.0f, 1.0f);
        spawner.setCamera(camera);

        Vector2D playerPos = new Vector2D(2048, 2048);
        Rectangle vis = camera.getVisibleBounds();

        // Spawn 40 enemies and verify distance is outside camera visible view
        for (int i = 0; i < 40; i++) {
            Vector2D spawnPos = spawner.generateSpawnPosition(playerPos, 220.0f, 16.0f);
            float dist = spawnPos.dist(playerPos);

            // Minimum distance must be at least 450px and <= 950px
            assertTrue(dist >= 450.0f, "Spawn distance should be >= 450px from player, was " + dist);
            assertTrue(dist <= 950.0f, "Spawn distance should be <= 950px from player, was " + dist);

            // Verify spawn point is not inside visible camera viewport center
            float halfW = vis.width * 0.5f;
            float halfH = vis.height * 0.5f;
            boolean insideFrustum = Math.abs(spawnPos.x - playerPos.x) < halfW
                    && Math.abs(spawnPos.y - playerPos.y) < halfH;
            assertFalse(insideFrustum, "Spawn position should be outside visible camera frustum");
        }
    }

    @Test
    @DisplayName("Traversability verification: no spawns in water or solid obstacles")
    public void testSpawnerTraversabilityValidationWaterAndObstacles() {
        TileMap tileMap = new TileMap(42);
        EnemySpawner spawner = new EnemySpawner(tileMap);
        Vector2D playerPos = new Vector2D(2048, 2048);

        for (int i = 0; i < 60; i++) {
            Vector2D spawnPos = spawner.generateSpawnPosition(playerPos, 220.0f, 16.0f);

            // Must be traversable
            assertTrue(tileMap.isTraversable(spawnPos.x, spawnPos.y, 16.0f),
                    "Spawn point at (" + spawnPos.x + ", " + spawnPos.y + ") must be traversable");

            // Must not be water tile center
            int tx = (int) (spawnPos.x / TileMap.TILE_SIZE);
            int ty = (int) (spawnPos.y / TileMap.TILE_SIZE);
            assertFalse(tileMap.isWater(tx, ty), "Spawn point must not be on water tile");
        }
    }

    @Test
    @DisplayName("Difficulty scaling over time (spawn interval tightening & cap growth)")
    public void testSpawnerDifficultyScalingOverTime() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048, 2048);

        // At t = 0s
        assertEquals(2.2f, spawner.getCurrentInterval(), 1e-4f);
        assertEquals(20, spawner.getMaxEnemies());

        // Advance to t = 60s (halfway through 120s scaling)
        spawner.update(60.0f, playerPos, activeEnemies);
        // Interval halfway: 2.2 - 0.5 * (2.2 - 0.55) = 2.2 - 0.825 = 1.375
        assertEquals(1.375f, spawner.getCurrentInterval(), 1e-2f);
        // Cap halfway: 20 + 0.5 * (36 - 20) = 28
        assertEquals(28, spawner.getMaxEnemies());

        // Advance to t = 120s (full scaling)
        spawner.update(60.0f, playerPos, activeEnemies);
        assertEquals(0.55f, spawner.getCurrentInterval(), 1e-2f);
        assertEquals(36, spawner.getMaxEnemies());
    }

    @Test
    @DisplayName("Controlled late-game HP scaling (+25% max after 50s)")
    public void testSpawnerLateGameHpScaling() {
        EnemySpawner spawner = new EnemySpawner();
        Vector2D playerPos = new Vector2D(2048, 2048);

        // Early enemy (t = 0)
        Enemy early = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
        assertEquals(30.0f, early.getMaxHealth(), 1e-4f);

        // Advance time to 200s
        List<Enemy> enemies = new ArrayList<>();
        spawner.update(200.0f, playerPos, enemies);

        Enemy late = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
        // HP scale = 1.0 + clamp((200 - 50) / 600, 0, 0.25) = 1.0 + 150/600 = 1.25 -> 30 * 1.25 = 37.5
        assertEquals(37.5f, late.getMaxHealth(), 1e-1f);
    }

    @Test
    @DisplayName("Deterministic pseudo-random seed reproducibility")
    public void testSpawnerSeedReproducibility() {
        TileMap map1 = new TileMap(42);
        TileMap map2 = new TileMap(42);

        EnemySpawner s1 = new EnemySpawner(map1, new Random(999L));
        EnemySpawner s2 = new EnemySpawner(map2, new Random(999L));

        Vector2D playerPos = new Vector2D(2048, 2048);

        for (int i = 0; i < 10; i++) {
            Enemy e1 = s1.spawnEnemy(playerPos);
            Enemy e2 = s2.spawnEnemy(playerPos);

            assertEquals(e1.getType(), e2.getType());
            assertEquals(e1.getX(), e2.getX(), 1e-4f);
            assertEquals(e1.getY(), e2.getY(), 1e-4f);
        }
    }

    // =========================================================================
    // Tier 4: Wave Progression Timeline & Full Simulation Cycle
    // =========================================================================

    @Test
    @DisplayName("Wave progression timeline: early melee/ranged, mid tanks, late mixed waves")
    public void testWaveProgressionTimeline0To120s() {
        EnemySpawner spawner = new EnemySpawner(null, new Random(42L));
        Vector2D playerPos = new Vector2D(2048, 2048);

        // Early phase (< 25s): strictly Melee and Ranged, zero Tanks
        for (int i = 0; i < 20; i++) {
            Enemy e = spawner.spawnEnemy(playerPos);
            assertNotEquals(EnemyType.TANK, e.getType(), "Early phase should not spawn Tanks");
        }

        // Fast forward to mid phase (t = 50s)
        List<Enemy> list = new ArrayList<>();
        spawner.update(50.0f, playerPos, list);

        boolean sawTank = false;
        for (int i = 0; i < 30; i++) {
            Enemy e = spawner.spawnEnemy(playerPos);
            if (e.getType() == EnemyType.TANK) {
                sawTank = true;
                break;
            }
        }
        assertTrue(sawTank, "Mid phase should spawn Tanks");
    }

    @Test
    @DisplayName("Full combat match simulation cycle and clean reset")
    public void testFullMatchCombatAndCleanupCycle() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048, 2048);

        // Run 60 seconds in 0.1s steps
        for (int step = 0; step < 600; step++) {
            spawner.update(0.1f, playerPos, activeEnemies);

            // Simulate combat damage & pruning every 20 steps
            if (step % 20 == 0 && !activeEnemies.isEmpty()) {
                activeEnemies.get(0).takeDamage(9999.0f);
                activeEnemies.removeIf(e -> !e.isAlive());
            }
        }

        assertTrue(activeEnemies.size() <= spawner.getMaxEnemies());
        assertTrue(spawner.getElapsedTime() >= 59.9f);

        // Reset spawner to start
        spawner.reset();
        assertEquals(0.0f, spawner.getElapsedTime(), 1e-4f);
        assertEquals(1.5f, spawner.getSpawnTimer(), 1e-4f);
        assertEquals(2.2f, spawner.getCurrentInterval(), 1e-4f);
        assertEquals(20, spawner.getMaxEnemies());
    }
}
