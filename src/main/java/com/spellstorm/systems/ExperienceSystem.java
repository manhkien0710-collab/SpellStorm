package com.spellstorm.systems;

import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Manages active experience gem pickups in the arena:
 * - Spawning gems from defeated enemies.
 * - Dynamic accelerating magnet attraction towards player.
 * - Collision detection and EXP progression awarding.
 * - Pruning collected / inactive gems.
 */
public class ExperienceSystem {

    public static final float MAGNET_RADIUS_MULTIPLIER = 2.80f;
    public static final float BASE_PULL_SPEED = 240.0f;
    public static final float PULL_ACCELERATION_FACTOR = 2.5f;

    private final List<ExperiencePickup> pickups = new ArrayList<>();

    public ExperienceSystem() {
    }

    public void spawnPickup(Vector2D position, int expValue) {
        if (position != null) {
            pickups.add(new ExperiencePickup(position, expValue));
        }
    }

    public void spawnPickup(float x, float y, int expValue) {
        pickups.add(new ExperiencePickup(x, y, expValue));
    }

    public void update(float dt, Player player, PlayerProgression progression) {
        if (player == null) {
            return;
        }

        final Vector2D playerPos = player.getPosition();
        final float collectRadius = player.getPickupRadius();
        final float collectRadiusSq = collectRadius * collectRadius;
        final float magnetRadius = collectRadius * MAGNET_RADIUS_MULTIPLIER;
        final float magnetRadiusSq = magnetRadius * magnetRadius;

        for (ExperiencePickup pickup : pickups) {
            if (!pickup.isAlive() || pickup.isCollected()) {
                continue;
            }

            pickup.update(dt);

            if (player.isAlive()) {
                float dx = pickup.getPosition().x - playerPos.x;
                float dy = pickup.getPosition().y - playerPos.y;
                float distSq = dx * dx + dy * dy;

                // 1. Magnet attraction towards player
                if (distSq <= magnetRadiusSq && distSq > 1e-4f) {
                    float dist = (float) Math.sqrt(distSq);
                    float pullSpeed = BASE_PULL_SPEED + (magnetRadius - dist) * PULL_ACCELERATION_FACTOR;
                    pickup.magnetTowards(playerPos, pullSpeed, dt);
                }

                // 2. Collection check
                // Re-evaluate distance after magnet movement
                dx = pickup.getPosition().x - playerPos.x;
                dy = pickup.getPosition().y - playerPos.y;
                distSq = dx * dx + dy * dy;

                if (distSq <= collectRadiusSq) {
                    int expVal = pickup.getExpValue();
                    pickup.collect();
                    if (progression != null) {
                        progression.addExp(expVal);
                    }
                }
            }
        }

        // Prune collected/dead gems
        pickups.removeIf(p -> !p.isAlive() || p.isCollected());
    }

    public int getPickupCount() {
        return pickups.size();
    }

    public int pickupCount() {
        return pickups.size();
    }

    public List<ExperiencePickup> getPickups() {
        return Collections.unmodifiableList(pickups);
    }

    public List<ExperiencePickup> pickups() {
        return Collections.unmodifiableList(pickups);
    }

    public void clear() {
        pickups.clear();
    }
}
