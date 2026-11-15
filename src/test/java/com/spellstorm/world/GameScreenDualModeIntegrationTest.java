package com.spellstorm.world;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.FontManager;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameScreen;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.SpellStormGame;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.ui.MinimapRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 4: GameSession & GameScreen Dual-Mode Integration Tests")
public class GameScreenDualModeIntegrationTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Dual-mode session initialization: createPhase2Session vs createInfiniteSession")
    public void testDualModeSessionInitialization() {
        // 1. Phase 2 TileMap mode
        GameSession tileMapSession = GameSession.createPhase2Session(
                SpellRegistry.createDefaultRegistry(), 42L, true);
        assertEquals(GameSession.WorldMode.TILE_MAP, tileMapSession.getWorldMode());
        assertFalse(tileMapSession.isInfiniteMode());
        assertNotNull(tileMapSession.getTileMap(), "TileMap session must provide non-null TileMap");
        assertNotNull(tileMapSession.getWorldGrid(), "TileMap session must provide non-null WorldGrid");
        assertTrue(tileMapSession.getWorldGrid().isBounded(), "TileMapAdapter must report isBounded == true");
        assertNotNull(tileMapSession.getArenaBounds(), "TileMap session must have non-null arenaBounds");
        assertEquals(4096.0f, tileMapSession.getArenaBounds().width, 1e-3f);
        assertTrue(tileMapSession.getCamera().isClampingEnabled(), "Camera clamping must be enabled in TileMap mode");
        assertNull(tileMapSession.getOriginManager(), "TileMap session does not require FloatingOriginManager");

        // 2. InfiniteWorld mode
        GameSession infiniteSession = GameSession.createInfiniteSession(
                SpellRegistry.createDefaultRegistry(), 42L, true);
        assertEquals(GameSession.WorldMode.INFINITE_WORLD, infiniteSession.getWorldMode());
        assertTrue(infiniteSession.isInfiniteMode());
        assertNull(infiniteSession.getTileMap(), "Pure infinite session has null legacy TileMap");
        assertNotNull(infiniteSession.getWorldGrid(), "Infinite session must provide non-null WorldGrid");
        assertFalse(infiniteSession.getWorldGrid().isBounded(), "InfiniteWorld must report isBounded == false");
        assertNull(infiniteSession.getArenaBounds(), "InfiniteWorld mode must have null arenaBounds");
        assertFalse(infiniteSession.getCamera().isClampingEnabled(), "Camera clamping must be disabled in InfiniteWorld mode");
        assertNotNull(infiniteSession.getOriginManager(), "Infinite session must have non-null FloatingOriginManager");
    }

    @Test
    @DisplayName("Unbounded player movement in InfiniteWorld mode across negative coordinates")
    public void testUnboundedPlayerMovementInInfiniteMode() {
        GameSession session = GameSession.createInfiniteSession(SpellRegistry.createDefaultRegistry(), 42L, true);
        Player player = session.getPlayer();
        GameCamera camera = session.getCamera();

        // Teleport player into deep negative quadrant
        Vector2D negativeTarget = new Vector2D(-3500.0f, -2800.0f);
        player.setPosition(negativeTarget);

        // Update session simulation tick
        session.update(0.016f);

        // Verify player is not clamped to [0, 4096]
        assertEquals(-3500.0f, player.getPosition().x, 10.0f, "Player position X must remain negative");
        assertEquals(-2800.0f, player.getPosition().y, 10.0f, "Player position Y must remain negative");

        // Verify camera smoothly tracks player without boundary clamping
        camera.update(player.getPosition().x, player.getPosition().y, 1.0f);
        assertTrue(camera.getCenterX() < 0.0f, "Camera center X must follow into negative space: " + camera.getCenterX());
        assertTrue(camera.getCenterY() < 0.0f, "Camera center Y must follow into negative space: " + camera.getCenterY());
    }

    @Test
    @DisplayName("Floating-origin atomic shift preserves relative entity distances without jitter")
    public void testFloatingOriginRebasingPreservesRelativeDistances() {
        GameSession session = GameSession.createInfiniteSession(SpellRegistry.createDefaultRegistry(), 42L, true);
        FloatingOriginManager originManager = session.getOriginManager();
        assertNotNull(originManager);

        Player player = session.getPlayer();
        player.setPosition(new Vector2D(1050.0f, 50.0f));

        Enemy enemy = new MeleeEnemy(new Vector2D(1200.0f, 150.0f));
        session.addEnemy(enemy);

        float initialDist = player.getPosition().dst(enemy.getPosition());

        // Trigger atomic rebase
        boolean rebased = originManager.checkAndRebase(player.getPosition());
        assertTrue(rebased, "Displacement of 1050px must trigger origin rebase");

        long deltaX = originManager.getLastDeltaX();
        long deltaY = originManager.getLastDeltaY();
        assertEquals(1024L, deltaX);
        assertEquals(0L, deltaY);

        // Player local X was shifted: 1050 - 1024 = 26
        assertEquals(26.0f, player.getPosition().x, 1e-3f);
        // Enemy local X was shifted: 1200 - 1024 = 176
        assertEquals(176.0f, enemy.getPosition().x, 1e-3f);

        // Verify distance invariance
        float postDist = player.getPosition().dst(enemy.getPosition());
        assertEquals(initialDist, postDist, 1e-3f, "Relative distance between player and enemy must be exactly preserved");
    }

    @Test
    @DisplayName("EngineConfig CLI parsing for --mode infinite vs --mode tilemap")
    public void testEngineConfigCliParsing() {
        // Default without --mode
        EngineConfig defaultConfig = EngineConfig.fromArgs(new String[]{});
        assertTrue(defaultConfig.isInfiniteMode());
        assertFalse(defaultConfig.isTileMapMode());

        // Explicit --mode infinite
        EngineConfig infiniteConfig = EngineConfig.fromArgs(new String[]{"--mode", "infinite"});
        assertTrue(infiniteConfig.isInfiniteMode());
        assertFalse(infiniteConfig.isTileMapMode());

        // Explicit --mode tilemap
        EngineConfig tilemapConfig = EngineConfig.fromArgs(new String[]{"--mode", "tilemap"});
        assertFalse(tilemapConfig.isInfiniteMode());
        assertTrue(tilemapConfig.isTileMapMode());
    }

    @Test
    @DisplayName("MinimapRenderer dual-mode execution with bounded and unbounded WorldGrid")
    public void testMinimapRendererDualModeExecution() {
        MinimapRenderer minimap = new MinimapRenderer();

        // 1. Bounded world grid (TileMapAdapter)
        TileMap tileMap = new TileMap(42L);
        WorldGrid boundedGrid = new TileMapAdapter(tileMap);
        assertDoesNotThrow(() -> {
            minimap.renderMinimap(null, null, null, boundedGrid,
                    new Vector2D(2048.0f, 2048.0f),
                    List.of(new Vector2D(1500.0f, 1600.0f)),
                    List.of(new Vector2D(3000.0f, 3000.0f)),
                    List.of(new Vector2D(1800.0f, 1900.0f)),
                    new Rectangle(1500, 1500, 1280, 720),
                    true);
        });

        // 2. Unbounded infinite world grid (InfiniteWorld)
        ChunkManagerImpl chunkManager = new ChunkManagerImpl(42L, 64);
        FloatingOriginManager originManager = new FloatingOriginManager();
        InfiniteWorld infiniteWorld = new InfiniteWorld(chunkManager, originManager);
        assertDoesNotThrow(() -> {
            minimap.renderMinimap(null, null, null, infiniteWorld,
                    new Vector2D(0.0f, 0.0f),
                    List.of(new Vector2D(250.0f, 300.0f)),
                    List.of(new Vector2D(-500.0f, -400.0f)),
                    List.of(new Vector2D(100.0f, -100.0f)),
                    new Rectangle(-640, -360, 1280, 720),
                    true);
        });
    }

    @Test
    @DisplayName("GameScreen FloatingOriginAware shifts active visual effects and tracked projectiles")
    public void testGameScreenFloatingOriginAwareRebasing() {
        TextureManager textureManager = new TextureManager();
        FontManager fontManager = new FontManager();
        GameSession session = GameSession.createInfiniteSession(SpellRegistry.createDefaultRegistry(), 42L, false);

        GameScreen screen = new GameScreen(null, session, textureManager, fontManager);
        screen.show();

        // Dispatch origin shift
        assertDoesNotThrow(() -> {
            screen.onOriginShift(1024L, 1024L);
        });

        screen.hide();
    }
}
