package com.spellstorm.p2;

import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.Entity;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.BalanceConfig;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Pod 3: EnemySpawner with BalanceConfig")
public class SpawnerP2Test {

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    void resetIds() {
        Entity.resetIdCounter(1);
    }

    @Test
    @DisplayName("First wave spawns 2-3 enemies within 2.0s inside [380, 520] ring with Phase 2 speeds")
    void testInitialWaveSpawnAndRing() {
        TileMap tileMap = new TileMap(42L);
        BalanceConfig cfg = BalanceConfig.defaults();
        EnemySpawner spawner = new EnemySpawner(tileMap, new Random(12345L), cfg);

        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.update(2048.0f, 2048.0f, 1.0f);
        spawner.setCamera(camera);

        assertSame(cfg, spawner.getBalanceConfig());

        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        // Before 1.2s -> no enemies yet
        spawner.update(1.0f, playerPos, activeEnemies);
        assertEquals(0, activeEnemies.size());

        // Cross 1.2s (still <= 2.0s total) -> first wave of 2..3 enemies
        spawner.update(0.3f, playerPos, activeEnemies);
        assertTrue(activeEnemies.size() >= 2 && activeEnemies.size() <= 3,
                "Expected initial wave of 2-3 enemies, got " + activeEnemies.size());

        for (Enemy e : activeEnemies) {
            float dist = e.getPosition().dist(playerPos);
            assertTrue(dist >= 380.0f - 1e-2f && dist <= 520.0f + 1e-2f,
                    "Enemy spawned outside [380, 520] ring: dist=" + dist);
            assertTrue(tileMap.isTraversable(e.getX(), e.getY(), e.getRadius()));

            if (e.getType() == EnemyType.MELEE) {
                assertEquals(185.0f, e.getSpeed(), 1e-4f);
            } else if (e.getType() == EnemyType.RANGED) {
                assertEquals(160.0f, e.getSpeed(), 1e-4f);
            }
        }
    }

    @Test
    @DisplayName("Spawn ring stays [380, 520] and applies BalanceConfig speeds and boss volley cooldown")
    void testRingAndArchetypeTuning() {
        TileMap tileMap = new TileMap(42L);
        BalanceConfig cfg = BalanceConfig.defaults();
        EnemySpawner spawner = new EnemySpawner(tileMap, new Random(77L), cfg);
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        for (int i = 0; i < 30; i++) {
            Enemy melee = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
            float dist = melee.getPosition().dist(playerPos);
            assertTrue(dist >= 380.0f - 1e-2f && dist <= 520.0f + 1e-2f,
                    "Spawn dist expected in [380, 520], got " + dist);
            assertEquals(185.0f, melee.getSpeed(), 1e-4f);
        }

        Enemy ranged = spawner.spawnEnemy(playerPos, EnemyType.RANGED);
        Enemy tank = spawner.spawnEnemy(playerPos, EnemyType.TANK);
        Enemy boss = spawner.spawnBoss(playerPos);

        assertEquals(160.0f, ranged.getSpeed(), 1e-4f);
        assertEquals(120.0f, tank.getSpeed(), 1e-4f);
        assertEquals(100.0f, boss.getSpeed(), 1e-4f);
        assertInstanceOf(BossEnemy.class, boss);
        assertEquals(3.2f, ((BossEnemy) boss).getVolleyCooldown(), 1e-4f);
        assertEquals(1800.0f, boss.getMaxHealth(), 1e-4f);
        assertEquals(45.0f, boss.getArmor(), 1e-4f);
        assertEquals(50.0f, boss.getRadius(), 1e-4f);
    }

    @Test
    @DisplayName("Spawner applies BalanceConfig interval/cap ramp and resets cleanly")
    void testBalanceProgressionAndReset() {
        TileMap tileMap = new TileMap(42L);
        EnemySpawner spawner = new EnemySpawner(tileMap, new Random(99L), BalanceConfig.defaults());
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        assertEquals(1.6f, spawner.getCurrentInterval(), 1e-4f);
        assertEquals(20, spawner.getMaxEnemies());

        spawner.update(60.0f, playerPos, activeEnemies);
        assertEquals(1.05f, spawner.getCurrentInterval(), 1e-2f);
        assertEquals(30, spawner.getMaxEnemies());

        spawner.update(60.0f, playerPos, activeEnemies);
        assertEquals(0.5f, spawner.getCurrentInterval(), 1e-2f);
        assertEquals(40, spawner.getMaxEnemies());

        spawner.reset();
        activeEnemies.clear();
        assertEquals(0.0f, spawner.getElapsedTime(), 1e-4f);
        assertEquals(1.2f, spawner.getSpawnTimer(), 1e-4f);
        assertEquals(1.6f, spawner.getCurrentInterval(), 1e-4f);
        assertEquals(20, spawner.getMaxEnemies());

        // Initial wave triggers again after reset
        spawner.update(1.3f, playerPos, activeEnemies);
        assertTrue(activeEnemies.size() >= 2 && activeEnemies.size() <= 3,
                "Expected initial wave of 2-3 after reset, got " + activeEnemies.size());
    }
}
