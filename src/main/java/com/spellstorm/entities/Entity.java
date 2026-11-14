package com.spellstorm.entities;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.FloatingOriginAware;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Abstract root base class for all physical entities living in the SpellStorm arena.
 *
 * Provides:
 * - Deterministic, sequentially generated Entity IDs.
 * - 2D world position and velocity vectors using {@link Vector2D}.
 * - Circular collision envelope radius.
 * - Active lifecycle management (active, destroy, revive).
 * - Semantic tag classification.
 * - Decoupled headless-safe simulation lifecycle.
 */
public abstract class Entity implements FloatingOriginAware {

    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

    private final int id;
    private String tag;
    protected final Vector2D position;
    protected final Vector2D velocity;
    protected float radius;
    protected boolean active;

    // =========================================================================
    // Static ID Management (Deterministic Test Isolation)
    // =========================================================================

    public static int getNextEntityId() {
        return ID_GENERATOR.getAndIncrement();
    }

    public static void resetIdCounter(int startId) {
        ID_GENERATOR.set(Math.max(1, startId));
    }

    public static void resetIdCounter() {
        resetIdCounter(1);
    }

    // =========================================================================
    // Constructors
    // =========================================================================

    protected Entity(Vector2D position, float radius, String tag) {
        this.id = getNextEntityId();
        this.position = position != null ? position.cpy() : new Vector2D();
        this.velocity = new Vector2D();
        this.radius = Math.max(0.0f, radius);
        this.tag = (tag != null && !tag.isBlank()) ? tag : "entity";
        this.active = true;
    }

    protected Entity(Vector2D position, float radius) {
        this(position, radius, "entity");
    }

    protected Entity(float x, float y, float radius) {
        this(new Vector2D(x, y), radius, "entity");
    }

    protected Entity() {
        this(new Vector2D(0.0f, 0.0f), 0.0f, "entity");
    }

    // =========================================================================
    // Abstract & Lifecycle Methods
    // =========================================================================

    /**
     * Executes one simulation update tick.
     *
     * @param dt delta time in seconds
     */
    public abstract void update(float dt);

    /**
     * Decoupled visual rendering. Safely no-ops in headless environments.
     */
    public void render(SpriteBatch batch, TextureManager textureManager) {
        // Default empty implementation for pure simulation entities
    }

    public void destroy() {
        this.active = false;
    }

    public void revive() {
        this.active = true;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public boolean isAlive() {
        return active;
    }

    // =========================================================================
    // Getters and Setters
    // =========================================================================

    public int getId() {
        return id;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = (tag != null && !tag.isBlank()) ? tag : "entity";
    }

    public Vector2D getPosition() {
        return position;
    }

    public void setPosition(float x, float y) {
        this.position.set(x, y);
    }

    public void setPosition(Vector2D pos) {
        if (pos != null) {
            this.position.set(pos.x, pos.y);
        }
    }

    public Vector2D getVelocity() {
        return velocity;
    }

    public void setVelocity(float vx, float vy) {
        this.velocity.set(vx, vy);
    }

    public void setVelocity(Vector2D vel) {
        if (vel != null) {
            this.velocity.set(vel.x, vel.y);
        }
    }

    public float getRadius() {
        return radius;
    }

    public void setRadius(float radius) {
        this.radius = Math.max(0.0f, radius);
    }

    public float getX() {
        return position.x;
    }

    public float getY() {
        return position.y;
    }

    // =========================================================================
    // Spatial Queries
    // =========================================================================

    public float dist(Entity other) {
        if (other == null) return Float.MAX_VALUE;
        return this.position.dist(other.position);
    }

    public float distSq(Entity other) {
        if (other == null) return Float.MAX_VALUE;
        return this.position.dist2(other.position);
    }

    public boolean collidesWith(Entity other) {
        if (other == null || !this.active || !other.active) return false;
        float totalRadius = this.radius + other.radius;
        return this.position.dist2(other.position) <= (totalRadius * totalRadius);
    }

    /**
     * Shifts the local world coordinates of this entity by (-deltaX, -deltaY)
     * during a centralized atomic floating-origin shift.
     */
    public void rebase(float deltaX, float deltaY) {
        this.position.sub(deltaX, deltaY);
    }

    @Override
    public void onOriginShift(long deltaX, long deltaY) {
        rebase((float) deltaX, (float) deltaY);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Entity other)) return false;
        return id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + "{" +
                "id=" + id +
                ", tag='" + tag + '\'' +
                ", pos=" + position +
                ", vel=" + velocity +
                ", r=" + radius +
                ", active=" + active +
                '}';
    }
}
