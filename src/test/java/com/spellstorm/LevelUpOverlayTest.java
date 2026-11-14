package com.spellstorm;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.core.GameSession;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.UpgradeInfo;
import com.spellstorm.systems.UpgradeType;
import com.spellstorm.ui.LevelUpOverlay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 6: LevelUpOverlay Two-Step Selection, 0.15s Grace Timer & 244px Text Wrapping")
public class LevelUpOverlayTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    private List<UpgradeInfo> createSampleOptions() {
        List<UpgradeInfo> options = new ArrayList<>();
        options.add(new UpgradeInfo(UpgradeType.MAX_HEALTH, "Vitality Surge", "+40 Max Health & instant heal", 0, 5, Color.RED));
        options.add(new UpgradeInfo(UpgradeType.MOVE_SPEED, "Fleet Footwork", "+12% Movement Speed", 0, 5, Color.GREEN));
        options.add(new UpgradeInfo(UpgradeType.DAMAGE, "Arcane Might", "+20% Spell Damage", 0, 5, Color.ORANGE));
        return options;
    }

    @Test
    @DisplayName("LevelUpOverlay Initialization and Card Dimensions")
    public void testLayoutAndDimensions() {
        LevelUpOverlay overlay = new LevelUpOverlay();
        assertFalse(overlay.isOpen());

        List<UpgradeInfo> options = createSampleOptions();
        overlay.open(options);
        assertTrue(overlay.isOpen());
        assertEquals(3, overlay.getOptions().size());
        assertEquals(-1, overlay.getSelectedIndex());
        assertFalse(overlay.hasValidSelection());

        List<Rectangle> cards = overlay.getCardBounds();
        assertEquals(3, cards.size());
        for (Rectangle r : cards) {
            assertEquals(LevelUpOverlay.CARD_WIDTH, r.width, 1e-4f);
            assertEquals(LevelUpOverlay.CARD_HEIGHT, r.height, 1e-4f);
        }

        // Spacing check: 35px
        assertEquals(LevelUpOverlay.CARD_SPACING, cards.get(1).x - (cards.get(0).x + cards.get(0).width), 1e-4f);
        assertEquals(LevelUpOverlay.CARD_SPACING, cards.get(2).x - (cards.get(1).x + cards.get(1).width), 1e-4f);

        // Confirm button dimensions
        Rectangle confirmBtn = overlay.getConfirmButtonBounds();
        assertEquals(LevelUpOverlay.CONFIRM_BTN_WIDTH, confirmBtn.width, 1e-4f);
        assertEquals(LevelUpOverlay.CONFIRM_BTN_HEIGHT, confirmBtn.height, 1e-4f);
    }

    @Test
    @DisplayName("0.15s Grace Timer Discards Accidental Clicks")
    public void testGraceTimer() {
        LevelUpOverlay overlay = new LevelUpOverlay();
        overlay.open(createSampleOptions());
        assertEquals(LevelUpOverlay.GRACE_TIMER_DURATION, overlay.getOpenGraceTimer(), 1e-4f);

        // Click while grace timer is active is rejected
        Rectangle card0 = overlay.getCardBounds().get(0);
        Vector2D clickPos = new Vector2D(card0.x + 10f, card0.y + 10f);
        boolean result = overlay.handleMouseClick(clickPos);
        assertFalse(result);
        assertEquals(-1, overlay.getSelectedIndex(), "Selection must not change during grace period");

        // Advance past grace timer (0.16s)
        overlay.update(0.16f);
        assertEquals(0.0f, overlay.getOpenGraceTimer(), 1e-4f);

        // Now click works
        result = overlay.handleMouseClick(clickPos);
        assertFalse(result, "Card click selects but does NOT confirm");
        assertEquals(0, overlay.getSelectedIndex(), "Card 0 is now selected");
    }

    @Test
    @DisplayName("Two-Step Selection Flow: Card Click (Selects) -> Confirm Button (Commits)")
    public void testTwoStepSelection() {
        LevelUpOverlay overlay = new LevelUpOverlay();
        overlay.open(createSampleOptions());
        overlay.update(0.20f); // expire grace period

        Rectangle card1 = overlay.getCardBounds().get(1);
        Rectangle confirmBtn = overlay.getConfirmButtonBounds();

        // 1. Click Confirm button with no selection -> returns false (disabled)
        assertFalse(overlay.hasValidSelection());
        boolean confirmedWithoutSelection = overlay.handleMouseClick(new Vector2D(confirmBtn.x + 5f, confirmBtn.y + 5f));
        assertFalse(confirmedWithoutSelection);

        // 2. Step 1: Click Card 1 -> sets selectedIndex=1, returns FALSE (does not commit)
        boolean cardClickResult = overlay.handleMouseClick(new Vector2D(card1.x + 50f, card1.y + 50f));
        assertFalse(cardClickResult, "Step 1 must return false and keep modal open");
        assertTrue(overlay.hasValidSelection());
        assertEquals(1, overlay.getSelectedIndex());
        assertEquals(UpgradeType.MOVE_SPEED, overlay.getSelectedUpgrade().type);

        // 3. Step 2: Click Confirm button -> returns TRUE (commits upgrade)
        boolean confirmClickResult = overlay.handleMouseClick(new Vector2D(confirmBtn.x + 50f, confirmBtn.y + 20f));
        assertTrue(confirmClickResult, "Step 2 confirm click must return true");

        // 4. Click outside returns false
        assertFalse(overlay.handleMouseClick(new Vector2D(10f, 10f)));
    }

    @Test
    @DisplayName("Keyboard Navigation: 1, 2, 3 Selects; Enter/Space Confirms")
    public void testKeyboardInteraction() {
        LevelUpOverlay overlay = new LevelUpOverlay();
        overlay.open(createSampleOptions());
        overlay.update(0.20f);

        // Enter without selection returns false
        assertFalse(overlay.handleKeyPress(Keys.ENTER));

        // Press '1' selects card 0
        assertFalse(overlay.handleKeyPress(Keys.NUM_1));
        assertEquals(0, overlay.getSelectedIndex());

        // Press '3' selects card 2
        assertFalse(overlay.handleKeyPress(Keys.NUM_3));
        assertEquals(2, overlay.getSelectedIndex());

        // Press Enter confirms
        assertTrue(overlay.handleKeyPress(Keys.ENTER));

        // Switch selection to card 1 with NUM_2 and confirm with Space
        assertFalse(overlay.handleKeyPress(Keys.NUM_2));
        assertEquals(1, overlay.getSelectedIndex());
        assertTrue(overlay.handleKeyPress(Keys.SPACE));
    }

    @Test
    @DisplayName("244px Text Wrapping: wrapText enforces maxWidth constraint")
    public void testTextWrapping() {
        // Long description from Piercing Lance
        String piercingDesc = "+1 Missile Pierce (max 2 ranks, -20% dmg/hit)";
        List<String> wrapped = LevelUpOverlay.wrapText(piercingDesc, null, LevelUpOverlay.CONTENT_WIDTH);
        assertNotNull(wrapped);
        assertTrue(wrapped.size() >= 2, "Long description must wrap into at least 2 lines");

        // Empty text
        assertTrue(LevelUpOverlay.wrapText("", null, 244.0f).isEmpty());
        assertTrue(LevelUpOverlay.wrapText(null, null, 244.0f).isEmpty());

        // Short text fits on 1 line
        String shortDesc = "+20% Damage";
        List<String> shortWrapped = LevelUpOverlay.wrapText(shortDesc, null, 244.0f);
        assertEquals(1, shortWrapped.size());
        assertEquals(shortDesc, shortWrapped.get(0));
    }

    @Test
    @DisplayName("GameSession Queued Level-Up Modal Recursion")
    public void testQueuedLevelUpRecursion() {
        GameSession session = new GameSession();
        // Award 145 EXP (grants 3 level-ups: levels 2, 3, 4)
        session.getProgression().addExp(145);
        assertEquals(4, session.getProgression().getLevel());
        assertEquals(3, session.getProgression().getPendingLevelUps());

        // Update session: detects pending level-up and opens LevelUpOverlay
        session.update(0.016f);
        assertTrue(session.isLevelUpOpen());
        assertEquals(3, session.getProgression().getPendingLevelUps());

        // Consume 1st level-up
        session.getLevelUpOverlay().update(0.20f);
        session.getLevelUpOverlay().selectCard(0);
        session.applySelectedLevelUpUpgrade();

        // 2 pending remain -> overlay immediately reopens with next choices!
        assertTrue(session.isLevelUpOpen());
        assertEquals(2, session.getProgression().getPendingLevelUps());

        // Consume 2nd level-up
        session.getLevelUpOverlay().update(0.20f);
        session.getLevelUpOverlay().selectCard(0);
        session.applySelectedLevelUpUpgrade();

        // 1 pending remains -> overlay immediately reopens
        assertTrue(session.isLevelUpOpen());
        assertEquals(1, session.getProgression().getPendingLevelUps());

        // Consume 3rd level-up
        session.getLevelUpOverlay().update(0.20f);
        session.getLevelUpOverlay().selectCard(0);
        session.applySelectedLevelUpUpgrade();

        // 0 pending remain -> overlay closes and game returns to playing
        assertFalse(session.isLevelUpOpen());
        assertEquals(0, session.getProgression().getPendingLevelUps());
        assertTrue(session.isPlaying());
    }
}
