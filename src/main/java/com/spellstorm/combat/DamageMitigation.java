package com.spellstorm.combat;

/**
 * Pure combat damage mitigation calculations based on target armor.
 * Formula: damage * 100 / (100 + max(0, armor)).
 * Armor gives diminishing returns: 100 armor exactly halves incoming damage (50% mitigation).
 * Non-positive damage yields 0 damage dealt. Negative armor is clamped to 0 (no damage amplification).
 */
public final class DamageMitigation {

    private DamageMitigation() {
    }

    /**
     * Calculates effective damage dealt after applying target armor mitigation.
     *
     * @param rawDamage incoming raw damage value
     * @param targetArmor target's armor value
     * @return actual damage dealt
     */
    public static float calculateDamage(float rawDamage, float targetArmor) {
        if (rawDamage <= 0.0f) {
            return 0.0f;
        }
        float effectiveArmor = Math.max(0.0f, targetArmor);
        return rawDamage * 100.0f / (100.0f + effectiveArmor);
    }

    /**
     * C++ reference alias for calculateDamage.
     */
    public static float mitigateDamage(float amount, float armor) {
        return calculateDamage(amount, armor);
    }
}
