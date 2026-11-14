package com.spellstorm.world;

import com.spellstorm.assets.AssetRegistry.TextureId;
import com.spellstorm.math.Vector2D;

/**
 * Environmental decoration types in SpellStorm.
 * Defines sprite dimensions, anchor origin, collision radius, collision offset, and default solidity.
 *
 * Anchor convention:
 * All decorations are anchored at their bottom-center base (position.x = center of sprite, position.y = ground contact line).
 * Visual bounds extend upward from the ground anchor: [posX - width/2, posY - height, width, height].
 */
public enum DecoType {
    TREE_OAK(0, "environment/tree_oak.png", TextureId.DECO_TREE_OAK, 32.0f, 48.0f, 12.0f, 0.0f, -8.0f, true),
    TREE_PINE(1, "environment/tree_pine.png", TextureId.DECO_TREE_PINE, 24.0f, 48.0f, 10.0f, 0.0f, -8.0f, true),
    ROCK(2, "environment/rock.png", TextureId.DECO_ROCK, 24.0f, 24.0f, 9.0f, 0.0f, -4.0f, true),
    ROCK_LARGE(3, "environment/rock_large.png", TextureId.DECO_ROCK_LARGE, 32.0f, 32.0f, 14.0f, 0.0f, -6.0f, true),
    BUSH(4, "environment/bush.png", TextureId.DECO_BUSH, 20.0f, 20.0f, 0.0f, 0.0f, 0.0f, false),
    LOG(5, "environment/log.png", TextureId.DECO_LOG, 28.0f, 18.0f, 0.0f, 0.0f, 0.0f, false),
    RUINS(6, "environment/ruins.png", TextureId.DECO_RUINS, 32.0f, 48.0f, 14.0f, 0.0f, -10.0f, true),
    TENT(7, "environment/camp_tent.png", TextureId.DECO_TENT, 40.0f, 36.0f, 16.0f, 0.0f, -8.0f, true),
    CAMPFIRE(8, "environment/campfire.png", TextureId.DECO_CAMPFIRE, 24.0f, 24.0f, 10.0f, 0.0f, -4.0f, true);

    private final int id;
    private final String texturePath;
    private final TextureId textureId;
    private final float width;
    private final float height;
    private final float defaultCollisionRadius;
    private final float collisionOffsetX;
    private final float collisionOffsetY;
    private final boolean defaultSolid;

    DecoType(int id, String texturePath, TextureId textureId,
             float width, float height, float defaultCollisionRadius,
             float collisionOffsetX, float collisionOffsetY, boolean defaultSolid) {
        this.id = id;
        this.texturePath = texturePath;
        this.textureId = textureId;
        this.width = width;
        this.height = height;
        this.defaultCollisionRadius = defaultCollisionRadius;
        this.collisionOffsetX = collisionOffsetX;
        this.collisionOffsetY = collisionOffsetY;
        this.defaultSolid = defaultSolid;
    }

    public int getId() {
        return id;
    }

    public String getTexturePath() {
        return texturePath;
    }

    public TextureId getTextureId() {
        return textureId;
    }

    public float getWidth() {
        return width;
    }

    public float getHeight() {
        return height;
    }

    public float getDefaultCollisionRadius() {
        return defaultCollisionRadius;
    }

    public float getCollisionOffsetX() {
        return collisionOffsetX;
    }

    public float getCollisionOffsetY() {
        return collisionOffsetY;
    }

    public Vector2D getCollisionOffset() {
        return new Vector2D(collisionOffsetX, collisionOffsetY);
    }

    public boolean isDefaultSolid() {
        return defaultSolid;
    }

    public static DecoType fromId(int id) {
        return switch (id) {
            case 0 -> TREE_OAK;
            case 1 -> TREE_PINE;
            case 2 -> ROCK;
            case 3 -> ROCK_LARGE;
            case 4 -> BUSH;
            case 5 -> LOG;
            case 6 -> RUINS;
            case 7 -> TENT;
            case 8 -> CAMPFIRE;
            default -> BUSH;
        };
    }
}
