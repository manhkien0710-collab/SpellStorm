package com.spellstorm;

import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.WorldPowerUp;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ExperienceSystem;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.systems.UpgradeManager;
import com.spellstorm.systems.UpgradeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: Progression, EXP, Perks and Power-Up Comprehensive Integration Suite")
public class ProgressionAndPowerUpTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Tier 1: Experience Drops, Magnet Pull and Linear Level Curve")
    public void testExperienceAndProgression() {
        ExperienceSystem expSystem = new ExperienceSystem();
        PlayerProgression prog = new PlayerProgression(1);
        Player player = new Player(new Vector2D(1000f, 1000f));
        player.setPickupRadius(45.0f);

        // Spawn Cyan gem (10 EXP)
        expSystem.spawnPickup(1000f, 1030f, 10);
        assertEquals(1, expSystem.getPickupCount());

        expSystem.update(0.016f, player, prog);
        assertEquals(0, expSystem.getPickupCount());
        assertEquals(10, prog.getCurrentExp());
        assertEquals(1, prog.getLevel());

        // Spawn Emerald gem (25 EXP) -> triggers level up (10 + 25 = 35 >= 25 -> Level 2, curExp 10)
        expSystem.spawnPickup(1000f, 1020f, 25);
        expSystem.update(0.016f, player, prog);
        assertEquals(2, prog.getLevel());
        assertEquals(10, prog.getCurrentExp());
        assertEquals(1, prog.getPendingLevelUps());
        assertTrue(prog.hasPendingLevelUp());
    }

    @Test
    @DisplayName("Tier 2: 7 Perks Stat Upgrades and Max Ranks")
    public void testUpgradePerksApplication() {
        UpgradeManager manager = new UpgradeManager();
        Player player = new Player();

        for (UpgradeType t : UpgradeType.values()) {
            assertEquals(0, manager.getRank(t));
            assertTrue(manager.applyUpgrade(t, player));
            assertEquals(1, manager.getRank(t));
        }

        // Apply Piercing 2nd time -> reaches max rank 2
        assertTrue(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, manager.getRank(UpgradeType.PIERCING));
        assertTrue(manager.isMaxRank(UpgradeType.PIERCING));

        // 3rd time is rejected
        assertFalse(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, manager.getRank(UpgradeType.PIERCING));
    }

    @Test
    @DisplayName("Tier 3: Power-Up Active Duration, Pause Suspension, and Modifiers")
    public void testPowerUpSystemIntegration() {
        PowerUpManager manager = new PowerUpManager();
        Player player = new Player(new Vector2D(500f, 500f));

        // Activate Arcane Overdrive
        manager.getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
        assertEquals(1.0f / 1.8f, manager.getCastCooldownMultiplier(), 1e-4f);
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 1e-4f);

        // Pause keeps duration intact
        manager.update(2.0f, player, true);
        assertEquals(5.0f, manager.getActiveAbility().getRemainingDuration(), 1e-4f);

        // Unpaused ticks duration
        manager.update(1.0f, player, false);
        assertEquals(4.0f, manager.getActiveAbility().getRemainingDuration(), 1e-4f);

        // Expiry cleans up modifiers
        manager.update(4.5f, player, false);
        assertFalse(manager.hasActiveAbility());
        assertEquals(1.0f, manager.getCastCooldownMultiplier(), 1e-4f);
    }
}
