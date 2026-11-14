package com.spellstorm.systems;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.math.MathUtils;

/**
 * Active temporary 5.0-second player combat ability.
 *
 * Policies:
 * - Active duration is exactly 5.0s of active gameplay.
 * - Pausing (Level-Up modal, Game Over, or manual pause) suspends the duration timer.
 * - Same-type recollection refreshes the remaining duration back to 5.0s.
 * - Different-type recollection replaces the ability and resets duration to 5.0s.
 * - On expiry, combat modifiers cleanly revert to normal.
 */
public class ActiveTemporaryAbility {

    public static final float DURATION = 5.0f;

    private PowerUpType type = PowerUpType.ARCANE_OVERDRIVE;
    private float remainingDuration = 0.0f;
    private boolean active = false;

    public void activate(PowerUpType newType) {
        this.type = (newType != null) ? newType : PowerUpType.ARCANE_OVERDRIVE;
        this.remainingDuration = DURATION;
        this.active = true;
    }

    public void refresh() {
        this.remainingDuration = DURATION;
        this.active = true;
    }

    public void reset() {
        this.active = false;
        this.remainingDuration = 0.0f;
        this.type = PowerUpType.ARCANE_OVERDRIVE;
    }

    public void update(float dt) {
        if (!active) {
            return;
        }
        remainingDuration -= dt;
        if (remainingDuration <= 0.0f) {
            remainingDuration = 0.0f;
            active = false;
        }
    }

    public float getDurationRatio() {
        return MathUtils.clamp(remainingDuration / DURATION, 0.0f, 1.0f);
    }

    public float durationRatio() {
        return getDurationRatio();
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public PowerUpType getType() {
        return type;
    }

    public PowerUpType type() {
        return type;
    }

    public float getRemainingDuration() {
        return remainingDuration;
    }

    public float remainingDuration() {
        return remainingDuration;
    }

    public String getName() {
        return switch (type) {
            case ARCANE_OVERDRIVE -> "ARCANE OVERDRIVE";
            case PIERCING_SURGE -> "PIERCING SURGE";
            case ELEMENTAL_BURST -> "ELEMENTAL BURST";
        };
    }

    public String name() {
        return getName();
    }

    public String getDescription() {
        return switch (type) {
            case ARCANE_OVERDRIVE -> "+80% Spell Firing Rate";
            case PIERCING_SURGE -> "+2 Projectile Piercing Hits";
            case ELEMENTAL_BURST -> "Empowered Twin Elemental Blast";
        };
    }

    public String description() {
        return getDescription();
    }

    public Color getPrimaryColor() {
        return switch (type) {
            case ARCANE_OVERDRIVE -> new Color(175f / 255f, 75f / 255f, 255f / 255f, 1.0f);
            case PIERCING_SURGE -> new Color(35f / 255f, 220f / 255f, 255f / 255f, 1.0f);
            case ELEMENTAL_BURST -> new Color(255f / 255f, 135f / 255f, 30f / 255f, 1.0f);
        };
    }

    public Color primaryColor() {
        return getPrimaryColor();
    }

    public Color getSecondaryColor() {
        return switch (type) {
            case ARCANE_OVERDRIVE -> new Color(235f / 255f, 190f / 255f, 255f / 255f, 1.0f);
            case PIERCING_SURGE -> new Color(200f / 255f, 250f / 255f, 255f / 255f, 1.0f);
            case ELEMENTAL_BURST -> new Color(255f / 255f, 235f / 255f, 130f / 255f, 1.0f);
        };
    }

    public Color secondaryColor() {
        return getSecondaryColor();
    }
}
