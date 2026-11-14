package com.spellstorm.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Disposable;
import com.spellstorm.assets.AssetRegistry.FontId;

import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * High-performance typography engine using FreeTypeFontGenerator.
 * Generates and caches BitmapFont instances at exact integer pixel sizes.
 * Provides pre-generated standard fonts and text measurement utilities.
 */
public class FontManager implements Disposable {

    private static class FontKey {
        final FontId id;
        final int size;

        FontKey(FontId id, int size) {
            this.id = id;
            this.size = size;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof FontKey that)) return false;
            return size == that.size && id == that.id;
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, size);
        }
    }

    private final Map<FontId, FreeTypeFontGenerator> generators = new EnumMap<>(FontId.class);
    private final Map<FontKey, BitmapFont> fontCache = new HashMap<>();
    private final Map<Integer, BitmapFont> fallbackFonts = new HashMap<>();

    // Standard high-use pre-generated fonts
    public BitmapFont fontSmall;       // Body 12px (EXP, timers)
    public BitmapFont fontRegular;     // Body 15px (Instructions, subtitles)
    public BitmapFont fontVitals;      // Body 13px (HP text, card desc)
    public BitmapFont fontBoldSmall;   // Bold 12px (Badges)
    public BitmapFont fontBoldRegular; // Bold 16px (Buttons, card titles, boss name)
    public BitmapFont fontTitleMedium; // Title 22px (HUD title)
    public BitmapFont fontTitleLarge;  // Title 30px (Level-Up header)
    public BitmapFont fontTitleHuge;   // Title 36px (Game-Over header)

    public FontManager() {}

    /**
     * Initializes FreeType generators from TTF files and warms standard font caches.
     */
    public void initialize() {
        for (FontId id : FontId.values()) {
            FileHandle handle = AssetRegistry.resolveFile("fonts", handleFileName(id.getPath()));
            if (handle.exists()) {
                try {
                    generators.put(id, new FreeTypeFontGenerator(handle));
                } catch (Exception e) {
                    System.err.println("[FontManager] Error initializing generator for " + id.getPath() + ": " + e.getMessage());
                }
            } else {
                System.err.println("[FontManager] Warning: Font file not found: " + id.getPath());
            }
        }

        // Warm standard fonts
        fontSmall       = getFont(FontId.BODY, 12);
        fontVitals      = getFont(FontId.BODY, 13);
        fontRegular     = getFont(FontId.BODY, 15);
        fontBoldSmall   = getFont(FontId.BOLD, 12);
        fontBoldRegular = getFont(FontId.BOLD, 16);
        fontTitleMedium = getFont(FontId.TITLE, 22);
        fontTitleLarge  = getFont(FontId.TITLE, 30);
        fontTitleHuge   = getFont(FontId.TITLE, 36);
    }

    private String handleFileName(String path) {
        int idx = path.lastIndexOf('/');
        return (idx >= 0) ? path.substring(idx + 1) : path;
    }

    /**
     * Retrieves or generates a BitmapFont for the given font ID and point size.
     * Caches generated fonts as well as fallback fonts to prevent GPU texture leaks.
     */
    public BitmapFont getFont(FontId id, int size) {
        // Enforce valid positive font size
        if (size <= 0) {
            size = 12;
        }

        FontKey key = new FontKey(id, size);
        BitmapFont cached = fontCache.get(key);
        if (cached != null) {
            return cached;
        }

        FreeTypeFontGenerator gen = (id != null) ? generators.get(id) : null;
        if (gen == null) {
            // Retrieve or instantiate cached fallback font for this size to prevent GPU texture leaks
            BitmapFont fallback = fallbackFonts.get(size);
            if (fallback == null) {
                System.err.println("[FontManager] Notice: Generator unavailable for " + id
                        + ", allocating cached fallback font for size " + size);
                fallback = new BitmapFont();
                if (size != 15) {
                    fallback.getData().setScale((float) size / 15f);
                }
                fallbackFonts.put(size, fallback);
            }
            fontCache.put(key, fallback);
            return fallback;
        }

        FreeTypeFontParameter param = new FreeTypeFontParameter();
        param.size = size;
        param.minFilter = TextureFilter.Linear;
        param.magFilter = TextureFilter.Linear;
        param.color = Color.WHITE;
        param.flip = true;

        BitmapFont font = gen.generateFont(param);
        fontCache.put(key, font);
        return font;
    }

    /**
     * Measures the rendered width and height of a string using the specified font.
     * Returns a new independent GlyphLayout instance to eliminate mutable aliasing hazards.
     */
    public GlyphLayout measure(BitmapFont font, CharSequence text) {
        return new GlyphLayout(font, text);
    }

    /**
     * Measures wrapped text within a target width (e.g. 244px card description in LevelUpOverlay).
     * Returns a new independent GlyphLayout instance to eliminate mutable aliasing hazards.
     */
    public GlyphLayout measureWrapped(BitmapFont font, CharSequence text, float targetWidth) {
        return new GlyphLayout(font, text, Color.WHITE, targetWidth, Align.left, true);
    }

    /**
     * Populates a caller-supplied GlyphLayout instance to eliminate object allocations in high-frequency rendering loops.
     */
    public GlyphLayout measure(BitmapFont font, CharSequence text, GlyphLayout targetLayout) {
        if (targetLayout == null) {
            targetLayout = new GlyphLayout();
        }
        targetLayout.setText(font, text);
        return targetLayout;
    }

    /**
     * Populates a caller-supplied GlyphLayout instance with wrapped text to eliminate object allocations in high-frequency loops.
     */
    public GlyphLayout measureWrapped(BitmapFont font, CharSequence text, float targetWidth, GlyphLayout targetLayout) {
        if (targetLayout == null) {
            targetLayout = new GlyphLayout();
        }
        targetLayout.setText(font, text, Color.WHITE, targetWidth, Align.left, true);
        return targetLayout;
    }

    @Override
    public void dispose() {
        // Track already-disposed fonts to safely handle shared or fallback font instances without double-free errors
        Set<BitmapFont> disposedFonts = Collections.newSetFromMap(new IdentityHashMap<>());

        for (BitmapFont font : fontCache.values()) {
            if (font != null && disposedFonts.add(font)) {
                font.dispose();
            }
        }
        fontCache.clear();

        for (BitmapFont font : fallbackFonts.values()) {
            if (font != null && disposedFonts.add(font)) {
                font.dispose();
            }
        }
        fallbackFonts.clear();

        for (FreeTypeFontGenerator gen : generators.values()) {
            if (gen != null) {
                gen.dispose();
            }
        }
        generators.clear();
    }
}
