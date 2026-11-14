package com.spellstorm.p2;

import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.assets.FontManager;
import com.spellstorm.math.Vector2D;
import com.spellstorm.ui.MinimapRenderer;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Pod 1: Minimap External Label, Viewport Rect & Boss/PowerUp Markers Tests")
public class MinimapP2Test {

    @BeforeAll
    static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("Minimap label bounds sit strictly outside/above minimap box in both yDown and yUp")
    void testMinimapLabelStrictlyOutsideAndAbove() {
        Rectangle boxDown = MinimapRenderer.getMinimapBounds(true);
        Rectangle labelDown = MinimapRenderer.getMinimapLabelBounds(true);

        assertFalse(boxDown.overlaps(labelDown), "Label must not overlap minimap box (yDown)");
        assertTrue(labelDown.y + labelDown.height <= boxDown.y,
                "Label bottom (" + (labelDown.y + labelDown.height) + ") must be <= box top (" + boxDown.y + ")");
        assertTrue(labelDown.x >= 1110f && labelDown.x + labelDown.width <= 1260f);
        assertTrue(labelDown.y >= 525f && boxDown.y + boxDown.height <= 695f);

        Rectangle boxUp = MinimapRenderer.getMinimapBounds(false);
        Rectangle labelUp = MinimapRenderer.getMinimapLabelBounds(false);

        assertFalse(boxUp.overlaps(labelUp), "Label must not overlap minimap box (yUp)");
        assertTrue(labelUp.y >= boxUp.y + boxUp.height,
                "In yUp, label bottom (" + labelUp.y + ") must be >= box top (" + (boxUp.y + boxUp.height) + ")");
        assertEquals(720f - labelDown.y - labelDown.height, labelUp.y, 1e-4f);

        // Verify "MAP [OVERWORLD]" text fits inside label bounds
        FontManager fm = new FontManager();
        fm.initialize();
        try {
            BitmapFont font = fm.fontSmall;
            GlyphLayout layout = new GlyphLayout(font, "MAP [OVERWORLD]");
            assertTrue(layout.width <= labelDown.width,
                    "Label text width " + layout.width + " must fit inside " + labelDown.width);
            assertTrue(layout.height <= labelDown.height,
                    "Label text height " + layout.height + " must fit inside " + labelDown.height);
        } finally {
            fm.dispose();
        }
    }

    @Test
    @DisplayName("computeViewportRectOnMinimap scales accurately and clamps strictly inside minimap bounds")
    void testComputeViewportRectOnMinimapClamping() {
        Rectangle mmDown = MinimapRenderer.getMinimapBounds(true);

        // 1. Interior viewport (quarter of 4096x4096 world centered at 2048, 2048)
        Rectangle camCenter = new Rectangle(1024f, 1024f, 2048f, 2048f);
        Rectangle vpDown = MinimapRenderer.computeViewportRectOnMinimap(camCenter, 4096f, 4096f, true);
        assertEquals(mmDown.x + 37.5f, vpDown.x, 1e-4f);
        assertEquals(mmDown.y + 37.5f, vpDown.y, 1e-4f);
        assertEquals(75.0f, vpDown.width, 1e-4f);
        assertEquals(75.0f, vpDown.height, 1e-4f);

        // 2. Oversized / out-of-bounds camera rectangle clamps strictly inside minimap
        Rectangle camOversized = new Rectangle(-1000f, -500f, 7000f, 6000f);
        Rectangle vpClamped = MinimapRenderer.computeViewportRectOnMinimap(camOversized, 4096f, 4096f, true);
        assertEquals(mmDown.x, vpClamped.x, 1e-4f);
        assertEquals(mmDown.y, vpClamped.y, 1e-4f);
        assertEquals(mmDown.width, vpClamped.width, 1e-4f);
        assertEquals(mmDown.height, vpClamped.height, 1e-4f);

        // 3. Partially out-of-bounds right/bottom corner clamps inside minimap
        Rectangle camCorner = new Rectangle(3500f, 3500f, 1200f, 1200f);
        Rectangle vpCorner = MinimapRenderer.computeViewportRectOnMinimap(camCorner, 4096f, 4096f, true);
        assertTrue(vpCorner.x >= mmDown.x);
        assertTrue(vpCorner.y >= mmDown.y);
        assertTrue(vpCorner.x + vpCorner.width <= mmDown.x + mmDown.width + 1e-4f);
        assertTrue(vpCorner.y + vpCorner.height <= mmDown.y + mmDown.height + 1e-4f);

        // 4. Y-up inversion maps top-left world region to top-left of Y-up minimap
        Rectangle mmUp = MinimapRenderer.getMinimapBounds(false);
        Rectangle camTopLeft = new Rectangle(0f, 0f, 2048f, 1024f);
        Rectangle vpUp = MinimapRenderer.computeViewportRectOnMinimap(camTopLeft, 4096f, 4096f, false);
        assertEquals(mmUp.x, vpUp.x, 1e-4f);
        assertEquals(mmUp.y + mmUp.height - 37.5f, vpUp.y, 1e-4f);
        assertEquals(75.0f, vpUp.width, 1e-4f);
        assertEquals(37.5f, vpUp.height, 1e-4f);

        // 5. Defensive null / invalid world size handling
        Rectangle vpNull = MinimapRenderer.computeViewportRectOnMinimap(null, 4096f, 4096f, true);
        assertNotNull(vpNull);
        assertEquals(0f, vpNull.width, 1e-4f);
        Rectangle vpZero = MinimapRenderer.computeViewportRectOnMinimap(camCenter, 0f, 4096f, true);
        assertEquals(0f, vpZero.width, 1e-4f);
    }

    @Test
    @DisplayName("10-arg renderMinimap handles bosses, power-ups, viewport rect, and nulls cleanly headless")
    void testPhase2RenderMinimapOverloadHeadless() {
        MinimapRenderer minimap = new MinimapRenderer();
        TileMap tileMap = new TileMap(42L);
        Rectangle camBounds = new Rectangle(1400f, 1500f, 948f, 533f);

        assertDoesNotThrow(() -> {
            minimap.renderMinimap(
                    null, null, null, tileMap,
                    new Vector2D(2048f, 2048f),
                    List.of(new Vector2D(1500f, 1600f), new Vector2D(2500f, 2400f)),
                    List.of(new Vector2D(3000f, 3000f)),
                    List.of(new Vector2D(1800f, 1900f)),
                    camBounds,
                    true);
            minimap.renderMinimap(
                    null, null, null, null,
                    null, null, null, null, null, false);
        });
    }
}
