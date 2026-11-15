package com.spellstorm.core;

import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.spellstorm.assets.FontManager;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.BalanceConfig;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.world.BiomeType;
import com.spellstorm.world.Chunk;
import com.spellstorm.world.ChunkManager;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGrid;

/**
 * Main application coordinator extending LibGDX Game.
 * Manages global shared rendering contexts, Phase 2 session creation, CLI scenario
 * execution, screenshot timing, delta-time clamping, and active screen delegation.
 */
public class SpellStormGame extends Game {

    private final EngineConfig config;
    private SpriteBatch batch;
    private ShapeRenderer shapeRenderer;
    private TextureManager textureManager;
    private FontManager fontManager;
    private GameSession session;
    private float matchElapsedTime = 0f;
    private boolean initialized = false;
    private boolean screenshotCaptured = false;

    public SpellStormGame() {
        this(new EngineConfig());
    }

    public SpellStormGame(String[] args) {
        this(EngineConfig.fromArgs(args));
    }

    public SpellStormGame(EngineConfig config) {
        this.config = config != null ? config : new EngineConfig();
    }

    @Override
    public void create() {
        if (Gdx.gl != null) {
            this.batch = new SpriteBatch();
            this.shapeRenderer = new ShapeRenderer();
            this.textureManager = new TextureManager();
            this.textureManager.loadAll();
            this.fontManager = new FontManager();
            this.fontManager.initialize();
            boolean isFiniteScenario = config.isTileMapMode()
                    || config.isScenarioM2Prototype()
                    || config.isScenarioM5Boss()
                    || config.isScenarioM5Elements()
                    || config.isTestBridge()
                    || config.isTestRuins()
                    || config.isTestCampsite()
                    || config.isTestBoss();

            if (isFiniteScenario) {
                this.session = GameSession.createPhase2Session(
                        SpellRegistry.createDefaultRegistry(),
                        config.getSeed(),
                        config.isDebugMode()
                );
            } else {
                this.session = GameSession.createInfiniteSession(
                        SpellRegistry.createDefaultRegistry(),
                        config.getSeed(),
                        config.isDebugMode()
                );
            }
            applyConfiguredScenarios(this.session, this.config);
            GameScreen gameScreen = new GameScreen(this, session, textureManager, fontManager);
            applyScreenScenarios(gameScreen, this.session, this.config);
            setScreen(gameScreen);
        }

        this.matchElapsedTime = 0f;
        this.initialized = true;

        System.out.println("[SpellStormGame] Application created with config: "
                + config.getWindowWidth() + "x" + config.getWindowHeight()
                + ", Title: " + config.getWindowTitle()
                + ", Headless: " + config.isHeadless()
                + ", Seed: " + (session != null ? session.getSeed() : config.getSeed())
                + ", Debug: " + config.isDebugMode()
                + ", Scenarios: " + config.getScenarios());
    }

    /**
     * Executes CLI scenario / autoplay flags onto a Phase 2 GameSession so
     * `--scenario <name>` and legacy `--test-*` flags drive real engine state.
     */
    public static void applyConfiguredScenarios(GameSession targetSession, EngineConfig cfg) {
        if (targetSession == null || cfg == null) {
            return;
        }

        boolean shouldStartMatch = cfg.isAutoplay();
        if (!shouldStartMatch) {
            for (String scenario : cfg.getScenarios()) {
                if (!EngineConfig.SCENARIO_TITLE.equals(scenario) && !EngineConfig.SCENARIO_SETTINGS.equals(scenario)) {
                    shouldStartMatch = true;
                    break;
                }
            }
        }
        if (shouldStartMatch) {
            targetSession.startMatch();
        }

        if (cfg.isTestAutoShot()) {
            targetSession.setShootingMode(ShootingMode.AUTO);
        }

        if (cfg.isTestBridge()) {
            movePlayerToTile(targetSession, TileType.BRIDGE_H, TileType.BRIDGE_V);
        } else if (cfg.isTestRuins()) {
            movePlayerToTile(targetSession, TileType.STONE, TileType.SAND);
        } else if (cfg.isTestCampsite()) {
            movePlayerToTile(targetSession, TileType.DIRT, TileType.GRASS);
        }

        if (cfg.isTestBoss()) {
            Vector2D bossPos = targetSession.getPlayer().getPosition().cpy().add(320.0f, 0.0f);
            BossEnemy boss = new BossEnemy(bossPos);
            BalanceConfig.defaults().applyToEnemy(boss);
            targetSession.addEnemy(boss);
        }

        if (cfg.isTestPowerup()) {
            Vector2D pickupPos = targetSession.getPlayer().getPosition().cpy().add(90.0f, 0.0f);
            targetSession.getPowerUpManager().spawnPickup(pickupPos, PowerUpType.ARCANE_OVERDRIVE);
            targetSession.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
        }

        if (cfg.isTestShowcase()) {
            Vector2D p = targetSession.getPlayer().getPosition();
            Enemy melee = new MeleeEnemy(p.cpy().add(260.0f, -60.0f));
            Enemy ranged = new RangedEnemy(p.cpy().add(-280.0f, 80.0f));
            BalanceConfig.defaults().applyToEnemy(melee);
            BalanceConfig.defaults().applyToEnemy(ranged);
            targetSession.addEnemy(melee);
            targetSession.addEnemy(ranged);
            targetSession.getPowerUpManager().spawnPickup(p.cpy().add(120.0f, 40.0f), PowerUpType.ELEMENTAL_BURST);
        }

        if (cfg.isScenarioM2Prototype()) {
            setupM2PrototypeScenario(targetSession);
        }

        if (cfg.isScenarioM5Boss()) {
            setupM5BossScenario(targetSession);
        }

        if (cfg.isScenarioM5Elements()) {
            setupM5ElementsScenario(targetSession);
        }

        if (cfg.hasScenario("exploration-plains")) {
            setupExplorationPlainsScenario(targetSession);
        } else if (cfg.hasScenario("exploration-forest")) {
            setupExplorationForestScenario(targetSession);
        } else if (cfg.hasScenario("exploration-radar")) {
            setupExplorationRadarScenario(targetSession);
        }

        if (cfg.isTestLevelUp()) {
            int needed = targetSession.getProgression().getExpForNextLevel();
            targetSession.getProgression().addExp(needed);
            targetSession.update(0.016f);
        }

        if (cfg.isTestGameOver()) {
            targetSession.triggerGameOver();
        }

        if (cfg.isTestRestartFlow()) {
            targetSession.triggerGameOver();
            targetSession.restartMatch();
        }

        if (cfg.isScenarioPause()) {
            targetSession.setPaused(true);
        }
    }

    /**
     * Applies UI-overlay scenario flags (--scenario title|pause|settings|codex)
     * onto the active GameScreen.
     */
    public static void applyScreenScenarios(GameScreen screen, GameSession targetSession, EngineConfig cfg) {
        if (screen == null || targetSession == null || cfg == null) {
            return;
        }
        if (cfg.isScenarioTitle()) {
            targetSession.returnToTitle();
        }
        if (cfg.isScenarioPause()) {
            targetSession.setPaused(true);
            screen.getPauseMenuOverlay().open();
        }
        if (cfg.isScenarioSettings()) {
            screen.getSettingsOverlay().open();
        }
        if (cfg.isScenarioCodex()) {
            screen.getCodexOverlay().setVisible(true);
        }
        if (cfg.isScenarioM5Boss() || cfg.isScenarioM5Elements()
                || (cfg.getScreenshotPath() != null && (cfg.isScenarioM2Prototype() || cfg.isTestBridge()))) {
            screen.getTutorialOverlay().dismiss();
        }
    }

    private static void setupM2PrototypeScenario(GameSession targetSession) {
        TileMap map = targetSession.getTileMap();
        if (map != null) {
            // Stone shrine corner beside the central East-West / North-South dirt crossroads (79, 64)
            for (int ty = 60; ty <= 62; ty++) {
                for (int tx = 73; tx <= 76; tx++) {
                    map.setTile(tx, ty, TileType.STONE);
                }
            }
            map.addDecoration(DecoType.CAMPFIRE, map.tileToWorld(76, 62), true);
            map.addDecoration(DecoType.TREE_OAK, map.tileToWorld(74, 70), true);
            map.addDecoration(DecoType.TREE_OAK, map.tileToWorld(84, 69), true);
            map.addDecoration(DecoType.ROCK, map.tileToWorld(75, 67), true);
            map.addDecoration(DecoType.ROCK, map.tileToWorld(82, 61), true);
            map.addDecoration(DecoType.BUSH, map.tileToWorld(74, 65), false);
            map.addDecoration(DecoType.BUSH, map.tileToWorld(82, 67), false);
            map.addDecoration(DecoType.BUSH, map.tileToWorld(74, 61), false);
            map.sortObstaclesByDepth();

            Vector2D playerPos = map.tileToWorld(79, 64);
            targetSession.getPlayer().setPosition(playerPos);
            targetSession.getCamera().setCenter(playerPos.x, playerPos.y);
        }

        Vector2D p = targetSession.getPlayer().getPosition();
        Vector2D[] offsets = {
                new Vector2D(155.0f, -30.0f),
                new Vector2D(-210.0f, 55.0f),
                new Vector2D(120.0f, 205.0f)
        };
        BalanceConfig balance = BalanceConfig.defaults();
        for (Vector2D offset : offsets) {
            MeleeEnemy fiend = new MeleeEnemy(p.cpy().add(offset));
            balance.applyToEnemy(fiend);
            targetSession.addEnemy(fiend);
        }
    }

    private static void setupM5BossScenario(GameSession targetSession) {
        TileMap map = targetSession.getTileMap();
        Vector2D playerPos = (map != null)
                ? map.tileToWorld(87, 36)
                : targetSession.getPlayer().getPosition().cpy();
        targetSession.getPlayer().setPosition(playerPos);
        targetSession.getCamera().setCenter(playerPos.x, playerPos.y);

        targetSession.infuseElement(Element.FIRE);
        targetSession.infuseElement(Element.LIGHTNING);
        targetSession.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);

        BalanceConfig balance = BalanceConfig.defaults();
        BossEnemy boss = new BossEnemy(map != null ? map.tileToWorld(92, 34) : playerPos.cpy().add(160.0f, -64.0f));
        balance.applyToEnemy(boss);
        targetSession.addEnemy(boss);

        TankEnemy golemWest = new TankEnemy(map != null ? map.tileToWorld(82, 33) : playerPos.cpy().add(-160.0f, -96.0f));
        TankEnemy golemEast = new TankEnemy(map != null ? map.tileToWorld(92, 39) : playerPos.cpy().add(160.0f, 96.0f));
        RangedEnemy cultistNorth = new RangedEnemy(map != null ? map.tileToWorld(89, 31) : playerPos.cpy().add(64.0f, -160.0f));
        RangedEnemy cultistSouth = new RangedEnemy(map != null ? map.tileToWorld(83, 39) : playerPos.cpy().add(-128.0f, 96.0f));
        for (Enemy extra : new Enemy[]{golemWest, golemEast, cultistNorth, cultistSouth}) {
            balance.applyToEnemy(extra);
            targetSession.addEnemy(extra);
        }

        targetSession.getPowerUpManager().spawnPickup(
                map != null ? map.tileToWorld(84, 37) : playerPos.cpy().add(-96.0f, 32.0f),
                PowerUpType.PIERCING_SURGE);
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(-185.0f, -45.0f), 40);
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(175.0f, 120.0f), 20);
    }

    private static void setupM5ElementsScenario(GameSession targetSession) {
        TileMap map = targetSession.getTileMap();
        Vector2D playerPos = (map != null)
                ? map.tileToWorld(93, 89)
                : targetSession.getPlayer().getPosition().cpy();
        targetSession.getPlayer().setPosition(playerPos);
        targetSession.getCamera().setCenter(playerPos.x + 48.0f, playerPos.y - 24.0f);

        targetSession.infuseElement(Element.ICE);
        targetSession.infuseElement(Element.WIND);
        targetSession.getPowerUpManager().getActiveAbility().activate(PowerUpType.PIERCING_SURGE);

        BalanceConfig balance = BalanceConfig.defaults();
        MeleeEnemy fiend = new MeleeEnemy(playerPos.cpy().add(210.0f, -85.0f));
        RangedEnemy cultist = new RangedEnemy(playerPos.cpy().add(235.0f, 40.0f));
        TankEnemy golem = new TankEnemy(playerPos.cpy().add(170.0f, -150.0f));
        for (Enemy foe : new Enemy[]{fiend, cultist, golem}) {
            balance.applyToEnemy(foe);
            targetSession.addEnemy(foe);
        }

        // Showcase Cyan, Emerald, Gold XP gems and Burst world power-up outside magnet instant pull
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(155.0f, 15.0f), 10);
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(185.0f, 38.0f), 20);
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(215.0f, 18.0f), 40);
        targetSession.getPowerUpManager().spawnPickup(playerPos.cpy().add(140.0f, 75.0f), PowerUpType.ELEMENTAL_BURST);

        // Launch multi-element spells across the campsite showcase clearing
        SpellRegistry reg = targetSession.getRegistry();
        Element[] elements = {Element.FIRE, Element.ICE, Element.LIGHTNING, Element.WIND, Element.EARTH};
        float[] angles = {-0.63f, -0.44f, -0.25f, -0.05f, 0.16f};
        for (int i = 0; i < elements.length; i++) {
            SpellDefinition spell = reg.baseSpell(elements[i]);
            if (spell == null) continue;
            Vector2D dir = Vector2D.fromAngle(angles[i]);
            Vector2D start = playerPos.cpy().mulAdd(dir, 36.0f + i * 14.0f);
            float projSpeed = Math.min(200.0f, 125.0f * spell.getProfile().getSpeedMultiplier());
            float projRadius = Math.max(spell.getProfile().getRadius(), 11.0f);
            float projDamage = 20.0f * spell.getProfile().getDamageMultiplier();
            targetSession.getCombatSystem().addProjectile(new Projectile(
                    start, dir, projSpeed, projRadius,
                    projDamage, 2.2f, 0, ProjectileFaction.PLAYER,
                    spell.getPrimaryColor(), spell.getSecondaryColor(), spell));
        }
        // Plus Player Arcane bolt & incoming Enemy Void bolt
        Vector2D arcDir = new Vector2D(0.95f, -0.31f).nor();
        targetSession.getCombatSystem().addProjectile(new Projectile(
                playerPos.cpy().mulAdd(arcDir, 52.0f), arcDir, 130.0f, 11.0f,
                16.0f, 2.2f, 0, ProjectileFaction.PLAYER,
                new com.badlogic.gdx.graphics.Color(0.55f, 0.85f, 1.0f, 1.0f),
                new com.badlogic.gdx.graphics.Color(0.9f, 0.96f, 1.0f, 1.0f), null));
        Vector2D enemyDir = Vector2D.sub(playerPos, cultist.getPosition()).nor();
        targetSession.getCombatSystem().addProjectile(new Projectile(
                cultist.getPosition().cpy().mulAdd(enemyDir, 24.0f), enemyDir, 120.0f, 10.0f,
                12.0f, 2.2f, 0, ProjectileFaction.ENEMY,
                new com.badlogic.gdx.graphics.Color(0.85f, 0.35f, 1.0f, 1.0f),
                new com.badlogic.gdx.graphics.Color(1.0f, 0.7f, 1.0f, 1.0f), null));
    }

    private static void setupExplorationPlainsScenario(GameSession targetSession) {
        WorldGrid worldGrid = targetSession.getWorldGrid();
        float targetX = 0.0f;
        float targetY = 0.0f;
        for (int r = 1; r < 80; r++) {
            for (int dx = -r; dx <= r; dx++) {
                int tx = dx;
                int ty = r;
                if (worldGrid.getTile(tx, ty) == TileType.GRASS &&
                        (worldGrid.getTile(tx + 2, ty) == TileType.WATER || worldGrid.getTile(tx - 2, ty) == TileType.WATER)) {
                    targetX = tx * 32.0f;
                    targetY = ty * 32.0f;
                    break;
                }
            }
            if (targetX != 0.0f || targetY != 0.0f) break;
        }

        Vector2D playerPos = new Vector2D(targetX, targetY);
        targetSession.getPlayer().setPosition(playerPos);
        targetSession.getCamera().setCenter(playerPos.x, playerPos.y);

        BalanceConfig balance = BalanceConfig.defaults();
        MeleeEnemy fiend = new MeleeEnemy(playerPos.cpy().add(180.0f, -40.0f));
        RangedEnemy cultist = new RangedEnemy(playerPos.cpy().add(-210.0f, 60.0f));
        balance.applyToEnemy(fiend);
        balance.applyToEnemy(cultist);
        targetSession.addEnemy(fiend);
        targetSession.addEnemy(cultist);

        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(110.0f, 25.0f), 10);
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(140.0f, -50.0f), 20);
        targetSession.getPowerUpManager().spawnPickup(playerPos.cpy().add(-110.0f, -45.0f), PowerUpType.ARCANE_OVERDRIVE);
    }

    private static void setupExplorationForestScenario(GameSession targetSession) {
        float targetX = 1500.0f;
        float targetY = 1500.0f;
        if (targetSession.getInfiniteWorld() != null) {
            ChunkManager cm = targetSession.getInfiniteWorld().getChunkManager();
            for (int cy = 1; cy <= 12; cy++) {
                for (int cx = 1; cx <= 12; cx++) {
                    Chunk chunk = cm.getChunk(cx, cy);
                    if (chunk != null) {
                        boolean hasTrees = false;
                        for (int i = 0; i < chunk.getObstacleCount(); i++) {
                            Obstacle o = chunk.getObstacle(i);
                            if (o.getType() == DecoType.TREE_OAK || o.getType() == DecoType.TREE_PINE) {
                                hasTrees = true;
                                break;
                            }
                        }
                        if (hasTrees) {
                            targetX = cx * 1024.0f + 512.0f;
                            targetY = cy * 1024.0f + 512.0f;
                            break;
                        }
                    }
                }
                if (targetX != 1500.0f || targetY != 1500.0f) break;
            }
        }

        Vector2D playerPos = new Vector2D(targetX, targetY);
        targetSession.getPlayer().setPosition(playerPos);
        targetSession.getCamera().setCenter(playerPos.x, playerPos.y);

        BalanceConfig balance = BalanceConfig.defaults();
        MeleeEnemy fiend = new MeleeEnemy(playerPos.cpy().add(170.0f, 80.0f));
        TankEnemy golem = new TankEnemy(playerPos.cpy().add(-200.0f, -60.0f));
        balance.applyToEnemy(fiend);
        balance.applyToEnemy(golem);
        targetSession.addEnemy(fiend);
        targetSession.addEnemy(golem);

        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(250.0f, -135.0f), 20);
        targetSession.getExperienceSystem().spawnPickup(playerPos.cpy().add(-280.0f, 155.0f), 40);
        targetSession.getPowerUpManager().spawnPickup(playerPos.cpy().add(180.0f, -120.0f), PowerUpType.ELEMENTAL_BURST);
    }

    private static void setupExplorationRadarScenario(GameSession targetSession) {
        Vector2D playerPos = targetSession.getPlayer().getPosition();
        targetSession.getCamera().setCenter(playerPos.x, playerPos.y);

        BalanceConfig balance = BalanceConfig.defaults();
        for (int i = 0; i < 8; i++) {
            float ang = (float) (i * 2.0 * Math.PI / 8.0);
            float dist = 320.0f + (i % 3) * 120.0f;
            Vector2D pos = playerPos.cpy().add((float) Math.cos(ang) * dist, (float) Math.sin(ang) * dist);
            Enemy e = (i % 2 == 0) ? new MeleeEnemy(pos) : new RangedEnemy(pos);
            balance.applyToEnemy(e);
            targetSession.addEnemy(e);
        }
        BossEnemy boss = new BossEnemy(playerPos.cpy().add(600.0f, 350.0f));
        balance.applyToEnemy(boss);
        targetSession.addEnemy(boss);

        targetSession.getPowerUpManager().spawnPickup(playerPos.cpy().add(-280.0f, 220.0f), PowerUpType.PIERCING_SURGE);
        targetSession.getPowerUpManager().spawnPickup(playerPos.cpy().add(350.0f, -260.0f), PowerUpType.ARCANE_OVERDRIVE);
    }

    private static void movePlayerToTile(GameSession targetSession, TileType primary, TileType fallback) {
        TileMap map = targetSession.getTileMap();
        if (map == null) return;
        int w = map.getWidth();
        int h = map.getHeight();
        float ts = map.getTileSize();
        for (int y = 4; y < h - 4; y++) {
            for (int x = 4; x < w - 4; x++) {
                TileType t = map.tileAt(x, y);
                if ((t == primary || t == fallback) && map.isTraversable((x + 0.5f) * ts, (y + 0.5f) * ts, 16.0f)) {
                    Vector2D targetPos = new Vector2D((x + 0.5f) * ts, (y + 0.5f) * ts);
                    targetSession.getPlayer().setPosition(targetPos);
                    targetSession.getCamera().setCenter(targetPos.x, targetPos.y);
                    return;
                }
            }
        }
    }

    @Override
    public void render() {
        float rawDelta = Gdx.graphics != null ? Gdx.graphics.getDeltaTime() : (1f / 60f);
        float dt = Math.min(rawDelta, 0.1f);
        this.matchElapsedTime += dt;

        // Auto-quit timer check for automated testing / headless runs
        if (config.getQuitAfterSeconds() > 0 && matchElapsedTime >= config.getQuitAfterSeconds()) {
            System.out.println("[SpellStormGame] Quit-after time reached (" + config.getQuitAfterSeconds() + "s), exiting.");
            if (Gdx.app != null) {
                Gdx.app.exit();
            }
            return;
        }

        if (getScreen() != null) {
            super.render();

            float screenshotTargetTime = config.getScreenshotAtSeconds() >= 0.0f
                    ? config.getScreenshotAtSeconds()
                    : 0.5f;
            if (config.getScreenshotPath() != null && !screenshotCaptured && matchElapsedTime >= screenshotTargetTime) {
                screenshotCaptured = true;
                try {
                    int w = Gdx.graphics.getBackBufferWidth();
                    int h = Gdx.graphics.getBackBufferHeight();
                    byte[] pixels = com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixels(0, 0, w, h, true);
                    com.badlogic.gdx.graphics.Pixmap pixmap = new com.badlogic.gdx.graphics.Pixmap(w, h, com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
                    com.badlogic.gdx.utils.BufferUtils.copy(pixels, 0, pixmap.getPixels(), pixels.length);
                    com.badlogic.gdx.files.FileHandle outFile = new com.badlogic.gdx.files.FileHandle(config.getScreenshotPath());
                    if (outFile.parent() != null) {
                        outFile.parent().mkdirs();
                    }
                    com.badlogic.gdx.graphics.PixmapIO.writePNG(outFile, pixmap);
                    pixmap.dispose();
                    System.out.println("[SpellStormGame] Screenshot saved to: " + config.getScreenshotPath());
                } catch (Exception e) {
                    System.err.println("[SpellStormGame] Failed to capture screenshot: " + e.getMessage());
                }
            }
        } else if (Gdx.gl != null) {
            Gdx.gl.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        }
    }

    @Override
    public void resize(int width, int height) {
        super.resize(width, height);
    }

    @Override
    public void pause() {
        super.pause();
    }

    @Override
    public void resume() {
        super.resume();
    }

    @Override
    public void dispose() {
        super.dispose();

        if (getScreen() != null) {
            getScreen().dispose();
            setScreen(null);
        }
        if (textureManager != null) {
            textureManager.dispose();
            textureManager = null;
        }
        if (fontManager != null) {
            fontManager.dispose();
            fontManager = null;
        }
        if (batch != null) {
            batch.dispose();
            batch = null;
        }
        if (shapeRenderer != null) {
            shapeRenderer.dispose();
            shapeRenderer = null;
        }

        this.initialized = false;
        System.out.println("[SpellStormGame] Application disposed cleanly.");
    }

    public EngineConfig getConfig() { return config; }
    public SpriteBatch getBatch() { return batch; }
    public ShapeRenderer getShapeRenderer() { return shapeRenderer; }
    public TextureManager getTextureManager() { return textureManager; }
    public FontManager getFontManager() { return fontManager; }
    public GameSession getSession() { return session; }
    public float getMatchElapsedTime() { return matchElapsedTime; }
    public boolean isInitialized() { return initialized; }
}
