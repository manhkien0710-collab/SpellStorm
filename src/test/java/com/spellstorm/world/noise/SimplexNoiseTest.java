package com.spellstorm.world.noise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SimplexNoise Range, Smoothness & Determinism Tests")
public class SimplexNoiseTest {

    @Test
    @DisplayName("SimplexNoise is bitwise deterministic for identical seed and coordinates")
    public void testNoiseDeterminism() {
        SimplexNoise noise1 = new SimplexNoise(123456L);
        SimplexNoise noise2 = new SimplexNoise(123456L);

        for (double x = -10.0; x <= 10.0; x += 0.5) {
            for (double y = -10.0; y <= 10.0; y += 0.5) {
                assertEquals(noise1.eval(x, y), noise2.eval(x, y), "Noise must be bitwise deterministic across instances");
            }
        }
    }

    @Test
    @DisplayName("Output range is strictly bounded within [-1.0f, +1.0f] across 100,000 samples")
    public void testOutputRange() {
        SimplexNoise noise = new SimplexNoise(9999L);
        float min = Float.MAX_VALUE;
        float max = -Float.MAX_VALUE;

        for (int i = 0; i < 100_000; i++) {
            double x = (i % 1000) * 0.317 - 150.0;
            double y = (i / 1000) * 0.317 - 150.0;
            float val = noise.eval(x, y);

            assertTrue(val >= -1.0f && val <= 1.0f, "Noise must remain in [-1.0, 1.0], was " + val);
            min = Math.min(min, val);
            max = Math.max(max, val);
        }

        assertTrue(min < -0.6f, "Noise must cover negative spectrum (min: " + min + ")");
        assertTrue(max > 0.6f, "Noise must cover positive spectrum (max: " + max + ")");
    }

    @Test
    @DisplayName("SimplexNoise is C^1 smooth with bounded gradient across fine steps")
    public void testSmoothnessAndContinuity() {
        SimplexNoise noise = new SimplexNoise(5555L);
        double delta = 0.001;
        float maxStep = 0.0f;

        for (double x = -5.0; x <= 5.0; x += 0.1) {
            float v1 = noise.eval(x, 2.5);
            float v2 = noise.eval(x + delta, 2.5);
            float diff = Math.abs(v2 - v1);
            maxStep = Math.max(maxStep, diff);
            assertTrue(diff < 0.015f, "Difference across 0.001 delta must be smooth, was " + diff);
        }
    }

    @Test
    @DisplayName("FractalNoise produces normalized values in range [0.0, 1.0]")
    public void testFractalNoiseRange() {
        FractalNoise fractal = new FractalNoise(42L, 4, 0.5f, 2.0f, 0.01f);
        for (double x = -50.0; x <= 50.0; x += 5.0) {
            for (double y = -50.0; y <= 50.0; y += 5.0) {
                float norm = fractal.getNoiseNormalized(x, y);
                assertTrue(norm >= 0.0f && norm <= 1.0f, "Normalized noise must be in [0, 1], was " + norm);
                float raw = fractal.getNoise(x, y);
                assertTrue(raw >= -1.0f && raw <= 1.0f, "Raw noise must be in [-1, 1], was " + raw);
                float ridge = fractal.getRidgeNoise(x, y);
                assertTrue(ridge >= 0.0f && ridge <= 1.0f, "Ridge noise must be in [0, 1], was " + ridge);
            }
        }
    }
}
