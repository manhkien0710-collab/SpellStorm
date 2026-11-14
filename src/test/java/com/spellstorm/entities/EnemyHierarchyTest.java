package com.spellstorm.entities;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for Enemy Hierarchy (Melee, Ranged, Tank).
 * Covers stats, contact combat, attack cooldowns, exp rewards, boundary clamping,
 * direct LOS charging, obstacle navigation, kiting, and strafing.
 */
public class EnemyHierarchyTest {

    @BeforeAll
    public static void setupHeadless() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    public void setUp() {
        Entity.resetIdCounter(1);
    }

    // =========================================================================
    // Tier 1: Archetype Stats & Legacy Compatibility
    // =========================================================================

    @Test
    @DisplayName("EnemyType enum properties and default dimensions")
    public void testEnemyTypeEnumProperties() {
        assertEquals("Swarm Skeleton", EnemyType.MELEE.getDisplayName());
        assertEquals(16.0f, EnemyType.MELEE.getDefaultRadius(), 1e-4f);
        assertEquals(30.0f, EnemyType.MELEE.getDefaultHealth(), 1e-4f);
        assertEquals(140.0f, EnemyType.MELEE.getDefaultSpeed(), 1e-4f);
        assertEquals(15.0f, EnemyType.MELEE.getDefaultContactDamage(), 1e-4f);
        assertEquals(0.8f, EnemyType.MELEE.getDefaultAttackCooldown(), 1e-4f);
        assertEquals(10, EnemyType.MELEE.getDefaultExpReward());
        assertFalse(EnemyType.MELEE.isBoss());

        assertEquals("Cultist", EnemyType.RANGED.getDisplayName());
        assertEquals(15.0f, EnemyType.RANGED.getDefaultRadius(), 1e-4f);
        assertEquals(20.0f, EnemyType.RANGED.getDefaultHealth(), 1e-4f);
        assertEquals(110.0f, EnemyType.RANGED.getDefaultSpeed(), 1e-4f);
        assertEquals(10.0f, EnemyType.RANGED.getDefaultContactDamage(), 1e-4f);
        assertEquals(15, EnemyType.RANGED.getDefaultExpReward());
        assertFalse(EnemyType.RANGED.isBoss());

        assertEquals("Golem", EnemyType.TANK.getDisplayName());
        assertEquals(26.0f, EnemyType.TANK.getDefaultRadius(), 1e-4f);
        assertEquals(120.0f, EnemyType.TANK.getDefaultHealth(), 1e-4f);
        assertEquals(80.0f, EnemyType.TANK.getDefaultSpeed(), 1e-4f);
        assertEquals(25.0f, EnemyType.TANK.getDefaultContactDamage(), 1e-4f);
        assertEquals(25.0f, EnemyType.TANK.getDefaultArmor(), 1e-4f);
        assertEquals(30, EnemyType.TANK.getDefaultExpReward());
        assertFalse(EnemyType.TANK.isBoss());

        assertEquals("Ancient Colossus", EnemyType.BOSS.getDisplayName());
        assertEquals(50.0f, EnemyType.BOSS.getDefaultRadius(), 1e-4f);
        assertEquals(1800.0f, EnemyType.BOSS.getDefaultHealth(), 1e-4f);
        assertEquals(45.0f, EnemyType.BOSS.getDefaultArmor(), 1e-4f);
        assertTrue(EnemyType.BOSS.isBoss());
    }

    @Test
    @DisplayName("MeleeEnemy default stats and legacy constructor overload")
    public void testMeleeEnemyDefaultStatsAndLegacyOverload() {
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(100, 100));
        assertEquals(EnemyType.MELEE, melee.getType());
        assertEquals(30.0f, melee.getHealth(), 1e-4f);
        assertEquals(140.0f, melee.getBaseMoveSpeed(), 1e-4f);
        assertEquals(15.0f, melee.getContactDamage(), 1e-4f);
        assertEquals(16.0f, melee.getRadius(), 1e-4f);
        assertEquals(0.8f, melee.getAttackCooldown(), 1e-4f);
        assertEquals(10, melee.getExpReward());

        MeleeEnemy legacy = MeleeEnemy.createLegacyReference(new Vector2D(100, 100));
        assertEquals(60.0f, legacy.getHealth(), 1e-4f);
        assertEquals(105.0f, legacy.getBaseMoveSpeed(), 1e-4f);
        assertEquals(18.0f, legacy.getContactDamage(), 1e-4f);
    }

    @Test
    @DisplayName("RangedEnemy default stats and legacy constructor overload")
    public void testRangedEnemyDefaultStatsAndLegacyOverload() {
        RangedEnemy ranged = new RangedEnemy(new Vector2D(100, 100));
        assertEquals(EnemyType.RANGED, ranged.getType());
        assertEquals(20.0f, ranged.getHealth(), 1e-4f);
        assertEquals(110.0f, ranged.getBaseMoveSpeed(), 1e-4f);
        assertEquals(10.0f, ranged.getContactDamage(), 1e-4f);
        assertEquals(15.0f, ranged.getRadius(), 1e-4f);
        assertEquals(240.0f, ranged.getPreferredDistance(), 1e-4f);
        assertEquals(35.0f, ranged.getDistanceTolerance(), 1e-4f);
        assertEquals(1.8f, ranged.getRangedAttackCooldown(), 1e-4f);
        assertEquals(15, ranged.getExpReward());

        RangedEnemy legacy = RangedEnemy.createLegacyReference(new Vector2D(100, 100));
        assertEquals(45.0f, legacy.getHealth(), 1e-4f);
        assertEquals(120.0f, legacy.getBaseMoveSpeed(), 1e-4f);
        assertEquals(8.0f, legacy.getContactDamage(), 1e-4f);
    }

    @Test
    @DisplayName("TankEnemy default stats and durability ratio")
    public void testTankEnemyDefaultStatsAndDurabilityRatio() {
        TankEnemy tank = new TankEnemy(new Vector2D(100, 100));
        assertEquals(EnemyType.TANK, tank.getType());
        assertEquals(120.0f, tank.getHealth(), 1e-4f);
        assertEquals(80.0f, tank.getBaseMoveSpeed(), 1e-4f);
        assertEquals(25.0f, tank.getContactDamage(), 1e-4f);
        assertEquals(25.0f, tank.getArmor(), 1e-4f);
        assertEquals(26.0f, tank.getRadius(), 1e-4f);
        assertEquals(0.5f, tank.getKnockbackResistance(), 1e-4f);
        assertEquals(30, tank.getExpReward());

        TankEnemy legacy = TankEnemy.createLegacyReference(new Vector2D(100, 100));
        assertEquals(200.0f, legacy.getHealth(), 1e-4f);
        assertEquals(65.0f, legacy.getBaseMoveSpeed(), 1e-4f);
        assertEquals(32.0f, legacy.getContactDamage(), 1e-4f);

        // Durability assertions: Tank HP > Melee HP, Tank Speed < Melee Speed
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(100, 100));
        assertTrue(tank.getMaxHealth() > melee.getMaxHealth());
        assertTrue(tank.getBaseMoveSpeed() < melee.getBaseMoveSpeed());
    }

    // =========================================================================
    // Tier 1 & 2: Contact Attack, Cooldowns & EXP Lifecycle
    // =========================================================================

    @Test
    @DisplayName("Contact damage application and attack cooldown enforcement")
    public void testContactDamageApplicationAndCooldown() {
        Player player = new Player(new Vector2D(100, 100), 200.0f, 0.0f, 200.0f, 45.0f);
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(115, 100)); // Within r1+r2 (20+16=36)

        assertTrue(melee.canAttack());
        boolean hit = melee.tryAttack(player);
        assertTrue(hit);
        assertEquals(185.0f, player.getHealth(), 1e-4f); // 200 - 15 = 185
        assertFalse(melee.canAttack());
        assertEquals(0.8f, melee.getAttackTimer(), 1e-4f);

        // Immediate subsequent hit within cooldown must fail
        boolean hitAgain = melee.tryAttack(player);
        assertFalse(hitAgain);
        assertEquals(185.0f, player.getHealth(), 1e-4f);

        // Advance time past attackCooldown
        melee.update(0.85f);
        assertTrue(melee.canAttack());

        // Now next hit succeeds
        boolean hit2 = melee.tryAttack(player);
        assertTrue(hit2);
        assertEquals(170.0f, player.getHealth(), 1e-4f);
    }

    @Test
    @DisplayName("Experience reward claiming is strictly idempotent")
    public void testExpRewardClaimingIdempotence() {
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(0, 0));
        assertFalse(melee.isRewardClaimed());

        int expFirst = melee.claimReward();
        assertEquals(10, expFirst);
        assertTrue(melee.isRewardClaimed());

        // Subsequent claims must return 0
        int expSecond = melee.claimReward();
        assertEquals(0, expSecond);
    }

    @Test
    @DisplayName("Overlapping positions do not produce NaN or Infinity in AI updates")
    public void testOverlappingPositionsNoNanOrInfinity() {
        Vector2D playerPos = new Vector2D(350, 350);
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(350, 350));
        RangedEnemy ranged = new RangedEnemy(new Vector2D(350, 350));

        assertDoesNotThrow(() -> melee.updateAI(0.016f, playerPos));
        assertFalse(Float.isNaN(melee.getX()));
        assertFalse(Float.isNaN(melee.getY()));
        assertFalse(Float.isInfinite(melee.getX()));

        assertDoesNotThrow(() -> ranged.updateAI(0.016f, playerPos));
        assertFalse(Float.isNaN(ranged.getX()));
        assertFalse(Float.isNaN(ranged.getY()));
    }

    @Test
    @DisplayName("Arena boundary clamping keeps enemy fully within world rectangle")
    public void testArenaBoundaryClamping() {
        TankEnemy tank = new TankEnemy(new Vector2D(-100, -100));
        Rectangle bounds = new Rectangle(0, 0, 4096, 4096);

        tank.constrainToBounds(bounds);
        // r = 26 -> minX = bounds.x + r = 26
        assertEquals(26.0f, tank.getX(), 1e-4f);
        assertEquals(26.0f, tank.getY(), 1e-4f);

        tank.setPosition(5000, 5000);
        tank.constrainToBounds(bounds);
        assertEquals(4096 - 26.0f, tank.getX(), 1e-4f);
        assertEquals(4096 - 26.0f, tank.getY(), 1e-4f);
    }

    @Test
    @DisplayName("Dead enemy cannot attack or move")
    public void testDeadEnemyCannotAttackOrAct() {
        Player player = new Player(new Vector2D(100, 100));
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(105, 100));

        melee.takeDamage(9999.0f);
        assertFalse(melee.isAlive());
        assertFalse(melee.canAttack());
        assertFalse(melee.tryAttack(player));

        Vector2D deadPos = melee.getPosition().cpy();
        melee.updateAI(0.05f, player.getPosition());
        assertEquals(deadPos.x, melee.getX(), 1e-4f);
        assertEquals(deadPos.y, melee.getY(), 1e-4f);
    }

    // =========================================================================
    // Tier 3: AI Behaviors (LOS, A* Navigation, Kiting & Strafing)
    // =========================================================================

    @Test
    @DisplayName("MeleeEnemy direct Line-Of-Sight charging within 650px")
    public void testMeleeEnemyDirectLosCharge() {
        Vector2D playerPos = new Vector2D(400, 300);
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(200, 300));

        float initialDist = melee.getPosition().dist(playerPos);
        melee.updateAI(0.1f, playerPos);

        float newDist = melee.getPosition().dist(playerPos);
        assertTrue(newDist < initialDist);
    }

    @Test
    @DisplayName("MeleeEnemy navigates obstacles using AStarPathfinder")
    public void testMeleeEnemyAStarRiverObstacleNavigation() {
        TileMap tileMap = new TileMap(42);
        AStarPathfinder pathfinder = new AStarPathfinder(tileMap);

        // Player and enemy on opposite sides of river
        Vector2D enemyPos = new Vector2D(1000, 1500);
        Vector2D playerPos = new Vector2D(1700, 1500);

        MeleeEnemy melee = new MeleeEnemy(enemyPos);
        melee.updateAI(0.1f, playerPos, tileMap, pathfinder);

        // Enemy must remain in a traversable tile
        assertTrue(tileMap.isTraversable(melee.getX(), melee.getY(), melee.getRadius()));
    }

    @Test
    @DisplayName("RangedEnemy kiting behavior: retreat when too close, advance when too far, strafe in sweet spot")
    public void testRangedEnemyKitingSweetSpot() {
        Vector2D playerPos = new Vector2D(100, 200);

        // Case A: Too far (dist = 400 > preferred 240 + tolerance 35 = 275)
        RangedEnemy rangedFar = new RangedEnemy(new Vector2D(500, 200));
        float distBeforeFar = rangedFar.getPosition().dist(playerPos);
        rangedFar.updateAI(0.1f, playerPos);
        float distAfterFar = rangedFar.getPosition().dist(playerPos);
        assertTrue(distAfterFar < distBeforeFar, "Ranged enemy should advance towards player when too far");

        // Case B: Too close (dist = 100 < preferred 240 - tolerance 35 = 205)
        RangedEnemy rangedClose = new RangedEnemy(new Vector2D(200, 200));
        float distBeforeClose = rangedClose.getPosition().dist(playerPos);
        rangedClose.updateAI(0.1f, playerPos);
        float distAfterClose = rangedClose.getPosition().dist(playerPos);
        assertTrue(distAfterClose > distBeforeClose, "Ranged enemy should kite away from player when too close");

        // Case C: Sweet spot (dist = 240 in [205, 275])
        RangedEnemy rangedSweet = new RangedEnemy(new Vector2D(340, 200));
        rangedSweet.updateAI(0.1f, playerPos);
        // Strafing produces lateral displacement on Y axis
        assertNotEquals(200.0f, rangedSweet.getY(), 1e-2f, "Ranged enemy should strafe laterally in sweet spot");
    }

    @Test
    @DisplayName("RangedEnemy attack range and shooting cooldown")
    public void testRangedEnemyShootingCooldown() {
        RangedEnemy ranged = new RangedEnemy(new Vector2D(300, 200));
        Vector2D playerPos = new Vector2D(100, 200);

        // Dist = 200 <= shootRange 350
        ranged.updateAI(0.016f, playerPos);
        assertTrue(ranged.isInAttackRange());

        // Initial delay before first shot is 1.0s
        assertFalse(ranged.canShoot());

        ranged.update(1.05f);
        assertTrue(ranged.canShoot());

        ranged.resetRangedAttackCooldown();
        assertFalse(ranged.canShoot());
        assertEquals(1.8f, ranged.getRangedAttackTimer(), 1e-4f);
    }
}
