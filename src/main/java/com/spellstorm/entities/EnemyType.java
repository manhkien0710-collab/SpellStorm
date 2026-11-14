package com.spellstorm.entities;

/**
 * Categorical enemy archetype classification and default configuration profiles.
 */
public enum EnemyType {
    MELEE("Swarm Skeleton", 16.0f, 30.0f, 0.0f, 140.0f, 15.0f, 0.8f, 10, "melee_fiend"),
    RANGED("Cultist", 15.0f, 20.0f, 0.0f, 110.0f, 10.0f, 1.4f, 15, "ranged_cultist"),
    TANK("Golem", 26.0f, 120.0f, 25.0f, 80.0f, 25.0f, 1.2f, 30, "tank_golem"),
    BOSS("Ancient Colossus", 50.0f, 1800.0f, 45.0f, 55.0f, 45.0f, 1.0f, 250, "boss_colossus");

    private final String displayName;
    private final float defaultRadius;
    private final float defaultHealth;
    private final float defaultArmor;
    private final float defaultSpeed;
    private final float defaultContactDamage;
    private final float defaultAttackCooldown;
    private final int defaultExpReward;
    private final String textureKey;

    EnemyType(String displayName, float defaultRadius, float defaultHealth, float defaultArmor,
              float defaultSpeed, float defaultContactDamage, float defaultAttackCooldown,
              int defaultExpReward, String textureKey) {
        this.displayName = displayName;
        this.defaultRadius = defaultRadius;
        this.defaultHealth = defaultHealth;
        this.defaultArmor = defaultArmor;
        this.defaultSpeed = defaultSpeed;
        this.defaultContactDamage = defaultContactDamage;
        this.defaultAttackCooldown = defaultAttackCooldown;
        this.defaultExpReward = defaultExpReward;
        this.textureKey = textureKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public float getDefaultRadius() {
        return defaultRadius;
    }

    public float getDefaultHealth() {
        return defaultHealth;
    }

    public float getDefaultArmor() {
        return defaultArmor;
    }

    public float getDefaultSpeed() {
        return defaultSpeed;
    }

    public float getDefaultContactDamage() {
        return defaultContactDamage;
    }

    public float getDefaultAttackCooldown() {
        return defaultAttackCooldown;
    }

    public int getDefaultExpReward() {
        return defaultExpReward;
    }

    public String getTextureKey() {
        return textureKey;
    }

    public boolean isBoss() {
        return this == BOSS;
    }
}
