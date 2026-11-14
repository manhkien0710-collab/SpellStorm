package com.spellstorm.entities;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.math.Vector2D;

/**
 * Dropped experience gem pickup entity in SpellStorm.
 *
 * Tiers:
 * - Cyan: expValue < 15 (default 10 EXP, regular melee foes)
 * - Emerald: 15 <= expValue < 30 (default 25 EXP or 15 EXP, ranged / intermediate foes)
 * - Gold / Amber: expValue >= 30 (default 75 EXP, 30 EXP, or 250 EXP boss)
 *
 * Features:
 * - Sinusoidal vertical bobbing: sin(animTimer * 5.0) * 3.0 px.
 * - Dynamic outer glow ring pulse: sin(animTimer * 6.0).
 * - Smooth accelerating magnet pull towards player.
 * - Ground shadow anchored to baseY.
 */
public class ExperiencePickup extends Entity {

    public static final float DEFAULT_RADIUS = 8.0f;
    public static final float BOB_FREQUENCY = 5.0f;
    public static final float BOB_AMPLITUDE = 3.0f;
    public static final float PULSE_FREQUENCY = 6.0f;

    // Palette specifications matching C++ sf::Color
    public static final Color CYAN_GEM_COLOR = new Color(60f / 255f, 200f / 255f, 255f / 255f, 1f);
    public static final Color CYAN_CORE_COLOR = new Color(210f / 255f, 250f / 255f, 255f / 255f, 1f);
    public static final Color EMERALD_GEM_COLOR = new Color(50f / 255f, 240f / 255f, 150f / 255f, 1f);
    public static final Color EMERALD_CORE_COLOR = new Color(200f / 255f, 255f / 255f, 230f / 255f, 1f);
    public static final Color GOLD_GEM_COLOR = new Color(255f / 255f, 210f / 255f, 50f / 255f, 1f);
    public static final Color GOLD_CORE_COLOR = new Color(255f / 255f, 255f / 255f, 220f / 255f, 1f);

    private final int expValue;
    private boolean collected = false;
    private float animTimer = 0.0f;
    private float baseY;
    private final Color gemColor;
    private final Color coreColor;
    private final AssetRegistry.TextureId textureId;

    public ExperiencePickup(Vector2D position, int expValue) {
        super(position != null ? position.cpy() : new Vector2D(), DEFAULT_RADIUS, "experience_pickup");
        this.expValue = Math.max(1, expValue);
        this.baseY = this.position.y;

        if (this.expValue < 15) {
            // Cyan gem
            this.gemColor = CYAN_GEM_COLOR.cpy();
            this.coreColor = CYAN_CORE_COLOR.cpy();
            this.textureId = AssetRegistry.TextureId.GEM_CYAN;
        } else if (this.expValue < 30) {
            // Emerald gem
            this.gemColor = EMERALD_GEM_COLOR.cpy();
            this.coreColor = EMERALD_CORE_COLOR.cpy();
            this.textureId = AssetRegistry.TextureId.GEM_EMERALD;
        } else {
            // Gold / Amber gem
            this.gemColor = GOLD_GEM_COLOR.cpy();
            this.coreColor = GOLD_CORE_COLOR.cpy();
            this.textureId = AssetRegistry.TextureId.GEM_GOLD;
        }
    }

    public ExperiencePickup(float x, float y, int expValue) {
        this(new Vector2D(x, y), expValue);
    }

    public int getExpValue() {
        return expValue;
    }

    public int expValue() {
        return expValue;
    }

    public boolean isCollected() {
        return collected;
    }

    public void collect() {
        if (!collected) {
            collected = true;
            destroy();
        }
    }

    /**
     * Magnet attraction towards target position with accelerating speed.
     */
    public void magnetTowards(Vector2D targetPos, float speed, float dt) {
        if (!isAlive() || collected || targetPos == null || dt <= 0.0f) {
            return;
        }

        Vector2D diff = targetPos.cpy().sub(position);
        float distSq = diff.len2();
        if (distSq > 1e-4f) {
            Vector2D dir = diff.nor();
            Vector2D step = dir.scl(speed * dt);
            position.add(step);
            baseY = position.y;
        }
    }

    @Override
    public void update(float dt) {
        if (!isAlive() || collected) {
            return;
        }
        animTimer += dt;
    }

    public float getBobOffset() {
        return (float) (Math.sin(animTimer * BOB_FREQUENCY) * BOB_AMPLITUDE);
    }

    public float getRenderY() {
        return position.y + getBobOffset();
    }

    public float getGlowPulse() {
        return (float) ((Math.sin(animTimer * PULSE_FREQUENCY) + 1.0) * 0.5);
    }

    public Color getGemColor() {
        return gemColor.cpy();
    }

    public Color getCoreColor() {
        return coreColor.cpy();
    }

    public AssetRegistry.TextureId getTextureId() {
        return textureId;
    }

    public float getAnimTimer() {
        return animTimer;
    }

    public float getBaseY() {
        return baseY;
    }

    @Override
    public void rebase(float deltaX, float deltaY) {
        super.rebase(deltaX, deltaY);
        this.baseY -= deltaY;
    }
}
