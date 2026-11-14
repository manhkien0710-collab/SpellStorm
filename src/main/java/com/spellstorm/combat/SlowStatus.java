package com.spellstorm.combat;

import com.spellstorm.math.MathUtils;

/**
 * Slow status effect that reduces target movement speed.
 * slowFraction is clamped to [0.0, 0.9], guaranteeing speed multiplier is in [0.10, 1.00].
 */
public class SlowStatus extends StatusEffect {

    private final float slowFraction;

    public SlowStatus(float slowFraction, float duration) {
        super(duration);
        this.slowFraction = MathUtils.clamp(slowFraction, 0.0f, 0.9f);
    }

    @Override
    public StatusKind kind() {
        return StatusKind.SLOW;
    }

    @Override
    public float speedMultiplier() {
        return 1.0f - slowFraction;
    }

    public float getSlowFraction() {
        return slowFraction;
    }

    public float slowFraction() {
        return slowFraction;
    }
}
