package com.spellstorm;

import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeManager;
import com.spellstorm.systems.UpgradeType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: 7 Upgrade Perks, Multipliers, Rank Caps & Deduplication")
public class UpgradePerksTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("7 Perks Registration and Initial Rank 0")
    public void testPerksRegistration() {
        UpgradeManager manager = new UpgradeManager();
        List<UpgradeInfo> all = manager.getUpgrades();
        assertEquals(7, all.size());

        for (UpgradeType type : UpgradeType.values()) {
            UpgradeInfo info = manager.getInfo(type);
            assertNotNull(info, "Perk must exist: " + type);
            assertEquals(0, info.currentRank);
            if (type == UpgradeType.PIERCING) {
                assertEquals(2, info.maxRank);
            } else {
                assertEquals(5, info.maxRank);
            }
        }
    }

    @Test
    @DisplayName("Stat Multipliers Application to Player")
    public void testStatMultipliers() {
        UpgradeManager manager = new UpgradeManager();
        Player player = new Player(new Vector2D(100f, 100f));

        // MaxHealth: +40 HP & MaxHP
        float initialHealth = player.getHealth();
        float initialMaxHealth = player.getMaxHealth();
        assertTrue(manager.applyUpgrade(UpgradeType.MAX_HEALTH, player));
        assertEquals(initialMaxHealth + 40.0f, player.getMaxHealth(), 1e-4f);
        assertEquals(initialHealth + 40.0f, player.getHealth(), 1e-4f);
        assertEquals(1, manager.getRank(UpgradeType.MAX_HEALTH));

        // MoveSpeed: *1.12
        float initialSpeed = player.getBaseMoveSpeed();
        assertTrue(manager.applyUpgrade(UpgradeType.MOVE_SPEED, player));
        assertEquals(initialSpeed * 1.12f, player.getBaseMoveSpeed(), 1e-3f);
        assertEquals(1, manager.getRank(UpgradeType.MOVE_SPEED));

        // Damage: *1.20
        float initialDmg = player.getDamageMultiplier();
        assertTrue(manager.applyUpgrade(UpgradeType.DAMAGE, player));
        assertEquals(initialDmg * 1.20f, player.getDamageMultiplier(), 1e-4f);
        assertEquals(1, manager.getRank(UpgradeType.DAMAGE));

        // CastCooldown: *0.88
        float initialCd = player.getBaseCastCooldown();
        assertTrue(manager.applyUpgrade(UpgradeType.CAST_COOLDOWN, player));
        assertEquals(initialCd * 0.88f, player.getBaseCastCooldown(), 1e-4f);
        assertEquals(1, manager.getRank(UpgradeType.CAST_COOLDOWN));

        // ProjectileSpeed: *1.20
        float initialProjSpeed = player.getProjectileSpeedMultiplier();
        assertTrue(manager.applyUpgrade(UpgradeType.PROJECTILE_SPEED, player));
        assertEquals(initialProjSpeed * 1.20f, player.getProjectileSpeedMultiplier(), 1e-4f);
        assertEquals(1, manager.getRank(UpgradeType.PROJECTILE_SPEED));

        // ProjectileLifetime: *1.25
        float initialLifetime = player.getProjectileLifetimeMultiplier();
        assertTrue(manager.applyUpgrade(UpgradeType.PROJECTILE_LIFETIME, player));
        assertEquals(initialLifetime * 1.25f, player.getProjectileLifetimeMultiplier(), 1e-4f);
        assertEquals(1, manager.getRank(UpgradeType.PROJECTILE_LIFETIME));

        // Piercing: +1 bonus pierce
        assertEquals(0, player.getBonusPierce());
        assertTrue(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(1, player.getBonusPierce());
        assertEquals(1, manager.getRank(UpgradeType.PIERCING));
    }

    @Test
    @DisplayName("Max Rank Caps: Piercing = 2, All Others = 5; 6th or 3rd application rejected")
    public void testRankCapsAndRejection() {
        UpgradeManager manager = new UpgradeManager();
        Player player = new Player(new Vector2D(0, 0));

        // Piercing: max 2 ranks
        assertTrue(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(1, manager.getRank(UpgradeType.PIERCING));
        assertFalse(manager.isMaxRank(UpgradeType.PIERCING));

        assertTrue(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, manager.getRank(UpgradeType.PIERCING));
        assertTrue(manager.isMaxRank(UpgradeType.PIERCING));

        // 3rd application rejected
        assertFalse(manager.applyUpgrade(UpgradeType.PIERCING, player));
        assertEquals(2, manager.getRank(UpgradeType.PIERCING));
        assertEquals(2, player.getBonusPierce()); // Stays clamped at 2

        // MaxHealth: max 5 ranks
        for (int i = 0; i < 5; i++) {
            assertTrue(manager.applyUpgrade(UpgradeType.MAX_HEALTH, player));
        }
        assertEquals(5, manager.getRank(UpgradeType.MAX_HEALTH));
        assertTrue(manager.isMaxRank(UpgradeType.MAX_HEALTH));

        // 6th application rejected
        float maxHpBefore = player.getMaxHealth();
        assertFalse(manager.applyUpgrade(UpgradeType.MAX_HEALTH, player));
        assertEquals(5, manager.getRank(UpgradeType.MAX_HEALTH));
        assertEquals(maxHpBefore, player.getMaxHealth(), 1e-4f);
    }

    @Test
    @DisplayName("Fisher-Yates 3-Card Draw: Mutual Uniqueness (No Duplicates) and Capped Exclusion")
    public void testFisherYatesDeduplicationAndCappedExclusion() {
        UpgradeManager manager = new UpgradeManager();
        Random rng = new Random(12345);

        // 100 consecutive draws must have no duplicate perks in the hand
        for (int round = 0; round < 100; round++) {
            List<UpgradeInfo> hand = manager.getAvailableUpgrades(3, rng);
            assertEquals(3, hand.size());
            Set<UpgradeType> seen = new HashSet<>();
            for (UpgradeInfo up : hand) {
                assertTrue(seen.add(up.type), "Draw must not contain duplicates: " + up.type);
            }
        }

        // Max out Piercing (2 ranks)
        Player dummy = new Player();
        manager.applyUpgrade(UpgradeType.PIERCING, dummy);
        manager.applyUpgrade(UpgradeType.PIERCING, dummy);
        assertTrue(manager.isMaxRank(UpgradeType.PIERCING));

        // Draw 100 times: Piercing must NEVER appear
        for (int round = 0; round < 100; round++) {
            List<UpgradeInfo> hand = manager.getAvailableUpgrades(3, rng);
            assertEquals(3, hand.size());
            for (UpgradeInfo up : hand) {
                assertNotEquals(UpgradeType.PIERCING, up.type, "Maxed perk must be excluded");
            }
        }

        // Max out 5 more perks, leaving only 1 available perk (MOVE_SPEED)
        for (UpgradeType t : UpgradeType.values()) {
            if (t != UpgradeType.MOVE_SPEED && t != UpgradeType.PIERCING) {
                for (int i = 0; i < 5; i++) {
                    manager.applyUpgrade(t, dummy);
                }
                assertTrue(manager.isMaxRank(t));
            }
        }

        // Only MOVE_SPEED is un-maxed: requesting 3 options should return exactly 1 option
        List<UpgradeInfo> hand = manager.getAvailableUpgrades(3, rng);
        assertEquals(1, hand.size());
        assertEquals(UpgradeType.MOVE_SPEED, hand.get(0).type);

        // Max out MOVE_SPEED as well (all 7 perks maxed)
        for (int i = 0; i < 5; i++) {
            manager.applyUpgrade(UpgradeType.MOVE_SPEED, dummy);
        }
        assertTrue(manager.isMaxRank(UpgradeType.MOVE_SPEED));

        // When all perks are maxed, draw returns empty list without crashing
        List<UpgradeInfo> emptyHand = manager.getAvailableUpgrades(3, rng);
        assertTrue(emptyHand.isEmpty());
    }
}
