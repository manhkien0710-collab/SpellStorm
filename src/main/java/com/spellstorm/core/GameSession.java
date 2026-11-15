package com.spellstorm.core;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.CombatSystem;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.ProjectileProfile;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.BalanceConfig;
import com.spellstorm.systems.EnemySeparationSystem;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.systems.ExperienceSystem;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeManager;
import com.spellstorm.ui.GameOverOverlay;
import com.spellstorm.ui.LevelUpOverlay;
import com.spellstorm.world.ChunkManagerImpl;
import com.spellstorm.world.FloatingOriginAware;
import com.spellstorm.world.FloatingOriginManager;
import com.spellstorm.world.InfiniteWorld;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileMapAdapter;
import com.spellstorm.world.WorldGrid;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;

/**
 * Primary game session coordinator orchestrating:
 * - Match state lifecycle (START, PLAYING, PAUSED, LEVEL_UP, GAME_OVER).
 * - Complete simulation freeze during START, PAUSED, modal overlays (LevelUpOverlay, GameOverOverlay, Codex, Settings).
 * - Player movement, camera tracking, and manual vs auto-shot targeting.
 * - Enemy spawning, AI steering, crowd separation, ranged attacks, and boss volleys.
 * - Combat projectile management, pierce falloff, and damage mitigation.
 * - Enemy defeat reward claiming, experience drops, and level-up queue.
 * - World power-up pickups, 5-second temporary ability lifecycle, and combat modifiers.
 * - Deterministic, idempotent match restart restoring all game state across match cycles.
 * - Dual-world mode support: bounded TileMap vs unbounded procedural InfiniteWorld.
 */
public class GameSession implements FloatingOriginAware {

    public static final float BASE_PLAYER_PROJECTILE_SPEED = 280.0f;

    public enum WorldMode {
        TILE_MAP,
        INFINITE_WORLD
    }

    private final WorldMode worldMode;
    private final WorldGrid worldGrid;
    private final TileMap tileMap;
    private final InfiniteWorld infiniteWorld;
    private final FloatingOriginManager originManager;
    private final Rectangle arenaBounds;
    private final SpellRegistry registry;
    private final Random rng;
    private final long seed;

    // Simulation subsystems
    private final Player player;
    private final GameCamera camera;
    private final List<Enemy> enemies = new ArrayList<>();
    private final CombatSystem combatSystem;
    private final ExperienceSystem experienceSystem;
    private final PlayerProgression progression;
    private final UpgradeManager upgradeManager;
    private final PowerUpManager powerUpManager;
    private final EnemySpawner spawner;
    private final EnemySeparationSystem separationSystem;
    private final com.spellstorm.navigation.AStarPathfinder pathfinder;

    // UI overlays
    private final LevelUpOverlay levelUpOverlay = new LevelUpOverlay();
    private final GameOverOverlay gameOverOverlay = new GameOverOverlay();

    // Match state and telemetry
    private GameState gameState = GameState.PLAYING;
    private boolean debugMode = true;
    private boolean externalOverlayBlocking = false;
    private float matchElapsed = 0.0f;
    private int enemiesDefeated = 0;
    private float overlayAutoTimer = 0.0f;

    // Spell mixture and shooting mode
    private final List<Element> activeMix = new ArrayList<>();
    private SpellDefinition currentSpell;
    private ShootingMode shootingMode = ShootingMode.MANUAL;
    private boolean toggleKeyHeld = false;
    private float modeNotificationTimer = 0.0f;
    private String modeNotificationText = "";

    // =========================================================================
    // Constructors & Factories (Dual-Mode: TileMap vs InfiniteWorld)
    // =========================================================================

    private GameSession(WorldGrid worldGrid, TileMap tileMap, InfiniteWorld infiniteWorld,
                        SpellRegistry registry, long seed, boolean startInTitle,
                        boolean debugMode, boolean usePhase2Systems) {
        this.seed = seed;
        this.worldGrid = Objects.requireNonNull(worldGrid, "WorldGrid cannot be null");
        this.tileMap = tileMap;
        this.infiniteWorld = infiniteWorld;
        this.worldMode = (infiniteWorld != null) ? WorldMode.INFINITE_WORLD : WorldMode.TILE_MAP;
        this.originManager = (infiniteWorld != null) ? infiniteWorld.getOriginManager() : null;
        this.arenaBounds = (worldGrid.isBounded()) ? new Rectangle(worldGrid.getWorldBounds()) : null;
        this.registry = (registry != null) ? registry : SpellRegistry.createDefaultRegistry();
        this.rng = new Random(seed);
        this.debugMode = debugMode;

        Vector2D initialPlayerPos = (arenaBounds != null)
                ? new Vector2D(arenaBounds.x + arenaBounds.width * 0.5f, arenaBounds.y + arenaBounds.height * 0.5f)
                : new Vector2D(0.0f, 0.0f); // Crossroads of E-W Highway & N-S Trail
        this.player = new Player(initialPlayerPos);
        this.camera = new GameCamera(1280.0f, 720.0f);
        if (arenaBounds != null) {
            this.camera.setWorldBounds(arenaBounds.x, arenaBounds.y, arenaBounds.width, arenaBounds.height);
            this.camera.setClampingEnabled(true);
        } else {
            this.camera.setWorldBounds(-50_000_000f, -50_000_000f, 100_000_000f, 100_000_000f);
            this.camera.setClampingEnabled(false);
        }

        Rectangle combatBounds = (arenaBounds != null)
                ? arenaBounds
                : new Rectangle(-50_000_000f, -50_000_000f, 100_000_000f, 100_000_000f);
        this.combatSystem = new CombatSystem(combatBounds);
        this.experienceSystem = new ExperienceSystem();
        this.progression = new PlayerProgression(1);
        this.upgradeManager = new UpgradeManager();
        this.powerUpManager = new PowerUpManager(combatBounds, rng);
        if (this.tileMap != null) {
            this.powerUpManager.setTileMap(this.tileMap);
        }

        if (usePhase2Systems) {
            this.spawner = new EnemySpawner(this.tileMap, rng, BalanceConfig.defaults());
            this.separationSystem = new EnemySeparationSystem();
        } else {
            this.spawner = new EnemySpawner(this.tileMap, rng);
            this.separationSystem = null;
        }
        this.spawner.setCamera(camera);
        this.pathfinder = (this.tileMap != null)
                ? new com.spellstorm.navigation.AStarPathfinder(this.tileMap)
                : null;

        // Register FloatingOriginAware listeners
        if (this.originManager != null) {
            this.originManager.registerListener(this.player);
            this.originManager.registerListener(this.camera);
            this.originManager.registerListener(this.combatSystem);
            this.originManager.registerListener(this.experienceSystem);
            this.originManager.registerListener(this.powerUpManager);
            this.originManager.registerListener(this); // for active enemies
        }

        restartMatch();
        if (startInTitle) {
            this.gameState = GameState.START;
        }
    }

    public GameSession(TileMap tileMap, SpellRegistry registry, long seed) {
        this(new TileMapAdapter(tileMap != null ? tileMap : new TileMap(seed)),
             tileMap != null ? tileMap : new TileMap(seed), null, registry, seed, false, true, false);
    }

    public GameSession(Rectangle bounds, SpellRegistry registry, long seed) {
        this(new TileMap(seed), registry, seed);
    }

    public GameSession(Rectangle bounds, SpellRegistry registry) {
        this(bounds, registry, 42L);
    }

    public GameSession() {
        this(new TileMap(42L), SpellRegistry.createDefaultRegistry(), 42L);
    }

    /**
     * Creates a Phase 2 application session starting on the Title Screen (GameState.START)
     * with BalanceConfig.defaults(), EnemySeparationSystem, debug mode disabled by default,
     * and either an explicit seed or a fresh random seed when explicitSeed is null.
     */
    public static GameSession createPhase2Session(SpellRegistry registry, Long explicitSeed, boolean debugMode) {
        long resolvedSeed = (explicitSeed != null) ? explicitSeed : new Random().nextLong();
        TileMap map = new TileMap(resolvedSeed);
        return new GameSession(new TileMapAdapter(map), map, null, registry, resolvedSeed, true, debugMode, true);
    }

    public static GameSession createPhase2Session(SpellRegistry registry, Long explicitSeed) {
        return createPhase2Session(registry, explicitSeed, false);
    }

    public static GameSession createPhase2Session() {
        return createPhase2Session(null, null, false);
    }

    /**
     * Creates an InfiniteWorld application session starting on the Title Screen (GameState.START)
     * with chunk streaming, centralized floating origin manager, and unbounded exploration.
     */
    public static GameSession createInfiniteSession(SpellRegistry registry, Long explicitSeed, boolean debugMode) {
        long resolvedSeed = (explicitSeed != null) ? explicitSeed : new Random().nextLong();
        ChunkManagerImpl chunkManager = new ChunkManagerImpl(resolvedSeed, 64);
        FloatingOriginManager originMgr = new FloatingOriginManager();
        InfiniteWorld infiniteWorld = new InfiniteWorld(chunkManager, originMgr);
        return new GameSession(infiniteWorld, null, infiniteWorld, registry, resolvedSeed, true, debugMode, true);
    }

    public static GameSession createInfiniteSession(SpellRegistry registry, Long explicitSeed) {
        return createInfiniteSession(registry, explicitSeed, false);
    }

    public static GameSession createInfiniteSession() {
        return createInfiniteSession(null, null, false);
    }

    // =========================================================================
    // Match Lifecycle Transitions
    // =========================================================================

    public void startMatch() {
        if (gameState == GameState.START) {
            gameState = GameState.PLAYING;
        }
    }

    public void returnToTitle() {
        restartMatch();
        gameState = GameState.START;
    }

    /**
     * Completely and deterministically restores all match state for a fresh game.
     */
    public void restartMatch() {
        Vector2D center = (arenaBounds != null)
                ? new Vector2D(arenaBounds.x + arenaBounds.width * 0.5f, arenaBounds.y + arenaBounds.height * 0.5f)
                : new Vector2D(0.0f, 0.0f);

        if (originManager != null) {
            originManager.reset();
        }

        // 1. Reset player vital statistics, modifiers, position, alive flag
        player.resetPlayer(center);
        camera.setZoom(GameCamera.DEFAULT_ZOOM);
        camera.setCenter(player.getPosition().x, player.getPosition().y);

        // 2. Clear all active entities and missiles
        enemies.clear();
        combatSystem.clearProjectiles();
        experienceSystem.clear();
        powerUpManager.reset();

        // 3. Reset progression systems and spawner
        spawner.reset();
        upgradeManager.reset();
        progression.reset(1);

        // 4. Close any open overlays
        levelUpOverlay.close();
        gameOverOverlay.close();
        externalOverlayBlocking = false;

        // 5. Reset match timers and metrics
        matchElapsed = 0.0f;
        enemiesDefeated = 0;
        overlayAutoTimer = 0.0f;

        // 6. Reset active spell element mixture to base Fire
        activeMix.clear();
        activeMix.add(Element.FIRE);
        currentSpell = registry.resolve(activeMix);

        // 7. Reset shooting mode to default Manual
        shootingMode = ShootingMode.MANUAL;
        player.setShootingMode(ShootingMode.MANUAL);
        toggleKeyHeld = false;
        modeNotificationTimer = 0.0f;
        modeNotificationText = "";

        // 8. Update game state
        gameState = GameState.PLAYING;
    }

    // =========================================================================
    // Shooting Mode & Target Acquisition
    // =========================================================================

    public void toggleShootingMode() {
        if (!isPlaying()) {
            return;
        }
        if (shootingMode == ShootingMode.MANUAL) {
            shootingMode = ShootingMode.AUTO;
            player.setShootingMode(ShootingMode.AUTO);
            modeNotificationTimer = 1.8f;
            modeNotificationText = "AUTO-SHOT ACTIVATED [V]";
        } else {
            shootingMode = ShootingMode.MANUAL;
            player.setShootingMode(ShootingMode.MANUAL);
            modeNotificationTimer = 1.8f;
            modeNotificationText = "MANUAL SHOOTING ACTIVATED [V]";
        }
    }

    public void setShootingMode(ShootingMode mode) {
        if (mode != null) {
            this.shootingMode = mode;
            this.player.setShootingMode(mode);
        }
    }

    public ShootingMode shootingMode() {
        return shootingMode;
    }

    public ShootingMode getShootingMode() {
        return shootingMode;
    }

    /**
     * Finds the nearest living enemy to the player.
     * Tie-breaks equidistant candidates deterministically by lowest entity ID.
     */
    public Enemy findTargetEnemy() {
        Enemy best = null;
        float bestDistSq = Float.MAX_VALUE;
        Vector2D playerPos = player.getPosition();

        for (Enemy enemy : enemies) {
            if (enemy == null || !enemy.isAlive()) {
                continue;
            }
            float dx = enemy.getPosition().x - playerPos.x;
            float dy = enemy.getPosition().y - playerPos.y;
            float dSq = dx * dx + dy * dy;

            if (dSq < bestDistSq - 1e-4f) {
                bestDistSq = dSq;
                best = enemy;
            } else if (Math.abs(dSq - bestDistSq) <= 1e-4f) {
                // Deterministic tie-breaking by lower entity ID
                if (best == null || enemy.getId() < best.getId()) {
                    best = enemy;
                }
            }
        }
        return best;
    }

    // =========================================================================
    // Spellcasting & Infusion
    // =========================================================================

    public void infuseElement(Element element) {
        if (!isPlaying() || element == null) {
            return;
        }
        activeMix.add(element);
        if (activeMix.size() > 2) {
            activeMix.remove(0);
        }
        currentSpell = registry.resolve(activeMix);
    }

    public void castSpell(Vector2D targetPos) {
        if (currentSpell == null || !player.canCast() || !player.isAlive() || !isPlaying()) {
            return;
        }

        Vector2D playerPos = player.getPosition();
        Vector2D toMouse = (targetPos != null) ? targetPos.cpy().sub(playerPos) : new Vector2D(1, 0);
        Vector2D baseDir = (toMouse.len2() > 1e-4f) ? toMouse.nor() : player.getAimDirection();

        ProjectileProfile profile = currentSpell.getProfile();
        float cdMultiplier = (profile != null) ? profile.getCooldownMultiplier() : 1.0f;
        player.triggerCast(cdMultiplier * powerUpManager.getCastCooldownMultiplier());

        final float damageBoost = powerUpManager.isElementalBurstActive() ? 1.35f : 1.0f;
        final float profDmg = (profile != null) ? profile.getDamageMultiplier() : 1.0f;
        final float baseDamage = 35.0f * profDmg * player.getDamageMultiplier() * damageBoost;

        final float profSpd = (profile != null) ? profile.getSpeedMultiplier() : 1.0f;
        final float baseSpeed = BASE_PLAYER_PROJECTILE_SPEED * profSpd * player.getProjectileSpeedMultiplier();

        final float profRad = (profile != null) ? profile.getRadius() : 9.0f;
        final float baseRadius = profRad + (powerUpManager.isElementalBurstActive() ? 3.0f : 0.0f);

        final float profLife = (profile != null) ? profile.getLifetime() : 1.2f;
        final float baseLifetime = profLife * player.getProjectileLifetimeMultiplier();

        final int profPierce = (profile != null) ? profile.getPierce() : 0;
        final int pierce = Math.min(Projectile.MAX_ALLOWED_PIERCE,
                profPierce + player.getBonusPierce() + powerUpManager.getBonusPierce());

        final int profExtra = (profile != null) ? profile.getExtraProjectiles() : 0;
        final int extra = profExtra + (powerUpManager.isElementalBurstActive() ? 1 : 0);

        Color primaryCol = currentSpell.getPrimaryColor();
        Color secondaryCol = currentSpell.getSecondaryColor();

        if (extra == 0) {
            Vector2D spawnPos = playerPos.cpy().mulAdd(baseDir, player.getRadius() + 4.0f);
            combatSystem.addProjectile(new Projectile(
                    spawnPos, baseDir, baseSpeed, baseRadius, baseDamage, baseLifetime, pierce,
                    ProjectileFaction.PLAYER, primaryCol, secondaryCol, currentSpell
            ));
        } else {
            float spreadDeg = (profile != null) ? profile.getSpreadDegrees() : 15.0f;
            float totalSpread = MathUtils.degToRad(spreadDeg * (float) extra);
            float startAngle = baseDir.angleRad() - totalSpread * 0.5f;
            float step = (extra > 0) ? (totalSpread / (float) extra) : 0.0f;

            for (int i = 0; i <= extra; i++) {
                float angle = startAngle + step * (float) i;
                Vector2D dir = Vector2D.fromAngle(angle);
                Vector2D spawnPos = playerPos.cpy().mulAdd(dir, player.getRadius() + 4.0f);
                combatSystem.addProjectile(new Projectile(
                        spawnPos, dir, baseSpeed, baseRadius, baseDamage, baseLifetime, pierce,
                        ProjectileFaction.PLAYER, primaryCol, secondaryCol, currentSpell
                ));
            }
        }
    }

    public void applySelectedLevelUpUpgrade() {
        UpgradeInfo selected = levelUpOverlay.selectedUpgrade();
        if (selected != null) {
            upgradeManager.applyUpgrade(selected.type, player);
            progression.consumeLevelUp();
            levelUpOverlay.close();

            if (progression.hasPendingLevelUp()) {
                List<UpgradeInfo> nextChoices = upgradeManager.getAvailableUpgrades(3, rng);
                if (!nextChoices.isEmpty()) {
                    levelUpOverlay.open(nextChoices);
                    gameState = GameState.LEVEL_UP;
                } else {
                    progression.consumeLevelUp();
                    gameState = GameState.PLAYING;
                }
            } else {
                gameState = GameState.PLAYING;
            }
        }
    }

    public void triggerGameOver() {
        if (gameOverOverlay.isOpen()) {
            return; // Idempotent guard
        }

        if (player.isAlive()) {
            player.destroy();
        }
        levelUpOverlay.close();
        gameOverOverlay.open(matchElapsed, enemiesDefeated, progression.getLevel());
        gameState = GameState.GAME_OVER;
    }

    // =========================================================================
    // Simulation Update Tick
    // =========================================================================

    public void update(float dt, Vector2D worldMousePos, Vector2D uiMousePos, boolean autoplay) {
        Vector2D safeWorldMouse = (worldMousePos != null) ? worldMousePos : player.getPosition();
        Vector2D safeUiMouse = (uiMousePos != null) ? uiMousePos : safeWorldMouse;

        // 0. If Title Screen is active, simulation remains completely frozen
        if (gameState == GameState.START) {
            return;
        }

        // 1. If Game Over is active, gameplay simulation remains completely frozen
        if (gameOverOverlay.isOpen()) {
            gameOverOverlay.handleMouseMove(safeUiMouse);
            gameOverOverlay.update(dt);
            gameState = GameState.GAME_OVER;
            return;
        }

        // 2. Check if player has died
        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            triggerGameOver();
            return;
        }

        // 3. If Level-Up modal is open, gameplay simulation remains frozen
        if (levelUpOverlay.isOpen()) {
            levelUpOverlay.handleMouseMove(safeUiMouse);
            levelUpOverlay.update(dt);
            gameState = GameState.LEVEL_UP;

            if (autoplay && !levelUpOverlay.getOptions().isEmpty()) {
                if (levelUpOverlay.getSelectedIndex() < 0) {
                    levelUpOverlay.selectCard(0);
                }
                overlayAutoTimer += dt;
                if (overlayAutoTimer >= 0.8f && levelUpOverlay.tryConfirm()) {
                    overlayAutoTimer = 0.0f;
                    applySelectedLevelUpUpgrade();
                }
            }
            return;
        }

        // 3b. If session is manually paused or blocked by an external modal (Codex/Settings), simulation remains frozen
        if (gameState == GameState.PAUSED || externalOverlayBlocking) {
            return;
        }

        // 4. Normal gameplay simulation tick
        gameState = GameState.PLAYING;
        matchElapsed += dt;

        if (modeNotificationTimer > 0.0f) {
            modeNotificationTimer = Math.max(0.0f, modeNotificationTimer - dt);
        }

        // 1. Centralized atomic origin check and chunk streaming
        if (originManager != null && infiniteWorld != null) {
            originManager.checkAndRebase(player.getPosition());
            infiniteWorld.getChunkManager().updateStreaming(player.getPosition(), camera);
        }

        // Player update, sliding collision resolution, and boundary clamping
        player.handleInput(dt, worldGrid);
        player.update(dt);
        if (arenaBounds != null) {
            constrainEntityToBounds(player);
        }

        // Camera smoothly follows player
        camera.update(player.getPosition().x, player.getPosition().y, dt);

        // Shooting execution based on active shooting mode
        if (autoplay) {
            Enemy target = findTargetEnemy();
            if (target != null) {
                player.setAimTarget(target.getPosition());
                if (player.canCast()) {
                    castSpell(target.getPosition());
                }
            } else {
                player.setAimTarget(safeWorldMouse);
            }
        } else if (shootingMode == ShootingMode.AUTO) {
            player.setAimTarget(safeWorldMouse);
            if (player.canCast()) {
                castSpell(safeWorldMouse);
            }
        } else {
            player.setAimTarget(safeWorldMouse);
        }

        // Update enemy spawner
        spawner.update(dt, player.getPosition(), enemies);

        // Update enemies: AI steering & boundaries
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            enemy.updateAI(dt, player.getPosition(), tileMap, pathfinder);
            enemy.update(dt);
            if (arenaBounds != null) {
                constrainEntityToBounds(enemy);
            }
        }

        // Crowd separation pass (Phase 2)
        if (separationSystem != null) {
            separationSystem.separate(enemies, tileMap, arenaBounds, dt);
        }

        // Contact damage, ranged attacks, and boss volleys
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }

            if (player.isAlive() && enemy.canAttack()) {
                float distSq = enemy.getPosition().cpy().sub(player.getPosition()).len2();
                float hitDist = enemy.getRadius() + player.getRadius();
                if (distSq <= hitDist * hitDist) {
                    player.takeDamage(enemy.getContactDamage());
                    enemy.resetAttackCooldown();
                }
            }

            if (enemy instanceof RangedEnemy ranged) {
                if (ranged.canShoot() && player.isAlive()) {
                    Projectile enemyProj = ranged.shoot(player.getPosition());
                    if (enemyProj != null) {
                        combatSystem.addProjectile(enemyProj);
                    }
                }
            }

            if (enemy instanceof BossEnemy boss) {
                if (boss.canFireVolley() && player.isAlive()) {
                    List<Projectile> bossProj = boss.fireVolley(player.getPosition());
                    if (bossProj != null) {
                        for (Projectile p : bossProj) {
                            combatSystem.addProjectile(p);
                        }
                    }
                }
            }
        }

        // Update combat system
        combatSystem.update(dt, player, enemies);

        // Process defeated enemies: idempotent reward claiming, drop experience gems, increment metrics
        for (Enemy enemy : enemies) {
            if (!enemy.isAlive()) {
                if (!enemy.isRewardClaimed()) {
                    int reward = enemy.claimReward();
                    experienceSystem.spawnPickup(enemy.getPosition(), reward);
                    enemiesDefeated++;
                }
            }
        }
        enemies.removeIf(e -> !e.isAlive());

        // Update experience pickups
        experienceSystem.update(dt, player, progression);

        // Update power-up pickups & active temporary ability (unpaused)
        powerUpManager.update(dt, player, false);

        // Check player death from combat
        if (!player.isAlive() || player.getHealth() <= 0.0f) {
            triggerGameOver();
            return;
        }

        // Check if level up occurred
        if (progression.hasPendingLevelUp()) {
            List<UpgradeInfo> choices = upgradeManager.getAvailableUpgrades(3, rng);
            if (!choices.isEmpty()) {
                levelUpOverlay.open(choices);
                gameState = GameState.LEVEL_UP;
            } else {
                progression.consumeLevelUp();
            }
        }
    }

    public void update(float dt, Vector2D worldMousePos, Vector2D uiMousePos) {
        update(dt, worldMousePos, uiMousePos, false);
    }

    public void update(float dt, Vector2D worldMousePos) {
        update(dt, worldMousePos, worldMousePos, false);
    }

    public void update(float dt) {
        update(dt, player.getPosition(), player.getPosition(), false);
    }

    @Override
    public void onOriginShift(long deltaX, long deltaY) {
        for (int i = 0; i < enemies.size(); i++) {
            Enemy enemy = enemies.get(i);
            if (enemy != null && enemy.isAlive()) {
                enemy.onOriginShift(deltaX, deltaY);
            }
        }
    }

    private void constrainEntityToBounds(com.spellstorm.entities.Entity e) {
        if (e == null || arenaBounds == null) return;
        Vector2D pos = e.getPosition();
        float r = e.getRadius();
        float minX = arenaBounds.x + r;
        float maxX = arenaBounds.x + arenaBounds.width - r;
        float minY = arenaBounds.y + r;
        float maxY = arenaBounds.y + arenaBounds.height - r;
        pos.x = MathUtils.clamp(pos.x, minX, maxX);
        pos.y = MathUtils.clamp(pos.y, minY, maxY);
    }

    // =========================================================================
    // State Queries & Accessors
    // =========================================================================

    public boolean isPlaying() {
        return gameState == GameState.PLAYING
                && !isGameOver()
                && !isLevelUpOpen()
                && !externalOverlayBlocking;
    }

    public boolean isStartScreen() {
        return gameState == GameState.START;
    }

    public boolean isGameOver() {
        return gameOverOverlay.isOpen();
    }

    public boolean isLevelUpOpen() {
        return levelUpOverlay.isOpen();
    }

    public boolean isPaused() {
        return gameState == GameState.PAUSED;
    }

    public void setPaused(boolean paused) {
        if (paused && gameState == GameState.PLAYING && !isGameOver() && !isLevelUpOpen()) {
            gameState = GameState.PAUSED;
        } else if (!paused && isPaused()) {
            gameState = GameState.PLAYING;
        }
    }

    public GameState getGameState() {
        return gameState;
    }

    public long getSeed() {
        return seed;
    }

    public SpellRegistry getRegistry() {
        return registry;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public void setDebugMode(boolean debugMode) {
        this.debugMode = debugMode;
    }

    public boolean isExternalOverlayBlocking() {
        return externalOverlayBlocking;
    }

    public void setExternalOverlayBlocking(boolean externalOverlayBlocking) {
        this.externalOverlayBlocking = externalOverlayBlocking;
    }

    public EnemySeparationSystem getSeparationSystem() {
        return separationSystem;
    }

    public Player getPlayer() { return player; }
    public Player player() { return player; }

    public GameCamera getCamera() { return camera; }
    public GameCamera camera() { return camera; }

    public TileMap getTileMap() { return tileMap; }
    public Rectangle getArenaBounds() { return arenaBounds != null ? new Rectangle(arenaBounds) : null; }
    public WorldGrid getWorldGrid() { return worldGrid; }
    public InfiniteWorld getInfiniteWorld() { return infiniteWorld; }
    public FloatingOriginManager getOriginManager() { return originManager; }
    public WorldMode getWorldMode() { return worldMode; }
    public boolean isInfiniteMode() { return worldMode == WorldMode.INFINITE_WORLD; }

    public List<Enemy> getEnemies() { return Collections.unmodifiableList(enemies); }
    public List<Enemy> enemies() { return Collections.unmodifiableList(enemies); }

    public void addEnemy(Enemy enemy) {
        if (enemy != null) {
            enemies.add(enemy);
        }
    }

    public CombatSystem getCombatSystem() { return combatSystem; }
    public CombatSystem combatSystem() { return combatSystem; }

    public ExperienceSystem getExperienceSystem() { return experienceSystem; }
    public ExperienceSystem experienceSystem() { return experienceSystem; }

    public PlayerProgression getProgression() { return progression; }
    public PlayerProgression progression() { return progression; }

    public UpgradeManager getUpgradeManager() { return upgradeManager; }
    public UpgradeManager upgradeManager() { return upgradeManager; }

    public PowerUpManager getPowerUpManager() { return powerUpManager; }
    public PowerUpManager powerUpManager() { return powerUpManager; }

    public EnemySpawner getSpawner() { return spawner; }
    public EnemySpawner spawner() { return spawner; }

    public LevelUpOverlay getLevelUpOverlay() { return levelUpOverlay; }
    public LevelUpOverlay levelUpOverlay() { return levelUpOverlay; }

    public GameOverOverlay getGameOverOverlay() { return gameOverOverlay; }
    public GameOverOverlay gameOverOverlay() { return gameOverOverlay; }

    public float getMatchElapsed() { return matchElapsed; }
    public float matchElapsed() { return matchElapsed; }

    public int getEnemiesDefeated() { return enemiesDefeated; }
    public int enemiesDefeated() { return enemiesDefeated; }

    public SpellDefinition getCurrentSpell() { return currentSpell; }
    public SpellDefinition currentSpell() { return currentSpell; }

    public List<Element> getActiveMix() { return Collections.unmodifiableList(activeMix); }
    public List<Element> activeMix() { return Collections.unmodifiableList(activeMix); }

    public float getModeNotificationTimer() { return modeNotificationTimer; }
    public String getModeNotificationText() { return modeNotificationText; }
}
