package com.spellstorm.p2;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.ProjectileProfile;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.ui.CodexOverlay;
import com.spellstorm.ui.ControlsOverlay;
import com.spellstorm.ui.TutorialOverlay;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Pod 4: CodexOverlay, TutorialOverlay, and ControlsOverlay")
public class GuidanceP2Test {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // CodexOverlay
    // =========================================================================

    @Test
    @DisplayName("CodexOverlay lists all 15 spell slots (5 base + 10 dual pairs) and 3 power-ups")
    public void testCodexSpellAndPowerUpCatalog() {
        CodexOverlay codex = new CodexOverlay();
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();

        assertEquals(15, codex.getDisplayedSpellCount(registry));
        List<CodexOverlay.SpellEntry> entries = codex.getSpellEntries(registry);
        assertEquals(15, entries.size());

        long baseCount = entries.stream().filter(e -> !e.combination).count();
        long comboCount = entries.stream().filter(e -> e.combination).count();
        assertEquals(5, baseCount);
        assertEquals(10, comboCount);

        // Verify all 5 base spells are present with non-empty stats & traits
        Set<String> baseNames = new HashSet<>();
        for (int i = 0; i < 5; i++) {
            CodexOverlay.SpellEntry entry = entries.get(i);
            assertFalse(entry.combination);
            assertTrue(entry.registered);
            assertEquals(1, entry.elements.size());
            assertNotNull(entry.spell);
            assertFalse(entry.spellName.isBlank());
            assertFalse(entry.damageText.isBlank());
            assertFalse(entry.cooldownText.isBlank());
            assertFalse(entry.speedRangeText.isBlank());
            assertFalse(entry.effectTrait.isBlank());
            assertNotNull(entry.primaryColor);
            baseNames.add(entry.spellName);
        }
        assertTrue(baseNames.contains("Firebolt"));
        assertTrue(baseNames.contains("Frost Shard"));
        assertTrue(baseNames.contains("Spark Arc"));
        assertTrue(baseNames.contains("Gale Burst"));
        assertTrue(baseNames.contains("Boulder Slam"));

        // Verify all 10 unique unordered dual-element pairs are covered
        Set<Set<Element>> seenPairs = new HashSet<>();
        int registeredCombos = 0;
        for (int i = 5; i < 15; i++) {
            CodexOverlay.SpellEntry entry = entries.get(i);
            assertTrue(entry.combination);
            assertEquals(2, entry.elements.size());
            assertNotEquals(entry.elements.get(0), entry.elements.get(1));
            seenPairs.add(EnumSet.of(entry.elements.get(0), entry.elements.get(1)));
            assertFalse(entry.recipeLabel.isBlank());
            assertFalse(entry.spellName.isBlank());
            assertFalse(entry.damageText.isBlank());
            assertFalse(entry.cooldownText.isBlank());
            assertFalse(entry.speedRangeText.isBlank());
            assertFalse(entry.effectTrait.isBlank());
            if (entry.registered) {
                registeredCombos++;
                assertNotNull(entry.spell);
            }
        }
        assertEquals(10, seenPairs.size());
        assertEquals(3, registeredCombos);

        // Also verify a fully populated 10-combo registry resolves all 10 combinations
        SpellRegistry fullRegistry = SpellRegistry.createDefaultRegistry();
        Element[] elems = Element.values();
        for (int i = 0; i < elems.length; i++) {
            for (int j = i + 1; j < elems.length; j++) {
                if (fullRegistry.combination(elems[i], elems[j]) == null) {
                    fullRegistry.registerCombination(elems[i], elems[j], new SpellDefinition(
                            elems[i].getDisplayName() + "-" + elems[j].getDisplayName(),
                            "Custom fusion trait",
                            new com.spellstorm.combat.ElementSet(elems[i], elems[j]),
                            elems[i].getColor(), elems[j].getColor(),
                            new ProjectileProfile(1.4f, 1.2f, 10f, 2.0f, 1.0f, 0f, 1, 0)
                    ));
                }
            }
        }
        assertEquals(15, codex.getDisplayedSpellCount(fullRegistry));
        long fullRegistered = codex.getSpellEntries(fullRegistry).stream().filter(e -> e.registered).count();
        assertEquals(15, fullRegistered);

        // Verify all 3 PowerUpType entries with distinct swatches and descriptions
        assertEquals(3, codex.getDisplayedPowerUpCount());
        List<CodexOverlay.PowerUpEntry> powerUps = codex.getPowerUpEntries();
        assertEquals(3, powerUps.size());
        Set<PowerUpType> seenPowerUps = EnumSet.noneOf(PowerUpType.class);
        Set<Color> seenSwatches = new HashSet<>();
        for (CodexOverlay.PowerUpEntry pu : powerUps) {
            seenPowerUps.add(pu.type);
            assertEquals(pu.type.getDisplayName(), pu.name);
            assertEquals(pu.type.getDescription(), pu.description);
            assertNotNull(pu.primaryColor);
            assertNotNull(pu.secondaryColor);
            seenSwatches.add(pu.primaryColor);
        }
        assertEquals(EnumSet.allOf(PowerUpType.class), seenPowerUps);
        assertEquals(3, seenSwatches.size());
    }

    @Test
    @DisplayName("CodexOverlay handles C toggle, ESC close, close button click, and bounds in 1280x720")
    public void testCodexInputAndBounds() {
        CodexOverlay codex = new CodexOverlay();
        assertFalse(codex.isVisible());

        // Bounds fit inside virtual 1280x720 and close button lies inside panel
        Rectangle panelYDown = codex.getPanelBounds(true);
        Rectangle panelYUp = codex.getPanelBounds(false);
        Rectangle closeYDown = codex.getCloseButtonBounds(true);
        assertTrue(panelYDown.x >= 0 && panelYDown.x + panelYDown.width <= 1280.0f);
        assertTrue(panelYDown.y >= 0 && panelYDown.y + panelYDown.height <= 720.0f);
        assertTrue(panelYUp.y >= 0 && panelYUp.y + panelYUp.height <= 720.0f);
        assertTrue(panelYDown.contains(closeYDown));

        // When closed, ESC or stray mouse click does NOT open or consume
        assertFalse(codex.handleInput(false, true, new Vector2D(640, 360), true));
        assertFalse(codex.isVisible());

        // Pressing 'C' opens and consumes
        assertTrue(codex.handleInput(true, false, new Vector2D(640, 360), false));
        assertTrue(codex.isVisible());

        // Clicking outside close button while open consumes input and remains open
        assertTrue(codex.handleInput(false, false, new Vector2D(panelYDown.x + 20f, panelYDown.y + 20f), true));
        assertTrue(codex.isVisible());

        // Pressing ESC while open closes and consumes
        assertTrue(codex.handleInput(false, true, new Vector2D(640, 360), false));
        assertFalse(codex.isVisible());

        // Toggle open then click close button to close
        codex.toggle();
        assertTrue(codex.isVisible());
        Vector2D closeCenter = new Vector2D(closeYDown.x + closeYDown.width * 0.5f,
                closeYDown.y + closeYDown.height * 0.5f);
        assertTrue(codex.handleInput(false, false, closeCenter, true));
        assertFalse(codex.isVisible());

        // Headless null render guard
        codex.setVisible(true);
        assertDoesNotThrow(() -> codex.render(null, null, null, SpellRegistry.createDefaultRegistry(), 1280f, 720f));
    }

    // =========================================================================
    // TutorialOverlay
    // =========================================================================

    @Test
    @DisplayName("TutorialOverlay displays 3 goal lines, fades over 8.0s, and dismisses on click/SPACE/ENTER")
    public void testTutorialCountdownFadeAndDismiss() {
        TutorialOverlay tutorial = new TutorialOverlay();
        assertEquals(8.0f, TutorialOverlay.DEFAULT_DURATION, 1e-4f);
        assertTrue(tutorial.isActive());
        assertEquals(8.0f, tutorial.getRemainingTime(), 1e-4f);
        assertEquals(1.0f, tutorial.getAlpha(), 1e-4f);
        assertEquals(3, tutorial.getBannerLines().size());
        for (String line : tutorial.getBannerLines()) {
            assertFalse(line.isBlank());
        }

        // Banner fits 1280x720 and avoids boss bar (360..920, 28..50) and minimap (1110..1260, 545..695)
        Rectangle banner = tutorial.getBannerBounds(true);
        assertTrue(banner.x >= 0 && banner.x + banner.width <= 1280.0f);
        assertTrue(banner.y >= 0 && banner.y + banner.height <= 720.0f);
        assertFalse(banner.overlaps(new Rectangle(360f, 28f, 560f, 22f)));
        assertFalse(banner.overlaps(new Rectangle(1110f, 545f, 150f, 150f)));

        // Advance to 7.0s (inside final fade window)
        tutorial.update(7.0f);
        assertTrue(tutorial.isActive());
        assertEquals(1.0f, tutorial.getRemainingTime(), 1e-4f);
        assertTrue(tutorial.getAlpha() > 0.0f && tutorial.getAlpha() < 1.0f);

        // Advance remaining 1.0s -> expires cleanly
        tutorial.update(1.0f);
        assertFalse(tutorial.isActive());
        assertEquals(0.0f, tutorial.getRemainingTime(), 1e-4f);
        assertEquals(0.0f, tutorial.getAlpha(), 1e-4f);

        // Reset and dismiss with SPACE
        tutorial.reset();
        assertTrue(tutorial.isActive());
        assertFalse(tutorial.handleKeyPress(Keys.W));
        assertTrue(tutorial.isActive());
        assertTrue(tutorial.handleKeyPress(Keys.SPACE));
        assertFalse(tutorial.isActive());

        // Reset and dismiss with ENTER
        tutorial.reset();
        assertTrue(tutorial.handleKeyPress(Keys.ENTER));
        assertFalse(tutorial.isActive());

        // Reset and dismiss with click on banner
        tutorial.reset();
        Vector2D bannerCenter = new Vector2D(banner.x + banner.width * 0.5f, banner.y + banner.height * 0.5f);
        assertTrue(tutorial.handleMouseClick(bannerCenter));
        assertFalse(tutorial.isActive());

        tutorial.reset();
        assertDoesNotThrow(() -> tutorial.render(null, null, null, 1280f, 720f));
    }

    // =========================================================================
    // ControlsOverlay
    // =========================================================================

    @Test
    @DisplayName("ControlsOverlay exposes all required bindings, compact hint text, and y-down list rendering")
    public void testControlsOverlayBindingsAndRender() {
        List<String[]> bindings = ControlsOverlay.getControlBindings();
        assertEquals(7, bindings.size());

        String joinedKeys = bindings.stream().map(b -> b[0]).reduce("", (a, b) -> a + " " + b);
        assertTrue(joinedKeys.contains("WASD"));
        assertTrue(joinedKeys.contains("Left Mouse"));
        assertTrue(joinedKeys.contains("1 - 5"));
        assertTrue(joinedKeys.contains("V"));
        assertTrue(joinedKeys.contains("P / ESC"));
        assertTrue(joinedKeys.contains("C"));
        assertTrue(joinedKeys.contains("F11"));

        for (String[] row : bindings) {
            assertEquals(2, row.length);
            assertFalse(row[0].isBlank());
            assertFalse(row[1].isBlank());
        }

        String hint = ControlsOverlay.getCompactHintBarText();
        assertTrue(hint.contains("WASD"));
        assertTrue(hint.contains("LMB"));
        assertTrue(hint.contains("1-5"));
        assertTrue(hint.contains("V"));
        assertTrue(hint.contains("P/ESC"));
        assertTrue(hint.contains("C"));
        assertTrue(hint.contains("F11"));

        assertDoesNotThrow(() -> ControlsOverlay.renderControlsList(null, null, null, 100f, 120f, 520f, 280f));
    }
}
