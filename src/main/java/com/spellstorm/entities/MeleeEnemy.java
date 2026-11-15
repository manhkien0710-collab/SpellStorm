package com.spellstorm.entities;

import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.WorldGrid;

/**
 * Fast, aggressive melee combatant (Swarm Skeleton / Fiend).
 * Relentlessly pursues the player and inflicts contact damage.
 */
public class MeleeEnemy extends Enemy {

    public static final float DEFAULT_HEALTH = 30.0f;
    public static final float DEFAULT_SPEED = 140.0f;
    public static final float DEFAULT_DAMAGE = 15.0f;
    public static final float DEFAULT_RADIUS = 16.0f;
    public static final float DEFAULT_ATTACK_COOLDOWN = 0.8f;
    public static final int DEFAULT_EXP_REWARD = 10;

    public static final float LEGACY_HEALTH = 60.0f;
    public static final float LEGACY_SPEED = 105.0f;
    public static final float LEGACY_DAMAGE = 18.0f;

    public MeleeEnemy(Vector2D position) {
        this(position, DEFAULT_HEALTH, DEFAULT_SPEED, DEFAULT_DAMAGE, DEFAULT_RADIUS,
                DEFAULT_ATTACK_COOLDOWN, DEFAULT_EXP_REWARD);
    }

    public MeleeEnemy(Vector2D position, boolean legacy) {
        this(position,
                legacy ? LEGACY_HEALTH : DEFAULT_HEALTH,
                legacy ? LEGACY_SPEED : DEFAULT_SPEED,
                legacy ? LEGACY_DAMAGE : DEFAULT_DAMAGE,
                DEFAULT_RADIUS,
                DEFAULT_ATTACK_COOLDOWN,
                DEFAULT_EXP_REWARD);
    }

    public MeleeEnemy(Vector2D position, float health, float speed, float damage,
                      float radius, float attackCooldown, int expReward) {
        super(EnemyType.MELEE, position, radius, health, 0.0f, speed, damage, attackCooldown, expReward);
    }

    public static MeleeEnemy createLegacyReference(Vector2D position) {
        return new MeleeEnemy(position, true);
    }

    @Override
    public void updateAIWithGrid(float dt, Vector2D targetPos, WorldGrid worldGrid, AStarPathfinder pathfinder) {
        if (!isAlive()) {
            return;
        }
        steerWithWorldGrid(dt, targetPos, worldGrid, pathfinder, 1.0f);
    }

    @Override
    public void updateAI(float dt, Vector2D targetPos, TileMap tileMap, AStarPathfinder pathfinder) {
        updateAIWithGrid(dt, targetPos, resolveWorldGrid(tileMap, pathfinder), pathfinder);
    }
}
