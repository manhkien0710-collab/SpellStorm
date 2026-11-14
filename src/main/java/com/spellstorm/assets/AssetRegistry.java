package com.spellstorm.assets;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Authoritative registry of all assets in Spell Storm.
 * Provides type-safe IDs, relative paths, metadata, and multi-candidate path resolution.
 */
public final class AssetRegistry {

    private AssetRegistry() {} // Prevent instantiation

    // Base directories
    public static final String ASSETS_DIR = "assets/";
    public static final String FONTS_DIR = "fonts/";
    public static final String TEXTURES_DIR = "textures/";

    // -------------------------------------------------------------
    // Font Identifiers & Paths
    // -------------------------------------------------------------
    public enum FontId {
        BODY("fonts/DejaVuSans.ttf", "Body font for vitals, descriptions, instructions"),
        BOLD("fonts/DejaVuSans-Bold.ttf", "Bold font for badges, boss name, button labels"),
        TITLE("fonts/DejaVuSerif-Bold.ttf", "Serif title font for Level-Up and Game-Over headers");

        private final String path;
        private final String description;

        FontId(String path, String description) {
            this.path = path;
            this.description = description;
        }

        public String getPath() { return path; }
        public String getDescription() { return description; }
    }

    // -------------------------------------------------------------
    // Texture Identifiers & Specifications
    // -------------------------------------------------------------
    public enum TextureId {
        // Player & Directional Sheets (64x32: 2 frames of 32x32)
        PLAYER("characters/wizard.png", 64, 32, 32, 32, 2, 1, 2, 16f, 16f),
        PLAYER_DOWN("characters/mage_down.png", 64, 32, 32, 32, 2, 1, 2, 16f, 16f),
        PLAYER_UP("characters/mage_up.png", 64, 32, 32, 32, 2, 1, 2, 16f, 16f),
        PLAYER_LEFT("characters/mage_left.png", 64, 32, 32, 32, 2, 1, 2, 16f, 16f),
        PLAYER_RIGHT("characters/mage_right.png", 64, 32, 32, 32, 2, 1, 2, 16f, 16f),

        // Enemies
        MELEE_ENEMY("enemies/melee_fiend.png", 32, 32, 32, 32, 1, 1, 1, 16f, 16f),
        RANGED_ENEMY("enemies/ranged_cultist.png", 32, 32, 32, 32, 1, 1, 1, 16f, 16f),
        TANK_ENEMY("enemies/tank_golem.png", 48, 48, 48, 48, 1, 1, 1, 24f, 24f),
        BOSS_ENEMY("enemies/boss_colossus.png", 64, 64, 64, 64, 1, 1, 1, 32f, 32f),

        // Projectiles (24x24)
        PROJECTILE_PLAYER("projectiles/projectile_player.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),
        PROJECTILE_ENEMY("projectiles/projectile_enemy.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),
        PROJECTILE_FIRE("projectiles/spell_fire.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),
        PROJECTILE_ICE("projectiles/spell_ice.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),
        PROJECTILE_LIGHTNING("projectiles/spell_lightning.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),
        PROJECTILE_WIND("projectiles/spell_wind.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),
        PROJECTILE_EARTH("projectiles/spell_earth.png", 24, 24, 24, 24, 1, 1, 1, 12f, 12f),

        // Pickups (16x16)
        GEM_CYAN("pickups/gem_cyan.png", 16, 16, 16, 16, 1, 1, 1, 8f, 8f),
        GEM_EMERALD("pickups/gem_emerald.png", 16, 16, 16, 16, 1, 1, 1, 8f, 8f),
        GEM_GOLD("pickups/gem_gold.png", 16, 16, 16, 16, 1, 1, 1, 8f, 8f),

        // Environment & Arena Floor
        ARENA_FLOOR("environment/floor_tile.png", 64, 64, 64, 64, 1, 1, 1, 0f, 0f),

        // Terrain Tiles (32x32)
        TILE_GRASS("terrain/grass.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),
        TILE_DIRT("terrain/dirt.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),
        TILE_STONE("terrain/stone.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),
        TILE_WATER("terrain/water.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),
        TILE_SAND("terrain/sand.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),
        TILE_BRIDGE_H("terrain/bridge_h.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),
        TILE_BRIDGE_V("terrain/bridge_v.png", 32, 32, 32, 32, 1, 1, 1, 0f, 0f),

        // Decorative Environment Objects (Anchor bottom-center: originX = w / 2, originY = h)
        DECO_TREE_OAK("environment/tree_oak.png", 32, 48, 32, 48, 1, 1, 1, 16f, 48f),
        DECO_TREE_PINE("environment/tree_pine.png", 24, 48, 24, 48, 1, 1, 1, 12f, 48f),
        DECO_ROCK("environment/rock.png", 24, 24, 24, 24, 1, 1, 1, 12f, 24f),
        DECO_ROCK_LARGE("environment/rock_large.png", 32, 32, 32, 32, 1, 1, 1, 16f, 32f),
        DECO_BUSH("environment/bush.png", 20, 20, 20, 20, 1, 1, 1, 10f, 20f),
        DECO_LOG("environment/log.png", 28, 18, 28, 18, 1, 1, 1, 14f, 18f),
        DECO_RUINS("environment/ruins.png", 32, 48, 32, 48, 1, 1, 1, 16f, 48f),
        DECO_TENT("environment/camp_tent.png", 40, 36, 40, 36, 1, 1, 1, 20f, 36f),
        DECO_CAMPFIRE("environment/campfire.png", 24, 24, 24, 24, 1, 1, 1, 12f, 24f);

        private final String subPath;
        private final int imageWidth;
        private final int imageHeight;
        private final int frameWidth;
        private final int frameHeight;
        private final int columns;
        private final int rows;
        private final int totalFrames;
        private final float defaultOriginX;
        private final float defaultOriginY;

        TextureId(String subPath, int imageWidth, int imageHeight,
                  int frameWidth, int frameHeight,
                  int columns, int rows, int totalFrames,
                  float defaultOriginX, float defaultOriginY) {
            this.subPath = subPath;
            this.imageWidth = imageWidth;
            this.imageHeight = imageHeight;
            this.frameWidth = frameWidth;
            this.frameHeight = frameHeight;
            this.columns = columns;
            this.rows = rows;
            this.totalFrames = totalFrames;
            this.defaultOriginX = defaultOriginX;
            this.defaultOriginY = defaultOriginY;
        }

        public String getSubPath() { return subPath; }
        public int getImageWidth() { return imageWidth; }
        public int getImageHeight() { return imageHeight; }
        public int getFrameWidth() { return frameWidth; }
        public int getFrameHeight() { return frameHeight; }
        public int getColumns() { return columns; }
        public int getRows() { return rows; }
        public int getTotalFrames() { return totalFrames; }
        public boolean isAnimated() { return totalFrames > 1; }
        public float getDefaultOriginX() { return defaultOriginX; }
        public float getDefaultOriginY() { return defaultOriginY; }
    }

    // -------------------------------------------------------------
    // Candidate Path Resolution
    // -------------------------------------------------------------
    /**
     * Resolves a file against standard candidate roots.
     * Mirrors C++ ResourceManager::candidatePaths.
     */
    public static FileHandle resolveFile(String subDir, String relativePath) {
        List<String> candidates = new ArrayList<>();
        String prefix = (subDir != null && !subDir.isEmpty()) ? (subDir + "/") : "";

        // 0. System property override if configured
        String sysProp = System.getProperty("spellstorm.assets.dir");
        if (sysProp != null && !sysProp.isBlank()) {
            candidates.add(new File(sysProp, prefix + relativePath).getPath());
            candidates.add(new File(sysProp, relativePath).getPath());
        }

        // 1. Direct path
        if (relativePath != null && (relativePath.startsWith("/") || relativePath.startsWith("assets/"))) {
            candidates.add(relativePath);
        }

        // 2. assets/<subDir>/<path>
        candidates.add("assets/" + prefix + relativePath);

        // 3. <subDir>/<path> (standard LibGDX internal assets root)
        candidates.add(prefix + relativePath);

        // 4. Fallback checking "images/" if looking for "textures/"
        if ("textures".equals(subDir)) {
            candidates.add("assets/images/" + relativePath);
            candidates.add("images/" + relativePath);
        }

        // 5. Parent fallbacks for test runner
        candidates.add("../assets/" + prefix + relativePath);
        candidates.add("../../assets/" + prefix + relativePath);

        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.exists()) {
                return Gdx.files != null ? Gdx.files.internal(candidate) : new FileHandle(f);
            }
        }

        // Return default attempt for clear LibGDX error message
        return Gdx.files != null ? Gdx.files.internal(prefix + relativePath) : new FileHandle(prefix + relativePath);
    }
}
