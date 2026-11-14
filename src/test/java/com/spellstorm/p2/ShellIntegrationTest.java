package com.spellstorm.p2;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameScreen;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.core.SpellStormGame;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.math.Vector2D;
import com.spellstorm.p0.InputIntegrationTest.MockInputController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2: App Shell, Overlays, Debug Gating & Scenario Integration Tests")
public class ShellIntegrationTest {

    private Input originalInput;
    private MockInputController mockInput;

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    void setUp() {
        originalInput = Gdx.input;
        mockInput = new MockInputController();
        Gdx.input = mockInput.createProxy();
    }

    @AfterEach
    void tearDown() {
        Gdx.input = originalInput;
    }

    @Test
    @DisplayName("Full App Shell flow: START -> PLAYING -> PAUSED -> SETTINGS -> PAUSED -> START at 1920x1080")
    void testFullAppShellStateTransitionsAtScaledResolution() {
        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 2025L);
        GameScreen screen = new GameScreen(null, session, null, null);
        screen.resize(1920, 1080);

        assertEquals(GameState.START, session.getGameState());
        screen.render(0.1f);
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);

        // Click TitleScreen Play button in 1920x1080 physical pixels (1.5x scale)
        Rectangle playBtn = screen.getTitleScreen().getPlayButtonBounds();
        int clickPlayX = Math.round((playBtn.x + playBtn.width * 0.5f) * 1.5f);
        int clickPlayY = Math.round((playBtn.y + playBtn.height * 0.5f) * 1.5f);
        assertTrue(screen.touchDown(clickPlayX, clickPlayY, 0, Buttons.LEFT));
        assertEquals(GameState.PLAYING, session.getGameState());
        assertTrue(screen.getTutorialOverlay().isActive());

        // Tutorial dismisses on SPACE without triggering debug self-damage
        float hpBeforeSpace = session.getPlayer().getHealth();
        screen.keyDown(Keys.SPACE);
        assertFalse(screen.getTutorialOverlay().isActive());
        assertEquals(hpBeforeSpace, session.getPlayer().getHealth(), 1e-4f);

        // Advance simulation (render clamps each frame dt to 0.1s max)
        screen.render(0.1f);
        screen.render(0.1f);
        screen.render(0.1f);
        assertTrue(session.getMatchElapsed() > 0.2f);

        // Press ESC to open Pause Menu
        screen.keyDown(Keys.ESCAPE);
        assertEquals(GameState.PAUSED, session.getGameState());
        assertTrue(screen.getPauseMenuOverlay().isOpen());

        // Click Settings inside Pause Menu
        Rectangle settingsBtn = screen.getPauseMenuOverlay().getSettingsButtonBounds();
        screen.touchDown(Math.round((settingsBtn.x + 20) * 1.5f), Math.round((settingsBtn.y + 15) * 1.5f), 0, Buttons.LEFT);
        assertTrue(screen.getSettingsOverlay().isOpen());
        assertFalse(screen.getPauseMenuOverlay().isOpen());

        // Toggle Fullscreen & return Back to Pause Menu
        Rectangle fsBtn = screen.getSettingsOverlay().getFullscreenButtonBounds();
        screen.touchDown(Math.round((fsBtn.x + 20) * 1.5f), Math.round((fsBtn.y + 15) * 1.5f), 0, Buttons.LEFT);
        assertTrue(screen.getSettingsOverlay().isFullscreen());
        screen.keyDown(Keys.ESCAPE);
        assertFalse(screen.getSettingsOverlay().isOpen());
        assertTrue(screen.getPauseMenuOverlay().isOpen());

        // Quit to Title + confirmation -> returns to START
        Rectangle quitBtn = screen.getPauseMenuOverlay().getQuitToTitleButtonBounds();
        screen.touchDown(Math.round((quitBtn.x + 20) * 1.5f), Math.round((quitBtn.y + 15) * 1.5f), 0, Buttons.LEFT);
        assertTrue(screen.getPauseMenuOverlay().isQuitConfirmOpen());
        Rectangle confirmBtn = screen.getPauseMenuOverlay().getConfirmQuitButtonBounds();
        screen.touchDown(Math.round((confirmBtn.x + 20) * 1.5f), Math.round((confirmBtn.y + 15) * 1.5f), 0, Buttons.LEFT);
        assertEquals(GameState.START, session.getGameState());
    }

    @Test
    @DisplayName("CodexOverlay ('C') freezes simulation and closes via 'C', 'ESC', or Close button")
    void testCodexOverlayFreezesSimulation() {
        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 111L);
        GameScreen screen = new GameScreen(null, session, null, null);
        session.startMatch();
        screen.getTutorialOverlay().dismiss();

        screen.render(0.1f);
        float elapsedBefore = session.getMatchElapsed();

        // Open Codex with 'C'
        screen.keyDown(Keys.C);
        assertTrue(screen.getCodexOverlay().isVisible());

        // Render frames while Codex is open -> time must stay frozen
        for (int i = 0; i < 10; i++) {
            screen.render(0.1f);
        }
        assertEquals(elapsedBefore, session.getMatchElapsed(), 1e-4f);

        // Close Codex via Close button
        Rectangle closeBtn = screen.getCodexOverlay().getCloseButtonBounds(true);
        screen.touchDown((int) (closeBtn.x + 10), (int) (closeBtn.y + 10), 0, Buttons.LEFT);
        assertFalse(screen.getCodexOverlay().isVisible());

        // Simulation resumes
        screen.render(0.1f);
        assertTrue(session.getMatchElapsed() > elapsedBefore);
    }

    @Test
    @DisplayName("Debug keys (SPACE/H/L) are gated behind --debug in Phase 2 sessions")
    void testDebugKeysGatedByDebugMode() {
        GameSession nonDebug = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, false);
        GameScreen screenNoDebug = new GameScreen(null, nonDebug, null, null);
        nonDebug.startMatch();
        screenNoDebug.getTutorialOverlay().dismiss();

        float startHp = nonDebug.getPlayer().getHealth();
        int startExp = nonDebug.getProgression().getCurrentExp();

        assertFalse(screenNoDebug.keyDown(Keys.SPACE));
        assertFalse(screenNoDebug.keyDown(Keys.H));
        assertFalse(screenNoDebug.keyDown(Keys.L));
        assertEquals(startHp, nonDebug.getPlayer().getHealth(), 1e-4f);
        assertEquals(startExp, nonDebug.getProgression().getCurrentExp());

        GameSession debug = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 42L, true);
        GameScreen screenDebug = new GameScreen(null, debug, null, null);
        debug.startMatch();
        screenDebug.getTutorialOverlay().dismiss();

        assertTrue(screenDebug.keyDown(Keys.SPACE));
        assertTrue(debug.getPlayer().getHealth() < startHp);
        assertTrue(screenDebug.keyDown(Keys.H));
        assertTrue(screenDebug.keyDown(Keys.L));
        assertTrue(debug.getProgression().getCurrentExp() > startExp);
    }

    @Test
    @DisplayName("EnemySeparationSystem prevents enemy stacking and SpellStormGame executes --scenario flags")
    void testSeparationAndScenarioExecution() {
        // Enemy separation integration inside Phase 2 session
        GameSession sepSession = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 555L);
        sepSession.startMatch();
        Vector2D pPos = sepSession.getPlayer().getPosition();
        MeleeEnemy e1 = new MeleeEnemy(pPos.cpy().add(250f, 0f));
        MeleeEnemy e2 = new MeleeEnemy(pPos.cpy().add(250f, 0f));
        sepSession.addEnemy(e1);
        sepSession.addEnemy(e2);
        sepSession.update(0.05f);
        assertTrue(e1.getPosition().cpy().sub(e2.getPosition()).len() > 1.0f,
                "Coincident enemies must separate in Phase 2 GameSession");

        // Boss + auto-shot scenarios via --scenario
        EngineConfig bossCfg = EngineConfig.fromArgs(new String[]{"--scenario", "boss", "--scenario", "auto-shot"});
        GameSession bossSession = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 99L);
        SpellStormGame.applyConfiguredScenarios(bossSession, bossCfg);
        assertEquals(GameState.PLAYING, bossSession.getGameState());
        assertEquals(ShootingMode.AUTO, bossSession.getShootingMode());
        assertTrue(bossSession.getEnemies().stream().anyMatch(e -> e.isBoss() && e.isAlive()));

        // Level-up scenario
        EngineConfig lvlCfg = EngineConfig.fromArgs(new String[]{"--scenario", "level-up"});
        GameSession lvlSession = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 99L);
        SpellStormGame.applyConfiguredScenarios(lvlSession, lvlCfg);
        assertEquals(GameState.LEVEL_UP, lvlSession.getGameState());

        // Game-over scenario
        EngineConfig goCfg = EngineConfig.fromArgs(new String[]{"--scenario", "game-over"});
        GameSession goSession = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 99L);
        SpellStormGame.applyConfiguredScenarios(goSession, goCfg);
        assertEquals(GameState.GAME_OVER, goSession.getGameState());
    }
}
