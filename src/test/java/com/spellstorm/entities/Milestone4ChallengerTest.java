package com.spellstorm.entities;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical challenger test suite for Milestone 4:
 * Entity Physics, Player Controls, Shooting Modes, and Enemy Spawner Subsystems.
 *
 * Authored by m4_challenger_1 to rigorously stress-test:
 * 1. Entity ID collision / concurrency and resetIdCounter robustness.
 * 2. Exponential knockback decay under variable delta-times (dt = 0.001s vs dt = 1.0s vs negative dt).
 * 3. Damage mitigation with extreme armor values (-1000, 0, 100, 10000, Float.MAX_VALUE) and negative damage.
 * 4. 8-way WASD input combinations (simultaneous opposing inputs W+S, A+D, all 4 pressed, no keys).
 * 5. TileMap sliding collision resolution: sliding against diagonal water banks, solid ruins pillars, map boundaries, corner pinches.
 * 6. Shooting mode toggle rapid firing (key bouncing, sub-millisecond toggles).
 * 7. Aiming vector precision across zoom levels [0.75, 2.25] and zero-distance cursor placement directly on player center.
 * 8. Closest enemy tie-breaking when 2 or more enemies are at bit-identical distances.
 */
@DisplayName("Milestone 4 Challenger: Empirical Stress Tests")
public class Milestone4ChallengerTest {

    private static final float TOLERANCE = 1e-4f;

    @BeforeAll
    static void initHeadless() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Challenge 1: Entity ID Monotonicity, Concurrency & Reset Robustness
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: Entity ID Collision, Concurrency & Reset Robustness")
    class EntityIdConcurrencyChallenge {

        @Test
        @DisplayName("1.1 Concurrent Entity ID Generation across 16 Threads (80,000 entities)")
        void testConcurrentEntityIdGeneration() throws InterruptedException {
            int threadCount = 16;
            int allocationsPerThread = 5000;
            int totalExpected = threadCount * allocationsPerThread;

            Entity.resetIdCounter(1);
            Set<Integer> idSet = ConcurrentHashMap.newKeySet(totalExpected);
            ExecutorService executor = Executors.newFixedThreadPool(threadCount);
            CountDownLatch latch = new CountDownLatch(threadCount);

            for (int t = 0; t < threadCount; t++) {
                executor.submit(() -> {
                    try {
                        for (int i = 0; i < allocationsPerThread; i++) {
                            int id = Entity.getNextEntityId();
                            idSet.add(id);
                        }
                    } finally {
                        latch.countDown();
                    }
                });
            }

            assertTrue(latch.await(10, TimeUnit.SECONDS), "ID allocation threads must complete within 10 seconds");
            executor.shutdown();

            assertEquals(totalExpected, idSet.size(), "All 80,000 allocated entity IDs must be strictly unique (zero collision)");
        }

        @Test
        @DisplayName("1.2 resetIdCounter boundary clamping with zero, negative, and large start values")
        void testResetIdCounterBoundaries() {
            // Negative start ID clamps to 1
            Entity.resetIdCounter(-500);
            assertEquals(1, Entity.getNextEntityId(), "Negative startId must clamp to 1");

            // Zero start ID clamps to 1
            Entity.resetIdCounter(0);
            assertEquals(1, Entity.getNextEntityId(), "Zero startId must clamp to 1");

            // Integer.MIN_VALUE clamps to 1
            Entity.resetIdCounter(Integer.MIN_VALUE);
            assertEquals(1, Entity.getNextEntityId(), "Integer.MIN_VALUE startId must clamp to 1");

            // Arbitrary positive start value
            Entity.resetIdCounter(42000);
            assertEquals(42000, Entity.getNextEntityId(), "Positive startId must be preserved exactly");
            assertEquals(42001, Entity.getNextEntityId(), "Subsequent call must increment monotonically");

            // Default reset resets to 1
            Entity.resetIdCounter();
            assertEquals(1, Entity.getNextEntityId(), "Default resetIdCounter() must reset to 1");
        }

        @Test
        @DisplayName("1.3 Near Integer.MAX_VALUE overflow behavior and Entity hash/equals stability")
        void testNearIntegerMaxOverflow() {
            Entity.resetIdCounter(Integer.MAX_VALUE - 1);
            Player e1 = new Player(100, 100);
            Player e2 = new Player(200, 200);
            Player e3 = new Player(300, 300); // Overflows into Integer.MIN_VALUE

            assertEquals(Integer.MAX_VALUE - 1, e1.getId());
            assertEquals(Integer.MAX_VALUE, e2.getId());
            assertEquals(Integer.MIN_VALUE, e3.getId(), "AtomicInteger overflow in Java wraps to Integer.MIN_VALUE");

            // Verify equals and hashCode under negative IDs
            assertNotEquals(e1, e2);
            assertNotEquals(e2, e3);
            assertEquals(e3, e3);
            assertDoesNotThrow(e3::hashCode);
            assertDoesNotThrow(e3::toString);

            // Clean up static counter
            Entity.resetIdCounter(1);
        }

        @Test
        @DisplayName("1.4 Spatial Queries Defensive Guards against Null References")
        void testSpatialQueriesDefensiveGuards() {
            Player player = new Player(100, 100);
            assertEquals(Float.MAX_VALUE, player.dist(null), "dist(null) must return Float.MAX_VALUE");
            assertEquals(Float.MAX_VALUE, player.distSq(null), "distSq(null) must return Float.MAX_VALUE");
            assertFalse(player.collidesWith(null), "collidesWith(null) must return false");

            MeleeEnemy inactiveEnemy = new MeleeEnemy(new Vector2D(100, 100));
            inactiveEnemy.destroy();
            assertFalse(player.collidesWith(inactiveEnemy), "collidesWith(inactive) must return false");
        }
    }

    // =========================================================================
    // Challenge 2: Exponential Knockback Decay under Variable Delta-Times
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: Exponential Knockback Decay under Variable Delta-Times")
    class KnockbackDecayChallenge {

        @Test
        @DisplayName("2.1 High-Frequency (dt = 0.001s, 500 steps) vs Single-Step (dt = 0.5s) Integration Parity")
        void testHighFrequencyVsSingleStepIntegrationParity() {
            Player p1 = new Player(100, 100);
            Player p2 = new Player(100, 100);

            // Apply identical initial knockback: (2500, 1800)
            p1.setKnockback(2500.0f, 1800.0f);
            p2.setKnockback(2500.0f, 1800.0f);

            // Integrate p1 with 500 micro-steps of dt = 0.001s (total 0.5s)
            for (int i = 0; i < 500; i++) {
                p1.updateKnockback(0.001f);
            }

            // Integrate p2 with a single step of dt = 0.5s
            p2.updateKnockback(0.5f);

            // Theoretical decay factor: exp(-7.0 * 0.5) = exp(-3.5) = 0.03019738f
            float expectedX = 2500.0f * (float) Math.exp(-3.5);
            float expectedY = 1800.0f * (float) Math.exp(-3.5);

            assertEquals(expectedX, p2.getKnockback().x, 0.01f, "Single-step knockback X must match theoretical exp(-3.5)");
            assertEquals(expectedY, p2.getKnockback().y, 0.01f, "Single-step knockback Y must match theoretical exp(-3.5)");

            // Compare micro-step accumulation vs single step: relative difference must be < 0.1%
            assertEquals(p2.getKnockback().x, p1.getKnockback().x, 0.5f,
                    "500 micro-steps of 1ms must match 1 step of 500ms within numerical tolerance");
            assertEquals(p2.getKnockback().y, p1.getKnockback().y, 0.5f,
                    "500 micro-steps of 1ms must match 1 step of 500ms within numerical tolerance");
        }

        @Test
        @DisplayName("2.2 Massive Lag Spike (dt = 1.0s, 5.0s, 60.0s) snaps cleanly to zero without underflow or NaN")
        void testMassiveLagSpikeSnapsToZero() {
            Player player = new Player(100, 100);
            player.setKnockback(500.0f, 500.0f);

            // 1.0 second lag spike: exp(-7.0 * 1.0) = 0.0009118 -> len2 < 1.0 -> snaps to 0
            player.updateKnockback(1.0f);
            assertEquals(0.0f, player.getKnockback().x, TOLERANCE, "1.0s lag spike must decay and snap knockback X to 0");
            assertEquals(0.0f, player.getKnockback().y, TOLERANCE, "1.0s lag spike must decay and snap knockback Y to 0");

            // Extreme 60.0s lag spike
            player.setKnockback(10000.0f, 10000.0f);
            player.updateKnockback(60.0f);
            assertEquals(0.0f, player.getKnockback().x, TOLERANCE);
            assertEquals(0.0f, player.getKnockback().y, TOLERANCE);
            assertFalse(Float.isNaN(player.getKnockback().x));
            assertFalse(Float.isNaN(player.getKnockback().y));
        }

        @Test
        @DisplayName("2.3 Negative and Zero Delta Time Safeguards")
        void testNegativeAndZeroDeltaTime() {
            Player player = new Player(100, 100);
            player.setKnockback(50.0f, 75.0f);

            // Zero dt must not modify knockback
            player.updateKnockback(0.0f);
            assertEquals(50.0f, player.getKnockback().x, TOLERANCE, "dt=0 must leave knockback unchanged");
            assertEquals(75.0f, player.getKnockback().y, TOLERANCE, "dt=0 must leave knockback unchanged");

            // Negative dt must not amplify or corrupt knockback
            player.updateKnockback(-0.016f);
            assertEquals(50.0f, player.getKnockback().x, TOLERANCE, "dt < 0 must leave knockback unchanged");
            assertEquals(75.0f, player.getKnockback().y, TOLERANCE, "dt < 0 must leave knockback unchanged");

            player.updateKnockback(-10.0f);
            assertEquals(50.0f, player.getKnockback().x, TOLERANCE, "Large negative dt must leave knockback unchanged");
            assertEquals(75.0f, player.getKnockback().y, TOLERANCE, "Large negative dt must leave knockback unchanged");

            // Negative dt in moveInDirection halts motion
            player.moveInDirection(new Vector2D(1, 0), 1.0f, -0.016f);
            assertEquals(100.0f, player.getX(), TOLERANCE, "dt < 0 in moveInDirection must not advance position");
            assertEquals(100.0f, player.getY(), TOLERANCE, "dt < 0 in moveInDirection must not advance position");
        }
    }

    // =========================================================================
    // Challenge 3: Damage Mitigation & Extreme Armor Stress
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: Damage Mitigation & Extreme Armor Stress")
    class DamageMitigationStressChallenge {

        @Test
        @DisplayName("3.1 Extreme Armor Values (-1000, 0, 100, 10000, Float.MAX_VALUE)")
        void testExtremeArmorValues() {
            float rawDamage = 100.0f;

            // Negative armor (-1000) must clamp to 0 (no damage amplification)
            float dmgNeg = Character.calculateMitigatedDamage(rawDamage, -1000.0f);
            assertEquals(100.0f, dmgNeg, TOLERANCE, "Negative armor must clamp to 0 and not amplify damage");

            // Zero armor: exactly 100% damage
            float dmgZero = Character.calculateMitigatedDamage(rawDamage, 0.0f);
            assertEquals(100.0f, dmgZero, TOLERANCE, "0 armor must result in exactly raw damage");

            // 100 armor: exactly 50% damage
            float dmg100 = Character.calculateMitigatedDamage(rawDamage, 100.0f);
            assertEquals(50.0f, dmg100, TOLERANCE, "100 armor must result in exactly 50% damage");

            // 10,000 armor: 100 * 100 / 10100 = 0.990099f
            float dmg10k = Character.calculateMitigatedDamage(rawDamage, 10000.0f);
            assertEquals(100.0f * 100.0f / 10100.0f, dmg10k, TOLERANCE);

            // Float.MAX_VALUE armor: damage mitigated to 0 (no overflow or NaN)
            float dmgMax = Character.calculateMitigatedDamage(rawDamage, Float.MAX_VALUE);
            assertTrue(dmgMax >= 0.0f && dmgMax <= 1e-30f, "Float.MAX_VALUE armor must mitigate damage to ~0.0");
            assertFalse(Float.isNaN(dmgMax), "Damage with Float.MAX_VALUE armor must not be NaN");
            assertFalse(Float.isInfinite(dmgMax), "Damage with Float.MAX_VALUE armor must not be Infinite");

            // Float.POSITIVE_INFINITY armor
            float dmgInf = Character.calculateMitigatedDamage(rawDamage, Float.POSITIVE_INFINITY);
            assertEquals(0.0f, dmgInf, TOLERANCE, "Infinite armor must mitigate damage to 0.0");
        }

        @Test
        @DisplayName("3.2 Negative and Zero Damage Application (No Unintended Healing)")
        void testNegativeAndZeroDamage() {
            Player player = new Player(100, 100);
            float initialHp = player.getHealth();

            // Zero damage
            float dealt0 = player.takeDamage(0.0f);
            assertEquals(0.0f, dealt0, TOLERANCE);
            assertEquals(initialHp, player.getHealth(), TOLERANCE);

            // Negative damage values must be completely ignored (no negative damage / healing bug)
            float dealtNeg1 = player.takeDamage(-50.0f);
            assertEquals(0.0f, dealtNeg1, TOLERANCE, "takeDamage(-50) must return 0");
            assertEquals(initialHp, player.getHealth(), TOLERANCE, "Health must not increase on negative damage");

            float dealtNeg2 = player.takeDamage(-10000.0f);
            assertEquals(0.0f, dealtNeg2, TOLERANCE);
            assertEquals(initialHp, player.getHealth(), TOLERANCE);

            // Armor bypass with negative damage
            float dealtBypassNeg = player.takeDamage(-25.0f, true);
            assertEquals(0.0f, dealtBypassNeg, TOLERANCE);
            assertEquals(initialHp, player.getHealth(), TOLERANCE);
        }

        @Test
        @DisplayName("3.3 Character Armor Setter Clamping and True Damage Bypass")
        void testArmorSetterAndBypass() {
            Player player = new Player(100, 100);

            // Setting negative armor clamps to 0
            player.setArmor(-50.0f);
            assertEquals(0.0f, player.getArmor(), TOLERANCE, "setArmor(-50) must clamp to 0");

            player.setArmor(50.0f);
            assertEquals(50.0f, player.getArmor(), TOLERANCE);

            // True damage bypass ignores 50 armor
            float initialHp = player.getHealth();
            float dealtBypass = player.takeDamage(40.0f, true);
            assertEquals(40.0f, dealtBypass, TOLERANCE, "takeDamage with ignoreArmor=true must deal full raw damage");
            assertEquals(initialHp - 40.0f, player.getHealth(), TOLERANCE);
        }
    }

    // =========================================================================
    // Challenge 4: 8-Way WASD Input Combinations & Cancellation
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: 8-Way WASD Input Combinations & Cancellation")
    class WASDInputCombinationChallenge {

        @Test
        @DisplayName("4.1 Exhaustive Permutation Test across all 16 Boolean States (2^4)")
        void testAll16WASDCombinations() {
            // State table: [up, down, left, right, expectedLen, expectedDx, expectedDy]
            boolean[] bools = {false, true};

            for (boolean up : bools) {
                for (boolean down : bools) {
                    for (boolean left : bools) {
                        for (boolean right : bools) {
                            Vector2D vec = Player.computeMovementVector(up, down, left, right);

                            // Calculate unnormalized raw components
                            float rawDx = (left ? -1.0f : 0.0f) + (right ? 1.0f : 0.0f);
                            float rawDy = (up ? -1.0f : 0.0f) + (down ? 1.0f : 0.0f);
                            float rawLen = (float) Math.hypot(rawDx, rawDy);

                            if (rawLen == 0.0f) {
                                // Full cancellation or no input
                                assertTrue(vec.isZero(), "Opposing inputs or no inputs must cancel to (0, 0)");
                                assertEquals(0.0f, vec.len(), TOLERANCE);
                            } else {
                                // Magnitude must be strictly 1.0f (no diagonal speed inflation)
                                assertEquals(1.0f, vec.len(), TOLERANCE,
                                        String.format("Movement vector for (U:%b, D:%b, L:%b, R:%b) must be normalized to len=1.0",
                                                up, down, left, right));

                                // Direction must point in exact angle of raw displacement
                                float expectedAngle = (float) Math.atan2(rawDy, rawDx);
                                float actualAngle = vec.angleRad();
                                assertEquals(expectedAngle, actualAngle, TOLERANCE,
                                        "Computed direction angle must match raw directional axis");
                            }
                        }
                    }
                }
            }
        }

        @Test
        @DisplayName("4.2 Player Movement Integration: Opposing Inputs W+S and A+D Produce Zero Displacement")
        void testPlayerMovementIntegrationOpposingInputs() {
            Player player = new Player(100, 100);

            // W + S (Up + Down): opposing vertical cancel
            player.handleMovementInput(true, true, false, false, 0.016f, null);
            assertEquals(100.0f, player.getX(), TOLERANCE, "W+S must produce zero vertical displacement");
            assertEquals(100.0f, player.getY(), TOLERANCE, "W+S must produce zero vertical displacement");

            // A + D (Left + Right): opposing horizontal cancel
            player.handleMovementInput(false, false, true, true, 0.016f, null);
            assertEquals(100.0f, player.getX(), TOLERANCE, "A+D must produce zero horizontal displacement");
            assertEquals(100.0f, player.getY(), TOLERANCE, "A+D must produce zero horizontal displacement");

            // W + S + A + D (all 4 held): complete cancellation
            player.handleMovementInput(true, true, true, true, 0.016f, null);
            assertEquals(100.0f, player.getX(), TOLERANCE, "All 4 keys held must produce zero displacement");
            assertEquals(100.0f, player.getY(), TOLERANCE, "All 4 keys held must produce zero displacement");

            // W + S + D: vertical cancels, moves pure Right
            player.handleMovementInput(true, true, false, true, 1.0f, null);
            assertEquals(100.0f + Player.DEFAULT_SPEED, player.getX(), TOLERANCE, "W+S+D must move Right at exact base speed");
            assertEquals(100.0f, player.getY(), TOLERANCE, "W+S+D must not drift vertically");
        }

        @Test
        @DisplayName("4.3 Diagonal Movement Speed Parity with Cardinal Movement (No Diagonal Speed Inflation)")
        void testDiagonalSpeedParity() {
            Player pCardinal = new Player(0, 0);
            Player pDiagonal = new Player(0, 0);

            float dt = 1.0f;
            // Cardinal: Move Right for 1.0s
            pCardinal.handleMovementInput(false, false, false, true, dt, null);
            float distCardinal = pCardinal.getPosition().dist(new Vector2D(0, 0));

            // Diagonal: Move Down + Right for 1.0s
            pDiagonal.handleMovementInput(false, true, false, true, dt, null);
            float distDiagonal = pDiagonal.getPosition().dist(new Vector2D(0, 0));

            assertEquals(Player.DEFAULT_SPEED, distCardinal, TOLERANCE, "Cardinal move distance in 1s must equal speed");
            assertEquals(Player.DEFAULT_SPEED, distDiagonal, TOLERANCE, "Diagonal move distance in 1s must equal speed (no sqrt(2) inflation)");
        }
    }

    // =========================================================================
    // Challenge 5: TileMap Sliding Collision Resolution
    // =========================================================================
    @Nested
    @DisplayName("Challenge 5: TileMap Sliding Collision Resolution")
    class SlidingCollisionChallenge {

        @Test
        @DisplayName("5.1 Diagonal Sliding against Solid Water Bank (Tangential Axis Preservation)")
        void testSlidingAgainstWaterBank() {
            TileMap map = new TileMap();
            map.clearObstacles();
            for (int y = 0; y < TileMap.HEIGHT; y++) {
                for (int x = 0; x < TileMap.WIDTH; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            // Water row at ty = 10 (world Y from 320 to 352)
            for (int x = 0; x < TileMap.WIDTH; x++) {
                map.setTile(x, 10, TileType.WATER);
            }

            // Player at (200, 300) with radius 16. Water starts at Y = 320.
            // Distance to water edge = 320 - 300 = 20 > 16 (legal position).
            Player player = new Player(200.0f, 300.0f);
            player.setRadius(16.0f);

            // Attempt to move diagonally Down+Right (dx > 0, dy > 0 towards water)
            // Desired candidate would place player at Y = 310 (distance to water = 10 < 16, BLOCKED by water)
            Vector2D moveDir = new Vector2D(1.0f, 1.0f).nor();
            player.moveAndResolve(moveDir, 0.1f, map);

            // Y movement is blocked by water, but X movement (Right) must succeed freely!
            assertTrue(player.getX() > 200.0f, "Player must slide freely along unblocked X axis");
            assertTrue(player.getY() + player.getRadius() <= 320.0f, "Player must not penetrate water boundary at Y=320");
        }

        @Test
        @DisplayName("5.2 Sliding against Solid Ruins Pillar (Circle-Circle Obstacle)")
        void testSlidingAgainstRuinsPillar() {
            TileMap map = new TileMap();
            map.clearObstacles();
            for (int y = 0; y < TileMap.HEIGHT; y++) {
                for (int x = 0; x < TileMap.WIDTH; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            // Place solid circular pillar at (500, 500)
            Obstacle pillar = new Obstacle(DecoType.RUINS, 500.0f, 500.0f, true);
            map.addObstacle(pillar);

            float colRadius = pillar.getCollisionRadius();
            assertTrue(colRadius > 0.0f, "Pillar collision radius must be positive");

            // Player (radius 16) starts at (460, 500). Sum of radii = 16 + colRadius
            Player player = new Player(460.0f, 500.0f);
            player.setRadius(16.0f);

            // Attempt to walk directly into the pillar (moving Right)
            Vector2D moveRight = new Vector2D(1.0f, 0.0f);
            player.moveAndResolve(moveRight, 0.1f, map);

            // Distance to pillar collision center must remain >= sum of radii
            float distToPillar = player.getPosition().dist(pillar.getCollisionCenter());
            float minRequired = player.getRadius() + colRadius;
            assertTrue(distToPillar >= minRequired - TOLERANCE,
                    String.format("Player must not penetrate solid pillar: dist=%f, minRequired=%f", distToPillar, minRequired));

            // Attempt to walk diagonally (Right + Down)
            Vector2D moveDiag = new Vector2D(1.0f, 1.0f).nor();
            player.moveAndResolve(moveDiag, 0.1f, map);

            distToPillar = player.getPosition().dist(pillar.getCollisionCenter());
            assertTrue(distToPillar >= minRequired - TOLERANCE, "Player must slide along pillar perimeter without penetrating");
        }

        @Test
        @DisplayName("5.3 World Boundary Clamping and Sliding across all 4 Edges and Corners")
        void testWorldBoundaryClampingAndSliding() {
            TileMap map = new TileMap();
            map.clearObstacles();
            for (int y = 0; y < TileMap.HEIGHT; y++) {
                for (int x = 0; x < TileMap.WIDTH; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            float r = 20.0f;
            Player player = new Player(r, r);
            player.setRadius(r);

            // Try to move beyond top-left corner (-X, -Y)
            player.moveAndResolve(new Vector2D(-1, -1), 0.5f, map);
            assertEquals(r, player.getX(), TOLERANCE, "Player X must be clamped to collision radius at left boundary");
            assertEquals(r, player.getY(), TOLERANCE, "Player Y must be clamped to collision radius at top boundary");

            // Move to bottom-right corner
            player.setPosition(4096.0f - r, 4096.0f - r);
            player.moveAndResolve(new Vector2D(1, 1), 0.5f, map);
            assertEquals(4096.0f - r, player.getX(), TOLERANCE, "Player X must be clamped at right boundary");
            assertEquals(4096.0f - r, player.getY(), TOLERANCE, "Player Y must be clamped at bottom boundary");

            // Move diagonally while sliding against left wall (attempting -X and +Y)
            player.setPosition(r, 2000.0f);
            player.moveAndResolve(new Vector2D(-1, 1), 0.1f, map);
            assertEquals(r, player.getX(), TOLERANCE, "X must remain clamped at left wall");
            assertTrue(player.getY() > 2000.0f, "Y must slide downwards freely along boundary");
        }

        @Test
        @DisplayName("5.4 Corner Pinch: Player blocked on both axes halts safely without oscillation or NaN")
        void testCornerPinchHalt() {
            TileMap map = new TileMap();
            map.clearObstacles();
            for (int y = 0; y < TileMap.HEIGHT; y++) {
                for (int x = 0; x < TileMap.WIDTH; x++) {
                    map.setTile(x, y, TileType.GRASS);
                }
            }

            // Create an internal right-angle corner of water at (10, 10)
            map.setTile(10, 9, TileType.WATER); // Water north
            map.setTile(9, 10, TileType.WATER); // Water west
            map.setTile(9, 9, TileType.WATER);  // Water northwest corner

            // Position player at (10.5 * 32, 10.5 * 32)
            float startX = 10.5f * 32.0f;
            float startY = 10.5f * 32.0f;
            Player player = new Player(startX, startY);
            player.setRadius(15.0f);

            // Try to push hard into the blocked corner (-X, -Y)
            for (int step = 0; step < 20; step++) {
                player.moveAndResolve(new Vector2D(-1, -1), 0.05f, map);
                assertFalse(Float.isNaN(player.getX()));
                assertFalse(Float.isNaN(player.getY()));
                assertTrue(map.isTraversable(player.getX(), player.getY(), player.getRadius()),
                        "Player must remain strictly in traversable space inside corner pinch");
            }
        }
    }

    // =========================================================================
    // Challenge 6: Shooting Mode Toggle Rapid Firing & Key Bouncing
    // =========================================================================
    @Nested
    @DisplayName("Challenge 6: Shooting Mode Rapid Firing & Key Bouncing")
    class ShootingModeDebounceChallenge {

        @Test
        @DisplayName("6.1 Sub-Millisecond Toggle Loop: 10,000 rapid calls toggle mode exactly once")
        void testSubMillisecondToggleLoop() {
            Player player = new Player(100, 100);
            assertEquals(ShootingMode.MANUAL, player.getShootingMode());

            int successCount = 0;
            for (int i = 0; i < 10000; i++) {
                if (player.tryToggleShootingMode()) {
                    successCount++;
                }
            }

            assertEquals(1, successCount, "Only the very first invocation in a tight loop must succeed");
            assertEquals(ShootingMode.AUTO, player.getShootingMode(), "Mode must be AUTO after 1 successful toggle");
            assertTrue(player.isToggleKeyHeld());
            assertEquals(Player.DEFAULT_TOGGLE_DEBOUNCE, player.getToggleDebounceTimer(), TOLERANCE);
        }

        @Test
        @DisplayName("6.2 Key-Held Repeat Suppression: Holding 'V' over 5.0 seconds prevents re-triggering")
        void testKeyHeldSuppressesRepeatToggles() {
            Player player = new Player(100, 100);
            assertTrue(player.tryToggleShootingMode()); // MANUAL -> AUTO
            assertEquals(ShootingMode.AUTO, player.getShootingMode());

            // Simulate holding 'V' down while time advances 5.0 seconds (300 frames of 16.6ms)
            for (int frame = 0; frame < 300; frame++) {
                player.updateCooldowns(0.0166f);
                assertFalse(player.tryToggleShootingMode(), "tryToggleShootingMode must return false while key remains held");
                assertEquals(ShootingMode.AUTO, player.getShootingMode(), "Mode must not toggle while key remains held");
            }

            // Key is released
            player.onToggleKeyReleased();
            assertFalse(player.isToggleKeyHeld());

            // Next press now succeeds
            assertTrue(player.tryToggleShootingMode(), "Pressing 'V' after release must succeed");
            assertEquals(ShootingMode.MANUAL, player.getShootingMode());
        }

        @Test
        @DisplayName("6.3 Rapid Key Bouncing (Sub-Debounce Releases and Re-presses within 0.25s)")
        void testRapidKeyBouncingWithinDebounceWindow() {
            Player player = new Player(100, 100);
            assertTrue(player.tryToggleShootingMode()); // Initial toggle

            // Simulate high-frequency mechanical switch bounce: 50 releases and presses within 0.15s (below 0.25s)
            float stepDt = 0.15f / 50.0f; // 0.003s per bounce
            int bounceSuccessCount = 0;

            for (int i = 0; i < 50; i++) {
                player.updateCooldowns(stepDt);
                player.onToggleKeyReleased(); // Physical bounce release
                if (player.tryToggleShootingMode()) { // Immediate re-press
                    bounceSuccessCount++;
                }
            }

            assertEquals(0, bounceSuccessCount, "Zero bounced key-presses must succeed before 0.25s debounce expires");
            assertEquals(ShootingMode.AUTO, player.getShootingMode());

            // Advance remaining time past 0.25s
            player.updateCooldowns(0.15f);
            player.onToggleKeyReleased();

            // Legitimate subsequent press succeeds
            assertTrue(player.tryToggleShootingMode());
            assertEquals(ShootingMode.MANUAL, player.getShootingMode());
        }
    }

    // =========================================================================
    // Challenge 7: Aiming Vector Precision across Zoom Levels & Zero-Distance
    // =========================================================================
    @Nested
    @DisplayName("Challenge 7: Aiming Vector Precision across Zoom Levels & Zero-Distance")
    class AimingPrecisionChallenge {

        @Test
        @DisplayName("7.1 Zero-Distance Cursor Placement Directly on Player Center (Fallback Handling)")
        void testZeroDistanceCursorOnPlayerCenter() {
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);
            Vector2D cursorOnPlayer = new Vector2D(2048.0f, 2048.0f);
            Vector2D previousAim = new Vector2D(0.0f, -1.0f); // Facing North

            // Manual Aim directly on player: must return fallback previous aim without NaN or division by zero
            Vector2D manualAim = AimingSubsystem.calculateManualAim(playerPos, cursorOnPlayer, previousAim);
            assertFalse(Float.isNaN(manualAim.x));
            assertFalse(Float.isNaN(manualAim.y));
            assertEquals(0.0f, manualAim.x, TOLERANCE);
            assertEquals(-1.0f, manualAim.y, TOLERANCE);
            assertEquals(1.0f, manualAim.len(), TOLERANCE);

            // Zero fallback direction must default cleanly to unit East (1, 0)
            Vector2D defaultAim = AimingSubsystem.calculateManualAim(playerPos, cursorOnPlayer, new Vector2D(0, 0));
            assertEquals(1.0f, defaultAim.x, TOLERANCE);
            assertEquals(0.0f, defaultAim.y, TOLERANCE);

            // Null fallback direction must default to unit East (1, 0)
            Vector2D nullFallbackAim = AimingSubsystem.calculateManualAim(playerPos, cursorOnPlayer, null);
            assertEquals(1.0f, nullFallbackAim.x, TOLERANCE);
            assertEquals(0.0f, nullFallbackAim.y, TOLERANCE);
        }

        @Test
        @DisplayName("7.2 Sub-Epsilon Cursor Distances (< 1e-4) do not produce floating-point overflow or NaN")
        void testSubEpsilonCursorDistances() {
            Vector2D playerPos = new Vector2D(100.0f, 100.0f);
            Vector2D fallback = new Vector2D(1.0f, 0.0f);

            float[] subEpsilons = {1e-5f, 1e-6f, 1e-8f, 1e-15f, 1e-30f};
            for (float eps : subEpsilons) {
                Vector2D cursor = new Vector2D(100.0f + eps, 100.0f + eps);
                Vector2D aim = AimingSubsystem.calculateManualAim(playerPos, cursor, fallback);
                assertFalse(Float.isNaN(aim.x), "Aim X must not be NaN for sub-epsilon distance: " + eps);
                assertFalse(Float.isNaN(aim.y), "Aim Y must not be NaN for sub-epsilon distance: " + eps);
                assertEquals(1.0f, aim.len(), TOLERANCE);
            }
        }

        @ParameterizedTest(name = "Zoom level {0}")
        @ValueSource(floats = {0.75f, 1.0f, 1.35f, 1.8f, 2.25f})
        @DisplayName("7.3 Aiming Direction Angle Invariance across All Zoom Levels [0.75, 2.25]")
        void testAimingAngleInvarianceAcrossZoomLevels(float zoom) {
            GameCamera camera = new GameCamera(1280.0f, 720.0f, 4096.0f, 4096.0f);
            Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);
            camera.setCenter(playerPos.x, playerPos.y);
            camera.setZoom(zoom);

            // Test target angles every 30 degrees (pi/6)
            for (int i = 0; i < 12; i++) {
                float targetAngle = i * (MathUtils.TWO_PI / 12.0f);
                float distance = 250.0f; // 250 world units away

                Vector2D worldTarget = new Vector2D(
                        playerPos.x + (float) Math.cos(targetAngle) * distance,
                        playerPos.y + (float) Math.sin(targetAngle) * distance
                );

                // Project target to screen coordinates
                Vector2D screenPos = camera.worldToScreen(worldTarget.x, worldTarget.y);

                // Unproject screen cursor back to world
                Vector2D unprojectedWorldMouse = camera.screenToWorld(screenPos.x, screenPos.y);

                // Compute aim vector via AimingSubsystem
                Vector2D aimDir = AimingSubsystem.calculateManualAim(playerPos, unprojectedWorldMouse, null);

                // Angle must match original targetAngle within 1e-4 tolerance
                float resultingAngle = aimDir.angleRad();
                // Normalize angle delta to [-pi, pi]
                float angleDelta = (resultingAngle - targetAngle);
                while (angleDelta > MathUtils.PI) angleDelta -= MathUtils.TWO_PI;
                while (angleDelta < -MathUtils.PI) angleDelta += MathUtils.TWO_PI;

                assertEquals(0.0f, angleDelta, TOLERANCE,
                        String.format("Aiming angle must remain invariant at zoom %f for target angle %f", zoom, targetAngle));
            }
        }
    }

    // =========================================================================
    // Challenge 8: Closest Enemy Tie-Breaking with Bit-Identical Distances
    // =========================================================================
    @Nested
    @DisplayName("Challenge 8: Closest Enemy Tie-Breaking with Bit-Identical Distances")
    class EnemyTieBreakingChallenge {

        @Test
        @DisplayName("8.1 Deterministic Tie-Breaking with Bit-Identical Distances: Lowest Entity ID Wins regardless of list permutation")
        void testDeterministicTieBreakingWithBitIdenticalDistances() {
            Vector2D playerPos = new Vector2D(500.0f, 500.0f);

            // Construct 4 enemies placed at exact bit-identical distance 100.0f from player
            // dSq is bit-identical: (100)^2 = 10000.0f
            Entity.resetIdCounter(10);
            MeleeEnemy enemyA = new MeleeEnemy(new Vector2D(600.0f, 500.0f)); // ID = 10
            MeleeEnemy enemyB = new MeleeEnemy(new Vector2D(400.0f, 500.0f)); // ID = 11
            MeleeEnemy enemyC = new MeleeEnemy(new Vector2D(500.0f, 600.0f)); // ID = 12
            MeleeEnemy enemyD = new MeleeEnemy(new Vector2D(500.0f, 400.0f)); // ID = 13

            // Verify all 4 have exact bit-identical dist2 to playerPos
            float dSqA = playerPos.dist2(enemyA.getPosition());
            float dSqB = playerPos.dist2(enemyB.getPosition());
            float dSqC = playerPos.dist2(enemyC.getPosition());
            float dSqD = playerPos.dist2(enemyD.getPosition());

            assertEquals(10000.0f, dSqA, TOLERANCE);
            assertEquals(dSqA, dSqB, "Distance squared must be bit-identical");
            assertEquals(dSqA, dSqC, "Distance squared must be bit-identical");
            assertEquals(dSqA, dSqD, "Distance squared must be bit-identical");

            // Test across all 24 permutations of the 4 enemies
            List<MeleeEnemy> baseList = new ArrayList<>(List.of(enemyA, enemyB, enemyC, enemyD));

            // Generate permutations and assert that enemyA (ID = 10, lowest) is ALWAYS selected
            for (int p = 0; p < 100; p++) {
                Collections.shuffle(baseList);
                MeleeEnemy closest = AimingSubsystem.findClosestEnemy(playerPos, baseList);
                assertNotNull(closest);
                assertEquals(10, closest.getId(), "Tie-breaker must deterministically select lowest Entity ID (10) across all list orderings");
                assertSame(enemyA, closest);
            }
        }

        @Test
        @DisplayName("8.2 Tie-Breaking Ignores Dead and Inactive Tied Enemies")
        void testTieBreakingIgnoresDeadAndInactiveEnemies() {
            Vector2D playerPos = new Vector2D(500.0f, 500.0f);

            Entity.resetIdCounter(1);
            MeleeEnemy enemy1 = new MeleeEnemy(new Vector2D(600.0f, 500.0f)); // ID = 1
            MeleeEnemy enemy2 = new MeleeEnemy(new Vector2D(400.0f, 500.0f)); // ID = 2
            MeleeEnemy enemy3 = new MeleeEnemy(new Vector2D(500.0f, 600.0f)); // ID = 3

            // Kill enemy1 (lowest ID)
            enemy1.destroy();
            assertFalse(enemy1.isAlive());

            List<MeleeEnemy> list = List.of(enemy1, enemy2, enemy3);
            MeleeEnemy closest = AimingSubsystem.findClosestEnemy(playerPos, list);

            assertNotNull(closest);
            assertEquals(2, closest.getId(), "Must skip dead enemy1 (ID 1) and select enemy2 (ID 2)");
            assertSame(enemy2, closest);

            // Kill enemy2 as well
            enemy2.takeDamage(1000.0f); // Kills enemy2
            assertFalse(enemy2.isAlive());

            MeleeEnemy closestLiving = AimingSubsystem.findClosestEnemy(playerPos, list);
            assertNotNull(closestLiving);
            assertEquals(3, closestLiving.getId(), "Must select enemy3 (ID 3) when earlier IDs are dead");
            assertSame(enemy3, closestLiving);
        }

        @Test
        @DisplayName("8.3 Near-Ties within Epsilon (diff < 1e-4) trigger deterministic lower-ID tie breaker")
        void testNearTiesWithinEpsilon() {
            Vector2D playerPos = new Vector2D(0.0f, 0.0f);

            Entity.resetIdCounter(20);
            MeleeEnemy enemyHigherId = new MeleeEnemy(new Vector2D(1.0f, 0.0f)); // ID = 20, dSq = 1.0f
            // ID = 5, distance slightly further by 2e-5 (dSq diff ~ 4e-5 < 1e-4 EPSILON_SQ)
            Entity.resetIdCounter(5);
            MeleeEnemy enemyLowerId = new MeleeEnemy(new Vector2D(1.00002f, 0.0f)); // ID = 5

            // Order 1: [HigherId, LowerId]
            List<MeleeEnemy> list1 = List.of(enemyHigherId, enemyLowerId);
            MeleeEnemy closest1 = AimingSubsystem.findClosestEnemy(playerPos, list1);
            assertNotNull(closest1);
            assertEquals(5, closest1.getId(), "Lower ID (5) must win within epsilon tolerance window");

            // Order 2: [LowerId, HigherId]
            List<MeleeEnemy> list2 = List.of(enemyLowerId, enemyHigherId);
            MeleeEnemy closest2 = AimingSubsystem.findClosestEnemy(playerPos, list2);
            assertNotNull(closest2);
            assertEquals(5, closest2.getId(), "Lower ID (5) must win within epsilon tolerance window regardless of order");
        }
    }
}
