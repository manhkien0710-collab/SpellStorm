package com.spellstorm.entities;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Abstract base class for arena enemies in SpellStorm.
 *
 * Extends {@link Character} with:
 * - Categorical archetype classification ({@link EnemyType}).
 * - Contact damage and attack cooldown mechanics.
 * - Deterministic, idempotent experience reward claiming.
 * - Arena boundary containment.
 * - 8-way A* navigation steering with Line-of-Sight (LOS) charging and string-pulling.
 */
public abstract class Enemy extends Character {

    private final EnemyType enemyType;
    private float contactDamage;
    private float attackCooldown;
    private float attackTimer = 0.0f;
    private int expReward;
    private boolean rewardClaimed = false;

    // AI navigation & path follow state
    protected final List<Vector2D> navPath = new ArrayList<>();
    protected int navWaypointIndex = 0;
    protected float navRepathTimer = 0.0f;
    protected final Vector2D navLastTargetPos = new Vector2D();
    protected final Vector2D navLastPosition = new Vector2D();
    protected float navStuckTimer = 0.0f;
    protected float animTimer = 0.0f;
    private boolean facingLeft = false;

    // Optional direct target reference
    protected Player target;

    // =========================================================================
    // Constructors
    // =========================================================================

    public Enemy(EnemyType type, Vector2D position, float radius, float maxHealth,
                 float armor, float moveSpeed, float contactDamage,
                 float attackCooldown, int expReward) {
        super(position, radius, maxHealth, armor, moveSpeed, "enemy");
        this.enemyType = (type != null) ? type : EnemyType.MELEE;
        this.contactDamage = Math.max(0.0f, contactDamage);
        this.attackCooldown = Math.max(0.05f, attackCooldown);
        this.attackTimer = 0.0f;
        this.expReward = Math.max(0, expReward);
        if (position != null) {
            this.navLastPosition.set(position);
        }
    }

    // =========================================================================
    // Type & Telemetry
    // =========================================================================

    public EnemyType getType() {
        return enemyType;
    }

    public EnemyType type() {
        return enemyType;
    }

    public boolean isBoss() {
        return enemyType.isBoss();
    }

    public String getName() {
        return enemyType.getDisplayName();
    }

    public String name() {
        return enemyType.getDisplayName();
    }

    public float getSpeed() {
        return getBaseMoveSpeed();
    }

    public void setSpeed(float speed) {
        setBaseMoveSpeed(speed);
    }

    public boolean isFacingLeft() {
        return facingLeft;
    }

    public void setFacingLeft(boolean facingLeft) {
        this.facingLeft = facingLeft;
    }

    /**
     * Updates horizontal sprite facing based on horizontal velocity when moving,
     * or relative target X position when nearly stationary.
     */
    protected void updateFacing(Vector2D targetPos) {
        if (Math.abs(velocity.x) > 1.0f) {
            this.facingLeft = velocity.x < -1.0f;
        } else if (targetPos != null && Math.abs(targetPos.x - position.x) > 1e-3f) {
            this.facingLeft = targetPos.x < position.x;
        }
    }

    public float getContactDamage() {
        return contactDamage;
    }

    public float contactDamage() {
        return contactDamage;
    }

    public void setContactDamage(float dmg) {
        this.contactDamage = Math.max(0.0f, dmg);
    }

    public float getAttackCooldown() {
        return attackCooldown;
    }

    public float attackCooldown() {
        return attackCooldown;
    }

    public void setAttackCooldown(float cd) {
        this.attackCooldown = Math.max(0.05f, cd);
    }

    public float getAttackTimer() {
        return attackTimer;
    }

    public float attackTimer() {
        return attackTimer;
    }

    public boolean canAttack() {
        return isAlive() && attackTimer <= 0.0f;
    }

    public void resetAttackCooldown() {
        this.attackTimer = this.attackCooldown;
    }

    // =========================================================================
    // Experience Reward (Idempotent)
    // =========================================================================

    public int getExpReward() {
        return expReward;
    }

    public int expReward() {
        return expReward;
    }

    public void setExpReward(int exp) {
        this.expReward = Math.max(0, exp);
    }

    public boolean isRewardClaimed() {
        return rewardClaimed;
    }

    public int claimReward() {
        if (!rewardClaimed) {
            rewardClaimed = true;
            return expReward;
        }
        return 0; // Idempotent guarantee: cannot claim multiple times
    }

    // =========================================================================
    // Target & Bounds
    // =========================================================================

    public Player getTarget() {
        return target;
    }

    public void setTarget(Player target) {
        this.target = target;
    }

    public void constrainToBounds(Rectangle bounds) {
        if (bounds == null) return;
        float r = getRadius();
        float minX = bounds.x + r;
        float maxX = bounds.x + bounds.width - r;
        float minY = bounds.y + r;
        float maxY = bounds.y + bounds.height - r;
        float cx = MathUtils.clamp(getPosition().x, minX, maxX);
        float cy = MathUtils.clamp(getPosition().y, minY, maxY);
        getPosition().set(cx, cy);
    }

    // =========================================================================
    // Combat: Contact Attack
    // =========================================================================

    /**
     * Checks contact collision with target Character and applies damage if cooldown ready.
     * Returns true if damage was dealt.
     */
    public boolean tryAttack(Character targetCharacter) {
        if (!isAlive() || targetCharacter == null || !targetCharacter.isAlive() || attackTimer > 0.0f) {
            return false;
        }

        float totalRadius = getRadius() + targetCharacter.getRadius();
        if (getPosition().dist2(targetCharacter.getPosition()) <= (totalRadius * totalRadius)) {
            targetCharacter.takeDamage(contactDamage);
            this.attackTimer = this.attackCooldown;
            return true;
        }
        return false;
    }

    // =========================================================================
    // Navigation Steering (A*, LOS charging & String-pulling)
    // =========================================================================

    /**
     * Reusable navigation steering helper: uses LOS when clear, 8-way A* when blocked.
     */
    public void steerWithNavigation(float dt, Vector2D targetPos, TileMap tileMap,
                                    AStarPathfinder pathfinder, float speedMultiplier) {
        if (!isAlive()) {
            return;
        }

        if (targetPos == null) {
            return;
        }

        steerMovement(dt, targetPos, tileMap, pathfinder, speedMultiplier);
        updateFacing(targetPos);
    }

    private void steerMovement(float dt, Vector2D targetPos, TileMap tileMap,
                               AStarPathfinder pathfinder, float speedMultiplier) {
        // Direct pursuit fallback if no TileMap or pathfinder is attached
        if (tileMap == null || pathfinder == null) {
            Vector2D diff = Vector2D.sub(targetPos, getPosition());
            if (diff.len2() > 1e-4f) {
                moveInDirection(diff.nor(), speedMultiplier, dt);
            }
            return;
        }

        navRepathTimer -= dt;

        // Movement progress tracking to detect being stuck against a corner or obstacle
        if (dt > 1e-4f) {
            float dx = getPosition().x - navLastPosition.x;
            float dy = getPosition().y - navLastPosition.y;
            if ((dx * dx + dy * dy) < (2.0f * 2.0f)) {
                navStuckTimer += dt;
            } else {
                navStuckTimer = 0.0f;
            }
            navLastPosition.set(getPosition());
        }

        // Check direct Line of Sight to target
        boolean hasLOS = pathfinder.hasLineOfSight(getPosition(), targetPos, getRadius());
        float distSqToTarget = getPosition().dist2(targetPos);

        // When line of sight is clear and target is within active engagement range (650px), charge directly
        if (hasLOS && distSqToTarget < (650.0f * 650.0f)) {
            navPath.clear();
            navWaypointIndex = 0;
            Vector2D diff = Vector2D.sub(targetPos, getPosition());
            if (diff.len2() > 1e-4f) {
                moveInDirection(diff.nor(), speedMultiplier, dt);
            }
            return;
        }

        // Line of sight blocked: evaluate whether a new path calculation is needed
        float targetDispSq = (targetPos.x - navLastTargetPos.x) * (targetPos.x - navLastTargetPos.x)
                + (targetPos.y - navLastTargetPos.y) * (targetPos.y - navLastTargetPos.y);

        boolean needRepath = navPath.isEmpty() ||
                (navWaypointIndex >= navPath.size()) ||
                (navRepathTimer <= 0.0f) ||
                (targetDispSq > (64.0f * 64.0f)) ||
                (navStuckTimer > 0.35f);

        if (needRepath) {
            List<Vector2D> newPath = pathfinder.findPath(getPosition(), targetPos, getRadius());
            navPath.clear();
            if (newPath != null) {
                navPath.addAll(newPath);
            }
            navWaypointIndex = 0;
            navLastTargetPos.set(targetPos);
            navStuckTimer = 0.0f;
            // Stagger repath cadence (0.35s to 0.55s) across entity IDs to distribute CPU load
            navRepathTimer = 0.35f + (getId() % 5) * 0.05f;
        }

        if (navWaypointIndex < navPath.size()) {
            Vector2D wp = navPath.get(navWaypointIndex);

            // Advance to next waypoint if close to current (< 22px)
            while (navWaypointIndex + 1 < navPath.size() &&
                    getPosition().dist2(wp) < (22.0f * 22.0f)) {
                navWaypointIndex++;
                wp = navPath.get(navWaypointIndex);
            }

            // Path shortcutting / string-pulling: if clear LOS to waypoint + 1, advance directly
            if (navWaypointIndex + 1 < navPath.size()) {
                Vector2D nextWp = navPath.get(navWaypointIndex + 1);
                if (pathfinder.hasLineOfSight(getPosition(), nextWp, getRadius())) {
                    navWaypointIndex++;
                    wp = nextWp;
                }
            }

            Vector2D diff = Vector2D.sub(wp, getPosition());
            if (diff.len2() > 1e-4f) {
                moveInDirection(diff.nor(), speedMultiplier, dt);
            }
        } else {
            // Fallback: steer towards target
            Vector2D diff = Vector2D.sub(targetPos, getPosition());
            if (diff.len2() > 1e-4f) {
                moveInDirection(diff.nor(), speedMultiplier, dt);
            }
        }
    }

    public abstract void updateAI(float dt, Vector2D targetPos, TileMap tileMap, AStarPathfinder pathfinder);

    public void updateAI(float dt, Vector2D targetPos) {
        updateAI(dt, targetPos, null, null);
    }

    public void updateAI(float dt, Character targetCharacter, TileMap tileMap, AStarPathfinder pathfinder) {
        if (targetCharacter != null) {
            updateAI(dt, targetCharacter.getPosition(), tileMap, pathfinder);
        }
    }

    public void updateAI(float dt, Character targetCharacter) {
        updateAI(dt, targetCharacter, null, null);
    }

    public List<Vector2D> getNavPath() {
        return Collections.unmodifiableList(navPath);
    }

    public List<Vector2D> navPath() {
        return Collections.unmodifiableList(navPath);
    }

    public int getNavWaypointIndex() {
        return navWaypointIndex;
    }

    public void clearNavPath() {
        navPath.clear();
        navWaypointIndex = 0;
    }

    public float getAnimTimer() {
        return animTimer;
    }

    // =========================================================================
    // Simulation Lifecycle
    // =========================================================================

    @Override
    public void update(float dt) {
        super.update(dt);
        if (attackTimer > 0.0f) {
            attackTimer = Math.max(0.0f, attackTimer - dt);
        }
        if (Math.abs(velocity.x) > 1.0f) {
            this.facingLeft = velocity.x < -1.0f;
        }
        animTimer += dt;
    }
}
