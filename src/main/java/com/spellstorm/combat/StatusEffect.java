package com.spellstorm.combat;

import com.spellstorm.entities.Character;

/**
 * Abstract base class for timed status conditions attached to a Character.
 * Pure simulation logic independent of rendering.
 */
public abstract class StatusEffect {

    protected final float duration;
    protected float remaining;

    public StatusEffect(float duration) {
        this.duration = Math.max(0.0f, duration);
        this.remaining = this.duration;
    }

    public abstract StatusKind kind();

    public float speedMultiplier() {
        return 1.0f;
    }

    public void update(float dt, Character owner) {
        if (expired()) {
            return;
        }
        float step = Math.min(dt, remaining);
        remaining -= dt;
        onTick(step, owner);
    }

    public boolean expired() {
        return remaining <= 0.0f;
    }

    public float remaining() {
        return remaining;
    }

    public float getRemaining() {
        return remaining;
    }

    public float duration() {
        return duration;
    }

    public float getDuration() {
        return duration;
    }

    protected void onTick(float dt, Character owner) {
    }
}
