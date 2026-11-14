package com.spellstorm.systems;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.WorldPowerUp;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;

import com.spellstorm.world.FloatingOriginAware;
import com.spellstorm.world.TileMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Manages random world power-up pickups and temporary 5-second combat abilities.
 * Handles procedural ground spawning, collision detection, 5-second gameplay timer updating,
 * pause suspension, recollection policy, and combat modifiers.
 */
public class PowerUpManager implements FloatingOriginAware {

    public static final float DEFAULT_MIN_SPAWN_INTERVAL = 12.0f;
    public static final float DEFAULT_MAX_SPAWN_INTERVAL = 20.0f;
    public static final float DEFAULT_INITIAL_SPAWN_TIMER = 8.0f;
    public static final float DEFAULT_GROUND_LIFETIME = 25.0f;
    public static final int DEFAULT_MAX_ACTIVE_PICKUPS = 4;

    private final Rectangle arenaBounds;
    private final Random rng;
    private TileMap tileMap;

    private final List<WorldPowerUp> pickups = new ArrayList<>();
    private final ActiveTemporaryAbility activeAbility = new ActiveTemporaryAbility();

    private float minSpawnInterval = DEFAULT_MIN_SPAWN_INTERVAL;
    private float maxSpawnInterval = DEFAULT_MAX_SPAWN_INTERVAL;
    private float nextSpawnTimer = DEFAULT_INITIAL_SPAWN_TIMER;
    private float groundLifetime = DEFAULT_GROUND_LIFETIME;
    private int maxActivePickups = DEFAULT_MAX_ACTIVE_PICKUPS;

    public PowerUpManager(Rectangle arenaBounds, Random rng, TileMap tileMap) {
        this.arenaBounds = (arenaBounds != null) ? new Rectangle(arenaBounds) : new Rectangle(0, 0, 4096, 4096);
        this.rng = (rng != null) ? rng : new Random();
        this.tileMap = tileMap;
        reset();
    }

    public PowerUpManager(Rectangle arenaBounds, Random rng) {
        this(arenaBounds, rng, null);
    }

    public PowerUpManager(Rectangle arenaBounds) {
        this(arenaBounds, new Random(), null);
    }

    public PowerUpManager() {
        this(new Rectangle(0, 0, 4096, 4096), new Random(), null);
    }

    public void setTileMap(TileMap tileMap) {
        this.tileMap = tileMap;
    }

    public TileMap getTileMap() {
        return tileMap;
    }

    public void reset() {
        pickups.clear();
        activeAbility.reset();
        nextSpawnTimer = DEFAULT_INITIAL_SPAWN_TIMER;
    }

    public void setSpawnIntervalRange(float minInterval, float maxInterval) {
        this.minSpawnInterval = minInterval;
        this.maxSpawnInterval = Math.max(minInterval, maxInterval);
    }

    public void setMaxActivePickups(int maxPickups) {
        this.maxActivePickups = maxPickups;
    }

    public void setGroundLifetime(float lifetime) {
        this.groundLifetime = lifetime;
    }

    public float getMinSpawnInterval() {
        return minSpawnInterval;
    }

    public float getMaxSpawnInterval() {
        return maxSpawnInterval;
    }

    public int getMaxActivePickups() {
        return maxActivePickups;
    }

    public float getGroundLifetime() {
        return groundLifetime;
    }

    public void spawnPickup(Vector2D pos, PowerUpType type) {
        WorldPowerUp p = new WorldPowerUp(pos, type);
        p.lifetime = groundLifetime;
        p.maxLifetime = groundLifetime;
        p.animTimer = rng.nextFloat() * 6.0f;
        pickups.add(p);
    }

    public void spawnPickup(float x, float y, PowerUpType type) {
        spawnPickup(new Vector2D(x, y), type);
    }

    public Vector2D findValidSpawnPosition(Vector2D playerPos) {
        final float minSpawnDist = 180.0f;
        final float maxSpawnDist = 650.0f;

        Vector2D center = (playerPos != null) ? playerPos : new Vector2D(arenaBounds.x + arenaBounds.width * 0.5f, arenaBounds.y + arenaBounds.height * 0.5f);

        float minBoundX = arenaBounds.x + 50.0f;
        float maxBoundX = arenaBounds.x + arenaBounds.width - 50.0f;
        float minBoundY = arenaBounds.y + 50.0f;
        float maxBoundY = arenaBounds.y + arenaBounds.height - 50.0f;

        for (int attempt = 0; attempt < 24; attempt++) {
            float angle = rng.nextFloat() * MathUtils.TWO_PI;
            float dist = minSpawnDist + rng.nextFloat() * (maxSpawnDist - minSpawnDist);
            float cx = center.x + (float) Math.cos(angle) * dist;
            float cy = center.y + (float) Math.sin(angle) * dist;

            if (cx >= minBoundX && cx <= maxBoundX && cy >= minBoundY && cy <= maxBoundY) {
                if (tileMap == null || tileMap.isTraversable(cx, cy, 18.0f)) {
                    return new Vector2D(cx, cy);
                }
            }
        }

        if (tileMap == null) {
            return new Vector2D(
                    MathUtils.clamp(center.x + 220.0f, minBoundX, maxBoundX),
                    MathUtils.clamp(center.y + 100.0f, minBoundY, maxBoundY)
            );
        }

        // When 24 random attempts fail, search in a safe spiral/ring near playerPos
        Vector2D safePos = searchSafePositionNear(center, minBoundX, maxBoundX, minBoundY, maxBoundY);
        if (safePos != null) {
            return safePos;
        }

        return new Vector2D(
                MathUtils.clamp(center.x + 220.0f, minBoundX, maxBoundX),
                MathUtils.clamp(center.y + 100.0f, minBoundY, maxBoundY)
        );
    }

    private Vector2D searchSafePositionNear(Vector2D center, float minX, float maxX, float minY, float maxY) {
        float startAngle = rng.nextFloat() * MathUtils.TWO_PI;

        // Band 1: Preferred distance range [180, 650]
        for (float r = 180.0f; r <= 650.0f; r += 25.0f) {
            int steps = Math.max(8, Math.round((float) (Math.PI * 2.0 * r / 30.0f)));
            float angleStep = MathUtils.TWO_PI / steps;
            for (int i = 0; i < steps; i++) {
                float angle = startAngle + i * angleStep;
                float cx = center.x + (float) Math.cos(angle) * r;
                float cy = center.y + (float) Math.sin(angle) * r;
                if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                    if (tileMap.isTraversable(cx, cy, 18.0f)) {
                        return new Vector2D(cx, cy);
                    }
                }
            }
        }

        // Band 2: Inner distance range [175 down to 30]
        for (float r = 175.0f; r >= 30.0f; r -= 25.0f) {
            int steps = Math.max(8, Math.round((float) (Math.PI * 2.0 * r / 30.0f)));
            float angleStep = MathUtils.TWO_PI / steps;
            for (int i = 0; i < steps; i++) {
                float angle = startAngle + i * angleStep;
                float cx = center.x + (float) Math.cos(angle) * r;
                float cy = center.y + (float) Math.sin(angle) * r;
                if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                    if (tileMap.isTraversable(cx, cy, 18.0f)) {
                        return new Vector2D(cx, cy);
                    }
                }
            }
        }

        // Band 3: Extended distance range [675 up to 1200]
        for (float r = 675.0f; r <= 1200.0f; r += 35.0f) {
            int steps = Math.max(12, Math.round((float) (Math.PI * 2.0 * r / 40.0f)));
            float angleStep = MathUtils.TWO_PI / steps;
            for (int i = 0; i < steps; i++) {
                float angle = startAngle + i * angleStep;
                float cx = center.x + (float) Math.cos(angle) * r;
                float cy = center.y + (float) Math.sin(angle) * r;
                if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                    if (tileMap.isTraversable(cx, cy, 18.0f)) {
                        return new Vector2D(cx, cy);
                    }
                }
            }
        }

        // Band 4: Search outwards from arena center (which has guaranteed clear terrain)
        Vector2D arenaCenter = new Vector2D(arenaBounds.x + arenaBounds.width * 0.5f,
                                            arenaBounds.y + arenaBounds.height * 0.5f);
        if (arenaCenter.x >= minX && arenaCenter.x <= maxX && arenaCenter.y >= minY && arenaCenter.y <= maxY
                && tileMap.isTraversable(arenaCenter.x, arenaCenter.y, 18.0f)) {
            return arenaCenter;
        }

        for (float r = 32.0f; r <= 2000.0f; r += 32.0f) {
            int steps = Math.max(8, Math.round((float) (Math.PI * 2.0 * r / 40.0f)));
            float angleStep = MathUtils.TWO_PI / steps;
            for (int i = 0; i < steps; i++) {
                float cx = arenaCenter.x + (float) Math.cos(i * angleStep) * r;
                float cy = arenaCenter.y + (float) Math.sin(i * angleStep) * r;
                if (cx >= minX && cx <= maxX && cy >= minY && cy <= maxY) {
                    if (tileMap.isTraversable(cx, cy, 18.0f)) {
                        return new Vector2D(cx, cy);
                    }
                }
            }
        }

        return null;
    }

    public void spawnRandomPickup(Vector2D playerPos) {
        float roll = rng.nextFloat() * 3.0f;
        PowerUpType type;
        if (roll < 1.0f) {
            type = PowerUpType.ARCANE_OVERDRIVE;
        } else if (roll < 2.0f) {
            type = PowerUpType.PIERCING_SURGE;
        } else {
            type = PowerUpType.ELEMENTAL_BURST;
        }

        Vector2D spawnPos = findValidSpawnPosition(playerPos);
        spawnPickup(spawnPos, type);
    }

    /**
     * Updates ground pickups and active power-up ability.
     * When paused is true, timers are suspended (no decrease in duration).
     */
    public void update(float dt, Player player, boolean paused) {
        if (paused || dt <= 0.0f) {
            return;
        }

        // 1. Update active temporary ability countdown
        activeAbility.update(dt);

        // 2. Spawn timer management
        nextSpawnTimer -= dt;
        if (nextSpawnTimer <= 0.0f) {
            if (pickups.size() < maxActivePickups) {
                spawnRandomPickup(player != null ? player.getPosition() : null);
            }
            nextSpawnTimer = minSpawnInterval + rng.nextFloat() * (maxSpawnInterval - minSpawnInterval);
        }

        // 3. Update existing pickups on ground & handle collection
        if (player != null && player.isAlive()) {
            Vector2D playerPos = player.getPosition();
            float pickupRadius = player.getPickupRadius();

            for (WorldPowerUp p : pickups) {
                if (!p.isAlive()) {
                    continue;
                }

                p.update(dt);

                // Player overlap collision check: 18.0f + player.getPickupRadius()
                float totalRadius = p.radius + pickupRadius;
                float dx = p.getPosition().x - playerPos.x;
                float dy = p.getPosition().y - playerPos.y;
                float distSq = dx * dx + dy * dy;

                if (distSq <= totalRadius * totalRadius) {
                    p.collect();

                    // Strict recollection policy:
                    // - Same pickup type refreshes active duration back to 5.0s
                    // - Different pickup type replaces currently active ability with new 5.0s
                    if (activeAbility.isActive() && activeAbility.getType() == p.type) {
                        activeAbility.refresh();
                    } else {
                        activeAbility.activate(p.type);
                    }
                }
            }
        } else {
            for (WorldPowerUp p : pickups) {
                if (p.isAlive()) {
                    p.update(dt);
                }
            }
        }

        // 4. Safely remove collected and expired pickups
        pickups.removeIf(p -> !p.isAlive());
    }

    public ActiveTemporaryAbility getActiveAbility() {
        return activeAbility;
    }

    public ActiveTemporaryAbility activeAbility() {
        return activeAbility;
    }

    public boolean hasActiveAbility() {
        return activeAbility.isActive();
    }

    public float getCastCooldownMultiplier() {
        return (activeAbility.isActive() && activeAbility.getType() == PowerUpType.ARCANE_OVERDRIVE)
                ? (1.0f / 1.8f)
                : 1.0f;
    }

    public int getBonusPierce() {
        return (activeAbility.isActive() && activeAbility.getType() == PowerUpType.PIERCING_SURGE)
                ? 2
                : 0;
    }

    public boolean isElementalBurstActive() {
        return activeAbility.isActive() && activeAbility.getType() == PowerUpType.ELEMENTAL_BURST;
    }

    public List<WorldPowerUp> getPickups() {
        return Collections.unmodifiableList(pickups);
    }

    public List<WorldPowerUp> pickups() {
        return Collections.unmodifiableList(pickups);
    }

    public float getNextSpawnTimer() {
        return nextSpawnTimer;
    }

    public void rebase(float deltaX, float deltaY) {
        for (int i = 0; i < pickups.size(); i++) {
            WorldPowerUp p = pickups.get(i);
            if (p != null && p.isAlive()) {
                p.rebase(deltaX, deltaY);
            }
        }
        if (arenaBounds != null) {
            arenaBounds.x -= deltaX;
            arenaBounds.y -= deltaY;
        }
    }

    @Override
    public void onOriginShift(long deltaX, long deltaY) {
        rebase((float) deltaX, (float) deltaY);
    }
}
