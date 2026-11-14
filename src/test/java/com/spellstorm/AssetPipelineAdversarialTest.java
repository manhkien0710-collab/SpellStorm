package com.spellstorm;

import com.badlogic.gdx.files.FileHandle;
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

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(HeadlessTestRunner.class)
public class AssetPipelineAdversarialTest {

    @Test
    @DisplayName("ADV-1: Nearest-Neighbor filtering (TextureFilter.Nearest) on ALL 36 textures")
    void testNearestNeighborFilteringOnAllTextures() {
        TextureManager tm = new TextureManager();
        try {
            tm.loadAll();

            assertEquals(36, TextureId.values().length, "There must be exactly 36 TextureId constants");

            for (TextureId id : TextureId.values()) {
                assertTrue(tm.hasTexture(id), "Texture must be loaded: " + id.name());
                Texture tex = tm.getTexture(id);
                assertNotNull(tex, "Texture instance must not be null for: " + id.name());

                // CRITICAL ADVERSARIAL CHECK: Both min and mag filter MUST be TextureFilter.Nearest
                assertEquals(TextureFilter.Nearest, tex.getMinFilter(),
                        "MinFilter for " + id.name() + " must be Nearest for pixel-art clarity");
                assertEquals(TextureFilter.Nearest, tex.getMagFilter(),
                        "MagFilter for " + id.name() + " must be Nearest for pixel-art clarity");
            }
        } finally {
            tm.dispose();
        }
    }

    @Test
    @DisplayName("ADV-2: Frame slicing edge cases across character sprite sheets")
    void testFrameSlicingEdgeCasesAcrossSheets() {
        TextureManager tm = new TextureManager();
        try {
            TextureId[] characterSheets = {
                    TextureId.PLAYER,
                    TextureId.PLAYER_DOWN,
                    TextureId.PLAYER_UP,
                    TextureId.PLAYER_LEFT,
                    TextureId.PLAYER_RIGHT
            };

            for (TextureId sheetId : characterSheets) {
                assertTrue(tm.loadTexture(sheetId));

                TextureRegion f0 = tm.getFrame(sheetId, 0);
                TextureRegion f1 = tm.getFrame(sheetId, 1);
                assertNotNull(f0, "Frame 0 must not be null for " + sheetId);
                assertNotNull(f1, "Frame 1 must not be null for " + sheetId);

                // Frame dimensions and region coordinates
                assertEquals(0, f0.getRegionX(), "Frame 0 X coordinate must be 0 for " + sheetId);
                assertEquals(0, f0.getRegionY(), "Frame 0 Y coordinate must be 0 for " + sheetId);
                assertEquals(32, f0.getRegionWidth(), "Frame 0 width must be 32 for " + sheetId);
                assertEquals(32, f0.getRegionHeight(), "Frame 0 height must be 32 for " + sheetId);

                assertEquals(32, f1.getRegionX(), "Frame 1 X coordinate must be 32 for " + sheetId);
                assertEquals(0, f1.getRegionY(), "Frame 1 Y coordinate must be 0 for " + sheetId);
                assertEquals(32, f1.getRegionWidth(), "Frame 1 width must be 32 for " + sheetId);
                assertEquals(32, f1.getRegionHeight(), "Frame 1 height must be 32 for " + sheetId);

                // Frame 2 wraps to Frame 0
                assertSame(f0, tm.getFrame(sheetId, 2), "Frame 2 must wrap around to Frame 0");

                // Frame 3 wraps to Frame 1
                assertSame(f1, tm.getFrame(sheetId, 3), "Frame 3 must wrap around to Frame 1");

                // Frame 4 wraps to Frame 0
                assertSame(f0, tm.getFrame(sheetId, 4), "Frame 4 must wrap around to Frame 0");

                // Negative frames clamp safely to Frame 0
                assertSame(f0, tm.getFrame(sheetId, -1), "Frame -1 must clamp to Frame 0");
                assertSame(f0, tm.getFrame(sheetId, -5), "Frame -5 must clamp to Frame 0");
                assertSame(f0, tm.getFrame(sheetId, -100), "Frame -100 must clamp to Frame 0");
                assertSame(f0, tm.getFrame(sheetId, Integer.MIN_VALUE), "Frame Integer.MIN_VALUE must clamp to Frame 0");

                // Large frame numbers wrap modulo totalFrames
                assertSame(f0, tm.getFrame(sheetId, 1_000_000), "Frame 1_000_000 must wrap to Frame 0");
                assertSame(f1, tm.getFrame(sheetId, 1_000_001), "Frame 1_000_001 must wrap to Frame 1");
                assertSame(f1, tm.getFrame(sheetId, Integer.MAX_VALUE), "Frame Integer.MAX_VALUE must wrap to Frame 1");

                // LibGDX Animation construction
                Animation<TextureRegion> anim = tm.getAnimation(sheetId, 0.15f, Animation.PlayMode.LOOP);
                assertNotNull(anim);
                assertEquals(2, anim.getKeyFrames().length);
                assertSame(f0, anim.getKeyFrame(0.0f));
                assertSame(f1, anim.getKeyFrame(0.2f));
            }
        } finally {
            tm.dispose();
        }
    }

    @Test
    @DisplayName("ADV-3: Single-frame textures boundary behavior")
    void testSingleFrameTexturesBoundaryBehavior() {
        TextureManager tm = new TextureManager();
        try {
            TextureId[] singleFrameIds = {
                    TextureId.BOSS_ENEMY,
                    TextureId.TILE_GRASS,
                    TextureId.PROJECTILE_FIRE,
                    TextureId.DECO_TREE_OAK,
                    TextureId.GEM_GOLD
            };

            for (TextureId id : singleFrameIds) {
                assertTrue(tm.loadTexture(id));
                TextureRegion baseRegion = tm.getRegion(id);
                assertNotNull(baseRegion);

                // Any positive frame index modulo 1 returns the only frame
                assertSame(baseRegion, tm.getFrame(id, 0));
                assertSame(baseRegion, tm.getFrame(id, 1));
                assertSame(baseRegion, tm.getFrame(id, 2));
                assertSame(baseRegion, tm.getFrame(id, 999));
                assertSame(baseRegion, tm.getFrame(id, 1_000_000));
                assertSame(baseRegion, tm.getFrame(id, Integer.MAX_VALUE));

                // Any negative frame index clamped to 0 returns the only frame
                assertSame(baseRegion, tm.getFrame(id, -1));
                assertSame(baseRegion, tm.getFrame(id, -5));
                assertSame(baseRegion, tm.getFrame(id, Integer.MIN_VALUE));

                // Animation for single frame
                Animation<TextureRegion> anim = tm.getAnimation(id, 0.1f, Animation.PlayMode.NORMAL);
                assertNotNull(anim);
                assertEquals(1, anim.getKeyFrames().length);
                assertSame(baseRegion, anim.getKeyFrames()[0]);
            }
        } finally {
            tm.dispose();
        }
    }

    @Test
    @DisplayName("ADV-4: Unloaded and null-safety defensive queries in TextureManager")
    void testUnloadedAndNullSafetyInTextureManager() {
        TextureManager tm = new TextureManager();
        try {
            // Queries on completely unpopulated manager
            assertFalse(tm.hasTexture(TextureId.PLAYER));
            assertNull(tm.getTexture(TextureId.PLAYER));
            assertNull(tm.getRegion(TextureId.PLAYER));
            assertNull(tm.getFrame(TextureId.PLAYER, 0));
            assertNull(tm.getFrame(TextureId.PLAYER, -5));
            assertNull(tm.getFrame(TextureId.PLAYER, 100));
            assertNull(tm.getAnimation(TextureId.PLAYER, 0.1f, Animation.PlayMode.LOOP));

            // Passing null TextureId should not throw unhandled exception
            assertFalse(tm.hasTexture(null));
            assertNull(tm.getTexture(null));
            assertNull(tm.getRegion(null));
            assertNull(tm.getFrame(null, 0));
            assertNull(tm.getAnimation(null, 0.1f, Animation.PlayMode.LOOP));
        } finally {
            tm.dispose();
        }
    }

    @Test
    @DisplayName("ADV-5: FontManager standard sizes, custom point-sizes, and layout bounds")
    void testFontManagerSizesAndLayoutBounds() {
        FontManager fm = new FontManager();
        try {
            fm.initialize();

            // All 8 standard pre-warmed fonts
            BitmapFont[] standardFonts = {
                    fm.fontSmall, fm.fontVitals, fm.fontRegular,
                    fm.fontBoldSmall, fm.fontBoldRegular,
                    fm.fontTitleMedium, fm.fontTitleLarge, fm.fontTitleHuge
            };
            for (BitmapFont font : standardFonts) {
                assertNotNull(font, "Standard pre-warmed font must not be null");
                assertTrue(font.getCapHeight() > 0, "Font cap height must be positive");
                assertTrue(font.getLineHeight() > 0, "Font line height must be positive");
            }

            // Verify point sizes correlate with capHeight / lineHeight progression
            assertTrue(fm.fontSmall.getLineHeight() < fm.fontRegular.getLineHeight(),
                    "12px small font must have smaller line height than 15px regular font");
            assertTrue(fm.fontTitleMedium.getLineHeight() < fm.fontTitleLarge.getLineHeight(),
                    "22px title font must have smaller line height than 30px title font");
            assertTrue(fm.fontTitleLarge.getLineHeight() < fm.fontTitleHuge.getLineHeight(),
                    "30px title font must have smaller line height than 36px title font");

            // Custom size generation & caching
            BitmapFont body20_a = fm.getFont(FontId.BODY, 20);
            BitmapFont body20_b = fm.getFont(FontId.BODY, 20);
            assertNotNull(body20_a);
            assertSame(body20_a, body20_b, "FontManager must cache generated BitmapFonts");

            BitmapFont bold40 = fm.getFont(FontId.BOLD, 40);
            assertNotNull(bold40);
            assertTrue(bold40.getLineHeight() > body20_a.getLineHeight());

            // Typography measurement edge cases
            GlyphLayout layoutEmpty = fm.measure(fm.fontRegular, "");
            assertEquals(0f, layoutEmpty.width, 0.001f, "Empty text must have 0 width");

            GlyphLayout layoutShort = fm.measure(fm.fontBoldRegular, "LVL 99");
            assertTrue(layoutShort.width > 0f);

            // Multiline string measurement
            GlyphLayout layoutMulti = fm.measure(fm.fontRegular, "Line 1\nLine 2\nLine 3");
            assertTrue(layoutMulti.height >= fm.fontRegular.getLineHeight() * 2f);

            // Wrapping constraint check (244px card description in LevelUpOverlay)
            String longDesc = "Unleashes an erupting blast of molten rock that deals heavy fire damage and pierces up to 3 targets.";
            GlyphLayout wrapped = fm.measureWrapped(fm.fontVitals, longDesc, 244f);
            assertTrue(wrapped.width <= 244.5f, "Wrapped layout width must not exceed 244px constraint");
            assertTrue(wrapped.height > fm.fontVitals.getLineHeight(), "Wrapped long text must wrap to multiple lines");
        } finally {
            fm.dispose();
        }
    }

    @Test
    @DisplayName("ADV-6: Fallback when FontManager is uninitialized or font is missing")
    void testFontManagerFallbackWhenUninitialized() {
        FontManager uninitFm = new FontManager();
        try {
            // Calling getFont without calling initialize()
            BitmapFont fallbackFont = uninitFm.getFont(FontId.BODY, 14);
            assertNotNull(fallbackFont, "Uninitialized FontManager must return fallback BitmapFont without throwing");

            GlyphLayout layout = uninitFm.measure(fallbackFont, "Fallback test");
            assertTrue(layout.width > 0f, "Fallback font must be capable of text layout measurement");
        } finally {
            uninitFm.dispose();
        }
    }

    @Test
    @DisplayName("ADV-7: Candidate path resolution edge cases in AssetRegistry")
    void testAssetRegistryCandidatePathResolution() {
        // Direct internal path
        FileHandle direct = AssetRegistry.resolveFile("textures", "characters/wizard.png");
        assertTrue(direct.exists(), "Direct textures resolution must succeed");

        // Relative path with images/ fallback
        FileHandle imgFallback = AssetRegistry.resolveFile("textures", "terrain/grass.png");
        assertTrue(imgFallback.exists(), "Terrain grass resolution must succeed");

        // Font resolution
        FileHandle fontHandle = AssetRegistry.resolveFile("fonts", "DejaVuSans.ttf");
        assertTrue(fontHandle.exists(), "DejaVuSans.ttf must resolve successfully");

        // Bogus non-existent file
        FileHandle fake = AssetRegistry.resolveFile("textures", "definitely_not_a_real_texture_98765.png");
        assertFalse(fake.exists(), "Non-existent file handle must return exists() == false");

        // Null subDir with full relative path
        FileHandle nullSubDir = AssetRegistry.resolveFile(null, "assets/fonts/DejaVuSerif-Bold.ttf");
        assertTrue(nullSubDir.exists(), "Null subDir with explicit assets/ prefix must resolve");
    }

    @Test
    @DisplayName("ADV-8: Idempotent disposal safety in TextureManager and FontManager")
    void testIdempotentDisposalSafety() {
        TextureManager tm = new TextureManager();
        tm.loadTexture(TextureId.PLAYER);
        tm.dispose();
        // Calling dispose a second time must be completely safe
        assertDoesNotThrow(tm::dispose, "TextureManager.dispose() must be idempotent");

        FontManager fm = new FontManager();
        fm.initialize();
        fm.dispose();
        // Calling dispose a second time must be completely safe
        assertDoesNotThrow(fm::dispose, "FontManager.dispose() must be idempotent");
    }

    @Test
    @DisplayName("ADV-9: Extreme font sizes stress testing")
    void testExtremeFontSizes() {
        FontManager fm = new FontManager();
        try {
            fm.initialize();

            // Very small font (4px)
            BitmapFont tiny = fm.getFont(FontId.BODY, 4);
            assertNotNull(tiny);
            assertTrue(tiny.getLineHeight() > 0);

            // Large font (72px)
            BitmapFont giant = fm.getFont(FontId.TITLE, 72);
            assertNotNull(giant);
            assertTrue(giant.getLineHeight() > fm.fontTitleHuge.getLineHeight());
        } finally {
            fm.dispose();
        }
    }
}
