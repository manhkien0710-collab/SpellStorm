package com.spellstorm.combat;

/**
 * Strategy interface representing a reusable piece of on-hit behaviour.
 * Spells are composed from one or more HitEffects.
 */
@FunctionalInterface
public interface HitEffect {

    void apply(HitContext context);
}
