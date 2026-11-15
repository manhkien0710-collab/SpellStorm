package com.spellstorm.entities;

import com.spellstorm.combat.DamageInfo;
import com.spellstorm.combat.DamageMitigation;
import com.spellstorm.combat.StatusEffect;
import com.spellstorm.combat.StatusEffectList;
import com.spellstorm.combat.StatusKind;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.WorldGrid;

/**
 * Base class for all living, mobile entities in SpellStorm (Player and Enemies).
 *
 * Implements:
 * - Health points, maximum health clamping, and armor mitigation.
 * - Frame-rate independent exponential knockback decay: K *= exp(-7.0 * dt).
 * - Resistance scaling (knockback resistance and slow resistance clamped [0.0, 1.0]).
 * - Damage mitigation formula: rawDamage * 100 / (100 + max(0, armor)).
 * - Hitflash feedback timer (0.12s).
 * - Timed status effect conditions (StatusEffectList).
 */
public abstract class Character extends Entity {

    public static final float HIT_FLASH_DURATION = 0.12f;
    public static final float KNOCKBACK_DECAY_RATE = 7.0f;
    public static final float KNOCKBACK_SNAP_EPSILON_SQ = 1.0f;

    protected float health;
    protected float maxHealth;
    protected float armor;
    protected float baseMoveSpeed;

    protected float slowResistance = 0.0f;
    protected float knockbackResistance = 0.0f;
    protected float hitFlashTimer = 0.0f;

    protected final Vector2D knockback = new Vector2D();
    protected float speedMultiplier = 1.0f;
    protected final StatusEffectList statuses = new StatusEffectList();
    protected WorldGrid activeWorldGrid;

    // =========================================================================
    // Constructors
    // =========================================================================

    protected Character(Vector2D position, float radius, float maxHealth, float armor,
                        float baseMoveSpeed, String tag) {
        super(position, radius, tag);
        this.maxHealth = Math.max(1.0f, maxHealth);
        this.health = this.maxHealth;
        this.armor = Math.max(0.0f, armor);
        this.baseMoveSpeed = Math.max(0.0f, baseMoveSpeed);
    }

    protected Character(Vector2D position, float radius, float maxHealth, float armor, float baseMoveSpeed) {
        this(position, radius, maxHealth, armor, baseMoveSpeed, "character");
    }

    // =========================================================================
    // Combat: Damage & Healing
    // =========================================================================

    /**
     * Calculates mitigated damage based on incoming amount and target armor.
     * Formula: amount * 100 / (100 + max(0, armor))
     */
    public static float calculateMitigatedDamage(float amount, float targetArmor) {
        if (amount <= 0.0f) {
            return 0.0f;
        }
        float effectiveArmor = Math.max(0.0f, targetArmor);
        return amount * 100.0f / (100.0f + effectiveArmor);
    }

    /**
     * Applies damage using a DamageInfo object, supporting direct physical damage and true DoT damage.
     *
     * @param damage DamageInfo descriptor
     * @return actual damage dealt
     */
    public float takeDamage(DamageInfo damage) {
        if (damage == null) {
            return 0.0f;
        }
        return takeDamage(damage.getAmount(), damage.isIgnoreArmor());
    }

    /**
     * Applies damage to this character taking armor mitigation into account.
     *
     * @param amount raw damage value
     * @return actual damage dealt
     */
    public float takeDamage(float amount) {
        return takeDamage(amount, false);
    }

    /**
     * Applies damage to this character with optional armor bypass (e.g. burn DoT).
     *
     * @param amount raw damage value
     * @param ignoreArmor whether to bypass armor mitigation
     * @return actual damage dealt
     */
    public float takeDamage(float amount, boolean ignoreArmor) {
        if (!isAlive() || amount <= 0.0f) {
            return 0.0f;
        }

        float dealt = ignoreArmor ? amount : calculateMitigatedDamage(amount, this.armor);
        this.health -= dealt;
        this.hitFlashTimer = HIT_FLASH_DURATION;

        if (this.health <= 0.0f) {
            this.health = 0.0f;
            destroy();
        }

        return dealt;
    }

    /**
     * Heals the character up to maxHealth. Dead characters cannot be healed.
     *
     * @param amount heal amount
     */
    public void heal(float amount) {
        if (!isAlive() || amount <= 0.0f) {
            return;
        }
        this.health = Math.min(this.maxHealth, this.health + amount);
    }

    @Override
    public boolean isAlive() {
        return active && health > 0.0f;
    }

    public float getHealthRatio() {
        return maxHealth > 0.0f ? MathUtils.clamp(health / maxHealth, 0.0f, 1.0f) : 0.0f;
    }

    public void scaleMaxHealth(float scale) {
        if (scale <= 0.0f) return;
        this.maxHealth *= scale;
        this.health = Math.min(this.health * scale, this.maxHealth);
    }

    // =========================================================================
    // Knockback Physics
    // =========================================================================

    /**
     * Applies a physical knockback impulse, reduced by knockback resistance.
     */
    public void applyKnockback(Vector2D impulse) {
        if (impulse != null && isAlive()) {
            float factor = Math.max(0.0f, 1.0f - knockbackResistance);
            this.knockback.mulAdd(impulse, factor);
        }
    }

    public void applyKnockback(float ix, float iy) {
        if (isAlive()) {
            float factor = Math.max(0.0f, 1.0f - knockbackResistance);
            this.knockback.add(ix * factor, iy * factor);
        }
    }

    /**
     * Updates exponential knockback decay: K *= exp(-7.0 * dt).
     * Snaps to zero when magnitude squared falls below 1.0.
     */
    public void updateKnockback(float dt) {
        if (dt <= 0.0f) {
            return;
        }

        float decayFactor = (float) Math.exp(-KNOCKBACK_DECAY_RATE * dt);
        this.knockback.scl(decayFactor);

        if (this.knockback.len2() < KNOCKBACK_SNAP_EPSILON_SQ) {
            this.knockback.setZero();
        }
    }

    public Vector2D getKnockback() {
        return knockback;
    }

    public void setKnockback(float kx, float ky) {
        this.knockback.set(kx, ky);
    }

    // =========================================================================
    // Movement & Speed
    // =========================================================================

    public float getBaseMoveSpeed() {
        return baseMoveSpeed;
    }

    public void setBaseMoveSpeed(float speed) {
        this.baseMoveSpeed = Math.max(0.0f, speed);
    }

    public float getCurrentMoveSpeed() {
        float slowed = statuses.speedMultiplier();
        float combinedMult = slowed * speedMultiplier;
        float effectiveMultiplier = 1.0f - (1.0f - combinedMult) * (1.0f - slowResistance);
        return baseMoveSpeed * Math.max(0.0f, effectiveMultiplier);
    }

    public float currentMoveSpeed() {
        return getCurrentMoveSpeed();
    }

    public float getSpeedMultiplier() {
        return speedMultiplier;
    }

    public void setSpeedMultiplier(float multiplier) {
        this.speedMultiplier = Math.max(0.0f, multiplier);
    }

    public WorldGrid getActiveWorldGrid() {
        return activeWorldGrid;
    }

    public void setActiveWorldGrid(WorldGrid activeWorldGrid) {
        this.activeWorldGrid = activeWorldGrid;
    }

    /**
     * Calculates combined velocity (desired move vector + knockback) and steps position.
     * When activeWorldGrid is set, resolves sliding collision so water and obstacles block movement.
     */
    public void moveInDirection(Vector2D direction, float speedFactor, float dt) {
        if (!isAlive() || dt <= 0.0f) {
            velocity.setZero();
            return;
        }

        Vector2D desired = (direction != null && !direction.isZero())
                ? direction.cpy().nor().scl(getCurrentMoveSpeed() * speedFactor)
                : new Vector2D();

        velocity.set(desired).add(knockback);
        float oldX = position.x;
        float oldY = position.y;
        float candX = oldX + velocity.x * dt;
        float candY = oldY + velocity.y * dt;

        if (activeWorldGrid != null) {
            Vector2D resolved = activeWorldGrid.resolveCollision(oldX, oldY, candX, candY, radius);
            position.set(resolved);
            velocity.set((resolved.x - oldX) / dt, (resolved.y - oldY) / dt);
        } else {
            position.set(candX, candY);
        }
        updateKnockback(dt);
    }

    // =========================================================================
    // Simulation Lifecycle
    // =========================================================================

    @Override
    public void update(float dt) {
        if (!isAlive()) {
            return;
        }

        statuses.update(dt, this);

        if (hitFlashTimer > 0.0f) {
            hitFlashTimer = Math.max(0.0f, hitFlashTimer - dt);
        }

        updateKnockback(dt);
    }

    public void resetCharacter(Vector2D position, float maxHealth, float armor, float baseMoveSpeed) {
        setPosition(position);
        revive();
        this.maxHealth = Math.max(1.0f, maxHealth);
        this.health = this.maxHealth;
        this.armor = Math.max(0.0f, armor);
        this.baseMoveSpeed = Math.max(0.0f, baseMoveSpeed);
        this.slowResistance = 0.0f;
        this.knockbackResistance = 0.0f;
        this.hitFlashTimer = 0.0f;
        this.knockback.setZero();
        this.velocity.setZero();
        this.speedMultiplier = 1.0f;
        this.statuses.clear();
    }

    // =========================================================================
    // Resistances & Getters/Setters
    // =========================================================================

    public float getHealth() { return health; }
    public void setHealth(float health) { this.health = MathUtils.clamp(health, 0.0f, maxHealth); }

    public float getMaxHealth() { return maxHealth; }
    public void setMaxHealth(float maxHealth) {
        this.maxHealth = Math.max(1.0f, maxHealth);
        this.health = Math.min(this.health, this.maxHealth);
    }

    public float getArmor() { return armor; }
    public void setArmor(float armor) { this.armor = Math.max(0.0f, armor); }

    public float getSlowResistance() { return slowResistance; }
    public void setSlowResistance(float slowResistance) {
        this.slowResistance = MathUtils.clamp(slowResistance, 0.0f, 1.0f);
    }

    public float getKnockbackResistance() { return knockbackResistance; }
    public void setKnockbackResistance(float knockbackResistance) {
        this.knockbackResistance = MathUtils.clamp(knockbackResistance, 0.0f, 1.0f);
    }

    public float getHitFlashTimer() { return hitFlashTimer; }
    public boolean isHitFlashing() { return hitFlashTimer > 0.0f; }

    // =========================================================================
    // Status Effects & C++ Parity Aliases
    // =========================================================================

    public void addStatus(StatusEffect status) {
        if (status != null && isAlive()) {
            statuses.add(status);
        }
    }

    public boolean hasStatus(StatusKind kind) {
        return statuses.has(kind);
    }

    public StatusEffectList getStatuses() {
        return statuses;
    }

    public StatusEffectList statuses() {
        return statuses;
    }

    public float health() { return getHealth(); }
    public float maxHealth() { return getMaxHealth(); }
    public float armor() { return getArmor(); }
    public float healthRatio() { return getHealthRatio(); }
    public float radius() { return getRadius(); }
}
