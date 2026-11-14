package com.spellstorm.world;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;

import java.util.Objects;

/**
 * Immutable representation of a placed environmental decoration/obstacle.
 *
 * Models visual bounds for frustum culling and collision circles for physics/navigation.
 * The anchor position represents the bottom-center of the object on the ground plane.
 */
public final class Obstacle {

    private final DecoType type;
    private final Vector2D position;
    private final Vector2D collisionCenter;
    private final float collisionRadius;
    private final Vector2D collisionOffset;
    private final Rectangle visualBounds;
    private final boolean isSolid;

    public Obstacle(DecoType type, Vector2D position, boolean solid) {
        this(type, position != null ? position.x : 0.0f, position != null ? position.y : 0.0f, solid);
    }

    public Obstacle(DecoType type, float posX, float posY, boolean solid) {
        this.type = Objects.requireNonNull(type, "DecoType cannot be null");
        this.position = new Vector2D(posX, posY);
        this.isSolid = solid;

        this.collisionOffset = type.getCollisionOffset();
        this.collisionRadius = solid ? type.getDefaultCollisionRadius() : 0.0f;
        this.collisionCenter = new Vector2D(posX + collisionOffset.x, posY + collisionOffset.y);

        float w = type.getWidth();
        float h = type.getHeight();
        this.visualBounds = new Rectangle(posX - w * 0.5f, posY - h, w, h);
    }

    public Obstacle(DecoType type, Vector2D position) {
        this(type, position, type != null && type.isDefaultSolid());
    }

    public Obstacle(DecoType type, float posX, float posY) {
        this(type, posX, posY, type != null && type.isDefaultSolid());
    }

    public DecoType getType() {
        return type;
    }

    public Vector2D getPosition() {
        return position;
    }

    public Vector2D getCollisionCenter() {
        return collisionCenter;
    }

    public float getCollisionRadius() {
        return collisionRadius;
    }

    public Vector2D getCollisionOffset() {
        return collisionOffset;
    }

    public Rectangle getVisualBounds() {
        return visualBounds;
    }

    public boolean isSolid() {
        return isSolid;
    }

    /**
     * Tests circle-circle collision against an entity circle using CollisionUtils.
     */
    public boolean collidesWith(Vector2D entityCenter, float entityRadius) {
        if (!isSolid || collisionRadius <= 0.0f || entityCenter == null || entityRadius < 0.0f) {
            return false;
        }
        return CollisionUtils.checkCircleVsCircle(
                collisionCenter.x, collisionCenter.y, collisionRadius,
                entityCenter.x, entityCenter.y, entityRadius
        );
    }

    public boolean collidesWith(float entityX, float entityY, float entityRadius) {
        if (!isSolid || collisionRadius <= 0.0f || entityRadius < 0.0f) {
            return false;
        }
        return CollisionUtils.checkCircleVsCircle(
                collisionCenter.x, collisionCenter.y, collisionRadius,
                entityX, entityY, entityRadius
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Obstacle obstacle = (Obstacle) o;
        return Float.compare(obstacle.collisionRadius, collisionRadius) == 0 &&
                isSolid == obstacle.isSolid &&
                type == obstacle.type &&
                Objects.equals(position, obstacle.position);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, position, collisionRadius, isSolid);
    }

    @Override
    public String toString() {
        return "Obstacle{" +
                "type=" + type +
                ", pos=" + position +
                ", colRadius=" + collisionRadius +
                ", solid=" + isSolid +
                '}';
    }
}
