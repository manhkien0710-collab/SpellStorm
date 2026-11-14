package com.spellstorm;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.assets.AssetRegistry.FontId;
import com.spellstorm.assets.AssetRegistry.TextureId;
import com.spellstorm.assets.FontManager;
import com.spellstorm.assets.TextureManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(HeadlessTestRunner.class)
public class AssetLoadingTest {

    // Authoritative catalog of all 36 PNG image assets with expected (width, height)
    private static final Map<String, int[]> EXPECTED_PNG_DIMENSIONS = Map.ofEntries(
            // Characters (64x32 sprite sheets with 2 frames of 32x32)
            Map.entry("textures/characters/wizard.png", new int[]{64, 32}),
            Map.entry("textures/characters/mage_down.png", new int[]{64, 32}),
            Map.entry("textures/characters/mage_up.png", new int[]{64, 32}),
            Map.entry("textures/characters/mage_left.png", new int[]{64, 32}),
            Map.entry("textures/characters/mage_right.png", new int[]{64, 32}),

            // Enemies
            Map.entry("textures/enemies/melee_fiend.png", new int[]{32, 32}),
            Map.entry("textures/enemies/ranged_cultist.png", new int[]{32, 32}),
            Map.entry("textures/enemies/tank_golem.png", new int[]{48, 48}),
            Map.entry("textures/enemies/boss_colossus.png", new int[]{64, 64}),

            // Environment Objects
            Map.entry("textures/environment/bush.png", new int[]{20, 20}),
            Map.entry("textures/environment/campfire.png", new int[]{24, 24}),
            Map.entry("textures/environment/camp_tent.png", new int[]{40, 36}),
            Map.entry("textures/environment/floor_tile.png", new int[]{64, 64}),
            Map.entry("textures/environment/log.png", new int[]{28, 18}),
            Map.entry("textures/environment/rock.png", new int[]{24, 24}),
            Map.entry("textures/environment/rock_large.png", new int[]{32, 32}),
            Map.entry("textures/environment/ruins.png", new int[]{32, 48}),
            Map.entry("textures/environment/tree_oak.png", new int[]{32, 48}),
            Map.entry("textures/environment/tree_pine.png", new int[]{24, 48}),

            // Pickups
            Map.entry("textures/pickups/gem_cyan.png", new int[]{16, 16}),
            Map.entry("textures/pickups/gem_emerald.png", new int[]{16, 16}),
            Map.entry("textures/pickups/gem_gold.png", new int[]{16, 16}),

            // Projectiles
            Map.entry("textures/projectiles/projectile_player.png", new int[]{24, 24}),
            Map.entry("textures/projectiles/projectile_enemy.png", new int[]{24, 24}),
            Map.entry("textures/projectiles/spell_fire.png", new int[]{24, 24}),
            Map.entry("textures/projectiles/spell_ice.png", new int[]{24, 24}),
            Map.entry("textures/projectiles/spell_lightning.png", new int[]{24, 24}),
            Map.entry("textures/projectiles/spell_wind.png", new int[]{24, 24}),
            Map.entry("textures/projectiles/spell_earth.png", new int[]{24, 24}),

            // Terrain Tiles
            Map.entry("textures/terrain/grass.png", new int[]{32, 32}),
            Map.entry("textures/terrain/dirt.png", new int[]{32, 32}),
            Map.entry("textures/terrain/stone.png", new int[]{32, 32}),
            Map.entry("textures/terrain/water.png", new int[]{32, 32}),
            Map.entry("textures/terrain/sand.png", new int[]{32, 32}),
            Map.entry("textures/terrain/bridge_h.png", new int[]{32, 32}),
            Map.entry("textures/terrain/bridge_v.png", new int[]{32, 32})
    );

    private static final List<String> EXPECTED_TTF_FONTS = List.of(
            "fonts/DejaVuSans.ttf",
            "fonts/DejaVuSans-Bold.ttf",
            "fonts/DejaVuSerif-Bold.ttf"
    );

    @Test
    @DisplayName("Verify asset directory structure and total file inventory count (>= 40)")
    void testAssetDirectoryStructureAndFileCount() {
        File assetRoot = HeadlessTestRunner.getAssetRoot();
        assertNotNull(assetRoot, "Asset root directory must be located");
        assertTrue(assetRoot.exists() && assetRoot.isDirectory(), "Asset root must be an existing directory");

        File texturesDir = new File(assetRoot, "textures");
        File fontsDir = new File(assetRoot, "fonts");

        assertTrue(texturesDir.exists() && texturesDir.isDirectory(), "textures/ directory must exist");
        assertTrue(fontsDir.exists() && fontsDir.isDirectory(), "fonts/ directory must exist");

        // Verify subdirectories in textures
        String[] expectedSubdirs = {"characters", "enemies", "environment", "pickups", "projectiles", "terrain"};
        for (String sub : expectedSubdirs) {
            File subDir = new File(texturesDir, sub);
            assertTrue(subDir.exists() && subDir.isDirectory(), "textures/" + sub + " must exist");
        }

        // Count total asset files
        int totalPngs = 0;
        int totalFonts = 0;
        File[] fontFiles = fontsDir.listFiles((dir, name) -> name.endsWith(".ttf"));
        if (fontFiles != null) totalFonts = fontFiles.length;

        for (String sub : expectedSubdirs) {
            File subDir = new File(texturesDir, sub);
            File[] pngs = subDir.listFiles((dir, name) -> name.endsWith(".png"));
            if (pngs != null) totalPngs += pngs.length;
        }

        assertEquals(3, totalFonts, "Must have exactly 3 TTF fonts");
        assertTrue(totalPngs >= 35 && totalPngs <= 36, 
                "Must have 35 or 36 PNG images (actual found: " + totalPngs + ")");
        assertTrue(totalPngs + totalFonts + 1 >= 40, "Total assets including license must be >= 40");
    }

    @Test
    @DisplayName("Verify all PNG images can be read as valid Pixmaps without corruption and match expected dimensions")
    void testAllPngImagesDecodableAsPixmaps() {
        for (Map.Entry<String, int[]> entry : EXPECTED_PNG_DIMENSIONS.entrySet()) {
            String path = entry.getKey();
            int[] expected = entry.getValue();

            FileHandle handle = HeadlessTestRunner.resolveAsset(path);
            assertTrue(handle.exists(), "Asset file must exist: " + path);

            Pixmap pixmap = null;
            try {
                pixmap = new Pixmap(handle);
                assertNotNull(pixmap, "Pixmap should decode successfully: " + path);
                assertEquals(expected[0], pixmap.getWidth(), "Width mismatch for: " + path);
                assertEquals(expected[1], pixmap.getHeight(), "Height mismatch for: " + path);
            } finally {
                if (pixmap != null) {
                    pixmap.dispose();
                }
            }
        }
    }

    @Test
    @DisplayName("Verify all PNG images can be instantiated as LibGDX Textures via Mock GL")
    void testAllPngImagesInstantiableAsTextures() {
        for (Map.Entry<String, int[]> entry : EXPECTED_PNG_DIMENSIONS.entrySet()) {
            String path = entry.getKey();
            int[] expected = entry.getValue();

            FileHandle handle = HeadlessTestRunner.resolveAsset(path);
            assertTrue(handle.exists(), "Asset file must exist: " + path);

            Texture texture = null;
            try {
                texture = new Texture(handle);
                assertNotNull(texture, "Texture must instantiate for: " + path);
                assertEquals(expected[0], texture.getWidth(), "Texture width mismatch for: " + path);
                assertEquals(expected[1], texture.getHeight(), "Texture height mismatch for: " + path);
            } finally {
                if (texture != null) {
                    texture.dispose();
                }
            }
        }
    }

    @Test
    @DisplayName("Verify all 3 TTF fonts exist, are non-empty, and contain valid TrueType signatures")
    void testAllTtfFontsExistAndNonEmpty() throws IOException {
        for (String fontPath : EXPECTED_TTF_FONTS) {
            FileHandle handle = HeadlessTestRunner.resolveAsset(fontPath);
            assertTrue(handle.exists(), "Font file must exist: " + fontPath);
            assertTrue(handle.length() > 100_000, "Font file must be non-empty (>100KB): " + fontPath + " (size: " + handle.length() + ")");

            // Verify TrueType magic signature bytes
            try (FileInputStream fis = new FileInputStream(handle.file())) {
                byte[] header = new byte[4];
                int read = fis.read(header);
                assertEquals(4, read, "Must read 4-byte header from font");

                boolean isValidTTF = (header[0] == 0x00 && header[1] == 0x01 && header[2] == 0x00 && header[3] == 0x00)
                        || (header[0] == 't' && header[1] == 'r' && header[2] == 'u' && header[3] == 'e')
                        || (header[0] == 'O' && header[1] == 'T' && header[2] == 'T' && header[3] == 'O');
                assertTrue(isValidTTF, "Font file must contain valid TrueType/OpenType magic signature: " + fontPath);
            }
        }

        // Verify DejaVu-LICENSE.txt exists and is non-empty
        FileHandle licenseHandle = HeadlessTestRunner.resolveAsset("fonts/DejaVu-LICENSE.txt");
        assertTrue(licenseHandle.exists(), "DejaVu-LICENSE.txt must exist");
        assertTrue(licenseHandle.length() > 1000, "License must be non-empty");
    }

    @Test
    @DisplayName("Verify sprite sheet slicing yields expected 32x32 frames, filter mode, and bounds math parity directly against TextureManager")
    void testSpriteSheetSlicing() {
        TextureManager tm = new TextureManager();
        try {
            boolean loaded = tm.loadTexture(TextureId.PLAYER);
            assertTrue(loaded, "PLAYER texture must load successfully");

            Texture texture = tm.getTexture(TextureId.PLAYER);
            assertNotNull(texture, "PLAYER texture instance must not be null");
            assertEquals(64, texture.getWidth(), "PLAYER texture width must be 64");
            assertEquals(32, texture.getHeight(), "PLAYER texture height must be 32");

            // Point sampling / Nearest-neighbor filtering verification
            assertEquals(TextureFilter.Nearest, texture.getMinFilter(), "Min filter must be Nearest for pixel-art clarity");
            assertEquals(TextureFilter.Nearest, texture.getMagFilter(), "Mag filter must be Nearest for pixel-art clarity");

            // Frame 0: (0, 0, 32, 32)
            TextureRegion r0 = tm.getFrame(TextureId.PLAYER, 0);
            assertNotNull(r0, "Frame 0 must not be null");
            assertEquals(0, r0.getRegionX(), "Frame 0 X offset must be 0");
            assertEquals(0, r0.getRegionY(), "Frame 0 Y offset must be 0");
            assertEquals(32, r0.getRegionWidth(), "Frame 0 width must be 32");
            assertEquals(32, r0.getRegionHeight(), "Frame 0 height must be 32");

            // Frame 1: (32, 0, 32, 32)
            TextureRegion r1 = tm.getFrame(TextureId.PLAYER, 1);
            assertNotNull(r1, "Frame 1 must not be null");
            assertEquals(32, r1.getRegionX(), "Frame 1 X offset must be 32");
            assertEquals(0, r1.getRegionY(), "Frame 1 Y offset must be 0");
            assertEquals(32, r1.getRegionWidth(), "Frame 1 width must be 32");
            assertEquals(32, r1.getRegionHeight(), "Frame 1 height must be 32");

            // Frame 2 wraps around to Frame 0 (parity with C++ testSpriteSheetFrameMath)
            TextureRegion r2 = tm.getFrame(TextureId.PLAYER, 2);
            assertNotNull(r2, "Frame 2 must not be null");
            assertEquals(0, r2.getRegionX(), "Frame 2 X offset must wrap to 0");
            assertEquals(0, r2.getRegionY(), "Frame 2 Y offset must wrap to 0");
            assertEquals(32, r2.getRegionWidth(), "Frame 2 width must be 32");
            assertEquals(32, r2.getRegionHeight(), "Frame 2 height must be 32");
            assertSame(r0, r2, "Frame 2 must wrap around to identical Frame 0 instance");

            // Negative index -5 clamps safely to Frame 0 (parity with C++ testSpriteSheetFrameMath)
            TextureRegion rNeg = tm.getFrame(TextureId.PLAYER, -5);
            assertNotNull(rNeg, "Negative frame must not be null");
            assertEquals(0, rNeg.getRegionX(), "Negative frame X offset must clamp to 0");
            assertEquals(0, rNeg.getRegionY(), "Negative frame Y offset must clamp to 0");
            assertEquals(32, rNeg.getRegionWidth(), "Negative frame width must be 32");
            assertEquals(32, rNeg.getRegionHeight(), "Negative frame height must be 32");
            assertSame(r0, rNeg, "Negative frame index must clamp safely to Frame 0 instance");
        } finally {
            tm.dispose();
        }
    }

    @Test
    @DisplayName("Verify TextureManager loads all 36 textures, slices frames, and enforces Nearest filtering")
    void testTextureManagerPipeline() {
        TextureManager tm = new TextureManager();
        try {
            // Load individual texture
            boolean loadedPlayer = tm.loadTexture(TextureId.PLAYER);
            assertTrue(loadedPlayer, "PLAYER texture should load successfully");
            assertNotNull(tm.getTexture(TextureId.PLAYER));
            assertNotNull(tm.getRegion(TextureId.PLAYER));

            // Verify TextureFilter.Nearest filtering for crisp pixel art
            Texture playerTex = tm.getTexture(TextureId.PLAYER);
            assertEquals(TextureFilter.Nearest, playerTex.getMinFilter(), "Min filter must be Nearest");
            assertEquals(TextureFilter.Nearest, playerTex.getMagFilter(), "Mag filter must be Nearest");

            // Verify frame 0 and frame 1
            TextureRegion f0 = tm.getFrame(TextureId.PLAYER, 0);
            TextureRegion f1 = tm.getFrame(TextureId.PLAYER, 1);
            assertNotNull(f0);
            assertNotNull(f1);
            assertEquals(32, f0.getRegionWidth());
            assertEquals(32, f1.getRegionWidth());

            // Modulo wrapping: frame 2 -> frame 0
            TextureRegion f2 = tm.getFrame(TextureId.PLAYER, 2);
            assertEquals(f0, f2);

            // Negative clamp: frame -1 -> frame 0
            TextureRegion fNeg = tm.getFrame(TextureId.PLAYER, -1);
            assertEquals(f0, fNeg);

            // Animation helper
            Animation<TextureRegion> anim = tm.getAnimation(TextureId.PLAYER, 0.125f, Animation.PlayMode.LOOP);
            assertNotNull(anim);
            assertEquals(2, anim.getKeyFrames().length);

            // Load all textures and verify TextureFilter.Nearest on all 36 textures
            tm.loadAll();
            for (TextureId id : TextureId.values()) {
                assertTrue(tm.hasTexture(id), "TextureId must be loaded: " + id.name());
                assertNotNull(tm.getRegion(id), "TextureRegion must not be null for: " + id.name());
                Texture tex = tm.getTexture(id);
                assertNotNull(tex, "Texture instance must not be null for: " + id.name());
                assertEquals(TextureFilter.Nearest, tex.getMinFilter(),
                        "MinFilter for " + id.name() + " must be Nearest for crisp pixel art");
                assertEquals(TextureFilter.Nearest, tex.getMagFilter(),
                        "MagFilter for " + id.name() + " must be Nearest for crisp pixel art");
            }
        } finally {
            tm.dispose();
        }
    }

    @Test
    @DisplayName("Verify FontManager initializes and generates BitmapFonts at standard sizes")
    void testFontManagerPipeline() {
        FontManager fm = new FontManager();
        try {
            fm.initialize();

            assertNotNull(fm.fontSmall, "fontSmall should be initialized");
            assertNotNull(fm.fontVitals, "fontVitals should be initialized");
            assertNotNull(fm.fontRegular, "fontRegular should be initialized");
            assertNotNull(fm.fontBoldSmall, "fontBoldSmall should be initialized");
            assertNotNull(fm.fontBoldRegular, "fontBoldRegular should be initialized");
            assertNotNull(fm.fontTitleMedium, "fontTitleMedium should be initialized");
            assertNotNull(fm.fontTitleLarge, "fontTitleLarge should be initialized");
            assertNotNull(fm.fontTitleHuge, "fontTitleHuge should be initialized");

            // Custom size generation
            BitmapFont customFont = fm.getFont(FontId.BOLD, 20);
            assertNotNull(customFont, "Custom size font should generate");

            // Measurement
            GlyphLayout layout = fm.measure(fm.fontRegular, "Test Text");
            assertTrue(layout.width > 0, "Measured text width should be > 0");

            // Wrapped measurement
            GlyphLayout wrapped = fm.measureWrapped(fm.fontVitals, "This is a long description text for upgrade cards.", 244f);
            assertTrue(wrapped.width <= 244.5f, "Wrapped width should fit target constraint");
        } finally {
            fm.dispose();
        }
    }

    @Test
    @DisplayName("Verify missing asset returns safe fallback / does not throw unhandled crash")
    void testMissingAssetGracefulFallback() {
        FileHandle missing = HeadlessTestRunner.resolveAsset("bogus_missing_path_xyz123.png");
        assertFalse(missing.exists(), "Bogus asset handle must report exists == false");

        FileHandle resolved = AssetRegistry.resolveFile("textures", "non_existent_file.png");
        assertFalse(resolved.exists(), "AssetRegistry should report non-existent file exists == false");
    }
}
