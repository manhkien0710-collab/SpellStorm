package com.spellstorm.e2e;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.*;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.*;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.systems.*;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Tier 3: Cross-Feature Pairwise Integration Test Suite (34 Tests)")
public class Tier3CrossFeaturePairwiseTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // -------------------------------------------------------------------------
    // Test 1: Features 5 & 6 (Camera Zoom & Screen Unprojection Invariance)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 01: Camera Zoom & Screen Unprojection Invariance (Features 5 & 6)")
    void testPair01_CameraZoom_ScreenUnprojection_Invariance() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.snapTo(2048.0f, 2048.0f);

        float screenX = 700.0f;
        float screenY = 400.0f;
        float expectedAngle = (float) Math.atan2(400.0f - 360.0f, 700.0f - 640.0f);

        for (float zoom : new float[]{0.75f, 1.0f, 1.35f, 1.8f, 2.25f}) {
            camera.setZoom(zoom);
            Vector2D worldPos = camera.screenToWorld(screenX, screenY);
            float angle = (float) Math.atan2(worldPos.y - 2048.0f, worldPos.x - 2048.0f);
            assertEquals(expectedAngle, angle, 1e-4f, "Aim angle must be zoom-invariant at " + zoom);
        }
    }

    // -------------------------------------------------------------------------
    // Test 2: Features 5 & 8 (Camera Frustum & World Boundary Clamping)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 02: Camera Frustum & World Boundary Clamping (Features 5 & 8)")
    void testPair02_CameraFrustum_WorldBoundary_Clamping() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(0.0f, 0.0f, 4096.0f, 4096.0f);

        // Pan to top-left corner
        camera.snapTo(-500.0f, -500.0f);
        assertTrue(camera.getVisibleLeft() >= 0.0f, "Visible left bound must clamp >= 0");
        assertTrue(camera.getVisibleTop() >= 0.0f, "Visible top bound must clamp >= 0");

        // Pan to bottom-right corner
        camera.snapTo(5000.0f, 5000.0f);
        assertTrue(camera.getVisibleBounds().x + camera.getVisibleBounds().width <= 4096.0f, "Visible right bound must clamp <= 4096");
        assertTrue(camera.getVisibleBounds().y + camera.getVisibleBounds().height <= 4096.0f, "Visible bottom bound must clamp <= 4096");
    }

    // -------------------------------------------------------------------------
    // Test 3: Features 7 & 17 (Player Movement & TileMap Sliding Collision)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 03: Player Movement & TileMap Sliding Collision (Features 7 & 17)")
    void testPair03_PlayerMovement_TileMapSlidingCollision() {
        TileMap map = new TileMap(42L);
        map.clearObstacles();
        // Add solid rock at (2000, 2000) with collision radius 14, collision center (2000, 1994)
        map.addDecoration(DecoType.ROCK_LARGE, 2000.0f, 2000.0f, true);

        Player player = new Player(new Vector2D(2030.0f, 1994.0f));
        float r = player.getRadius(); // 15.0f

        Vector2D oldPos = player.getPosition().cpy();
        Vector2D desiredPos = new Vector2D(2010.0f, 2020.0f); // moves left into rock and down

        Vector2D resolved = map.resolveCollision(oldPos, desiredPos, r);
        assertEquals(oldPos.x, resolved.x, 1e-3f, "X movement into obstacle must slide/hold");
        assertEquals(desiredPos.y, resolved.y, 1e-3f, "Y unobstructed movement must advance cleanly");
    }

    // -------------------------------------------------------------------------
    // Test 4: Features 7 & 8 (Player Movement & River Shoreline Collision)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 04: Player Movement & River Shoreline Collision (Features 7 & 8)")
    void testPair04_PlayerMovement_RiverShorelineCollision() {
        TileMap map = new TileMap(42L);
        // Find water boundary and traversable bank west of river
        int waterTx = -1;
        int row = 46;
        Vector2D bankPos = null;
        for (int y = 30; y < 60; y++) {
            for (int x = 5; x < 120; x++) {
                if (map.tileAt(x, y) == TileType.WATER) {
                    Vector2D candidate = map.tileToWorld(x - 2, y);
                    if (map.isTraversable(candidate, 15.0f)) {
                        waterTx = x;
                        row = y;
                        bankPos = candidate;
                        break;
                    }
                }
            }
            if (bankPos != null) break;
        }
        assertTrue(waterTx > 5);
        assertNotNull(bankPos);

        Player player = new Player(bankPos);
        assertTrue(map.isTraversable(player.getPosition(), player.getRadius()));

        // Movement into water is blocked
        Vector2D intoWater = map.tileToWorld(waterTx, row);
        assertFalse(map.isTraversable(intoWater, player.getRadius()));

        Vector2D resolved = map.resolveCollision(bankPos, intoWater, player.getRadius());
        assertTrue(map.isTraversable(resolved, player.getRadius()), "Resolved position must be traversable");
    }

    // -------------------------------------------------------------------------
    // Test 5: Features 17 & 18 (Manual Shooting & Cast Cooldown Limit)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 05: Manual Shooting & Cast Cooldown Limit (Features 17 & 18)")
    void testPair05_ManualShooting_CastCooldownLimit() {
        Player player = new Player(new Vector2D(500f, 500f));
        assertEquals(ShootingMode.MANUAL, player.getShootingMode());
        assertTrue(player.canCast());

        // Cast spell
        player.triggerCast();
        assertFalse(player.canCast(), "Player cannot cast immediately after firing");

        // Advance cooldown partially (0.10s < 0.28s)
        player.update(0.10f);
        assertFalse(player.canCast());

        // Advance cooldown past 0.28s
        player.update(0.20f);
        assertTrue(player.canCast(), "Player can cast once cooldown expires");
    }

    // -------------------------------------------------------------------------
    // Test 6: Features 18 & 19 (Auto-Shooting & Dynamic Aim Targeting)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 06: Auto-Shooting & Dynamic Aim Targeting (Features 18 & 19)")
    void testPair06_AutoShooting_DynamicAimTargeting() {
        GameSession session = new GameSession();
        Player player = session.getPlayer();
        player.setPosition(new Vector2D(2048.0f, 2048.0f));

        // Add near enemy (dist 100) and far enemy (dist 400)
        MeleeEnemy nearEnemy = new MeleeEnemy(new Vector2D(2148.0f, 2048.0f));
        MeleeEnemy farEnemy = new MeleeEnemy(new Vector2D(2448.0f, 2048.0f));
        session.addEnemy(nearEnemy);
        session.addEnemy(farEnemy);

        // Update in autoplay/auto mode
        session.update(0.016f, player.getPosition(), player.getPosition(), true);
        assertEquals(1, session.getCombatSystem().getProjectileCount());

        Projectile p = session.getCombatSystem().getProjectiles().get(0);
        // Projectile direction should head east (towards nearEnemy at angle 0)
        assertEquals(1.0f, p.getVelocity().nor().x, 0.05f);
        assertEquals(0.0f, p.getVelocity().nor().y, 0.05f);
    }

    // -------------------------------------------------------------------------
    // Test 7: Features 18 & 19 (Auto-Shooting Equidistant Entity Tie Breaking)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 07: Auto-Shooting & Equidistant Entity Tie-Breaking (Features 18 & 19)")
    void testPair07_AutoShooting_EquidistantEntityTieBreaking() {
        GameSession session = new GameSession();
        Player player = session.getPlayer();
        player.setPosition(new Vector2D(2048.0f, 2048.0f));

        // Equidistant enemies at North (+100 Y) and South (-100 Y)
        MeleeEnemy northEnemy = new MeleeEnemy(new Vector2D(2048.0f, 2148.0f));
        MeleeEnemy southEnemy = new MeleeEnemy(new Vector2D(2048.0f, 1948.0f));
        session.addEnemy(northEnemy);
        session.addEnemy(southEnemy);

        session.update(0.016f, player.getPosition(), player.getPosition(), true);
        assertEquals(1, session.getCombatSystem().getProjectileCount(), "Should fire at first targeted enemy");
    }

    // -------------------------------------------------------------------------
    // Test 8: Features 10 & 19 (Enemy Steering & Rock Obstacle Avoidance)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 08: Enemy Steering & Rock Obstacle Avoidance (Features 10 & 19)")
    void testPair08_EnemySteering_RockObstacleAvoidance() {
        TileMap map = new TileMap(42L);
        map.clearObstacles();
        map.addDecoration(DecoType.ROCK_LARGE, 1000.0f, 1000.0f, true);

        MeleeEnemy enemy = new MeleeEnemy(new Vector2D(970.0f, 1000.0f));
        Vector2D targetPlayer = new Vector2D(1030.0f, 1000.0f);
        AStarPathfinder pf = new AStarPathfinder(map);

        assertFalse(pf.hasLineOfSight(enemy.getPosition(), targetPlayer, enemy.getRadius()));
        List<Vector2D> path = pf.findPath(enemy.getPosition(), targetPlayer, enemy.getRadius());
        assertFalse(path.isEmpty(), "Enemy must find steering path around rock");
    }

    // -------------------------------------------------------------------------
    // Test 9: Features 9 & 12 (Enemy Steering & Bridge Traversal)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 09: Enemy Steering & Bridge Traversal (Features 9 & 12)")
    void testPair09_EnemySteering_BridgeTraversal() {
        TileMap map = new TileMap(42L);
        AStarPathfinder pf = new AStarPathfinder(map);

        Vector2D westApproach = new Vector2D(1000.0f, 118 * 32.0f + 16.0f);
        Vector2D eastApproach = new Vector2D(2200.0f, 118 * 32.0f + 16.0f);

        List<Vector2D> path = pf.findPath(westApproach, eastApproach, 16.0f);
        assertFalse(path.isEmpty());
        // Verify path stays out of water
        for (Vector2D wp : path) {
            int[] t = map.worldToTile(wp);
            assertFalse(map.isWater(t[0], t[1]));
        }
    }

    // -------------------------------------------------------------------------
    // Test 10: Features 7 & 19 (Ranged Enemy Kiting & Wall Sliding)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 10: Ranged Enemy Kiting & Wall Sliding (Features 7 & 19)")
    void testPair10_RangedEnemy_KitingAndWallSliding() {
        TileMap map = new TileMap(42L);
        AStarPathfinder pf = new AStarPathfinder(map);
        RangedEnemy ranged = new RangedEnemy(new Vector2D(100.0f, 2048.0f)); // Near west arena wall
        Vector2D closePlayer = new Vector2D(180.0f, 2048.0f); // < 205px retreat threshold

        ranged.updateAI(0.016f, closePlayer, map, pf);
        ranged.update(0.016f);

        assertTrue(ranged.getPosition().x >= 15.0f, "Kiting enemy must not penetrate arena boundary");
    }

    // -------------------------------------------------------------------------
    // Test 11: Features 16 & 19 (Tank Enemy Knockback Decay & Armor)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 11: Tank Enemy Knockback Decay & Armor (Features 16 & 19)")
    void testPair11_TankEnemy_KnockbackDecayAndArmor() {
        TankEnemy tank = new TankEnemy(new Vector2D(1000f, 1000f));
        assertEquals(TankEnemy.DEFAULT_ARMOR, tank.getArmor(), 1e-4f);

        // Armor mitigation: 100 * 100 / (100 + 25) = 80.0 damage
        float dealt = tank.takeDamage(100.0f);
        assertEquals(80.0f, dealt, 0.5f);

        // Knockback application (0.5 resistance) and exponential decay
        tank.applyKnockback(new Vector2D(200.0f, 0.0f));
        assertEquals(100.0f, tank.getKnockback().len(), 1e-4f, "Tank 50% knockback resistance cuts impulse in half");

        tank.update(0.5f);
        // Exponential decay e^(-7 * 0.5) approx 0.03 -> ~3.0
        assertTrue(tank.getKnockback().len() < 20.0f, "Knockback must decay exponentially");
    }

    // -------------------------------------------------------------------------
    // Test 12: Features 21 & 22 (Boss Volley & Player Hitbox Collision)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 12: Boss Volley & Player Hitbox Collision (Features 21 & 22)")
    void testPair12_BossVolley_PlayerHitboxCollision() {
        BossEnemy boss = new BossEnemy(new Vector2D(2048f, 1900f));
        Player player = new Player(new Vector2D(2048f, 2048f));

        List<Projectile> volley = boss.fireVolley(player.getPosition());
        assertEquals(3, volley.size());

        // Center missile heads directly south towards player
        Projectile centerMissile = volley.get(1);
        assertEquals(0.0f, centerMissile.getDirection().x, 0.05f);
        assertEquals(1.0f, centerMissile.getDirection().y, 0.05f);
    }

    // -------------------------------------------------------------------------
    // Test 13: Features 21 & 23 (Damage Mitigation Formula Across Armor Levels)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 13: Damage Mitigation Formula Across Armor Levels (Features 21 & 23)")
    void testPair13_DamageMitigationFormula_ArmorLevels() {
        float raw = 100.0f;
        assertEquals(100.0f, DamageMitigation.calculateDamage(raw, 0.0f), 0.01f);
        assertEquals(86.96f, DamageMitigation.calculateDamage(raw, 15.0f), 0.05f);
        assertEquals(76.92f, DamageMitigation.calculateDamage(raw, 30.0f), 0.05f);
        assertEquals(68.97f, DamageMitigation.calculateDamage(raw, 45.0f), 0.05f);
        assertEquals(50.00f, DamageMitigation.calculateDamage(raw, 100.0f), 0.01f);
    }

    // -------------------------------------------------------------------------
    // Test 14: Features 23 & 24 (Burn DoT Armor-Piercing Behavior)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 14: Burn DoT Armor-Piercing Behavior (Features 23 & 24)")
    void testPair14_BurnDoT_ArmorPiercingBehavior() {
        TankEnemy tank = new TankEnemy(new Vector2D(1000f, 1000f));
        float hpBefore = tank.getHealth();

        // Burn deals true damage ignoring armor
        tank.addStatus(new BurnStatus(10.0f, 2.0f));
        tank.update(0.55f); // 1 tick at 0.5s dealing 10 * 0.5 = 5 damage

        assertEquals(hpBefore - 5.0f, tank.getHealth(), 0.5f);
    }

    // -------------------------------------------------------------------------
    // Test 15: Features 19 & 24 (Slow Status Speed Reduction & Clamping)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 15: Slow Status Speed Reduction & Clamping (Features 19 & 24)")
    void testPair15_SlowStatus_SpeedReductionAndClamping() {
        MeleeEnemy enemy = new MeleeEnemy(new Vector2D(1000f, 1000f));
        float baseSpeed = enemy.getCurrentMoveSpeed(); // 80.0f

        enemy.addStatus(new SlowStatus(0.40f, 2.0f));
        assertEquals(baseSpeed * 0.60f, enemy.getCurrentMoveSpeed(), 0.1f);

        // Clamping to max 90% slow
        SlowStatus maxSlow = new SlowStatus(0.99f, 2.0f);
        assertEquals(0.9f, maxSlow.getSlowFraction(), 1e-4f, "Slow must clamp to at most 90% speed reduction");
    }

    // -------------------------------------------------------------------------
    // Test 16: Features 23 & 26 (Projectile Pierce Damage Falloff Sequence)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 16: Projectile Pierce Damage Falloff Sequence (Features 23 & 26)")
    void testPair16_ProjectilePierce_DamageFalloffSequence() {
        Projectile p = new Projectile(
                new Vector2D(0, 0), new Vector2D(1, 0), 300f, 10f, 100f, 2.0f, 2,
                ProjectileFaction.PLAYER, Color.WHITE, Color.GRAY, null
        );

        assertEquals(100.0f, p.getDamage(), 1e-4f);
        assertEquals(2, p.getPierceRemaining());

        // Hit 1: 100 dmg -> pierce = 1, dmg = 80
        p.recordHit(1L);
        p.decrementPierce();
        assertEquals(1, p.getPierceRemaining());
        assertEquals(80.0f, p.getDamage(), 1e-4f);
        assertTrue(p.isAlive());

        // Hit 2: 80 dmg -> pierce = 0, dmg = 64
        p.recordHit(2L);
        p.decrementPierce();
        assertEquals(0, p.getPierceRemaining());
        assertEquals(64.0f, p.getDamage(), 1e-4f);
        assertTrue(p.isAlive());

        // Hit 3: final hit -> destroyed
        p.recordHit(3L);
        p.decrementPierce();
        assertFalse(p.isAlive());
    }

    // -------------------------------------------------------------------------
    // Test 17: Features 25 & 26 (Spell Recipe Magma Core Definition)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 17: Spell Recipe Magma Core Definition (Features 25 & 26)")
    void testPair17_SpellRecipe_MagmaCoreExplosion() {
        SpellRegistry reg = SpellRegistry.createDefaultRegistry();
        SpellDefinition magma = reg.combination(new ElementSet(Element.FIRE, Element.EARTH));
        assertNotNull(magma);
        assertTrue(magma.getName().contains("Magma Core"));
        assertTrue(magma.getProfile().getDamageMultiplier() >= 1.5f);
        assertTrue(magma.getProfile().getRadius() >= 12.0f);
    }

    // -------------------------------------------------------------------------
    // Test 18: Features 24 & 25 (Spell Recipe Frost Tempest Status Effects)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 18: Spell Recipe Frost Tempest Status Effects (Features 24 & 25)")
    void testPair18_SpellRecipe_FrostTempestStatusEffects() {
        SpellRegistry reg = SpellRegistry.createDefaultRegistry();
        SpellDefinition frostTempest = reg.combination(new ElementSet(Element.ICE, Element.LIGHTNING));
        assertNotNull(frostTempest);
        assertTrue(frostTempest.getName().contains("Frost Tempest"));
        assertFalse(frostTempest.getEffects().isEmpty());
    }

    // -------------------------------------------------------------------------
    // Test 19: Features 19 & 20 (Spawner Timeline 0-30s Melee & Ranged Only)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 19: Spawner Timeline 0-30s Melee & Ranged Only (Features 19 & 20)")
    void testPair19_SpawnerTimeline_0to30s_MeleeOnly() {
        EnemySpawner spawner = new EnemySpawner(new TileMap(42L), new Random(12345));
        for (int i = 0; i < 50; i++) {
            Enemy e = spawner.spawnEnemy(new Vector2D(2048, 2048));
            assertFalse(e.isBoss(), "Boss must never spawn in first 30s");
            assertNotEquals(EnemyType.TANK, e.getType(), "Tank must not spawn in first 25s");
        }
    }

    // -------------------------------------------------------------------------
    // Test 20: Features 19 & 20 (Spawner Timeline 30-60s Tank Inclusion)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 20: Spawner Timeline 30-60s Tank Inclusion (Features 19 & 20)")
    void testPair20_SpawnerTimeline_30to60s_RangedInclusion() {
        EnemySpawner spawner = new EnemySpawner(new TileMap(42L), new Random(12345));
        spawner.update(45.0f, new Vector2D(2048, 2048), null); // Advance to 45s

        boolean observedTank = false;
        for (int i = 0; i < 50; i++) {
            Enemy e = spawner.spawnEnemy(new Vector2D(2048, 2048));
            if (e.getType() == EnemyType.TANK) {
                observedTank = true;
                break;
            }
        }
        assertTrue(observedTank, "Tank enemy must be eligible to spawn at 45s timeline");
    }

    // -------------------------------------------------------------------------
    // Test 21: Features 20 & 21 (Spawner Timeline 60-120s Boss Milestone)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 21: Spawner Timeline 60-120s Boss Milestone (Features 20 & 21)")
    void testPair21_SpawnerTimeline_60to120s_BossMilestone() {
        EnemySpawner spawner = new EnemySpawner(new TileMap(42L), new Random(12345));
        List<Enemy> active = new java.util.ArrayList<>();

        // Advance past 60s
        spawner.update(60.1f, new Vector2D(2048, 2048), active);
        boolean bossSpawned = active.stream().anyMatch(Enemy::isBoss);
        assertTrue(bossSpawned, "Boss must spawn at 60s milestone");
    }

    // -------------------------------------------------------------------------
    // Test 22: Features 19 & 20 (Spawner Cap Enforcement Suppression)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 22: Spawner Cap Enforcement Suppression (Features 19 & 20)")
    void testPair22_SpawnerCap_EnforcementSuppression() {
        EnemySpawner spawner = new EnemySpawner(new TileMap(42L), new Random(12345));
        List<Enemy> active = new java.util.ArrayList<>();
        int cap = spawner.getMaxEnemies();

        for (int i = 0; i < cap; i++) {
            active.add(new MeleeEnemy(new Vector2D(1000, 1000)));
        }

        // Trigger spawn timer
        spawner.update(5.0f, new Vector2D(2048, 2048), active);
        assertEquals(cap, active.size(), "Spawner must strictly suppress spawning when at enemy cap");
    }

    // -------------------------------------------------------------------------
    // Test 23: Features 19 & 27 (Melee Defeat Cyan Gem Drop)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 23: Melee Defeat Cyan Gem Drop (Features 19 & 27)")
    void testPair23_MeleeDefeat_CyanGemDrop() {
        MeleeEnemy melee = new MeleeEnemy(new Vector2D(0, 0));
        assertEquals(10, melee.getExpReward());

        ExperiencePickup gem = new ExperiencePickup(melee.getPosition(), melee.getExpReward());
        assertEquals(AssetRegistry.TextureId.GEM_CYAN, gem.getTextureId());
    }

    // -------------------------------------------------------------------------
    // Test 24: Features 19 & 27 (Ranged and Tank Defeat Gem Drops)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 24: Ranged and Tank Defeat Gem Drops (Features 19 & 27)")
    void testPair24_TankDefeat_EmeraldGemDrop() {
        RangedEnemy ranged = new RangedEnemy(new Vector2D(0, 0));
        assertEquals(15, ranged.getExpReward());
        ExperiencePickup emeraldGem = new ExperiencePickup(ranged.getPosition(), ranged.getExpReward());
        assertEquals(AssetRegistry.TextureId.GEM_EMERALD, emeraldGem.getTextureId());

        TankEnemy tank = new TankEnemy(new Vector2D(0, 0));
        assertEquals(30, tank.getExpReward());
        ExperiencePickup goldGem = new ExperiencePickup(tank.getPosition(), tank.getExpReward());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, goldGem.getTextureId());
    }

    // -------------------------------------------------------------------------
    // Test 25: Features 21 & 27 (Boss Defeat High-Tier Reward Claim)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 25: Boss Defeat High-Tier Reward Claim (Features 21 & 27)")
    void testPair25_BossDefeat_HighTierRewardClaim() {
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
        assertEquals(250, boss.getExpReward());

        ExperiencePickup gem = new ExperiencePickup(boss.getPosition(), boss.getExpReward());
        assertEquals(AssetRegistry.TextureId.GEM_GOLD, gem.getTextureId());
    }

    // -------------------------------------------------------------------------
    // Test 26: Features 17 & 27 (Exp Gem Magnet Acceleration to Player)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 26: Exp Gem Magnet Acceleration to Player (Features 17 & 27)")
    void testPair26_ExpGem_MagnetAccelerationToPlayer() {
        ExperienceSystem expSystem = new ExperienceSystem();
        Player player = new Player(new Vector2D(1000f, 1000f));
        expSystem.spawnPickup(1110f, 1000f, 10); // dist = 110 < 126 magnet radius, > 45 collect radius

        expSystem.update(0.016f, player, new PlayerProgression(1));
        assertFalse(expSystem.getPickups().isEmpty(), "Gem must still exist before collection");
        ExperiencePickup gem = expSystem.getPickups().get(0);
        assertTrue(gem.getPosition().x < 1110f, "Gem must be drawn towards player position");
    }

    // -------------------------------------------------------------------------
    // Test 27: Features 28 & 30 (Level Curve Multi-Level Queue & Overflow)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 27: Level Curve Multi-Level Queue & Overflow (Features 28 & 30)")
    void testPair27_LevelCurve_MultiLevelQueueAndOverflow() {
        PlayerProgression prog = new PlayerProgression(1);
        int levels = prog.addExp(145);
        assertEquals(3, levels);
        assertEquals(4, prog.getLevel());
        assertEquals(10, prog.getCurrentExp());
        assertEquals(3, prog.getPendingLevelUps());
    }

    // -------------------------------------------------------------------------
    // Test 28: Features 26 & 29 (Upgrade Perk Piercing Lance Rank Cap)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 28: Upgrade Perk Piercing Lance Rank Cap (Features 26 & 29)")
    void testPair28_UpgradePerk_PiercingLanceRankCap() {
        UpgradeManager upgrades = new UpgradeManager();
        Player player = new Player();

        assertTrue(upgrades.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(1, player.getBonusPierce());

        assertTrue(upgrades.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, player.getBonusPierce());

        // Max rank for Piercing is 2
        assertFalse(upgrades.applyUpgrade(UpgradeType.PIERCING, player), "Piercing Lance cannot exceed rank 2");
        assertEquals(2, player.getBonusPierce());
    }

    // -------------------------------------------------------------------------
    // Test 29: Features 17 & 29 (Upgrade Perk Vitality Surge Heal & Cap)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 29: Upgrade Perk Vitality Surge Heal & Cap (Features 17 & 29)")
    void testPair29_UpgradePerk_VitalitySurgeHealAndCap() {
        UpgradeManager upgrades = new UpgradeManager();
        Player player = new Player(new Vector2D(0, 0), 250f, 30f, 230f, 45f);
        player.takeDamage(100f);

        float hpBefore = player.getHealth();
        assertTrue(upgrades.applyUpgrade(UpgradeType.MAX_HEALTH, player));
        assertEquals(290f, player.getMaxHealth());
        assertEquals(hpBefore + 40f, player.getHealth());
    }

    // -------------------------------------------------------------------------
    // Test 30: Features 29 & 31 (Power-Up Arcane Overdrive Perk Stacking)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 30: Power-Up Arcane Overdrive Perk Stacking (Features 29 & 31)")
    void testPair30_PowerUp_ArcaneOverdrive_PerkStacking() {
        UpgradeManager upgrades = new UpgradeManager();
        Player player = new Player();
        PowerUpManager powerUps = new PowerUpManager();

        upgrades.applyUpgrade(UpgradeType.CAST_COOLDOWN, player); // * 0.88
        powerUps.getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE); // * (1.0 / 1.8)

        float combinedCooldown = player.getBaseCastCooldown() * powerUps.getCastCooldownMultiplier();
        float expected = (0.28f * 0.88f) / 1.8f;
        assertEquals(expected, combinedCooldown, 1e-4f);
    }

    // -------------------------------------------------------------------------
    // Test 31: Features 26 & 31 (Power-Up Piercing Surge Falloff Calculation)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 31: Power-Up Piercing Surge Falloff Calculation (Features 26 & 31)")
    void testPair31_PowerUp_PiercingSurge_FalloffCalculation() {
        PowerUpManager powerUps = new PowerUpManager();
        powerUps.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);
        assertEquals(2, powerUps.getBonusPierce());

        Projectile p = new Projectile(
                new Vector2D(0, 0), new Vector2D(1, 0), 300f, 10f, 50f, 2.0f,
                powerUps.getBonusPierce(), ProjectileFaction.PLAYER, Color.CYAN, Color.WHITE, null
        );
        assertEquals(2, p.getPierceRemaining());
    }

    // -------------------------------------------------------------------------
    // Test 32: Features 25 & 31 (Power-Up Elemental Burst Recipe Buffs)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 32: Power-Up Elemental Burst Recipe Buffs (Features 25 & 31)")
    void testPair32_PowerUp_ElementalBurst_RecipeBuffs() {
        PowerUpManager powerUps = new PowerUpManager();
        powerUps.getActiveAbility().activate(PowerUpType.ELEMENTAL_BURST);
        assertTrue(powerUps.isElementalBurstActive());
    }

    // -------------------------------------------------------------------------
    // Test 33: Features 30 & 32 (Level-Up Modal Power-Up Timer Suspension)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 33: Level-Up Modal Power-Up Timer Suspension (Features 30 & 32)")
    void testPair33_LevelUpModal_PowerUpTimerSuspension() {
        GameSession session = new GameSession();
        PowerUpManager powerUps = session.getPowerUpManager();
        powerUps.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);

        // Open level up modal
        session.getProgression().addExp(50);
        session.update(0.016f);
        assertTrue(session.isLevelUpOpen());

        // Update while modal open
        session.update(2.0f);
        assertEquals(5.0f, powerUps.getActiveAbility().getRemainingDuration(), 0.05f);
    }

    // -------------------------------------------------------------------------
    // Test 34: Features 33 & 34 (GameOver Overlay Restart Match Clean State)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Pair 34: GameOver Overlay Restart Match Clean State (Features 33 & 34)")
    void testPair34_GameOverOverlay_RestartMatchCleanState() {
        GameSession session = new GameSession();
        for (int i = 0; i < 50; i++) session.update(0.1f);
        session.getPlayer().takeDamage(1000f);
        session.update(0.016f);
        assertTrue(session.isGameOver());

        session.restartMatch();
        assertTrue(session.isPlaying());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
        assertEquals(250.0f, session.getPlayer().getHealth(), 1e-4f);
    }
}
