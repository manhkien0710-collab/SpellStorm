package com.spellstorm.p0;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P0 Defect D7: Asset Repair & Bounding Box Verification Test")
public class AssetBBoxTest {

    public static class BBox {
        public final int minX;
        public final int minY;
        public final int maxX;
        public final int maxY;

        public BBox(int minX, int minY, int maxX, int maxY) {
            this.minX = minX;
            this.minY = minY;
            this.maxX = maxX;
            this.maxY = maxY;
        }

        public int getWidth() {
            return maxX < 0 ? 0 : (maxX - minX + 1);
        }

        public int getHeight() {
            return maxY < 0 ? 0 : (maxY - minY + 1);
        }

        public double getCenterX() {
            return (minX + maxX) / 2.0;
        }

        public double getCenterY() {
            return (minY + maxY) / 2.0;
        }

        @Override
        public String toString() {
            return String.format("[minX=%d, minY=%d, maxX=%d, maxY=%d, w=%d, h=%d, cx=%.2f, cy=%.2f]",
                    minX, minY, maxX, maxY, getWidth(), getHeight(), getCenterX(), getCenterY());
        }
    }

    private static File resolveAssetFile(String relativePath) {
        String[] candidates = {
                relativePath,
                "assets/" + relativePath,
                "../assets/" + relativePath
        };
        for (String c : candidates) {
            File f = new File(c);
            if (f.exists()) {
                return f;
            }
        }
        return new File("assets/" + relativePath);
    }

    public static BBox computeNonZeroAlphaBBox(BufferedImage img) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = -1;
        int maxY = -1;

        int width = img.getWidth();
        int height = img.getHeight();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = img.getRGB(x, y);
                int alpha = (pixel >> 24) & 0xFF;
                if (alpha > 0) {
                    if (x < minX) minX = x;
                    if (x > maxX) maxX = x;
                    if (y < minY) minY = y;
                    if (y > maxY) maxY = y;
                }
            }
        }

        if (maxX < 0) {
            return new BBox(0, 0, -1, -1);
        }
        return new BBox(minX, minY, maxX, maxY);
    }

    @ParameterizedTest(name = "Gem bounding box check: {0}")
    @ValueSource(strings = {
            "textures/pickups/gem_cyan.png",
            "textures/pickups/gem_gold.png",
            "textures/pickups/gem_emerald.png"
    })
    @DisplayName("Verify repaired EXP gem textures are coherent, centered, and >= 8x8")
    void testGemBoundingBox(String relativePath) throws IOException {
        File file = resolveAssetFile(relativePath);
        assertTrue(file.exists(), "Asset file must exist: " + relativePath);

        BufferedImage img = ImageIO.read(file);
        assertNotNull(img, "ImageIO must successfully decode: " + relativePath);
        assertTrue(img.getColorModel().hasAlpha(), "Gem must have alpha channel");
        assertEquals(16, img.getWidth(), "Gem canvas width must be exactly 16");
        assertEquals(16, img.getHeight(), "Gem canvas height must be exactly 16");

        BBox bbox = computeNonZeroAlphaBBox(img);

        // Canvas center for 16x16 is (8.0, 8.0)
        double canvasCenterX = 8.0;
        double canvasCenterY = 8.0;

        assertTrue(bbox.getWidth() >= 8,
                "Gem " + relativePath + " bbox width must be >= 8, was: " + bbox.getWidth() + " " + bbox);
        assertTrue(bbox.getHeight() >= 8,
                "Gem " + relativePath + " bbox height must be >= 8, was: " + bbox.getHeight() + " " + bbox);
        assertTrue(Math.abs(bbox.getCenterX() - canvasCenterX) <= 2.0,
                "Gem " + relativePath + " center X must be within 2.0 px of 8.0, was: " + bbox.getCenterX() + " " + bbox);
        assertTrue(Math.abs(bbox.getCenterY() - canvasCenterY) <= 2.0,
                "Gem " + relativePath + " center Y must be within 2.0 px of 8.0, was: " + bbox.getCenterY() + " " + bbox);

        // Verify gem is coherent in the center and not split across left/right canvas borders
        for (int y = 0; y < 16; y++) {
            int leftAlpha = (img.getRGB(0, y) >> 24) & 0xFF;
            int rightAlpha = (img.getRGB(15, y) >> 24) & 0xFF;
            assertEquals(0, leftAlpha, "Gem col 0 must be transparent (not split across left border) at row " + y);
            assertEquals(0, rightAlpha, "Gem col 15 must be transparent (not split across right border) at row " + y);
        }

        // Verify non-trivial opacity (not a faint alpha sliver)
        int maxAlpha = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int a = (img.getRGB(x, y) >> 24) & 0xFF;
                if (a > maxAlpha) maxAlpha = a;
            }
        }
        assertTrue(maxAlpha >= 200, "Gem must contain solid/opaque pixels (max alpha >= 200), was: " + maxAlpha);
    }

    @ParameterizedTest(name = "Spell projectile bounding box check: {0}")
    @ValueSource(strings = {
            "textures/projectiles/spell_lightning.png",
            "textures/projectiles/spell_wind.png",
            "textures/projectiles/spell_fire.png"
    })
    @DisplayName("Verify repaired spell projectile textures are centered and >= 12x12")
    void testSpellProjectileBoundingBox(String relativePath) throws IOException {
        File file = resolveAssetFile(relativePath);
        assertTrue(file.exists(), "Asset file must exist: " + relativePath);

        BufferedImage img = ImageIO.read(file);
        assertNotNull(img, "ImageIO must successfully decode: " + relativePath);
        assertTrue(img.getColorModel().hasAlpha(), "Projectile must have alpha channel");
        assertEquals(24, img.getWidth(), "Spell projectile canvas width must be exactly 24");
        assertEquals(24, img.getHeight(), "Spell projectile canvas height must be exactly 24");

        BBox bbox = computeNonZeroAlphaBBox(img);

        // Canvas center for 24x24 is (12.0, 12.0)
        double canvasCenterX = 12.0;
        double canvasCenterY = 12.0;

        assertTrue(bbox.getWidth() >= 12,
                "Spell projectile " + relativePath + " bbox width must be >= 12, was: " + bbox.getWidth() + " " + bbox);
        assertTrue(bbox.getHeight() >= 12,
                "Spell projectile " + relativePath + " bbox height must be >= 12, was: " + bbox.getHeight() + " " + bbox);
        assertTrue(Math.abs(bbox.getCenterX() - canvasCenterX) <= 2.5,
                "Spell projectile " + relativePath + " center X must be within 2.5 px of 12.0, was: " + bbox.getCenterX() + " " + bbox);
        assertTrue(Math.abs(bbox.getCenterY() - canvasCenterY) <= 2.5,
                "Spell projectile " + relativePath + " center Y must be within 2.5 px of 12.0, was: " + bbox.getCenterY() + " " + bbox);

        // Verify border columns are transparent to prevent edge clipping artifacts
        for (int y = 0; y < 24; y++) {
            int leftAlpha = (img.getRGB(0, y) >> 24) & 0xFF;
            int rightAlpha = (img.getRGB(23, y) >> 24) & 0xFF;
            assertEquals(0, leftAlpha, "Spell projectile col 0 must be transparent at row " + y);
            assertEquals(0, rightAlpha, "Spell projectile col 23 must be transparent at row " + y);
        }
    }

    @Test
    @DisplayName("Verify gem emerald has genuine green palette distinct from cyan and gold")
    void testGemEmeraldColorPalette() throws IOException {
        File emeraldFile = resolveAssetFile("textures/pickups/gem_emerald.png");
        BufferedImage emerald = ImageIO.read(emeraldFile);

        long totalR = 0, totalG = 0, totalB = 0;
        int count = 0;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int pixel = emerald.getRGB(x, y);
                int a = (pixel >> 24) & 0xFF;
                if (a > 64) {
                    totalR += (pixel >> 16) & 0xFF;
                    totalG += (pixel >> 8) & 0xFF;
                    totalB += pixel & 0xFF;
                    count++;
                }
            }
        }

        assertTrue(count > 0, "Emerald gem must contain visible pixels");
        double avgR = (double) totalR / count;
        double avgG = (double) totalG / count;
        double avgB = (double) totalB / count;

        assertTrue(avgG > avgR * 1.5, "Emerald gem must have green dominant over red (avgG=" + avgG + ", avgR=" + avgR + ")");
        assertTrue(avgG > avgB * 1.2, "Emerald gem must have green dominant over blue (avgG=" + avgG + ", avgB=" + avgB + ")");
    }
}
