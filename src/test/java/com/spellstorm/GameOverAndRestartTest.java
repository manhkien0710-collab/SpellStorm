package com.spellstorm;

import com.badlogic.gdx.Input.Keys;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.UpgradeType;
import com.spellstorm.ui.GameOverOverlay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: Game Over Overlay, Simulation Freeze & Deterministic restartMatch()")
public class GameOverAndRestartTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Player Lethal Damage Triggers Game Over & Freezes Simulation")
    public void testPlayerDeathAndSimulationFreeze() {
        GameSession session = new GameSession();
        assertTrue(session.isPlaying());
        assertFalse(session.isGameOver());

        // Advance 5.0 seconds in active play
        for (int i = 0; i < 50; i++) {
            session.update(0.1f);
        }
        assertTrue(session.getMatchElapsed() >= 4.9f);
        assertTrue(session.isPlaying());

        // Apply lethal damage to player
        session.getPlayer().takeDamage(500.0f);
        assertEquals(0.0f, session.getPlayer().getHealth(), 1e-4f);
        assertFalse(session.getPlayer().isAlive());

        // Update session triggers Game Over
        session.update(0.016f);
        assertTrue(session.isGameOver());
        assertFalse(session.isPlaying());
        assertTrue(session.getGameOverOverlay().isOpen());

        float frozenElapsed = session.getMatchElapsed();
        float frozenSpawnerTimer = session.getSpawner().getSpawnTimer();
        int frozenEnemyCount = session.getEnemies().size();

        // 20 subsequent update ticks must NOT advance simulation
        for (int i = 0; i < 20; i++) {
            session.update(0.1f);
            assertEquals(frozenElapsed, session.getMatchElapsed(), 1e-4f, "Match timer must remain frozen");
            assertEquals(frozenSpawnerTimer, session.getSpawner().getSpawnTimer(), 1e-4f, "Spawner timer must remain frozen");
            assertEquals(frozenEnemyCount, session.getEnemies().size(), "Enemies list must not spawn");
            assertTrue(session.isGameOver());
            assertFalse(session.isPlaying());
        }
    }

    @Test
    @DisplayName("GameOverOverlay Telemetry & Formatted Time MM:SS")
    public void testTelemetryAndFormattedTime() {
        GameOverOverlay overlay = new GameOverOverlay();
        assertFalse(overlay.isOpen());

        overlay.open(85.5f, 42, 5);
        assertTrue(overlay.isOpen());
        assertEquals(85.5f, overlay.getSurvivalTime(), 1e-4f);
        assertEquals(42, overlay.getEnemiesDefeated());
        assertEquals(5, overlay.getLevelReached());
        assertEquals("01:25", overlay.formattedSurvivalTime());

        // Edge case times
        overlay.open(0.0f, 0, 1);
        assertEquals("00:00", overlay.formattedSurvivalTime());

        overlay.open(59.9f, 0, 1);
        assertEquals("00:59", overlay.formattedSurvivalTime());

        overlay.open(60.0f, 0, 1);
        assertEquals("01:00", overlay.formattedSurvivalTime());

        overlay.open(365.2f, 100, 10);
        assertEquals("06:05", overlay.formattedSurvivalTime());
    }

    @Test
    @DisplayName("GameOverOverlay Interaction: Grace Timer, Buttons & Keyboard Shortcuts")
    public void testOverlayInteraction() {
        GameOverOverlay overlay = new GameOverOverlay();
        overlay.open(120.0f, 15, 3);
        assertEquals(GameOverOverlay.GRACE_TIMER_DURATION, overlay.getOpenGraceTimer(), 1e-4f);

        Vector2D restartBtnPos = new Vector2D(
                overlay.getRestartButtonBounds().x + 10f,
                overlay.getRestartButtonBounds().y + 10f
        );

        // Click during grace timer returns NONE
        assertEquals(GameOverOverlay.Action.NONE, overlay.handleMouseClick(restartBtnPos));

        // Advance past grace timer (0.21s)
        overlay.update(0.21f);
        assertEquals(0.0f, overlay.getOpenGraceTimer(), 1e-4f);

        // Click Restart button
        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleMouseClick(restartBtnPos));

        // Click Quit button
        Vector2D quitBtnPos = new Vector2D(
                overlay.getQuitButtonBounds().x + 10f,
                overlay.getQuitButtonBounds().y + 10f
        );
        assertEquals(GameOverOverlay.Action.QUIT, overlay.handleMouseClick(quitBtnPos));

        // Click outside returns NONE
        assertEquals(GameOverOverlay.Action.NONE, overlay.handleMouseClick(new Vector2D(10f, 10f)));

        // Keyboard shortcuts
        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.R));
        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.ENTER));
        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.SPACE));
        assertEquals(GameOverOverlay.Action.QUIT, overlay.handleKeyPress(Keys.ESCAPE));
        assertEquals(GameOverOverlay.Action.QUIT, overlay.handleKeyPress(Keys.Q));
    }

    @Test
    @DisplayName("Deterministic restartMatch(): Full State Reset Across 8 Consecutive Match Cycles")
    public void testDeterministicEightCycleRestarts() {
        GameSession session = new GameSession();

        for (int cycle = 1; cycle <= 8; cycle++) {
            // Verify clean state at start of cycle
            assertTrue(session.isPlaying(), "Cycle " + cycle + " must start in PLAYING state");
            assertFalse(session.isGameOver());
            assertFalse(session.isLevelUpOpen());

            Player p = session.getPlayer();
            assertTrue(p.isAlive());
            assertEquals(250.0f, p.getHealth(), 1e-4f);
            assertEquals(250.0f, p.getMaxHealth(), 1e-4f);
            assertEquals(30.0f, p.getArmor(), 1e-4f);
            assertEquals(230.0f, p.getBaseMoveSpeed(), 1e-4f);
            assertEquals(1.0f, p.getDamageMultiplier(), 1e-4f);
            assertEquals(1.0f, p.getProjectileSpeedMultiplier(), 1e-4f);
            assertEquals(1.0f, p.getProjectileLifetimeMultiplier(), 1e-4f);
            assertEquals(0, p.getBonusPierce());
            assertTrue(p.canCast());

            assertEquals(1, session.getProgression().getLevel());
            assertEquals(0, session.getProgression().getCurrentExp());
            assertEquals(0, session.getProgression().getPendingLevelUps());

            for (UpgradeType t : UpgradeType.values()) {
                assertEquals(0, session.getUpgradeManager().getRank(t));
            }

            assertEquals(0, session.getCombatSystem().getProjectileCount());
            assertEquals(0, session.getExperienceSystem().getPickupCount());
            assertEquals(0, session.getPowerUpManager().getPickups().size());
            assertFalse(session.getPowerUpManager().hasActiveAbility());
            assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
            assertEquals(0, session.getEnemiesDefeated());

            // Mutate state during gameplay: simulate 10 seconds, apply upgrades, take damage
            for (int t = 0; t < 100; t++) {
                session.update(0.1f);
            }
            session.getUpgradeManager().applyUpgrade(UpgradeType.DAMAGE, session.getPlayer());
            session.getUpgradeManager().applyUpgrade(UpgradeType.PIERCING, session.getPlayer());
            session.getProgression().addExp(50);
            session.getPlayer().takeDamage(100.0f);

            // Kill player to trigger Game Over
            session.getPlayer().takeDamage(500.0f);
            session.update(0.016f);
            assertTrue(session.isGameOver());

            // Perform deterministic restart
            session.restartMatch();
        }
    }

    @Test
    @DisplayName("Tier 2 (Feature 34): Illegal state transitions safety (Pause during GameOver & Mid-game Restart)")
    public void testIllegalStateTransitionsSafety() {
        GameSession session = new GameSession();
        assertTrue(session.isPlaying());

        // 1. Advance gameplay and trigger Game Over
        session.getPlayer().takeDamage(500.0f);
        session.update(0.016f);
        assertTrue(session.isGameOver());
        assertEquals(GameState.GAME_OVER, session.getGameState());

        // Attempt to pause while in GAME_OVER state -> must be rejected
        session.setPaused(true);
        assertFalse(session.isPaused(), "Cannot pause when game is already in GAME_OVER state");
        assertEquals(GameState.GAME_OVER, session.getGameState());

        // Update tick in Game Over remains Game Over
        session.update(0.1f);
        assertEquals(GameState.GAME_OVER, session.getGameState());

        // 2. Restart from Game Over restores playing state
        session.restartMatch();
        assertTrue(session.isPlaying());
        assertEquals(GameState.PLAYING, session.getGameState());

        // 3. Mid-game restart: invoke restartMatch() while in PLAYING state with active elapsed time
        for (int i = 0; i < 30; i++) {
            session.update(0.1f);
        }
        assertTrue(session.getMatchElapsed() >= 2.9f);
        session.getPlayer().takeDamage(50.0f);

        // Directly restart during active match
        session.restartMatch();
        assertTrue(session.isPlaying());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
        assertEquals(250.0f, session.getPlayer().getHealth(), 1e-4f);
        assertEquals(0, session.getEnemiesDefeated());
    }
}
