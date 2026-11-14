package com.spellstorm.world;

/**
 * Cohesive biomes in SpellStorm defined over Whittaker temperature and moisture gradients.
 */
public enum BiomeType {
    PLAINS("Plains", TileType.GRASS, 0.08f, 0.35f, 0.50f),
    FOREST("Forest", TileType.GRASS, 0.35f, 0.65f, 0.50f),
    TAIGA("Taiga", TileType.GRASS, 0.30f, 0.55f, 0.25f),
    TUNDRA("Tundra", TileType.STONE, 0.12f, 0.25f, 0.20f),
    DESERT("Desert", TileType.SAND, 0.05f, 0.20f, 0.80f);

    private final String displayName;
    private final TileType primaryTile;
    private final float treeDensity;
    private final float targetMoisture;
    private final float targetTemperature;

    BiomeType(String displayName, TileType primaryTile, float treeDensity, float targetMoisture, float targetTemperature) {
        this.displayName = displayName;
        this.primaryTile = primaryTile;
        this.treeDensity = treeDensity;
        this.targetMoisture = targetMoisture;
        this.targetTemperature = targetTemperature;
    }

    public String getDisplayName() {
        return displayName;
    }

    public TileType getPrimaryTile() {
        return primaryTile;
    }

    public float getTreeDensity() {
        return treeDensity;
    }

    public float getTargetMoisture() {
        return targetMoisture;
    }

    public float getTargetTemperature() {
        return targetTemperature;
    }
}
