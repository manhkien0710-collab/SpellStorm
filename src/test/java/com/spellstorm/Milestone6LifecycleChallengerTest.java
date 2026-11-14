package com.spellstorm;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.entities.WorldPowerUp;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeType;
import com.spellstorm.ui.LevelUpOverlay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 6 Lifecycle Empirical Adversarial Challenger Test Suite:
 * Rigorously stress-tests:
 * 1. Power-up active 5.0s lifecycle under variable dt (tiny dt=0.001s x 5000, large dt=6.0s),
 *    verifying exact expiration, monotonic progression, and clean attribute reset.
 * 2. Same-type collection refreshing active time back to exactly 5.0s regardless of remaining time
 *    (e.g., at remaining=0.1s, 4.9s, 0.01s, 2.5s) without multiplier stacking.
 * 3. Different-type collection immediately replacing type and resetting timer to 5.0s without
 *    modifier cross-contamination.
 * 4. Modal pause suspension: 100 seconds in LEVEL_UP or PAUSED state, asserting active power-up
 *    duration and ground pickups have zero timer decrement.
 * 5. Two-step selection in LevelUpOverlay: clicking card does NOT commit; clicking different cards
 *    switches selection; confirm button or Enter commits; grace timer (0.15s) strictly rejects inputs.
 * 6. Text description wrapping: stress tests wrapText with long strings, words exceeding 244px,
 *    empty strings, newline characters, ensuring no wrapped line exceeds 244px.
 * 7. Deterministic restart test: 20 consecutive cycles of restartMatch() verifying player HP=250,
 *    armor=30, speed=230, CD=0.28, mults=1.0, bonus pierce=0, active entities=0, timers=0.
 */
@DisplayName("Milestone 6 Challenger: Empirical Lifecycle, Modal Pause & Overlay Stress Tests")
public class Milestone6LifecycleChallengerTest {

    private static final float EPSILON = 1e-4f;
    private static final float LOOSE_EPSILON = 0.02f;

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    private static List<UpgradeInfo> createSampleUpgradeOptions() {
        List<UpgradeInfo> options = new ArrayList<>();
        options.add(new UpgradeInfo(UpgradeType.MAX_HEALTH, "Vitality Surge", "+40 Max Health & instant heal", 0, 5, Color.RED));
        options.add(new UpgradeInfo(UpgradeType.MOVE_SPEED, "Fleet Footwork", "+12% Movement Speed", 0, 5, Color.GREEN));
        options.add(new UpgradeInfo(UpgradeType.DAMAGE, "Arcane Might", "+20% Spell Damage", 0, 5, Color.ORANGE));
        return options;
    }

    // =========================================================================
    // Challenge 1: Power-Up Active 5.0s Lifecycle under Variable dt
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: Power-Up Active 5.0s Lifecycle & Variable dt Stress")
    class PowerUpLifecycleVariableDtTests {

        @Test
        @DisplayName("1.1 Tiny dt Progression (5000 x 0.001s): Exact Monotonic Countdown & Expiration")
        public void testTinyDtProgression5000Steps() {
            ActiveTemporaryAbility ability = new ActiveTemporaryAbility();
            ability.activate(PowerUpType.ARCANE_OVERDRIVE);

            assertTrue(ability.isActive());
            assertEquals(5.0f, ability.getRemainingDuration(), EPSILON);
            assertEquals(1.0f, ability.getDurationRatio(), EPSILON);

            final float dt = 0.001f;
            float prevRemaining = ability.getRemainingDuration();

            // Run 2500 micro-steps -> 2.5s elapsed
            for (int i = 0; i < 2500; i++) {
                ability.update(dt);
                assertTrue(ability.isActive(), "Ability must remain active at step " + i);
                float curr = ability.getRemainingDuration();
                assertTrue(curr <= prevRemaining + 1e-6f, "Duration must monotonically decrease");
                prevRemaining = curr;
            }
            assertEquals(2.5f, ability.getRemainingDuration(), LOOSE_EPSILON);
            assertEquals(0.5f, ability.getDurationRatio(), LOOSE_EPSILON);

            // Run another 2490 micro-steps -> 4.990s elapsed
            for (int i = 2500; i < 4990; i++) {
                ability.update(dt);
                assertTrue(ability.isActive(), "Ability must remain active at step " + i);
            }
            assertTrue(ability.isActive(), "Ability must still be active at 4.990s");
            assertTrue(ability.getRemainingDuration() > 0.0f, "Remaining duration must be strictly positive");
            assertTrue(ability.getRemainingDuration() <= 0.02f, "Remaining duration must be near 0.01s");

            // Run the remaining micro-steps to hit and cross 5.000s
            for (int i = 4990; i < 5010; i++) {
                ability.update(dt);
            }
            assertFalse(ability.isActive(), "Ability must expire after crossing 5.0s");
            assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);
            assertEquals(0.0f, ability.getDurationRatio(), EPSILON);

            // Additional micro-steps maintain zero state
            for (int i = 0; i < 500; i++) {
                ability.update(dt);
                assertFalse(ability.isActive());
                assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);
            }
        }

        @Test
        @DisplayName("1.2 Large dt Clamping (dt=6.0s & dt=100.0s): Immediate Clamping to Exactly 0.0s")
        public void testLargeDtClamping() {
            ActiveTemporaryAbility ability = new ActiveTemporaryAbility();

            // Single step dt = 6.0s
            ability.activate(PowerUpType.PIERCING_SURGE);
            assertTrue(ability.isActive());
            ability.update(6.0f);
            assertFalse(ability.isActive());
            assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);
            assertEquals(0.0f, ability.getDurationRatio(), EPSILON);

            // Single step dt = 100.0s
            ability.activate(PowerUpType.ELEMENTAL_BURST);
            assertTrue(ability.isActive());
            ability.update(100.0f);
            assertFalse(ability.isActive());
            assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);
            assertEquals(0.0f, ability.getDurationRatio(), EPSILON);
        }

        @Test
        @DisplayName("1.3 Variable Non-Uniform dt Sequence: Clamping, Ratio Bounds [0.0, 1.0]")
        public void testVariableNonUniformDtSequence() {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500f, 500f));
            ActiveTemporaryAbility ability = manager.getActiveAbility();

            ability.activate(PowerUpType.ARCANE_OVERDRIVE);

            float[] dts = {0.016f, 0.033f, 0.125f, 0.5f, 0.005f, 0.89f, 1.25f, 0.31f, 1.0f, 0.4f, 0.6f};
            float totalElapsed = 0.0f;

            for (float dt : dts) {
                totalElapsed += dt;
                manager.update(dt, player, false);

                float ratio = ability.getDurationRatio();
                assertTrue(ratio >= 0.0f && ratio <= 1.0f, "Duration ratio must strictly clamp between 0 and 1");

                if (totalElapsed < 5.0f) {
                    assertTrue(ability.isActive(), "Must be active when totalElapsed=" + totalElapsed + " < 5.0");
                    assertEquals(5.0f - totalElapsed, ability.getRemainingDuration(), LOOSE_EPSILON);
                } else {
                    assertFalse(ability.isActive(), "Must be inactive when totalElapsed=" + totalElapsed + " >= 5.0");
                    assertEquals(0.0f, ability.getRemainingDuration(), EPSILON);
                }
            }
        }

        @Test
        @DisplayName("1.4 Zero and Negative dt Resilience")
        public void testZeroAndNegativeDtResilience() {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500f, 500f));

            manager.getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
            float before = manager.getActiveAbility().getRemainingDuration();
            assertEquals(5.0f, before, EPSILON);

            // dt = 0.0f must have zero effect
            manager.update(0.0f, player, false);
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON);

            // Negative dt must be safely rejected
            manager.update(-0.5f, player, false);
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON);
        }

        @ParameterizedTest
        @EnumSource(PowerUpType.class)
        @DisplayName("1.5 Clean Attribute & Combat Modifier Reset on Expiration for All PowerUpTypes")
        public void testAttributeResetOnExpiration(PowerUpType type) {
            PowerUpManager manager = new PowerUpManager();

            // Verify clean baseline
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
            assertEquals(0, manager.getBonusPierce());
            assertFalse(manager.isElementalBurstActive());

            // Activate ability
            manager.getActiveAbility().activate(type);
            assertTrue(manager.hasActiveAbility());

            // Check active modifier
            switch (type) {
                case ARCANE_OVERDRIVE -> {
                    assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), EPSILON);
                    assertEquals(0, manager.getBonusPierce());
                    assertFalse(manager.isElementalBurstActive());
                }
                case PIERCING_SURGE -> {
                    assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
                    assertEquals(2, manager.getBonusPierce());
                    assertFalse(manager.isElementalBurstActive());
                }
                case ELEMENTAL_BURST -> {
                    assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
                    assertEquals(0, manager.getBonusPierce());
                    assertTrue(manager.isElementalBurstActive());
                }
            }

            // Advance time to expire ability
            manager.getActiveAbility().update(5.01f);
            assertFalse(manager.hasActiveAbility());

            // Verify clean restoration
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON, "Cooldown mult must revert to 1.0");
            assertEquals(0, manager.getBonusPierce(), "Bonus pierce must revert to 0");
            assertFalse(manager.isElementalBurstActive(), "Elemental burst must revert to false");
        }
    }

    // =========================================================================
    // Challenge 2: Same-Type Collection Refreshing to Exactly 5.0s
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: Same-Type Collection Refresh Back to Exactly 5.0s")
    class SameTypeRecollectionTests {

        @ParameterizedTest
        @ValueSource(floats = {0.01f, 0.1f, 1.0f, 2.5f, 4.0f, 4.9f, 4.99f})
        @DisplayName("2.1 Same-Type Refresh across Varied Remaining Durations [0.01s to 4.99s]")
        public void testSameTypeRefreshAtVariedRemainingTimes(float targetRemaining) {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500f, 500f));
            player.setPickupRadius(45.0f);

            for (PowerUpType type : PowerUpType.values()) {
                manager.getActiveAbility().activate(type);

                // Simulate down to targetRemaining
                float dtToElapse = 5.0f - targetRemaining;
                manager.getActiveAbility().update(dtToElapse);
                assertEquals(targetRemaining, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);

                // Spawn ground pickup of same type right at player
                manager.spawnPickup(500f, 500f, type);
                assertEquals(1, manager.getPickups().size());

                // Update manager with small frame tick (0.016s)
                manager.update(0.016f, player, false);

                // Pickup should be collected and duration refreshed back to EXACTLY 5.0s (not 5.0 + targetRemaining)
                assertEquals(type, manager.getActiveAbility().getType());
                assertTrue(manager.hasActiveAbility());
                assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 0.03f);
                assertTrue(manager.getPickups().isEmpty(), "Collected pickup must be cleanly removed");
            }
        }

        @Test
        @DisplayName("2.2 Rapid Sequential Same-Type Pickups: Zero Multiplier Stacking & Memory Cleansing")
        public void testRapidSameTypePickupsNoStacking() {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500f, 500f));
            player.setPickupRadius(45.0f);

            // Spawn 5 Arcane Overdrive pickups at player position
            for (int i = 0; i < 5; i++) {
                manager.spawnPickup(500f, 500f, PowerUpType.ARCANE_OVERDRIVE);
            }
            assertEquals(5, manager.getPickups().size());

            // Update manager: player collects all 5 in the frame
            manager.update(0.016f, player, false);

            assertTrue(manager.hasActiveAbility());
            assertEquals(PowerUpType.ARCANE_OVERDRIVE, manager.getActiveAbility().getType());
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 0.03f);
            assertTrue(manager.getPickups().isEmpty(), "All 5 pickups must be collected and removed");

            // Multiplier must NOT stack (must be 1/1.8f, not (1/1.8f)^5)
            assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), EPSILON);

            // Same test for Piercing Surge: bonus pierce must be 2, not 10
            for (int i = 0; i < 5; i++) {
                manager.spawnPickup(500f, 500f, PowerUpType.PIERCING_SURGE);
            }
            manager.update(0.016f, player, false);
            assertEquals(2, manager.getBonusPierce(), "Bonus pierce must remain strictly 2, not stack");
        }
    }

    // =========================================================================
    // Challenge 3: Different-Type Collection Replacement & Modifier Isolation
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: Different-Type Replacement & Zero Cross-Contamination")
    class DifferentTypeReplacementTests {

        @Test
        @DisplayName("3.1 Complete 6-Permutation Replacement Cycle with Strict Modifier Isolation")
        public void testDifferentTypeReplacementPermutations() {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500f, 500f));
            player.setPickupRadius(45.0f);

            // 1. Initial Pickup: ARCANE_OVERDRIVE
            manager.spawnPickup(500f, 500f, PowerUpType.ARCANE_OVERDRIVE);
            manager.update(0.016f, player, false);
            assertEquals(PowerUpType.ARCANE_OVERDRIVE, manager.getActiveAbility().getType());
            assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), EPSILON);
            assertEquals(0, manager.getBonusPierce());
            assertFalse(manager.isElementalBurstActive());

            // Elapse 2.0s
            manager.update(2.0f, player, false);
            assertEquals(3.0f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);

            // 2. Replace with PIERCING_SURGE
            manager.spawnPickup(500f, 500f, PowerUpType.PIERCING_SURGE);
            manager.update(0.016f, player, false);
            assertEquals(PowerUpType.PIERCING_SURGE, manager.getActiveAbility().getType());
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);
            // Verify NO cross-contamination from Arcane Overdrive!
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON, "Cooldown must revert immediately");
            assertEquals(2, manager.getBonusPierce(), "Bonus pierce must apply immediately");
            assertFalse(manager.isElementalBurstActive(), "Elemental burst must remain false");

            // Elapse 3.5s
            manager.update(3.5f, player, false);
            assertEquals(1.5f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);

            // 3. Replace with ELEMENTAL_BURST
            manager.spawnPickup(500f, 500f, PowerUpType.ELEMENTAL_BURST);
            manager.update(0.016f, player, false);
            assertEquals(PowerUpType.ELEMENTAL_BURST, manager.getActiveAbility().getType());
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);
            // Verify NO cross-contamination from Piercing Surge!
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
            assertEquals(0, manager.getBonusPierce(), "Bonus pierce must revert immediately to 0");
            assertTrue(manager.isElementalBurstActive(), "Elemental burst must apply immediately");

            // Elapse 4.2s
            manager.update(4.2f, player, false);
            assertEquals(0.8f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);

            // 4. Replace back with ARCANE_OVERDRIVE
            manager.spawnPickup(500f, 500f, PowerUpType.ARCANE_OVERDRIVE);
            manager.update(0.016f, player, false);
            assertEquals(PowerUpType.ARCANE_OVERDRIVE, manager.getActiveAbility().getType());
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);
            assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), EPSILON);
            assertEquals(0, manager.getBonusPierce());
            assertFalse(manager.isElementalBurstActive(), "Elemental burst must revert immediately to false");
        }

        @Test
        @DisplayName("3.2 Instantaneous Zero-Delta Chain Replacement (AO -> PS -> EB in Single Frame)")
        public void testInstantaneousZeroDeltaChainReplacement() {
            PowerUpManager manager = new PowerUpManager();
            ActiveTemporaryAbility ability = manager.getActiveAbility();

            ability.activate(PowerUpType.ARCANE_OVERDRIVE);
            assertEquals(PowerUpType.ARCANE_OVERDRIVE, ability.getType());
            assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), EPSILON);

            ability.activate(PowerUpType.PIERCING_SURGE);
            assertEquals(PowerUpType.PIERCING_SURGE, ability.getType());
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
            assertEquals(2, manager.getBonusPierce());

            ability.activate(PowerUpType.ELEMENTAL_BURST);
            assertEquals(PowerUpType.ELEMENTAL_BURST, ability.getType());
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
            assertEquals(0, manager.getBonusPierce());
            assertTrue(manager.isElementalBurstActive());
            assertEquals(5.0f, ability.getRemainingDuration(), EPSILON);
        }

        @Test
        @DisplayName("3.3 Natural Expiry Followed by Different-Type Re-Activation")
        public void testNaturalExpiryFollowedByDifferentTypeActivation() {
            PowerUpManager manager = new PowerUpManager();
            manager.getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
            manager.getActiveAbility().update(5.5f); // naturally expire
            assertFalse(manager.hasActiveAbility());

            // Collect Piercing Surge
            manager.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);
            assertTrue(manager.hasActiveAbility());
            assertEquals(PowerUpType.PIERCING_SURGE, manager.getActiveAbility().getType());
            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON);
            assertEquals(2, manager.getBonusPierce());
            assertEquals(1.0f, manager.getCastCooldownMultiplier(), EPSILON);
        }
    }

    // =========================================================================
    // Challenge 4: Modal Pause Suspension (100 Seconds Simulation)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: Modal Pause Suspension (100 Seconds Stress)")
    class ModalPauseSuspensionTests {

        @Test
        @DisplayName("4.1 PowerUpManager Direct Pause Suspension: 100 Seconds (6250 Steps & 1-Shot 100s)")
        public void testPowerUpManagerDirectPauseSuspension100Seconds() {
            PowerUpManager manager = new PowerUpManager();
            Player player = new Player(new Vector2D(500f, 500f));

            // Activate Piercing Surge
            manager.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);
            // Spawn 3 ground pickups
            manager.spawnPickup(1000f, 1000f, PowerUpType.ARCANE_OVERDRIVE);
            manager.spawnPickup(1200f, 1200f, PowerUpType.PIERCING_SURGE);
            manager.spawnPickup(1400f, 1400f, PowerUpType.ELEMENTAL_BURST);

            assertEquals(3, manager.getPickups().size());
            float abilityDurBefore = manager.getActiveAbility().getRemainingDuration();
            float p0LifeBefore = manager.getPickups().get(0).getLifetime();
            float p1LifeBefore = manager.getPickups().get(1).getLifetime();
            float p2LifeBefore = manager.getPickups().get(2).getLifetime();

            assertEquals(5.0f, abilityDurBefore, EPSILON);
            assertEquals(25.0f, p0LifeBefore, EPSILON);

            // Phase A: 6250 micro-steps of 0.016s with paused = true (100 seconds)
            for (int i = 0; i < 6250; i++) {
                manager.update(0.016f, player, true);
            }

            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON,
                    "Active power-up must have ZERO decrement over 100s paused");
            assertTrue(manager.hasActiveAbility());
            assertEquals(25.0f, manager.getPickups().get(0).getLifetime(), EPSILON);
            assertEquals(25.0f, manager.getPickups().get(1).getLifetime(), EPSILON);
            assertEquals(25.0f, manager.getPickups().get(2).getLifetime(), EPSILON);
            assertEquals(3, manager.getPickups().size());

            // Phase B: Single massive step of dt = 100.0s with paused = true
            manager.update(100.0f, player, true);

            assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), EPSILON,
                    "Active power-up must have ZERO decrement after 100s single step paused");
            assertEquals(25.0f, manager.getPickups().get(0).getLifetime(), EPSILON);
            assertEquals(25.0f, manager.getPickups().get(1).getLifetime(), EPSILON);
            assertEquals(25.0f, manager.getPickups().get(2).getLifetime(), EPSILON);

            // Phase C: Resume unpaused for 1.0s -> timers must decrement by exactly 1.0s
            manager.update(1.0f, player, false);
            assertEquals(4.0f, manager.getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);
            assertEquals(24.0f, manager.getPickups().get(0).getLifetime(), LOOSE_EPSILON);
            assertEquals(24.0f, manager.getPickups().get(1).getLifetime(), LOOSE_EPSILON);
            assertEquals(24.0f, manager.getPickups().get(2).getLifetime(), LOOSE_EPSILON);
        }

        @Test
        @DisplayName("4.2 GameSession LEVEL_UP Modal Suspension: 100 Seconds (1000 Ticks) Total Stillness")
        public void testGameSessionLevelUpModalPause100Seconds() {
            GameSession session = new GameSession();

            // Give player an active ability
            session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ELEMENTAL_BURST);
            assertEquals(5.0f, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), EPSILON);

            // Spawn ground pickup
            session.getPowerUpManager().spawnPickup(2500f, 2500f, PowerUpType.ARCANE_OVERDRIVE);
            assertEquals(1, session.getPowerUpManager().getPickups().size());
            WorldPowerUp pickup = session.getPowerUpManager().getPickups().get(0);
            assertEquals(25.0f, pickup.getLifetime(), EPSILON);

            // Open LevelUp overlay -> Game enters LEVEL_UP modal state
            session.getLevelUpOverlay().open(createSampleUpgradeOptions());
            assertTrue(session.isLevelUpOpen());
            assertFalse(session.isPlaying());

            float matchElapsedBefore = session.getMatchElapsed();
            float playerHpBefore = session.getPlayer().getHealth();
            Vector2D playerPosBefore = session.getPlayer().getPosition().cpy();

            // Simulate 100.0 seconds while in LEVEL_UP modal (1000 ticks of dt=0.1s)
            for (int i = 0; i < 1000; i++) {
                session.update(0.1f);
                assertTrue(session.isLevelUpOpen());
                assertEquals(GameState.LEVEL_UP, session.getGameState(), "Session state must be LEVEL_UP during modal freeze");
            }

            // Assert absolute freeze across all gameplay subsystems
            assertEquals(matchElapsedBefore, session.getMatchElapsed(), EPSILON, "Match timer must not advance");
            assertEquals(5.0f, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), EPSILON,
                    "Active ability remaining duration must have ZERO decrement in LEVEL_UP");
            assertEquals(25.0f, pickup.getLifetime(), EPSILON,
                    "Ground pickup lifetime must have ZERO decrement in LEVEL_UP");
            assertEquals(playerHpBefore, session.getPlayer().getHealth(), EPSILON);
            assertEquals(playerPosBefore.x, session.getPlayer().getPosition().x, EPSILON);
            assertEquals(playerPosBefore.y, session.getPlayer().getPosition().y, EPSILON);
            assertEquals(0, session.getCombatSystem().getProjectileCount());

            // Close modal -> unpause
            session.getLevelUpOverlay().close();
            session.update(0.001f); // refresh state
            assertTrue(session.isPlaying());

            // Advance 1.0s unpaused -> power-up and pickup decrement normally
            session.update(1.0f);
            assertEquals(4.0f, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), LOOSE_EPSILON);
            assertEquals(24.0f, pickup.getLifetime(), LOOSE_EPSILON);
        }

        @Test
        @DisplayName("4.3 GameSession GAME_OVER Modal Suspension: 100 Seconds Total Stillness")
        public void testGameSessionGameOverModalPause100Seconds() {
            GameSession session = new GameSession();
            session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
            session.getPowerUpManager().spawnPickup(2000f, 2000f, PowerUpType.PIERCING_SURGE);

            // Lethal damage triggers game over
            session.getPlayer().takeDamage(500.0f);
            session.update(0.016f);
            assertTrue(session.isGameOver());
            assertEquals(GameState.GAME_OVER, session.getGameState());

            // Simulate 100 seconds in Game Over
            for (int i = 0; i < 1000; i++) {
                session.update(0.1f);
            }

            assertEquals(5.0f, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), EPSILON);
            assertEquals(25.0f, session.getPowerUpManager().getPickups().get(0).getLifetime(), EPSILON);
            assertTrue(session.isGameOver());
        }
    }

    // =========================================================================
    // Challenge 5: Two-Step Selection in LevelUpOverlay
    // =========================================================================
    @Nested
    @DisplayName("Challenge 5: Two-Step Selection, Grace Timer & Keyboard in LevelUpOverlay")
    class LevelUpOverlayTwoStepTests {

        @Test
        @DisplayName("5.1 0.15s Grace Timer Rejects All Inputs (t=0.0s, 0.05s, 0.10s, 0.149s) and Unlocks at 0.151s")
        public void testGraceTimerStrictRejection() {
            LevelUpOverlay overlay = new LevelUpOverlay();
            overlay.open(createSampleUpgradeOptions());

            Rectangle card0 = overlay.getCardBounds().get(0);
            Rectangle confirmBtn = overlay.getConfirmButtonBounds();
            Vector2D card0Pos = new Vector2D(card0.x + 10f, card0.y + 10f);
            Vector2D confirmPos = new Vector2D(confirmBtn.x + 10f, confirmBtn.y + 10f);

            // t = 0.00s: grace timer active (0.15s)
            assertEquals(0.15f, overlay.getOpenGraceTimer(), EPSILON);
            assertFalse(overlay.handleMouseClick(card0Pos), "Click card at t=0 must be rejected");
            assertEquals(-1, overlay.getSelectedIndex(), "Selection must remain -1");
            assertFalse(overlay.handleMouseClick(confirmPos), "Click confirm at t=0 must be rejected");

            // t = 0.05s: grace timer remaining 0.10s
            overlay.update(0.05f);
            assertEquals(0.10f, overlay.getOpenGraceTimer(), LOOSE_EPSILON);
            assertFalse(overlay.handleMouseClick(card0Pos));
            assertEquals(-1, overlay.getSelectedIndex());

            // t = 0.10s: grace timer remaining 0.05s
            overlay.update(0.05f);
            assertEquals(0.05f, overlay.getOpenGraceTimer(), LOOSE_EPSILON);
            assertFalse(overlay.handleMouseClick(card0Pos));
            assertEquals(-1, overlay.getSelectedIndex());

            // t = 0.149s: grace timer remaining ~0.001s (> 0)
            overlay.update(0.049f);
            assertTrue(overlay.getOpenGraceTimer() > 0.0f);
            assertFalse(overlay.handleMouseClick(card0Pos));
            assertEquals(-1, overlay.getSelectedIndex());

            // t = 0.151s: grace timer expired (0.0s)
            overlay.update(0.005f);
            assertEquals(0.0f, overlay.getOpenGraceTimer(), EPSILON);

            // Now card click is accepted!
            assertFalse(overlay.handleMouseClick(card0Pos), "Step 1 click selects but does NOT confirm");
            assertEquals(0, overlay.getSelectedIndex(), "Card 0 is now successfully selected");
        }

        @Test
        @DisplayName("5.2 Step 1 Clicking Card Selects without Committing; Switching Between Cards")
        public void testCardSelectionAndSwitching() {
            LevelUpOverlay overlay = new LevelUpOverlay();
            List<UpgradeInfo> options = createSampleUpgradeOptions();
            overlay.open(options);
            overlay.update(0.20f); // expire grace timer

            List<Rectangle> cards = overlay.getCardBounds();
            Vector2D c0 = new Vector2D(cards.get(0).x + 20f, cards.get(0).y + 20f);
            Vector2D c1 = new Vector2D(cards.get(1).x + 20f, cards.get(1).y + 20f);
            Vector2D c2 = new Vector2D(cards.get(2).x + 20f, cards.get(2).y + 20f);

            // Click card 0
            boolean committed = overlay.handleMouseClick(c0);
            assertFalse(committed, "Clicking card 0 must return false");
            assertEquals(0, overlay.getSelectedIndex());
            assertEquals(options.get(0), overlay.getSelectedUpgrade());
            assertTrue(overlay.hasValidSelection());

            // Switch to card 2
            committed = overlay.handleMouseClick(c2);
            assertFalse(committed, "Clicking card 2 must return false");
            assertEquals(2, overlay.getSelectedIndex(), "Selection must switch to card 2");
            assertEquals(options.get(2), overlay.getSelectedUpgrade());

            // Switch to card 1
            committed = overlay.handleMouseClick(c1);
            assertFalse(committed, "Clicking card 1 must return false");
            assertEquals(1, overlay.getSelectedIndex(), "Selection must switch to card 1");
            assertEquals(options.get(1), overlay.getSelectedUpgrade());

            // Re-select card 0
            committed = overlay.handleMouseClick(c0);
            assertFalse(committed);
            assertEquals(0, overlay.getSelectedIndex());
        }

        @Test
        @DisplayName("5.3 Step 2 Confirm Button Commits Only with Valid Selection")
        public void testConfirmButtonCommit() {
            LevelUpOverlay overlay = new LevelUpOverlay();
            overlay.open(createSampleUpgradeOptions());
            overlay.update(0.20f);

            Rectangle confirmBtn = overlay.getConfirmButtonBounds();
            Vector2D confirmPos = new Vector2D(confirmBtn.x + 20f, confirmBtn.y + 20f);

            // Attempt to confirm without card selection -> rejected!
            assertFalse(overlay.hasValidSelection());
            assertFalse(overlay.handleMouseClick(confirmPos), "Confirming with no selection must return false");

            // Select card 1
            Rectangle card1 = overlay.getCardBounds().get(1);
            overlay.handleMouseClick(new Vector2D(card1.x + 20f, card1.y + 20f));
            assertTrue(overlay.hasValidSelection());
            assertEquals(1, overlay.getSelectedIndex());

            // Click confirm button -> commits!
            assertTrue(overlay.handleMouseClick(confirmPos), "Confirming with card 1 selected must return true");
        }

        @Test
        @DisplayName("5.4 Keyboard Navigation (1/2/3 Selects, Enter/Space Commits, Enter without Selection Rejects)")
        public void testKeyboardTwoStepNavigation() {
            LevelUpOverlay overlay = new LevelUpOverlay();
            overlay.open(createSampleUpgradeOptions());
            overlay.update(0.20f);

            // Enter / Space without selection must reject
            assertFalse(overlay.handleKeyPress(Keys.ENTER));
            assertFalse(overlay.handleKeyPress(Keys.SPACE));

            // Press '1' selects card 0
            assertFalse(overlay.handleKeyPress(Keys.NUM_1));
            assertEquals(0, overlay.getSelectedIndex());

            // Press '3' switches to card 2
            assertFalse(overlay.handleKeyPress(Keys.NUM_3));
            assertEquals(2, overlay.getSelectedIndex());

            // Press '2' switches to card 1
            assertFalse(overlay.handleKeyPress(Keys.NUM_2));
            assertEquals(1, overlay.getSelectedIndex());

            // Press Enter commits
            assertTrue(overlay.handleKeyPress(Keys.ENTER));

            // Re-open and verify Space commits
            overlay.open(createSampleUpgradeOptions());
            overlay.update(0.20f);
            overlay.handleKeyPress(Keys.NUM_1);
            assertTrue(overlay.handleKeyPress(Keys.SPACE));
        }

        @Test
        @DisplayName("5.5 Clicks Outside Cards and Confirm Button Return False")
        public void testClicksOutsideReturnFalse() {
            LevelUpOverlay overlay = new LevelUpOverlay();
            overlay.open(createSampleUpgradeOptions());
            overlay.update(0.20f);

            assertFalse(overlay.handleMouseClick(new Vector2D(0f, 0f)));
            assertFalse(overlay.handleMouseClick(new Vector2D(-50f, -50f)));
            assertFalse(overlay.handleMouseClick(new Vector2D(1280f, 720f)));
            assertFalse(overlay.handleMouseClick(null));
        }
    }

    // =========================================================================
    // Challenge 6: Text Description Wrapping (wrapText <= 244px)
    // =========================================================================
    @Nested
    @DisplayName("Challenge 6: Description Text Wrapping Stress (wrapText <= 244px)")
    class TextDescriptionWrappingTests {

        private static final float MAX_CONTENT_WIDTH = 244.0f;
        // In headless mode: fallbackMaxChars = max(10, (int)(244.0f / 7.0f)) = 34
        private static final int FALLBACK_MAX_CHARS = 34;

        @Test
        @DisplayName("6.1 Empty, Null, Whitespace & Non-Positive Width Inputs Return Empty List")
        public void testNullEmptyAndInvalidWidth() {
            assertTrue(LevelUpOverlay.wrapText(null, null, MAX_CONTENT_WIDTH).isEmpty());
            assertTrue(LevelUpOverlay.wrapText("", null, MAX_CONTENT_WIDTH).isEmpty());
            assertTrue(LevelUpOverlay.wrapText("   ", null, MAX_CONTENT_WIDTH).isEmpty());
            assertTrue(LevelUpOverlay.wrapText("Valid Text", null, 0.0f).isEmpty());
            assertTrue(LevelUpOverlay.wrapText("Valid Text", null, -50.0f).isEmpty());
        }

        @Test
        @DisplayName("6.2 Multi-line String with Embedded Newlines '\\n' Preserves Paragraph Breaks")
        public void testNewlinesPreserveParagraphs() {
            String multiLine = "Line One\nLine Two\nLine Three";
            List<String> wrapped = LevelUpOverlay.wrapText(multiLine, null, MAX_CONTENT_WIDTH);

            assertEquals(3, wrapped.size());
            assertEquals("Line One", wrapped.get(0));
            assertEquals("Line Two", wrapped.get(1));
            assertEquals("Line Three", wrapped.get(2));

            // Consecutive newlines
            String emptyParagraphs = "Paragraph 1\n\n\nParagraph 2";
            List<String> wrapped2 = LevelUpOverlay.wrapText(emptyParagraphs, null, MAX_CONTENT_WIDTH);
            assertEquals(2, wrapped2.size());
            assertEquals("Paragraph 1", wrapped2.get(0));
            assertEquals("Paragraph 2", wrapped2.get(1));
        }

        @Test
        @DisplayName("6.3 Long Sentences Wrapped into Multiple Lines, None Exceeding 244px (<= 34 chars)")
        public void testLongSentencesWrapWithinWidth() {
            String longDesc = "Increases maximum player health by 40 points immediately, restores all missing health, and grants a permanent bonus.";
            List<String> wrapped = LevelUpOverlay.wrapText(longDesc, null, MAX_CONTENT_WIDTH);

            assertNotNull(wrapped);
            assertTrue(wrapped.size() >= 3, "Long description should wrap into at least 3 lines, got " + wrapped.size());

            for (int i = 0; i < wrapped.size(); i++) {
                String line = wrapped.get(i);
                assertTrue(line.length() <= FALLBACK_MAX_CHARS,
                        "Line " + i + " length (" + line.length() + ") exceeds max allowed chars (" + FALLBACK_MAX_CHARS + "): '" + line + "'");
            }
        }

        @Test
        @DisplayName("6.4 All 7 Milestone 6 Game Upgrade Perk Descriptions Fit Strictly within 244px")
        public void testAllGamePerkDescriptionsFitWithin244px() {
            String[] descriptions = {
                    "+40 Max Health & instant heal",
                    "+12% Movement Speed",
                    "+20% Spell Damage",
                    "-12% Spell Cast Cooldown",
                    "+20% Projectile Speed",
                    "+25% Projectile Range/Lifetime",
                    "+1 Missile Pierce (max 2 ranks, -20% dmg/hit)"
            };

            for (String desc : descriptions) {
                List<String> wrapped = LevelUpOverlay.wrapText(desc, null, MAX_CONTENT_WIDTH);
                assertFalse(wrapped.isEmpty(), "Wrapped lines must not be empty for: " + desc);

                for (String line : wrapped) {
                    assertTrue(line.length() <= FALLBACK_MAX_CHARS,
                            "Perk line exceeds 244px capacity (" + line.length() + " > " + FALLBACK_MAX_CHARS + "): '" + line + "'");
                }
            }
        }

        @Test
        @DisplayName("6.5 Single Unbroken Word Exceeding 244px Handled Safely on Own Line Without Crash")
        public void testSingleWordExceedingWidthHandledSafely() {
            String ultraLongWord = "SupercalifragilisticexpialidociousUltraLongWordWithoutSpacesExceedingContainerWidth";
            String mixedText = "Prefix " + ultraLongWord + " Suffix with regular wrapping words";

            List<String> wrapped = LevelUpOverlay.wrapText(mixedText, null, MAX_CONTENT_WIDTH);
            assertNotNull(wrapped);
            assertTrue(wrapped.size() >= 3, "Mixed text with ultra-long word must produce >= 3 lines");

            // Line 0 should be "Prefix"
            assertEquals("Prefix", wrapped.get(0));
            // Line 1 should isolate the ultraLongWord
            assertEquals(ultraLongWord, wrapped.get(1));
            // Remaining lines should wrap the suffix cleanly
            for (int i = 2; i < wrapped.size(); i++) {
                assertTrue(wrapped.get(i).length() <= FALLBACK_MAX_CHARS,
                        "Suffix lines must wrap within width: '" + wrapped.get(i) + "'");
            }
        }

        @Test
        @DisplayName("6.6 Randomized Text Generator: 20 Varied Multi-word Sentences")
        public void testRandomizedTextGeneratorWrapping() {
            Random rng = new Random(12345L);
            String[] wordBank = {"Arcane", "Pierce", "Overdrive", "Elemental", "Burst", "Surge", "Damage",
                    "Cooldown", "Health", "Speed", "Lifetime", "Projectiles", "Spells", "Lightning", "Fireball",
                    "Frost", "Tempest", "Meteor", "Radius", "Mitigation"};

            for (int trial = 0; trial < 20; trial++) {
                int wordCount = 10 + rng.nextInt(30);
                StringBuilder sb = new StringBuilder();
                for (int w = 0; w < wordCount; w++) {
                    sb.append(wordBank[rng.nextInt(wordBank.length)]).append(" ");
                }
                String sample = sb.toString().trim();

                List<String> wrapped = LevelUpOverlay.wrapText(sample, null, MAX_CONTENT_WIDTH);
                assertFalse(wrapped.isEmpty());

                for (String line : wrapped) {
                    assertTrue(line.length() <= FALLBACK_MAX_CHARS,
                            "Trial " + trial + " line exceeds width: '" + line + "'");
                }
            }
        }
    }

    // =========================================================================
    // Challenge 7: Deterministic Restart: 20 Consecutive Cycles of restartMatch()
    // =========================================================================
    @Nested
    @DisplayName("Challenge 7: Deterministic Restart (20 Consecutive Cycles Stress)")
    class DeterministicRestartTests {

        @Test
        @DisplayName("7.1 20 Consecutive Cycles of restartMatch(): Complete State Cleansing & Zero Leakage")
        public void testTwentyConsecutiveCyclesRestartMatch() {
            GameSession session = new GameSession();

            for (int cycle = 1; cycle <= 20; cycle++) {
                // Assert pristine initial conditions for each cycle
                assertTrue(session.isPlaying(), "Cycle " + cycle + ": must be in PLAYING state");
                assertFalse(session.isGameOver(), "Cycle " + cycle + ": isGameOver must be false");
                assertFalse(session.isLevelUpOpen(), "Cycle " + cycle + ": isLevelUpOpen must be false");
                assertEquals(GameState.PLAYING, session.getGameState());

                Player player = session.getPlayer();
                assertTrue(player.isAlive(), "Cycle " + cycle + ": player must be alive");
                assertEquals(250.0f, player.getHealth(), EPSILON, "Cycle " + cycle + ": player HP must be 250");
                assertEquals(250.0f, player.getMaxHealth(), EPSILON, "Cycle " + cycle + ": player maxHP must be 250");
                assertEquals(30.0f, player.getArmor(), EPSILON, "Cycle " + cycle + ": player armor must be 30");
                assertEquals(230.0f, player.getBaseMoveSpeed(), EPSILON, "Cycle " + cycle + ": player speed must be 230");
                assertEquals(0.28f, player.getBaseCastCooldown(), EPSILON, "Cycle " + cycle + ": player CD must be 0.28");
                assertEquals(0.0f, player.getCastTimer(), EPSILON, "Cycle " + cycle + ": player cast timer must be 0");
                assertEquals(0.0f, player.getToggleDebounceTimer(), EPSILON, "Cycle " + cycle + ": debounce timer must be 0");
                assertEquals(1.0f, player.getDamageMultiplier(), EPSILON, "Cycle " + cycle + ": dmg mult must be 1.0");
                assertEquals(1.0f, player.getProjectileSpeedMultiplier(), EPSILON, "Cycle " + cycle + ": proj spd mult must be 1.0");
                assertEquals(1.0f, player.getProjectileLifetimeMultiplier(), EPSILON, "Cycle " + cycle + ": proj life mult must be 1.0");
                assertEquals(45.0f, player.getPickupRadius(), EPSILON, "Cycle " + cycle + ": pickup radius must be 45.0");
                assertEquals(0, player.getBonusPierce(), "Cycle " + cycle + ": bonus pierce must be 0");
                assertTrue(player.canCast(), "Cycle " + cycle + ": player must be able to cast");
                assertEquals(ShootingMode.MANUAL, session.getShootingMode(), "Cycle " + cycle + ": shooting mode must be MANUAL");

                // Progression clean reset
                assertEquals(1, session.getProgression().getLevel(), "Cycle " + cycle + ": level must be 1");
                assertEquals(0, session.getProgression().getCurrentExp(), "Cycle " + cycle + ": current EXP must be 0");
                assertEquals(0, session.getProgression().getPendingLevelUps(), "Cycle " + cycle + ": pending levelups must be 0");

                // Upgrade manager clean reset
                for (UpgradeType perk : UpgradeType.values()) {
                    assertEquals(0, session.getUpgradeManager().getRank(perk),
                            "Cycle " + cycle + ": perk " + perk + " rank must be 0");
                }

                // Entity lists clean reset
                assertEquals(0, session.getEnemies().size(), "Cycle " + cycle + ": active enemies must be 0");
                assertEquals(0, session.getCombatSystem().getProjectileCount(), "Cycle " + cycle + ": projectiles must be 0");
                assertEquals(0, session.getExperienceSystem().getPickupCount(), "Cycle " + cycle + ": EXP gems must be 0");
                assertEquals(0, session.getPowerUpManager().getPickups().size(), "Cycle " + cycle + ": ground pickups must be 0");

                // PowerUp ability clean reset
                assertFalse(session.getPowerUpManager().hasActiveAbility(), "Cycle " + cycle + ": active ability must be false");
                assertEquals(1.0f, session.getPowerUpManager().getCastCooldownMultiplier(), EPSILON);
                assertEquals(0, session.getPowerUpManager().getBonusPierce());
                assertFalse(session.getPowerUpManager().isElementalBurstActive());

                // Session timers and telemetry clean reset
                assertEquals(0.0f, session.getMatchElapsed(), EPSILON, "Cycle " + cycle + ": match elapsed must be 0");
                assertEquals(0, session.getEnemiesDefeated(), "Cycle " + cycle + ": enemies defeated must be 0");
                assertEquals(0.0f, session.getModeNotificationTimer(), EPSILON);
                assertEquals("", session.getModeNotificationText());

                // Camera reset
                assertEquals(1.35f, session.getCamera().getZoom(), EPSILON);

                // =============================================================
                // Heavily mutate state during the cycle
                // =============================================================
                // Advance 10 seconds of gameplay
                for (int t = 0; t < 100; t++) {
                    session.update(0.1f);
                }

                // Apply upgrades
                session.getUpgradeManager().applyUpgrade(UpgradeType.DAMAGE, player);
                session.getUpgradeManager().applyUpgrade(UpgradeType.PIERCING, player);
                session.getUpgradeManager().applyUpgrade(UpgradeType.MAX_HEALTH, player);
                session.getUpgradeManager().applyUpgrade(UpgradeType.MOVE_SPEED, player);

                // Give power-up
                session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
                session.getPowerUpManager().spawnPickup(2000f, 2000f, PowerUpType.ELEMENTAL_BURST);

                // Add EXP
                session.getProgression().addExp(100);

                // Toggle shooting mode
                session.toggleShootingMode();

                // Inflict damage to player and kill player to trigger game over
                player.takeDamage(100.0f);
                player.takeDamage(600.0f);
                session.update(0.016f);
                assertTrue(session.isGameOver(), "Cycle " + cycle + ": GameOverOverlay must open upon death");

                // Execute deterministic restart for next cycle
                session.restartMatch();
            }
        }
    }
}
