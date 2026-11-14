package com.spellstorm.combat;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.entities.Enemy;
import com.spellstorm.math.Vector2D;

/**
 * Contextual payload provided when a projectile or spell hits a target.
 */
public class HitContext {

    public final Enemy target;
    public final Vector2D impactPoint;
    public final Vector2D direction;
    public final float baseDamage;
    public final Color color;
    public final CombatServices services;

    public HitContext(Enemy target, Vector2D impactPoint, Vector2D direction,
                      float baseDamage, Color color, CombatServices services) {
        this.target = target;
        this.impactPoint = impactPoint != null ? impactPoint : new Vector2D();
        this.direction = direction != null ? direction : new Vector2D(1.0f, 0.0f);
        this.baseDamage = Math.max(0.0f, baseDamage);
        this.color = color != null ? color : Color.WHITE;
        this.services = services;
    }

    public Enemy getTarget() {
        return target;
    }

    public Vector2D getImpactPoint() {
        return impactPoint;
    }

    public Vector2D getDirection() {
        return direction;
    }

    public float getBaseDamage() {
        return baseDamage;
    }

    public Color getColor() {
        return color;
    }

    public CombatServices getServices() {
        return services;
    }
}
