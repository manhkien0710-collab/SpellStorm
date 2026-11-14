package com.spellstorm.systems;

import com.spellstorm.math.MathUtils;

/**
 * Manages player experience gain, level thresholds, excess EXP carryover,
 * and pending level-up perk selection queue.
 *
 * Progression curve:
 * expRequiredForLevel(lvl) = 25 + (max(1, lvl) - 1) * 20
 * Level 1: 25 EXP
 * Level 2: 45 EXP
 * Level 3: 65 EXP
 * Level 4: 85 EXP ...
 */
public class PlayerProgression {

    private int level = 1;
    private int currentExp = 0;
    private int pendingLevelUps = 0;

    public PlayerProgression() {
        this(1);
    }

    public PlayerProgression(int startingLevel) {
        reset(startingLevel);
    }

    public void reset(int startingLevel) {
        this.level = Math.max(1, startingLevel);
        this.currentExp = 0;
        this.pendingLevelUps = 0;
    }

    /**
     * Experience required to advance from the given level to the next level.
     */
    public static int expRequiredForLevel(int lvl) {
        final int clampedLvl = Math.max(1, lvl);
        return 25 + (clampedLvl - 1) * 20;
    }

    public int getExpForNextLevel() {
        return expRequiredForLevel(level);
    }

    public int expForNextLevel() {
        return getExpForNextLevel();
    }

    public float getProgressRatio() {
        final int needed = expForNextLevel();
        if (needed <= 0) {
            return 1.0f;
        }
        return MathUtils.clamp((float) currentExp / (float) needed, 0.0f, 1.0f);
    }

    public float progressRatio() {
        return getProgressRatio();
    }

    /**
     * Awards experience to the player.
     * Retains excess experience across level boundaries and supports multiple level gains.
     *
     * @param amount experience to add (non-positive amounts safely ignored)
     * @return number of levels gained from this award
     */
    public int addExp(int amount) {
        if (amount <= 0) {
            return 0;
        }

        // Guard against integer overflow
        if (amount > Integer.MAX_VALUE - currentExp) {
            currentExp = Integer.MAX_VALUE;
        } else {
            currentExp += amount;
        }

        int levelsGained = 0;
        while (true) {
            final int needed = expForNextLevel();
            if (needed <= 0) {
                break;
            }

            if (currentExp >= needed) {
                currentExp -= needed;
                level++;
                pendingLevelUps++;
                levelsGained++;
            } else {
                break;
            }
        }

        return levelsGained;
    }

    public boolean hasPendingLevelUp() {
        return pendingLevelUps > 0;
    }

    public int getPendingLevelUps() {
        return pendingLevelUps;
    }

    public int pendingLevelUps() {
        return pendingLevelUps;
    }

    public void consumeLevelUp() {
        if (pendingLevelUps > 0) {
            pendingLevelUps--;
        }
    }

    public int getLevel() {
        return level;
    }

    public int level() {
        return level;
    }

    public int getCurrentExp() {
        return currentExp;
    }

    public int currentExp() {
        return currentExp;
    }
}
