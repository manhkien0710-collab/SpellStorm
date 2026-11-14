package com.spellstorm.world;

import com.spellstorm.assets.AssetRegistry.TextureId;

/**
 * Enumeration of terrain tile types in SpellStorm.
 *
 * Tile properties:
 * - GRASS: standard walkable terrain.
 * - DIRT: pathways, roads, and campsite ground.
 * - STONE: ancient ruins sanctuary floor.
 * - WATER: solid impassable river channel.
 * - SAND: transition banks along the river.
 * - BRIDGE_H / BRIDGE_V: walkable wooden river bridges.
 */
public enum TileType {
    GRASS(0, "terrain/grass.png", TextureId.TILE_GRASS, true, false, false),
    DIRT(1, "terrain/dirt.png", TextureId.TILE_DIRT, true, false, false),
    STONE(2, "terrain/stone.png", TextureId.TILE_STONE, true, false, false),
    WATER(3, "terrain/water.png", TextureId.TILE_WATER, false, true, false),
    SAND(4, "terrain/sand.png", TextureId.TILE_SAND, true, false, false),
    BRIDGE_H(5, "terrain/bridge_h.png", TextureId.TILE_BRIDGE_H, true, false, true),
    BRIDGE_V(6, "terrain/bridge_v.png", TextureId.TILE_BRIDGE_V, true, false, true);

    public static final TileType BRIDGE = BRIDGE_H;

    private final int id;
    private final String texturePath;
    private final TextureId textureId;
    private final boolean walkable;
    private final boolean water;
    private final boolean bridge;

    TileType(int id, String texturePath, TextureId textureId, boolean walkable, boolean water, boolean bridge) {
        this.id = id;
        this.texturePath = texturePath;
        this.textureId = textureId;
        this.walkable = walkable;
        this.water = water;
        this.bridge = bridge;
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

    public boolean isWalkable() {
        return walkable;
    }

    public boolean isSolid() {
        return !walkable;
    }

    public boolean isWater() {
        return water;
    }

    public boolean isBridge() {
        return bridge;
    }

    public static TileType fromId(int id) {
        return switch (id) {
            case 0 -> GRASS;
            case 1 -> DIRT;
            case 2 -> STONE;
            case 3 -> WATER;
            case 4 -> SAND;
            case 5 -> BRIDGE_H;
            case 6 -> BRIDGE_V;
            default -> WATER; // Outside or invalid IDs treat as impassable water
        };
    }
}
