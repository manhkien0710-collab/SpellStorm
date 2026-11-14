package com.spellstorm;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.GameState;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.ui.MinimapRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: Game State Transitions, HUD Layout Positioning & Minimap Bounds")
public class GameStateAndHudTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("GameState State Machine & Transitions")
    public void testStateTransitions() {
        GameSession session = new GameSession();
        // Match starts in PLAYING
        assertEquals(GameState.PLAYING, session.getGameState());
        assertTrue(session.isPlaying());
        assertFalse(session.isPaused());
        assertFalse(session.isLevelUpOpen());
        assertFalse(session.isGameOver());

        // 1. PLAYING -> PAUSED -> PLAYING
        session.setPaused(true);
        assertEquals(GameState.PAUSED, session.getGameState());
        assertTrue(session.isPaused());
        assertFalse(session.isPlaying());

        session.setPaused(false);
        assertEquals(GameState.PLAYING, session.getGameState());
        assertTrue(session.isPlaying());
        assertFalse(session.isPaused());

        // 2. PLAYING -> LEVEL_UP -> PLAYING
        session.getProgression().addExp(25); // triggers pending level-up
        session.update(0.016f);
        assertEquals(GameState.LEVEL_UP, session.getGameState());
        assertTrue(session.isLevelUpOpen());
        assertFalse(session.isPlaying());

        session.getLevelUpOverlay().update(0.20f);
        session.getLevelUpOverlay().selectCard(0);
        session.applySelectedLevelUpUpgrade();
        assertEquals(GameState.PLAYING, session.getGameState());
        assertTrue(session.isPlaying());
        assertFalse(session.isLevelUpOpen());

        // 3. PLAYING -> GAME_OVER -> PLAYING (via restartMatch)
        session.getPlayer().takeDamage(500.0f);
        session.update(0.016f);
        assertEquals(GameState.GAME_OVER, session.getGameState());
        assertTrue(session.isGameOver());
        assertFalse(session.isPlaying());

        session.restartMatch();
        assertEquals(GameState.PLAYING, session.getGameState());
        assertTrue(session.isPlaying());
        assertFalse(session.isGameOver());
    }

    @Test
    @DisplayName("Fixed Screen-Space HUD Layout Positioning (1280x720)")
    public void testHudComponentBounds() {
        // Vitals bar: x=75, y=120, w=260, h=18
        Rectangle vitalsYDown = HudRenderer.getHealthBarBounds(true);
        assertEquals(75.0f, vitalsYDown.x, 1e-4f);
        assertEquals(120.0f, vitalsYDown.y, 1e-4f);
        assertEquals(260.0f, vitalsYDown.width, 1e-4f);
        assertEquals(18.0f, vitalsYDown.height, 1e-4f);

        Rectangle vitalsYUp = HudRenderer.getHealthBarBounds(false);
        assertEquals(75.0f, vitalsYUp.x, 1e-4f);
        assertEquals(720.0f - 120.0f - 18.0f, vitalsYUp.y, 1e-4f); // 582.0f

        // EXP bar: x=75, y=148, w=260, h=12
        Rectangle expYDown = HudRenderer.getExpBarBounds(true);
        assertEquals(75.0f, expYDown.x, 1e-4f);
        assertEquals(148.0f, expYDown.y, 1e-4f);
        assertEquals(260.0f, expYDown.width, 1e-4f);
        assertEquals(12.0f, expYDown.height, 1e-4f);

        Rectangle expYUp = HudRenderer.getExpBarBounds(false);
        assertEquals(75.0f, expYUp.x, 1e-4f);
        assertEquals(720.0f - 148.0f - 12.0f, expYUp.y, 1e-4f); // 560.0f

        // Shooting Mode badge: x=75, y=172, w=204, h=22
        Rectangle modeYDown = HudRenderer.getShootingModeBadgeBounds(true);
        assertEquals(75.0f, modeYDown.x, 1e-4f);
        assertEquals(172.0f, modeYDown.y, 1e-4f);
        assertEquals(204.0f, modeYDown.width, 1e-4f);
        assertEquals(22.0f, modeYDown.height, 1e-4f);

        Rectangle modeYUp = HudRenderer.getShootingModeBadgeBounds(false);
        assertEquals(75.0f, modeYUp.x, 1e-4f);
        assertEquals(720.0f - 172.0f - 22.0f, modeYUp.y, 1e-4f); // 526.0f

        // Power-Up badge: x=75, y=202, w=230, h=34
        Rectangle powerUpYDown = HudRenderer.getPowerUpBadgeBounds(true);
        assertEquals(75.0f, powerUpYDown.x, 1e-4f);
        assertEquals(202.0f, powerUpYDown.y, 1e-4f);
        assertEquals(230.0f, powerUpYDown.width, 1e-4f);
        assertEquals(34.0f, powerUpYDown.height, 1e-4f);

        Rectangle powerUpYUp = HudRenderer.getPowerUpBadgeBounds(false);
        assertEquals(75.0f, powerUpYUp.x, 1e-4f);
        assertEquals(720.0f - 202.0f - 34.0f, powerUpYUp.y, 1e-4f); // 484.0f

        // Boss Bar: 560x22 centered horizontally at x=(1280-560)/2 = 360, y=28
        Rectangle bossYDown = HudRenderer.getBossBarBounds(1280f, 720f, true);
        assertEquals(360.0f, bossYDown.x, 1e-4f);
        assertEquals(28.0f, bossYDown.y, 1e-4f);
        assertEquals(560.0f, bossYDown.width, 1e-4f);
        assertEquals(22.0f, bossYDown.height, 1e-4f);

        Rectangle bossYUp = HudRenderer.getBossBarBounds(1280f, 720f, false);
        assertEquals(360.0f, bossYUp.x, 1e-4f);
        assertEquals(720.0f - 28.0f - 22.0f, bossYUp.y, 1e-4f); // 670.0f
    }

    @Test
    @DisplayName("Minimap Widget Bounds (150x150 at 1110, 545) & Subsampling")
    public void testMinimapBoundsAndSubsampling() {
        Rectangle mmYDown = MinimapRenderer.getMinimapBounds(true);
        assertEquals(1110.0f, mmYDown.x, 1e-4f);
        assertEquals(545.0f, mmYDown.y, 1e-4f);
        assertEquals(150.0f, mmYDown.width, 1e-4f);
        assertEquals(150.0f, mmYDown.height, 1e-4f);

        Rectangle mmYUp = MinimapRenderer.getMinimapBounds(false);
        assertEquals(1110.0f, mmYUp.x, 1e-4f);
        assertEquals(720.0f - 545.0f - 150.0f, mmYUp.y, 1e-4f); // 25.0f
        assertEquals(150.0f, mmYUp.width, 1e-4f);
        assertEquals(150.0f, mmYUp.height, 1e-4f);

        // Subsampling step constant
        assertEquals(4, MinimapRenderer.SUBSAMPLING_STEP);
    }
}
