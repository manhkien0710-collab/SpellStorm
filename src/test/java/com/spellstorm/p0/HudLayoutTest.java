package com.spellstorm.p0;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.FontManager;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.ui.HudRenderer;
import com.spellstorm.ui.MinimapRenderer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P0 Defect D5: HUD & Boss Health Bar Layout Test")
public class HudLayoutTest {

    @BeforeAll
    static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("D5.1 Boss health bar bounds in y-down UI camera space are positioned at top-center (560px)")
    void testBossBarBoundsDefaultYDown() {
        Rectangle bounds = HudRenderer.getBossBarBounds(1280f, 720f, true);
        assertEquals(360f, bounds.x, 1e-4f, "Bar X should be centered: (1280 - 560)/2 = 360");
        assertEquals(28f, bounds.y, 1e-4f, "Bar Y in y-down UI camera space must be top offset 28");
        assertEquals(560f, bounds.width, 1e-4f, "Bar width should be 560");
        assertEquals(22f, bounds.height, 1e-4f, "Bar height should be 22");
    }

    @Test
    @DisplayName("D5.2 Boss health bar bounds with 480px width are positioned at top-center (400px)")
    void testBossBarBoundsCustomWidth480YDown() {
        Rectangle bounds480 = HudRenderer.getBossBarBounds(1280f, 720f, 480f, 22f, true);
        assertEquals(400f, bounds480.x, 1e-4f, "Bar X should be centered: (1280 - 480)/2 = 400");
        assertEquals(28f, bounds480.y, 1e-4f, "Bar Y in y-down UI camera space must be top offset 28");
        assertEquals(480f, bounds480.width, 1e-4f, "Bar width should be 480");
        assertEquals(22f, bounds480.height, 1e-4f, "Bar height should be 22");
    }

    @Test
    @DisplayName("D5.3 Boss name, HP text, and health bar bounds in y-down space are strictly inside 1280x720 viewport")
    void testBossNameHpAndBarBoundsStrictlyInsideViewport() {
        FontManager fm = new FontManager();
        fm.initialize();
        try {
            BitmapFont bold = fm.fontBoldRegular;
            BitmapFont body = fm.fontVitals;
            assertNotNull(bold);
            assertNotNull(body);

            BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
            String bossName = boss.getName();
            String hpText = HudRenderer.formatHpText(boss);

            GlyphLayout layoutName = new GlyphLayout(bold, bossName);
            GlyphLayout layoutHp = new GlyphLayout(body, hpText);

            Rectangle barBounds = HudRenderer.getBossBarBounds(1280f, 720f, true);
            Rectangle nameBounds = HudRenderer.getBossNameBounds(1280f, 720f, layoutName.width, layoutName.height, true);
            Rectangle hpBounds = HudRenderer.getBossHpTextBounds(1280f, 720f, layoutHp.width, layoutHp.height, true);

            // 1. Health Bar viewport containment
            assertTrue(barBounds.x >= 0f, "Bar X must be non-negative");
            assertTrue(barBounds.x + barBounds.width <= 1280f, "Bar right edge must not exceed 1280");
            assertTrue(barBounds.y >= 0f, "Bar Y must be non-negative");
            assertTrue(barBounds.y + barBounds.height <= 720f, "Bar bottom edge must not exceed 720");

            // 2. Boss Name text viewport containment
            assertTrue(nameBounds.x >= 0f, "Boss name X must be non-negative (x=" + nameBounds.x + ")");
            assertTrue(nameBounds.x + nameBounds.width <= 1280f, "Boss name right edge must not exceed 1280");
            assertTrue(nameBounds.y >= 0f, "Boss name Y must be non-negative (y=" + nameBounds.y + ")");
            assertTrue(nameBounds.y + nameBounds.height <= 720f, "Boss name bottom edge must not exceed 720");

            // Boss name must be strictly above the health bar in y-down space
            assertTrue(nameBounds.y + nameBounds.height <= barBounds.y,
                    "Boss name must be above health bar: name bottom (" + (nameBounds.y + nameBounds.height)
                            + ") <= bar top (" + barBounds.y + ")");

            // 3. HP text viewport containment
            assertTrue(hpBounds.x >= 0f, "Boss HP text X must be non-negative");
            assertTrue(hpBounds.x + hpBounds.width <= 1280f, "Boss HP text right edge must not exceed 1280");
            assertTrue(hpBounds.y >= 0f, "Boss HP text Y must be non-negative");
            assertTrue(hpBounds.y + hpBounds.height <= 720f, "Boss HP text bottom edge must not exceed 720");

            // HP text must be vertically centered inside the health bar
            assertTrue(hpBounds.y >= barBounds.y, "HP text top must be inside bar");
            assertTrue(hpBounds.y + hpBounds.height <= barBounds.y + barBounds.height,
                    "HP text bottom must be inside bar: hp bottom (" + (hpBounds.y + hpBounds.height)
                            + ") <= bar bottom (" + (barBounds.y + barBounds.height) + ")");
        } finally {
            fm.dispose();
        }
    }

    @Test
    @DisplayName("D5.4 Boss health bar is rendered exactly once per frame from renderHUD")
    void testBossBarRenderedExactlyOncePerFrame() {
        HudRenderer hud = new HudRenderer();
        Player player = new Player();
        PlayerProgression prog = new PlayerProgression();
        PowerUpManager powerUp = new PowerUpManager();
        BossEnemy boss = new BossEnemy(new Vector2D(0, 0));
        List<Enemy> enemies = List.of(boss);

        hud.resetRenderCounts();
        assertEquals(0, hud.getBossBarRenderCount(), "Initial count must be 0");

        // Frame 1
        hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 0, null, true);
        assertEquals(1, hud.getBossBarRenderCount(), "Boss health bar must be drawn exactly once per frame");

        // Frame 2
        hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 0, null, true);
        assertEquals(2, hud.getBossBarRenderCount(), "Second frame must increment count by exactly 1");

        // When no boss exists in enemy list, render count should not increment
        List<Enemy> noBossEnemies = List.of(new MeleeEnemy(new Vector2D(100, 100)));
        hud.renderHUD(null, null, null, player, prog, powerUp, noBossEnemies, 0, null, true);
        assertEquals(2, hud.getBossBarRenderCount(), "Non-boss enemies must not trigger boss bar render");

        // When enemy list is empty or null, render count should not increment
        hud.renderHUD(null, null, null, player, prog, powerUp, Collections.emptyList(), 0, null, true);
        hud.renderHUD(null, null, null, player, prog, powerUp, null, 0, null, true);
        assertEquals(2, hud.getBossBarRenderCount(), "Empty/null enemies must not trigger boss bar render");

        // When boss is dead, render count should not increment
        boss.takeDamage(9999f, true);
        assertFalse(boss.isAlive());
        hud.renderHUD(null, null, null, player, prog, powerUp, enemies, 0, null, true);
        assertEquals(2, hud.getBossBarRenderCount(), "Dead boss must not trigger boss bar render");
    }

    @Test
    @DisplayName("D5.5 Boss health bar bounds invert cleanly between y-down and y-up camera configurations")
    void testBossBarBoundsYInversion() {
        Rectangle yDown = HudRenderer.getBossBarBounds(1280f, 720f, true);
        Rectangle yUp = HudRenderer.getBossBarBounds(1280f, 720f, false);

        assertEquals(yDown.x, yUp.x);
        assertEquals(yDown.width, yUp.width);
        assertEquals(yDown.height, yUp.height);
        assertEquals(28f, yDown.y, 1e-4f);
        assertEquals(720f - 28f - 22f, yUp.y, 1e-4f);
        assertEquals(720f - yDown.y - yDown.height, yUp.y, 1e-4f);
    }

    @Test
    @DisplayName("D5.6 Minimap and Boss health bar areas do not overlap in standard 1280x720 layout")
    void testMinimapAndBossBarDisjointLayout() {
        Rectangle bossBar = HudRenderer.getBossBarBounds(1280f, 720f, true);
        Rectangle minimap = MinimapRenderer.getMinimapBounds(true);

        // Minimap is bottom-right (1110, 545, 150, 150)
        assertTrue(minimap.x >= 0f && minimap.x + minimap.width <= 1280f);
        assertTrue(minimap.y >= 0f && minimap.y + minimap.height <= 720f);

        // Boss bar is top-center (360, 28, 560, 22)
        assertFalse(bossBar.overlaps(minimap), "Boss bar and Minimap widgets must be completely disjoint");
        assertTrue(bossBar.y + bossBar.height < minimap.y, "Boss bar is at top, minimap is at bottom");
    }
}
