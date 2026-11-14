package com.spellstorm.entities;

import com.spellstorm.math.Vector2D;

import java.util.List;

/**
 * Pure simulation aiming subsystem computing directional trajectories and target acquisition.
 */
public final class AimingSubsystem {

    public static final float EPSILON_SQ = 1e-4f;

    private AimingSubsystem() {}

    /**
     * Computes the normalized aim direction for Manual mode given player position and mouse world position.
     *
     * @param playerPos player center in world coordinates
     * @param worldMousePos mouse cursor unprojected into world coordinates
     * @param fallbackDir fallback direction if mouse is coincident with player center
     * @return normalized 2D direction vector
     */
    public static Vector2D calculateManualAim(Vector2D playerPos, Vector2D worldMousePos, Vector2D fallbackDir) {
        if (playerPos == null) {
            return fallbackDir != null ? fallbackDir.cpy().nor() : new Vector2D(1.0f, 0.0f);
        }
        if (worldMousePos == null) {
            return fallbackDir != null ? fallbackDir.cpy().nor() : new Vector2D(1.0f, 0.0f);
        }

        float dx = worldMousePos.x - playerPos.x;
        float dy = worldMousePos.y - playerPos.y;
        float distSq = dx * dx + dy * dy;

        if (distSq > EPSILON_SQ) {
            float invDist = 1.0f / (float) Math.sqrt(distSq);
            return new Vector2D(dx * invDist, dy * invDist);
        }

        // Fallback to previous aim direction or default East (1, 0)
        return (fallbackDir != null && fallbackDir.len2() > EPSILON_SQ)
                ? fallbackDir.cpy().nor()
                : new Vector2D(1.0f, 0.0f);
    }

    /**
     * Scans active enemies and finds the closest living enemy by Euclidean distance.
     * Ties are broken deterministically by selecting the enemy with the lower entity ID.
     *
     * @param playerPos player center in world coordinates
     * @param enemies list of candidate enemies
     * @return closest living enemy, or null if list is empty or all enemies dead
     */
    public static <T extends Entity> T findClosestEnemy(Vector2D playerPos, List<T> enemies) {
        if (playerPos == null || enemies == null || enemies.isEmpty()) {
            return null;
        }

        T best = null;
        float bestDistSq = Float.MAX_VALUE;

        for (int i = 0; i < enemies.size(); i++) {
            T enemy = enemies.get(i);
            if (enemy == null || !enemy.isActive()) {
                continue;
            }
            if (enemy instanceof Character character && !character.isAlive()) {
                continue;
            }

            Vector2D enemyPos = enemy.getPosition();
            float dx = enemyPos.x - playerPos.x;
            float dy = enemyPos.y - playerPos.y;
            float dSq = dx * dx + dy * dy;

            if (dSq < bestDistSq - EPSILON_SQ) {
                bestDistSq = dSq;
                best = enemy;
            } else if (Math.abs(dSq - bestDistSq) <= EPSILON_SQ) {
                // Deterministic tie-breaking by lower entity ID
                if (best == null || enemy.getId() < best.getId()) {
                    best = enemy;
                }
            }
        }

        return best;
    }

    /**
     * Computes the normalized aim direction for Auto mode.
     * Scans active enemies for the closest living enemy.
     * Fallback hierarchy if no enemies exist:
     * 1. Mouse world position (if provided and distSq > 1e-4)
     * 2. Fallback direction (player's previous aim direction)
     * 3. Default unit vector (1, 0)
     */
    public static <T extends Entity> Vector2D calculateAutoAim(
            Vector2D playerPos, Vector2D worldMousePos, List<T> enemies, Vector2D fallbackDir) {

        T target = findClosestEnemy(playerPos, enemies);
        if (target != null) {
            Vector2D targetPos = target.getPosition();
            float dx = targetPos.x - playerPos.x;
            float dy = targetPos.y - playerPos.y;
            float distSq = dx * dx + dy * dy;
            if (distSq > EPSILON_SQ) {
                float invDist = 1.0f / (float) Math.sqrt(distSq);
                return new Vector2D(dx * invDist, dy * invDist);
            }
        }

        // Fallback: follow mouse cursor if provided (matches C++ Auto-shot mouse tracking)
        if (worldMousePos != null && playerPos != null) {
            float dx = worldMousePos.x - playerPos.x;
            float dy = worldMousePos.y - playerPos.y;
            float distSq = dx * dx + dy * dy;
            if (distSq > EPSILON_SQ) {
                float invDist = 1.0f / (float) Math.sqrt(distSq);
                return new Vector2D(dx * invDist, dy * invDist);
            }
        }

        // Final fallback: previous direction or default
        return (fallbackDir != null && fallbackDir.len2() > EPSILON_SQ)
                ? fallbackDir.cpy().nor()
                : new Vector2D(1.0f, 0.0f);
    }
}
