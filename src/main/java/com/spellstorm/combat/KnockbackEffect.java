package com.spellstorm.combat;

import com.spellstorm.math.Vector2D;

/**
 * Wind / Earth on-hit effect applying a physical knockback impulse to the target along hit direction.
 */
public class KnockbackEffect implements HitEffect {

    private final float strength;

    public KnockbackEffect(float strength) {
        this.strength = strength;
    }

    public float getStrength() {
        return strength;
    }

    @Override
    public void apply(HitContext context) {
        if (context == null || context.target == null || !context.target.isAlive()) {
            return;
        }
        Vector2D dir = (context.direction != null && context.direction.len2() > 1e-4f)
                ? context.direction.cpy().nor()
                : new Vector2D(1.0f, 0.0f);
        context.target.applyKnockback(dir.scl(strength));
    }
}
