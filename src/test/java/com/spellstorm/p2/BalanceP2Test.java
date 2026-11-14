package com.spellstorm.p2;

import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Entity;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.BalanceConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Pod 3: BalanceConfig, Facing, Boss Volley & Player Walk Frame")
public class BalanceP2Test {

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    void resetIds() {
        Entity.resetIdCounter(1);
    }

    @Test
    @DisplayName("BalanceConfig.defaults() provides Phase 2 tuned constants")
    void testDefaultsValues() {
        BalanceConfig cfg = BalanceConfig.defaults();

        assertEquals(230.0f, cfg.getPlayerSpeed(), 1e-4f);
        assertEquals(185.0f, cfg.getMeleeSpeed(), 1e-4f);
        assertEquals(160.0f, cfg.getRangedSpeed(), 1e-4f);
        assertEquals(120.0f, cfg.getTankSpeed(), 1e-4f);
        assertEquals(100.0f, cfg.getBossSpeed(), 1e-4f);

        assertEquals(1800.0f, cfg.getBossHp(), 1e-4f);
        assertEquals(45.0f, cfg.getBossArmor(), 1e-4f);
        assertEquals(50.0f, cfg.getBossRadius(), 1e-4f);
        assertEquals(3.2f, cfg.getBossVolleyCooldown(), 1e-4f);

        assertEquals(380.0f, cfg.getMinSpawnDistance(), 1e-4f);
        assertEquals(520.0f, cfg.getMaxSpawnDistance(), 1e-4f);
        assertTrue(cfg.getInitialSpawnDelay() <= 2.0f);
        assertEquals(1.2f, cfg.getInitialSpawnDelay(), 1e-4f);
        assertEquals(2, cfg.getInitialWaveMin());
        assertEquals(3, cfg.getInitialWaveMax());

        assertEquals(1.6f, cfg.getBaseSpawnInterval(), 1e-4f);
        assertEquals(0.5f, cfg.getMinSpawnInterval(), 1e-4f);
        assertEquals(20, cfg.getBaseMaxEnemies());
        assertEquals(40, cfg.getMaxEnemiesCap());
        assertEquals(120.0f, cfg.getRampDurationSeconds(), 1e-4f);
    }

    @Test
    @DisplayName("BalanceConfig.legacy() preserves Phase 1 values and raw constructors stay untouched")
    void testLegacyValuesAndRawConstructors() {
        BalanceConfig legacy = BalanceConfig.legacy();
        assertEquals(140.0f, legacy.getMeleeSpeed(), 1e-4f);
        assertEquals(110.0f, legacy.getRangedSpeed(), 1e-4f);
        assertEquals(80.0f, legacy.getTankSpeed(), 1e-4f);
        assertEquals(55.0f, legacy.getBossSpeed(), 1e-4f);
        assertEquals(4.0f, legacy.getBossVolleyCooldown(), 1e-4f);
        assertEquals(2.2f, legacy.getBaseSpawnInterval(), 1e-4f);
        assertEquals(0.55f, legacy.getMinSpawnInterval(), 1e-4f);
        assertEquals(20, legacy.getBaseMaxEnemies());
        assertEquals(36, legacy.getMaxEnemiesCap());

        MeleeEnemy melee = new MeleeEnemy(new Vector2D(0, 0));
        RangedEnemy ranged = new RangedEnemy(new Vector2D(0, 0));
        TankEnemy tank = new TankEnemy(new Vector2D(0, 0));
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
        assertEquals(140.0f, melee.getBaseMoveSpeed(), 1e-4f);
        assertEquals(110.0f, ranged.getBaseMoveSpeed(), 1e-4f);
        assertEquals(80.0f, tank.getBaseMoveSpeed(), 1e-4f);
        assertEquals(55.0f, boss.getBaseMoveSpeed(), 1e-4f);
        assertEquals(4.0f, boss.getVolleyCooldown(), 1e-4f);
    }

    @Test
    @DisplayName("applyToEnemy updates archetype speeds and boss volley cooldown")
    void testApplyToEnemyAndBossVolleyCooldown() {
        BalanceConfig cfg = BalanceConfig.defaults();

        MeleeEnemy melee = new MeleeEnemy(new Vector2D(0, 0));
        RangedEnemy ranged = new RangedEnemy(new Vector2D(0, 0));
        TankEnemy tank = new TankEnemy(new Vector2D(0, 0));
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));

        cfg.applyToEnemy(melee);
        cfg.applyToEnemy(ranged);
        cfg.applyToEnemy(tank);
        cfg.applyToEnemy(boss);
        cfg.applyToEnemy(null);

        assertEquals(185.0f, melee.getSpeed(), 1e-4f);
        assertEquals(160.0f, ranged.getSpeed(), 1e-4f);
        assertEquals(120.0f, tank.getSpeed(), 1e-4f);
        assertEquals(100.0f, boss.getSpeed(), 1e-4f);

        assertEquals(1800.0f, boss.getMaxHealth(), 1e-4f);
        assertEquals(45.0f, boss.getArmor(), 1e-4f);
        assertEquals(50.0f, boss.getRadius(), 1e-4f);
        assertEquals(3.2f, boss.getVolleyCooldown(), 1e-4f);

        boss.fireVolley(new Vector2D(100, 0));
        assertEquals(3.2f, boss.getVolleyTimer(), 1e-4f);
        boss.setVolleyTimer(0.0f);
        boss.resetVolleyTimer();
        assertEquals(3.2f, boss.getVolleyTimer(), 1e-4f);
    }

    @Test
    @DisplayName("computeSpawnInterval and computeMaxEnemies ramp smoothly over 120s")
    void testProgressionRamp() {
        BalanceConfig cfg = BalanceConfig.defaults();

        assertEquals(1.6f, cfg.computeSpawnInterval(-5.0f), 1e-4f);
        assertEquals(1.6f, cfg.computeSpawnInterval(0.0f), 1e-4f);
        assertEquals(1.05f, cfg.computeSpawnInterval(60.0f), 1e-4f);
        assertEquals(0.5f, cfg.computeSpawnInterval(120.0f), 1e-4f);
        assertEquals(0.5f, cfg.computeSpawnInterval(300.0f), 1e-4f);

        assertEquals(20, cfg.computeMaxEnemies(-5.0f));
        assertEquals(20, cfg.computeMaxEnemies(0.0f));
        assertEquals(30, cfg.computeMaxEnemies(60.0f));
        assertEquals(40, cfg.computeMaxEnemies(120.0f));
        assertEquals(40, cfg.computeMaxEnemies(300.0f));
    }

    @Test
    @DisplayName("Enemy horizontal facingLeft tracks movement direction and target X cleanly")
    void testEnemyFacingTracking() {
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(500.0f, 500.0f));
        assertFalse(melee.isFacingLeft());

        // Move toward target on the left -> facingLeft = true
        melee.updateAI(0.016f, new Vector2D(200.0f, 500.0f), null, null);
        assertTrue(melee.isFacingLeft());

        // Move toward target on the right -> facingLeft = false
        melee.updateAI(0.016f, new Vector2D(800.0f, 500.0f), null, null);
        assertFalse(melee.isFacingLeft());

        // Ranged enemy kiting left away from player to its right -> velocity.x < -1 -> facingLeft = true
        RangedEnemy ranged = new RangedEnemy(new Vector2D(500.0f, 500.0f));
        ranged.updateAI(0.016f, new Vector2D(550.0f, 500.0f), null, null);
        assertTrue(ranged.isFacingLeft());

        // Boss facing updates when firing volley while stationary
        BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 500.0f));
        boss.setVelocity(0.0f, 0.0f);
        boss.fireVolley(new Vector2D(300.0f, 500.0f));
        assertTrue(boss.isFacingLeft());
        boss.fireVolley(new Vector2D(700.0f, 500.0f));
        assertFalse(boss.isFacingLeft());

        boss.setFacingLeft(true);
        assertTrue(boss.isFacingLeft());
    }

    @Test
    @DisplayName("Player defaults to single stable walk frame (0) to avoid frame-1 flicker")
    void testPlayerStableSingleWalkFrame() {
        Player player = new Player(new Vector2D(2048.0f, 2048.0f));
        assertTrue(player.isSingleFrameWalk());
        assertEquals(0, player.getAnimationFrame());

        for (int i = 0; i < 30; i++) {
            player.handleMovementInput(false, false, false, true, 0.05f, null);
            assertEquals(0, player.getAnimationFrame(),
                    "Default single-frame walk must keep animation frame at 0 while moving");
        }
        assertTrue(player.getAnimTimer() > 0.0f);

        // Opting into multi-frame walk still works when explicitly disabled
        player.setSingleFrameWalk(false);
        assertFalse(player.isSingleFrameWalk());
        player.setVelocity(0.0f, 0.0f);
        assertEquals(0, player.getAnimationFrame());
    }
}
