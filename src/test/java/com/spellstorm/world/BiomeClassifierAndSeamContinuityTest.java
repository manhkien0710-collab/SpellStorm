package com.spellstorm.world;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("BiomeClassifier & Seam Continuity Tests")
public class BiomeClassifierAndSeamContinuityTest {

    @ParameterizedTest
    @CsvSource({
        "0, 0, 1, 0",      // (0,0) to East neighbor (1,0)
        "-1, 0, 0, 0",     // Negative boundary: (-1, 0) to (0, 0)
        "-5, -5, -4, -5",  // Negative quadrant border
        "100, 200, 101, 200"
    })
    @DisplayName("Continuous East-West chunk seam (lx=31 -> lx=0): Zero step discontinuity")
    public void testEastWestChunkSeamContinuity(int cx1, int cy1, int cx2, int cy2) {
        long worldSeed = 777123456L;
        BiomeClassifier classifier = new BiomeClassifier(worldSeed);

        // West chunk boundary column lx = 31
        // East chunk boundary column lx = 0
        float maxTempDelta = 0.0f;
        float maxMoistDelta = 0.0f;

        for (int ly = 0; ly < 32; ly++) {
            int gx1 = cx1 * 32 + 31;
            int gy1 = cy1 * 32 + ly;

            int gx2 = cx2 * 32 + 0;
            int gy2 = cy2 * 32 + ly;

            // Global delta is exactly 1 tile
            assertEquals(1, gx2 - gx1, "Seam crossing must represent a 1-tile global delta");
            assertEquals(gy1, gy2, "Y coordinates must match along East-West seam");

            float t1 = classifier.getTemperature(gx1, gy1);
            float t2 = classifier.getTemperature(gx2, gy2);
            float dt = Math.abs(t2 - t1);

            float m1 = classifier.getMoisture(gx1, gy1);
            float m2 = classifier.getMoisture(gx2, gy2);
            float dm = Math.abs(m2 - m1);

            maxTempDelta = Math.max(maxTempDelta, dt);
            maxMoistDelta = Math.max(maxMoistDelta, dm);

            // With scale = 0.005, a 1-tile step cannot exceed 0.035
            assertTrue(dt < 0.035f, "Temperature delta across chunk seam must be continuous (was " + dt + ")");
            assertTrue(dm < 0.035f, "Moisture delta across chunk seam must be continuous (was " + dm + ")");
        }
    }

    @Test
    @DisplayName("North-South chunk seam (ly=31 -> ly=0): Zero step discontinuity")
    public void testNorthSouthChunkSeamContinuity() {
        long worldSeed = 888234567L;
        BiomeClassifier classifier = new BiomeClassifier(worldSeed);

        int cx = 3;
        int cy1 = -2;
        int cy2 = -1;

        for (int lx = 0; lx < 32; lx++) {
            int gx1 = cx * 32 + lx;
            int gy1 = cy1 * 32 + 31;

            int gx2 = cx * 32 + lx;
            int gy2 = cy2 * 32 + 0;

            assertEquals(1, gy2 - gy1, "Seam crossing must represent a 1-tile global delta");

            float t1 = classifier.getTemperature(gx1, gy1);
            float t2 = classifier.getTemperature(gx2, gy2);
            assertTrue(Math.abs(t2 - t1) < 0.035f, "Temperature must transition smoothly across North-South seam");
        }
    }

    @Test
    @DisplayName("Gate 2 Connected Biome Diversity: At least 2 distinct connected biomes generated")
    public void testGate2BiomeDiversity() {
        long worldSeed = 10101010L;
        BiomeClassifier classifier = new BiomeClassifier(worldSeed);

        Set<BiomeType> observedBiomes = EnumSet.noneOf(BiomeType.class);

        // Sample a 256x256 tile region (8x8 chunks)
        for (int gy = -128; gy < 128; gy += 4) {
            for (int gx = -128; gx < 128; gx += 4) {
                observedBiomes.add(classifier.getBiome(gx, gy));
            }
        }

        assertTrue(observedBiomes.size() >= 2, "Must contain at least 2 distinct connected biomes for Gate 2 (observed: " + observedBiomes.size() + ")");
        assertTrue(observedBiomes.contains(BiomeType.PLAINS) || observedBiomes.contains(BiomeType.FOREST),
                "Must include core Plains or Forest biomes");
    }

    @Test
    @DisplayName("Chunk generation order independence: Sampling biomes in reverse produces bitwise identical results")
    public void testGenerationOrderIndependence() {
        long worldSeed = 54321L;
        BiomeClassifier classifier = new BiomeClassifier(worldSeed);

        // Forward sequence: (0, 0) then (1, 1)
        BiomeType b1 = classifier.getBiome(15, 15);
        BiomeType b2 = classifier.getBiome(45, 45);

        // Reverse sequence on fresh instance
        BiomeClassifier classifierRev = new BiomeClassifier(worldSeed);
        BiomeType b2Rev = classifierRev.getBiome(45, 45);
        BiomeType b1Rev = classifierRev.getBiome(15, 15);

        assertEquals(b1, b1Rev, "Biome classification at (15, 15) must be order-invariant");
        assertEquals(b2, b2Rev, "Biome classification at (45, 45) must be order-invariant");
    }
}
