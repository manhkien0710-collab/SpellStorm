package com.spellstorm.entities;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Keys;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.WorldGrid;

/**
 * Controllable wizard player avatar in SpellStorm.
 *
 * Implements:
 * - 8-way normalized movement calculation (WASD / Arrow keys).
 * - Continuous axis-aligned sliding collision resolution with {@link TileMap}.
 * - World-space mouse aiming, continuous rotation, and 4-quadrant facing direction.
 * - Shooting mode management (Manual vs Auto) with debounce cooldown on key 'V'.
 * - Spellcasting cooldown management (default 0.28s).
 * - Upgrade multipliers: damage, projectile speed, projectile lifetime, pickup radius, bonus pierce.
 * - Dual-mode API: headless input injection for JUnit 5, hardware keyboard polling for desktop.
 */
public class Player extends Character {

    // Primary dispatch balance presets
    public static final float BASE_HEALTH = 100.0f;
    public static final float BASE_SPEED = 180.0f;
    public static final float DEFAULT_HEALTH = 250.0f;
    public static final float DEFAULT_ARMOR = 30.0f;
    public static final float DEFAULT_SPEED = 230.0f;
    public static final float DEFAULT_PICKUP_RADIUS = 45.0f;
    public static final float DEFAULT_CAST_COOLDOWN = 0.28f;
    public static final float DEFAULT_COLLISION_RADIUS = 20.0f;
    public static final float DEFAULT_TOGGLE_DEBOUNCE = 0.25f;

    public enum FacingDirection {
        DOWN,
        UP,
        LEFT,
        RIGHT
    }

    // Shooting Mode & Aiming State
    private ShootingMode shootingMode = ShootingMode.MANUAL;
    private float toggleDebounceTimer = 0.0f;
    private boolean toggleKeyHeld = false;

    private float aimAngle = 0.0f; // radians in [-PI, PI]
    private final Vector2D aimDirection = new Vector2D(1.0f, 0.0f);
    private float animTimer = 0.0f;
    private float idleTimer = 0.0f;
    private boolean singleFrameWalk = true;
    private float baseCastCooldown = DEFAULT_CAST_COOLDOWN;
    private float castTimer = 0.0f;

    // Progression & Perk Multipliers
    private float damageMultiplier = 1.0f;
    private float projectileSpeedMultiplier = 1.0f;
    private float projectileLifetimeMultiplier = 1.0f;
    private float pickupRadius = DEFAULT_PICKUP_RADIUS;
    private int bonusPierce = 0;

    // =========================================================================
    // Constructors
    // =========================================================================

    public Player(Vector2D position, float health, float armor, float moveSpeed, float pickupRadius) {
        super(position, DEFAULT_COLLISION_RADIUS, health, armor, moveSpeed, "player");
        this.pickupRadius = Math.max(0.0f, pickupRadius);
    }

    public Player(Vector2D position) {
        this(position, DEFAULT_HEALTH, DEFAULT_ARMOR, DEFAULT_SPEED, DEFAULT_PICKUP_RADIUS);
    }

    public Player(float x, float y) {
        this(new Vector2D(x, y));
    }

    public Player() {
        this(new Vector2D(TileMap.WORLD_CENTER.x, TileMap.WORLD_CENTER.y));
    }

    // =========================================================================
    // 8-Way Movement Calculation (Headless & Hardware)
    // =========================================================================

    /**
     * Computes the 8-way normalized movement direction vector from boolean input states.
     * Prevents diagonal speed inflation (magnitude is strictly 1.0 or 0.0).
     */
    public static Vector2D computeMovementVector(boolean up, boolean down, boolean left, boolean right) {
        float dx = 0.0f;
        float dy = 0.0f;

        if (up) dy -= 1.0f;
        if (down) dy += 1.0f;
        if (left) dx -= 1.0f;
        if (right) dx += 1.0f;

        Vector2D dir = new Vector2D(dx, dy);
        if (!dir.isZero()) {
            dir.nor();
        }
        return dir;
    }

    /**
     * Headless input method: updates movement from explicitly injected boolean states.
     */
    public void handleMovementInput(boolean up, boolean down, boolean left, boolean right,
                                     float dt, TileMap tileMap) {
        if (!isAlive()) {
            velocity.setZero();
            return;
        }

        Vector2D moveDir = computeMovementVector(up, down, left, right);
        moveAndResolve(moveDir, dt, tileMap);
    }

    /**
     * Moves the player in the given direction and resolves sliding collisions against the TileMap.
     */
    public void moveAndResolve(Vector2D moveDir, float dt, TileMap tileMap) {
        if (!isAlive() || dt <= 0.0f) {
            velocity.setZero();
            return;
        }

        Vector2D desiredVel = (moveDir != null && !moveDir.isZero())
                ? moveDir.cpy().nor().scl(getCurrentMoveSpeed())
                : new Vector2D();

        Vector2D combinedVel = desiredVel.cpy().add(knockback);

        Vector2D oldPos = position.cpy();
        Vector2D candidatePos = oldPos.cpy().mulAdd(combinedVel, dt);

        if (tileMap != null) {
            Vector2D resolvedPos = tileMap.resolveCollision(oldPos, candidatePos, radius);
            position.set(resolvedPos);
            velocity.set((resolvedPos.x - oldPos.x) / dt, (resolvedPos.y - oldPos.y) / dt);
        } else {
            position.set(candidatePos);
            velocity.set(combinedVel);
        }

        updateKnockback(dt);

        if (velocity.len2() > 1.0f) {
            animTimer += dt * 8.0f;
        }
    }

    /**
     * Interactive input handler polling LibGDX keyboard state.
     * Safely no-ops if Gdx.input is null in headless environments.
     */
    public void handleInput(float dt, TileMap tileMap) {
        if (Gdx.input == null || !isAlive()) {
            return;
        }

        boolean up = Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP);
        boolean down = Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN);
        boolean left = Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT);
        boolean right = Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT);

        handleMovementInput(up, down, left, right, dt, tileMap);
    }

    /**
     * Headless input method: updates movement from explicitly injected boolean states with WorldGrid.
     */
    public void handleMovementInputGrid(boolean up, boolean down, boolean left, boolean right,
                                        float dt, WorldGrid worldGrid) {
        if (!isAlive()) {
            velocity.setZero();
            return;
        }

        Vector2D moveDir = computeMovementVector(up, down, left, right);
        moveAndResolve(moveDir, dt, worldGrid);
    }

    /**
     * Moves the player in the given direction and resolves sliding collisions against the WorldGrid.
     */
    public void moveAndResolve(Vector2D moveDir, float dt, WorldGrid worldGrid) {
        if (!isAlive() || dt <= 0.0f) {
            velocity.setZero();
            return;
        }

        Vector2D desiredVel = (moveDir != null && !moveDir.isZero())
                ? moveDir.cpy().nor().scl(getCurrentMoveSpeed())
                : new Vector2D();

        Vector2D combinedVel = desiredVel.cpy().add(knockback);

        Vector2D oldPos = position.cpy();
        Vector2D candidatePos = oldPos.cpy().mulAdd(combinedVel, dt);

        if (worldGrid != null) {
            Vector2D resolvedPos = worldGrid.resolveCollision(oldPos, candidatePos, radius);
            position.set(resolvedPos);
            velocity.set((resolvedPos.x - oldPos.x) / dt, (resolvedPos.y - oldPos.y) / dt);
        } else {
            position.set(candidatePos);
            velocity.set(combinedVel);
        }

        updateKnockback(dt);

        if (velocity.len2() > 1.0f) {
            animTimer += dt * 8.0f;
        }
    }

    /**
     * Interactive input handler polling LibGDX keyboard state with WorldGrid.
     * Safely no-ops if Gdx.input is null in headless environments.
     */
    public void handleInput(float dt, WorldGrid worldGrid) {
        if (Gdx.input == null || !isAlive()) {
            return;
        }

        boolean up = Gdx.input.isKeyPressed(Keys.W) || Gdx.input.isKeyPressed(Keys.UP);
        boolean down = Gdx.input.isKeyPressed(Keys.S) || Gdx.input.isKeyPressed(Keys.DOWN);
        boolean left = Gdx.input.isKeyPressed(Keys.A) || Gdx.input.isKeyPressed(Keys.LEFT);
        boolean right = Gdx.input.isKeyPressed(Keys.D) || Gdx.input.isKeyPressed(Keys.RIGHT);

        handleMovementInputGrid(up, down, left, right, dt, worldGrid);
    }

    // =========================================================================
    // Shooting Mode Management & Debounce
    // =========================================================================

    public ShootingMode getShootingMode() {
        return shootingMode;
    }

    public void setShootingMode(ShootingMode mode) {
        if (mode != null) {
            this.shootingMode = mode;
        }
    }

    public void toggleShootingMode() {
        this.shootingMode = this.shootingMode.toggle();
    }

    public boolean tryToggleShootingMode() {
        if (toggleKeyHeld || toggleDebounceTimer > 0.0f) {
            return false;
        }
        toggleKeyHeld = true;
        toggleDebounceTimer = DEFAULT_TOGGLE_DEBOUNCE;
        toggleShootingMode();
        return true;
    }

    public void onToggleKeyReleased() {
        this.toggleKeyHeld = false;
    }

    public boolean isToggleKeyHeld() {
        return toggleKeyHeld;
    }

    public float getToggleDebounceTimer() {
        return toggleDebounceTimer;
    }

    public void updateCooldowns(float dt) {
        if (!isAlive()) return;
        if (castTimer > 0.0f) {
            castTimer = Math.max(0.0f, castTimer - dt);
        }
        if (toggleDebounceTimer > 0.0f) {
            toggleDebounceTimer = Math.max(0.0f, toggleDebounceTimer - dt);
        }
    }

    // =========================================================================
    // Aiming & Facing Rotation
    // =========================================================================

    public void setAimTarget(Vector2D targetPos) {
        if (targetPos != null && isAlive()) {
            setAimTarget(targetPos.x, targetPos.y);
        }
    }

    public void setAimTarget(float targetX, float targetY) {
        if (!isAlive()) {
            return;
        }

        float dx = targetX - position.x;
        float dy = targetY - position.y;
        float dist2 = dx * dx + dy * dy;

        if (dist2 > 1e-4f) {
            this.aimAngle = (float) Math.atan2(dy, dx);
            float invLen = 1.0f / (float) Math.sqrt(dist2);
            this.aimDirection.set(dx * invLen, dy * invLen);
        }
    }

    public void setAimDirection(Vector2D dir) {
        if (!isAlive() || dir == null) return;
        if (dir.len2() > 1e-4f) {
            this.aimDirection.set(dir).nor();
            this.aimAngle = this.aimDirection.angleRad();
        }
    }

    public float getAimAngle() {
        return aimAngle;
    }

    public float getAimAngleDeg() {
        return MathUtils.radToDeg(aimAngle);
    }

    public Vector2D getAimDirection() {
        return aimDirection.cpy();
    }

    public FacingDirection getFacingDirection() {
        float pi = MathUtils.PI;
        if (aimAngle >= pi * 0.25f && aimAngle < pi * 0.75f) {
            return FacingDirection.DOWN; // South (+Y in y-down)
        } else if (aimAngle <= -pi * 0.25f && aimAngle > -pi * 0.75f) {
            return FacingDirection.UP; // North (-Y in y-down)
        } else if (Math.abs(aimAngle) >= pi * 0.75f) {
            return FacingDirection.LEFT; // West (-X)
        } else {
            return FacingDirection.RIGHT; // East (+X)
        }
    }

    // =========================================================================
    // Spellcasting Cooldown Management
    // =========================================================================

    public boolean canCast() {
        return isAlive() && castTimer <= 0.0f;
    }

    public void triggerCast(float cooldownMultiplier) {
        if (!isAlive()) return;
        this.castTimer = baseCastCooldown * Math.max(0.1f, cooldownMultiplier);
    }

    public void triggerCast() {
        triggerCast(1.0f);
    }

    public float getCastTimer() {
        return castTimer;
    }

    public float getBaseCastCooldown() {
        return baseCastCooldown;
    }

    public void setBaseCastCooldown(float cd) {
        this.baseCastCooldown = Math.max(0.05f, cd);
    }

    // =========================================================================
    // Progression & Upgrade Modifiers
    // =========================================================================

    public void increaseMaxHealth(float amount) {
        setMaxHealth(maxHealth + amount);
        heal(amount);
    }

    public void multiplyMoveSpeed(float factor) {
        setBaseMoveSpeed(baseMoveSpeed * factor);
    }

    public float getDamageMultiplier() { return damageMultiplier; }
    public void setDamageMultiplier(float m) { this.damageMultiplier = m; }
    public void multiplyDamage(float factor) { this.damageMultiplier *= factor; }

    public void multiplyCastCooldown(float factor) {
        this.baseCastCooldown = Math.max(0.06f, this.baseCastCooldown * factor);
    }

    public float getProjectileSpeedMultiplier() { return projectileSpeedMultiplier; }
    public void setProjectileSpeedMultiplier(float m) { this.projectileSpeedMultiplier = m; }
    public void multiplyProjectileSpeed(float factor) { this.projectileSpeedMultiplier *= factor; }

    public float getProjectileLifetimeMultiplier() { return projectileLifetimeMultiplier; }
    public void setProjectileLifetimeMultiplier(float m) { this.projectileLifetimeMultiplier = m; }
    public void multiplyProjectileLifetime(float factor) { this.projectileLifetimeMultiplier *= factor; }

    public float getPickupRadius() { return pickupRadius; }
    public void setPickupRadius(float r) { this.pickupRadius = Math.max(0.0f, r); }
    public void increasePickupRadius(float amount) { this.pickupRadius += amount; }

    public int getBonusPierce() { return bonusPierce; }
    public void setBonusPierce(int pierce) { this.bonusPierce = MathUtils.clamp(pierce, 0, 2); }
    public void addBonusPierce(int amount) { setBonusPierce(this.bonusPierce + amount); }

    // =========================================================================
    // Simulation Lifecycle
    // =========================================================================

    @Override
    public void update(float dt) {
        if (!isAlive()) {
            velocity.setZero();
            return;
        }

        super.update(dt);
        updateCooldowns(dt);
        if (dt > 0.0f) {
            idleTimer += dt;
        }
    }

    public void resetPlayer(Vector2D position) {
        resetCharacter(position, DEFAULT_HEALTH, DEFAULT_ARMOR, DEFAULT_SPEED);
        this.shootingMode = ShootingMode.MANUAL;
        this.toggleDebounceTimer = 0.0f;
        this.toggleKeyHeld = false;
        this.aimAngle = 0.0f;
        this.aimDirection.set(1.0f, 0.0f);
        this.animTimer = 0.0f;
        this.idleTimer = 0.0f;
        this.baseCastCooldown = DEFAULT_CAST_COOLDOWN;
        this.castTimer = 0.0f;
        this.damageMultiplier = 1.0f;
        this.projectileSpeedMultiplier = 1.0f;
        this.projectileLifetimeMultiplier = 1.0f;
        this.pickupRadius = DEFAULT_PICKUP_RADIUS;
        this.bonusPierce = 0;
    }

    public float getAnimTimer() {
        return animTimer;
    }

    public float getIdleTimer() {
        return idleTimer;
    }

    public boolean isSingleFrameWalk() {
        return singleFrameWalk;
    }

    public void setSingleFrameWalk(boolean singleFrameWalk) {
        this.singleFrameWalk = singleFrameWalk;
    }

    /**
     * Returns the active walk animation frame index.
     * Uses a single stable walk frame (0) by default to avoid frame-1 flicker/pose glitches.
     */
    public int getAnimationFrame() {
        if (singleFrameWalk || velocity.len2() <= 1.0f) {
            return 0;
        }
        return ((int) animTimer) % 2;
    }
}
