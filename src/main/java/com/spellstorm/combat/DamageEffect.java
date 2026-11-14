package com.spellstorm.combat;

/**
 * On-hit effect dealing extra direct damage: baseDamage * multiplier.
 */
public class DamageEffect implements HitEffect {

    private final float multiplier;

    public DamageEffect(float multiplier) {
        this.multiplier = multiplier;
    }

    public float getMultiplier() {
        return multiplier;
    }

    @Override
    public void apply(HitContext context) {
        if (context == null || context.target == null || !context.target.isAlive()) {
            return;
        }
        float dealtDamage = context.baseDamage * multiplier;
        if (context.services != null) {
            context.services.damageEnemy(context.target, new DamageInfo(dealtDamage, false), context.color);
        } else {
            context.target.takeDamage(new DamageInfo(dealtDamage, false));
        }
    }
}
