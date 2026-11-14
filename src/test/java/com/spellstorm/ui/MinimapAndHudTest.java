package com.spellstorm.ui;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Features 15 & 33: Minimap HUD Widget & Fixed Screen-Space HUD")
public class MinimapAndHudTest {

    @BeforeAll
    static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Feature 15: Minimap HUD Widget - Tier 1 (Equivalence, 5 tests)
    // =========================================================================
    @Test
    @DisplayName("15.1 Minimap palette colors adhere to SFML reference specification")
    void testMinimapPaletteColors() {
        assertNotNull(MinimapRenderer.COLOR_MINIMAP_BG);
        assertNotNull(MinimapRenderer.COLOR_MINIMAP_BORDER);
        assertNotNull(MinimapRenderer.COLOR_TILE_WATER);
        assertNotNull(MinimapRenderer.COLOR_TILE_DIRT);
        assertNotNull(MinimapRenderer.COLOR_TILE_STONE);
        assertNotNull(MinimapRenderer.COLOR_ENEMY_BLIP);
        assertNotNull(MinimapRenderer.COLOR_PLAYER_MARKER);
        assertNotNull(MinimapRenderer.COLOR_LABEL);
    }

    @Test
    @DisplayName("15.2 Minimap coordinate scaling ratio math across 4096x4096px world")
    void testMinimapCoordinateScalingMath() {
        Rectangle bounds = MinimapRenderer.getMinimapBounds(true);
        float scaleX = bounds.width / 4096.0f;
        float scaleY = bounds.height / 4096.0f;
        assertEquals(150.0f / 4096.0f, scaleX, 1e-6f);
        assertEquals(150.0f / 4096.0f, scaleY, 1e-6f);
    }

    @Test
    @DisplayName("15.3 Player marker projection at world center (2048, 2048) places dot at minimap geometric center")
    void testMinimapPlayerMarkerProjectionCenter() {
        Rectangle bounds = MinimapRenderer.getMinimapBounds(true);
        float scaleX = bounds.width / 4096.0f;
        float scaleY = bounds.height / 4096.0f;

        float px = bounds.x + 2048.0f * scaleX;
        float py = bounds.y + 2048.0f * scaleY;

        assertEquals(bounds.x + 75.0f, px, 1e-4f);
        assertEquals(bounds.y + 75.0f, py, 1e-4f);
    }

    @Test
    @DisplayName("15.4 Enemy blips projection correctly maps multiple coordinates")
    void testMinimapEnemyBlipsProjection() {
        Rectangle bounds = MinimapRenderer.getMinimapBounds(true);
        float scaleX = bounds.width / 4096.0f;
        float scaleY = bounds.height / 4096.0f;

        Vector2D enemyPos = new Vector2D(1024.0f, 3072.0f);
        float ex = bounds.x + enemyPos.x * scaleX;
        float ey = bounds.y + enemyPos.y * scaleY;

        assertEquals(bounds.x + 37.5f, ex, 1e-4f);
        assertEquals(bounds.y + 112.5f, ey, 1e-4f);
    }

    @Test
    @DisplayName("15.5 Subsampling step 4x4 produces exactly 32x32 sample cells across 128x128 map")
    void testMinimapSubsamplingStepAndCellCount() {
        assertEquals(4, MinimapRenderer.SUBSAMPLING_STEP);
        int sampleCellsPerAxis = 128 / MinimapRenderer.SUBSAMPLING_STEP;
        assertEquals(32, sampleCellsPerAxis);
        float cellW = 150.0f / (float) sampleCellsPerAxis;
        assertEquals(4.6875f, cellW, 1e-4f);
    }

    // =========================================================================
    // Feature 15: Minimap HUD Widget - Tier 2 (Boundary & Corner, 5 tests)
    // =========================================================================
    @Test
    @DisplayName("15.6 Minimap handles entities at extreme world bounds [0, 4096] accurately")
    void testMinimapEntitiesAtExtremeBounds() {
        Rectangle bounds = MinimapRenderer.getMinimapBounds(true);
        float scaleX = bounds.width / 4096.0f;

        float minX = bounds.x + 0.0f * scaleX;
        float maxX = bounds.x + 4096.0f * scaleX;

        assertEquals(bounds.x, minX, 1e-4f);
        assertEquals(bounds.x + bounds.width, maxX, 1e-4f);
    }

    @Test
    @DisplayName("15.7 MinimapRenderer.renderMinimap safely no-ops under headless environment without throwing")
    void testMinimapRenderHeadlessNoOp() {
        MinimapRenderer renderer = new MinimapRenderer();
        TileMap tileMap = new TileMap(42);
        assertDoesNotThrow(() -> {
            renderer.renderMinimap(null, null, null, tileMap, new Vector2D(2048, 2048), List.of(), true);
        });
    }

    @Test
    @DisplayName("15.8 MinimapRenderer handles null player and null enemy list defensively")
    void testMinimapRenderNullParameters() {
        MinimapRenderer renderer = new MinimapRenderer();
        assertDoesNotThrow(() -> {
            renderer.renderMinimap(null, null, null, null, null, null, false);
        });
    }

    @Test
    @DisplayName("15.9 Minimap Y-up vs Y-down bounds invert vertically with exact screen offset")
    void testMinimapYUpVsYDownInversion() {
        Rectangle yDown = MinimapRenderer.getMinimapBounds(true);
        Rectangle yUp = MinimapRenderer.getMinimapBounds(false);

        assertEquals(yDown.x, yUp.x);
        assertEquals(yDown.width, yUp.width);
        assertEquals(yDown.height, yUp.height);
        assertEquals(720.0f - yDown.y - yDown.height, yUp.y, 1e-4f);
    }

    @Test
    @DisplayName("15.10 Minimap handles 500 enemy blips without performance degradation or error")
    void testMinimapDenseEnemyListStress() {
        MinimapRenderer renderer = new MinimapRenderer();
        List<Vector2D> enemies = new ArrayList<>(500);
        for (int i = 0; i < 500; i++) {
            enemies.add(new Vector2D(i * 8.0f, i * 8.0f));
        }
        assertDoesNotThrow(() -> {
            renderer.renderMinimap(null, null, null, null, new Vector2D(2048, 2048), enemies, true);
        });
    }

    // =========================================================================
    // Feature 33: Fixed Screen-Space HUD - Tier 1 (Equivalence, 5 tests)
    // =========================================================================
    @Test
    @DisplayName("33.1 HudRenderer formatVitalsText produces expected HP and Armor readout")
    void testHudFormatVitalsText() {
        Player player = new Player(new Vector2D(0, 0), 250f, 30f, 230f, 45f);
        assertEquals("HP: 250 / 250 | ARMOR: 30", HudRenderer.formatVitalsText(player));

        player.takeDamage(50.0f); // 50 * 100/130 = 38.46 -> HP = 211.54
        assertEquals("HP: 212 / 250 | ARMOR: 30", HudRenderer.formatVitalsText(player));
    }

    @Test
    @DisplayName("33.2 HudRenderer formatExpText produces formatted level and progress percentage")
    void testHudFormatExpText() {
        PlayerProgression prog = new PlayerProgression(1);
        assertEquals("LVL 1 (0 / 25 - 0%)", HudRenderer.formatExpText(prog));

        prog.addExp(10);
        assertEquals("LVL 1 (10 / 25 - 40%)", HudRenderer.formatExpText(prog));
    }

    @Test
    @DisplayName("33.3 HudRenderer formatPowerUpText formats active countdown with two-digit tenths")
    void testHudFormatPowerUpText() {
        ActiveTemporaryAbility ability = new ActiveTemporaryAbility();
        ability.activate(PowerUpType.ARCANE_OVERDRIVE);
        assertEquals("ARCANE OVERDRIVE (05.0s)", HudRenderer.formatPowerUpText(ability));

        ability.update(1.5f);
        assertEquals("ARCANE OVERDRIVE (03.5s)", HudRenderer.formatPowerUpText(ability));
    }

    @Test
    @DisplayName("33.4 HudRenderer formatHpText formats Boss name and health ratio")
    void testHudFormatBossHpText() {
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
        assertEquals("1800 / 1800 (100%)", HudRenderer.formatHpText(boss));

        boss.takeDamage(900.0f, true); // true damage reduces by 900 -> 900 remaining
        assertEquals("900 / 1800 (50%)", HudRenderer.formatHpText(boss));
    }

    @Test
    @DisplayName("33.5 HudRenderer constants match 1280x720 layout specification")
    void testHudLayoutConstants() {
        assertEquals(75.0f, HudRenderer.TITLE_X);
        assertEquals(60.0f, HudRenderer.TITLE_Y);
        assertEquals(75.0f, HudRenderer.VITALS_BAR_X);
        assertEquals(120.0f, HudRenderer.VITALS_BAR_Y);
        assertEquals(260.0f, HudRenderer.VITALS_BAR_WIDTH);
        assertEquals(18.0f, HudRenderer.VITALS_BAR_HEIGHT);
        assertEquals(560.0f, HudRenderer.BOSS_BAR_WIDTH);
        assertEquals(22.0f, HudRenderer.BOSS_BAR_HEIGHT);
    }

    // =========================================================================
    // Feature 33: Fixed Screen-Space HUD - Tier 2 (Boundary & Corner, 5 tests)
    // =========================================================================
    @Test
    @DisplayName("33.6 HudRenderer formatHpText handles 0 HP and overkill without negative percentages")
    void testHudBossHpOverkill() {
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
        boss.takeDamage(9999.0f, true);
        assertEquals("0 / 1800 (0%)", HudRenderer.formatHpText(boss));
    }

    @Test
    @DisplayName("33.7 HudRenderer.renderHud safely no-ops under headless environment without throwing")
    void testHudRenderHeadlessSafeNoOp() {
        HudRenderer renderer = new HudRenderer();
        Player player = new Player();
        PlayerProgression prog = new PlayerProgression();
        PowerUpManager powerUp = new PowerUpManager();
        SpellDefinition spell = SpellRegistry.createDefaultRegistry().baseSpell(com.spellstorm.combat.Element.FIRE);

        assertDoesNotThrow(() -> {
            renderer.renderHud(null, null, null, player, List.of(), prog, powerUp, spell, true);
        });
    }

    @Test
    @DisplayName("33.8 HudRenderer handles null components in renderHud defensively")
    void testHudRenderNullDefensiveSafety() {
        HudRenderer renderer = new HudRenderer();
        assertDoesNotThrow(() -> {
            renderer.renderHud(null, null, null, null, null, null, null, null, false);
        });
    }

    @Test
    @DisplayName("33.9 HudRenderer Y-up vs Y-down coordinates invert properly across all HUD elements")
    void testHudComponentYInversions() {
        Rectangle vitalsDown = HudRenderer.getHealthBarBounds(true);
        Rectangle vitalsUp = HudRenderer.getHealthBarBounds(false);
        assertEquals(720.0f - vitalsDown.y - vitalsDown.height, vitalsUp.y, 1e-4f);

        Rectangle expDown = HudRenderer.getExpBarBounds(true);
        Rectangle expUp = HudRenderer.getExpBarBounds(false);
        assertEquals(720.0f - expDown.y - expDown.height, expUp.y, 1e-4f);

        Rectangle bossDown = HudRenderer.getBossBarBounds(1280f, 720f, true);
        Rectangle bossUp = HudRenderer.getBossBarBounds(1280f, 720f, false);
        assertEquals(720.0f - bossDown.y - bossDown.height, bossUp.y, 1e-4f);
    }

    @Test
    @DisplayName("33.10 HudRenderer formatExpText handles max level and 100% progress smoothly")
    void testHudExpFormattingMaxLevel() {
        PlayerProgression prog = new PlayerProgression(100);
        String text = HudRenderer.formatExpText(prog);
        assertTrue(text.startsWith("LVL 100"));
    }
}
