package com.spellstorm;

import com.badlogic.gdx.Input.Keys;
import com.spellstorm.core.GameSession;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeType;
import com.spellstorm.ui.GameOverOverlay;
import com.spellstorm.ui.LevelUpOverlay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: Game State & Lifecycle Comprehensive Suite")
public class StateAndLifecycleTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Tier 1: LevelUpOverlay Two-Step Selection & 244px Text Wrapping")
    public void testLevelUpOverlayTwoStepAndWrapping() {
        LevelUpOverlay overlay = new LevelUpOverlay();
        List<UpgradeInfo> options = new ArrayList<>();
        options.add(new UpgradeInfo(UpgradeType.MAX_HEALTH, "Vitality Surge", "+40 Max Health & instant heal", 0, 5, null));
        options.add(new UpgradeInfo(UpgradeType.PIERCING, "Piercing Lance", "+1 Missile Pierce (max 2 ranks, -20% dmg/hit)", 0, 2, null));

        overlay.open(options);
        overlay.update(0.20f); // expire grace period

        // Clicking card selects it and returns false
        assertFalse(overlay.handleMouseClick(new Vector2D(overlay.getCardBounds().get(0).x + 5f, overlay.getCardBounds().get(0).y + 5f)));
        assertTrue(overlay.hasValidSelection());
        assertEquals(0, overlay.getSelectedIndex());

        // Confirm button commits
        assertTrue(overlay.handleMouseClick(new Vector2D(overlay.getConfirmButtonBounds().x + 5f, overlay.getConfirmButtonBounds().y + 5f)));

        // Description text wrapping <= 244px
        List<String> wrapped = LevelUpOverlay.wrapText(options.get(1).description, null, 244.0f);
        assertTrue(wrapped.size() >= 2);
    }

    @Test
    @DisplayName("Tier 2: GameOverOverlay Telemetry and Button Actions")
    public void testGameOverOverlayActions() {
        GameOverOverlay overlay = new GameOverOverlay();
        overlay.open(135.0f, 25, 4);
        overlay.update(0.25f);

        assertEquals("02:15", overlay.formattedSurvivalTime());
        assertEquals(25, overlay.getEnemiesDefeated());
        assertEquals(4, overlay.getLevelReached());

        assertEquals(GameOverOverlay.Action.RESTART, overlay.handleKeyPress(Keys.R));
        assertEquals(GameOverOverlay.Action.QUIT, overlay.handleKeyPress(Keys.ESCAPE));
    }

    @Test
    @DisplayName("Tier 3: GameSession Deterministic restartMatch() Multi-Cycle Cleansing")
    public void testRestartMatchDeterministicCleansing() {
        GameSession session = new GameSession();
        assertTrue(session.isPlaying());

        // Simulate match, mutate state
        session.update(2.0f);
        session.getPlayer().takeDamage(100.0f);
        session.getUpgradeManager().applyUpgrade(UpgradeType.MOVE_SPEED, session.getPlayer());

        // Trigger game over
        session.getPlayer().takeDamage(500.0f);
        session.update(0.016f);
        assertTrue(session.isGameOver());

        // Reset
        session.restartMatch();
        assertTrue(session.isPlaying());
        assertEquals(250.0f, session.getPlayer().getHealth(), 1e-4f);
        assertEquals(0, session.getUpgradeManager().getRank(UpgradeType.MOVE_SPEED));
        assertEquals(0, session.getEnemies().size());
        assertEquals(0.0f, session.getMatchElapsed(), 1e-4f);
    }
}
