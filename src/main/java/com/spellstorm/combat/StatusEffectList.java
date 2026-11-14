package com.spellstorm.combat;

import com.spellstorm.entities.Character;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Container of active StatusEffect instances attached to a Character.
 * Re-applying an effect of the same kind replaces (refreshes) the existing effect,
 * resetting duration and parameters rather than stacking infinite instances.
 */
public class StatusEffectList {

    private final List<StatusEffect> effects = new ArrayList<>();

    public void add(StatusEffect effect) {
        if (effect == null) {
            return;
        }
        for (int i = 0; i < effects.size(); i++) {
            if (effects.get(i).kind() == effect.kind()) {
                StatusEffect existing = effects.get(i);
                if (existing instanceof BurnStatus) {
                    ((BurnStatus) existing).refreshFrom(effect);
                    return;
                }
                effects.set(i, effect); // Refresh / replace
                return;
            }
        }
        effects.add(effect);
    }

    public void update(float dt, Character owner) {
        for (int i = 0; i < effects.size(); i++) {
            effects.get(i).update(dt, owner);
        }
        effects.removeIf(StatusEffect::expired);
    }

    /**
     * Calculates composite speed multiplier (minimum across all active effects).
     */
    public float speedMultiplier() {
        float mult = 1.0f;
        for (StatusEffect effect : effects) {
            mult = Math.min(mult, effect.speedMultiplier());
        }
        return mult;
    }

    public boolean has(StatusKind kind) {
        for (StatusEffect effect : effects) {
            if (effect.kind() == kind) {
                return true;
            }
        }
        return false;
    }

    public void clear() {
        effects.clear();
    }

    public int size() {
        return effects.size();
    }

    public List<StatusEffect> getEffects() {
        return Collections.unmodifiableList(effects);
    }
}
