package com.spellstorm.combat;

/**
 * Fire on-hit effect attaching a BurnStatus to the target.
 * Deals (baseDamage * dpsFraction) per second for duration seconds.
 */
public class BurnEffect implements HitEffect {

    private final float dpsFraction;
    private final float duration;

    public BurnEffect(float dpsFraction, float duration) {
        this.dpsFraction = dpsFraction;
        this.duration = duration;
    }

    public float getDpsFraction() {
        return dpsFraction;
    }

    public float getDuration() {
        return duration;
    }

    @Override
    public void apply(HitContext context) {
        if (context == null || context.target == null || !context.target.isAlive()) {
            return;
        }
        float dps = context.baseDamage * dpsFraction;
        context.target.addStatus(new BurnStatus(dps, duration));
    }
}
