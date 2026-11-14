package com.spellstorm.core;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics.DisplayMode;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.InputProcessor;
import com.badlogic.gdx.Screen;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.spellstorm.assets.AssetRegistry.TextureId;
import com.spellstorm.assets.FontManager;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.CompleteSheetId;
import com.spellstorm.assets.TextureManager.ExtendedSheetId;
import com.spellstorm.assets.TextureManager.SpriteSheetId;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.StatusKind;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.WorldPowerUp;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.ui.CodexOverlay;
import com.spellstorm.ui.GameOverOverlay;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.ui.MinimapRenderer;
import com.spellstorm.ui.PauseMenuOverlay;
import com.spellstorm.ui.SettingsOverlay;
import com.spellstorm.ui.TitleScreen;
import com.spellstorm.ui.TutorialOverlay;
import com.spellstorm.ui.ViewportManager;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TileMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Primary gameplay screen orchestrating the interactive rendering pipeline,
 * FitViewport scaling/unprojection, shell screens, overlays, and desktop input.
 */
public class GameScreen implements Screen, InputProcessor {

    private enum SettingsReturnTarget {
        TITLE,
        PAUSE
    }

    private final SpellStormGame game;
    private final GameSession session;
    private final TextureManager textureManager;
    private final FontManager fontManager;

    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private final ViewportManager viewportManager = new ViewportManager();
    private final HudRenderer hudRenderer = new HudRenderer();
    private final MinimapRenderer minimapRenderer = new MinimapRenderer();

    private final TitleScreen titleScreen = new TitleScreen();
    private final PauseMenuOverlay pauseMenuOverlay = new PauseMenuOverlay();
    private final SettingsOverlay settingsOverlay = new SettingsOverlay();
    private final CodexOverlay codexOverlay = new CodexOverlay();
    private final TutorialOverlay tutorialOverlay = new TutorialOverlay();
    private SettingsReturnTarget settingsReturnTarget = SettingsReturnTarget.TITLE;
    private int windowedWidth = EngineConfig.DEFAULT_WIDTH;
    private int windowedHeight = EngineConfig.DEFAULT_HEIGHT;

    private final List<Vector2D> enemyPositionsBuffer = new ArrayList<>();
    private final List<Vector2D> bossPositionsBuffer = new ArrayList<>();
    private final List<Vector2D> powerUpPositionsBuffer = new ArrayList<>();
    private final GlyphLayout glyphLayout = new GlyphLayout();

    /** Unified renderable kinds participating in the Y-depth sorted world pass. */
    public enum RenderableKind {
        OBSTACLE,
        ENEMY,
        PLAYER
    }

    /** Comparable Y-depth render item anchored at its ground contact Y coordinate. */
    public static final class DepthSortedEntry {
        private final RenderableKind kind;
        private final float anchorY;
        private final int tieOrder;
        private final Obstacle obstacle;
        private final Enemy enemy;

        private DepthSortedEntry(RenderableKind kind, float anchorY, int tieOrder,
                                 Obstacle obstacle, Enemy enemy) {
            this.kind = kind;
            this.anchorY = anchorY;
            this.tieOrder = tieOrder;
            this.obstacle = obstacle;
            this.enemy = enemy;
        }

        public RenderableKind getKind() { return kind; }
        public float getAnchorY() { return anchorY; }
        public Obstacle getObstacle() { return obstacle; }
        public Enemy getEnemy() { return enemy; }
    }

    public static final Comparator<DepthSortedEntry> Y_DEPTH_COMPARATOR = (a, b) -> {
        int cmp = Float.compare(a.anchorY, b.anchorY);
        return (cmp != 0) ? cmp : Integer.compare(a.tieOrder, b.tieOrder);
    };

    public static float getObstacleDepthAnchorY(Obstacle obstacle) {
        return obstacle != null ? obstacle.getPosition().y : 0.0f;
    }

    public static float getEnemyDepthAnchorY(Enemy enemy) {
        return enemy != null ? (enemy.getPosition().y + enemy.getRadius() * 0.85f) : 0.0f;
    }

    public static float getPlayerDepthAnchorY(Player player) {
        return player != null ? (player.getPosition().y + player.getRadius() * 0.85f) : 0.0f;
    }

    /**
     * Collects and sorts visible obstacles, living enemies, and the player by their ground
     * Y anchor coordinate so actors walk behind tree canopies and in front of trunks.
     */
    public static List<DepthSortedEntry> buildYDepthSortedEntries(
            List<Obstacle> obstacles, List<Enemy> enemies, Player player, GameCamera camera) {
        List<DepthSortedEntry> entries = new ArrayList<>();
        int tie = 0;
        if (obstacles != null) {
            for (Obstacle obs : obstacles) {
                if (obs == null) continue;
                if (camera != null && !camera.isVisible(obs.getVisualBounds())) {
                    continue;
                }
                entries.add(new DepthSortedEntry(RenderableKind.OBSTACLE, getObstacleDepthAnchorY(obs), tie++, obs, null));
            }
        }
        if (enemies != null) {
            for (Enemy enemy : enemies) {
                if (enemy == null || !enemy.isAlive()) continue;
                entries.add(new DepthSortedEntry(RenderableKind.ENEMY, getEnemyDepthAnchorY(enemy), tie++, null, enemy));
            }
        }
        if (player != null) {
            entries.add(new DepthSortedEntry(RenderableKind.PLAYER, getPlayerDepthAnchorY(player), tie++, null, null));
        }
        entries.sort(Y_DEPTH_COMPARATOR);
        return entries;
    }

    private static final class VisualEffectInstance {
        final SpriteSheetId sheetId;
        final Vector2D position;
        final float size;
        final float angleDeg;
        final float duration;
        final Color tint;
        float elapsed = 0.0f;

        VisualEffectInstance(SpriteSheetId sheetId, Vector2D position, float size,
                             float angleDeg, float duration, Color tint) {
            this.sheetId = sheetId;
            this.position = position.cpy();
            this.size = size;
            this.angleDeg = angleDeg;
            this.duration = duration;
            this.tint = tint != null ? new Color(tint) : new Color(Color.WHITE);
        }
    }

    private static final class TrackedProjectile {
        final Vector2D lastPosition = new Vector2D();
        final TextureId projectileTexId;
        final boolean playerFaction;
        float lastLifetime;

        TrackedProjectile(Vector2D pos, float lifetime, TextureId projectileTexId, boolean playerFaction) {
            this.lastPosition.set(pos);
            this.lastLifetime = lifetime;
            this.projectileTexId = projectileTexId;
            this.playerFaction = playerFaction;
        }
    }

    private final Map<Long, TrackedProjectile> trackedProjectiles = new LinkedHashMap<>();
    private final List<VisualEffectInstance> activeVisualEffects = new ArrayList<>();
    private float lastVfxMatchElapsed = 0.0f;
    private float renderAnimElapsed = 0.0f;
    private float playerDeathAnimTimer = 0.0f;

    public GameScreen(SpellStormGame game, GameSession session,
                      TextureManager textureManager, FontManager fontManager) {
        this.game = game;
        this.session = session != null ? session : new GameSession();
        this.textureManager = textureManager;
        this.fontManager = fontManager;
        this.hudRenderer.setTextureManager(textureManager);

        if (Gdx.graphics != null && Gdx.graphics.getWidth() > 0 && Gdx.graphics.getHeight() > 0) {
            viewportManager.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        } else {
            viewportManager.update((int) ViewportManager.VIRTUAL_WIDTH, (int) ViewportManager.VIRTUAL_HEIGHT);
        }
    }

    @Override
    public void show() {
        if (game != null) {
            this.batch = game.getBatch();
            this.shapeRenderer = game.getShapeRenderer();
        }
        if (Gdx.graphics != null && Gdx.graphics.getWidth() > 0 && Gdx.graphics.getHeight() > 0) {
            viewportManager.update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            settingsOverlay.setFullscreen(Gdx.graphics.isFullscreen());
        }
        if (Gdx.input != null) {
            Gdx.input.setInputProcessor(this);
        }
    }

    @Override
    public void render(float delta) {
        float dt = Math.min(delta, 0.1f);

        // 1. Clear full window black so letterbox/pillarbox bars remain clean black
        viewportManager.beginFrameClear();

        // 2. Coordinate unprojection through FitViewport
        float screenX = Gdx.input != null ? Gdx.input.getX() : viewportManager.getScreenWidth() * 0.5f;
        float screenY = Gdx.input != null ? Gdx.input.getY() : viewportManager.getScreenHeight() * 0.5f;
        Vector2D uiMouse = viewportManager.screenToUi(screenX, screenY);
        GameCamera camera = session.getCamera();
        Vector2D worldMouse = viewportManager.screenToWorld(camera, screenX, screenY);

        // 3. Synchronize modal overlay states with session
        session.setExternalOverlayBlocking(codexOverlay.isVisible() || settingsOverlay.isOpen());
        if (session.isPaused() && !settingsOverlay.isOpen() && !pauseMenuOverlay.isOpen()) {
            pauseMenuOverlay.open();
        } else if (!session.isPaused() && pauseMenuOverlay.isOpen()) {
            pauseMenuOverlay.close();
        }

        // Hover tracking for shell overlays
        if (settingsOverlay.isOpen()) {
            settingsOverlay.handleMouseMove(uiMouse);
        } else if (session.isStartScreen()) {
            titleScreen.handleMouseMove(uiMouse);
        } else if (session.isPaused()) {
            pauseMenuOverlay.handleMouseMove(uiMouse);
        }

        // 4. Continuous input handling during active gameplay
        if (session.isPlaying() && Gdx.input != null && Gdx.input.isButtonPressed(Buttons.LEFT)) {
            session.castSpell(worldMouse);
        }

        // 5. Update tutorial banner only during active play
        if (session.isPlaying()) {
            tutorialOverlay.update(dt);
        }

        // 6. Update simulation session
        boolean autoplay = game != null && game.getConfig() != null && game.getConfig().isAutoplay();
        session.update(dt, worldMouse, uiMouse, autoplay);

        renderAnimElapsed += dt;

        if (session.isPlaying()) {
            updateVisualEffects(dt);
        } else if (session.isStartScreen()) {
            trackedProjectiles.clear();
            activeVisualEffects.clear();
            lastVfxMatchElapsed = 0.0f;
            playerDeathAnimTimer = 0.0f;
        }

        if (!session.isStartScreen()) {
            Player player = session.getPlayer();
            if (!player.isAlive() || player.getHealth() <= 0.0f) {
                playerDeathAnimTimer += dt;
            } else {
                playerDeathAnimTimer = 0.0f;
            }
        }

        // 7. World Space Render Pass
        OrthographicCamera gdxCam = camera.getOrthographicCamera();
        viewportManager.applyWorldViewport(gdxCam);
        renderWorldPass(camera, gdxCam);

        // 8. Screen Space UI Render Pass (fixed 1280x720 virtual UI)
        viewportManager.applyUiViewport();
        renderUiPass(uiMouse);
    }

    // =========================================================================
    // World Space Render Pass
    // =========================================================================

    private void renderWorldPass(GameCamera camera, OrthographicCamera gdxCam) {
        if (batch == null || shapeRenderer == null || textureManager == null || Gdx.gl == null) {
            return;
        }

        batch.setProjectionMatrix(gdxCam.combined);
        shapeRenderer.setProjectionMatrix(gdxCam.combined);

        enableAlphaBlend();

        // A. Terrain Tiles
        batch.begin();
        session.getTileMap().drawTerrain(batch, textureManager, camera, renderAnimElapsed);
        batch.end();

        // B. Ground Shadows, Pickup Halos & Subtle Projectile Under-Glows
        enableAlphaBlend();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderGroundShadows(shapeRenderer, camera);
        renderProjectileUnderGlows(shapeRenderer);
        shapeRenderer.end();

        // C. Pickups, Unified Y-Depth Sorted Obstacles & Actors, Active Projectiles & Catalyst Orb
        enableAlphaBlend();
        batch.begin();
        renderPickups(batch);
        renderYDepthSortedWorld(batch, camera);
        renderProjectiles(batch);
        renderCatalystOrb(batch);
        batch.end();

        // C2. Additive Glows (Projectiles, Catalyst Halo, Campfire Floor Glow) & Active Cast/Impact VFX Pass
        batch.begin();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE);
        renderProjectileBloom(batch);
        renderCatalystOrbHalo(batch);
        renderCampfireFloorGlow(batch, camera);
        renderVisualEffects(batch);
        batch.flush();
        batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        batch.end();
        enableAlphaBlend();

        // D. World Overlay Shapes
        enableAlphaBlend();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        renderWorldOverlayShapes(shapeRenderer);
        shapeRenderer.end();

        enableAlphaBlend();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        renderWorldOverlayLines(shapeRenderer);
        shapeRenderer.end();
    }

    private void enableAlphaBlend() {
        if (Gdx.gl != null) {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFuncSeparate(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA,
                    GL20.GL_ONE, GL20.GL_ONE_MINUS_SRC_ALPHA);
        }
    }

    private void renderGroundShadows(ShapeRenderer sr, GameCamera camera) {
        sr.setColor(0.0f, 0.0f, 0.0f, 0.26f);

        for (Obstacle obs : session.getTileMap().getObstacles()) {
            if (camera != null && !camera.isVisible(obs.getVisualBounds())) {
                continue;
            }
            renderPropGroundShadow(sr, obs);
        }

        sr.setColor(0.0f, 0.0f, 0.0f, 0.28f);

        for (ExperiencePickup p : session.getExperienceSystem().getPickups()) {
            if (p.isAlive()) {
                Vector2D pos = p.getPosition();
                float r = p.getRadius();
                sr.ellipse(pos.x - r * 0.85f, pos.y + r * 0.45f, r * 1.7f, r * 0.7f);
            }
        }

        for (WorldPowerUp wp : session.getPowerUpManager().getPickups()) {
            if (wp.isAlive()) {
                Vector2D pos = wp.getPosition();
                float r = wp.radius;
                sr.ellipse(pos.x - r * 0.75f, pos.y + r * 0.50f, r * 1.5f, r * 0.58f);
            }
        }

        for (Enemy e : session.getEnemies()) {
            if (e.isAlive()) {
                Vector2D pos = e.getPosition();
                float r = e.getRadius();
                sr.ellipse(pos.x - r * 0.85f, pos.y + r * 0.45f, r * 1.7f, r * 0.65f);
            }
        }

        Player player = session.getPlayer();
        if (player.isAlive()) {
            Vector2D pos = player.getPosition();
            float r = player.getRadius();
            sr.ellipse(pos.x - r * 0.85f, pos.y + r * 0.45f, r * 1.7f, r * 0.65f);
        }
    }

    private static void renderPropGroundShadow(ShapeRenderer sr, Obstacle obs) {
        float w = obs.getVisualBounds().width;
        Vector2D pos = obs.getPosition();
        switch (obs.getType()) {
            case TREE_OAK -> sr.ellipse(pos.x - w * 0.46f, pos.y - 5.0f, w * 0.92f, 10.0f);
            case TREE_PINE -> sr.ellipse(pos.x - w * 0.45f, pos.y - 4.5f, w * 0.90f, 9.0f);
            case ROCK -> sr.ellipse(pos.x - w * 0.42f, pos.y - 3.5f, w * 0.84f, 7.0f);
            case ROCK_LARGE -> sr.ellipse(pos.x - w * 0.45f, pos.y - 4.5f, w * 0.90f, 9.0f);
            case BUSH -> sr.ellipse(pos.x - w * 0.42f, pos.y - 3.2f, w * 0.84f, 6.5f);
            case LOG -> sr.ellipse(pos.x - w * 0.45f, pos.y - 3.2f, w * 0.90f, 6.5f);
            case RUINS -> sr.ellipse(pos.x - w * 0.47f, pos.y - 5.0f, w * 0.94f, 10.5f);
            case TENT -> sr.ellipse(pos.x - w * 0.47f, pos.y - 5.0f, w * 0.94f, 10.5f);
            case CAMPFIRE -> { /* Warm campfire glow rendered in additive C2 pass */ }
        }
    }

    private void renderYDepthSortedWorld(SpriteBatch b, GameCamera camera) {
        TileMap tileMap = session.getTileMap();
        List<DepthSortedEntry> entries = buildYDepthSortedEntries(
                tileMap.getObstacles(), session.getEnemies(), session.getPlayer(), camera);
        for (DepthSortedEntry entry : entries) {
            switch (entry.getKind()) {
                case OBSTACLE -> tileMap.drawDecoration(b, textureManager, entry.getObstacle(), renderAnimElapsed);
                case ENEMY -> renderSingleEnemy(b, entry.getEnemy());
                case PLAYER -> renderPlayer(b);
            }
        }
    }

    private void renderPickups(SpriteBatch b) {
        boolean hasGemSheet = textureManager.hasExtendedSheet(CompleteSheetId.GEMS_ANIM);
        b.setColor(Color.WHITE);
        for (ExperiencePickup p : session.getExperienceSystem().getPickups()) {
            if (!p.isAlive()) continue;
            Vector2D pos = p.getPosition();
            float bob = (float) Math.sin(p.getAnimTimer() * 5.0f) * 3.0f;
            float r = p.getRadius();
            float diam = r * 2.2f;

            TextureRegion region = null;
            if (hasGemSheet) {
                int baseFrame = switch (p.getTextureId()) {
                    case GEM_EMERALD -> 4;
                    case GEM_GOLD -> 8;
                    default -> 0;
                };
                int shimmer = ((int) (p.getAnimTimer() * 8.0f)) % 4;
                region = textureManager.getExtendedFrame(CompleteSheetId.GEMS_ANIM, baseFrame + shimmer);
            }
            if (region == null) {
                region = textureManager.getRegion(p.getTextureId());
            }
            if (region != null) {
                drawRegion(b, region, pos.x - diam * 0.5f, pos.y + bob - diam * 0.5f, diam, diam);
            }
        }

        boolean hasPowerUpIcons = textureManager.hasExtendedSheet(CompleteSheetId.POWERUP_ICONS);
        for (WorldPowerUp wp : session.getPowerUpManager().getPickups()) {
            if (!wp.isAlive()) continue;
            Vector2D pos = wp.getPosition();
            float bob = (float) Math.sin(wp.animTimer * 4.0f) * 2.5f;

            if (hasPowerUpIcons) {
                float iconSize = wp.radius * 1.48f;
                int iconIdx = HudRenderer.getPowerUpIconFrameIndex(wp.type);
                TextureRegion region = textureManager.getExtendedFrame(CompleteSheetId.POWERUP_ICONS, iconIdx);
                if (region != null) {
                    b.setColor(1.0f, 1.0f, 1.0f, wp.getAlphaFactor());
                    drawRegion(b, region, pos.x - iconSize * 0.5f, pos.y + bob - iconSize * 0.5f,
                            iconSize, iconSize);
                    b.setColor(Color.WHITE);
                }
            } else {
                float diam = wp.radius * 2.0f;
                TextureRegion region = textureManager.getRegion(TextureId.GEM_GOLD);
                if (region != null) {
                    b.setColor(getPowerUpColor(wp.type));
                    drawRegion(b, region, pos.x - diam * 0.5f, pos.y + bob - diam * 0.5f, diam, diam);
                    b.setColor(Color.WHITE);
                }
            }
        }
    }

    private Color getPowerUpColor(PowerUpType type) {
        if (type == null) return Color.GOLD;
        return switch (type) {
            case ARCANE_OVERDRIVE -> new Color(175f / 255f, 75f / 255f, 255f / 255f, 1.0f);
            case PIERCING_SURGE -> new Color(35f / 255f, 220f / 255f, 255f / 255f, 1.0f);
            case ELEMENTAL_BURST -> new Color(255f / 255f, 135f / 255f, 30f / 255f, 1.0f);
        };
    }

    private static SpriteSheetId resolveEnemySheetId(EnemyType type) {
        if (type == null) return null;
        return switch (type) {
            case MELEE -> ExtendedSheetId.MELEE_FIEND_ANIM;
            case RANGED -> CompleteSheetId.RANGED_CULTIST_ANIM;
            case TANK -> CompleteSheetId.TANK_GOLEM_ANIM;
            case BOSS -> CompleteSheetId.BOSS_COLOSSUS_ANIM;
        };
    }

    private int selectEnemyAnimFrame(Enemy e) {
        if (e.getType() == EnemyType.RANGED && e instanceof RangedEnemy re) {
            if (re.isTelegraphingCast()) return 6;
            if (re.isReleasingCast()) return 7;
        } else if (e.getType() == EnemyType.BOSS && e instanceof BossEnemy boss) {
            if (boss.getVolleyTimer() > 0.0f && boss.getVolleyTimer() <= 0.45f) return 6;
            if (boss.getVolleyTimer() > Math.max(0.0f, boss.getVolleyCooldown() - 0.30f)) return 7;
        }

        if (e.getAttackTimer() > e.getAttackCooldown() * 0.55f) {
            return (e.getAttackTimer() > e.getAttackCooldown() * 0.78f) ? 6 : 7;
        }

        if (e.getType() == EnemyType.TANK && e.getAttackTimer() <= 0.1f) {
            Player player = session.getPlayer();
            if (player.isAlive()
                    && e.getPosition().dst(player.getPosition()) < e.getRadius() + player.getRadius() + 24.0f) {
                return 6;
            }
        }

        if (e.getVelocity().len2() > 1.0f) {
            float speedScale = switch (e.getType()) {
                case MELEE -> 10.0f;
                case RANGED -> 8.0f;
                case TANK -> 6.0f;
                case BOSS -> 5.5f;
            };
            return 2 + (((int) (e.getAnimTimer() * speedScale)) % 4);
        }
        return ((int) (e.getAnimTimer() * 3.2f)) % 2;
    }

    private void renderSingleEnemy(SpriteBatch b, Enemy e) {
        if (e == null || !e.isAlive()) return;
        Vector2D pos = e.getPosition();
        float r = e.getRadius();
        float diam = (e.getType() == EnemyType.BOSS) ? (r * 2.5f) : (r * 2.4f);

        TextureRegion region = null;
        SpriteSheetId sheetId = resolveEnemySheetId(e.getType());
        if (sheetId != null && textureManager.hasSheet(sheetId)) {
            region = textureManager.getSheetFrame(sheetId, selectEnemyAnimFrame(e));
        }

        if (region == null) {
            TextureId texId = switch (e.getType()) {
                case MELEE -> TextureId.MELEE_ENEMY;
                case RANGED -> TextureId.RANGED_ENEMY;
                case TANK -> TextureId.TANK_ENEMY;
                case BOSS -> TextureId.BOSS_ENEMY;
            };
            region = textureManager.getRegion(texId);
        }

        if (region != null) {
            if (e.isHitFlashing()) {
                b.setColor(1.0f, 0.4f, 0.4f, 1.0f);
            } else if (e.getStatuses().has(StatusKind.BURN)) {
                b.setColor(1.0f, 0.6f, 0.3f, 1.0f);
            } else if (e.getStatuses().has(StatusKind.SLOW)) {
                b.setColor(0.5f, 0.8f, 1.0f, 1.0f);
            } else {
                b.setColor(Color.WHITE);
            }

            // Upright enemy presentation: rotation = 0, flip horizontally on facingLeft
            drawRegion(b, region, pos.x - diam * 0.5f, pos.y - diam * 0.5f,
                    diam * 0.5f, diam * 0.5f, diam, diam, 1.0f, 1.0f, 0.0f, e.isFacingLeft());
            b.setColor(Color.WHITE);
        }
    }

    private void renderPlayer(SpriteBatch b) {
        Player player = session.getPlayer();
        Vector2D pos = player.getPosition();
        float r = player.getRadius();
        float visualDiam = r * 2.35f;

        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            TextureRegion deathFrame = null;
            if (textureManager.hasExtendedSheet(CompleteSheetId.PLAYER_HURT_DEATH_ANIM)) {
                int idx = 2 + Math.min(3, (int) (playerDeathAnimTimer / 0.18f));
                deathFrame = textureManager.getExtendedFrame(CompleteSheetId.PLAYER_HURT_DEATH_ANIM, idx);
            }
            if (deathFrame == null) {
                deathFrame = textureManager.getFrame(TextureId.PLAYER, 0);
            }
            if (deathFrame != null) {
                b.setColor(Color.WHITE);
                drawRegion(b, deathFrame, pos.x - visualDiam * 0.5f, pos.y - visualDiam * 0.5f,
                        visualDiam, visualDiam);
            }
            return;
        }

        TextureRegion frameRegion = null;
        if (player.isHitFlashing()
                && textureManager.hasExtendedSheet(CompleteSheetId.PLAYER_HURT_DEATH_ANIM)) {
            int hurtFrame = (player.getHitFlashTimer() > 0.06f) ? 0 : 1;
            frameRegion = textureManager.getExtendedFrame(CompleteSheetId.PLAYER_HURT_DEATH_ANIM, hurtFrame);
        }

        if (frameRegion == null) {
            ExtendedSheetId extSheet = switch (player.getFacingDirection()) {
                case DOWN -> ExtendedSheetId.PLAYER_DOWN_ANIM;
                case UP -> ExtendedSheetId.PLAYER_UP_ANIM;
                case LEFT -> ExtendedSheetId.PLAYER_LEFT_ANIM;
                case RIGHT -> ExtendedSheetId.PLAYER_RIGHT_ANIM;
            };

            if (textureManager.hasExtendedSheet(extSheet)) {
                int frameIdx;
                float baseCd = player.getBaseCastCooldown();
                if (player.getCastTimer() > baseCd * 0.45f) {
                    frameIdx = (player.getCastTimer() > baseCd * 0.72f) ? 6 : 7;
                } else if (player.getVelocity().len2() > 1.0f) {
                    frameIdx = 2 + (((int) (player.getAnimTimer() * 1.25f)) % 4);
                } else {
                    frameIdx = ((int) (player.getIdleTimer() * 3.0f)) % 2;
                }
                frameRegion = textureManager.getExtendedFrame(extSheet, frameIdx);
            }
        }

        if (frameRegion == null) {
            TextureId activeTexId = switch (player.getFacingDirection()) {
                case DOWN -> TextureId.PLAYER_DOWN;
                case UP -> TextureId.PLAYER_UP;
                case LEFT -> TextureId.PLAYER_LEFT;
                case RIGHT -> TextureId.PLAYER_RIGHT;
            };

            int frame = player.getAnimationFrame();
            frameRegion = textureManager.getFrame(activeTexId, frame);
            if (frameRegion == null) {
                frameRegion = textureManager.getFrame(TextureId.PLAYER, frame);
            }
        }

        if (frameRegion != null) {
            if (player.isHitFlashing()) {
                b.setColor(1.0f, 0.72f, 0.72f, 1.0f);
            } else {
                b.setColor(Color.WHITE);
            }
            drawRegion(b, frameRegion, pos.x - visualDiam * 0.5f, pos.y - visualDiam * 0.5f, visualDiam, visualDiam);
            b.setColor(Color.WHITE);
        }
    }

    private TextureId resolveProjectileTextureId(Projectile p) {
        if (p.getFaction() == ProjectileFaction.ENEMY) {
            return TextureId.PROJECTILE_ENEMY;
        }
        if (p.getSpell() != null) {
            var elements = p.getSpell().getElements();
            if (elements.contains(Element.FIRE)) return TextureId.PROJECTILE_FIRE;
            if (elements.contains(Element.ICE)) return TextureId.PROJECTILE_ICE;
            if (elements.contains(Element.LIGHTNING)) return TextureId.PROJECTILE_LIGHTNING;
            if (elements.contains(Element.WIND)) return TextureId.PROJECTILE_WIND;
            if (elements.contains(Element.EARTH)) return TextureId.PROJECTILE_EARTH;
            return TextureId.PROJECTILE_PLAYER;
        }
        return TextureId.PROJECTILE_PLAYER;
    }

    private static SpriteSheetId resolveProjectileSheetId(TextureId texId) {
        if (texId == null) return CompleteSheetId.PROJECTILE_PLAYER_ANIM;
        return switch (texId) {
            case PROJECTILE_FIRE -> ExtendedSheetId.SPELL_FIRE_ANIM;
            case PROJECTILE_ICE -> CompleteSheetId.SPELL_ICE_ANIM;
            case PROJECTILE_LIGHTNING -> CompleteSheetId.SPELL_LIGHTNING_ANIM;
            case PROJECTILE_WIND -> CompleteSheetId.SPELL_WIND_ANIM;
            case PROJECTILE_EARTH -> CompleteSheetId.SPELL_EARTH_ANIM;
            case PROJECTILE_ENEMY -> CompleteSheetId.PROJECTILE_ENEMY_ANIM;
            default -> CompleteSheetId.PROJECTILE_PLAYER_ANIM;
        };
    }

    private static SpriteSheetId resolveImpactVfxSheetId(TextureId texId) {
        if (texId == null) return CompleteSheetId.VFX_ARCANE_IMPACT;
        return switch (texId) {
            case PROJECTILE_FIRE -> ExtendedSheetId.VFX_FIRE_IMPACT;
            case PROJECTILE_ICE -> CompleteSheetId.VFX_ICE_IMPACT;
            case PROJECTILE_LIGHTNING -> CompleteSheetId.VFX_LIGHTNING_IMPACT;
            case PROJECTILE_WIND -> CompleteSheetId.VFX_WIND_IMPACT;
            case PROJECTILE_EARTH -> CompleteSheetId.VFX_EARTH_IMPACT;
            default -> CompleteSheetId.VFX_ARCANE_IMPACT;
        };
    }

    private static Color resolveProjectileBloomColor(TextureId texId) {
        if (texId == null) return new Color(0.60f, 0.80f, 1.0f, 0.36f);
        return switch (texId) {
            case PROJECTILE_FIRE -> new Color(1.0f, 0.65f, 0.25f, 0.40f);
            case PROJECTILE_ICE -> new Color(0.45f, 0.88f, 1.0f, 0.36f);
            case PROJECTILE_LIGHTNING -> new Color(0.75f, 0.92f, 1.0f, 0.42f);
            case PROJECTILE_WIND -> new Color(0.45f, 1.0f, 0.68f, 0.34f);
            case PROJECTILE_EARTH -> new Color(0.95f, 0.65f, 0.30f, 0.26f);
            case PROJECTILE_ENEMY -> new Color(0.88f, 0.38f, 1.0f, 0.34f);
            default -> new Color(0.60f, 0.80f, 1.0f, 0.36f);
        };
    }

    private TextureRegion resolveProjectileFrame(Projectile p, TextureId texId) {
        SpriteSheetId sheetId = resolveProjectileSheetId(texId);
        if (sheetId != null && textureManager.hasSheet(sheetId)) {
            int frameIdx = ((int) (p.getAnimTimer() * 14.0f)) % sheetId.getTotalFrames();
            return textureManager.getSheetFrame(sheetId, frameIdx);
        }
        return textureManager.getRegion(texId);
    }

    private void renderProjectiles(SpriteBatch b) {
        b.setColor(Color.WHITE);
        for (Projectile p : session.getCombatSystem().getProjectiles()) {
            if (!p.isAlive()) continue;

            Vector2D pos = p.getPosition();
            float r = p.getRadius();
            float diam = r * 2.2f;

            TextureId texId = resolveProjectileTextureId(p);
            TextureRegion region = resolveProjectileFrame(p, texId);
            if (region != null) {
                float angleDeg = p.getDirection().angleDeg();
                drawRegion(b, region, pos.x - diam * 0.5f, pos.y - diam * 0.5f,
                        diam * 0.5f, diam * 0.5f, diam, diam, 1.0f, 1.0f, angleDeg, false);
            }
        }
    }

    private void renderProjectileBloom(SpriteBatch b) {
        for (Projectile p : session.getCombatSystem().getProjectiles()) {
            if (!p.isAlive()) continue;
            TextureId texId = resolveProjectileTextureId(p);
            TextureRegion region = resolveProjectileFrame(p, texId);
            if (region == null) continue;

            Vector2D pos = p.getPosition();
            float diam = p.getRadius() * 2.2f * 1.3f;
            float angleDeg = p.getDirection().angleDeg();
            Color bloom = resolveProjectileBloomColor(texId);
            b.setColor(bloom);
            drawRegion(b, region, pos.x - diam * 0.5f, pos.y - diam * 0.5f,
                    diam * 0.5f, diam * 0.5f, diam, diam, 1.0f, 1.0f, angleDeg, false);
        }
        b.setColor(Color.WHITE);
    }

    private Vector2D getCatalystOrbPosition(Player player) {
        Vector2D pos = player.getPosition();
        float r = player.getRadius();
        Vector2D aim = player.getAimDirection();
        return pos.cpy().mulAdd(aim, r + 11.0f);
    }

    private Color getCatalystOrbColor() {
        return session.getCurrentSpell() != null
                ? session.getCurrentSpell().getPrimaryColor()
                : Color.GOLD;
    }

    private void renderCatalystOrb(SpriteBatch b) {
        Player player = session.getPlayer();
        if (!player.isAlive() || !textureManager.hasExtendedSheet(CompleteSheetId.CATALYST_ORB_ANIM)) {
            return;
        }
        Vector2D orbPos = getCatalystOrbPosition(player);
        Color orbColor = getCatalystOrbColor();
        int orbFrame = ((int) (renderAnimElapsed * 10.0f)) % 6;
        TextureRegion orbReg = textureManager.getExtendedFrame(CompleteSheetId.CATALYST_ORB_ANIM, orbFrame);
        if (orbReg != null) {
            float orbSize = 20.0f;
            b.setColor(orbColor.r, orbColor.g, orbColor.b, 1.0f);
            drawRegion(b, orbReg, orbPos.x - orbSize * 0.5f, orbPos.y - orbSize * 0.5f, orbSize, orbSize);
            b.setColor(Color.WHITE);
        }
    }

    private void renderCatalystOrbHalo(SpriteBatch b) {
        Player player = session.getPlayer();
        if (!player.isAlive() || !textureManager.hasExtendedSheet(CompleteSheetId.CATALYST_ORB_ANIM)) {
            return;
        }
        Vector2D orbPos = getCatalystOrbPosition(player);
        Color orbColor = getCatalystOrbColor();
        int orbFrame = ((int) (renderAnimElapsed * 10.0f)) % 6;
        TextureRegion orbReg = textureManager.getExtendedFrame(CompleteSheetId.CATALYST_ORB_ANIM, orbFrame);
        if (orbReg != null) {
            float pulse = 1.0f + 0.14f * (float) Math.sin(renderAnimElapsed * 5.0f);
            float haloSize = 26.0f * pulse;
            b.setColor(orbColor.r, orbColor.g, orbColor.b, 0.40f);
            drawRegion(b, orbReg, orbPos.x - haloSize * 0.5f, orbPos.y - haloSize * 0.5f, haloSize, haloSize);
            b.setColor(Color.WHITE);
        }
    }

    private void renderCampfireFloorGlow(SpriteBatch b, GameCamera camera) {
        if (!textureManager.hasExtendedSheet(ExtendedSheetId.VFX_FIRE_IMPACT)) {
            return;
        }
        for (Obstacle obs : session.getTileMap().getObstacles()) {
            if (obs.getType() != DecoType.CAMPFIRE) {
                continue;
            }
            if (camera != null && !camera.isVisible(obs.getVisualBounds())) {
                continue;
            }
            Vector2D pos = obs.getPosition();
            int glowFrame = 1 + (((int) (renderAnimElapsed * 6.0f + pos.x * 0.05f)) % 2);
            TextureRegion region = textureManager.getExtendedFrame(ExtendedSheetId.VFX_FIRE_IMPACT, glowFrame);
            if (region == null) {
                continue;
            }
            float flickerAlpha = 0.29f + 0.05f * (float) Math.sin(renderAnimElapsed * 9.5f + pos.x * 0.1f);
            float glowSize = 60.0f + 4.0f * (float) Math.sin(renderAnimElapsed * 7.0f + pos.y * 0.1f);
            float centerX = pos.x;
            float centerY = pos.y - 6.0f;
            b.setColor(1.0f, 0.58f, 0.22f, flickerAlpha);
            drawRegion(b, region, centerX - glowSize * 0.5f, centerY - glowSize * 0.5f, glowSize, glowSize);
        }
        b.setColor(Color.WHITE);
    }

    private void updateVisualEffects(float dt) {
        float elapsed = session.getMatchElapsed();
        if (elapsed < lastVfxMatchElapsed) {
            trackedProjectiles.clear();
            activeVisualEffects.clear();
        }
        lastVfxMatchElapsed = elapsed;

        for (int i = activeVisualEffects.size() - 1; i >= 0; i--) {
            VisualEffectInstance vfx = activeVisualEffects.get(i);
            vfx.elapsed += dt;
            if (vfx.elapsed >= vfx.duration) {
                activeVisualEffects.remove(i);
            }
        }

        Set<Long> aliveIds = new HashSet<>();
        Player player = session.getPlayer();
        for (Projectile p : session.getCombatSystem().getProjectiles()) {
            if (!p.isAlive()) {
                continue;
            }
            long id = p.getId();
            aliveIds.add(id);
            TextureId texId = resolveProjectileTextureId(p);
            TrackedProjectile tracked = trackedProjectiles.get(id);
            if (tracked == null) {
                boolean isPlayerProj = p.getFaction() == ProjectileFaction.PLAYER;
                if (isPlayerProj && texId == TextureId.PROJECTILE_FIRE) {
                    Vector2D dir = p.getDirection();
                    float angleDeg = dir.angleDeg();
                    Vector2D muzzlePos = player.getPosition().cpy().mulAdd(dir, player.getRadius() + 8.0f);
                    activeVisualEffects.add(new VisualEffectInstance(
                            ExtendedSheetId.VFX_FIRE_CAST, muzzlePos, 28.0f, angleDeg, 0.20f, Color.WHITE));
                }
                trackedProjectiles.put(id, new TrackedProjectile(p.getPosition(), p.getLifetime(), texId, isPlayerProj));
            } else {
                tracked.lastPosition.set(p.getPosition());
                tracked.lastLifetime = p.getLifetime();
            }
        }

        Iterator<Map.Entry<Long, TrackedProjectile>> it = trackedProjectiles.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, TrackedProjectile> entry = it.next();
            if (!aliveIds.contains(entry.getKey())) {
                TrackedProjectile removed = entry.getValue();
                it.remove();
                if (removed.lastLifetime > dt + 0.02f) {
                    SpriteSheetId impactSheet = resolveImpactVfxSheetId(removed.projectileTexId);
                    float impactSize = removed.playerFaction ? 48.0f : 34.0f;
                    Color tint = (removed.projectileTexId == TextureId.PROJECTILE_ENEMY)
                            ? new Color(0.92f, 0.42f, 1.0f, 0.90f)
                            : Color.WHITE;
                    activeVisualEffects.add(new VisualEffectInstance(
                            impactSheet, removed.lastPosition, impactSize, 0.0f, 0.36f, tint));
                }
            }
        }
    }

    private void renderVisualEffects(SpriteBatch b) {
        for (VisualEffectInstance vfx : activeVisualEffects) {
            if (!textureManager.hasSheet(vfx.sheetId)) {
                continue;
            }
            int totalFrames = vfx.sheetId.getTotalFrames();
            float progress = MathUtils.clamp(vfx.elapsed / Math.max(0.001f, vfx.duration), 0.0f, 0.9999f);
            int frameIdx = Math.min(totalFrames - 1, (int) (progress * totalFrames));
            TextureRegion region = textureManager.getSheetFrame(vfx.sheetId, frameIdx);
            if (region == null) {
                continue;
            }
            b.setColor(vfx.tint);
            float half = vfx.size * 0.5f;
            drawRegion(b, region, vfx.position.x - half, vfx.position.y - half,
                    half, half, vfx.size, vfx.size, 1.0f, 1.0f, vfx.angleDeg, false);
        }
        b.setColor(Color.WHITE);
    }

    private void drawRegion(SpriteBatch b, TextureRegion region, float x, float y, float width, float height) {
        if (b == null || region == null) return;
        b.draw(region.getTexture(), x, y, width, height,
                region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
                false, true);
    }

    private void drawRegion(SpriteBatch b, TextureRegion region, float x, float y,
                            float originX, float originY, float width, float height,
                            float scaleX, float scaleY, float rotation, boolean flipX) {
        if (b == null || region == null) return;
        b.draw(region.getTexture(), x, y, originX, originY, width, height,
                scaleX, scaleY, rotation,
                region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
                flipX, true);
    }

    private void renderProjectileUnderGlows(ShapeRenderer sr) {
        boolean hasPowerUpIcons = textureManager.hasExtendedSheet(CompleteSheetId.POWERUP_ICONS);
        if (hasPowerUpIcons) {
            for (WorldPowerUp wp : session.getPowerUpManager().getPickups()) {
                if (!wp.isAlive()) continue;
                Vector2D pos = wp.getPosition();
                float bob = (float) Math.sin(wp.animTimer * 4.0f) * 2.5f;
                float pulse = 1.0f + 0.12f * (float) Math.sin(renderAnimElapsed * 5.0f);
                Color col = getPowerUpColor(wp.type);
                float alpha = wp.getAlphaFactor();
                sr.setColor(col.r, col.g, col.b, 0.18f * alpha);
                sr.circle(pos.x, pos.y + bob, wp.radius * 1.25f * pulse);
                sr.setColor(col.r, col.g, col.b, 0.32f * alpha);
                sr.circle(pos.x, pos.y + bob, wp.radius * 0.90f * pulse);
            }
        }

        for (Projectile p : session.getCombatSystem().getProjectiles()) {
            if (!p.isAlive()) continue;
            Vector2D pos = p.getPosition();
            Color col = p.getPrimaryColor();
            sr.setColor(col.r, col.g, col.b, 0.24f);
            sr.circle(pos.x, pos.y, p.getRadius() * 1.15f);

            Vector2D tailEnd = pos.cpy().sub(p.getDirection().cpy().scl(p.getRadius() * 1.8f));
            sr.rectLine(pos.x, pos.y, tailEnd.x, tailEnd.y, p.getRadius() * 0.55f);
        }
    }

    private void renderWorldOverlayShapes(ShapeRenderer sr) {
        // Compact overhead enemy health bars
        for (Enemy e : session.getEnemies()) {
            if (!e.isAlive() || e.isBoss() || e.getHealthRatio() >= 1.0f) {
                continue;
            }
            Vector2D pos = e.getPosition();
            float r = e.getRadius();
            float barW = Math.max(26.0f, r * 1.35f);
            float barH = 3.5f;
            float barX = pos.x - barW * 0.5f;
            float barY = pos.y - r - 9.0f;

            sr.setColor(24f / 255f, 24f / 255f, 34f / 255f, 0.9f);
            sr.rect(barX, barY, barW, barH);

            float fillW = barW * MathUtils.clamp(e.getHealthRatio(), 0.0f, 1.0f);
            sr.setColor(220f / 255f, 50f / 255f, 50f / 255f, 1.0f);
            sr.rect(barX, barY, fillW, barH);
        }

        // Fallback ShapeRenderer catalyst circle only when CATALYST_ORB_ANIM is unavailable
        Player player = session.getPlayer();
        if (player.isAlive() && !textureManager.hasExtendedSheet(CompleteSheetId.CATALYST_ORB_ANIM)) {
            float pulse = 1.0f + 0.15f * (float) Math.sin(player.getAnimTimer() * 4.0f);
            Vector2D orbPos = getCatalystOrbPosition(player);
            sr.setColor(getCatalystOrbColor());
            sr.circle(orbPos.x, orbPos.y, 4.5f * pulse);
        }
    }

    private void renderWorldOverlayLines(ShapeRenderer sr) {
        for (Enemy e : session.getEnemies()) {
            if (!e.isAlive() || !(e instanceof RangedEnemy ranged)) continue;
            if (ranged.isInAttackRange()) {
                Vector2D pos = ranged.getPosition();
                Vector2D target = ranged.getTargetPosCopy();
                Vector2D diff = Vector2D.sub(target, pos);
                float dist = diff.len();
                if (dist > 1e-4f) {
                    Vector2D dir = diff.nor();
                    float len = Math.min(dist, 180.0f);
                    sr.setColor(210f / 255f, 100f / 255f, 255f / 255f, 0.35f);
                    sr.line(pos.x, pos.y, pos.x + dir.x * len, pos.y + dir.y * len);

                    float pulseR = 5.0f + (float) Math.sin(ranged.getAnimTimer() * 8.0f) * 2.5f;
                    sr.setColor(220f / 255f, 150f / 255f, 255f / 255f, 0.55f);
                    sr.circle(pos.x, pos.y, pulseR);
                }
            }
        }
    }

    // =========================================================================
    // Screen Space UI Render Pass (Virtual 1280x720)
    // =========================================================================

    private void renderUiPass(Vector2D uiMouse) {
        if (batch == null || shapeRenderer == null || fontManager == null) {
            return;
        }

        enableAlphaBlend();
        OrthographicCamera uiCamera = viewportManager.getUiCamera();
        uiCamera.update();
        batch.setProjectionMatrix(uiCamera.combined);
        shapeRenderer.setProjectionMatrix(uiCamera.combined);

        final float viewW = ViewportManager.VIRTUAL_WIDTH;
        final float viewH = ViewportManager.VIRTUAL_HEIGHT;

        if (session.isStartScreen()) {
            if (settingsOverlay.isOpen()) {
                settingsOverlay.render(shapeRenderer, batch, fontManager, viewW, viewH);
            } else {
                titleScreen.render(shapeRenderer, batch, fontManager, session.getSeed(), viewW, viewH);
            }
            return;
        }

        // 1. Phase 2 Minimap (enemies, bosses, power-ups, camera viewport rect)
        enemyPositionsBuffer.clear();
        bossPositionsBuffer.clear();
        for (Enemy e : session.getEnemies()) {
            if (e != null && e.isAlive()) {
                if (e.isBoss()) {
                    bossPositionsBuffer.add(e.getPosition());
                } else {
                    enemyPositionsBuffer.add(e.getPosition());
                }
            }
        }
        powerUpPositionsBuffer.clear();
        for (WorldPowerUp wp : session.getPowerUpManager().getPickups()) {
            if (wp != null && wp.isAlive()) {
                powerUpPositionsBuffer.add(wp.getPosition());
            }
        }
        minimapRenderer.renderMinimap(shapeRenderer, batch, fontManager, session.getTileMap(),
                session.getPlayer().getPosition(), enemyPositionsBuffer, bossPositionsBuffer,
                powerUpPositionsBuffer, session.getCamera().getVisibleBounds(), true);

        // 2. Phase 2 Fixed HUD
        hudRenderer.renderHUD(shapeRenderer, batch, fontManager,
                session.getPlayer(), session.getProgression(), session.getPowerUpManager(),
                session.getEnemies(), session.getEnemiesDefeated(), session.getCurrentSpell(),
                session.getActiveMix(), session.getMatchElapsed(), session.getSeed(),
                session.isDebugMode(), true);

        // 3. Non-blocking Tutorial Goal Banner
        if (tutorialOverlay.isActive() && session.isPlaying()) {
            tutorialOverlay.render(shapeRenderer, batch, fontManager, viewW, viewH);
        }

        // 4. Transient Mode Switch Notification Banner
        if (session.getModeNotificationTimer() > 0.0f && session.getModeNotificationText() != null) {
            renderModeNotificationBanner();
        }

        // 5. Codex Modal Overlay
        if (codexOverlay.isVisible()) {
            codexOverlay.render(shapeRenderer, batch, fontManager, session.getRegistry(), viewW, viewH);
        }

        // 6. Level-Up Modal Overlay
        if (session.isLevelUpOpen()) {
            session.getLevelUpOverlay().render(shapeRenderer, batch, fontManager, viewW, viewH);
        }

        // 7. Settings or Pause Menu Overlay
        if (settingsOverlay.isOpen()) {
            settingsOverlay.render(shapeRenderer, batch, fontManager, viewW, viewH);
        } else if (session.isPaused()) {
            pauseMenuOverlay.render(shapeRenderer, batch, fontManager,
                    session.getSeed(), session.getMatchElapsed(),
                    session.getEnemiesDefeated(), session.getProgression().getLevel(),
                    viewW, viewH);
        }

        // 8. Game Over Screen Modal
        if (session.isGameOver()) {
            session.getGameOverOverlay().render(shapeRenderer, batch, fontManager, viewW, viewH);
        }
    }

    private void renderModeNotificationBanner() {
        BitmapFont boldFont = (fontManager.fontBoldRegular != null)
                ? fontManager.fontBoldRegular
                : fontManager.fontRegular;
        if (boldFont == null) return;

        String text = session.getModeNotificationText();
        glyphLayout.setText(boldFont, text);

        float centerX = 640.0f;
        float bannerY = 55.0f;
        float boxW = glyphLayout.width + 30.0f;
        float boxH = glyphLayout.height + 14.0f;
        float boxX = centerX - boxW * 0.5f;

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(14f / 255f, 18f / 255f, 26f / 255f, 0.85f);
        shapeRenderer.rect(boxX, bannerY - boxH * 0.5f, boxW, boxH);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(65f / 255f, 225f / 255f, 115f / 255f, 0.85f);
        shapeRenderer.rect(boxX, bannerY - boxH * 0.5f, boxW, boxH);
        shapeRenderer.end();

        batch.begin();
        boldFont.setColor(Color.WHITE);
        boldFont.draw(batch, text, centerX - glyphLayout.width * 0.5f, bannerY + glyphLayout.height * 0.35f);
        batch.end();
    }

    // =========================================================================
    // Fullscreen & Window Helpers
    // =========================================================================

    private void applyFullscreenState(boolean fullscreen) {
        settingsOverlay.setFullscreen(fullscreen);
        if (Gdx.graphics == null) {
            return;
        }
        if (fullscreen) {
            if (!Gdx.graphics.isFullscreen()) {
                windowedWidth = Math.max(640, Gdx.graphics.getWidth());
                windowedHeight = Math.max(360, Gdx.graphics.getHeight());
                DisplayMode mode = Gdx.graphics.getDisplayMode();
                if (mode != null) {
                    Gdx.graphics.setFullscreenMode(mode);
                }
            }
        } else {
            if (Gdx.graphics.isFullscreen()) {
                Gdx.graphics.setWindowedMode(windowedWidth, windowedHeight);
            }
        }
    }

    private void exitApplication() {
        if (Gdx.app != null) {
            Gdx.app.exit();
        }
    }

    // =========================================================================
    // Desktop Input Handling (com.badlogic.gdx.InputProcessor)
    // =========================================================================

    @Override
    public boolean keyDown(int keycode) {
        if (keycode == Keys.F11) {
            applyFullscreenState(!settingsOverlay.isFullscreen());
            return true;
        }

        if (settingsOverlay.isOpen()) {
            SettingsOverlay.Action action = settingsOverlay.handleKeyPress(keycode);
            if (action == SettingsOverlay.Action.TOGGLE_FULLSCREEN) {
                applyFullscreenState(settingsOverlay.isFullscreen());
            } else if (action == SettingsOverlay.Action.BACK) {
                settingsOverlay.close();
                if (settingsReturnTarget == SettingsReturnTarget.PAUSE) {
                    pauseMenuOverlay.open();
                }
            }
            return true;
        }

        if (session.isStartScreen()) {
            TitleScreen.Action action = titleScreen.handleKeyPress(keycode);
            if (action == TitleScreen.Action.PLAY) {
                session.startMatch();
                tutorialOverlay.reset();
            } else if (action == TitleScreen.Action.QUIT) {
                exitApplication();
            }
            return true;
        }

        if (session.isGameOver()) {
            GameOverOverlay.Action action = session.getGameOverOverlay().handleKeyPress(keycode);
            if (action == GameOverOverlay.Action.RESTART) {
                session.restartMatch();
                tutorialOverlay.reset();
            } else if (action == GameOverOverlay.Action.QUIT) {
                session.returnToTitle();
                tutorialOverlay.reset();
            }
            return true;
        }

        if (session.isLevelUpOpen()) {
            boolean confirmed = session.getLevelUpOverlay().handleKeyPress(keycode);
            if (confirmed) {
                session.applySelectedLevelUpUpgrade();
            }
            return true;
        }

        if (codexOverlay.isVisible()) {
            codexOverlay.handleInput(keycode == Keys.C, keycode == Keys.ESCAPE, null, false);
            return true;
        }

        if (session.isPaused()) {
            PauseMenuOverlay.Action action = pauseMenuOverlay.handleKeyPress(keycode);
            if (action == PauseMenuOverlay.Action.RESUME) {
                pauseMenuOverlay.close();
                session.setPaused(false);
            } else if (action == PauseMenuOverlay.Action.RESTART) {
                pauseMenuOverlay.close();
                session.restartMatch();
                tutorialOverlay.reset();
            } else if (action == PauseMenuOverlay.Action.QUIT_TO_TITLE) {
                pauseMenuOverlay.close();
                session.returnToTitle();
                tutorialOverlay.reset();
            }
            return true;
        }

        // Active match keyboard shortcuts
        switch (keycode) {
            case Keys.NUM_1:
                session.infuseElement(Element.FIRE);
                return true;
            case Keys.NUM_2:
                session.infuseElement(Element.ICE);
                return true;
            case Keys.NUM_3:
                session.infuseElement(Element.LIGHTNING);
                return true;
            case Keys.NUM_4:
                session.infuseElement(Element.WIND);
                return true;
            case Keys.NUM_5:
                session.infuseElement(Element.EARTH);
                return true;
            case Keys.V:
                session.toggleShootingMode();
                return true;
            case Keys.C:
                codexOverlay.handleInput(true, false, null, false);
                return true;
            case Keys.P:
            case Keys.ESCAPE:
                session.setPaused(true);
                pauseMenuOverlay.open();
                return true;
            case Keys.ENTER:
                if (tutorialOverlay.isActive()) {
                    tutorialOverlay.handleKeyPress(keycode);
                    return true;
                }
                return false;
            case Keys.SPACE:
                if (tutorialOverlay.isActive()) {
                    tutorialOverlay.handleKeyPress(keycode);
                    return true;
                }
                if (session.isDebugMode()) {
                    session.getPlayer().takeDamage(20.0f);
                    return true;
                }
                return false;
            case Keys.H:
                if (session.isDebugMode()) {
                    session.getPlayer().heal(30.0f);
                    return true;
                }
                return false;
            case Keys.L:
                if (session.isDebugMode()) {
                    session.getProgression().addExp(40);
                    return true;
                }
                return false;
            case Keys.PLUS:
            case Keys.EQUALS:
                session.getCamera().adjustZoom(GameCamera.KEYBOARD_ZOOM_STEP);
                return true;
            case Keys.MINUS:
                session.getCamera().adjustZoom(-GameCamera.KEYBOARD_ZOOM_STEP);
                return true;
            default:
                return false;
        }
    }

    @Override
    public boolean keyUp(int keycode) {
        return false;
    }

    @Override
    public boolean keyTyped(char character) {
        return false;
    }

    @Override
    public boolean touchDown(int screenX, int screenY, int pointer, int button) {
        if (button != Buttons.LEFT) {
            return false;
        }

        Vector2D uiPos = viewportManager.screenToUi(screenX, screenY);

        if (settingsOverlay.isOpen()) {
            SettingsOverlay.Action action = settingsOverlay.handleMouseClick(uiPos);
            if (action == SettingsOverlay.Action.TOGGLE_FULLSCREEN) {
                applyFullscreenState(settingsOverlay.isFullscreen());
            } else if (action == SettingsOverlay.Action.BACK) {
                settingsOverlay.close();
                if (settingsReturnTarget == SettingsReturnTarget.PAUSE) {
                    pauseMenuOverlay.open();
                }
            }
            return true;
        }

        if (session.isStartScreen()) {
            TitleScreen.Action action = titleScreen.handleMouseClick(uiPos);
            if (action == TitleScreen.Action.PLAY) {
                session.startMatch();
                tutorialOverlay.reset();
            } else if (action == TitleScreen.Action.SETTINGS) {
                settingsReturnTarget = SettingsReturnTarget.TITLE;
                settingsOverlay.open();
            } else if (action == TitleScreen.Action.QUIT) {
                exitApplication();
            }
            return true;
        }

        if (session.isGameOver()) {
            session.getGameOverOverlay().handleMouseButtonPressed(uiPos);
            return true;
        }

        if (session.isLevelUpOpen()) {
            boolean confirmed = session.getLevelUpOverlay().handleMouseClick(uiPos);
            if (confirmed) {
                session.applySelectedLevelUpUpgrade();
            }
            return true;
        }

        if (codexOverlay.isVisible()) {
            codexOverlay.handleInput(false, false, uiPos, true);
            return true;
        }

        if (session.isPaused()) {
            PauseMenuOverlay.Action action = pauseMenuOverlay.handleMouseClick(uiPos);
            if (action == PauseMenuOverlay.Action.RESUME) {
                pauseMenuOverlay.close();
                session.setPaused(false);
            } else if (action == PauseMenuOverlay.Action.RESTART) {
                pauseMenuOverlay.close();
                session.restartMatch();
                tutorialOverlay.reset();
            } else if (action == PauseMenuOverlay.Action.SETTINGS) {
                settingsReturnTarget = SettingsReturnTarget.PAUSE;
                pauseMenuOverlay.close();
                settingsOverlay.open();
            } else if (action == PauseMenuOverlay.Action.QUIT_TO_TITLE) {
                pauseMenuOverlay.close();
                session.returnToTitle();
                tutorialOverlay.reset();
            }
            return true;
        }

        if (tutorialOverlay.isActive() && tutorialOverlay.handleMouseClick(uiPos)) {
            return true;
        }

        if (session.isPlaying()) {
            Vector2D worldPos = viewportManager.screenToWorld(session.getCamera(), screenX, screenY);
            session.castSpell(worldPos);
            return true;
        }

        return false;
    }

    @Override
    public boolean touchUp(int screenX, int screenY, int pointer, int button) {
        if (button != Buttons.LEFT) {
            return false;
        }

        Vector2D uiPos = viewportManager.screenToUi(screenX, screenY);

        if (session.isGameOver()) {
            GameOverOverlay.Action action = session.getGameOverOverlay().handleMouseButtonReleased(uiPos);
            if (action == GameOverOverlay.Action.RESTART) {
                session.restartMatch();
                tutorialOverlay.reset();
            } else if (action == GameOverOverlay.Action.QUIT) {
                session.returnToTitle();
                tutorialOverlay.reset();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean touchCancelled(int screenX, int screenY, int pointer, int button) {
        return false;
    }

    @Override
    public boolean touchDragged(int screenX, int screenY, int pointer) {
        return mouseMoved(screenX, screenY);
    }

    @Override
    public boolean mouseMoved(int screenX, int screenY) {
        Vector2D uiPos = viewportManager.screenToUi(screenX, screenY);
        if (settingsOverlay.isOpen()) {
            settingsOverlay.handleMouseMove(uiPos);
            return true;
        }
        if (session.isStartScreen()) {
            titleScreen.handleMouseMove(uiPos);
            return true;
        }
        if (session.isPaused()) {
            pauseMenuOverlay.handleMouseMove(uiPos);
            return true;
        }
        if (session.isLevelUpOpen()) {
            session.getLevelUpOverlay().handleMouseMove(uiPos);
            return true;
        }
        if (session.isGameOver()) {
            session.getGameOverOverlay().handleMouseMove(uiPos);
            return true;
        }
        return false;
    }

    @Override
    public boolean scrolled(float amountX, float amountY) {
        if (session.isPlaying()) {
            session.getCamera().adjustZoom(-amountY * GameCamera.MOUSE_WHEEL_ZOOM_STEP);
            return true;
        }
        return false;
    }

    // =========================================================================
    // Lifecycle Hooks
    // =========================================================================

    @Override
    public void resize(int width, int height) {
        if (width > 0 && height > 0) {
            viewportManager.update(width, height);
            session.getCamera().setViewportSize(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
            session.getLevelUpOverlay().updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
            session.getGameOverOverlay().updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
            titleScreen.updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
            pauseMenuOverlay.updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
            settingsOverlay.updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
        }
    }

    @Override
    public void pause() {
        if (session.isPlaying()) {
            session.setPaused(true);
            pauseMenuOverlay.open();
        }
    }

    @Override
    public void resume() {
        // Stay paused in PauseMenuOverlay until player explicitly resumes
    }

    @Override
    public void hide() {
        if (Gdx.input != null && Gdx.input.getInputProcessor() == this) {
            Gdx.input.setInputProcessor(null);
        }
    }

    @Override
    public void dispose() {
        hide();
    }

    public GameSession getSession() {
        return session;
    }

    public ViewportManager getViewportManager() {
        return viewportManager;
    }

    public TitleScreen getTitleScreen() {
        return titleScreen;
    }

    public PauseMenuOverlay getPauseMenuOverlay() {
        return pauseMenuOverlay;
    }

    public SettingsOverlay getSettingsOverlay() {
        return settingsOverlay;
    }

    public CodexOverlay getCodexOverlay() {
        return codexOverlay;
    }

    public TutorialOverlay getTutorialOverlay() {
        return tutorialOverlay;
    }
}
