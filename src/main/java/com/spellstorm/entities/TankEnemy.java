package com.spellstorm.entities;

import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;

/**
 * Massive, heavily armored behemoth (Golem).
 * Possesses high durability, high armor, reduced speed, devastating contact damage,
 * and high resistance to physical knockback impulses.
 */
public class TankEnemy extends Enemy {

    public static final float DEFAULT_HEALTH = 120.0f;
    public static final float DEFAULT_SPEED = 80.0f;
    public static final float DEFAULT_DAMAGE = 25.0f;
    public static final float DEFAULT_ARMOR = 25.0f;
    public static final float DEFAULT_RADIUS = 26.0f;
    public static final float DEFAULT_ATTACK_COOLDOWN = 1.2f;
    public static final int DEFAULT_EXP_REWARD = 30;

    public static final float LEGACY_HEALTH = 200.0f;
    public static final float LEGACY_SPEED = 65.0f;
    public static final float LEGACY_DAMAGE = 32.0f;

    public TankEnemy(Vector2D position) {
        this(position, DEFAULT_HEALTH, DEFAULT_SPEED, DEFAULT_DAMAGE, DEFAULT_RADIUS,
                DEFAULT_ARMOR, DEFAULT_ATTACK_COOLDOWN, DEFAULT_EXP_REWARD);
    }

    public TankEnemy(Vector2D position, boolean legacy) {
        this(position,
                legacy ? LEGACY_HEALTH : DEFAULT_HEALTH,
                legacy ? LEGACY_SPEED : DEFAULT_SPEED,
                legacy ? LEGACY_DAMAGE : DEFAULT_DAMAGE,
                DEFAULT_RADIUS,
                DEFAULT_ARMOR,
                DEFAULT_ATTACK_COOLDOWN,
                DEFAULT_EXP_REWARD);
    }

    public TankEnemy(Vector2D position, float health, float speed, float damage,
                     float radius, float armor, float attackCooldown, int expReward) {
        super(EnemyType.TANK, position, radius, health, armor, speed, damage, attackCooldown, expReward);
        setKnockbackResistance(0.5f);
    }

    public static TankEnemy createLegacyReference(Vector2D position) {
        return new TankEnemy(position, true);
    }

    @Override
    public void updateAI(float dt, Vector2D targetPos, TileMap tileMap, AStarPathfinder pathfinder) {
        if (!isAlive()) {
            return;
        }
        steerWithNavigation(dt, targetPos, tileMap, pathfinder, 1.0f);
    }
}
