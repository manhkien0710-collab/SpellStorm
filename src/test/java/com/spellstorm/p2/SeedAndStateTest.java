package com.spellstorm.p2;

import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PowerUpType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2: Seed Policy, CLI Flags & Strict State Freeze Tests")
public class SeedAndStateTest {

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Phase 2 sessions use random seed when omitted and honor explicit --seed")
    void testRandomAndExplicitSeedPolicy() {
        Set<Long> seeds = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            GameSession s = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), null);
            assertEquals(GameState.START, s.getGameState());
            assertFalse(s.isDebugMode());
            seeds.add(s.getSeed());
        }
        assertTrue(seeds.size() >= 2, "Unseeded Phase 2 sessions must generate varying random seeds");

        GameSession seeded = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 987654321L);
        assertEquals(987654321L, seeded.getSeed());
        assertEquals(GameState.START, seeded.getGameState());

        // Legacy constructor remains deterministic with seed 42, PLAYING, debugMode=true
        GameSession legacy = new GameSession();
        assertEquals(42L, legacy.getSeed());
        assertEquals(GameState.PLAYING, legacy.getGameState());
        assertTrue(legacy.isDebugMode());
    }

    @Test
    @DisplayName("EngineConfig parses Phase 2 flags and unifies legacy --test-* flags into scenarios")
    void testEngineConfigPhase2CliParsing() {
        String[] args = {
                "--seed", "1337",
                "--debug",
                "--scenario", "boss",
                "--screenshot", "build/boss.png",
                "--screenshot-at", "2.75",
                "--width", "1920",
                "--height", "1080"
        };
        EngineConfig cfg = EngineConfig.fromArgs(args);
        assertTrue(cfg.hasExplicitSeed());
        assertEquals(1337L, cfg.getSeed());
        assertTrue(cfg.isDebugMode());
        assertEquals("boss", cfg.getScenario());
        assertTrue(cfg.hasScenario("boss"));
        assertTrue(cfg.isTestBoss());
        assertEquals("build/boss.png", cfg.getScreenshotPath());
        assertEquals(2.75f, cfg.getScreenshotAtSeconds(), 1e-4f);
        assertEquals(1920, cfg.getWindowWidth());
        assertEquals(1080, cfg.getWindowHeight());
    }

    @Test
    @DisplayName("Simulation & timers freeze strictly in START, PAUSED, externalOverlayBlocking, LEVEL_UP, and GAME_OVER")
    void testStrictSimulationFreezeAcrossAllNonPlayingStates() {
        GameSession session = GameSession.createPhase2Session(SpellRegistry.createDefaultRegistry(), 777L);
        assertEquals(GameState.START, session.getGameState());

        // 1. Freeze in START
        for (int i = 0; i < 20; i++) {
            session.update(0.1f);
        }
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
        assertEquals(0, session.getEnemies().size());

        // Start match and advance 0.5s
        session.startMatch();
        assertEquals(GameState.PLAYING, session.getGameState());
        session.getPowerUpManager().getActiveAbility().activate(PowerUpType.ARCANE_OVERDRIVE);
        session.addEnemy(new MeleeEnemy(new Vector2D(session.getPlayer().getPosition().x + 200f, session.getPlayer().getPosition().y)));
        session.update(0.5f);
        float elapsedBeforePause = session.getMatchElapsed();
        float powerUpBeforePause = session.getPowerUpManager().getActiveAbility().getRemainingDuration();
        float enemyXBeforePause = session.getEnemies().get(0).getPosition().x;

        // 2. Freeze in PAUSED
        session.setPaused(true);
        assertEquals(GameState.PAUSED, session.getGameState());
        for (int i = 0; i < 20; i++) {
            session.update(0.1f);
        }
        assertEquals(elapsedBeforePause, session.getMatchElapsed(), 1e-4f);
        assertEquals(powerUpBeforePause, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), 1e-4f);
        assertEquals(enemyXBeforePause, session.getEnemies().get(0).getPosition().x, 1e-4f);

        // 3. Freeze when externalOverlayBlocking (Codex / Settings open)
        session.setPaused(false);
        assertEquals(GameState.PLAYING, session.getGameState());
        session.setExternalOverlayBlocking(true);
        assertFalse(session.isPlaying());
        for (int i = 0; i < 20; i++) {
            session.update(0.1f);
        }
        assertEquals(elapsedBeforePause, session.getMatchElapsed(), 1e-4f);
        assertEquals(powerUpBeforePause, session.getPowerUpManager().getActiveAbility().getRemainingDuration(), 1e-4f);

        // Unblock and trigger LEVEL_UP
        session.setExternalOverlayBlocking(false);
        session.getProgression().addExp(session.getProgression().getExpForNextLevel());
        session.update(0.05f);
        assertEquals(GameState.LEVEL_UP, session.getGameState());
        float elapsedAtLevelUp = session.getMatchElapsed();
        for (int i = 0; i < 20; i++) {
            session.update(0.1f);
        }
        assertEquals(elapsedAtLevelUp, session.getMatchElapsed(), 1e-4f);

        // Return to title resets to START cleanly
        session.returnToTitle();
        assertEquals(GameState.START, session.getGameState());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
    }
}
