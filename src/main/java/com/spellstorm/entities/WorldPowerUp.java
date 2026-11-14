package com.spellstorm.entities;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PowerUpType;

/**
 * World power-up pickup spawned on the traversable arena ground.
 *
 * Characteristics:
 * - Radius: 18.0f px.
 * - Lifetime: 25.0s total ground lifetime.
 * - Blinking phase: when remaining lifetime < 4.0s, pulses rapidly at 12 rad/s.
 * - Collection check: distance <= 18.0f + player.getPickupRadius().
 */
public class WorldPowerUp extends Entity {

    public static final float DEFAULT_RADIUS = 18.0f;
    public static final float DEFAULT_LIFETIME = 25.0f;
    public static final float BLINK_THRESHOLD = 4.0f;
    public static final float BLINK_FREQUENCY = 12.0f;

    public PowerUpType type = PowerUpType.ARCANE_OVERDRIVE;
    public float radius = DEFAULT_RADIUS;
    public float lifetime = DEFAULT_LIFETIME;
    public float maxLifetime = DEFAULT_LIFETIME;
    public float animTimer = 0.0f;
    public boolean collected = false;

    public WorldPowerUp(Vector2D pos, PowerUpType type) {
        super(pos != null ? pos.cpy() : new Vector2D(), DEFAULT_RADIUS, "world_powerup");
        this.type = type != null ? type : PowerUpType.ARCANE_OVERDRIVE;
        this.radius = DEFAULT_RADIUS;
        this.lifetime = DEFAULT_LIFETIME;
        this.maxLifetime = DEFAULT_LIFETIME;
        this.animTimer = 0.0f;
        this.collected = false;
    }

    public WorldPowerUp(float x, float y, PowerUpType type) {
        this(new Vector2D(x, y), type);
    }

    public WorldPowerUp() {
        this(new Vector2D(), PowerUpType.ARCANE_OVERDRIVE);
    }

    @Override
    public boolean isAlive() {
        return !collected && lifetime > 0.0f && super.isAlive();
    }

    public void collect() {
        if (!collected) {
            collected = true;
            destroy();
        }
    }

    public boolean isCollected() {
        return collected;
    }

    public PowerUpType getType() {
        return type;
    }

    public float getLifetime() {
        return lifetime;
    }

    public float getMaxLifetime() {
        return maxLifetime;
    }

    public float getAnimTimer() {
        return animTimer;
    }

    public boolean isBlinking() {
        return lifetime < BLINK_THRESHOLD;
    }

    public float getAlphaFactor() {
        if (lifetime >= BLINK_THRESHOLD) {
            return 1.0f;
        }
        float alphaFactor = (float) (0.5 + 0.5 * Math.sin(lifetime * BLINK_FREQUENCY));
        return MathUtils.clamp(alphaFactor, 40f / 255f, 1.0f);
    }

    public Color getPrimaryColor() {
        return switch (type) {
            case ARCANE_OVERDRIVE -> new Color(175f / 255f, 75f / 255f, 255f / 255f, 1.0f);
            case PIERCING_SURGE -> new Color(35f / 255f, 220f / 255f, 255f / 255f, 1.0f);
            case ELEMENTAL_BURST -> new Color(255f / 255f, 135f / 255f, 30f / 255f, 1.0f);
        };
    }

    public Color getSecondaryColor() {
        return switch (type) {
            case ARCANE_OVERDRIVE -> new Color(235f / 255f, 190f / 255f, 255f / 255f, 1.0f);
            case PIERCING_SURGE -> new Color(200f / 255f, 250f / 255f, 255f / 255f, 1.0f);
            case ELEMENTAL_BURST -> new Color(255f / 255f, 235f / 255f, 130f / 255f, 1.0f);
        };
    }

    @Override
    public void update(float dt) {
        if (!isAlive()) {
            return;
        }
        lifetime -= dt;
        animTimer += dt * 3.0f;
        if (lifetime <= 0.0f) {
            lifetime = 0.0f;
            destroy();
        }
    }
}
