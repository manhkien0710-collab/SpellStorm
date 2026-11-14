package com.spellstorm;

import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.ui.HudRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite covering the periodic Boss encounter lifecycle:
 * 60s first encounter milestone, 90s recurrence interval, 10s retry delay when boss is fighting,
 * health scaling immunity, cap limit exemption, and HUD health bar visibility.
 */
public class BossSpawnerTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. 60s Milestone Encounter & 90s Recurrence
    // =========================================================================

    @Test
    @DisplayName("Boss spawns at 60s milestone when no active boss is present, resetting timer to 90s")
    public void testBossFirstSpawnAt60Seconds() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        assertEquals(60.0f, spawner.getBossSpawnTimer(), 0.01f);

        // Advance 59.0s -> timer = 1.0s: no boss spawned yet
        spawner.update(59.0f, playerPos, activeEnemies);
        assertFalse(HudRenderer.isBossBarVisible(activeEnemies));
        assertEquals(1.0f, spawner.getBossSpawnTimer(), 0.1f);

        // Advance 1.5s -> crosses 60s threshold: boss is spawned
        spawner.update(1.5f, playerPos, activeEnemies);
        assertTrue(HudRenderer.isBossBarVisible(activeEnemies));

        // Find spawned boss
        Enemy boss = HudRenderer.findActiveBoss(activeEnemies);
        assertNotNull(boss);
        assertTrue(boss.isBoss());
        assertEquals("ANCIENT GOLEM OVERLORD", boss.getName());
        assertEquals(1800.0f, boss.getMaxHealth(), 0.01f);

        // Timer must reset to 90.0s for next recurrence
        assertEquals(90.0f, spawner.getBossSpawnTimer(), 0.5f);
    }

    @Test
    @DisplayName("Boss concurrency check: retries in 10s if previous boss is still alive")
    public void testBossRetryWhenPreviousBossIsAlive() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        // Manually trigger timer to 0s while an alive boss is already in the arena
        BossEnemy existingBoss = new BossEnemy(new Vector2D(2200.0f, 2200.0f));
        activeEnemies.add(existingBoss);
        spawner.setBossSpawnTimer(0.1f);

        // Update 0.2s: timer expires, but detects existing living boss
        spawner.update(0.2f, playerPos, activeEnemies);

        // Count bosses in active list: must remain exactly 1
        long bossCount = activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count();
        assertEquals(1, bossCount, "Must not spawn duplicate boss while one is alive");

        // Timer is deferred by exactly 10.0s retry delay
        assertEquals(10.0f, spawner.getBossSpawnTimer(), 0.5f);
    }

    @Test
    @DisplayName("Next boss wave spawns after 90s once previous boss is defeated")
    public void testBossRecurrenceAfterDefeat() {
        EnemySpawner spawner = new EnemySpawner();
        List<Enemy> activeEnemies = new ArrayList<>();
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        // First boss spawns at 60s
        spawner.update(60.1f, playerPos, activeEnemies);
        BossEnemy firstBoss = (BossEnemy) HudRenderer.findActiveBoss(activeEnemies);
        assertNotNull(firstBoss);
        assertEquals(90.0f, spawner.getBossSpawnTimer(), 0.5f);

        // Kill the first boss
        firstBoss.takeDamage(9999.0f, true);
        assertFalse(firstBoss.isAlive());

        // Advance 90.5s: second boss wave spawns
        spawner.update(90.5f, playerPos, activeEnemies);
        long aliveBossCount = activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count();
        assertEquals(1, aliveBossCount, "New boss should spawn after 90s recurrence following defeat");
    }

    // =========================================================================
    // 2. Health Scaling Immunity & Cap Exemption
    // =========================================================================

    @Test
    @DisplayName("Boss is strictly exempt from spawner late-game health scaling (!enemy.isBoss())")
    public void testBossHealthScalingExemption() {
        EnemySpawner spawner = new EnemySpawner();
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        // Advance spawner time deep into late-game (e.g. 300s, where standard enemies get +25% HP)
        List<Enemy> list = new ArrayList<>();
        spawner.update(300.0f, playerPos, list);

        // Standard melee enemy has scaled health: 30 * 1.25 = 37.5 HP
        Enemy melee = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
        assertTrue(melee.getMaxHealth() > 30.0f);

        // Boss enemy retains unscaled base health (1800.0 HP)
        Enemy boss = spawner.spawnEnemy(playerPos, EnemyType.BOSS);
        assertTrue(boss.isBoss());
        assertEquals(1800.0f, boss.getMaxHealth(), 0.001f,
                "Boss must maintain exactly 1800.0 HP and be exempt from spawner health scaling");
    }

    // =========================================================================
    // 3. HUD Boss Health Bar Telemetry
    // =========================================================================

    @Test
    @DisplayName("HUD boss health bar format and visibility invariants")
    public void testHudBossBarVisibilityAndText() {
        List<Enemy> enemies = new ArrayList<>();

        // No boss in list: bar is hidden
        assertFalse(HudRenderer.isBossBarVisible(enemies));
        assertNull(HudRenderer.findActiveBoss(enemies));

        // Add living boss: bar is visible
        BossEnemy boss = new BossEnemy(new Vector2D(100.0f, 100.0f));
        enemies.add(boss);
        assertTrue(HudRenderer.isBossBarVisible(enemies));
        assertEquals("1800 / 1800 (100%)", HudRenderer.formatHpText(boss));

        // Boss takes 900 damage: health ~ 1179
        boss.takeDamage(900.0f, false);
        assertEquals("1180 / 1800 (65%)", HudRenderer.formatHpText(boss));

        // Boss defeated: bar is hidden immediately
        boss.takeDamage(2000.0f, true);
        assertFalse(boss.isAlive());
        assertFalse(HudRenderer.isBossBarVisible(enemies),
                "Boss bar must be hidden immediately when the boss is dead");
    }
}
