package com.spellstorm.world.noise;

/**
 * Fractal Brownian Motion (FBM) generator wrapping SimplexNoise.
 * Computes multi-octave continuous fractal noise with configurable
 * octaves, persistence, lacunarity, and base scale.
 */
public class FractalNoise {

    private final SimplexNoise noise;
    private final int octaves;
    private final float persistence;
    private final float lacunarity;
    private final float scale;
    private final float maxAmplitude;

    public FractalNoise(long seed, int octaves, float persistence, float lacunarity, float scale) {
        if (octaves < 1) {
            throw new IllegalArgumentException("Octaves must be >= 1");
        }
        this.noise = new SimplexNoise(seed);
        this.octaves = octaves;
        this.persistence = persistence;
        this.lacunarity = lacunarity;
        this.scale = scale;

        float amp = 1.0f;
        float totalAmp = 0.0f;
        for (int i = 0; i < octaves; i++) {
            totalAmp += amp;
            amp *= persistence;
        }
        this.maxAmplitude = totalAmp > 0.0f ? totalAmp : 1.0f;
    }

    /**
     * Evaluates normalized FBM noise at coordinates (x, y) in [-1.0, 1.0].
     */
    public float getNoise(double x, double y) {
        double currentScale = this.scale;
        float currentAmp = 1.0f;
        float sum = 0.0f;

        for (int i = 0; i < octaves; i++) {
            sum += currentAmp * noise.eval(x * currentScale, y * currentScale);
            currentScale *= lacunarity;
            currentAmp *= persistence;
        }

        return sum / maxAmplitude;
    }

    /**
     * Evaluates normalized FBM noise mapped to [0.0, 1.0].
     */
    public float getNoiseNormalized(double x, double y) {
        return (getNoise(x, y) + 1.0f) * 0.5f;
    }

    /**
     * Ridge noise helper: |F(x, y)| for river channels and ridges in [0.0, 1.0].
     */
    public float getRidgeNoise(double x, double y) {
        return Math.abs(getNoise(x, y));
    }
}
