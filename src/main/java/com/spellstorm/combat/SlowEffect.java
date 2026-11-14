package com.spellstorm.combat;

/**
 * Ice on-hit effect attaching a SlowStatus to the target.
 * Slows target movement speed by slowFraction for duration seconds.
 */
public class SlowEffect implements HitEffect {

    private final float slowFraction;
    private final float duration;

    public SlowEffect(float slowFraction, float duration) {
        this.slowFraction = slowFraction;
        this.duration = duration;
    }

    public float getSlowFraction() {
        return slowFraction;
    }

    public float getDuration() {
        return duration;
    }

    @Override
    public void apply(HitContext context) {
        if (context == null || context.target == null || !context.target.isAlive()) {
            return;
        }
        context.target.addStatus(new SlowStatus(slowFraction, duration));
    }
}
