package com.spellstorm.entities;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Dynamic projectile entity traveling through the arena.
 * Supports delta-time kinematics, multi-target piercing limits with damage falloff,
 * multi-hit prevention tracking per entity ID, boundary pruning, and elemental spell effect delegation.
 */
public class Projectile extends Entity {

    // Pierce balancing constants
    public static final int MAX_ALLOWED_PIERCE = 3;
    public static final float PIERCE_DAMAGE_FALLOFF = 0.80f;
    public static final float MIN_DAMAGE_MULTIPLIER = 0.25f;

    // C++ reference naming aliases
    public static final int kMaxAllowedPierce = MAX_ALLOWED_PIERCE;
    public static final float kPierceDamageFalloff = PIERCE_DAMAGE_FALLOFF;
    public static final float kMinDamageMultiplier = MIN_DAMAGE_MULTIPLIER;

    // Nested Faction alias
    public enum Faction {
        PLAYER,
        ENEMY;

        public ProjectileFaction toProjectileFaction() {
            return this == PLAYER ? ProjectileFaction.PLAYER : ProjectileFaction.ENEMY;
        }

        public static Faction from(ProjectileFaction pf) {
            return pf == ProjectileFaction.PLAYER ? PLAYER : ENEMY;
        }
    }

    private final Vector2D direction = new Vector2D();
    private float speed;
    private float damage;
    private final float initialDamage;
    private float lifetime;
    private int pierceRemaining;
    private final ProjectileFaction faction;
    private final Color primaryColor;
    private final Color secondaryColor;
    private final SpellDefinition spell;
    private final List<Long> hitEntities = new ArrayList<>();
    private final Set<Long> hitEntitiesSet = new HashSet<>();
    private float animTimer = 0.0f;

    public Projectile(Vector2D position, Vector2D dir, float speed, float radius,
                      float damage, float lifetime, int pierce, ProjectileFaction faction,
                      Color primaryColor, Color secondaryColor, SpellDefinition spell) {
        super(position, Math.max(2.0f, radius), "projectile");
        this.speed = Math.max(10.0f, speed);
        this.damage = Math.max(0.0f, damage);
        this.initialDamage = this.damage;
        this.lifetime = Math.max(0.05f, lifetime);
        this.pierceRemaining = MathUtils.clamp(pierce, 0, MAX_ALLOWED_PIERCE);
        this.faction = faction != null ? faction : ProjectileFaction.PLAYER;
        this.primaryColor = primaryColor != null ? primaryColor.cpy() : Color.WHITE.cpy();
        this.secondaryColor = secondaryColor != null ? secondaryColor.cpy() : Color.WHITE.cpy();
        this.spell = spell;

        if (dir != null && dir.len2() > 1e-4f) {
            this.direction.set(dir).nor();
        } else {
            this.direction.set(1.0f, 0.0f);
        }
        this.velocity.set(this.direction).scl(this.speed);
    }

    public Projectile(Vector2D position, Vector2D dir, float speed, float radius,
                      float damage, float lifetime, int pierce, ProjectileFaction faction,
                      Color primaryColor, Color secondaryColor) {
        this(position, dir, speed, radius, damage, lifetime, pierce, faction, primaryColor, secondaryColor, null);
    }

    public Projectile(Vector2D position, Vector2D dir, float speed, float radius,
                      float damage, float lifetime, int pierce, ProjectileFaction faction) {
        this(position, dir, speed, radius, damage, lifetime, pierce, faction, Color.WHITE, Color.WHITE, null);
    }

    public Projectile(Vector2D position, Vector2D dir, float speed, float radius,
                      float damage, float lifetime, int pierce, Faction faction,
                      Color primaryColor, Color secondaryColor, SpellDefinition spell) {
        this(position, dir, speed, radius, damage, lifetime, pierce,
                faction != null ? faction.toProjectileFaction() : ProjectileFaction.PLAYER,
                primaryColor, secondaryColor, spell);
    }

    /**
     * Checks whether the entity has already been struck by this projectile.
     */
    public boolean canHit(long entityId) {
        return isAlive() && !hitEntitiesSet.contains(entityId);
    }

    public boolean canHit(int entityId) {
        return canHit((long) entityId);
    }

    /**
     * Records entity ID to prevent multiple hits against the same target across successive frames.
     */
    public void recordHit(long entityId) {
        if (!hitEntitiesSet.contains(entityId)) {
            hitEntitiesSet.add(entityId);
            hitEntities.add(entityId);
        }
    }

    public void recordHit(int entityId) {
        recordHit((long) entityId);
    }

    /**
     * Decrements remaining pierce count and reduces damage by 20% (falloff 0.80),
     * clamped at 25% of initial damage. If pierce was 0, destroys the projectile.
     */
    public void decrementPierce() {
        if (pierceRemaining > 0) {
            pierceRemaining--;
            damage = Math.max(initialDamage * MIN_DAMAGE_MULTIPLIER, damage * PIERCE_DAMAGE_FALLOFF);
        } else {
            destroy();
        }
    }

    @Override
    public void update(float dt) {
        if (!isAlive()) {
            return;
        }

        lifetime -= dt;
        animTimer += dt;

        if (lifetime <= 0.0f) {
            destroy();
            return;
        }

        position.add(velocity.x * dt, velocity.y * dt);
    }

    // Getters and Accessors (Java Bean style and C++ parity style)
    public ProjectileFaction getFaction() { return faction; }
    public ProjectileFaction faction() { return faction; }

    public Vector2D getDirection() { return direction.cpy(); }
    public Vector2D direction() { return direction.cpy(); }

    public float getSpeed() { return speed; }
    public float speed() { return speed; }

    public float getDamage() { return damage; }
    public float damage() { return damage; }

    public float getInitialDamage() { return initialDamage; }
    public float initialDamage() { return initialDamage; }

    public float getLifetime() { return lifetime; }
    public float lifetime() { return lifetime; }

    public int getPierceRemaining() { return pierceRemaining; }
    public int pierceRemaining() { return pierceRemaining; }

    public int getHitCount() { return hitEntities.size(); }
    public int hitCount() { return hitEntities.size(); }

    public List<Long> getHitEntities() { return Collections.unmodifiableList(hitEntities); }
    public List<Long> getHitEntityIds() { return Collections.unmodifiableList(hitEntities); }

    public Color getPrimaryColor() { return primaryColor.cpy(); }
    public Color primaryColor() { return primaryColor.cpy(); }

    public Color getSecondaryColor() { return secondaryColor.cpy(); }
    public Color secondaryColor() { return secondaryColor.cpy(); }

    public SpellDefinition getSpell() { return spell; }
    public SpellDefinition spell() { return spell; }

    public float getAnimTimer() { return animTimer; }
}
