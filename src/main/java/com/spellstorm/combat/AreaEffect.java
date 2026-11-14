package com.spellstorm.combat;

import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.List;

/**
 * Area explosion on-hit effect: applies child effects to every enemy within radius.
 */
public class AreaEffect implements HitEffect {

    private final float radius;
    private final boolean includePrimary;
    private final List<HitEffect> onEachTarget = new ArrayList<>();

    public AreaEffect(float radius, boolean includePrimaryTarget) {
        this.radius = Math.max(0.0f, radius);
        this.includePrimary = includePrimaryTarget;
    }

    public AreaEffect then(HitEffect effect) {
        if (effect != null) {
            onEachTarget.add(effect);
        }
        return this;
    }

    public float getRadius() {
        return radius;
    }

    public boolean isIncludePrimary() {
        return includePrimary;
    }

    public List<HitEffect> getOnEachTarget() {
        return onEachTarget;
    }

    @Override
    public void apply(HitContext context) {
        if (context == null || context.services == null) {
            return;
        }

        Vector2D center = context.impactPoint.cpy();
        long primaryId = context.target != null ? context.target.getId() : -1L;

        context.services.forEachEnemyInRadius(center, radius, enemy -> {
            if (enemy == null || !enemy.isAlive()) {
                return;
            }
            if (!includePrimary && enemy.getId() == primaryId) {
                return;
            }

            Vector2D dir = Vector2D.sub(enemy.getPosition(), center);
            if (dir.len2() > 1e-4f) {
                dir.nor();
            } else {
                dir.set(1.0f, 0.0f);
            }

            HitContext areaContext = new HitContext(enemy, center, dir, context.baseDamage, context.color, context.services);
            for (HitEffect effect : onEachTarget) {
                if (!enemy.isAlive()) {
                    break;
                }
                effect.apply(areaContext);
            }
        });
    }
}
