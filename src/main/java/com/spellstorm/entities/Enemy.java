package com.spellstorm.entities;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileMapAdapter;
import com.spellstorm.world.WorldGrid;

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

    public static final int NAV_SEARCH_RADIUS_TILES = 44;
    public static final float NAV_SEARCH_RADIUS_PX = NAV_SEARCH_RADIUS_TILES * TileMap.TILE_SIZE;

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
    private float navStuckRecoveryTimer = 0.0f;
    protected boolean navPathIsPartial = false;
    private int navPartialRepathCount = 0;
    private float navShorelineSlideSign = 1.0f;
    private TileMap cachedTileMap = null;
    private WorldGrid cachedTileMapAdapter = null;
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
        this.navShorelineSlideSign = (getId() % 2 == 0) ? 1.0f : -1.0f;
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
        WorldGrid grid = resolveWorldGrid(tileMap, pathfinder);
        steerWithWorldGrid(dt, targetPos, grid, pathfinder, speedMultiplier);
    }

    /**
     * WorldGrid-backed navigation steering with collision-resolved movement,
     * partial-path repath backoff, and shoreline tangent fallback.
     */
    public final void steerWithWorldGrid(float dt, Vector2D targetPos, WorldGrid worldGrid,
                                           AStarPathfinder pathfinder, float speedMultiplier) {
        if (!isAlive() || targetPos == null) {
            return;
        }
        setActiveWorldGrid(worldGrid);
        steerMovement(dt, targetPos, worldGrid, pathfinder, speedMultiplier);
        updateFacing(targetPos);
    }

    protected WorldGrid resolveWorldGrid(TileMap tileMap, AStarPathfinder pathfinder) {
        if (tileMap == null) {
            return (pathfinder != null) ? pathfinder.getWorldGrid() : null;
        }
        if (pathfinder != null && pathfinder.getTileMap() == tileMap && pathfinder.getWorldGrid() != null) {
            return pathfinder.getWorldGrid();
        }
        if (cachedTileMap != tileMap) {
            cachedTileMap = tileMap;
            cachedTileMapAdapter = new TileMapAdapter(tileMap);
        }
        return cachedTileMapAdapter;
    }

    private void steerMovement(float dt, Vector2D targetPos, WorldGrid worldGrid,
                               AStarPathfinder pathfinder, float speedMultiplier) {
        // Direct pursuit fallback if no WorldGrid or pathfinder is attached
        if (worldGrid == null || pathfinder == null) {
            Vector2D diff = Vector2D.sub(targetPos, getPosition());
            if (diff.len2() > 1e-4f) {
                moveInDirection(diff.nor(), speedMultiplier, dt);
            }
            return;
        }

        navRepathTimer -= dt;
        if (navStuckRecoveryTimer > 0.0f) {
            navStuckRecoveryTimer = Math.max(0.0f, navStuckRecoveryTimer - dt);
        }

        // Movement progress tracking to detect being stuck against a bank or obstacle
        float expectedStep = getCurrentMoveSpeed() * Math.max(0.1f, speedMultiplier) * Math.max(0.0f, dt);
        float progressThreshold = Math.min(2.0f, Math.max(0.15f, expectedStep * 0.25f));
        if (dt > 1e-4f) {
            float dx = getPosition().x - navLastPosition.x;
            float dy = getPosition().y - navLastPosition.y;
            if ((dx * dx + dy * dy) < (progressThreshold * progressThreshold)) {
                navStuckTimer += dt;
            } else {
                navStuckTimer = 0.0f;
            }
            navLastPosition.set(getPosition());
        }

        float losProbeRadius = Math.min(getRadius() + 2.0f, getRadius() * 1.15f);
        float distSqToTarget = getPosition().dist2(targetPos);
        // When close to target, allow exact radius LOS so melee range against banks still connects;
        // at longer range require a 2px safety margin so enemies don't skim bridge entrance corners.
        float directLosRadius = (distSqToTarget < (96.0f * 96.0f)) ? getRadius() : losProbeRadius;
        boolean hasLOS = pathfinder.hasLineOfSight(getPosition(), targetPos, directLosRadius);

        // When line of sight is clear and target is within active engagement range (650px), charge directly
        if (hasLOS && distSqToTarget < (650.0f * 650.0f)) {
            navPath.clear();
            navWaypointIndex = 0;
            navPathIsPartial = false;
            navPartialRepathCount = 0;
            navStuckRecoveryTimer = 0.0f;
            Vector2D diff = Vector2D.sub(targetPos, getPosition());
            if (diff.len2() > 1e-4f) {
                moveInDirection(diff.nor(), speedMultiplier, dt);
            }
            return;
        }

        // Line of sight blocked: evaluate whether a new path calculation is needed
        float targetDispSq = (targetPos.x - navLastTargetPos.x) * (targetPos.x - navLastTargetPos.x)
                + (targetPos.y - navLastTargetPos.y) * (targetPos.y - navLastTargetPos.y);

        boolean needRepath;
        if (navPathIsPartial) {
            needRepath = navPath.isEmpty()
                    || (navRepathTimer <= 0.0f)
                    || (targetDispSq > (128.0f * 128.0f));
        } else {
            needRepath = navPath.isEmpty()
                    || (navWaypointIndex >= navPath.size())
                    || (navRepathTimer <= 0.0f)
                    || (targetDispSq > (64.0f * 64.0f))
                    || (navStuckTimer > 0.35f);
        }

        if (needRepath) {
            boolean wasStuck = navStuckTimer > 0.35f;
            if (wasStuck) {
                navStuckRecoveryTimer = 0.55f;
            }
            List<Vector2D> newPath = pathfinder.findPath(getPosition(), targetPos, getRadius());
            navPath.clear();
            if (newPath != null) {
                navPath.addAll(newPath);
            }
            navWaypointIndex = 0;
            navLastTargetPos.set(targetPos);
            navStuckTimer = 0.0f;

            if (!pathfinder.didLastSearchReachGoal()) {
                navPathIsPartial = true;
                navPartialRepathCount = Math.min(4, navPartialRepathCount + 1);
                float backoff = Math.min(3.0f, 0.6f * (1 << (navPartialRepathCount - 1)));
                navRepathTimer = backoff + (getId() % 5) * 0.05f;
            } else {
                navPathIsPartial = false;
                navPartialRepathCount = 0;
                // Stagger repath cadence (0.35s to 0.55s) across entity IDs to distribute CPU load
                navRepathTimer = 0.35f + (getId() % 5) * 0.05f;
            }
        }

        if (navWaypointIndex < navPath.size()) {
            Vector2D wp = navPath.get(navWaypointIndex);

            // Advance to next waypoint if close to current (< 22px)
            while (navWaypointIndex + 1 < navPath.size()
                    && getPosition().dist2(wp) < (22.0f * 22.0f)) {
                navWaypointIndex++;
                wp = navPath.get(navWaypointIndex);
            }

            // If at final waypoint of a partial shoreline path and arrived within threshold, switch to shoreline slide
            if (navPathIsPartial && navWaypointIndex == navPath.size() - 1
                    && getPosition().dist2(wp) < (22.0f * 22.0f)) {
                navWaypointIndex++;
            } else {
                // Path shortcutting / string-pulling: if clear LOS to waypoint + 1 (with safety clearance), advance directly
                if (navWaypointIndex + 1 < navPath.size()) {
                    Vector2D nextWp = navPath.get(navWaypointIndex + 1);
                    if (pathfinder.hasLineOfSight(getPosition(), nextWp, losProbeRadius)) {
                        navWaypointIndex++;
                        wp = nextWp;
                    }
                }

                Vector2D diff = Vector2D.sub(wp, getPosition());
                if (diff.len2() > 1e-4f) {
                    Vector2D moveDir = diff.nor();
                    if (navStuckRecoveryTimer > 0.0f || navStuckTimer > 0.35f) {
                        Vector2D slideDir = computeCornerOrShorelineSlide(worldGrid, moveDir, getRadius(), !navPathIsPartial);
                        if (slideDir != null) {
                            moveInDirection(slideDir, speedMultiplier, dt);
                            // Clear recovery early once we make solid forward progress toward waypoint
                            float dotForward = slideDir.dot(moveDir);
                            if (dotForward > 0.5f && getPosition().dist2(navLastPosition) >= (progressThreshold * progressThreshold)) {
                                navStuckRecoveryTimer = 0.0f;
                            }
                        } else {
                            velocity.setZero();
                        }
                    } else {
                        float prevX = getPosition().x;
                        float prevY = getPosition().y;
                        moveInDirection(moveDir, speedMultiplier, dt);
                        if (dt > 1e-4f && expectedStep > 0.5f) {
                            float movedSq = (getPosition().x - prevX) * (getPosition().x - prevX)
                                    + (getPosition().y - prevY) * (getPosition().y - prevY);
                            float minDirectStep = expectedStep * 0.15f;
                            if (movedSq < (minDirectStep * minDirectStep)) {
                                navStuckRecoveryTimer = 0.35f;
                            }
                        }
                    }
                }
                return;
            }
        }

        // Reached end of path (or partial shoreline endpoint): slide tangentially along bank instead of thrashing
        Vector2D toTarget = Vector2D.sub(targetPos, getPosition());
        if (toTarget.len2() > 1e-4f) {
            Vector2D targetDir = toTarget.nor();
            if (navPathIsPartial || navStuckRecoveryTimer > 0.0f || navStuckTimer > 0.35f) {
                Vector2D slideDir = computeCornerOrShorelineSlide(worldGrid, targetDir, getRadius(), false);
                if (slideDir != null) {
                    moveInDirection(slideDir, speedMultiplier, dt);
                } else {
                    velocity.setZero();
                }
            } else {
                moveInDirection(targetDir, speedMultiplier, dt);
            }
        }
    }

    /**
     * Finds a collision-cleared tangential direction along a shoreline or around a blocked corner.
     * When preferForwardCorner is true, tries small forward-diagonal deflections (+/-45 deg) first
     * so corner-snagged enemies step cleanly onto bridge entrances instead of backtracking.
     */
    protected Vector2D computeShorelineSlideDirection(WorldGrid worldGrid, Vector2D blockedDir, float entityRadius) {
        return computeCornerOrShorelineSlide(worldGrid, blockedDir, entityRadius, false);
    }

    private Vector2D computeCornerOrShorelineSlide(WorldGrid worldGrid, Vector2D blockedDir,
                                                   float entityRadius, boolean preferForwardCorner) {
        if (worldGrid == null || blockedDir == null || blockedDir.len2() < 1e-6f) {
            return null;
        }
        Vector2D base = blockedDir.cpy().nor();
        float probeDist = Math.max(16.0f, getCurrentMoveSpeed() * 0.25f);
        Vector2D pos = getPosition();
        float side = navShorelineSlideSign;

        float[] angleOffsetsDeg = preferForwardCorner
                ? new float[] {
                        side * 45.0f, -side * 45.0f,
                        side * 60.0f, -side * 60.0f,
                        side * 90.0f, -side * 90.0f,
                        side * 120.0f, -side * 120.0f
                }
                : new float[] {
                        side * 60.0f, side * 90.0f, side * 120.0f,
                        -side * 60.0f, -side * 90.0f, -side * 120.0f
                };
        float[] sideSigns = preferForwardCorner
                ? new float[] { side, -side, side, -side, side, -side, side, -side }
                : new float[] { side, side, side, -side, -side, -side };

        for (int i = 0; i < angleOffsetsDeg.length; i++) {
            double rad = Math.toRadians(angleOffsetsDeg[i]);
            float cos = (float) Math.cos(rad);
            float sin = (float) Math.sin(rad);
            float cx = base.x * cos - base.y * sin;
            float cy = base.x * sin + base.y * cos;

            Vector2D resolved = worldGrid.resolveCollision(
                    pos.x, pos.y,
                    pos.x + cx * probeDist,
                    pos.y + cy * probeDist,
                    entityRadius);
            float rx = resolved.x - pos.x;
            float ry = resolved.y - pos.y;
            float progressSq = rx * rx + ry * ry;
            float minProgress = probeDist * 0.35f;
            if (progressSq >= (minProgress * minProgress)
                    && worldGrid.isTraversable(resolved.x, resolved.y, entityRadius)) {
                if (sideSigns[i] != navShorelineSlideSign) {
                    navShorelineSlideSign = sideSigns[i];
                }
                return new Vector2D(cx, cy);
            }
        }
        return null;
    }

    public abstract void updateAIWithGrid(float dt, Vector2D targetPos, WorldGrid worldGrid, AStarPathfinder pathfinder);

    public void updateAI(float dt, Vector2D targetPos, TileMap tileMap, AStarPathfinder pathfinder) {
        updateAIWithGrid(dt, targetPos, resolveWorldGrid(tileMap, pathfinder), pathfinder);
    }

    public void updateAI(float dt, Vector2D targetPos) {
        updateAIWithGrid(dt, targetPos, null, null);
    }

    public void updateAIWithGrid(float dt, Character targetCharacter, WorldGrid worldGrid, AStarPathfinder pathfinder) {
        if (targetCharacter != null) {
            updateAIWithGrid(dt, targetCharacter.getPosition(), worldGrid, pathfinder);
        }
    }

    public void updateAI(float dt, Character targetCharacter, TileMap tileMap, AStarPathfinder pathfinder) {
        if (targetCharacter != null) {
            updateAI(dt, targetCharacter.getPosition(), tileMap, pathfinder);
        }
    }

    public void updateAI(float dt, Character targetCharacter) {
        updateAIWithGrid(dt, targetCharacter, null, null);
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

    public Vector2D getNavLastPosition() {
        return navLastPosition;
    }

    public Vector2D getNavLastTargetPos() {
        return navLastTargetPos;
    }

    public void setNavPathForTesting(List<Vector2D> path) {
        navPath.clear();
        if (path != null) {
            for (Vector2D p : path) {
                navPath.add(p != null ? p.cpy() : new Vector2D());
            }
        }
    }

    public void setNavLastPositionForTesting(Vector2D pos) {
        if (pos != null) {
            navLastPosition.set(pos);
        }
    }

    public void setNavLastTargetPosForTesting(Vector2D pos) {
        if (pos != null) {
            navLastTargetPos.set(pos);
        }
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

    @Override
    public void rebase(float deltaX, float deltaY) {
        applyOriginDelta(deltaX, deltaY);
    }

    /**
     * Shifts position, cached navigation waypoints, and tracking positions by (-deltaX, -deltaY)
     * during floating-origin rebasing so active bridge paths stay synchronized.
     */
    public void applyOriginDelta(float deltaX, float deltaY) {
        super.rebase(deltaX, deltaY);
        for (int i = 0; i < navPath.size(); i++) {
            Vector2D wp = navPath.get(i);
            if (wp != null) {
                wp.sub(deltaX, deltaY);
            }
        }
        navLastTargetPos.sub(deltaX, deltaY);
        navLastPosition.sub(deltaX, deltaY);
    }
}
