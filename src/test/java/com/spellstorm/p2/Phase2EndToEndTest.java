package com.spellstorm.p2;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameScreen;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Entity;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.p0.InputIntegrationTest.MockInputController;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.ui.GameOverOverlay;
import com.spellstorm.ui.LevelUpOverlay;
import com.spellstorm.ui.PauseMenuOverlay;
import com.spellstorm.ui.SettingsOverlay;
import com.spellstorm.ui.TitleScreen;
import com.spellstorm.ui.ViewportManager;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 End-to-End Verification Suite")
public class Phase2EndToEndTest {

    private Input originalInput;
    private MockInputController mockInput;

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    void setUp() {
        Entity.resetIdCounter(1);
        originalInput = Gdx.input;
        mockInput = new MockInputController();
        Gdx.input = mockInput.createProxy();
    }

    @AfterEach
    void tearDown() {
        Gdx.input = originalInput;
    }

    private static int toPhysicalX(ViewportManager vm, float uiX) {
        return Math.round(vm.getViewportX() + uiX * (vm.getViewportWidth() / ViewportManager.VIRTUAL_WIDTH));
    }

    private static int toPhysicalY(ViewportManager vm, float uiY) {
        return Math.round(vm.getViewportY() + uiY * (vm.getViewportHeight() / ViewportManager.VIRTUAL_HEIGHT));
    }

    private static void clickUiBoundsCenter(GameScreen screen, Rectangle uiBounds) {
        ViewportManager vm = screen.getViewportManager();
        int px = toPhysicalX(vm, uiBounds.x + uiBounds.width * 0.5f);
        int py = toPhysicalY(vm, uiBounds.y + uiBounds.height * 0.5f);
        screen.touchDown(px, py, 0, Buttons.LEFT);
    }

    private static void assertFrozenAcrossTicks(GameSession session, GameScreen screen, int ticks, float dt) {
        float elapsedBefore = session.getMatchElapsed();
        float playerXBefore = session.getPlayer().getX();
        float playerYBefore = session.getPlayer().getY();
        float spawnerBefore = session.getSpawner().getSpawnTimer();
        float powerUpBefore = session.getPowerUpManager().getActiveAbility().getRemainingDuration();
        List<Vector2D> enemyPositionsBefore = new ArrayList<>();
        for (Enemy e : session.getEnemies()) {
            enemyPositionsBefore.add(e.getPosition().cpy());
        }

        for (int i = 0; i < ticks; i++) {
            screen.render(dt);
        }

        assertEquals(elapsedBefore, session.getMatchElapsed(), 1e-4f, "matchElapsed must remain frozen");
        assertEquals(playerXBefore, session.getPlayer().getX(), 1e-4f, "player X must remain frozen");
        assertEquals(playerYBefore, session.getPlayer().getY(), 1e-4f, "player Y must remain frozen");
        assertEquals(spawnerBefore, session.getSpawner().getSpawnTimer(), 1e-4f, "spawner timer must remain frozen");
        assertEquals(powerUpBefore, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), 1e-4f,
                "power-up duration must remain frozen");
        assertEquals(enemyPositionsBefore.size(), session.getEnemies().size(), "enemy count must remain frozen");
        for (int i = 0; i < enemyPositionsBefore.size(); i++) {
            assertEquals(enemyPositionsBefore.get(i).x, session.getEnemies().get(i).getX(), 1e-4f);
            assertEquals(enemyPositionsBefore.get(i).y, session.getEnemies().get(i).getY(), 1e-4f);
        }
    }

    @Test
    @DisplayName("Full App Shell lifecycle, Quit Confirmation (ESC never instant-exits), and strict simulation freeze")
    void testFullAppShellLifecycleAndSimulationFreeze() {
        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, false);
        GameScreen screen = new GameScreen(null, session, null, null);
        screen.resize(1280, 720);

        // 1. START (TitleScreen) — frozen simulation + ESC opens quit confirm instead of instant exit
        assertEquals(GameState.START, session.getGameState());
        assertTrue(session.isStartScreen());
        assertFrozenAcrossTicks(session, screen, 10, 0.05f);

        assertFalse(screen.getTitleScreen().isQuitConfirmOpen());
        screen.keyDown(Keys.ESCAPE);
        assertTrue(screen.getTitleScreen().isQuitConfirmOpen(), "ESC on TitleScreen must open Quit Confirmation");
        assertEquals(GameState.START, session.getGameState());
        // Cancel quit confirmation with ESC
        screen.keyDown(Keys.ESCAPE);
        assertFalse(screen.getTitleScreen().isQuitConfirmOpen());

        // 2. Title -> Play (PLAYING)
        clickUiBoundsCenter(screen, screen.getTitleScreen().getPlayButtonBounds());
        assertEquals(GameState.PLAYING, session.getGameState());
        assertTrue(session.isPlaying());
        screen.getTutorialOverlay().dismiss();

        // Seed an enemy & active power-up to verify freeze across overlays
        session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
        session.addEnemy(new MeleeEnemy(session.getPlayer().getPosition().cpy().add(260.0f, 0.0f)));
        mockInput.pressKey(Keys.D);
        screen.render(0.1f);
        mockInput.clear();
        assertTrue(session.getMatchElapsed() > 0.05f);

        // 3. Play -> Pause (PAUSED via ESC, never instant-exit) -> frozen
        screen.keyDown(Keys.ESCAPE);
        assertEquals(GameState.PAUSED, session.getGameState());
        assertTrue(screen.getPauseMenuOverlay().isOpen());
        assertFrozenAcrossTicks(session, screen, 10, 0.05f);

        // 4. Pause -> Settings -> frozen -> Back -> Pause
        clickUiBoundsCenter(screen, screen.getPauseMenuOverlay().getSettingsButtonBounds());
        assertTrue(screen.getSettingsOverlay().isOpen());
        assertFalse(screen.getPauseMenuOverlay().isOpen());
        assertFrozenAcrossTicks(session, screen, 10, 0.05f);

        clickUiBoundsCenter(screen, screen.getSettingsOverlay().getBackButtonBounds());
        assertFalse(screen.getSettingsOverlay().isOpen());
        assertTrue(screen.getPauseMenuOverlay().isOpen());

        // Pause Quit-to-Title requires confirmation too
        clickUiBoundsCenter(screen, screen.getPauseMenuOverlay().getQuitToTitleButtonBounds());
        assertTrue(screen.getPauseMenuOverlay().isQuitConfirmOpen());
        clickUiBoundsCenter(screen, screen.getPauseMenuOverlay().getCancelQuitButtonBounds());
        assertFalse(screen.getPauseMenuOverlay().isQuitConfirmOpen());
        assertEquals(GameState.PAUSED, session.getGameState());

        // 5. Pause -> Resume (PLAYING)
        clickUiBoundsCenter(screen, screen.getPauseMenuOverlay().getResumeButtonBounds());
        assertFalse(screen.getPauseMenuOverlay().isOpen());
        assertEquals(GameState.PLAYING, session.getGameState());

        // 6. Play -> Codex ('C') -> frozen -> close via 'C'
        screen.keyDown(Keys.C);
        assertTrue(screen.getCodexOverlay().isVisible());
        assertFalse(session.isPlaying());
        assertFrozenAcrossTicks(session, screen, 10, 0.05f);

        screen.keyDown(Keys.C);
        assertFalse(screen.getCodexOverlay().isVisible());
        screen.render(0.016f);
        assertTrue(session.isPlaying());

        // 7. Play -> Level-Up -> frozen -> select & confirm -> PLAYING
        session.getProgression().addExp(session.getProgression().getExpForNextLevel());
        screen.render(0.05f);
        assertEquals(GameState.LEVEL_UP, session.getGameState());
        assertTrue(session.isLevelUpOpen());
        assertFrozenAcrossTicks(session, screen, 10, 0.05f);

        LevelUpOverlay lvlOverlay = session.getLevelUpOverlay();
        assertFalse(lvlOverlay.getCardBounds().isEmpty());
        clickUiBoundsCenter(screen, lvlOverlay.getCardBounds().get(0));
        clickUiBoundsCenter(screen, lvlOverlay.getConfirmButtonBounds());
        assertEquals(GameState.PLAYING, session.getGameState());
        assertFalse(session.isLevelUpOpen());

        // 8. Play -> Game-Over -> frozen -> Restart
        session.getPlayer().takeDamage(9999.0f);
        screen.render(0.05f);
        assertEquals(GameState.GAME_OVER, session.getGameState());
        assertTrue(session.isGameOver());
        assertFrozenAcrossTicks(session, screen, 10, 0.05f);

        // Advance past GameOverOverlay grace timer, then click Restart
        for (int i = 0; i < 6; i++) {
            screen.render(0.05f);
        }
        GameOverOverlay goOverlay = session.getGameOverOverlay();
        ViewportManager vm = screen.getViewportManager();
        Rectangle restartBtn = goOverlay.getRestartButtonBounds();
        int rx = toPhysicalX(vm, restartBtn.x + restartBtn.width * 0.5f);
        int ry = toPhysicalY(vm, restartBtn.y + restartBtn.height * 0.5f);
        screen.touchDown(rx, ry, 0, Buttons.LEFT);
        screen.touchUp(rx, ry, 0, Buttons.LEFT);

        assertEquals(GameState.PLAYING, session.getGameState());
        assertFalse(session.isGameOver());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
        assertEquals(0, session.getEnemiesDefeated());
        assertEquals(1, session.getProgression().getLevel());
        assertTrue(session.getPlayer().isAlive());

        // Verify fresh Phase 2 session factory generates a distinct new seed when unseeded
        Set<Long> freshSeeds = new HashSet<>();
        for (int i = 0; i < 6; i++) {
            freshSeeds.add(GameSession.createPhase2Session().getSeed());
        }
        assertTrue(freshSeeds.size() >= 2, "Fresh unseeded sessions must produce varied seeds");
    }

    @Test
    @DisplayName("--debug gating: SPACE/H/L ignored when debugMode=false, active when debugMode=true")
    void testDebugModeGating() {
        EngineConfig noDebugCfg = EngineConfig.fromArgs(new String[]{"--seed", "42"});
        assertFalse(noDebugCfg.isDebugMode());

        GameSession noDebugSession = GameSession.createPhase2Session(
                SpellRegistry.createDefaultRegistry(), noDebugCfg.getSeed(), noDebugCfg.isDebugMode());
        GameScreen noDebugScreen = new GameScreen(null, noDebugSession, null, null);
        noDebugSession.startMatch();
        noDebugScreen.getTutorialOverlay().dismiss();

        float baseHp = noDebugSession.getPlayer().getHealth();
        int baseExp = noDebugSession.getProgression().getCurrentExp();

        assertFalse(noDebugScreen.keyDown(Keys.SPACE));
        assertFalse(noDebugScreen.keyDown(Keys.H));
        assertFalse(noDebugScreen.keyDown(Keys.L));
        assertEquals(baseHp, noDebugSession.getPlayer().getHealth(), 1e-4f);
        assertEquals(baseExp, noDebugSession.getProgression().getCurrentExp());

        EngineConfig debugCfg = EngineConfig.fromArgs(new String[]{"--seed", "42", "--debug"});
        assertTrue(debugCfg.isDebugMode());

        GameSession debugSession = GameSession.createPhase2Session(
                SpellRegistry.createDefaultRegistry(), debugCfg.getSeed(), debugCfg.isDebugMode());
        GameScreen debugScreen = new GameScreen(null, debugSession, null, null);
        debugSession.startMatch();
        debugScreen.getTutorialOverlay().dismiss();

        assertTrue(debugScreen.keyDown(Keys.SPACE));
        float afterDamageHp = debugSession.getPlayer().getHealth();
        assertTrue(afterDamageHp < baseHp, "SPACE with --debug must damage player");

        assertTrue(debugScreen.keyDown(Keys.H));
        assertTrue(debugSession.getPlayer().getHealth() > afterDamageHp, "H with --debug must heal player");

        assertTrue(debugScreen.keyDown(Keys.L));
        assertTrue(debugSession.getProgression().getLevel() > 1 || debugSession.getProgression().getCurrentExp() > baseExp,
                "L with --debug must grant +40 EXP (level up or increase EXP)");
        assertEquals(2, debugSession.getProgression().getLevel());
        assertEquals(15, debugSession.getProgression().getCurrentExp());
    }

    @Test
    @DisplayName("Seed reproducibility: two sessions with seed=42 produce identical snapshots; random seed factory varies")
    void testSeedReproducibilityAndRandomSeedFactory() {
        String snapA = runDeterministicSnapshot(42L);
        String snapB = runDeterministicSnapshot(42L);
        assertEquals(snapA, snapB, "Identical seed=42 sessions must yield bit-for-bit identical simulation snapshots");

        String snapDifferent = runDeterministicSnapshot(999L);
        assertNotEquals(snapA, snapDifferent, "Distinct seeds should produce distinct simulation states");

        Set<Long> randomSeeds = new HashSet<>();
        for (int i = 0; i < 8; i++) {
            randomSeeds.add(GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), null).getSeed());
        }
        assertTrue(randomSeeds.size() >= 4, "Random seed factory must produce varied seeds, got " + randomSeeds);
    }

    private String runDeterministicSnapshot(long seed) {
        Entity.resetIdCounter(1);
        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), seed);
        session.startMatch();
        session.infuseElement(Element.WIND); // Firestorm

        for (int i = 0; i < 300; i++) {
            session.update(0.05f, session.getPlayer().getPosition(), session.getPlayer().getPosition(), true);
            if (session.isLevelUpOpen()) {
                session.getLevelUpOverlay().selectCard(0);
                session.applySelectedLevelUpUpgrade();
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("seed=").append(session.getSeed())
                .append("|elapsed=").append(String.format("%.4f", session.getMatchElapsed()))
                .append("|px=").append(String.format("%.4f", session.getPlayer().getX()))
                .append("|py=").append(String.format("%.4f", session.getPlayer().getY()))
                .append("|hp=").append(String.format("%.4f", session.getPlayer().getHealth()))
                .append("|lvl=").append(session.getProgression().getLevel())
                .append("|exp=").append(session.getProgression().getCurrentExp())
                .append("|kills=").append(session.getEnemiesDefeated())
                .append("|enemies=").append(session.getEnemies().size());
        for (Enemy e : session.getEnemies()) {
            sb.append("|[").append(e.getType())
                    .append(",").append(String.format("%.3f", e.getX()))
                    .append(",").append(String.format("%.3f", e.getY()))
                    .append(",").append(String.format("%.3f", e.getHealth())).append("]");
        }
        return sb.toString();
    }

    @Test
    @DisplayName("ViewportManager screenToUi/screenToWorld and overlay hitboxes stay aligned across 16:9 and non-16:9 windows")
    void testViewportAndOverlayHitboxAlignmentAcrossResolutions() {
        int[][] windowSizes = {
                {1280, 720},
                {1920, 1080},
                {2560, 1440},
                {1280, 1024}, // 5:4 vertical letterbox
                {2560, 1080}, // 21:9 horizontal pillarbox
                {1600, 1200}  // 4:3 vertical letterbox
        };

        ViewportManager vm = new ViewportManager();
        GameCamera cam = new GameCamera(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
        cam.setCenter(2048.0f, 2048.0f);
        cam.setZoom(1.35f);

        vm.update(1280, 720);
        Vector2D baselineWorldCorner = vm.screenToWorld(cam, 320.0f, 180.0f);
        Vector2D baselineWorldCenter = vm.screenToWorld(cam, 640.0f, 360.0f);

        for (int[] size : windowSizes) {
            int w = size[0];
            int h = size[1];
            vm.update(w, h);

            // Center unprojection
            Vector2D uiCenter = vm.screenToUi(w * 0.5f, h * 0.5f);
            assertEquals(640.0f, uiCenter.x, 0.05f, "UI center X mismatch at " + w + "x" + h);
            assertEquals(360.0f, uiCenter.y, 0.05f, "UI center Y mismatch at " + w + "x" + h);

            Vector2D worldCenter = vm.screenToWorld(cam, w * 0.5f, h * 0.5f);
            assertEquals(baselineWorldCenter.x, worldCenter.x, 0.1f, "World center X mismatch at " + w + "x" + h);
            assertEquals(baselineWorldCenter.y, worldCenter.y, 0.1f, "World center Y mismatch at " + w + "x" + h);

            // Quarter-point inside active viewport unprojects identically to baseline
            float quarterScreenX = vm.getViewportX() + vm.getViewportWidth() * 0.25f;
            float quarterScreenY = vm.getViewportY() + vm.getViewportHeight() * 0.25f;
            Vector2D uiQuarter = vm.screenToUi(quarterScreenX, quarterScreenY);
            assertEquals(320.0f, uiQuarter.x, 0.05f);
            assertEquals(180.0f, uiQuarter.y, 0.05f);

            Vector2D worldQuarter = vm.screenToWorld(cam, quarterScreenX, quarterScreenY);
            assertEquals(baselineWorldCorner.x, worldQuarter.x, 0.1f);
            assertEquals(baselineWorldCorner.y, worldQuarter.y, 0.1f);

            // Verify real overlay button hits through physical screen -> screenToUi at this window size
            TitleScreen title = new TitleScreen();
            Rectangle playBounds = title.getPlayButtonBounds();
            assertEquals(TitleScreen.Action.PLAY,
                    title.handleMouseClick(vm.screenToUi(
                            toPhysicalX(vm, playBounds.x + playBounds.width * 0.5f),
                            toPhysicalY(vm, playBounds.y + playBounds.height * 0.5f))),
                    "Title Play button misaligned at " + w + "x" + h);

            PauseMenuOverlay pause = new PauseMenuOverlay();
            pause.open();
            Rectangle settingsBtn = pause.getSettingsButtonBounds();
            assertEquals(PauseMenuOverlay.Action.SETTINGS,
                    pause.handleMouseClick(vm.screenToUi(
                            toPhysicalX(vm, settingsBtn.x + settingsBtn.width * 0.5f),
                            toPhysicalY(vm, settingsBtn.y + settingsBtn.height * 0.5f))),
                    "Pause Settings button misaligned at " + w + "x" + h);

            SettingsOverlay settings = new SettingsOverlay();
            settings.open();
            Rectangle backBtn = settings.getBackButtonBounds();
            assertEquals(SettingsOverlay.Action.BACK,
                    settings.handleMouseClick(vm.screenToUi(
                            toPhysicalX(vm, backBtn.x + backBtn.width * 0.5f),
                            toPhysicalY(vm, backBtn.y + backBtn.height * 0.5f))),
                    "Settings Back button misaligned at " + w + "x" + h);
        }
    }

    private static class SoakMetrics {
        int kills;
        int finalLevel;
        float survivalTime;
        float firstDeathTime = -1.0f;
        float bossSpawnTime = -1.0f;
        int maxConcurrentEnemies;
        float minInterEnemyDistance = Float.MAX_VALUE;
        int overlapPairsObserved;
        float maxNormalEnemySpeed;
    }

    private SoakMetrics run120sSoak(boolean phase2) {
        Entity.resetIdCounter(1);
        GameSession session;
        if (phase2) {
            session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L);
            session.startMatch();
        } else {
            session = new GameSession(new TileMap(42L), SpellRegistry.createDefaultRegistry(), 42L);
        }

        SoakMetrics m = new SoakMetrics();
        final float dt = 0.05f;
        int ticks = 0;

        while (session.getMatchElapsed() < 120.0f && ticks < 3500) {
            ticks++;
            session.update(dt, session.getPlayer().getPosition(), session.getPlayer().getPosition(), true);

            if (session.isLevelUpOpen()) {
                session.getLevelUpOverlay().selectCard(0);
                session.applySelectedLevelUpUpgrade();
            }

            if (session.isGameOver()) {
                if (m.firstDeathTime < 0.0f) {
                    m.firstDeathTime = session.getMatchElapsed();
                }
                break;
            }

            List<Enemy> enemies = session.getEnemies();
            m.maxConcurrentEnemies = Math.max(m.maxConcurrentEnemies, enemies.size());

            for (int i = 0; i < enemies.size(); i++) {
                Enemy e1 = enemies.get(i);
                if (!e1.isAlive()) continue;
                if (e1.isBoss() && m.bossSpawnTime < 0.0f) {
                    m.bossSpawnTime = session.getMatchElapsed();
                }
                if (!e1.isBoss()) {
                    m.maxNormalEnemySpeed = Math.max(m.maxNormalEnemySpeed, e1.getSpeed());
                }
                for (int j = i + 1; j < enemies.size(); j++) {
                    Enemy e2 = enemies.get(j);
                    if (!e2.isAlive()) continue;
                    float dist = e1.getPosition().dist(e2.getPosition());
                    m.minInterEnemyDistance = Math.min(m.minInterEnemyDistance, dist);
                    if (dist < (e1.getRadius() + e2.getRadius()) * 0.5f) {
                        m.overlapPairsObserved++;
                    }
                }
            }
        }

        m.kills = session.getEnemiesDefeated();
        m.finalLevel = session.getProgression().getLevel();
        m.survivalTime = session.getMatchElapsed();
        return m;
    }

    @Test
    @DisplayName("120s Phase 2 Balance soak simulation vs Phase 1 legacy soak")
    void test120sPhase2VsPhase1BalanceSoak() {
        SoakMetrics p2 = run120sSoak(true);
        SoakMetrics p1 = run120sSoak(false);

        System.out.printf("[SOAK-PHASE2] survival=%.2fs death=%.2fs kills=%d level=%d bossSpawn=%.2fs maxConcurrent=%d minEnemyDist=%.2f maxNormalSpeed=%.1f overlapPairs=%d%n",
                p2.survivalTime, p2.firstDeathTime, p2.kills, p2.finalLevel, p2.bossSpawnTime,
                p2.maxConcurrentEnemies, p2.minInterEnemyDistance, p2.maxNormalEnemySpeed, p2.overlapPairsObserved);
        System.out.printf("[SOAK-LEGACY] survival=%.2fs death=%.2fs kills=%d level=%d bossSpawn=%.2fs maxConcurrent=%d minEnemyDist=%.2f maxNormalSpeed=%.1f overlapPairs=%d%n",
                p1.survivalTime, p1.firstDeathTime, p1.kills, p1.finalLevel, p1.bossSpawnTime,
                p1.maxConcurrentEnemies, p1.minInterEnemyDistance, p1.maxNormalEnemySpeed, p1.overlapPairsObserved);

        assertTrue(p2.kills > 0, "Phase 2 soak must defeat enemies");
        assertTrue(p2.maxNormalEnemySpeed <= 230.0f,
                "No normal enemy may exceed player speed 230.0, got " + p2.maxNormalEnemySpeed);
        assertTrue(p2.maxConcurrentEnemies <= 40,
                "Phase 2 enemy cap must not exceed 40, got " + p2.maxConcurrentEnemies);

        // If player died before 60s under stationary autoplay, also run an endurance 120s soak with debug heal to verify boss & late-wave separation
        Entity.resetIdCounter(1);
        GameSession p2Endurance = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L);
        p2Endurance.startMatch();
        float enduranceBossSpawn = -1.0f;
        int enduranceMaxConcurrent = 0;
        float enduranceMinDist = Float.MAX_VALUE;
        int enduranceTicks = 0;
        while (p2Endurance.getMatchElapsed() < 120.0f && enduranceTicks < 3500) {
            enduranceTicks++;
            p2Endurance.update(0.05f, p2Endurance.getPlayer().getPosition(), p2Endurance.getPlayer().getPosition(), true);
            if (p2Endurance.isLevelUpOpen()) {
                p2Endurance.getLevelUpOverlay().selectCard(0);
                p2Endurance.applySelectedLevelUpUpgrade();
            }
            if (p2Endurance.getPlayer().getHealth() < 120.0f) {
                p2Endurance.getPlayer().heal(150.0f);
            }
            List<Enemy> enemies = p2Endurance.getEnemies();
            enduranceMaxConcurrent = Math.max(enduranceMaxConcurrent, enemies.size());
            for (int i = 0; i < enemies.size(); i++) {
                Enemy e1 = enemies.get(i);
                if (!e1.isAlive()) continue;
                if (e1.isBoss() && enduranceBossSpawn < 0.0f) {
                    enduranceBossSpawn = p2Endurance.getMatchElapsed();
                }
                if (!e1.isBoss()) {
                    assertTrue(e1.getSpeed() <= 230.0f);
                }
                for (int j = i + 1; j < enemies.size(); j++) {
                    Enemy e2 = enemies.get(j);
                    if (!e2.isAlive()) continue;
                    enduranceMinDist = Math.min(enduranceMinDist, e1.getPosition().dist(e2.getPosition()));
                }
            }
        }
        System.out.printf("[SOAK-PHASE2-120S-ENDURANCE] elapsed=%.2fs kills=%d level=%d bossSpawn=%.2fs maxConcurrent=%d minEnemyDist=%.2f%n",
                p2Endurance.getMatchElapsed(), p2Endurance.getEnemiesDefeated(),
                p2Endurance.getProgression().getLevel(), enduranceBossSpawn,
                enduranceMaxConcurrent, enduranceMinDist);

        assertTrue(p2Endurance.getMatchElapsed() >= 120.0f);
        assertTrue(enduranceBossSpawn >= 59.0f && enduranceBossSpawn <= 65.0f,
                "Boss must spawn near 60s, got " + enduranceBossSpawn);
        assertTrue(enduranceMinDist > 5.0f,
                "EnemySeparationSystem must prevent enemy point-collapse, minDist=" + enduranceMinDist);
        assertTrue(p2Endurance.getProgression().getLevel() >= 2);
        assertTrue(p2Endurance.getEnemiesDefeated() > 0);
    }
}
