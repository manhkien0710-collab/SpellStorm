package com.spellstorm;

import com.spellstorm.combat.DamageInfo;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 5 Challenger Test Suite (Empirical Adversarial Stress Testing).
 *
 * Covers:
 * 1. Boss volley attack: multi-angle parametric sweep, exact [base - 24°, base, base + 24°] angles,
 *    dot products, angular deltas, projectile properties, spawn offsets, corner cases, and cooldown state machine.
 * 2. Spawner boss lifecycle: exact 60s initial spawn boundary, 90s recurrence, 10s retry delay while fighting,
 *    recurrence upon defeat, late-game health scaling exemption, enemy cap exemption, and reset idempotency.
 * 3. Milestone 8 Navigation: Colossus (radius 50) bridge clearance, continuous west-to-east traversal across
 *    all 5 river bridges, and river water avoidance detours.
 */
@DisplayName("Milestone 5 Boss Challenger: Empirical Stress Tests")
public class Milestone5BossChallengerTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Area 1: Boss Volley Attack Math, Projectile Geometry & Cooldown State Machine
    // =========================================================================

    @Nested
    @DisplayName("Challenge 1: Boss Volley Attack Math, Angles & Projectile Specs")
    class VolleyAttackMathAndSpecsTests {

        private static final float SPREAD_DEG = 24.0f;
        private static final float SPREAD_RAD = MathUtils.degToRad(SPREAD_DEG); // ~0.41887903 rad
        private static final float COS_24 = (float) Math.cos(SPREAD_RAD);       // ~0.91354546
        private static final float COS_48 = (float) Math.cos(2.0f * SPREAD_RAD);// ~0.6691306

        /**
         * Wraps an angle in radians to [-PI, PI].
         */
        private float wrapAngleRad(float angle) {
            while (angle > MathUtils.PI) angle -= MathUtils.TWO_PI;
            while (angle < -MathUtils.PI) angle += MathUtils.TWO_PI;
            return angle;
        }

        @ParameterizedTest(name = "Aiming angle {0} deg produces exact [base - 24°, base, base + 24°] spread")
        @ValueSource(floats = {0.0f, 22.5f, 45.0f, 67.5f, 90.0f, 120.0f, 137.5f, 180.0f,
                               210.0f, 225.0f, 270.0f, 300.0f, 315.0f, 337.5f, 359.0f, -45.0f})
        void testVolleyMultiAngleParametricSweep(float aimAngleDeg) {
            BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 500.0f));
            float aimAngleRad = MathUtils.degToRad(aimAngleDeg);
            Vector2D aimDir = Vector2D.fromAngle(aimAngleRad);
            Vector2D targetPos = new Vector2D(500.0f + aimDir.x * 300.0f, 500.0f + aimDir.y * 300.0f);

            List<Projectile> volley = boss.fireVolley(targetPos);
            assertEquals(3, volley.size(), "Boss volley must always produce exactly 3 projectiles");

            Projectile left = volley.get(0);
            Projectile mid = volley.get(1);
            Projectile right = volley.get(2);

            Vector2D dirL = left.getDirection();
            Vector2D dirM = mid.getDirection();
            Vector2D dirR = right.getDirection();

            // 1. Direction vectors must be unit length
            assertEquals(1.0f, dirL.len(), 1e-4f, "Left missile direction must be normalized");
            assertEquals(1.0f, dirM.len(), 1e-4f, "Middle missile direction must be normalized");
            assertEquals(1.0f, dirR.len(), 1e-4f, "Right missile direction must be normalized");

            // 2. Middle missile must aim precisely at base angle
            float deltaMidAim = Math.abs(wrapAngleRad(dirM.angleRad() - aimAngleRad));
            assertEquals(0.0f, deltaMidAim, 1e-3f,
                    "Middle projectile must align with aiming angle " + aimAngleDeg + " deg");

            // 3. Left missile must be base - 24 deg, Right missile must be base + 24 deg
            float deltaLeftExpected = Math.abs(wrapAngleRad(dirL.angleRad() - (aimAngleRad - SPREAD_RAD)));
            float deltaRightExpected = Math.abs(wrapAngleRad(dirR.angleRad() - (aimAngleRad + SPREAD_RAD)));

            assertEquals(0.0f, deltaLeftExpected, 1e-3f,
                    "Left projectile angle must be base - 24 deg for aim " + aimAngleDeg + " deg");
            assertEquals(0.0f, deltaRightExpected, 1e-3f,
                    "Right projectile angle must be base + 24 deg for aim " + aimAngleDeg + " deg");

            // 4. Dot products:
            // mid . left == cos(24°)
            // mid . right == cos(24°)
            // left . right == cos(48°)
            assertEquals(COS_24, dirM.dot(dirL), 1e-3f, "Dot(mid, left) must equal cos(24°)");
            assertEquals(COS_24, dirM.dot(dirR), 1e-3f, "Dot(mid, right) must equal cos(24°)");
            assertEquals(COS_48, dirL.dot(dirR), 1e-3f, "Dot(left, right) must equal cos(48°)");

            // 5. Angular delta between adjacent missiles must be exactly 24 degrees
            float deltaMidLeft = Math.abs(wrapAngleRad(dirM.angleRad() - dirL.angleRad()));
            float deltaRightMid = Math.abs(wrapAngleRad(dirR.angleRad() - dirM.angleRad()));
            assertEquals(SPREAD_RAD, deltaMidLeft, 1e-3f, "Delta(mid, left) must be 24 degrees");
            assertEquals(SPREAD_RAD, deltaRightMid, 1e-3f, "Delta(right, mid) must be 24 degrees");
        }

        @Test
        @DisplayName("Volley projectiles have exact physical attributes: speed=210, radius=10, dmg=20, lifetime=3.2s, pierce=0")
        void testVolleyProjectileAttributes() {
            BossEnemy boss = new BossEnemy(new Vector2D(200.0f, 200.0f));
            List<Projectile> volley = boss.fireVolley(new Vector2D(300.0f, 200.0f));

            assertEquals(3, volley.size());
            for (int i = 0; i < 3; i++) {
                Projectile p = volley.get(i);
                assertEquals(210.0f, p.getSpeed(), 0.01f, "Projectile speed must be 210.0f");
                assertEquals(10.0f, p.getRadius(), 0.01f, "Projectile radius must be 10.0f");
                assertEquals(20.0f, p.getDamage(), 0.01f, "Projectile damage must be 20.0f");
                assertEquals(3.2f, p.getLifetime(), 0.01f, "Projectile lifetime must be 3.2f seconds");
                assertEquals(0, p.getPierceRemaining(), "Projectile pierce must be 0");
                assertEquals(ProjectileFaction.ENEMY, p.getFaction(), "Projectile faction must be ENEMY");
                assertNotNull(p.getPrimaryColor(), "Primary color must be configured");
                assertNotNull(p.getSecondaryColor(), "Secondary color must be configured");
                assertTrue(p.isAlive(), "Projectile must be alive upon firing");
            }
        }

        @Test
        @DisplayName("Volley spawn offset: each projectile spawns at bossPos + dir * (radius + 8) = 58px")
        void testVolleySpawnOffsets() {
            Vector2D bossPos = new Vector2D(1000.0f, 1500.0f);
            BossEnemy boss = new BossEnemy(bossPos);

            // Boss radius is 50.0f, offset is radius + 8.0f = 58.0f
            float expectedOffset = boss.getRadius() + 8.0f;
            assertEquals(58.0f, expectedOffset, 0.01f);

            List<Projectile> volley = boss.fireVolley(new Vector2D(1000.0f, 1200.0f)); // Aiming upward (-Y)
            for (Projectile p : volley) {
                Vector2D spawnPos = p.getPosition();
                Vector2D dir = p.getDirection();

                // Vector from bossPos to spawnPos must be parallel to dir and length exactly 58.0
                Vector2D diff = Vector2D.sub(spawnPos, bossPos);
                assertEquals(expectedOffset, diff.len(), 0.05f,
                        "Missile spawn distance from boss center must be 58px");

                // Check that diff normalized matches dir
                Vector2D normDiff = diff.nor();
                assertEquals(dir.x, normDiff.x, 1e-3f);
                assertEquals(dir.y, normDiff.y, 1e-3f);
            }
        }

        @Test
        @DisplayName("Volley aiming corner cases: null, zero-distance, micro-distance, and astronomical distance")
        void testVolleyAimingCornerCases() {
            BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 500.0f));

            // 1. Null target: safe fallback to direction (1, 0)
            List<Projectile> nullTargetVolley = boss.fireVolley(null);
            assertEquals(3, nullTargetVolley.size());
            assertEquals(1.0f, nullTargetVolley.get(1).getDirection().x, 1e-4f);
            assertEquals(0.0f, nullTargetVolley.get(1).getDirection().y, 1e-4f);

            // 2. Zero-distance (target exactly at boss position)
            List<Projectile> zeroDistVolley = boss.fireVolley(new Vector2D(500.0f, 500.0f));
            assertEquals(3, zeroDistVolley.size());
            assertEquals(1.0f, zeroDistVolley.get(1).getDirection().x, 1e-4f);
            assertEquals(0.0f, zeroDistVolley.get(1).getDirection().y, 1e-4f);

            // 3. Micro-distance (distance squared < 1e-4)
            List<Projectile> microDistVolley = boss.fireVolley(new Vector2D(500.001f, 500.001f));
            assertEquals(3, microDistVolley.size());
            assertEquals(1.0f, microDistVolley.get(1).getDirection().x, 1e-4f);
            assertEquals(0.0f, microDistVolley.get(1).getDirection().y, 1e-4f);

            // 4. Astronomical distance (10^7 units away in +X, -Y)
            List<Projectile> astroVolley = boss.fireVolley(new Vector2D(500.0f + 1e7f, 500.0f - 1e7f));
            assertEquals(3, astroVolley.size());
            Vector2D midDir = astroVolley.get(1).getDirection();
            float invSqrt2 = 1.0f / (float) Math.sqrt(2.0);
            assertEquals(invSqrt2, midDir.x, 1e-3f);
            assertEquals(-invSqrt2, midDir.y, 1e-3f);
        }

        @Test
        @DisplayName("Volley cooldown state machine: reset to 4.0s, decay with dt, and dead boss safety")
        void testVolleyCooldownStateMachine() {
            BossEnemy boss = new BossEnemy(new Vector2D(100.0f, 100.0f));

            // Initially ready
            assertTrue(boss.canFireVolley());
            assertEquals(0.0f, boss.getVolleyTimer(), 0.001f);

            // Fire resets cooldown to 4.0s
            boss.fireVolley(new Vector2D(200.0f, 100.0f));
            assertFalse(boss.canFireVolley());
            assertEquals(4.0f, boss.getVolleyTimer(), 0.01f);

            // Advance dt = 1.5s -> timer = 2.5s
            boss.update(1.5f);
            assertFalse(boss.canFireVolley());
            assertEquals(2.5f, boss.getVolleyTimer(), 0.01f);

            // Advance dt = 2.49s -> timer = 0.01s (still cannot fire)
            boss.update(2.49f);
            assertFalse(boss.canFireVolley());
            assertEquals(0.01f, boss.getVolleyTimer(), 0.01f);

            // Advance dt = 0.02s -> timer reaches 0.0s (can fire)
            boss.update(0.02f);
            assertTrue(boss.canFireVolley());
            assertEquals(0.0f, boss.getVolleyTimer(), 0.001f);

            // Sub-zero clamp test: update past 0 stays at 0.0
            boss.update(1.0f);
            assertEquals(0.0f, boss.getVolleyTimer(), 0.001f);

            // Manual timer manipulation
            boss.setVolleyTimer(2.5f);
            assertEquals(2.5f, boss.getVolleyTimer(), 0.01f);
            assertFalse(boss.canFireVolley());

            boss.setVolleyTimer(-10.0f); // Clamps to 0
            assertEquals(0.0f, boss.getVolleyTimer(), 0.001f);
            assertTrue(boss.canFireVolley());

            boss.resetVolleyTimer();
            assertEquals(4.0f, boss.getVolleyTimer(), 0.01f);

            // Adversarial condition: Dead boss CANNOT fire volley even if timer == 0
            boss.setVolleyTimer(0.0f);
            boss.takeDamage(new DamageInfo(2500.0f, true));
            assertFalse(boss.isAlive(), "Boss must be dead");
            assertFalse(boss.canFireVolley(), "Dead boss MUST NOT be able to fire volley attack");
        }
    }

    // =========================================================================
    // Area 2: Spawner Boss Lifecycle Timeline Simulation
    // =========================================================================

    @Nested
    @DisplayName("Challenge 2: Spawner Boss Lifecycle Timeline Simulation")
    class SpawnerBossLifecycleTests {

        @Test
        @DisplayName("Exact 60.0s spawn boundary: no boss at t=59.9s, boss spawns at t=60.01s with exact stats")
        void testExact60SecondSpawnBoundary() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> activeEnemies = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

            assertEquals(60.0f, spawner.getBossSpawnTimer(), 0.001f);

            // Simulate up to t = 59.9s in 1.0s and 0.1s steps
            for (int i = 0; i < 59; i++) {
                spawner.update(1.0f, playerPos, activeEnemies);
            }
            spawner.update(0.9f, playerPos, activeEnemies);

            assertEquals(59.9f, spawner.getElapsedTime(), 0.01f);
            assertEquals(0.1f, spawner.getBossSpawnTimer(), 0.01f);

            // Verify no boss has spawned yet
            long bossCount59 = activeEnemies.stream().filter(e -> e != null && e.isBoss()).count();
            assertEquals(0, bossCount59, "No boss should exist prior to t=60s");

            // Advance by 0.11s -> t = 60.01s (crosses 60.0s boundary)
            spawner.update(0.11f, playerPos, activeEnemies);

            long bossCount60 = activeEnemies.stream().filter(e -> e != null && e.isBoss()).count();
            assertEquals(1, bossCount60, "Exactly one boss must be spawned at t=60s");

            // Verify exact boss attributes
            BossEnemy boss = (BossEnemy) activeEnemies.stream().filter(e -> e != null && e.isBoss()).findFirst().orElse(null);
            assertNotNull(boss);
            assertEquals(1800.0f, boss.getMaxHealth(), 0.01f);
            assertEquals(45.0f, boss.getArmor(), 0.01f);
            assertEquals(55.0f, boss.getBaseMoveSpeed(), 0.01f);
            assertEquals(45.0f, boss.getContactDamage(), 0.01f);
            assertEquals(50.0f, boss.getRadius(), 0.01f);
            assertEquals(250, boss.getExpReward());
            assertEquals("ANCIENT GOLEM OVERLORD", boss.getName());
            assertTrue(boss.isBoss());

            // Timer must be reset to 90.0s
            assertEquals(90.0f, spawner.getBossSpawnTimer(), 0.2f);
        }

        @Test
        @DisplayName("Boss concurrency suppression: while boss is alive, no second boss spawns across timeline")
        void testBossConcurrencySuppressionWhileAlive() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> activeEnemies = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

            // Trigger boss at 60s
            spawner.update(60.01f, playerPos, activeEnemies);
            assertEquals(1, activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count());

            // Advance simulation through multiple milestones while boss remains alive:
            // t = 65, 70, 75, 80, 100, 120, 149 seconds
            float[] checkTimes = {5.0f, 5.0f, 5.0f, 5.0f, 20.0f, 20.0f, 29.0f}; // Cumulative elapsed: 65, 70, 75, 80, 100, 120, 149
            for (float dt : checkTimes) {
                spawner.update(dt, playerPos, activeEnemies);
                long livingBosses = activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count();
                assertEquals(1, livingBosses, "Boss count must remain strictly 1 at elapsed time " + spawner.getElapsedTime());
            }
        }

        @Test
        @DisplayName("10s retry delay: when boss is still alive at timer expiry (t=150s, 160s), retries every 10s until defeat")
        void testBossRetryDelayMechanism() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> activeEnemies = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

            // First boss spawns at 60s
            spawner.update(60.01f, playerPos, activeEnemies);
            BossEnemy firstBoss = (BossEnemy) activeEnemies.stream().filter(e -> e != null && e.isBoss()).findFirst().orElse(null);
            assertNotNull(firstBoss);
            assertTrue(firstBoss.isAlive());

            // Advance 89.9s -> t = 149.91s (timer is ~0.09s)
            spawner.update(89.9f, playerPos, activeEnemies);
            assertEquals(1, activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count());
            assertTrue(spawner.getBossSpawnTimer() > 0.0f && spawner.getBossSpawnTimer() <= 0.2f);

            // Advance 0.2s -> t = 150.11s: timer hits 0 while first boss is still fighting!
            spawner.update(0.2f, playerPos, activeEnemies);

            // Concurrency check: no duplicate boss spawned!
            assertEquals(1, activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count());

            // Retry delay: timer must be reset to exactly 10.0s!
            assertEquals(10.0f, spawner.getBossSpawnTimer(), 0.5f);

            // Advance another 10.0s -> t = 160.11s: boss still alive -> retry timer triggers again!
            spawner.update(10.0f, playerPos, activeEnemies);
            assertEquals(1, activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count());
            assertEquals(10.0f, spawner.getBossSpawnTimer(), 0.5f);

            // Player defeats the first boss at t = 165s
            spawner.update(5.0f, playerPos, activeEnemies); // 5s into the 10s retry window
            firstBoss.takeDamage(9999.0f, true);
            assertFalse(firstBoss.isAlive(), "First boss defeated");

            // Advance remaining 5.1s -> t = 170.21s: retry timer hits 0 with no living boss!
            spawner.update(5.1f, playerPos, activeEnemies);

            // SECOND BOSS SPAWNS!
            long aliveBossesAfterRetry = activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count();
            assertEquals(1, aliveBossesAfterRetry, "Second boss must spawn at t=170s after 10s retry window");

            // Timer resets to 90.0s for the next wave
            assertEquals(90.0f, spawner.getBossSpawnTimer(), 0.5f);
        }

        @Test
        @DisplayName("Recurrence at 90s: when boss is defeated early (t=80s), next boss spawns at t=150s (60s + 90s)")
        void testBossRecurrenceAfterEarlyDefeat() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> activeEnemies = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

            // Boss 1 spawns at 60s
            spawner.update(60.01f, playerPos, activeEnemies);
            BossEnemy firstBoss = (BossEnemy) activeEnemies.stream().filter(e -> e != null && e.isBoss()).findFirst().orElse(null);
            assertNotNull(firstBoss);

            // Boss 1 defeated at t = 80s (20s into encounter)
            spawner.update(19.99f, playerPos, activeEnemies);
            firstBoss.takeDamage(9999.0f, true);
            assertFalse(firstBoss.isAlive());

            // Between t=80s and t=149s, no new boss should spawn
            spawner.update(69.0f, playerPos, activeEnemies); // t = 149s
            assertEquals(0, activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count());

            // Advance to t = 150.1s: 90s recurrence triggers next boss wave!
            spawner.update(1.2f, playerPos, activeEnemies);
            assertEquals(1, activeEnemies.stream().filter(e -> e != null && e.isAlive() && e.isBoss()).count());
        }

        @Test
        @DisplayName("Late-game HP scaling exemption: normal enemies scale +25% at t=300s, boss retains exact 1800 HP")
        void testBossHealthScalingExemption() {
            EnemySpawner spawner = new EnemySpawner();
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);
            List<Enemy> list = new ArrayList<>();

            // Fast-forward timeline to 300s
            spawner.update(300.0f, playerPos, list);

            // Normal enemies: Melee, Ranged, Tank receive HP scaling (+25% max)
            Enemy melee = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
            Enemy ranged = spawner.spawnEnemy(playerPos, EnemyType.RANGED);
            Enemy tank = spawner.spawnEnemy(playerPos, EnemyType.TANK);

            // Base health scaled by 1.25x (+25% clamp at t=300s)
            assertEquals(MeleeEnemy.DEFAULT_HEALTH * 1.25f, melee.getMaxHealth(), 0.01f,
                    "Melee enemy should receive +25% HP scaling at t=300s (30 * 1.25 = 37.5)");
            assertEquals(RangedEnemy.DEFAULT_HEALTH * 1.25f, ranged.getMaxHealth(), 0.01f,
                    "Ranged enemy should receive +25% HP scaling at t=300s (20 * 1.25 = 25.0)");
            assertEquals(TankEnemy.DEFAULT_HEALTH * 1.25f, tank.getMaxHealth(), 0.01f,
                    "Tank enemy should receive +25% HP scaling at t=300s (120 * 1.25 = 150.0)");

            // BossEnemy must remain strictly 1800.0 HP (exemption '!enemy.isBoss()')
            Enemy bossViaSpawnBoss = spawner.spawnBoss(playerPos);
            Enemy bossViaSpawnEnemy = spawner.spawnEnemy(playerPos, EnemyType.BOSS);

            assertEquals(1800.0f, bossViaSpawnBoss.getMaxHealth(), 1e-4f,
                    "Boss spawned via spawnBoss() must have exactly 1800.0 HP (immune to late-game scaling)");
            assertEquals(1800.0f, bossViaSpawnEnemy.getMaxHealth(), 1e-4f,
                    "Boss spawned via spawnEnemy(BOSS) must have exactly 1800.0 HP (immune to late-game scaling)");
        }

        @Test
        @DisplayName("Enemy cap exemption: boss spawns even when activeEnemies has reached maxEnemies cap")
        void testBossEnemyCapExemption() {
            EnemySpawner spawner = new EnemySpawner();
            spawner.setMaxEnemies(10);
            List<Enemy> activeEnemies = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

            // Fill activeEnemies with 10 normal enemies
            for (int i = 0; i < 10; i++) {
                activeEnemies.add(new MeleeEnemy(new Vector2D(100.0f * i, 100.0f)));
            }
            assertEquals(10, activeEnemies.size());

            // Force boss timer to expire
            spawner.setBossSpawnTimer(0.1f);
            spawner.update(0.2f, playerPos, activeEnemies);

            // Total enemies should now be 11 (boss was added despite cap)
            assertEquals(11, activeEnemies.size());
            long bossCount = activeEnemies.stream().filter(e -> e != null && e.isBoss()).count();
            assertEquals(1, bossCount, "Boss must spawn even when active enemy cap is reached");
        }

        @Test
        @DisplayName("Spawner reset restores initial timers and telemetry")
        void testSpawnerResetIntegrity() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> list = new ArrayList<>();
            spawner.update(120.0f, new Vector2D(2048.0f, 2048.0f), list);

            assertTrue(spawner.getElapsedTime() >= 120.0f);
            spawner.reset();

            assertEquals(0.0f, spawner.getElapsedTime(), 0.001f);
            assertEquals(60.0f, spawner.getBossSpawnTimer(), 0.001f);
            assertEquals(1.5f, spawner.getSpawnTimer(), 0.001f);
            assertEquals(spawner.getBaseInterval(), spawner.getCurrentInterval(), 0.001f);
            assertEquals(spawner.getBaseMaxEnemies(), spawner.getMaxEnemies());
        }
    }

    // =========================================================================
    // Area 3: Milestone 8 Navigation, Bridge Geometry & Water Avoidance
    // =========================================================================

    @Nested
    @DisplayName("Challenge 3: M8 Navigation, Bridge Geometry & Water Avoidance")
    class NavigationBridgeCrossingTests {

        private static final int[] BRIDGE_Y_CENTERS = {13, 30, 64, 87, 111};
        private static final String[] BRIDGE_NAMES = {
                "Far North Crossing (y=11..15)",
                "Northern Crossing (y=28..32)",
                "Central Highway (y=62..66)",
                "Southern Crossing (y=85..89)",
                "Far South Crossing (y=109..113)"
        };

        @Test
        @DisplayName("Geometric clearance: Boss (radius 50, diameter 100) has >= 30px lateral clearance on all 5 bridges")
        void testBridgeBossGeometricClearance() {
            TileMap map = new TileMap(42L);
            WorldGenerator.generate(map, 42L);
            float bossRadius = BossEnemy.BOSS_RADIUS; // 50.0f

            for (int i = 0; i < BRIDGE_Y_CENTERS.length; i++) {
                int cy = BRIDGE_Y_CENTERS[i];
                String name = BRIDGE_NAMES[i];

                // Find bridge span bounds at cy
                int minBx = 999;
                int maxBx = -1;
                for (int x = 0; x < map.getWidth(); x++) {
                    if (map.isBridgeTile(x, cy)) {
                        minBx = Math.min(minBx, x);
                        maxBx = Math.max(maxBx, x);
                    }
                }
                assertTrue(minBx <= maxBx, name + " must have bridge tiles");

                // Bridge center
                float bridgeCenterX = (minBx + maxBx + 1) * 0.5f * map.getTileSize();
                float bridgeCenterY = (cy + 0.5f) * map.getTileSize();
                Vector2D center = new Vector2D(bridgeCenterX, bridgeCenterY);

                // Boss must be traversable at geometric center
                assertTrue(map.isTraversable(center, bossRadius),
                        "Boss (radius 50) must be traversable at center of " + name + " " + center);

                // Bridge is 5 tiles wide (160px). Boss diameter is 100px.
                // Distance from center to north water edge: (5 * 32) / 2 = 80px.
                // Distance minus boss radius = 80 - 50 = 30px buffer.
                float bridgeSpanHeight = 5.0f * map.getTileSize(); // 160px
                float lateralBuffer = (bridgeSpanHeight - bossRadius * 2.0f) * 0.5f;
                assertEquals(30.0f, lateralBuffer, 0.01f, "Lateral buffer must be 30px");
            }
        }

        @Test
        @DisplayName("Continuous west-to-east pathfinding: Boss (radius 50) successfully traverses all 5 river bridges")
        void testBossBridgePathfindingAcrossAllFiveBridges() {
            TileMap map = new TileMap(42L);
            WorldGenerator.generate(map, 42L);
            AStarPathfinder pf = new AStarPathfinder(map);
            float bossRadius = BossEnemy.BOSS_RADIUS; // 50.0f

            for (int i = 0; i < BRIDGE_Y_CENTERS.length; i++) {
                int cy = BRIDGE_Y_CENTERS[i];
                String name = BRIDGE_NAMES[i];

                // Find bridge span X range
                int minBx = 999;
                int maxBx = -1;
                for (int x = 0; x < map.getWidth(); x++) {
                    if (map.isBridgeTile(x, cy)) {
                        minBx = Math.min(minBx, x);
                        maxBx = Math.max(maxBx, x);
                    }
                }

                // Start on West approach landing (3 tiles west of bridge entrance)
                // Target on East approach landing (3 tiles east of bridge exit)
                Vector2D startWest = map.tileToWorld(minBx - 3, cy);
                Vector2D endEast = map.tileToWorld(maxBx + 3, cy);

                assertTrue(map.isTraversable(startWest, bossRadius),
                        name + " West landing must be traversable for Boss");
                assertTrue(map.isTraversable(endEast, bossRadius),
                        name + " East landing must be traversable for Boss");

                // Compute A* path for Boss
                List<Vector2D> path = pf.findPath(startWest, endEast, bossRadius);
                assertFalse(path.isEmpty(), "Boss path must not be empty across " + name);

                // Verify endpoint is reached
                Vector2D lastWp = path.get(path.size() - 1);
                assertEquals(endEast.x, lastWp.x, 32.0f, name + " Boss must reach East landing X");
                assertEquals(endEast.y, lastWp.y, 32.0f, name + " Boss must reach East landing Y");

                // Verify all waypoints and all sampled points along path are traversable and NEVER touch water
                Vector2D prev = startWest;
                boolean crossedBridge = false;

                for (Vector2D wp : path) {
                    assertTrue(map.isTraversable(wp, bossRadius),
                            name + ": Waypoint " + wp + " must be traversable for Boss (r=50)");

                    // Sample points between prev and wp every 4px
                    float segmentDist = prev.dist(wp);
                    int steps = Math.max(1, (int) Math.ceil(segmentDist / 4.0f));
                    for (int s = 0; s <= steps; s++) {
                        float t = (float) s / (float) steps;
                        float sx = prev.x + (wp.x - prev.x) * t;
                        float sy = prev.y + (wp.y - prev.y) * t;
                        Vector2D sampledPos = new Vector2D(sx, sy);

                        assertTrue(map.isTraversable(sampledPos, bossRadius),
                                name + ": Sampled point " + sampledPos + " must be traversable for Boss (r=50)");

                        int[] tile = map.worldToTile(sx, sy);
                        if (map.isBridgeTile(tile[0], tile[1])) {
                            crossedBridge = true;
                        }
                    }
                    prev = wp;
                }

                assertTrue(crossedBridge, name + ": Boss path must actually cross bridge tiles");
            }
        }

        @Test
        @DisplayName("Water avoidance detour: when facing unbridged river (y=46), Boss path routes to bridge and avoids water")
        void testBossWaterAvoidanceAndRiverDetour() {
            TileMap map = new TileMap(42L);
            WorldGenerator.generate(map, 42L);
            AStarPathfinder pf = new AStarPathfinder(map);
            float bossRadius = BossEnemy.BOSS_RADIUS; // 50.0f

            // Row y = 46 has NO bridge (water channel between bridges at y=30 and y=64)
            int yRow = 46;

            // Find water channel at row 46
            int waterMinX = 999;
            int waterMaxX = -1;
            for (int x = 0; x < map.getWidth(); x++) {
                if (map.isWaterTile(x, yRow)) {
                    waterMinX = Math.min(waterMinX, x);
                    waterMaxX = Math.max(waterMaxX, x);
                }
            }
            assertTrue(waterMinX <= waterMaxX, "Row 46 must contain river water");

            // West start 5 tiles west of water, East target 5 tiles east of water
            Vector2D westStart = map.tileToWorld(waterMinX - 5, yRow);
            Vector2D eastTarget = map.tileToWorld(waterMaxX + 5, yRow);

            assertTrue(map.isTraversable(westStart, bossRadius), "West start must be traversable");
            assertTrue(map.isTraversable(eastTarget, bossRadius), "East target must be traversable");

            // Pathfinder must NOT take direct path across water, but detour via bridge (e.g. y=30 or y=64)
            List<Vector2D> path = pf.findPath(westStart, eastTarget, bossRadius);
            assertFalse(path.isEmpty(), "Detour path must be found");

            // Verify that path moves vertically towards a bridge (either y < 40 or y > 55)
            boolean detouredToBridge = false;
            for (Vector2D wp : path) {
                int[] tile = map.worldToTile(wp.x, wp.y);
                if (map.isBridgeTile(tile[0], tile[1])) {
                    detouredToBridge = true;
                    break;
                }
            }
            assertTrue(detouredToBridge, "Pathfinder must route Boss across a bridge when facing river water");

            // Verify that every point along the path is strictly traversable for radius 50
            Vector2D prev = westStart;
            for (Vector2D wp : path) {
                float dist = prev.dist(wp);
                int steps = Math.max(1, (int) Math.ceil(dist / 4.0f));
                for (int s = 0; s <= steps; s++) {
                    float t = (float) s / (float) steps;
                    float sx = prev.x + (wp.x - prev.x) * t;
                    float sy = prev.y + (wp.y - prev.y) * t;
                    Vector2D p = new Vector2D(sx, sy);

                    assertTrue(map.isTraversable(p, bossRadius),
                            "Boss must never enter water or impassable terrain: violation at " + p);
                }
                prev = wp;
            }
        }
    }
}
