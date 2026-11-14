package com.spellstorm;

import com.spellstorm.entities.Player;
import com.spellstorm.entities.WorldPowerUp;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: Power-Up 5.0s Lifecycle, Pause Suspension, Recollection & Modifiers")
public class PowerUpLifecycleTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("ActiveTemporaryAbility: Exact 5.0s Duration, Countdown & Expiry Reversion")
    public void testActiveAbilityDurationAndExpiry() {
        ActiveTemporaryAbility ability = new ActiveTemporaryAbility();
        assertFalse(ability.isActive());
        assertEquals(0.0f, ability.getRemainingDuration(), 1e-4f);

        ability.activate(PowerUpType.ARCANE_OVERDRIVE);
        assertTrue(ability.isActive());
        assertEquals(PowerUpType.ARCANE_OVERDRIVE, ability.getType());
        assertEquals(5.0f, ability.getRemainingDuration(), 1e-4f);
        assertEquals(1.0f, ability.getDurationRatio(), 1e-4f);

        // Update 1.5s -> 3.5s remaining
        ability.update(1.5f);
        assertTrue(ability.isActive());
        assertEquals(3.5f, ability.getRemainingDuration(), 1e-4f);
        assertEquals(3.5f / 5.0f, ability.getDurationRatio(), 1e-4f);

        // Update 3.5s -> 0.0s remaining (expired)
        ability.update(3.5f);
        assertFalse(ability.isActive());
        assertEquals(0.0f, ability.getRemainingDuration(), 1e-4f);
        assertEquals(0.0f, ability.getDurationRatio(), 1e-4f);

        // Further updates remain expired
        ability.update(1.0f);
        assertFalse(ability.isActive());
        assertEquals(0.0f, ability.getRemainingDuration(), 1e-4f);
    }

    @Test
    @DisplayName("Recollection Policy: Same-Type Refresh vs Different-Type Replacement")
    public void testRecollectionPolicy() {
        PowerUpManager manager = new PowerUpManager();
        Player player = new Player(new Vector2D(500f, 500f));
        player.setPickupRadius(45.0f);

        // Collect Arcane Overdrive
        manager.spawnPickup(500f, 510f, PowerUpType.ARCANE_OVERDRIVE);
        manager.update(0.016f, player, false);
        assertTrue(manager.hasActiveAbility());
        assertEquals(PowerUpType.ARCANE_OVERDRIVE, manager.getActiveAbility().getType());
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 0.02f);

        // Simulate 2.0s ticking down -> 3.0s left
        manager.update(2.0f, player, false);
        assertEquals(3.0f, manager.getActiveAbility().getRemainingDuration(), 0.05f);

        // 1. Same-type pickup: refreshes to 5.0s
        manager.spawnPickup(500f, 510f, PowerUpType.ARCANE_OVERDRIVE);
        manager.update(0.016f, player, false);
        assertEquals(PowerUpType.ARCANE_OVERDRIVE, manager.getActiveAbility().getType());
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 0.02f);

        // Simulate 2.5s ticking down -> 2.5s left
        manager.update(2.5f, player, false);
        assertEquals(2.5f, manager.getActiveAbility().getRemainingDuration(), 0.05f);

        // 2. Different-type pickup: replaces ability and resets to 5.0s
        manager.spawnPickup(500f, 510f, PowerUpType.PIERCING_SURGE);
        manager.update(0.016f, player, false);
        assertEquals(PowerUpType.PIERCING_SURGE, manager.getActiveAbility().getType());
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 0.02f);
    }

    @Test
    @DisplayName("Modal Pause Suspension: Power-Up Duration and Ground Lifetime Frozen")
    public void testPauseSuspension() {
        PowerUpManager manager = new PowerUpManager();
        Player player = new Player(new Vector2D(500f, 500f));

        // Activate Piercing Surge
        manager.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 1e-4f);

        // Spawn a ground pickup
        manager.spawnPickup(700f, 700f, PowerUpType.ELEMENTAL_BURST);
        assertEquals(1, manager.getPickups().size());
        float groundLifeBefore = manager.getPickups().get(0).getLifetime();
        assertEquals(25.0f, groundLifeBefore, 1e-4f);

        // Update with paused = true for 3.0 seconds
        manager.update(3.0f, player, true);

        // Timers must remain strictly unchanged!
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 1e-4f);
        assertTrue(manager.hasActiveAbility());
        assertEquals(25.0f, manager.getPickups().get(0).getLifetime(), 1e-4f);

        // Unpaused update for 1.0s ticks normally
        manager.update(1.0f, player, false);
        assertEquals(4.0f, manager.getActiveAbility().getRemainingDuration(), 1e-3f);
        assertEquals(24.0f, manager.getPickups().get(0).getLifetime(), 1e-3f);
    }

    @Test
    @DisplayName("Combat Modifiers & Clean Restoration upon Expiry")
    public void testCombatModifiersAndRestoration() {
        PowerUpManager manager = new PowerUpManager();

        // Inactive default modifiers
        assertEquals(1.0f, manager.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(0, manager.getBonusPierce());
        assertFalse(manager.isElementalBurstActive());

        // 1. Arcane Overdrive: cast cooldown * (1.0 / 1.8)
        manager.getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
        assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(0, manager.getBonusPierce());
        assertFalse(manager.isElementalBurstActive());

        // 2. Piercing Surge: +2 bonus pierce
        manager.getActiveAbility().activate(PowerUpType.PIERCING_SURGE);
        assertEquals(1.0f, manager.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(2, manager.getBonusPierce());
        assertFalse(manager.isElementalBurstActive());

        // 3. Elemental Burst: isElementalBurstActive = true
        manager.getActiveAbility().activate(PowerUpType.ELEMENTAL_BURST);
        assertEquals(1.0f, manager.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(0, manager.getBonusPierce());
        assertTrue(manager.isElementalBurstActive());

        // Expire ability (5.1s)
        manager.getActiveAbility().update(5.1f);
        assertFalse(manager.hasActiveAbility());
        // Clean reversion to defaults
        assertEquals(1.0f, manager.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(0, manager.getBonusPierce());
        assertFalse(manager.isElementalBurstActive());
    }

    @Test
    @DisplayName("WorldPowerUp: Ground Lifetime, 4.0s Blinking Phase & Collection Radius")
    public void testWorldPowerUpGroundBehavior() {
        WorldPowerUp pickup = new WorldPowerUp(new Vector2D(100f, 100f), PowerUpType.ARCANE_OVERDRIVE);
        assertEquals(18.0f, pickup.getRadius(), 1e-4f);
        assertEquals(25.0f, pickup.getLifetime(), 1e-4f);
        assertFalse(pickup.isBlinking());
        assertEquals(1.0f, pickup.getAlphaFactor(), 1e-4f);
        assertTrue(pickup.isAlive());

        // Advance 21.5s -> 3.5s remaining (< 4.0s blink threshold)
        pickup.update(21.5f);
        assertEquals(3.5f, pickup.getLifetime(), 1e-3f);
        assertTrue(pickup.isBlinking());
        assertTrue(pickup.getAlphaFactor() >= 40f / 255f && pickup.getAlphaFactor() <= 1.0f);

        // Advance to 0s -> expires and is no longer alive
        pickup.update(3.6f);
        assertEquals(0.0f, pickup.getLifetime(), 1e-4f);
        assertFalse(pickup.isAlive());

        // Collection radius check: 18.0 + 45.0 = 63.0 px
        Player player = new Player(new Vector2D(200f, 200f));
        player.setPickupRadius(45.0f);

        WorldPowerUp pFar = new WorldPowerUp(new Vector2D(200f, 265f), PowerUpType.PIERCING_SURGE); // 65px > 63px
        float distSqFar = pFar.getPosition().dist2(player.getPosition());
        float collRadius = pFar.radius + player.getPickupRadius();
        assertTrue(distSqFar > collRadius * collRadius);

        WorldPowerUp pClose = new WorldPowerUp(new Vector2D(200f, 260f), PowerUpType.PIERCING_SURGE); // 60px <= 63px
        float distSqClose = pClose.getPosition().dist2(player.getPosition());
        assertTrue(distSqClose <= collRadius * collRadius);
    }

    @Test
    @DisplayName("Tier 1 (Feature 31): Ground power-up spawn attributes and palettes for all 3 types")
    public void testGroundPowerUpSpawnAttributes() {
        for (PowerUpType type : PowerUpType.values()) {
            WorldPowerUp powerUp = new WorldPowerUp(new Vector2D(100f, 100f), type);
            assertEquals(type, powerUp.getType());
            assertEquals(18.0f, powerUp.getRadius(), 1e-4f);
            assertEquals(25.0f, powerUp.getLifetime(), 1e-4f);
            assertEquals(25.0f, powerUp.getMaxLifetime(), 1e-4f);
            assertFalse(powerUp.isBlinking());
            assertEquals(1.0f, powerUp.getAlphaFactor(), 1e-4f);
            assertTrue(powerUp.isAlive());
            assertNotNull(powerUp.getPrimaryColor());
            assertNotNull(powerUp.getSecondaryColor());
        }
    }

    @Test
    @DisplayName("Tier 2 (Feature 31): Blinking alpha factor formula and boundaries")
    public void testBlinkingAlphaFactorFormula() {
        WorldPowerUp powerUp = new WorldPowerUp(new Vector2D(100f, 100f), PowerUpType.ARCANE_OVERDRIVE);

        // Before 4.0s threshold: alpha factor must be exactly 1.0f
        assertEquals(1.0f, powerUp.getAlphaFactor(), 1e-4f);
        powerUp.update(20.0f); // 5.0s remaining >= 4.0s
        assertEquals(5.0f, powerUp.getLifetime(), 1e-3f);
        assertFalse(powerUp.isBlinking());
        assertEquals(1.0f, powerUp.getAlphaFactor(), 1e-4f);

        // Exactly at or below 4.0s threshold
        powerUp.update(1.01f); // 3.99s remaining < 4.0s
        assertTrue(powerUp.isBlinking());

        // Sample through remaining lifetime in 0.1s steps, verifying alpha clamp [40/255, 1.0]
        float minAlpha = 40f / 255f;
        float maxAlpha = 1.0f;
        boolean observedOscillation = false;
        float lastAlpha = powerUp.getAlphaFactor();

        while (powerUp.getLifetime() > 0.1f) {
            powerUp.update(0.1f);
            float alpha = powerUp.getAlphaFactor();
            assertTrue(alpha >= minAlpha - 1e-4f && alpha <= maxAlpha + 1e-4f,
                    "Alpha factor " + alpha + " must be clamped within [" + minAlpha + ", " + maxAlpha + "]");
            if (Math.abs(alpha - lastAlpha) > 0.05f) {
                observedOscillation = true;
            }
            lastAlpha = alpha;
        }

        assertTrue(observedOscillation, "Alpha factor must oscillate during blinking phase");
    }
}
