package com.spellstorm;

import com.spellstorm.combat.DamageInfo;
import com.spellstorm.combat.DamageMitigation;
import com.spellstorm.entities.Character;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite covering pure damage mitigation math, armor diminishing returns curve,
 * armor bypass (true damage), non-positive inputs, and Character combat reactions.
 */
public class CombatDamageTest {

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
    // 1. Damage Mitigation Formula & Diminishing Returns Curve
    // =========================================================================

    @Test
    @DisplayName("Damage mitigation at standard milestones: 0, 50, 100, 300 armor (C++ parity)")
    public void testDamageMitigationMilestones() {
        // 0 armor: 100% damage dealt
        assertEquals(100.0f, DamageMitigation.calculateDamage(100.0f, 0.0f), 0.01f);
        assertEquals(100.0f, DamageMitigation.mitigateDamage(100.0f, 0.0f), 0.01f);

        // 25 armor (Tank): 100 * (100 / 125) = 80.0f (20% reduction)
        assertEquals(80.0f, DamageMitigation.calculateDamage(100.0f, 25.0f), 0.01f);

        // 30 armor (Player): 100 * (100 / 130) = 76.923f
        assertEquals(76.923f, DamageMitigation.calculateDamage(100.0f, 30.0f), 0.01f);

        // 45 armor (Boss): 100 * (100 / 145) = 68.966f
        assertEquals(68.966f, DamageMitigation.calculateDamage(100.0f, 45.0f), 0.01f);

        // 50 armor: 100 * (100 / 150) = 66.667f (33.33% reduction)
        assertEquals(66.667f, DamageMitigation.calculateDamage(100.0f, 50.0f), 0.01f);

        // 100 armor: 100 * (100 / 200) = 50.0f (exactly halves incoming damage)
        assertEquals(50.0f, DamageMitigation.calculateDamage(100.0f, 100.0f), 0.01f);

        // 300 armor: 100 * (100 / 400) = 25.0f (75% reduction)
        assertEquals(25.0f, DamageMitigation.calculateDamage(100.0f, 300.0f), 0.01f);
    }

    @ParameterizedTest(name = "Damage {0} against armor {1} -> expected {2}")
    @CsvSource({
            "50.0, 0.0, 50.0",
            "50.0, 100.0, 25.0",
            "200.0, 100.0, 100.0",
            "40.0, 25.0, 32.0",
            "1000.0, 900.0, 100.0"
    })
    @DisplayName("Parameterized armor mitigation calculations")
    public void testParameterizedMitigation(float rawDamage, float armor, float expected) {
        assertEquals(expected, DamageMitigation.calculateDamage(rawDamage, armor), 0.01f);
    }

    @Test
    @DisplayName("Asymptotic diminishing returns: higher armor yields monotonically smaller marginal mitigation")
    public void testDiminishingReturnsMonotonicity() {
        float raw = 1000.0f;
        float prevDamage = raw;
        for (float armor = 0.0f; armor <= 1000.0f; armor += 50.0f) {
            float dealt = DamageMitigation.calculateDamage(raw, armor);
            assertTrue(dealt <= prevDamage, "Damage dealt must decrease monotonically with increasing armor");
            assertTrue(dealt > 0.0f, "Damage dealt must remain positive for positive raw damage");
            prevDamage = dealt;
        }
    }

    // =========================================================================
    // 2. Negative, Zero, and Boundary Values
    // =========================================================================

    @Test
    @DisplayName("Non-positive raw damage returns zero (no negative damage or phantom healing)")
    public void testNonPositiveDamageHandling() {
        assertEquals(0.0f, DamageMitigation.calculateDamage(0.0f, 50.0f));
        assertEquals(0.0f, DamageMitigation.calculateDamage(-10.0f, 50.0f));
        assertEquals(0.0f, DamageMitigation.calculateDamage(-9999.0f, 100.0f));
    }

    @Test
    @DisplayName("Negative armor is clamped to zero (no damage amplification above 100%)")
    public void testNegativeArmorClamping() {
        assertEquals(100.0f, DamageMitigation.calculateDamage(100.0f, -10.0f), 0.01f);
        assertEquals(100.0f, DamageMitigation.calculateDamage(100.0f, -500.0f), 0.01f);
    }

    // =========================================================================
    // 3. Character Combat Reaction: Mitigation, True Damage, Healing
    // =========================================================================

    @Test
    @DisplayName("Character taking mitigated damage (C++ test_combat.cpp parity)")
    public void testCharacterMitigatedDamage() {
        DummyCharacter ch = new DummyCharacter(100.0f, 50.0f, 100.0f);
        assertEquals(100.0f, ch.getHealth(), 0.01f);
        assertTrue(ch.isAlive());

        // 100 raw damage against 50 armor => deals 100 * (100 / 150) = 66.6667
        float dealt = ch.takeDamage(new DamageInfo(100.0f, false));
        assertEquals(66.667f, dealt, 0.1f);
        assertEquals(33.333f, ch.getHealth(), 0.1f);
        assertTrue(ch.isAlive());

        // Healing restores health up to maxHealth
        ch.heal(20.0f);
        assertEquals(53.333f, ch.getHealth(), 0.1f);
    }

    @Test
    @DisplayName("True damage bypasses armor mitigation completely")
    public void testTrueDamageArmorBypass() {
        DummyCharacter ch = new DummyCharacter(200.0f, 100.0f, 100.0f);

        // Armor 100 would mitigate 50% of normal damage, but ignoreArmor=true takes full 40
        float dealt = ch.takeDamage(new DamageInfo(40.0f, true));
        assertEquals(40.0f, dealt, 0.01f);
        assertEquals(160.0f, ch.getHealth(), 0.01f);
    }

    @Test
    @DisplayName("Overkill damage clamps health to 0 and destroys character")
    public void testOverkillAndDeath() {
        DummyCharacter ch = new DummyCharacter(50.0f, 10.0f, 100.0f);
        float dealt = ch.takeDamage(new DamageInfo(1000.0f, true));
        assertEquals(1000.0f, dealt, 0.01f);
        assertEquals(0.0f, ch.getHealth(), 0.01f);
        assertFalse(ch.isAlive());

        // Subsequent damage against dead target is ignored
        float postMortem = ch.takeDamage(new DamageInfo(50.0f, false));
        assertEquals(0.0f, postMortem, 0.01f);

        // Dead character cannot be healed
        ch.heal(50.0f);
        assertEquals(0.0f, ch.getHealth(), 0.01f);
    }
}
