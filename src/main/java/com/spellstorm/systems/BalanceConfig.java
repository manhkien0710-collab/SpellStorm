package com.spellstorm.systems;

import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.MathUtils;

/**
 * Centralized gameplay balance and wave-pacing configuration for SpellStorm.
 * Provides Phase 2 tuned defaults ({@link #defaults()}) as well as legacy Phase 1
 * values ({@link #legacy()}) for deterministic compatibility testing.
 */
public class BalanceConfig {

    private float playerSpeed;
    private float meleeSpeed;
    private float rangedSpeed;
    private float tankSpeed;
    private float bossSpeed;

    private float bossHp;
    private float bossArmor;
    private float bossRadius;
    private float bossVolleyCooldown;

    private float minSpawnDistance;
    private float maxSpawnDistance;
    private float initialSpawnDelay;
    private int initialWaveMin;
    private int initialWaveMax;

    private float baseSpawnInterval;
    private float minSpawnInterval;
    private int baseMaxEnemies;
    private int maxEnemiesCap;
    private float rampDurationSeconds;

    /**
     * Constructs a BalanceConfig initialized to Phase 2 tuned defaults.
     */
    public BalanceConfig() {
        this(
                230.0f,
                185.0f, 160.0f, 120.0f, 100.0f,
                1800.0f, 45.0f, 50.0f, 3.2f,
                380.0f, 520.0f,
                1.2f, 2, 3,
                1.6f, 0.5f,
                20, 40, 120.0f
        );
    }

    public BalanceConfig(
            float playerSpeed,
            float meleeSpeed,
            float rangedSpeed,
            float tankSpeed,
            float bossSpeed,
            float bossHp,
            float bossArmor,
            float bossRadius,
            float bossVolleyCooldown,
            float minSpawnDistance,
            float maxSpawnDistance,
            float initialSpawnDelay,
            int initialWaveMin,
            int initialWaveMax,
            float baseSpawnInterval,
            float minSpawnInterval,
            int baseMaxEnemies,
            int maxEnemiesCap,
            float rampDurationSeconds) {
        this.playerSpeed = Math.max(0.0f, playerSpeed);
        this.meleeSpeed = Math.max(0.0f, meleeSpeed);
        this.rangedSpeed = Math.max(0.0f, rangedSpeed);
        this.tankSpeed = Math.max(0.0f, tankSpeed);
        this.bossSpeed = Math.max(0.0f, bossSpeed);

        this.bossHp = Math.max(1.0f, bossHp);
        this.bossArmor = Math.max(0.0f, bossArmor);
        this.bossRadius = Math.max(1.0f, bossRadius);
        this.bossVolleyCooldown = Math.max(0.05f, bossVolleyCooldown);

        this.minSpawnDistance = Math.max(0.0f, minSpawnDistance);
        this.maxSpawnDistance = Math.max(this.minSpawnDistance, maxSpawnDistance);
        this.initialSpawnDelay = Math.max(0.0f, initialSpawnDelay);
        this.initialWaveMin = Math.max(1, initialWaveMin);
        this.initialWaveMax = Math.max(this.initialWaveMin, initialWaveMax);

        this.baseSpawnInterval = Math.max(0.05f, baseSpawnInterval);
        this.minSpawnInterval = MathUtils.clamp(minSpawnInterval, 0.05f, this.baseSpawnInterval);
        this.baseMaxEnemies = Math.max(1, baseMaxEnemies);
        this.maxEnemiesCap = Math.max(this.baseMaxEnemies, maxEnemiesCap);
        this.rampDurationSeconds = Math.max(1.0f, rampDurationSeconds);
    }

    /**
     * Returns the Phase 2 tuned balance configuration.
     */
    public static BalanceConfig defaults() {
        return new BalanceConfig();
    }

    /**
     * Returns the Phase 1 legacy balance values for regression comparison.
     */
    public static BalanceConfig legacy() {
        return new BalanceConfig(
                Player.DEFAULT_SPEED,
                MeleeEnemy.DEFAULT_SPEED,
                RangedEnemy.DEFAULT_SPEED,
                TankEnemy.DEFAULT_SPEED,
                BossEnemy.BOSS_MOVE_SPEED,
                BossEnemy.BOSS_MAX_HEALTH,
                BossEnemy.BOSS_ARMOR,
                BossEnemy.BOSS_RADIUS,
                BossEnemy.VOLLEY_COOLDOWN,
                450.0f,
                800.0f,
                1.5f,
                1,
                1,
                EnemySpawner.DEFAULT_BASE_INTERVAL,
                EnemySpawner.DEFAULT_MIN_INTERVAL,
                EnemySpawner.DEFAULT_BASE_MAX_ENEMIES,
                EnemySpawner.DEFAULT_MAX_ENEMIES_CAP,
                EnemySpawner.DEFAULT_SCALING_DURATION
        );
    }

    /**
     * Applies archetype speed and boss volley cooldown tuning to the given enemy.
     */
    public void applyToEnemy(Enemy enemy) {
        if (enemy == null) {
            return;
        }
        switch (enemy.getType()) {
            case MELEE -> enemy.setSpeed(meleeSpeed);
            case RANGED -> enemy.setSpeed(rangedSpeed);
            case TANK -> enemy.setSpeed(tankSpeed);
            case BOSS -> enemy.setSpeed(bossSpeed);
        }
        if (enemy instanceof BossEnemy boss) {
            boss.setVolleyCooldown(bossVolleyCooldown);
        }
    }

    /**
     * Computes the spawn interval at the given match elapsed time in seconds.
     */
    public float computeSpawnInterval(float elapsedTime) {
        float clampedElapsed = Math.max(0.0f, elapsedTime);
        float progress = MathUtils.clamp(clampedElapsed / rampDurationSeconds, 0.0f, 1.0f);
        return baseSpawnInterval - progress * (baseSpawnInterval - minSpawnInterval);
    }

    /**
     * Computes the active enemy cap at the given match elapsed time in seconds.
     */
    public int computeMaxEnemies(float elapsedTime) {
        float clampedElapsed = Math.max(0.0f, elapsedTime);
        float progress = MathUtils.clamp(clampedElapsed / rampDurationSeconds, 0.0f, 1.0f);
        if (maxEnemiesCap > baseMaxEnemies) {
            return (int) (baseMaxEnemies + progress * (maxEnemiesCap - baseMaxEnemies));
        }
        return baseMaxEnemies;
    }

    // =========================================================================
    // Getters
    // =========================================================================

    public float getPlayerSpeed() { return playerSpeed; }
    public float getMeleeSpeed() { return meleeSpeed; }
    public float getRangedSpeed() { return rangedSpeed; }
    public float getTankSpeed() { return tankSpeed; }
    public float getBossSpeed() { return bossSpeed; }

    public float getBossHp() { return bossHp; }
    public float getBossArmor() { return bossArmor; }
    public float getBossRadius() { return bossRadius; }
    public float getBossVolleyCooldown() { return bossVolleyCooldown; }

    public float getMinSpawnDistance() { return minSpawnDistance; }
    public float getMaxSpawnDistance() { return maxSpawnDistance; }
    public float getInitialSpawnDelay() { return initialSpawnDelay; }
    public int getInitialWaveMin() { return initialWaveMin; }
    public int getInitialWaveMax() { return initialWaveMax; }

    public float getBaseSpawnInterval() { return baseSpawnInterval; }
    public float getMinSpawnInterval() { return minSpawnInterval; }
    public int getBaseMaxEnemies() { return baseMaxEnemies; }
    public int getMaxEnemiesCap() { return maxEnemiesCap; }
    public float getRampDurationSeconds() { return rampDurationSeconds; }
}
