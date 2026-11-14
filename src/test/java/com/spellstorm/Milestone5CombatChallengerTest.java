package com.spellstorm;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.combat.BurnStatus;
import com.spellstorm.combat.CombatSystem;
import com.spellstorm.combat.DamageInfo;
import com.spellstorm.combat.DamageMitigation;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.ElementSet;
import com.spellstorm.combat.SlowStatus;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.combat.StatusKind;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Character;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Adversarial empirical challenger test suite for Milestone 5:
 * Combat calculations, status effects, projectile piercing falloff,
 * multi-hit prevention, and elemental recipe resolution.
 *
 * Authored by m5_challenger_1_gen3 to empirically stress-test:
 * 1. Mitigation formula with extreme edge cases (A=10000, A=-500, D=-10, D=0.001).
 * 2. Burn DoT tick behavior under various dt slices (dt=0.016, dt=0.49, dt=0.51, dt=2.5)
 *    ensuring exact tick count, zero damage on frame 0, and true damage bypass.
 * 3. SlowStatus clamping [0.0, 0.9] and composite speed with slow resistance.
 * 4. Projectile piercing: verify initial damage 100 -> hit 1 (80) -> hit 2 (64) -> hit 3 (51.2) -> destroyed,
 *    25% minimum floor clamping, multi-hit prevention across repeated frames.
 * 5. ElementSet bitmask commutativity and recipe resolution.
 * 6. Boss Colossus Overlord durability and 3-way spread volley attack.
 */
@DisplayName("Milestone 5 Combat Challenger: Empirical Stress Tests")
public class Milestone5CombatChallengerTest {

    private static final float EPSILON = 1e-4f;

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    private static class DummyCharacter extends Character {
        public DummyCharacter(float hp, float armor, float speed) {
            super(new Vector2D(0.0f, 0.0f), 15.0f, hp, armor, speed, "dummy");
        }
    }

    // =========================================================================
    // Challenge 1: Damage Mitigation Formula Extreme Edge Cases
    // =========================================================================
    @Nested
    @DisplayName("Challenge 1: Damage Mitigation Extreme Edge Cases")
    class DamageMitigationChallenges {

        @Test
        @DisplayName("Extreme armor A=10000 yields heavy mitigation without division overflow (0.9901 damage dealt)")
        void testMitigationExtremeArmor10000() {
            float rawDamage = 100.0f;
            float armor = 10000.0f;
            // Formula: 100 * 100 / (100 + 10000) = 10000 / 10100 = 0.990099f
            float expected = 100.0f * 100.0f / 10100.0f;
            float actual = DamageMitigation.calculateDamage(rawDamage, armor);
            assertEquals(expected, actual, EPSILON);
            assertEquals(0.9901f, actual, 0.001f);
            assertTrue(actual > 0.0f, "Dealt damage should remain positive");
            assertFalse(Float.isNaN(actual), "Result must not be NaN");
            assertFalse(Float.isInfinite(actual), "Result must not be infinite");
        }

        @Test
        @DisplayName("Extreme negative armor A=-500 is clamped to 0.0f (no damage amplification)")
        void testMitigationExtremeNegativeArmor500() {
            float rawDamage = 100.0f;
            float armor = -500.0f;
            // Formula clamps negative armor to 0: 100 * 100 / (100 + 0) = 100.0f
            float actual = DamageMitigation.calculateDamage(rawDamage, armor);
            assertEquals(100.0f, actual, EPSILON, "Negative armor must not amplify incoming damage");
        }

        @Test
        @DisplayName("Negative armor A=-10 is clamped to 0.0f")
        void testMitigationNegativeArmor10() {
            float rawDamage = 100.0f;
            float armor = -10.0f;
            float actual = DamageMitigation.calculateDamage(rawDamage, armor);
            assertEquals(100.0f, actual, EPSILON);
        }

        @Test
        @DisplayName("Near-zero armor A=0.001f computes accurately without precision corruption")
        void testMitigationNearZeroArmor() {
            float rawDamage = 100.0f;
            float armor = 0.001f;
            // Formula: 100 * 100 / (100 + 0.001) = 10000 / 100.001 = 99.999f
            float expected = 10000.0f / 100.001f;
            float actual = DamageMitigation.calculateDamage(rawDamage, armor);
            assertEquals(expected, actual, EPSILON);
            assertEquals(99.999f, actual, 0.005f);
        }

        @Test
        @DisplayName("Negative raw damage D=-10 and D=-500 strictly return 0.0f damage dealt")
        void testMitigationNegativeRawDamage() {
            assertEquals(0.0f, DamageMitigation.calculateDamage(-10.0f, 50.0f), EPSILON);
            assertEquals(0.0f, DamageMitigation.calculateDamage(-500.0f, 0.0f), EPSILON);
            assertEquals(0.0f, DamageMitigation.calculateDamage(-0.0001f, 100.0f), EPSILON);
            assertEquals(0.0f, DamageMitigation.calculateDamage(0.0f, 100.0f), EPSILON);
        }

        @Test
        @DisplayName("Very small positive raw damage D=0.001f computes smoothly without underflowing to NaN")
        void testMitigationSmallPositiveRawDamage() {
            float rawDamage = 0.001f;
            // A=0: 0.001
            assertEquals(0.001f, DamageMitigation.calculateDamage(rawDamage, 0.0f), EPSILON);
            // A=100: 0.001 * 100 / 200 = 0.0005f
            assertEquals(0.0005f, DamageMitigation.calculateDamage(rawDamage, 100.0f), EPSILON);
            // A=10000: 0.001 * 100 / 10100 = 9.90099e-6f
            float tiny = DamageMitigation.calculateDamage(rawDamage, 10000.0f);
            assertTrue(tiny > 0.0f, "Tiny damage must not truncate to zero or become NaN");
            assertEquals(0.001f * 100.0f / 10100.0f, tiny, 1e-7f);
        }

        @Test
        @DisplayName("Character.takeDamage integration matches DamageMitigation with extreme values")
        void testCharacterTakeDamageIntegration() {
            // Character with 10000 armor takes 100 raw damage
            DummyCharacter tank = new DummyCharacter(500.0f, 10000.0f, 100.0f);
            float dealt = tank.takeDamage(100.0f);
            assertEquals(0.9901f, dealt, 0.001f);
            assertEquals(500.0f - dealt, tank.getHealth(), EPSILON);

            // Character with -500 armor takes 50 raw damage (armor clamped to 0)
            DummyCharacter glass = new DummyCharacter(200.0f, -500.0f, 100.0f);
            float dealtGlass = glass.takeDamage(50.0f);
            assertEquals(50.0f, dealtGlass, EPSILON);
            assertEquals(150.0f, glass.getHealth(), EPSILON);

            // Negative damage does not heal or harm
            float dealtNeg = glass.takeDamage(-10.0f);
            assertEquals(0.0f, dealtNeg, EPSILON);
            assertEquals(150.0f, glass.getHealth(), EPSILON);

            // True damage bypasses even 10000 armor completely
            float trueDealt = tank.takeDamage(new DamageInfo(50.0f, true));
            assertEquals(50.0f, trueDealt, EPSILON);
            assertEquals(500.0f - dealt - 50.0f, tank.getHealth(), EPSILON);
        }
    }

    // =========================================================================
    // Challenge 2: Burn DoT Tick Behavior Under Various dt Slices
    // =========================================================================
    @Nested
    @DisplayName("Challenge 2: Burn DoT Tick Behavior Under Various dt Slices")
    class BurnDoTChallenges {

        @Test
        @DisplayName("BurnStatus inflicts zero damage on frame 0 (application delay = 0.5s)")
        void testBurnZeroDamageOnFrameZero() {
            DummyCharacter target = new DummyCharacter(100.0f, 50.0f, 100.0f);
            BurnStatus burn = new BurnStatus(30.0f, 3.0f); // 30 DPS, 3.0s duration

            target.addStatus(burn);
            assertTrue(target.hasStatus(StatusKind.BURN));
            assertEquals(100.0f, target.getHealth(), EPSILON, "Frame 0 health must remain untouched");

            // Sub-interval dt=0.25s: still no damage dealt
            target.update(0.25f);
            assertEquals(100.0f, target.getHealth(), EPSILON, "No damage should occur before 0.5s interval");
        }

        @Test
        @DisplayName("BurnStatus under small dt slices dt=0.016s (~60 FPS) delivers exact tick behavior and phase jitter")
        void testBurnSmallDtSlices_0_016() {
            DummyCharacter target = new DummyCharacter(100.0f, 100.0f, 100.0f);
            float dps = 20.0f;
            float duration = 3.0f;
            float tickDamage = dps * BurnStatus.TICK_INTERVAL; // 20 * 0.5 = 10.0
            BurnStatus burn = new BurnStatus(dps, duration);
            target.addStatus(burn);

            float dt = 0.016f;

            // Frames 1..31 (t = 31 * 0.016 = 0.496s < 0.5s): 0 damage dealt
            for (int i = 1; i <= 31; i++) {
                target.update(dt);
                assertEquals(100.0f, target.getHealth(), EPSILON, "Frame " + i + " must not have ticked yet");
            }

            // Frame 32 (t = 32 * 0.016 = 0.512s >= 0.5s): First tick triggers!
            target.update(dt);
            assertEquals(90.0f, target.getHealth(), EPSILON, "First tick must deliver exactly 10.0 true damage");

            // Step through frame 188 (t = 3.0s total elapsed)
            for (int i = 33; i <= 188; i++) {
                target.update(dt);
            }

            // Empirical insight: 31 frames * 0.016 = 0.496s introduces a 12ms phase delay per tick.
            // Tick 1 at 0.512s, Tick 2 at 1.008s, Tick 3 at 1.504s, Tick 4 at 2.000s, Tick 5 at 2.512s.
            // Tick 6 requires t=3.008s. When duration is capped at exactly 3.000s, remaining time clamps
            // at 3.000s, so tick 6 is cut off by 8ms. Exactly 5 ticks fire (50 damage dealt).
            assertEquals(50.0f, target.getHealth(), EPSILON, "Within strictly clamped 3.000s under dt=0.016s, 5 ticks fire (50.0 damage)");
            assertFalse(target.hasStatus(StatusKind.BURN), "BurnStatus must be expired and pruned");

            // Subsequent frames deal zero additional damage
            target.update(dt);
            assertEquals(50.0f, target.getHealth(), EPSILON, "Expired BurnStatus must not deal ghost damage");
        }

        @Test
        @DisplayName("BurnStatus under true 60 FPS (dt = 1.0f / 60.0f) delivers all 6 ticks (60.0 damage)")
        void testBurnTrue60Fps_1_Over_60() {
            DummyCharacter target = new DummyCharacter(100.0f, 100.0f, 100.0f);
            float dps = 20.0f;
            float duration = 3.0f;
            BurnStatus burn = new BurnStatus(dps, duration);
            target.addStatus(burn);

            float dt = 1.0f / 60.0f; // 30 frames per 0.5s interval
            for (int frame = 1; frame <= 185; frame++) {
                target.update(dt);
            }

            // Exactly 6 ticks over 3.0s (180 frames): total 60 damage dealt (health = 100 - 60 = 40)
            assertEquals(40.0f, target.getHealth(), EPSILON, "Under dt=1/60s, all 6 ticks fire delivering 60.0 damage");
            assertFalse(target.hasStatus(StatusKind.BURN));
        }

        @Test
        @DisplayName("BurnStatus with 3.05s duration under dt=0.016s permits frame 188 (3.008s) to deliver all 6 ticks")
        void testBurnDt0_016WithSlightSlack() {
            DummyCharacter target = new DummyCharacter(100.0f, 100.0f, 100.0f);
            float dps = 20.0f;
            BurnStatus burn = new BurnStatus(dps, 3.05f); // 3.05s allows 188th frame (3.008s) to trigger tick 6
            target.addStatus(burn);

            float dt = 0.016f;
            for (int frame = 1; frame <= 200; frame++) {
                target.update(dt);
            }

            assertEquals(40.0f, target.getHealth(), EPSILON, "All 6 ticks fire when 188th frame is permitted to complete");
            assertFalse(target.hasStatus(StatusKind.BURN));
        }

        @Test
        @DisplayName("BurnStatus boundary dt slice dt=0.49s ticks accurately when accumulated time crosses each 0.5s boundary")
        void testBurnBoundaryDtSlice_0_49() {
            DummyCharacter target = new DummyCharacter(100.0f, 50.0f, 100.0f);
            BurnStatus burn = new BurnStatus(20.0f, 3.0f); // 10 damage per tick
            target.addStatus(burn);

            float dt = 0.49f;

            // Frame 1 (t = 0.49s): tickTimer = 0.01s > 0 -> 0 damage
            target.update(dt);
            assertEquals(100.0f, target.getHealth(), EPSILON, "t=0.49s has not crossed 0.5s yet");

            // Frame 2 (t = 0.98s): crossed 0.5s -> tick 1 triggers (90 HP)
            target.update(dt);
            assertEquals(90.0f, target.getHealth(), EPSILON, "t=0.98s must trigger tick 1");

            // Frame 3 (t = 1.47s): crossed 1.0s -> tick 2 triggers (80 HP)
            target.update(dt);
            assertEquals(80.0f, target.getHealth(), EPSILON, "t=1.47s has crossed 1.0s, triggering tick 2");

            // Frame 4 (t = 1.96s): crossed 1.5s -> tick 3 triggers (70 HP)
            target.update(dt);
            assertEquals(70.0f, target.getHealth(), EPSILON, "t=1.96s has crossed 1.5s, triggering tick 3");

            // Frame 5 (t = 2.45s): crossed 2.0s -> tick 4 triggers (60 HP)
            target.update(dt);
            assertEquals(60.0f, target.getHealth(), EPSILON, "t=2.45s has crossed 2.0s, triggering tick 4");

            // Frame 6 (t = 2.94s): crossed 2.5s -> tick 5 triggers (50 HP)
            target.update(dt);
            assertEquals(50.0f, target.getHealth(), EPSILON, "t=2.94s has crossed 2.5s, triggering tick 5");

            // Frame 7 (t = 3.43s, remaining 0.06s processed): crossed 3.0s -> tick 6 triggers (40 HP)
            target.update(dt);
            assertEquals(40.0f, target.getHealth(), EPSILON, "Final partial slice processes remaining 0.06s and triggers tick 6");
            assertFalse(target.hasStatus(StatusKind.BURN), "BurnStatus expired after 3.0s");
        }

        @Test
        @DisplayName("BurnStatus boundary dt slice dt=0.51s triggers exactly 1 tick on frame 1")
        void testBurnBoundaryDtSlice_0_51() {
            DummyCharacter target = new DummyCharacter(100.0f, 50.0f, 100.0f);
            BurnStatus burn = new BurnStatus(20.0f, 2.0f);
            target.addStatus(burn);

            // Frame 1 (t = 0.51s): tickTimer = 0.50 - 0.51 = -0.01 <= 0 -> 1 tick triggers immediately
            target.update(0.51f);
            assertEquals(90.0f, target.getHealth(), EPSILON, "t=0.51s must trigger 1 tick on frame 1");
        }

        @Test
        @DisplayName("BurnStatus large dt slice dt=2.5s loops internally and triggers exactly 5 ticks in a single step")
        void testBurnLargeDtSlice_2_5() {
            DummyCharacter target = new DummyCharacter(150.0f, 80.0f, 100.0f);
            float dps = 10.0f; // 5.0 damage per tick
            BurnStatus burn = new BurnStatus(dps, 5.0f);
            target.addStatus(burn);

            // Single massive update: dt = 2.5s (5 intervals of 0.5s)
            target.update(2.5f);
            // Must have fired exactly 5 ticks = 25.0 true damage
            assertEquals(125.0f, target.getHealth(), EPSILON, "dt=2.5s must deliver exactly 5 ticks (25.0 true damage)");

            // Second update of dt = 2.5s completes the remaining 5.0s duration
            target.update(2.5f);
            assertEquals(100.0f, target.getHealth(), EPSILON, "Full 5.0s must deliver 50.0 total damage (10 ticks)");
            assertFalse(target.hasStatus(StatusKind.BURN), "Burn must expire cleanly after 5.0s");
        }

        @Test
        @DisplayName("BurnStatus true damage completely bypasses extreme armor A=10000")
        void testBurnTrueDamageBypassesExtremeArmor() {
            DummyCharacter bossTarget = new DummyCharacter(500.0f, 10000.0f, 50.0f);
            BurnStatus burn = new BurnStatus(40.0f, 2.0f); // 20.0 true damage per tick
            bossTarget.addStatus(burn);

            bossTarget.update(0.5f);
            // With normal mitigation, 20 damage against 10000 armor would deal 0.198 damage.
            // With true damage, it MUST deal exactly 20.0 damage!
            assertEquals(480.0f, bossTarget.getHealth(), EPSILON, "Burn DoT must ignore armor completely");
        }
    }

    // =========================================================================
    // Challenge 3: SlowStatus Clamping & Composite Speed with Slow Resistance
    // =========================================================================
    @Nested
    @DisplayName("Challenge 3: SlowStatus Clamping & Composite Speed Calculations")
    class SlowStatusChallenges {

        @Test
        @DisplayName("Negative slowFraction is clamped to 0.0f (multiplier = 1.0f)")
        void testSlowStatusClampingNegative() {
            SlowStatus slowNeg = new SlowStatus(-0.5f, 2.0f);
            assertEquals(0.0f, slowNeg.slowFraction(), EPSILON);
            assertEquals(0.0f, slowNeg.getSlowFraction(), EPSILON);
            assertEquals(1.0f, slowNeg.speedMultiplier(), EPSILON);

            SlowStatus slowNegHuge = new SlowStatus(-100.0f, 2.0f);
            assertEquals(0.0f, slowNegHuge.slowFraction(), EPSILON);
            assertEquals(1.0f, slowNegHuge.speedMultiplier(), EPSILON);
        }

        @Test
        @DisplayName("Excessive slowFraction > 0.9 is strictly clamped to 0.9f (minimum speed multiplier = 0.10f)")
        void testSlowStatusClampingExcessive() {
            SlowStatus slow95 = new SlowStatus(0.95f, 2.0f);
            assertEquals(0.9f, slow95.slowFraction(), EPSILON);
            assertEquals(0.1f, slow95.speedMultiplier(), EPSILON);

            SlowStatus slow100 = new SlowStatus(1.0f, 2.0f);
            assertEquals(0.9f, slow100.slowFraction(), EPSILON);
            assertEquals(0.1f, slow100.speedMultiplier(), EPSILON);

            SlowStatus slowHuge = new SlowStatus(50.0f, 2.0f);
            assertEquals(0.9f, slowHuge.slowFraction(), EPSILON);
            assertEquals(0.1f, slowHuge.speedMultiplier(), EPSILON);
        }

        @Test
        @DisplayName("Exact boundary values 0.0f and 0.9f produce 1.0f and 0.1f multipliers")
        void testSlowStatusExactBoundaries() {
            SlowStatus slowZero = new SlowStatus(0.0f, 1.0f);
            assertEquals(0.0f, slowZero.slowFraction(), EPSILON);
            assertEquals(1.0f, slowZero.speedMultiplier(), EPSILON);

            SlowStatus slowMax = new SlowStatus(0.9f, 1.0f);
            assertEquals(0.9f, slowMax.slowFraction(), EPSILON);
            assertEquals(0.10f, slowMax.speedMultiplier(), EPSILON);
        }

        @ParameterizedTest(name = "Slow={0}, Resistance={1} -> Expected Speed={2}")
        @CsvSource({
                "0.00, 0.00, 200.0", // No slow -> 200
                "0.50, 0.00, 100.0", // 50% slow, 0% res -> 100
                "0.90, 0.00,  20.0", // 90% slow, 0% res -> 20 (floor)
                "0.50, 0.50, 150.0", // 50% slow, 50% res -> effective slow = 25% -> 150
                "0.90, 0.50, 110.0", // 90% slow, 50% res -> effective slow = 45% -> 110
                "0.50, 1.00, 200.0", // 50% slow, 100% res -> full immunity -> 200
                "0.90, 1.00, 200.0", // 90% slow, 100% res -> full immunity -> 200
                "1.50, 0.00,  20.0", // Clamped to 0.90 -> 20
                "1.50, 0.50, 110.0"  // Clamped to 0.90, 50% res -> 110
        })
        @DisplayName("Composite speed calculation across various slowFraction and slowResistance combinations")
        void testCompositeSpeedWithResistance(float slowFraction, float resistance, float expectedSpeed) {
            DummyCharacter target = new DummyCharacter(100.0f, 0.0f, 200.0f);
            target.setSlowResistance(resistance);

            SlowStatus slow = new SlowStatus(slowFraction, 2.0f);
            target.addStatus(slow);

            assertEquals(expectedSpeed, target.getCurrentMoveSpeed(), 0.01f);
            assertEquals(expectedSpeed, target.currentMoveSpeed(), 0.01f);
        }

        @Test
        @DisplayName("Re-applying SlowStatus replaces/refreshes rather than compounding indefinitely")
        void testSlowStatusSingleKindRefresh() {
            DummyCharacter target = new DummyCharacter(100.0f, 0.0f, 200.0f);

            target.addStatus(new SlowStatus(0.3f, 1.0f)); // 30% slow -> 140 speed
            assertEquals(140.0f, target.getCurrentMoveSpeed(), 0.01f);

            // Re-apply 60% slow: should REPLACE existing slow, resulting in 80 speed
            // If it compounded, it would be 200 * 0.7 * 0.4 = 56 speed.
            target.addStatus(new SlowStatus(0.6f, 2.0f));
            assertEquals(80.0f, target.getCurrentMoveSpeed(), 0.01f, "Slow must replace existing instance, not compound");

            // Verify only 1 slow effect in list
            assertEquals(1, target.getStatuses().size());
        }

        @Test
        @DisplayName("SlowStatus expiration cleanly restores character base movement speed")
        void testSlowStatusExpiryRestoresSpeed() {
            DummyCharacter target = new DummyCharacter(100.0f, 0.0f, 200.0f);
            target.addStatus(new SlowStatus(0.5f, 1.0f)); // 1.0s duration

            target.update(0.5f);
            assertEquals(100.0f, target.getCurrentMoveSpeed(), 0.01f, "Mid-duration speed is halved");

            target.update(0.6f); // Total 1.1s > 1.0s -> expired
            assertEquals(200.0f, target.getCurrentMoveSpeed(), 0.01f, "Expired slow must restore base speed");
            assertFalse(target.hasStatus(StatusKind.SLOW));
        }
    }

    // =========================================================================
    // Challenge 4: Projectile Piercing Falloff, Floor Clamping & Multi-Hit Prevention
    // =========================================================================
    @Nested
    @DisplayName("Challenge 4: Projectile Piercing Falloff, Floor Clamping & Multi-Hit Prevention")
    class ProjectilePiercingChallenges {

        @Test
        @DisplayName("Piercing sequence: initial 100 -> hit 1 (80) -> hit 2 (64) -> hit 3 (51.2) -> destroyed")
        void testPiercingExactFalloffSequence() {
            Vector2D pos = new Vector2D(0.0f, 0.0f);
            Vector2D dir = new Vector2D(1.0f, 0.0f);
            Projectile p = new Projectile(pos, dir, 200.0f, 8.0f, 100.0f, 3.0f, 3, ProjectileFaction.PLAYER);

            // Pre-condition
            assertEquals(100.0f, p.getDamage(), EPSILON);
            assertEquals(100.0f, p.getInitialDamage(), EPSILON);
            assertEquals(3, p.getPierceRemaining());
            assertEquals(0, p.getHitCount());
            assertTrue(p.isAlive());

            // Hit 1: Enemy 101 struck
            assertTrue(p.canHit(101));
            p.recordHit(101);
            p.decrementPierce();
            assertTrue(p.isAlive(), "Projectile with pierce remaining must survive hit 1");
            assertEquals(2, p.getPierceRemaining());
            assertEquals(1, p.getHitCount());
            assertEquals(80.0f, p.getDamage(), EPSILON, "Hit 1 must reduce damage by 20% to 80.0");

            // Hit 2: Enemy 102 struck
            assertTrue(p.canHit(102));
            p.recordHit(102);
            p.decrementPierce();
            assertTrue(p.isAlive(), "Projectile with pierce remaining must survive hit 2");
            assertEquals(1, p.getPierceRemaining());
            assertEquals(2, p.getHitCount());
            assertEquals(64.0f, p.getDamage(), EPSILON, "Hit 2 must reduce damage by 20% to 64.0");

            // Hit 3: Enemy 103 struck
            assertTrue(p.canHit(103));
            p.recordHit(103);
            p.decrementPierce();
            assertTrue(p.isAlive(), "Projectile with pierce 0 remaining survives until next decrement");
            assertEquals(0, p.getPierceRemaining());
            assertEquals(3, p.getHitCount());
            assertEquals(51.2f, p.getDamage(), 0.001f, "Hit 3 must reduce damage by 20% to 51.2");

            // Hit 4: Enemy 104 struck when pierceRemaining == 0 -> projectile destroyed!
            assertTrue(p.canHit(104));
            p.recordHit(104);
            p.decrementPierce();
            assertFalse(p.isAlive(), "Projectile with pierce exhausted must be destroyed on subsequent hit");
            assertEquals(4, p.getHitCount());
        }

        @Test
        @DisplayName("25% minimum floor clamping: damage never falls below 25% of initial damage")
        void testDamageFloorClamping25Percent() {
            // Base damage = 100.0f -> 25% minimum floor is 25.0f
            Projectile p = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(1.0f, 0.0f),
                    100.0f, 10.0f, 100.0f, 2.0f, 3, ProjectileFaction.PLAYER);

            // 1st hit: 80.0
            p.decrementPierce();
            assertEquals(80.0f, p.getDamage(), EPSILON);

            // 2nd hit: 64.0
            p.decrementPierce();
            assertEquals(64.0f, p.getDamage(), EPSILON);

            // 3rd hit: 51.2
            p.decrementPierce();
            assertEquals(51.2f, p.getDamage(), 0.001f);

            // Artificially test repeated 0.8 falloffs on low damage starting value
            // E.g. Projectile with initialDamage = 10.0f -> 25% floor = 2.5f
            Projectile pLow = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(1.0f, 0.0f),
                    100.0f, 10.0f, 3.0f, 2.0f, 3, ProjectileFaction.PLAYER);
            // 3.0 * 0.8 = 2.4 < 2.5f (which is 25% of 10.0? No, initialDamage was 3.0f so floor = 0.75f)
            // Let's verify clamping formula directly:
            // pLow.damage = Math.max(initialDamage * 0.25f, damage * 0.80f)
            assertTrue(pLow.getDamage() >= pLow.getInitialDamage() * Projectile.MIN_DAMAGE_MULTIPLIER);
            for (int i = 0; i < 3; i++) {
                pLow.decrementPierce();
                assertTrue(pLow.getDamage() >= pLow.getInitialDamage() * Projectile.MIN_DAMAGE_MULTIPLIER);
            }
        }

        @Test
        @DisplayName("Multi-hit prevention: enemy overlapping projectile for 100 frames is hit exactly ONCE")
        void testMultiHitPreventionAcrossRepeatedFrames() {
            CombatSystem cs = new CombatSystem(new Rectangle(0, 0, 1000, 1000));
            Player player = new Player(new Vector2D(500, 500));

            // Enemy with 200 HP and 0 armor, placed at (100, 100)
            MeleeEnemy enemy = new MeleeEnemy(new Vector2D(100, 100));
            enemy.setMaxHealth(200.0f);
            enemy.setHealth(200.0f);
            enemy.setArmor(0.0f);
            List<Enemy> enemies = new ArrayList<>(Collections.singletonList(enemy));

            // Projectile overlapping enemy: position (100, 100), speed 0 (stationary to test overlap)
            Projectile p = new Projectile(
                    new Vector2D(100, 100), new Vector2D(1, 0),
                    10.0f, 15.0f, 40.0f, 5.0f, 3, ProjectileFaction.PLAYER
            );
            cs.addProjectile(p);

            // Frame 1: Collision occurs!
            cs.update(0.016f, player, enemies);
            assertEquals(160.0f, enemy.getHealth(), EPSILON, "Enemy must take exactly 40.0 damage on first collision frame");
            assertEquals(2, p.getPierceRemaining(), "Pierce remaining must decrease from 3 to 2");
            assertEquals(1, p.getHitCount(), "Hit count must be 1");
            assertFalse(p.canHit(enemy.getId()), "canHit must now be false for this enemy");

            // Frames 2 through 100: Enemy stays overlapping with projectile for 99 more simulation frames
            for (int frame = 2; frame <= 100; frame++) {
                cs.update(0.016f, player, enemies);
                assertEquals(160.0f, enemy.getHealth(), EPSILON, "Frame " + frame + ": Enemy must NOT take multi-hit damage");
                assertEquals(2, p.getPierceRemaining(), "Frame " + frame + ": Pierce count must NOT be decremented again");
                assertEquals(1, p.getHitCount(), "Frame " + frame + ": Hit count must remain 1");
            }

            // Frame 101: A distinct second enemy (ID different) enters collision range
            MeleeEnemy enemy2 = new MeleeEnemy(new Vector2D(100, 100));
            enemy2.setMaxHealth(200.0f);
            enemy2.setHealth(200.0f);
            enemy2.setArmor(0.0f);
            enemies.add(enemy2);

            cs.update(0.016f, player, enemies);
            // Enemy 1 still at 160 HP
            assertEquals(160.0f, enemy.getHealth(), EPSILON, "Enemy 1 must still not take additional damage");
            // Enemy 2 takes damage (damage was 40 * 0.8 = 32.0f)
            assertEquals(200.0f - 32.0f, enemy2.getHealth(), EPSILON, "Enemy 2 must take hit 2 damage");
            assertEquals(1, p.getPierceRemaining(), "Pierce count must decrease to 1");
            assertEquals(2, p.getHitCount(), "Hit count must be 2");
        }

        @Test
        @DisplayName("Non-piercing projectile (pierce = 0) is destroyed immediately on first hit")
        void testNonPiercingDestructionOnHit1() {
            Projectile p = new Projectile(new Vector2D(0, 0), new Vector2D(1, 0),
                    100.0f, 8.0f, 50.0f, 2.0f, 0, ProjectileFaction.PLAYER);

            assertEquals(0, p.getPierceRemaining());
            p.recordHit(999);
            p.decrementPierce();
            assertFalse(p.isAlive(), "Pierce 0 missile must be destroyed on hit 1");
        }
    }

    // =========================================================================
    // Challenge 5: ElementSet Bitmask Commutativity & Recipe Resolution
    // =========================================================================
    @Nested
    @DisplayName("Challenge 5: ElementSet Bitmask Commutativity & Recipe Resolution")
    class ElementSetAndRecipeChallenges {

        @Test
        @DisplayName("ElementSet bitmask commutativity holds for all 10 pairs of elements: {A, B} == {B, A}")
        void testBitmaskCommutativityAllPairs() {
            Element[] elements = Element.values();
            for (int i = 0; i < elements.length; i++) {
                for (int j = i + 1; j < elements.length; j++) {
                    Element a = elements[i];
                    Element b = elements[j];

                    ElementSet setAB = new ElementSet(a, b);
                    ElementSet setBA = new ElementSet(b, a);

                    // 1. Bitmask equality
                    assertEquals(setAB.getMask(), setBA.getMask(),
                            "Mask must be identical regardless of constructor order for (" + a + ", " + b + ")");
                    assertEquals(setAB.mask(), setBA.mask());

                    // 2. Equals method
                    assertEquals(setAB, setBA,
                            "setAB.equals(setBA) must return true for (" + a + ", " + b + ")");
                    assertEquals(setBA, setAB,
                            "setBA.equals(setAB) must return true for (" + a + ", " + b + ")");

                    // 3. HashCode contract
                    assertEquals(setAB.hashCode(), setBA.hashCode(),
                            "Hash codes must be equal for equivalent sets");

                    // 4. Element containment & count
                    assertEquals(2, setAB.count());
                    assertEquals(2, setBA.count());
                    assertTrue(setAB.contains(a));
                    assertTrue(setAB.contains(b));
                    assertTrue(setBA.contains(a));
                    assertTrue(setBA.contains(b));
                }
            }
        }

        @Test
        @DisplayName("ElementSet permutation invariance holds across 3 elements: {Fire, Ice, Wind}")
        void testPermutationInvarianceThreeElements() {
            ElementSet p1 = new ElementSet(Element.FIRE, Element.ICE, Element.WIND);
            ElementSet p2 = new ElementSet(Element.WIND, Element.FIRE, Element.ICE);
            ElementSet p3 = new ElementSet(Element.ICE, Element.WIND, Element.FIRE);

            assertEquals(p1, p2);
            assertEquals(p2, p3);
            assertEquals(p1.getMask(), p2.getMask());
            assertEquals(p2.getMask(), p3.getMask());
            assertEquals(p1.hashCode(), p2.hashCode());
            assertEquals(3, p1.count());
        }

        @Test
        @DisplayName("Duplicate additions to ElementSet are idempotent (set semantics)")
        void testDuplicateElementIdempotency() {
            ElementSet set = new ElementSet();
            set.add(Element.FIRE);
            set.add(Element.FIRE);
            set.add(Element.FIRE);

            assertEquals(1, set.count());
            assertEquals(new ElementSet(Element.FIRE), set);
            assertEquals(new ElementSet(Element.FIRE).getMask(), set.getMask());
        }

        @Test
        @DisplayName("Default SpellRegistry correctly resolves all 5 fundamental base spells")
        void testDefaultSpellRegistryBaseSpells() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();

            assertNotNull(registry.baseSpell(Element.FIRE));
            assertEquals("Firebolt", registry.baseSpell(Element.FIRE).getName());

            assertNotNull(registry.baseSpell(Element.ICE));
            assertEquals("Frost Shard", registry.baseSpell(Element.ICE).getName());

            assertNotNull(registry.baseSpell(Element.LIGHTNING));
            assertEquals("Spark Arc", registry.baseSpell(Element.LIGHTNING).getName());

            assertNotNull(registry.baseSpell(Element.WIND));
            assertEquals("Gale Burst", registry.baseSpell(Element.WIND).getName());

            assertNotNull(registry.baseSpell(Element.EARTH));
            assertEquals("Boulder Slam", registry.baseSpell(Element.EARTH).getName());
        }

        @Test
        @DisplayName("Default SpellRegistry correctly resolves combination recipes in either order")
        void testDefaultSpellRegistryCombinationsCommutative() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();

            // 1. Fire + Wind == Wind + Fire -> Firestorm
            SpellDefinition firestorm1 = registry.combination(Element.FIRE, Element.WIND);
            SpellDefinition firestorm2 = registry.combination(Element.WIND, Element.FIRE);
            assertNotNull(firestorm1);
            assertSame(firestorm1, firestorm2, "Firestorm recipe must resolve to same definition regardless of order");
            assertTrue(firestorm1.getName().contains("Firestorm"));

            // 2. Ice + Lightning == Lightning + Ice -> Frost Tempest
            SpellDefinition frostTempest1 = registry.combination(Element.ICE, Element.LIGHTNING);
            SpellDefinition frostTempest2 = registry.combination(Element.LIGHTNING, Element.ICE);
            assertNotNull(frostTempest1);
            assertSame(frostTempest1, frostTempest2);
            assertTrue(frostTempest1.getName().contains("Frost Tempest"));

            // 3. Earth + Fire == Fire + Earth -> Magma Core
            SpellDefinition magmaCore1 = registry.combination(Element.EARTH, Element.FIRE);
            SpellDefinition magmaCore2 = registry.combination(Element.FIRE, Element.EARTH);
            assertNotNull(magmaCore1);
            assertSame(magmaCore1, magmaCore2);
            assertTrue(magmaCore1.getName().contains("Magma Core"));
        }

        @Test
        @DisplayName("SpellRegistry.resolve resolves history mixes, combinations, and fallbacks")
        void testSpellRegistryResolveSequences() {
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();

            // Null or empty mix -> null
            assertNull(registry.resolve(null));
            assertNull(registry.resolve(Collections.emptyList()));

            // Single element -> base spell
            assertEquals("Firebolt", registry.resolve(Collections.singletonList(Element.FIRE)).getName());
            assertEquals("Frost Shard", registry.resolve(Collections.singletonList(Element.ICE)).getName());

            // Ordered combination
            SpellDefinition comboFW = registry.resolve(Arrays.asList(Element.FIRE, Element.WIND));
            assertTrue(comboFW.getName().contains("Firestorm"));

            // Reverse ordered combination
            SpellDefinition comboWF = registry.resolve(Arrays.asList(Element.WIND, Element.FIRE));
            assertTrue(comboWF.getName().contains("Firestorm"));

            // History sequence [Earth, Fire, Wind] -> last two are (Fire, Wind) -> Firestorm
            SpellDefinition comboHist = registry.resolve(Arrays.asList(Element.EARTH, Element.FIRE, Element.WIND));
            assertTrue(comboHist.getName().contains("Firestorm"));

            // History sequence [Fire, Earth, Ice] -> last two are (Earth, Ice): no combo registered -> fallback to Ice base spell
            SpellDefinition fallback = registry.resolve(Arrays.asList(Element.FIRE, Element.EARTH, Element.ICE));
            assertEquals("Frost Shard", fallback.getName(), "Unregistered pair must fallback to newest element base spell");

            // Repeating same element [Fire, Fire] -> no self-combo -> fallback to Firebolt
            SpellDefinition sameRepeat = registry.resolve(Arrays.asList(Element.FIRE, Element.FIRE));
            assertEquals("Firebolt", sameRepeat.getName());
        }
    }

    // =========================================================================
    // Challenge 6: Boss Colossus Overlord Volley Attack & Durability
    // =========================================================================
    @Nested
    @DisplayName("Challenge 6: Boss Colossus Overlord Volley Attack & Durability")
    class BossEncounterChallenges {

        @Test
        @DisplayName("Boss stats match Milestone 5 specifications: HP 1800, Armor 45, Radius 50, Volley CD 4.0s")
        void testBossStatsAndDurability() {
            BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 500.0f));

            assertTrue(boss.isBoss());
            assertEquals("ANCIENT GOLEM OVERLORD", boss.getName());
            assertEquals(1800.0f, boss.getMaxHealth(), EPSILON);
            assertEquals(1800.0f, boss.getHealth(), EPSILON);
            assertEquals(45.0f, boss.getArmor(), EPSILON);
            assertEquals(50.0f, boss.getRadius(), EPSILON);
            assertEquals(55.0f, boss.getBaseMoveSpeed(), EPSILON);
            assertEquals(45.0f, boss.getContactDamage(), EPSILON);
            assertEquals(250, boss.getExpReward());
        }

        @Test
        @DisplayName("Boss volley attack produces 3 enemy projectiles with exact 24° spread and cooldown enforcement")
        void testBossVolleyAttackMechanics() {
            BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 500.0f));
            assertTrue(boss.canFireVolley(), "Boss must be ready to fire volley initially");

            // Aim due East (X axis)
            Vector2D targetPos = new Vector2D(800.0f, 500.0f);
            List<Projectile> volley = boss.fireVolley(targetPos);

            // Volley properties
            assertEquals(3, volley.size(), "Volley must create exactly 3 projectiles");
            assertEquals(4.0f, boss.getVolleyTimer(), EPSILON, "Firing volley must reset cooldown to 4.0s");
            assertFalse(boss.canFireVolley(), "Boss cannot fire volley while on cooldown");

            float expectedSpreadRad = MathUtils.degToRad(24.0f);

            for (int i = 0; i < 3; i++) {
                Projectile p = volley.get(i);
                assertEquals(ProjectileFaction.ENEMY, p.getFaction());
                assertEquals(210.0f, p.getSpeed(), EPSILON);
                assertEquals(10.0f, p.getRadius(), EPSILON);
                assertEquals(20.0f, p.getDamage(), EPSILON);
                assertEquals(3.2f, p.getLifetime(), EPSILON);
                assertEquals(0, p.getPierceRemaining());

                // Angle check: i=0 is -24°, i=1 is 0°, i=2 is +24°
                float expectedAngle = (float) (i - 1) * expectedSpreadRad;
                float actualAngle = p.getDirection().angleRad();
                assertEquals(expectedAngle, actualAngle, 0.01f, "Projectile " + i + " must have expected spread angle");
            }

            // Advance 2.0s: halfway through cooldown
            boss.update(2.0f);
            assertEquals(2.0f, boss.getVolleyTimer(), EPSILON);
            assertFalse(boss.canFireVolley());

            // Advance remaining 2.05s: cooldown refreshed
            boss.update(2.05f);
            assertEquals(0.0f, boss.getVolleyTimer(), EPSILON);
            assertTrue(boss.canFireVolley(), "Boss must be ready to fire volley again after 4.0s cooldown");
        }
    }
}
