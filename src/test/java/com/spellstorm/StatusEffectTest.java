package com.spellstorm;

import com.spellstorm.combat.BurnStatus;
import com.spellstorm.combat.SlowStatus;
import com.spellstorm.combat.StatusEffectList;
import com.spellstorm.combat.StatusKind;
import com.spellstorm.entities.Character;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite covering StatusEffect hierarchy, Burn DoT periodic ticking & true damage,
 * SlowStatus clamping & resistance scaling, and StatusEffectList refresh/replace lifecycle.
 */
public class StatusEffectTest {

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
    // 1. BurnStatus: Periodic Ticking, True Damage, and Initial Delay
    // =========================================================================

    @Test
    @DisplayName("BurnStatus does not inflict damage at t=0 application (initial delay is 0.5s)")
    public void testBurnStatusNoFrameZeroDamage() {
        DummyCharacter target = new DummyCharacter(100.0f, 100.0f, 100.0f);
        BurnStatus burn = new BurnStatus(20.0f, 3.0f); // 20 DPS for 3.0s

        target.addStatus(burn);
        assertTrue(target.hasStatus(StatusKind.BURN));
        assertEquals(100.0f, target.getHealth(), 0.001f);

        // Advance 0.3s -> less than 0.5s tick interval: still no damage dealt
        target.update(0.3f);
        assertEquals(100.0f, target.getHealth(), 0.001f);
    }

    @Test
    @DisplayName("BurnStatus ticks every 0.5s dealing (dps * 0.5) true damage (ignores armor)")
    public void testBurnStatusPeriodicTicksAndArmorBypass() {
        // High armor (200) would mitigate normal damage by 66.7%
        DummyCharacter target = new DummyCharacter(200.0f, 200.0f, 100.0f);
        BurnStatus burn = new BurnStatus(30.0f, 2.0f); // 30 DPS -> 15 damage per 0.5s tick

        target.addStatus(burn);

        // First tick at t = 0.5s: inflicts exactly 15.0 damage
        target.update(0.5f);
        assertEquals(185.0f, target.getHealth(), 0.01f);

        // Second tick at t = 1.0s: inflicts another 15.0 damage
        target.update(0.5f);
        assertEquals(170.0f, target.getHealth(), 0.01f);

        // Third tick at t = 1.5s: inflicts another 15.0 damage
        target.update(0.5f);
        assertEquals(155.0f, target.getHealth(), 0.01f);

        // Fourth tick at t = 2.0s: inflicts final 15.0 damage, effect expires
        target.update(0.5f);
        assertEquals(140.0f, target.getHealth(), 0.01f);
        assertFalse(target.hasStatus(StatusKind.BURN), "BurnStatus should expire after duration (2.0s)");
    }

    // =========================================================================
    // 2. SlowStatus: Fraction Clamping & Character Speed Multiplier
    // =========================================================================

    @Test
    @DisplayName("SlowStatus clamps slowFraction to [0.0, 0.9] (floor speed multiplier is 0.10)")
    public void testSlowStatusClamping() {
        SlowStatus normalSlow = new SlowStatus(0.4f, 2.0f);
        assertEquals(0.4f, normalSlow.getSlowFraction(), 0.001f);
        assertEquals(0.6f, normalSlow.speedMultiplier(), 0.001f);

        // Negative slow clamped to 0.0
        SlowStatus negSlow = new SlowStatus(-0.5f, 2.0f);
        assertEquals(0.0f, negSlow.getSlowFraction(), 0.001f);
        assertEquals(1.0f, negSlow.speedMultiplier(), 0.001f);

        // Excessive slow (> 0.9) clamped to 0.90
        SlowStatus maxSlow = new SlowStatus(1.5f, 2.0f);
        assertEquals(0.9f, maxSlow.getSlowFraction(), 0.001f);
        assertEquals(0.1f, maxSlow.speedMultiplier(), 0.001f);
    }

    @Test
    @DisplayName("Character movement speed under Slow with slow resistance scaling (C++ parity)")
    public void testCharacterSlowWithResistance() {
        DummyCharacter ch = new DummyCharacter(100.0f, 50.0f, 100.0f);
        assertEquals(100.0f, ch.getCurrentMoveSpeed(), 0.01f);

        // 40% slow with 0% resistance -> speed = 100 * (1 - 0.40) = 60.0
        ch.addStatus(new SlowStatus(0.4f, 2.0f));
        assertTrue(ch.hasStatus(StatusKind.SLOW));
        assertEquals(60.0f, ch.getCurrentMoveSpeed(), 0.01f);
        assertEquals(60.0f, ch.currentMoveSpeed(), 0.01f);

        // Set 50% slow resistance -> effective slow = 0.40 * (1 - 0.5) = 0.20 -> speed = 80.0
        ch.setSlowResistance(0.5f);
        assertEquals(80.0f, ch.getCurrentMoveSpeed(), 0.01f);

        // Set 100% slow resistance -> immune to slow -> speed = 100.0
        ch.setSlowResistance(1.0f);
        assertEquals(100.0f, ch.getCurrentMoveSpeed(), 0.01f);
    }

    // =========================================================================
    // 3. StatusEffectList: Refresh/Replace Policy & Composite Multiplier
    // =========================================================================

    @Test
    @DisplayName("StatusEffectList refreshes/replaces same-kind effect instead of stacking infinite instances")
    public void testStatusEffectRefreshReplace() {
        StatusEffectList list = new StatusEffectList();
        DummyCharacter target = new DummyCharacter(100.0f, 0.0f, 100.0f);

        list.add(new SlowStatus(0.3f, 2.0f));
        assertEquals(1, list.size());
        assertEquals(0.7f, list.speedMultiplier(), 0.01f);

        // Re-applying SlowStatus with 50% slow and 4.0s duration replaces the old one
        list.add(new SlowStatus(0.5f, 4.0f));
        assertEquals(1, list.size(), "List must contain exactly 1 SlowStatus instance");
        assertEquals(0.5f, list.speedMultiplier(), 0.01f);

        // Different kind (Burn) can coexist with Slow
        list.add(new BurnStatus(10.0f, 3.0f));
        assertEquals(2, list.size());
        assertTrue(list.has(StatusKind.SLOW));
        assertTrue(list.has(StatusKind.BURN));
    }

    @Test
    @DisplayName("StatusEffectList expiry cleanup when duration reaches zero")
    public void testStatusEffectExpiryCleanup() {
        StatusEffectList list = new StatusEffectList();
        DummyCharacter target = new DummyCharacter(100.0f, 0.0f, 100.0f);

        list.add(new SlowStatus(0.5f, 1.0f));
        assertEquals(1, list.size());

        // Update 0.6s -> remaining = 0.4s: still active
        list.update(0.6f, target);
        assertEquals(1, list.size());
        assertTrue(list.has(StatusKind.SLOW));

        // Update another 0.5s -> total 1.1s > 1.0s: expired and removed
        list.update(0.5f, target);
        assertEquals(0, list.size());
        assertFalse(list.has(StatusKind.SLOW));
        assertEquals(1.0f, list.speedMultiplier(), 0.01f);
    }
}
