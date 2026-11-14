package com.spellstorm.combat;

import com.spellstorm.entities.Character;

/**
 * Burning status effect dealing periodic damage over time that ignores armor.
 * Ticks every 0.5 seconds, dealing (damagePerSecond * 0.5) per tick.
 * Initial tick occurs after 0.5 seconds of application (no frame-0 damage).
 */
public class BurnStatus extends StatusEffect {

    public static final float TICK_INTERVAL = 0.5f;
    public static final float kTickInterval = 0.5f;

    private float damagePerSecond;
    private float tickTimer = TICK_INTERVAL;

    public BurnStatus(float damagePerSecond, float duration) {
        super(duration);
        this.damagePerSecond = Math.max(0.0f, damagePerSecond);
    }

    /**
     * Refreshes burn duration and parameters while preserving the active tickTimer cadence.
     */
    public void refreshFrom(StatusEffect other) {
        if (other == null) {
            return;
        }
        this.remaining = Math.max(this.remaining, other.duration());
        if (other instanceof BurnStatus bs) {
            this.damagePerSecond = Math.max(this.damagePerSecond, bs.damagePerSecond);
        }
    }

    public void refreshFrom(BurnStatus other) {
        refreshFrom((StatusEffect) other);
    }

    public float getTickTimer() {
        return tickTimer;
    }

    @Override
    public StatusKind kind() {
        return StatusKind.BURN;
    }

    public float getDamagePerSecond() {
        return damagePerSecond;
    }

    public float damagePerSecond() {
        return damagePerSecond;
    }

    @Override
    protected void onTick(float dt, Character owner) {
        if (owner == null) {
            return;
        }
        tickTimer -= dt;
        while (tickTimer <= 0.0f) {
            tickTimer += TICK_INTERVAL;
            owner.takeDamage(new DamageInfo(damagePerSecond * TICK_INTERVAL, true));
        }
    }
}
