package com.spellstorm.entities;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Adversarial Challenge Test Suite for Milestone 4:
 * Enemy Hierarchy, AI Steering, and Spawner Subsystem.
 *
 * Authored by m4_challenger_2 to rigorously challenge:
 * 1. Enemy archetype stats, radii (16, 15, 26), and touch damage cooldown enforcement.
 * 2. RangedEnemy kiting behavior (< 205px retreat) and lateral strafing ([205, 275]px).
 * 3. Enemy navigation steering with AStarPathfinder across river bridges and around obstacles.
 * 4. EnemySpawner off-screen spawn ring outside camera frustum across zoom levels [0.75, 2.25].
 * 5. EnemySpawner traversability guarantee: 0 water/obstacle spawns across 1,000+ spawns.
 * 6. EnemySpawner match progression scaling from 0 to 120s and extended 15-minute matches.
 */
@DisplayName("Milestone 4 Challenger: Enemy Hierarchy & Spawner Subsystem Adversarial Tests")
public class EnemyHierarchyAndSpawnerAdversarialTest {

    @BeforeAll
    public static void initHeadless() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    public void setUp() {
        Entity.resetIdCounter(1);
    }

    // =========================================================================
    // Challenge 1: Archetype Dimensions, Stats & Touch Damage Cooldown
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: Archetype Dimensions, Stats & Touch Damage Cooldown")
    class ArchetypeStatsAndCooldownChallenge {

        @Test
        @DisplayName("1.1 Enemy dimensions and base stats match Milestone 4 specification")
        public void testEnemyDimensionsAndBaseStats() {
            // MeleeEnemy
            MeleeEnemy melee = new MeleeEnemy(new Vector2D(100, 100));
            assertEquals(16.0f, melee.getRadius(), 1e-4f, "Melee radius must be 16.0");
            assertEquals(30.0f, melee.getMaxHealth(), 1e-4f, "Melee default HP must be 30.0");
            assertEquals(140.0f, melee.getBaseMoveSpeed(), 1e-4f, "Melee default speed must be 140.0");
            assertEquals(15.0f, melee.getContactDamage(), 1e-4f, "Melee default contact damage must be 15.0");
            assertEquals(0.8f, melee.getAttackCooldown(), 1e-4f, "Melee attack cooldown must be 0.8s");
            assertEquals(10, melee.getExpReward(), "Melee exp reward must be 10");
            assertEquals(EnemyType.MELEE, melee.getType());

            // RangedEnemy
            RangedEnemy ranged = new RangedEnemy(new Vector2D(100, 100));
            assertEquals(15.0f, ranged.getRadius(), 1e-4f, "Ranged radius must be 15.0");
            assertEquals(20.0f, ranged.getMaxHealth(), 1e-4f, "Ranged default HP must be 20.0");
            assertEquals(110.0f, ranged.getBaseMoveSpeed(), 1e-4f, "Ranged default speed must be 110.0");
            assertEquals(10.0f, ranged.getContactDamage(), 1e-4f, "Ranged default contact damage must be 10.0");
            assertEquals(1.4f, ranged.getAttackCooldown(), 1e-4f, "Ranged contact cooldown must be 1.4s");
            assertEquals(1.8f, ranged.getRangedAttackCooldown(), 1e-4f, "Ranged attack cooldown must be 1.8s");
            assertEquals(15, ranged.getExpReward(), "Ranged exp reward must be 15");
            assertEquals(EnemyType.RANGED, ranged.getType());

            // TankEnemy
            TankEnemy tank = new TankEnemy(new Vector2D(100, 100));
            assertEquals(26.0f, tank.getRadius(), 1e-4f, "Tank radius must be 26.0");
            assertEquals(120.0f, tank.getMaxHealth(), 1e-4f, "Tank default HP must be 120.0");
            assertEquals(80.0f, tank.getBaseMoveSpeed(), 1e-4f, "Tank default speed must be 80.0");
            assertEquals(25.0f, tank.getContactDamage(), 1e-4f, "Tank default contact damage must be 25.0");
            assertEquals(25.0f, tank.getArmor(), 1e-4f, "Tank default armor must be 25.0");
            assertEquals(1.2f, tank.getAttackCooldown(), 1e-4f, "Tank contact cooldown must be 1.2s");
            assertEquals(0.5f, tank.getKnockbackResistance(), 1e-4f, "Tank knockback resistance must be 0.5");
            assertEquals(30, tank.getExpReward(), "Tank exp reward must be 30");
            assertEquals(EnemyType.TANK, tank.getType());
        }

        @Test
        @DisplayName("1.2 Touch damage cooldown enforcement: MeleeEnemy does NOT deal damage every frame")
        public void testTouchDamageCooldownEnforcementMelee() {
            Player player = new Player(new Vector2D(200, 200), 250.0f, 0.0f, 230.0f, 45.0f);
            MeleeEnemy melee = new MeleeEnemy(new Vector2D(210, 200)); // Overlapping: dist = 10 < 20 + 16 = 36

            float dt = 1.0f / 60.0f; // 60 FPS
            int hitCount = 0;
            float totalDamageDealt = 0.0f;
            float initialHp = player.getHealth();

            // Simulate 60 frames (1.0 second) of continuous contact
            for (int frame = 0; frame < 60; frame++) {
                float prevHp = player.getHealth();
                boolean attacked = melee.tryAttack(player);
                if (attacked) {
                    hitCount++;
                    totalDamageDealt += (prevHp - player.getHealth());
                }
                melee.update(dt);
            }

            // At 60 FPS over 1.0s with cooldown 0.8s:
            // Frame 0 (t=0.0s): Hit 1 -> cooldown set to 0.8s
            // Frames 1 to 47 (t < 0.8s): attackTimer > 0, 0 hits
            // Frame 48 (t=0.8s): Hit 2 -> cooldown reset to 0.8s
            // Frames 49 to 59 (t < 1.0s): attackTimer > 0, 0 hits
            assertEquals(2, hitCount, "Melee enemy should hit exactly twice in 1.0s with 0.8s cooldown");
            assertEquals(30.0f, totalDamageDealt, 1e-4f, "Total damage must be 2 * 15 = 30, NOT 60 * 15 = 900");
            assertEquals(initialHp - 30.0f, player.getHealth(), 1e-4f);
        }

        @Test
        @DisplayName("1.3 Touch damage cooldown enforcement: TankEnemy across 120 frames with armor mitigation")
        public void testTouchDamageCooldownEnforcementTank() {
            // Player has 30 armor -> damage reduction = 100 / (100 + 30) = 100/130 ~ 0.7692
            // Tank dmg = 25 -> mitigated = 25 * 100 / 130 = 19.2307
            Player player = new Player(new Vector2D(300, 300), 250.0f, 30.0f, 230.0f, 45.0f);
            TankEnemy tank = new TankEnemy(new Vector2D(310, 300)); // Overlapping: dist = 10 < 20 + 26 = 46

            float dt = 1.0f / 60.0f;
            int hitCount = 0;

            // Simulate 120 frames (2.0 seconds) of continuous contact
            for (int frame = 0; frame < 120; frame++) {
                boolean attacked = tank.tryAttack(player);
                if (attacked) {
                    hitCount++;
                }
                tank.update(dt);
            }

            // Tank cooldown is 1.2s:
            // Frame 0 (t=0.0s): Hit 1
            // Frame 72 (t=1.2s): Hit 2
            // At t=2.0s: exactly 2 hits
            assertEquals(2, hitCount, "Tank enemy should hit exactly twice in 2.0s with 1.2s cooldown");
            float expectedDmgPerHit = 25.0f * 100.0f / (100.0f + 30.0f);
            float expectedTotalDmg = expectedDmgPerHit * 2.0f;
            assertEquals(250.0f - expectedTotalDmg, player.getHealth(), 1e-2f);
        }

        @Test
        @DisplayName("1.4 Stress test: 600 frames continuous overlap strictly enforces cooldown invariant")
        public void testContinuousTouchDamageStress600Frames() {
            Player player = new Player(new Vector2D(500, 500), 10000.0f, 0.0f, 230.0f, 45.0f);
            MeleeEnemy melee = new MeleeEnemy(new Vector2D(505, 500));

            float dt = 0.0166667f; // ~60 FPS
            int totalFrames = 600; // 10.0 seconds
            int hits = 0;

            for (int f = 0; f < totalFrames; f++) {
                if (melee.tryAttack(player)) {
                    hits++;
                }
                melee.update(dt);
            }

            // Expected hits over 10.0s with 0.8s cd: 1 + floor(10.0 / 0.8) = 1 + 12 = 13 hits
            assertEquals(13, hits, "Continuous overlap over 10s must produce exactly 13 hits");
            assertEquals(10000.0f - (13 * 15.0f), player.getHealth(), 1e-3f);
        }

        @Test
        @DisplayName("1.5 Dead enemy cannot inflict touch damage or act")
        public void testDeadEnemyTouchDamageRejection() {
            Player player = new Player(new Vector2D(100, 100));
            TankEnemy tank = new TankEnemy(new Vector2D(105, 100));

            tank.takeDamage(9999.0f);
            assertFalse(tank.isAlive());
            assertFalse(tank.canAttack());
            assertFalse(tank.tryAttack(player));
            assertEquals(player.getMaxHealth(), player.getHealth(), 1e-4f);
        }
    }

    // =========================================================================
    // Challenge 2: RangedEnemy Kiting and Lateral Strafing Behavior
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: RangedEnemy Kiting and Lateral Strafing")
    class RangedEnemyKitingAndStrafingChallenge {

        private static final float PREFERRED_DIST = 240.0f;
        private static final float TOLERANCE = 35.0f;
        private static final float RETREAT_THRESHOLD = PREFERRED_DIST - TOLERANCE; // 205.0f
        private static final float ADVANCE_THRESHOLD = PREFERRED_DIST + TOLERANCE; // 275.0f

        @ParameterizedTest
        @ValueSource(floats = {40.0f, 80.0f, 120.0f, 160.0f, 200.0f, 204.5f})
        @DisplayName("2.1 Kiting: Retreat away from player when distance < 205px across multiple distances")
        public void testRangedEnemyRetreatsWhenBelow205(float initialDist) {
            Vector2D playerPos = new Vector2D(500, 500);
            // Place enemy to the right of player
            RangedEnemy enemy = new RangedEnemy(new Vector2D(playerPos.x + initialDist, playerPos.y));

            float distBefore = enemy.getPosition().dist(playerPos);
            enemy.updateAI(0.1f, playerPos);
            float distAfter = enemy.getPosition().dist(playerPos);

            assertTrue(distAfter > distBefore,
                    "Enemy at dist " + initialDist + " (< 205px) must retreat away from player. Before: "
                            + distBefore + ", After: " + distAfter);
            assertTrue(enemy.getX() > playerPos.x + initialDist,
                    "Enemy X coordinate should have increased moving away from player");
        }

        @Test
        @DisplayName("2.2 Kiting: Retreat verified across all 8 cardinal and diagonal quadrants")
        public void testRangedEnemyRetreatsInAllDirections() {
            Vector2D playerPos = new Vector2D(1000, 1000);
            float testDist = 150.0f; // < 205px

            for (int i = 0; i < 8; i++) {
                float angle = i * (MathUtils.TWO_PI / 8.0f);
                float ex = playerPos.x + (float) Math.cos(angle) * testDist;
                float ey = playerPos.y + (float) Math.sin(angle) * testDist;

                RangedEnemy enemy = new RangedEnemy(new Vector2D(ex, ey));
                float distBefore = enemy.getPosition().dist(playerPos);
                enemy.updateAI(0.1f, playerPos);
                float distAfter = enemy.getPosition().dist(playerPos);

                assertTrue(distAfter > distBefore,
                        "Enemy in direction " + i + " must retreat. distBefore=" + distBefore + ", distAfter=" + distAfter);

                // Check movement vector points away from player: dot(displacement, dirToPlayer) < 0
                Vector2D disp = Vector2D.sub(enemy.getPosition(), new Vector2D(ex, ey));
                Vector2D toPlayer = Vector2D.sub(playerPos, new Vector2D(ex, ey)).nor();
                float dot = disp.dot(toPlayer);
                assertTrue(dot < -1e-3f, "Displacement must be oriented away from player (dot < 0), was " + dot);
            }
        }

        @ParameterizedTest
        @ValueSource(floats = {275.5f, 290.0f, 320.0f, 400.0f, 500.0f})
        @DisplayName("2.3 Advancing: Approach player when distance > 275px")
        public void testRangedEnemyAdvancesWhenAbove275(float initialDist) {
            Vector2D playerPos = new Vector2D(500, 500);
            RangedEnemy enemy = new RangedEnemy(new Vector2D(playerPos.x + initialDist, playerPos.y));

            float distBefore = enemy.getPosition().dist(playerPos);
            enemy.updateAI(0.1f, playerPos);
            float distAfter = enemy.getPosition().dist(playerPos);

            assertTrue(distAfter < distBefore,
                    "Enemy at dist " + initialDist + " (> 275px) must advance towards player. Before: "
                            + distBefore + ", After: " + distAfter);
        }

        @ParameterizedTest
        @ValueSource(floats = {206.0f, 220.0f, 240.0f, 260.0f, 274.0f})
        @DisplayName("2.4 Strafing: Lateral movement within sweet spot [205px, 275px]")
        public void testRangedEnemyLateralStrafingInSweetSpot(float sweetSpotDist) {
            Vector2D playerPos = new Vector2D(500, 500);
            // Enemy directly East of player at sweet spot distance
            RangedEnemy enemy = new RangedEnemy(new Vector2D(playerPos.x + sweetSpotDist, playerPos.y));

            float distBefore = enemy.getPosition().dist(playerPos);
            enemy.updateAI(0.1f, playerPos);
            float distAfter = enemy.getPosition().dist(playerPos);

            // In sweet spot, radial distance stays almost identical
            assertEquals(distBefore, distAfter, 1.5f,
                    "In sweet spot, radial distance to player should remain stable");

            // Lateral displacement on Y axis must be non-zero
            float lateralDisp = Math.abs(enemy.getY() - playerPos.y);
            assertTrue(lateralDisp > 0.1f,
                    "Enemy should displace laterally in sweet spot. Lateral disp: " + lateralDisp);
        }

        @Test
        @DisplayName("2.5 Strafing oscillation: animTimer oscillation causes alternating strafe directions")
        public void testRangedEnemyStrafeOscillation() {
            Vector2D playerPos = new Vector2D(500, 500);
            RangedEnemy enemy = new RangedEnemy(new Vector2D(740, 500)); // Exactly 240px

            // animTimer starts at 0: moves laterally
            enemy.updateAI(0.05f, playerPos);
            float y1 = enemy.getY();
            assertNotEquals(500.0f, y1, 1e-3f, "Initial strafe should produce non-zero lateral displacement");
            float deltaY1 = y1 - 500.0f;

            // Advance animTimer to peak positive sine: sin(animTimer * 1.5) > 0
            // When animTimer = 1.05s -> 1.05 * 1.5 = 1.575 ~ PI/2 -> sin ~ 1.0 > 0 -> strafeFactor = +0.45
            enemy.update(1.05f);
            enemy.updateAI(0.05f, playerPos);
            float y2 = enemy.getY();
            float deltaY2 = y2 - y1;

            // Directions must be opposite (one positive, one negative)
            assertTrue((deltaY1 * deltaY2) < 0.0f,
                    "Oscillation across animTimer cycle must alternate strafe direction. deltaY1="
                            + deltaY1 + ", deltaY2=" + deltaY2);
        }
    }

    // =========================================================================
    // Challenge 3: Enemy Navigation Steering (A*, Bridges, Obstacles, LOS)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: Enemy Navigation Steering (A*, Bridges, Obstacles)")
    class NavigationSteeringChallenge {

        @Test
        @DisplayName("3.1 MeleeEnemy traverses river bridge via AStarPathfinder without entering water")
        public void testMeleeNavigationAcrossBridge() {
            TileMap tileMap = new TileMap(42);
            AStarPathfinder pathfinder = new AStarPathfinder(tileMap);

            // River is unbridged at row y=21 (y_px ~ 680px), between bridge 1 (y~416) and bridge 2 (y~960)
            Vector2D enemyStart = new Vector2D(900.0f, 680.0f);
            Vector2D playerPos = new Vector2D(1600.0f, 680.0f);

            assertTrue(tileMap.isTraversable(enemyStart.x, enemyStart.y, 16.0f), "Start must be traversable");
            assertTrue(tileMap.isTraversable(playerPos.x, playerPos.y, 20.0f), "Target must be traversable");

            // Direct line of sight must be blocked by river
            assertFalse(pathfinder.hasLineOfSight(enemyStart, playerPos, 16.0f),
                    "Direct line of sight across unbridged river segment must be blocked by water");

            MeleeEnemy enemy = new MeleeEnemy(enemyStart.cpy());

            // Run multi-step navigation simulation (step enemy through path)
            float prevDist = enemy.getPosition().dist(playerPos);
            boolean reachedEastSide = false;

            for (int step = 0; step < 220; step++) {
                enemy.updateAI(0.05f, playerPos, tileMap, pathfinder);
                enemy.update(0.05f);

                // Continuous invariant: enemy must NEVER occupy an impassable/water tile
                assertTrue(tileMap.isTraversable(enemy.getX(), enemy.getY(), enemy.getRadius()),
                        "Enemy at (" + enemy.getX() + ", " + enemy.getY() + ") on step " + step + " stepped into invalid/water tile!");

                if (enemy.getX() >= 1350.0f) {
                    reachedEastSide = true;
                    break;
                }
            }

            assertTrue(reachedEastSide, "Enemy should navigate across bridge to reach East side of river");
            float finalDist = enemy.getPosition().dist(playerPos);
            assertTrue(finalDist < prevDist, "Enemy should have significantly closed distance to player");
        }

        @Test
        @DisplayName("3.2 TankEnemy (radius 26) navigates around solid obstacles without collision penetration")
        public void testTankNavigationAroundSolidObstacles() {
            TileMap tileMap = new TileMap(42);
            AStarPathfinder pathfinder = new AStarPathfinder(tileMap);

            // Find a solid obstacle on the map
            Obstacle solidRock = null;
            for (Obstacle o : tileMap.getObstacles()) {
                if (o.isSolid() && o.getCollisionRadius() >= 15.0f) {
                    solidRock = o;
                    break;
                }
            }
            assertNotNull(solidRock, "Must have at least one solid obstacle");

            Vector2D rockCenter = solidRock.getCollisionCenter();
            float rockRadius = solidRock.getCollisionRadius();

            // Place Tank on one side of rock, target on opposite side
            Vector2D tankStart = new Vector2D(rockCenter.x - 70.0f, rockCenter.y);
            Vector2D targetPos = new Vector2D(rockCenter.x + 70.0f, rockCenter.y);

            TankEnemy tank = new TankEnemy(tankStart.cpy());

            // Step through 80 simulation ticks
            for (int step = 0; step < 80; step++) {
                tank.updateAI(0.05f, targetPos, tileMap, pathfinder);
                tank.update(0.05f);

                // Invariant: tank collision circle never penetrates obstacle collision circle
                float distToRock = tank.getPosition().dist(rockCenter);
                float minAllowedDist = tank.getRadius() + rockRadius;
                assertTrue(distToRock >= minAllowedDist - 0.5f,
                        "Tank penetrated solid obstacle on step " + step + "! Dist: " + distToRock + ", Min: " + minAllowedDist);
            }
        }

        @Test
        @DisplayName("3.3 Direct Line-Of-Sight charging within 650px range on clear terrain")
        public void testDirectLosChargingClearTerrain() {
            Vector2D playerPos = new Vector2D(2048, 2048);
            Vector2D enemyPos = new Vector2D(2048 + 400, 2048); // 400px < 650px

            MeleeEnemy enemy = new MeleeEnemy(enemyPos.cpy());
            enemy.updateAI(0.1f, playerPos);

            // When in clear LOS and < 650px, navPath is cleared for direct linear charging
            assertTrue(enemy.getNavPath().isEmpty(), "Clear LOS within 650px should direct charge without waypoints");
            assertTrue(enemy.getX() < enemyPos.x, "Enemy should move directly towards player on X axis");
        }
    }

    // =========================================================================
    // Challenge 4: EnemySpawner Off-Screen Spawn Ring Across Zoom Levels [0.75, 2.25]
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: EnemySpawner Off-Screen Spawn Ring Across Zoom Levels")
    class SpawnRingFrustumChallenge {

        @ParameterizedTest
        @ValueSource(floats = {0.75f, 0.90f, 1.0f, 1.35f, 1.60f, 1.80f, 2.0f, 2.25f})
        @DisplayName("4.1 100% of spawned enemies are strictly outside camera frustum across zoom levels")
        public void testSpawnRingOutsideCameraFrustumAcrossZooms(float testZoom) {
            TileMap tileMap = new TileMap(42);
            EnemySpawner spawner = new EnemySpawner(tileMap);

            GameCamera camera = new GameCamera(1280, 720);
            camera.setTargetZoom(testZoom);
            camera.update(2048.0f, 2048.0f, 1.0f); // Position camera centered on player
            spawner.setCamera(camera);

            Vector2D playerPos = new Vector2D(2048, 2048);
            Rectangle vis = camera.getVisibleBounds();

            // Spawn 150 enemies per zoom level
            for (int i = 0; i < 150; i++) {
                Enemy enemy = spawner.spawnEnemy(playerPos);
                assertNotNull(enemy);

                Vector2D spawnPos = enemy.getPosition();
                float r = enemy.getRadius();

                // Check 1: Camera visibility query must be FALSE
                boolean visible = camera.isVisible(spawnPos, r);
                assertFalse(visible, "Spawned " + enemy.getType() + " at (" + spawnPos.x + ", " + spawnPos.y
                        + ") with radius " + r + " must NOT be visible in camera at zoom " + testZoom
                        + ". Visible bounds: " + vis);

                // Check 2: Spatial coordinates strictly outside visible rectangle
                float vl = camera.getVisibleLeft();
                float vt = camera.getVisibleTop();
                float vw = camera.getViewWidth();
                float vh = camera.getViewHeight();

                boolean overlapsFrustum = (spawnPos.x + r >= vl && spawnPos.x - r <= vl + vw
                        && spawnPos.y + r >= vt && spawnPos.y - r <= vt + vh);
                assertFalse(overlapsFrustum, "Spawn circle overlaps camera visible viewport!");

                // Check 3: Distance from camera center must exceed half-diagonal
                float halfDiag = (float) Math.hypot(vw * 0.5f, vh * 0.5f);
                float distFromCenter = spawnPos.dist(camera.getCenter());
                assertTrue(distFromCenter > halfDiag,
                        "Spawn distance from camera center (" + distFromCenter + ") must exceed halfDiag (" + halfDiag + ")");
            }
        }

        @Test
        @DisplayName("4.2 Spawn ring safety margin: min distance is at least halfDiag + 50px")
        public void testSpawnRingSafetyMarginCalculation() {
            GameCamera camera = new GameCamera(1280, 720);
            camera.setTargetZoom(0.75f); // Maximum frustum
            camera.update(2048, 2048, 1.0f);

            EnemySpawner spawner = new EnemySpawner();
            spawner.setCamera(camera);

            Rectangle vis = camera.getVisibleBounds();
            float halfDiag = (float) Math.hypot(vis.width * 0.5f, vis.height * 0.5f);
            float minSpawnDist = spawner.computeMinSpawnDistance(220.0f);

            assertEquals(halfDiag + 50.0f, minSpawnDist, 1e-2f,
                    "computeMinSpawnDistance must include 50px safety margin beyond halfDiag");
        }
    }

    // =========================================================================
    // Challenge 5: EnemySpawner Traversability Guarantee (1,000+ Spawns)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 5: EnemySpawner Traversability Guarantee (1,000+ Spawns)")
    class TraversabilityGuaranteeChallenge {

        @Test
        @DisplayName("5.1 Over 1,200 simulated spawns: 0 spawn in water tiles or intersecting solid obstacles")
        public void test1200SimulatedSpawnsZeroTraversabilityViolations() {
            TileMap tileMap = new TileMap(42);
            EnemySpawner spawner = new EnemySpawner(tileMap, new Random(1337L));

            GameCamera camera = new GameCamera(1280, 720);
            camera.setTargetZoom(1.35f);
            camera.update(2048, 2048, 1.0f);
            spawner.setCamera(camera);

            // Test player in varied strategic locations across map
            Vector2D[] testPlayerPositions = {
                    new Vector2D(2048, 2048), // Center
                    new Vector2D(1200, 2048), // Near west riverbank
                    new Vector2D(1500, 960),  // Near bridge crossing
                    new Vector2D(700, 700),   // Near Ruins Plaza
                    new Vector2D(3200, 3200), // Near Campsite Settlement
                    new Vector2D(600, 3400)   // Open grassland
            };

            int totalSpawns = 0;
            int waterTileViolations = 0;
            int solidObstacleViolations = 0;
            int worldBoundsViolations = 0;

            for (Vector2D pPos : testPlayerPositions) {
                camera.update(pPos.x, pPos.y, 1.0f);

                for (int i = 0; i < 200; i++) {
                    Enemy enemy = spawner.spawnEnemy(pPos);
                    assertNotNull(enemy);
                    totalSpawns++;

                    float ex = enemy.getX();
                    float ey = enemy.getY();
                    float er = enemy.getRadius();

                    // 1. World bounds check
                    if (ex - er < 0.0f || ex + er > TileMap.WORLD_WIDTH ||
                            ey - er < 0.0f || ey + er > TileMap.WORLD_HEIGHT) {
                        worldBoundsViolations++;
                    }

                    // 2. Water tile check
                    int tx = (int) (ex / TileMap.TILE_SIZE);
                    int ty = (int) (ey / TileMap.TILE_SIZE);
                    if (tileMap.isWater(tx, ty)) {
                        waterTileViolations++;
                    }

                    // 3. Solid obstacle intersection check
                    for (Obstacle obs : tileMap.getObstacles()) {
                        if (obs.isSolid() && obs.getCollisionRadius() > 0.0f) {
                            float totalR = er + obs.getCollisionRadius();
                            Vector2D center = obs.getCollisionCenter();
                            float dx = ex - center.x;
                            float dy = ey - center.y;
                            if ((dx * dx + dy * dy) < (totalR * totalR)) {
                                solidObstacleViolations++;
                            }
                        }
                    }

                    // 4. TileMap continuous traversability check
                    assertTrue(tileMap.isTraversable(ex, ey, er),
                            "Enemy spawned at non-traversable position (" + ex + ", " + ey + ")");
                }
            }

            assertEquals(1200, totalSpawns);
            assertEquals(0, worldBoundsViolations, "Zero enemies should spawn out of world bounds");
            assertEquals(0, waterTileViolations, "Zero enemies should spawn on water tiles");
            assertEquals(0, solidObstacleViolations, "Zero enemies should intersect solid obstacles");
        }

        @Test
        @DisplayName("5.2 Radius awareness: Melee (16), Ranged (15), Tank (26) respect respective clearances")
        public void testAllEnemyArchetypesRespectRadiusClearance() {
            TileMap tileMap = new TileMap(42);
            EnemySpawner spawner = new EnemySpawner(tileMap, new Random(777L));
            Vector2D playerPos = new Vector2D(2048, 2048);

            for (EnemyType type : new EnemyType[]{EnemyType.MELEE, EnemyType.RANGED, EnemyType.TANK}) {
                for (int i = 0; i < 50; i++) {
                    Enemy enemy = spawner.spawnEnemy(playerPos, type);
                    assertEquals(type, enemy.getType());
                    assertTrue(tileMap.isTraversable(enemy.getX(), enemy.getY(), enemy.getRadius()),
                            "Enemy " + type + " (r=" + enemy.getRadius() + ") must be fully traversable");
                }
            }
        }
    }

    // =========================================================================
    // Challenge 6: Match Progression Scaling (0 to 120s and 15 min)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 6: Match Progression Scaling (0 to 120s and 15 min)")
    class ProgressionScalingChallenge {

        @Test
        @DisplayName("6.1 Progression timeline 0s to 120s: interval scales 2.2s -> 0.55s, cap scales 20 -> 36")
        public void testProgressionTimeline0To120s() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> activeList = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048, 2048);

            // Timeline checkpoint values:
            // t = 0s: interval = 2.200s, cap = 20
            assertEquals(2.200f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(20, spawner.getMaxEnemies());

            // Advance to t = 30s (progress = 30/120 = 0.25)
            // interval = 2.2 - 0.25 * (2.2 - 0.55) = 2.2 - 0.4125 = 1.7875
            // cap = 20 + 0.25 * (36 - 20) = 20 + 4 = 24
            spawner.update(30.0f, playerPos, activeList);
            assertEquals(1.7875f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(24, spawner.getMaxEnemies());

            // Advance to t = 60s (progress = 60/120 = 0.50)
            // interval = 2.2 - 0.50 * 1.65 = 1.375
            // cap = 20 + 0.50 * 16 = 28
            spawner.update(30.0f, playerPos, activeList);
            assertEquals(1.375f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(28, spawner.getMaxEnemies());

            // Advance to t = 90s (progress = 90/120 = 0.75)
            // interval = 2.2 - 0.75 * 1.65 = 0.9625
            // cap = 20 + 0.75 * 16 = 32
            spawner.update(30.0f, playerPos, activeList);
            assertEquals(0.9625f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(32, spawner.getMaxEnemies());

            // Advance to t = 120s (progress = 1.0)
            // interval = 0.550
            // cap = 36
            spawner.update(30.0f, playerPos, activeList);
            assertEquals(0.550f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(36, spawner.getMaxEnemies());

            // Beyond 120s: values must remain clamped at their caps
            spawner.update(60.0f, playerPos, activeList);
            assertEquals(0.550f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(36, spawner.getMaxEnemies());
        }

        @Test
        @DisplayName("6.2 Extended 15-minute match scaling with custom duration")
        public void testExtended15MinMatchScaling() {
            EnemySpawner spawner = new EnemySpawner();
            spawner.setScalingDuration(900.0f); // 15 minutes
            List<Enemy> activeList = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048, 2048);

            // t = 0s
            assertEquals(2.2f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(20, spawner.getMaxEnemies());

            // t = 450s (halfway)
            spawner.update(450.0f, playerPos, activeList);
            assertEquals(1.375f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(28, spawner.getMaxEnemies());

            // t = 900s (15 min)
            spawner.update(450.0f, playerPos, activeList);
            assertEquals(0.550f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(36, spawner.getMaxEnemies());
        }

        @Test
        @DisplayName("6.3 Wave composition evolution: early (<25s) has 0% tanks, mid (25-70s) and late (>70s) scale")
        public void testWaveCompositionEvolution() {
            EnemySpawner spawner = new EnemySpawner(null, new Random(42L));
            Vector2D playerPos = new Vector2D(2048, 2048);
            List<Enemy> dummy = new ArrayList<>();

            // Early phase (t = 10s)
            spawner.update(10.0f, playerPos, dummy);
            int meleeEarly = 0, rangedEarly = 0, tankEarly = 0;
            for (int i = 0; i < 100; i++) {
                Enemy e = spawner.spawnEnemy(playerPos);
                if (e.getType() == EnemyType.MELEE) meleeEarly++;
                else if (e.getType() == EnemyType.RANGED) rangedEarly++;
                else if (e.getType() == EnemyType.TANK) tankEarly++;
            }
            assertEquals(0, tankEarly, "Early phase (< 25s) must have ZERO tanks");
            assertTrue(meleeEarly > 50, "Early phase should be predominantly Melee");

            // Mid phase (t = 50s)
            spawner.update(40.0f, playerPos, dummy);
            int tankMid = 0;
            for (int i = 0; i < 100; i++) {
                Enemy e = spawner.spawnEnemy(playerPos);
                if (e.getType() == EnemyType.TANK) tankMid++;
            }
            assertTrue(tankMid > 5 && tankMid < 30, "Mid phase should spawn approximately 15% tanks, was " + tankMid);

            // Late phase (t = 80s)
            spawner.update(30.0f, playerPos, dummy);
            int tankLate = 0;
            for (int i = 0; i < 100; i++) {
                Enemy e = spawner.spawnEnemy(playerPos);
                if (e.getType() == EnemyType.TANK) tankLate++;
            }
            assertTrue(tankLate > 10 && tankLate < 35, "Late phase should spawn approximately 20% tanks, was " + tankLate);
        }

        @Test
        @DisplayName("6.4 Late-game health scaling: +25% max after 50s")
        public void testLateGameHealthScaling() {
            EnemySpawner spawner = new EnemySpawner();
            Vector2D playerPos = new Vector2D(2048, 2048);
            List<Enemy> dummy = new ArrayList<>();

            // At t = 30s (< 50s): no scaling
            spawner.update(30.0f, playerPos, dummy);
            Enemy e1 = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
            assertEquals(30.0f, e1.getMaxHealth(), 1e-4f);

            // At t = 200s: hpScale = 1.0 + clamp((200-50)/600, 0, 0.25) = 1.0 + 0.25 = 1.25
            spawner.update(170.0f, playerPos, dummy);
            Enemy e2 = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
            assertEquals(37.5f, e2.getMaxHealth(), 1e-2f);

            // At t = 1000s: hpScale still clamped at 1.25 (+25%)
            spawner.update(800.0f, playerPos, dummy);
            Enemy e3 = spawner.spawnEnemy(playerPos, EnemyType.MELEE);
            assertEquals(37.5f, e3.getMaxHealth(), 1e-2f);
        }

        @Test
        @DisplayName("6.5 Spawner clean reset: resets all timers, intervals, and caps")
        public void testSpawnerCleanReset() {
            EnemySpawner spawner = new EnemySpawner();
            List<Enemy> dummy = new ArrayList<>();
            Vector2D playerPos = new Vector2D(2048, 2048);

            spawner.update(150.0f, playerPos, dummy);
            assertEquals(0.55f, spawner.getCurrentInterval(), 1e-3f);
            assertEquals(36, spawner.getMaxEnemies());

            spawner.reset();
            assertEquals(0.0f, spawner.getElapsedTime(), 1e-4f);
            assertEquals(1.5f, spawner.getSpawnTimer(), 1e-4f);
            assertEquals(2.2f, spawner.getCurrentInterval(), 1e-4f);
            assertEquals(20, spawner.getMaxEnemies());
        }
    }
}
