package com.spellstorm.entities;

import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;

/**
 * Cunning sorcerer enemy that kites the player (Cultist).
 * Maintains a preferred combat range, strafes laterally, and fires ranged attacks when in range.
 */
public class RangedEnemy extends Enemy {

    public static final float DEFAULT_HEALTH = 20.0f;
    public static final float DEFAULT_SPEED = 110.0f;
    public static final float DEFAULT_DAMAGE = 10.0f;
    public static final float DEFAULT_RADIUS = 15.0f;
    public static final float DEFAULT_PREFERRED_DIST = 240.0f;
    public static final float DEFAULT_TOLERANCE = 35.0f;
    public static final float DEFAULT_SHOOT_RANGE = 350.0f;
    public static final float DEFAULT_CONTACT_COOLDOWN = 1.4f;
    public static final float DEFAULT_RANGED_COOLDOWN = 1.8f;
    public static final int DEFAULT_EXP_REWARD = 15;

    public static final float LEGACY_HEALTH = 45.0f;
    public static final float LEGACY_SPEED = 120.0f;
    public static final float LEGACY_DAMAGE = 8.0f;
    public static final float LEGACY_SHOOT_RANGE = 310.0f;

    private float preferredDistance = DEFAULT_PREFERRED_DIST;
    private float distanceTolerance = DEFAULT_TOLERANCE;
    private float shootRange = DEFAULT_SHOOT_RANGE;
    private boolean inAttackRange = false;
    private float rangedAttackCooldown = DEFAULT_RANGED_COOLDOWN;
    private float rangedAttackTimer = 1.0f; // initial delay before first shot
    private final Vector2D targetPosCopy = new Vector2D();

    public RangedEnemy(Vector2D position) {
        this(position, DEFAULT_HEALTH, DEFAULT_SPEED, DEFAULT_DAMAGE, DEFAULT_RADIUS,
                DEFAULT_SHOOT_RANGE, DEFAULT_CONTACT_COOLDOWN, DEFAULT_RANGED_COOLDOWN, DEFAULT_EXP_REWARD);
    }

    public RangedEnemy(Vector2D position, boolean legacy) {
        this(position,
                legacy ? LEGACY_HEALTH : DEFAULT_HEALTH,
                legacy ? LEGACY_SPEED : DEFAULT_SPEED,
                legacy ? LEGACY_DAMAGE : DEFAULT_DAMAGE,
                DEFAULT_RADIUS,
                legacy ? LEGACY_SHOOT_RANGE : DEFAULT_SHOOT_RANGE,
                DEFAULT_CONTACT_COOLDOWN,
                DEFAULT_RANGED_COOLDOWN,
                DEFAULT_EXP_REWARD);
    }

    public RangedEnemy(Vector2D position, float health, float speed, float contactDamage,
                       float radius, float shootRange, float contactCooldown,
                       float rangedCooldown, int expReward) {
        super(EnemyType.RANGED, position, radius, health, 0.0f, speed, contactDamage, contactCooldown, expReward);
        this.shootRange = shootRange;
        this.rangedAttackCooldown = Math.max(0.05f, rangedCooldown);
        this.rangedAttackTimer = 1.0f;
    }

    public static RangedEnemy createLegacyReference(Vector2D position) {
        return new RangedEnemy(position, true);
    }

    public float getPreferredDistance() {
        return preferredDistance;
    }

    public float preferredDistance() {
        return preferredDistance;
    }

    public void setPreferredDistance(float preferredDistance) {
        this.preferredDistance = Math.max(0.0f, preferredDistance);
    }

    public float getDistanceTolerance() {
        return distanceTolerance;
    }

    public float distanceTolerance() {
        return distanceTolerance;
    }

    public void setDistanceTolerance(float distanceTolerance) {
        this.distanceTolerance = Math.max(0.0f, distanceTolerance);
    }

    public float getShootRange() {
        return shootRange;
    }

    public float shootRange() {
        return shootRange;
    }

    public void setShootRange(float shootRange) {
        this.shootRange = Math.max(0.0f, shootRange);
    }

    public boolean isInAttackRange() {
        return inAttackRange;
    }

    public boolean inAttackRange() {
        return inAttackRange;
    }

    public float getRangedAttackCooldown() {
        return rangedAttackCooldown;
    }

    public float rangedAttackCooldown() {
        return rangedAttackCooldown;
    }

    public void setRangedAttackCooldown(float cd) {
        this.rangedAttackCooldown = Math.max(0.05f, cd);
    }

    public float getRangedAttackTimer() {
        return rangedAttackTimer;
    }

    public float rangedAttackTimer() {
        return rangedAttackTimer;
    }

    public void resetRangedAttackCooldown() {
        this.rangedAttackTimer = this.rangedAttackCooldown;
    }

    public boolean canShoot() {
        return isAlive() && inAttackRange && rangedAttackTimer <= 0.0f;
    }

    /** True during the brief windup window right before releasing a ranged bolt. */
    public boolean isTelegraphingCast() {
        return isAlive() && inAttackRange && rangedAttackTimer > 0.0f && rangedAttackTimer <= 0.35f;
    }

    /** True immediately after firing a ranged bolt for cast release animation feedback. */
    public boolean isReleasingCast() {
        return isAlive() && inAttackRange && rangedAttackTimer > Math.max(0.0f, rangedAttackCooldown - 0.22f);
    }

    /**
     * Spawns an enemy bolt traveling toward targetPos and resets ranged attack timer.
     *
     * @param targetPos target coordinates
     * @return newly spawned projectile
     */
    public Projectile shoot(Vector2D targetPos) {
        resetRangedAttackCooldown();
        Vector2D delta = (targetPos != null) ? Vector2D.sub(targetPos, getPosition()) : new Vector2D(1.0f, 0.0f);
        Vector2D dir = (delta.len2() > 1e-4f) ? delta.nor() : new Vector2D(1.0f, 0.0f);
        return new Projectile(
                getPosition().cpy(), dir, 220.0f, 7.0f, 15.0f, 2.4f, 0,
                ProjectileFaction.ENEMY,
                new com.badlogic.gdx.graphics.Color(215f / 255f, 80f / 255f, 245f / 255f, 1.0f),
                new com.badlogic.gdx.graphics.Color(1.0f, 140f / 255f, 1.0f, 1.0f),
                null
        );
    }

    public Projectile shoot() {
        return shoot(targetPosCopy);
    }

    public Vector2D getTargetPosCopy() {
        return targetPosCopy.cpy();
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (rangedAttackTimer > 0.0f) {
            rangedAttackTimer = Math.max(0.0f, rangedAttackTimer - dt);
        }
    }

    @Override
    public void updateAI(float dt, Vector2D targetPos, TileMap tileMap, AStarPathfinder pathfinder) {
        if (!isAlive() || targetPos == null) {
            return;
        }

        targetPosCopy.set(targetPos);

        Vector2D diff = Vector2D.sub(targetPos, getPosition());
        float dist = diff.len();

        if (dist < 1e-4f) {
            diff = new Vector2D(1.0f, 0.0f);
            dist = 1.0f;
        }

        Vector2D dir = diff.cpy().scl(1.0f / dist);

        // Line of sight verification across obstacles and rivers
        boolean hasLos = true;
        if (pathfinder != null) {
            hasLos = pathfinder.hasLineOfSight(getPosition(), targetPos, getRadius());
        }

        this.inAttackRange = (dist <= preferredDistance + 70.0f || dist <= shootRange) && hasLos;

        if (!hasLos || dist > preferredDistance + distanceTolerance) {
            // Obstructed or too far away: steer via navigation towards player
            steerWithNavigation(dt, targetPos, tileMap, pathfinder, 1.0f);
        } else if (dist < preferredDistance - distanceTolerance) {
            // Too close with clear LOS: retreat / kite away from player
            moveInDirection(dir.cpy().scl(-1.0f), 1.0f, dt);
        } else {
            // In preferred range sweet spot with LOS: strafe laterally around player
            Vector2D strafeDir = new Vector2D(-dir.y, dir.x);
            float strafeFactor = (float) Math.sin(animTimer * 1.5f) > 0.0f ? 0.45f : -0.45f;
            moveInDirection(strafeDir, strafeFactor, dt);
        }
        updateFacing(targetPos);
    }
}
