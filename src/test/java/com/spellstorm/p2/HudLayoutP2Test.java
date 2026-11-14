package com.spellstorm.p2;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.assets.FontManager;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.ui.MinimapRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Pod 1: HUD Layout, Compact Text, Panels & Power-Up Blink Tests")
public class HudLayoutP2Test {

    @BeforeAll
    static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Compact HUD text formatters fit cleanly inside their bar and badge widths")
    void testCompactFormattersFitInsidePanelWidths() {
        FontManager fm = new FontManager();
        fm.initialize();
        try {
            BitmapFont vitalsFont = fm.fontVitals;
            BitmapFont expFont = fm.getFont(AssetRegistry.FontId.BODY, 10);
            BitmapFont boldSmallFont = fm.fontBoldSmall;
            BitmapFont smallFont = fm.fontSmall;

            // 1. Vitals bar (260x18)
            Player player = new Player(new Vector2D(0, 0), 9999f, 100f, 230f, 45f);
            String vitals = HudRenderer.formatCompactVitalsText(player);
            assertEquals("HP: 9999/9999 | ARM: 100", vitals);
            GlyphLayout vitalsLayout = new GlyphLayout(vitalsFont, vitals);
            assertTrue(vitalsLayout.width + 16f <= HudRenderer.VITALS_BAR_WIDTH,
                    "Vitals text width " + vitalsLayout.width + " must fit in " + HudRenderer.VITALS_BAR_WIDTH);
            assertTrue(vitalsLayout.height <= HudRenderer.VITALS_BAR_HEIGHT,
                    "Vitals text height " + vitalsLayout.height + " must fit in " + HudRenderer.VITALS_BAR_HEIGHT);

            // 2. EXP bar (260x12)
            PlayerProgression prog = new PlayerProgression(99);
            prog.addExp(120);
            String exp = HudRenderer.formatCompactExpText(prog);
            assertTrue(exp.startsWith("LV 99  EXP "), "Unexpected compact exp format: " + exp);
            GlyphLayout expLayout = new GlyphLayout(expFont, exp);
            assertTrue(expLayout.width + 12f <= HudRenderer.EXP_BAR_WIDTH,
                    "EXP text width " + expLayout.width + " must fit in " + HudRenderer.EXP_BAR_WIDTH);
            assertTrue(expLayout.height <= HudRenderer.EXP_BAR_HEIGHT,
                    "EXP 10pt text height " + expLayout.height + " must fit in " + HudRenderer.EXP_BAR_HEIGHT);

            // 3. Shooting badge (204x22)
            for (ShootingMode mode : ShootingMode.values()) {
                String modeText = HudRenderer.formatShootingModeText(mode);
                GlyphLayout modeLayout = new GlyphLayout(boldSmallFont, modeText);
                assertTrue(modeLayout.width + 20f <= HudRenderer.SHOOTING_BADGE_WIDTH,
                        "Mode text '" + modeText + "' width " + modeLayout.width + " exceeds " + HudRenderer.SHOOTING_BADGE_WIDTH);
                assertTrue(modeLayout.height <= HudRenderer.SHOOTING_BADGE_HEIGHT);
            }
            assertEquals("MODE: AUTO [V]", HudRenderer.formatShootingModeText(ShootingMode.AUTO));
            assertEquals("MODE: MANUAL [V]", HudRenderer.formatShootingModeText(ShootingMode.MANUAL));

            // 4. PowerUp badge (230x34, minus 34px for left icon)
            ActiveTemporaryAbility ability = new ActiveTemporaryAbility();
            for (PowerUpType type : PowerUpType.values()) {
                ability.activate(type);
                ability.update(0.8f); // 4.2s remaining
                String puText = HudRenderer.formatCompactPowerUpText(ability);
                GlyphLayout puLayout = new GlyphLayout(boldSmallFont, puText);
                assertTrue(puLayout.width + 40f <= HudRenderer.POWERUP_BADGE_WIDTH,
                        "Power-up text '" + puText + "' width " + puLayout.width + " exceeds available badge width");
            }

            // 5. Controls hint footer bar (560x26)
            GlyphLayout hintLayout = new GlyphLayout(smallFont, HudRenderer.CONTROLS_HINT_TEXT);
            assertTrue(hintLayout.width + 16f <= HudRenderer.CONTROLS_BAR_WIDTH,
                    "Controls hint width " + hintLayout.width + " exceeds " + HudRenderer.CONTROLS_BAR_WIDTH);

            // 6. All 15 spell names fit inside Element Slots panel (260px - 62px icon column = 198px)
            SpellRegistry registry = SpellRegistry.createDefaultRegistry();
            List<SpellDefinition> allSpells = new ArrayList<>();
            for (Element e : Element.values()) {
                SpellDefinition base = registry.baseSpell(e);
                if (base != null) {
                    allSpells.add(base);
                }
            }
            allSpells.addAll(registry.getCombinations());
            assertTrue(allSpells.size() >= 8);
            for (SpellDefinition spell : allSpells) {
                String compactSpell = HudRenderer.formatCompactSpellName(spell);
                GlyphLayout spellLayout = new GlyphLayout(boldSmallFont, compactSpell);
                assertTrue(spellLayout.width <= HudRenderer.ELEMENT_PANEL_WIDTH - 68f,
                        "Compact spell name '" + compactSpell + "' width " + spellLayout.width + " overflows element panel");
            }

            // 7. Stats panel columns fit strictly inside 300px without colliding
            GlyphLayout timeLayout = new GlyphLayout(boldSmallFont, "TIME 59:59");
            GlyphLayout killsLayout = new GlyphLayout(boldSmallFont, "KILLS 999");
            GlyphLayout lvLayout = new GlyphLayout(boldSmallFont, "LV 99");
            assertTrue(12f + timeLayout.width < 118f, "TIME column collides with KILLS column");
            assertTrue(118f + killsLayout.width < 225f, "KILLS column collides with LV column");
            assertTrue(225f + lvLayout.width <= HudRenderer.STATS_PANEL_WIDTH - 8f, "LV column overflows stats panel");
        } finally {
            fm.dispose();
        }
    }

    @Test
    @DisplayName("All HUD panels and Boss Bar / Minimap exclusion zones are strictly disjoint and inside 1280x720")
    void testAllHudPanelsNonOverlappingAndContained() {
        List<Rectangle> panelsYDown = List.of(
                HudRenderer.getHealthBarBounds(true),
                HudRenderer.getExpBarBounds(true),
                HudRenderer.getShootingModeBadgeBounds(true),
                HudRenderer.getPowerUpBadgeBounds(true),
                HudRenderer.getStatsPanelBounds(true),
                HudRenderer.getElementSlotsPanelBounds(true),
                HudRenderer.getControlsHintBarBounds(true),
                new Rectangle(360f, 6f, 560f, 44f), // Boss Bar exclusion zone x=[360..920], y=[6..50]
                new Rectangle(1110f, 525f, 150f, 170f) // Minimap + label exclusion zone x=[1110..1260], y=[525..695]
        );

        for (int i = 0; i < panelsYDown.size(); i++) {
            Rectangle a = panelsYDown.get(i);
            assertTrue(a.x >= 0f && a.x + a.width <= 1280f, "Panel " + i + " out of horizontal bounds: " + a);
            assertTrue(a.y >= 0f && a.y + a.height <= 720f, "Panel " + i + " out of vertical bounds: " + a);
            for (int j = i + 1; j < panelsYDown.size(); j++) {
                Rectangle b = panelsYDown.get(j);
                assertFalse(a.overlaps(b), "Panels " + i + " (" + a + ") and " + j + " (" + b + ") must not overlap");
            }
        }

        // Verify Y-up inversion matches 720 - yDown.y - height for all new panels
        Rectangle statsDown = HudRenderer.getStatsPanelBounds(true);
        Rectangle statsUp = HudRenderer.getStatsPanelBounds(false);
        assertEquals(720f - statsDown.y - statsDown.height, statsUp.y, 1e-4f);

        Rectangle elemDown = HudRenderer.getElementSlotsPanelBounds(true);
        Rectangle elemUp = HudRenderer.getElementSlotsPanelBounds(false);
        assertEquals(720f - elemDown.y - elemDown.height, elemUp.y, 1e-4f);

        Rectangle ctrlDown = HudRenderer.getControlsHintBarBounds(true);
        Rectangle ctrlUp = HudRenderer.getControlsHintBarBounds(false);
        assertEquals(720f - ctrlDown.y - ctrlDown.height, ctrlUp.y, 1e-4f);

        // Verify Minimap label + box also do not overlap any HUD panel
        Rectangle mmLabel = MinimapRenderer.getMinimapLabelBounds(true);
        Rectangle mmBox = MinimapRenderer.getMinimapBounds(true);
        for (int i = 0; i < 7; i++) {
            Rectangle p = panelsYDown.get(i);
            assertFalse(p.overlaps(mmLabel), "HUD panel " + i + " overlaps minimap label");
            assertFalse(p.overlaps(mmBox), "HUD panel " + i + " overlaps minimap box");
        }
    }

    @Test
    @DisplayName("PowerUp badge colors are distinct per enum and blink alpha oscillates in [0.35, 1.0] below 4.0s")
    void testPowerUpColorsAndBlinkAlpha() {
        Color cOver = HudRenderer.getPowerUpBadgeColor(PowerUpType.ARCANE_OVERDRIVE);
        Color cPierce = HudRenderer.getPowerUpBadgeColor(PowerUpType.PIERCING_SURGE);
        Color cBurst = HudRenderer.getPowerUpBadgeColor(PowerUpType.ELEMENTAL_BURST);
        assertNotEquals(cOver, cPierce);
        assertNotEquals(cOver, cBurst);
        assertNotEquals(cPierce, cBurst);
        assertNotNull(HudRenderer.getPowerUpBadgeColor(null));

        assertFalse(HudRenderer.isPowerUpBlinking(null));
        assertEquals(1.0f, HudRenderer.computePowerUpBlinkAlpha(null, 1.5f), 1e-5f);

        ActiveTemporaryAbility ability = new ActiveTemporaryAbility();
        assertFalse(HudRenderer.isPowerUpBlinking(ability));
        assertEquals(1.0f, HudRenderer.computePowerUpBlinkAlpha(ability, 1.5f), 1e-5f);

        // 5.0s -> not blinking
        ability.activate(PowerUpType.ARCANE_OVERDRIVE);
        assertFalse(HudRenderer.isPowerUpBlinking(ability));
        assertEquals(1.0f, HudRenderer.computePowerUpBlinkAlpha(ability, 0.2f), 1e-5f);

        // Exact 4.0s -> not blinking (< 4.0f required)
        ability.update(1.0f);
        assertEquals(4.0f, ability.getRemainingDuration(), 1e-4f);
        assertFalse(HudRenderer.isPowerUpBlinking(ability));
        assertEquals(1.0f, HudRenderer.computePowerUpBlinkAlpha(ability, 0.2f), 1e-5f);

        // 3.5s -> blinking within [0.35f, 1.0f] and oscillating over time
        ability.update(0.5f);
        assertTrue(HudRenderer.isPowerUpBlinking(ability));
        float minSeen = 2.0f;
        float maxSeen = -1.0f;
        for (int i = 0; i < 40; i++) {
            float alpha = HudRenderer.computePowerUpBlinkAlpha(ability, i * 0.05f);
            assertTrue(alpha >= 0.35f && alpha <= 1.0f, "Alpha out of range: " + alpha);
            minSeen = Math.min(minSeen, alpha);
            maxSeen = Math.max(maxSeen, alpha);
        }
        assertTrue(maxSeen - minSeen > 0.4f, "Blink alpha must oscillate visibly over time");
    }

    @Test
    @DisplayName("Survival timer formats MM:SS and estimateTextWidth is monotonic and null-safe")
    void testSurvivalTimerAndEstimateTextWidth() {
        assertEquals("00:00", HudRenderer.formatSurvivalTimer(-5.0f));
        assertEquals("00:00", HudRenderer.formatSurvivalTimer(0.0f));
        assertEquals("00:59", HudRenderer.formatSurvivalTimer(59.9f));
        assertEquals("01:05", HudRenderer.formatSurvivalTimer(65.0f));
        assertEquals("10:00", HudRenderer.formatSurvivalTimer(600.0f));

        assertEquals(0.0f, HudRenderer.estimateTextWidth(null), 1e-5f);
        assertEquals(0.0f, HudRenderer.estimateTextWidth(""), 1e-5f);
        float w1 = HudRenderer.estimateTextWidth("HP: 250/250");
        float w2 = HudRenderer.estimateTextWidth("HP: 250/250 | ARM: 30");
        assertTrue(w1 > 0.0f);
        assertTrue(w2 > w1, "estimateTextWidth must be strictly monotonic");

        // Null safety for compact formatters
        assertEquals("", HudRenderer.formatCompactVitalsText(null));
        assertEquals("", HudRenderer.formatCompactExpText(null));
        assertEquals("", HudRenderer.formatCompactPowerUpText(null));
        assertEquals("MODE: MANUAL [V]", HudRenderer.formatShootingModeText(null));
    }

    @Test
    @DisplayName("14-arg renderHUD executes safely headless, renders boss bar once, and handles nulls")
    void testPhase2RenderHudOverloadHeadless() {
        HudRenderer hud = new HudRenderer();
        Player player = new Player();
        PlayerProgression prog = new PlayerProgression();
        PowerUpManager powerUp = new PowerUpManager();
        powerUp.getActiveAbility().activate(PowerUpType.ELEMENTAL_BURST);
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
        List<Enemy> enemies = new ArrayList<>(List.of(boss));
        SpellDefinition spell = SpellRegistry.createDefaultRegistry().resolve(List.of(Element.FIRE, Element.ICE));

        hud.resetRenderCounts();
        assertDoesNotThrow(() -> {
            hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 12,
                    spell, List.of(Element.FIRE, Element.ICE), 75.5f, 12345L, true, true);
            hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 12,
                    spell, List.of(Element.FIRE), 75.5f, 12345L, false, false);
            hud.renderHUD(null, null, null, null, null, null, null, 0,
                    null, null, 0f, 0L, false, true);
        });
        assertEquals(2, hud.getBossBarRenderCount());
    }
}
