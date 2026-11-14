package com.spellstorm.world;

import com.spellstorm.world.noise.FractalNoise;

/**
 * Deterministic Whittaker biome classifier sampling continuous global temperature and moisture.
 * Guarantees C^1 continuity across chunk seams with zero seam lines.
 */
public class BiomeClassifier {

    public static final float DEFAULT_BIOME_SCALE = 0.005f; // ~200 tiles / 6.25 chunks per cycle

    private final FractalNoise temperatureNoise;
    private final FractalNoise moistureNoise;

    public BiomeClassifier(long worldSeed) {
        this.temperatureNoise = new FractalNoise(worldSeed ^ 0x54656D704E6F6973L, 3, 0.5f, 2.0f, DEFAULT_BIOME_SCALE);
        this.moistureNoise = new FractalNoise(worldSeed ^ 0x4D6F6973744E6F69L, 3, 0.5f, 2.0f, DEFAULT_BIOME_SCALE);
    }

    public BiomeClassifier(FractalNoise temperatureNoise, FractalNoise moistureNoise) {
        this.temperatureNoise = temperatureNoise;
        this.moistureNoise = moistureNoise;
    }

    public float getTemperature(int gx, int gy) {
        return temperatureNoise.getNoiseNormalized(gx, gy);
    }

    public float getMoisture(int gx, int gy) {
        return moistureNoise.getNoiseNormalized(gx, gy);
    }

    public BiomeType classify(float temperature, float moisture) {
        if (temperature < 0.35f) {
            return moisture < 0.45f ? BiomeType.TUNDRA : BiomeType.TAIGA;
        } else if (temperature < 0.65f) {
            return moisture < 0.45f ? BiomeType.PLAINS : BiomeType.FOREST;
        } else {
            return moisture < 0.40f ? BiomeType.DESERT : BiomeType.FOREST;
        }
    }

    public BiomeType getBiome(int gx, int gy) {
        return classify(getTemperature(gx, gy), getMoisture(gx, gy));
    }
}
