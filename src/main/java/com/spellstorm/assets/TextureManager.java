package com.spellstorm.assets;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.Texture.TextureFilter;
import com.badlogic.gdx.graphics.g2d.Animation;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.utils.Disposable;
import com.spellstorm.assets.AssetRegistry.TextureId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Owns and caches all Texture and TextureRegion resources.
 * Enforces TextureFilter.Nearest for crisp pixel art, slices sprite sheets,
 * and handles graceful procedural fallback when assets are not available.
 */
public class TextureManager implements Disposable {

    /**
     * Common descriptor for multi-frame sprite sheets stored outside the
     * canonical 36 {@link TextureId} entries.
     */
    public interface SpriteSheetId {
        String getSubPath();
        int getFrameWidth();
        int getFrameHeight();
        int getTotalFrames();
    }

    /**
     * Extended Milestone 2 animation and VFX sprite sheets stored outside the
     * canonical 36 {@link TextureId} entries to preserve P0/P1 asset inventory invariants.
     */
    public enum ExtendedSheetId implements SpriteSheetId {
        PLAYER_DOWN_ANIM("animations/player_down_sheet.png", 32, 32, 8),
        PLAYER_UP_ANIM("animations/player_up_sheet.png", 32, 32, 8),
        PLAYER_LEFT_ANIM("animations/player_left_sheet.png", 32, 32, 8),
        PLAYER_RIGHT_ANIM("animations/player_right_sheet.png", 32, 32, 8),
        MELEE_FIEND_ANIM("animations/melee_fiend_sheet.png", 32, 32, 8),
        CAMPFIRE_ANIM("animations/campfire_sheet.png", 24, 24, 4),
        SPELL_FIRE_ANIM("vfx/spell_fire_sheet.png", 24, 24, 6),
        VFX_FIRE_IMPACT("vfx/vfx_fire_impact.png", 32, 32, 6),
        VFX_FIRE_CAST("vfx/vfx_fire_cast.png", 24, 24, 4);

        private final String subPath;
        private final int frameWidth;
        private final int frameHeight;
        private final int totalFrames;

        ExtendedSheetId(String subPath, int frameWidth, int frameHeight, int totalFrames) {
            this.subPath = subPath;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.totalFrames = totalFrames;
        }

        @Override public String getSubPath() { return subPath; }
        @Override public int getFrameWidth() { return frameWidth; }
        @Override public int getFrameHeight() { return frameHeight; }
        @Override public int getTotalFrames() { return totalFrames; }
    }

    /**
     * Milestone 3/4 sprite, VFX, terrain transition, and UI sheets completing
     * the 31-sheet SpellStorm graphics suite alongside {@link ExtendedSheetId}.
     */
    public enum CompleteSheetId implements SpriteSheetId {
        PLAYER_HURT_DEATH_ANIM("animations/player_hurt_death_sheet.png", 32, 32, 6),
        CATALYST_ORB_ANIM("vfx/catalyst_orb_sheet.png", 16, 16, 6),
        RANGED_CULTIST_ANIM("animations/ranged_cultist_sheet.png", 32, 32, 8),
        TANK_GOLEM_ANIM("animations/tank_golem_sheet.png", 48, 48, 8),
        BOSS_COLOSSUS_ANIM("animations/boss_colossus_sheet.png", 64, 64, 8),
        WATER_ANIM("animations/water_sheet.png", 32, 32, 4),
        TERRAIN_VARIANTS("animations/terrain_variants_sheet.png", 32, 32, 8),
        TERRAIN_TRANSITIONS("animations/terrain_transitions_sheet.png", 32, 32, 8),
        SPELL_ICE_ANIM("vfx/spell_ice_sheet.png", 24, 24, 6),
        SPELL_LIGHTNING_ANIM("vfx/spell_lightning_sheet.png", 24, 24, 6),
        SPELL_WIND_ANIM("vfx/spell_wind_sheet.png", 24, 24, 6),
        SPELL_EARTH_ANIM("vfx/spell_earth_sheet.png", 24, 24, 6),
        PROJECTILE_PLAYER_ANIM("vfx/projectile_player_sheet.png", 24, 24, 6),
        PROJECTILE_ENEMY_ANIM("vfx/projectile_enemy_sheet.png", 24, 24, 6),
        VFX_ICE_IMPACT("vfx/vfx_ice_impact.png", 32, 32, 6),
        VFX_LIGHTNING_IMPACT("vfx/vfx_lightning_impact.png", 32, 32, 6),
        VFX_WIND_IMPACT("vfx/vfx_wind_impact.png", 32, 32, 6),
        VFX_EARTH_IMPACT("vfx/vfx_earth_impact.png", 32, 32, 6),
        VFX_ARCANE_IMPACT("vfx/vfx_arcane_impact.png", 32, 32, 6),
        GEMS_ANIM("animations/gems_anim_sheet.png", 16, 16, 12),
        HUD_ELEMENTS("ui/hud_elements_sheet.png", 16, 16, 6),
        POWERUP_ICONS("ui/powerup_icons_sheet.png", 16, 16, 3);

        private final String subPath;
        private final int frameWidth;
        private final int frameHeight;
        private final int totalFrames;

        CompleteSheetId(String subPath, int frameWidth, int frameHeight, int totalFrames) {
            this.subPath = subPath;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.totalFrames = totalFrames;
        }

        @Override public String getSubPath() { return subPath; }
        @Override public int getFrameWidth() { return frameWidth; }
        @Override public int getFrameHeight() { return frameHeight; }
        @Override public int getTotalFrames() { return totalFrames; }
    }

    private final Map<TextureId, Texture> textures = new EnumMap<>(TextureId.class);
    private final Map<TextureId, TextureRegion[]> spriteFrames = new EnumMap<>(TextureId.class);
    private final Map<SpriteSheetId, Texture> sheetTextures = new HashMap<>();
    private final Map<SpriteSheetId, TextureRegion[]> sheetFrames = new HashMap<>();
    private final Map<String, Texture> namedTextures = new HashMap<>();

    public TextureManager() {}

    /** Returns all 31 registered {@link SpriteSheetId} descriptors (9 extended + 22 complete). */
    public static List<SpriteSheetId> getAllSheetIds() {
        List<SpriteSheetId> all = new ArrayList<>();
        Collections.addAll(all, ExtendedSheetId.values());
        Collections.addAll(all, CompleteSheetId.values());
        return Collections.unmodifiableList(all);
    }

    /**
     * Loads all 36 registered textures and all 31 Milestone 2-4 sprite sheets,
     * generating sprite sheet frames with Nearest-neighbor filtering.
     */
    public void loadAll() {
        for (TextureId id : TextureId.values()) {
            loadTexture(id);
        }
        for (ExtendedSheetId ext : ExtendedSheetId.values()) {
            loadSheet(ext);
        }
        for (CompleteSheetId complete : CompleteSheetId.values()) {
            loadSheet(complete);
        }
    }

    /**
     * Loads a specific TextureId using candidate path resolution.
     * Applies TextureFilter.Nearest to min and mag filters.
     * Returns true if loaded successfully, false on failure (without throwing).
     */
    public boolean loadTexture(TextureId id) {
        if (textures.containsKey(id)) {
            return true; // Already loaded
        }

        FileHandle handle = AssetRegistry.resolveFile("textures", id.getSubPath());
        if (!handle.exists()) {
            System.err.println("[TextureManager] Notice: Texture not found: " + id.getSubPath()
                    + " (entity will use procedural geometric fallback)");
            return false;
        }

        try {
            Texture texture = new Texture(handle);
            // CRITICAL: Point sampling / Nearest-neighbor filtering for crisp pixel art
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            textures.put(id, texture);

            // Slicing into frames
            sliceFrames(id, texture);
            return true;
        } catch (Exception e) {
            System.err.println("[TextureManager] Error loading " + id.getSubPath() + ": " + e.getMessage());
            return false;
        }
    }

    private void sliceFrames(TextureId id, Texture texture) {
        final int fw = id.getFrameWidth();
        final int fh = id.getFrameHeight();
        final int cols = id.getColumns();
        final int rows = id.getRows();
        final int total = id.getTotalFrames();

        TextureRegion[] frames = new TextureRegion[total];

        if (total == 1 && cols == 1 && rows == 1) {
            // Single frame texture
            frames[0] = new TextureRegion(texture, 0, 0, fw, fh);
        } else {
            // Multi-frame grid slicing (e.g. 64x32 player sheet -> 2 frames of 32x32)
            TextureRegion[][] grid = TextureRegion.split(texture, fw, fh);
            for (int i = 0; i < total; i++) {
                int col = (cols > 0) ? (i % cols) : 0;
                int row = (cols > 0) ? (i / cols) : 0;
                frames[i] = grid[row][col];
            }
        }
        spriteFrames.put(id, frames);
    }

    /**
     * Returns the underlying LibGDX Texture, or null if not loaded.
     */
    public Texture getTexture(TextureId id) {
        return textures.get(id);
    }

    /**
     * Returns the primary / first TextureRegion for this texture, or null if not loaded.
     */
    public TextureRegion getRegion(TextureId id) {
        TextureRegion[] frames = spriteFrames.get(id);
        if (frames != null && frames.length > 0) {
            return frames[0];
        }
        return null;
    }

    /**
     * Gets a specific animation frame with modulo wrapping and safe negative index clamping.
     * 1:1 parity with C++ ss::ResourceManager::getFrameRect.
     */
    public TextureRegion getFrame(TextureId id, int frameIndex) {
        TextureRegion[] frames = spriteFrames.get(id);
        if (frames == null || frames.length == 0) {
            return null;
        }
        if (frameIndex < 0) {
            frameIndex = 0;
        }
        int index = frameIndex % frames.length;
        return frames[index];
    }

    /**
     * Constructs a LibGDX Animation for the specified TextureId.
     */
    public Animation<TextureRegion> getAnimation(TextureId id, float frameDuration, Animation.PlayMode playMode) {
        TextureRegion[] frames = spriteFrames.get(id);
        if (frames == null || frames.length == 0) {
            return null;
        }
        Animation<TextureRegion> anim = new Animation<>(frameDuration, frames);
        anim.setPlayMode(playMode);
        return anim;
    }

    public boolean hasTexture(TextureId id) {
        return id != null && textures.containsKey(id);
    }

    /**
     * Loads a Milestone 2 extended animation/VFX sprite sheet with Nearest-neighbor filtering
     * Loads any registered {@link SpriteSheetId} with Nearest-neighbor filtering
     * and slices it into {@code totalFrames} sequential regions.
     */
    public boolean loadSheet(SpriteSheetId id) {
        if (id == null) {
            return false;
        }
        if (sheetTextures.containsKey(id)) {
            return true;
        }

        FileHandle handle = AssetRegistry.resolveFile("textures", id.getSubPath());
        if (!handle.exists()) {
            System.err.println("[TextureManager] Notice: Sprite sheet not found: " + id.getSubPath());
            return false;
        }

        try {
            Texture texture = new Texture(handle);
            texture.setFilter(TextureFilter.Nearest, TextureFilter.Nearest);
            sheetTextures.put(id, texture);

            int fw = id.getFrameWidth();
            int fh = id.getFrameHeight();
            int total = id.getTotalFrames();
            TextureRegion[][] grid = TextureRegion.split(texture, fw, fh);
            int rows = grid.length;
            int cols = (rows > 0) ? grid[0].length : 0;

            TextureRegion[] frames = new TextureRegion[total];
            for (int i = 0; i < total; i++) {
                int row = (cols > 0) ? (i / cols) : 0;
                int col = (cols > 0) ? (i % cols) : 0;
                if (row < rows && col < cols) {
                    frames[i] = grid[row][col];
                }
            }
            sheetFrames.put(id, frames);
            return true;
        } catch (Exception e) {
            System.err.println("[TextureManager] Error loading sprite sheet " + id.getSubPath() + ": " + e.getMessage());
            return false;
        }
    }

    public boolean loadExtendedSheet(SpriteSheetId id) {
        return loadSheet(id);
    }

    public boolean hasSheet(SpriteSheetId id) {
        return id != null && sheetTextures.containsKey(id);
    }

    public boolean hasExtendedSheet(SpriteSheetId id) {
        return hasSheet(id);
    }

    public Texture getSheetTexture(SpriteSheetId id) {
        return id != null ? sheetTextures.get(id) : null;
    }

    public Texture getExtendedTexture(SpriteSheetId id) {
        return getSheetTexture(id);
    }

    /**
     * Returns a specific frame from a loaded sprite sheet with modulo wrapping
     * and safe negative index clamping.
     */
    public TextureRegion getSheetFrame(SpriteSheetId id, int frameIndex) {
        if (id == null) {
            return null;
        }
        TextureRegion[] frames = sheetFrames.get(id);
        if (frames == null || frames.length == 0) {
            return null;
        }
        if (frameIndex < 0) {
            frameIndex = 0;
        }
        return frames[frameIndex % frames.length];
    }

    public TextureRegion getExtendedFrame(SpriteSheetId id, int frameIndex) {
        return getSheetFrame(id, frameIndex);
    }

    @Override
    public void dispose() {
        for (Texture tex : textures.values()) {
            if (tex != null) {
                tex.dispose();
            }
        }
        textures.clear();
        spriteFrames.clear();

        for (Texture tex : sheetTextures.values()) {
            if (tex != null) {
                tex.dispose();
            }
        }
        sheetTextures.clear();
        sheetFrames.clear();

        for (Texture tex : namedTextures.values()) {
            if (tex != null) {
                tex.dispose();
            }
        }
        namedTextures.clear();
    }
}
