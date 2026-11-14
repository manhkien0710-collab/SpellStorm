package com.spellstorm.systems;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.entities.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages player perk upgrades, rank limits, stat applications,
 * and random 3-card draws without duplicate choices.
 */
public class UpgradeManager {

    private final List<UpgradeInfo> upgrades = new ArrayList<>();

    public UpgradeManager() {
        reset();
    }

    public void reset() {
        upgrades.clear();
        upgrades.add(new UpgradeInfo(
                UpgradeType.MAX_HEALTH,
                "Vitality Surge",
                "+40 Max Health & instant heal",
                0, 5,
                new Color(255f / 255f, 80f / 255f, 100f / 255f, 1.0f)
        ));
        upgrades.add(new UpgradeInfo(
                UpgradeType.MOVE_SPEED,
                "Fleet Footwork",
                "+12% Movement Speed",
                0, 5,
                new Color(80f / 255f, 240f / 255f, 140f / 255f, 1.0f)
        ));
        upgrades.add(new UpgradeInfo(
                UpgradeType.DAMAGE,
                "Arcane Might",
                "+20% Spell Damage",
                0, 5,
                new Color(255f / 255f, 160f / 255f, 40f / 255f, 1.0f)
        ));
        upgrades.add(new UpgradeInfo(
                UpgradeType.CAST_COOLDOWN,
                "Quickcast",
                "-12% Spell Cast Cooldown",
                0, 5,
                new Color(190f / 255f, 100f / 255f, 255f / 255f, 1.0f)
        ));
        upgrades.add(new UpgradeInfo(
                UpgradeType.PROJECTILE_SPEED,
                "Velocity Shard",
                "+20% Missile Speed",
                0, 5,
                new Color(60f / 255f, 215f / 255f, 255f / 255f, 1.0f)
        ));
        upgrades.add(new UpgradeInfo(
                UpgradeType.PROJECTILE_LIFETIME,
                "Far-reaching Arc",
                "+25% Missile Lifetime / Range",
                0, 5,
                new Color(255f / 255f, 225f / 255f, 75f / 255f, 1.0f)
        ));
        upgrades.add(new UpgradeInfo(
                UpgradeType.PIERCING,
                "Piercing Lance",
                "+1 Missile Pierce (max 2 ranks, -20% dmg/hit)",
                0, 2,
                new Color(255f / 255f, 120f / 255f, 200f / 255f, 1.0f)
        ));
    }

    /**
     * Draws up to `count` distinct upgrade perks that have not reached their maximum rank.
     * Uses backward Fisher-Yates shuffle with the provided Random generator.
     */
    public List<UpgradeInfo> getAvailableUpgrades(int count, Random rng) {
        Random r = (rng != null) ? rng : new Random();
        List<UpgradeInfo> available = new ArrayList<>();
        for (UpgradeInfo up : upgrades) {
            if (up.currentRank < up.maxRank) {
                available.add(up.cpy());
            }
        }

        if (available.size() <= count) {
            return available;
        }

        // Fisher-Yates shuffle
        for (int i = available.size() - 1; i > 0; i--) {
            int j = r.nextInt(i + 1);
            Collections.swap(available, i, j);
        }

        return new ArrayList<>(available.subList(0, count));
    }

    public List<UpgradeInfo> getAvailableUpgrades(int count) {
        return getAvailableUpgrades(count, new Random());
    }

    /**
     * Applies the perk upgrade to the given player.
     * Rejects if perk is already at maximum rank.
     *
     * @return true if successfully applied, false if max rank or type not found
     */
    public boolean applyUpgrade(UpgradeType type, Player player) {
        if (player == null || type == null) {
            return false;
        }

        for (UpgradeInfo up : upgrades) {
            if (up.type == type) {
                if (up.currentRank >= up.maxRank) {
                    return false;
                }

                switch (type) {
                    case MAX_HEALTH -> player.increaseMaxHealth(40.0f);
                    case MOVE_SPEED -> player.multiplyMoveSpeed(1.12f);
                    case DAMAGE -> player.multiplyDamage(1.20f);
                    case CAST_COOLDOWN -> player.multiplyCastCooldown(0.88f);
                    case PROJECTILE_SPEED -> player.multiplyProjectileSpeed(1.20f);
                    case PROJECTILE_LIFETIME -> player.multiplyProjectileLifetime(1.25f);
                    case PIERCING -> player.addBonusPierce(1);
                }

                up.currentRank++;
                return true;
            }
        }

        return false;
    }

    public int getRank(UpgradeType type) {
        for (UpgradeInfo up : upgrades) {
            if (up.type == type) {
                return up.currentRank;
            }
        }
        return 0;
    }

    public int getMaxRank(UpgradeType type) {
        for (UpgradeInfo up : upgrades) {
            if (up.type == type) {
                return up.maxRank;
            }
        }
        return 0;
    }

    public boolean isMaxRank(UpgradeType type) {
        for (UpgradeInfo up : upgrades) {
            if (up.type == type) {
                return up.currentRank >= up.maxRank;
            }
        }
        return true;
    }

    public UpgradeInfo getInfo(UpgradeType type) {
        for (UpgradeInfo up : upgrades) {
            if (up.type == type) {
                return up;
            }
        }
        return null;
    }

    public List<UpgradeInfo> getUpgrades() {
        return Collections.unmodifiableList(upgrades);
    }
}
