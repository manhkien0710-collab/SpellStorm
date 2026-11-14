package com.spellstorm.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.spellstorm.assets.FontManager;

import java.util.List;

/**
 * Shared control bindings catalog and reusable list renderer for Settings and Pause screens.
 * Uses y-down UI coordinates (1280x720 virtual UI space).
 */
public final class ControlsOverlay {

    private static final List<String[]> CONTROL_BINDINGS = List.of(
            new String[]{"WASD / Arrows", "Move Wizard (8-way)"},
            new String[]{"Left Mouse", "Aim & Cast Active Spell"},
            new String[]{"1 - 5", "Infuse Element (Fire, Ice, Lightning, Wind, Earth)"},
            new String[]{"V", "Toggle Auto-Shot / Manual Aim"},
            new String[]{"P / ESC", "Pause / Resume Match"},
            new String[]{"C", "Open / Close Spell & Power-Up Codex"},
            new String[]{"F11", "Toggle Fullscreen"}
    );

    private static final String COMPACT_HINT_BAR_TEXT =
            "WASD Move | LMB Cast | 1-5 Infuse | V Auto | P/ESC Pause | C Codex | F11 Fullscreen";

    private ControlsOverlay() {
    }

    /**
     * Returns immutable [key, description] pairs for all player controls.
     */
    public static List<String[]> getControlBindings() {
        return CONTROL_BINDINGS;
    }

    /**
     * Returns a compact single-line summary string suitable for footer HUD hint bars.
     */
    public static String getCompactHintBarText() {
        return COMPACT_HINT_BAR_TEXT;
    }

    /**
     * Renders the controls table inside the supplied y-down UI rectangle.
     */
    public static void renderControlsList(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                          FontManager fontManager, float x, float y,
                                          float width, float height) {
        if (width <= 0.0f || height <= 0.0f) {
            return;
        }

        final float headerH = Math.min(34.0f, height * 0.2f);
        final float padding = 10.0f;
        final float bodyY = y + headerH + 4.0f;
        final float bodyH = Math.max(20.0f, height - headerH - padding);
        final int count = CONTROL_BINDINGS.size();
        final float rowH = bodyH / Math.max(1, count);
        final float badgeW = Math.min(138.0f, width * 0.34f);

        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(16f / 255f, 20f / 255f, 30f / 255f, 0.94f);
            shapeRenderer.rect(x, y, width, height);

            shapeRenderer.setColor(26f / 255f, 34f / 255f, 52f / 255f, 1.0f);
            shapeRenderer.rect(x, y, width, headerH);

            for (int i = 0; i < count; i++) {
                float ry = bodyY + i * rowH;
                if (i % 2 == 0) {
                    shapeRenderer.setColor(22f / 255f, 27f / 255f, 40f / 255f, 0.85f);
                    shapeRenderer.rect(x + 6.0f, ry + 1.0f, width - 12.0f, Math.max(4.0f, rowH - 3.0f));
                }

                // Keycap pill
                float pillH = Math.max(14.0f, rowH - 6.0f);
                shapeRenderer.setColor(38f / 255f, 52f / 255f, 78f / 255f, 1.0f);
                shapeRenderer.rect(x + 12.0f, ry + (rowH - pillH) * 0.5f, badgeW, pillH);
            }
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(70f / 255f, 110f / 255f, 170f / 255f, 1.0f);
            shapeRenderer.rect(x, y, width, height);

            for (int i = 0; i < count; i++) {
                float ry = bodyY + i * rowH;
                float pillH = Math.max(14.0f, rowH - 6.0f);
                shapeRenderer.setColor(95f / 255f, 150f / 255f, 225f / 255f, 0.9f);
                shapeRenderer.rect(x + 12.0f, ry + (rowH - pillH) * 0.5f, badgeW, pillH);
            }
            shapeRenderer.end();
        }

        if (batch != null && fontManager != null) {
            batch.begin();
            BitmapFont boldSmall = fontManager.fontBoldSmall != null ? fontManager.fontBoldSmall : fontManager.fontRegular;
            BitmapFont smallFont = fontManager.fontSmall != null ? fontManager.fontSmall : fontManager.fontRegular;

            if (boldSmall != null) {
                boldSmall.setColor(120f / 255f, 210f / 255f, 1.0f, 1.0f);
                boldSmall.draw(batch, "CONTROLS", x + 12.0f, y + headerH * 0.68f);
            }

            for (int i = 0; i < count; i++) {
                String[] row = CONTROL_BINDINGS.get(i);
                float ry = bodyY + i * rowH;
                float textY = ry + rowH * 0.65f;

                if (boldSmall != null) {
                    boldSmall.setColor(1.0f, 220f / 255f, 90f / 255f, 1.0f);
                    GlyphLayout glKey = fontManager.measure(boldSmall, row[0]);
                    boldSmall.draw(batch, row[0], x + 12.0f + (badgeW - glKey.width) * 0.5f, textY);
                }

                if (smallFont != null) {
                    smallFont.setColor(220f / 255f, 230f / 255f, 245f / 255f, 1.0f);
                    smallFont.draw(batch, row[1], x + 22.0f + badgeW, textY);
                }
            }
            batch.end();
        }
    }
}
