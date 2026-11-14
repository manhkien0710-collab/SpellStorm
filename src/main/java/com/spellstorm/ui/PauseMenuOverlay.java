package com.spellstorm.ui;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.FontManager;
import com.spellstorm.math.Vector2D;

/**
 * Interactive pause modal displayed when GameState == PAUSED.
 * Provides Resume, Restart, Settings, Quit to Title (with confirmation), and Run Seed/stats display.
 */
public class PauseMenuOverlay {

    public enum Action {
        NONE,
        RESUME,
        RESTART,
        SETTINGS,
        QUIT_TO_TITLE
    }

    public static final float PANEL_WIDTH = 460.0f;
    public static final float PANEL_HEIGHT = 440.0f;
    public static final float BTN_WIDTH = 280.0f;
    public static final float BTN_HEIGHT = 44.0f;
    public static final float BTN_SPACING = 14.0f;

    private boolean open = false;
    private boolean quitConfirmOpen = false;

    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle resumeButtonBounds = new Rectangle();
    private final Rectangle restartButtonBounds = new Rectangle();
    private final Rectangle settingsButtonBounds = new Rectangle();
    private final Rectangle quitToTitleButtonBounds = new Rectangle();

    private final Rectangle confirmPanelBounds = new Rectangle();
    private final Rectangle confirmQuitButtonBounds = new Rectangle();
    private final Rectangle cancelQuitButtonBounds = new Rectangle();

    private boolean resumeHovered = false;
    private boolean restartHovered = false;
    private boolean settingsHovered = false;
    private boolean quitHovered = false;
    private boolean confirmHovered = false;
    private boolean cancelHovered = false;

    public PauseMenuOverlay() {
        updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
    }

    public void open() {
        this.open = true;
        this.quitConfirmOpen = false;
        clearHover();
        updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
    }

    public void close() {
        this.open = false;
        this.quitConfirmOpen = false;
        clearHover();
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isQuitConfirmOpen() {
        return quitConfirmOpen;
    }

    public void setQuitConfirmOpen(boolean quitConfirmOpen) {
        this.quitConfirmOpen = quitConfirmOpen;
    }

    public void updateLayout(float viewW, float viewH) {
        float px = (viewW - PANEL_WIDTH) * 0.5f;
        float py = (viewH - PANEL_HEIGHT) * 0.5f;
        panelBounds.set(px, py, PANEL_WIDTH, PANEL_HEIGHT);

        float bx = (viewW - BTN_WIDTH) * 0.5f;
        float startY = py + 150.0f;
        resumeButtonBounds.set(bx, startY, BTN_WIDTH, BTN_HEIGHT);
        restartButtonBounds.set(bx, startY + (BTN_HEIGHT + BTN_SPACING), BTN_WIDTH, BTN_HEIGHT);
        settingsButtonBounds.set(bx, startY + (BTN_HEIGHT + BTN_SPACING) * 2.0f, BTN_WIDTH, BTN_HEIGHT);
        quitToTitleButtonBounds.set(bx, startY + (BTN_HEIGHT + BTN_SPACING) * 3.0f, BTN_WIDTH, BTN_HEIGHT);

        float cW = 420.0f;
        float cH = 190.0f;
        float cX = (viewW - cW) * 0.5f;
        float cY = (viewH - cH) * 0.5f;
        confirmPanelBounds.set(cX, cY, cW, cH);

        float cBtnW = 160.0f;
        float cBtnH = 44.0f;
        float cBtnGap = 24.0f;
        float cBtnStartX = cX + (cW - (cBtnW * 2.0f + cBtnGap)) * 0.5f;
        float cBtnY = cY + cH - 68.0f;
        confirmQuitButtonBounds.set(cBtnStartX, cBtnY, cBtnW, cBtnH);
        cancelQuitButtonBounds.set(cBtnStartX + cBtnW + cBtnGap, cBtnY, cBtnW, cBtnH);
    }

    public void handleMouseMove(Vector2D uiMouse) {
        if (!open || uiMouse == null) {
            clearHover();
            return;
        }
        if (quitConfirmOpen) {
            resumeHovered = false;
            restartHovered = false;
            settingsHovered = false;
            quitHovered = false;
            confirmHovered = confirmQuitButtonBounds.contains(uiMouse.x, uiMouse.y);
            cancelHovered = cancelQuitButtonBounds.contains(uiMouse.x, uiMouse.y);
        } else {
            resumeHovered = resumeButtonBounds.contains(uiMouse.x, uiMouse.y);
            restartHovered = restartButtonBounds.contains(uiMouse.x, uiMouse.y);
            settingsHovered = settingsButtonBounds.contains(uiMouse.x, uiMouse.y);
            quitHovered = quitToTitleButtonBounds.contains(uiMouse.x, uiMouse.y);
            confirmHovered = false;
            cancelHovered = false;
        }
    }

    public Action handleMouseClick(Vector2D uiMouse) {
        if (!open || uiMouse == null) {
            return Action.NONE;
        }
        if (quitConfirmOpen) {
            if (confirmQuitButtonBounds.contains(uiMouse.x, uiMouse.y)) {
                quitConfirmOpen = false;
                return Action.QUIT_TO_TITLE;
            }
            if (cancelQuitButtonBounds.contains(uiMouse.x, uiMouse.y)) {
                quitConfirmOpen = false;
                return Action.NONE;
            }
            return Action.NONE;
        }

        if (resumeButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            return Action.RESUME;
        }
        if (restartButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            return Action.RESTART;
        }
        if (settingsButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            return Action.SETTINGS;
        }
        if (quitToTitleButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            quitConfirmOpen = true;
            return Action.NONE;
        }
        return Action.NONE;
    }

    public Action handleKeyPress(int keycode) {
        if (!open) {
            return Action.NONE;
        }
        if (quitConfirmOpen) {
            if (keycode == Keys.ENTER || keycode == Keys.Y) {
                quitConfirmOpen = false;
                return Action.QUIT_TO_TITLE;
            }
            if (keycode == Keys.ESCAPE || keycode == Keys.N) {
                quitConfirmOpen = false;
                return Action.NONE;
            }
            return Action.NONE;
        }

        if (keycode == Keys.ESCAPE || keycode == Keys.P) {
            return Action.RESUME;
        }
        if (keycode == Keys.R) {
            return Action.RESTART;
        }
        return Action.NONE;
    }

    public void render(ShapeRenderer sr, SpriteBatch batch, FontManager fontManager,
                       long seed, float elapsedTime, int enemiesDefeated, int level,
                       float viewW, float viewH) {
        if (!open) {
            return;
        }
        updateLayout(viewW, viewH);

        if (sr != null) {
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(8f / 255f, 10f / 255f, 18f / 255f, 0.78f);
            sr.rect(0, 0, viewW, viewH);

            sr.setColor(20f / 255f, 24f / 255f, 38f / 255f, 0.98f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

            sr.setColor(255f / 255f, 195f / 255f, 65f / 255f, 1.0f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, 6.0f);

            drawButton(sr, resumeButtonBounds, resumeHovered,
                    new Color(45f / 255f, 138f / 255f, 92f / 255f, 1f),
                    new Color(65f / 255f, 178f / 255f, 118f / 255f, 1f));
            drawButton(sr, restartButtonBounds, restartHovered,
                    new Color(48f / 255f, 75f / 255f, 128f / 255f, 1f),
                    new Color(68f / 255f, 105f / 255f, 175f / 255f, 1f));
            drawButton(sr, settingsButtonBounds, settingsHovered,
                    new Color(48f / 255f, 58f / 255f, 88f / 255f, 1f),
                    new Color(72f / 255f, 88f / 255f, 132f / 255f, 1f));
            drawButton(sr, quitToTitleButtonBounds, quitHovered,
                    new Color(108f / 255f, 36f / 255f, 46f / 255f, 1f),
                    new Color(152f / 255f, 52f / 255f, 66f / 255f, 1f));

            if (quitConfirmOpen) {
                sr.setColor(5f / 255f, 6f / 255f, 12f / 255f, 0.72f);
                sr.rect(0, 0, viewW, viewH);

                sr.setColor(26f / 255f, 28f / 255f, 44f / 255f, 1.0f);
                sr.rect(confirmPanelBounds.x, confirmPanelBounds.y, confirmPanelBounds.width, confirmPanelBounds.height);

                drawButton(sr, confirmQuitButtonBounds, confirmHovered,
                        new Color(140f / 255f, 42f / 255f, 52f / 255f, 1f),
                        new Color(190f / 255f, 60f / 255f, 72f / 255f, 1f));
                drawButton(sr, cancelQuitButtonBounds, cancelHovered,
                        new Color(48f / 255f, 58f / 255f, 82f / 255f, 1f),
                        new Color(72f / 255f, 88f / 255f, 122f / 255f, 1f));
            }
            sr.end();

            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(100f / 255f, 145f / 255f, 215f / 255f, 0.9f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);
            sr.setColor(145f / 255f, 235f / 255f, 180f / 255f, 1f);
            sr.rect(resumeButtonBounds.x, resumeButtonBounds.y, resumeButtonBounds.width, resumeButtonBounds.height);
            sr.setColor(135f / 255f, 175f / 255f, 240f / 255f, 1f);
            sr.rect(restartButtonBounds.x, restartButtonBounds.y, restartButtonBounds.width, restartButtonBounds.height);
            sr.setColor(135f / 255f, 155f / 255f, 205f / 255f, 1f);
            sr.rect(settingsButtonBounds.x, settingsButtonBounds.y, settingsButtonBounds.width, settingsButtonBounds.height);
            sr.setColor(215f / 255f, 95f / 255f, 110f / 255f, 1f);
            sr.rect(quitToTitleButtonBounds.x, quitToTitleButtonBounds.y, quitToTitleButtonBounds.width, quitToTitleButtonBounds.height);

            if (quitConfirmOpen) {
                sr.setColor(225f / 255f, 85f / 255f, 95f / 255f, 1f);
                sr.rect(confirmPanelBounds.x, confirmPanelBounds.y, confirmPanelBounds.width, confirmPanelBounds.height);
                sr.setColor(255f / 255f, 150f / 255f, 160f / 255f, 1f);
                sr.rect(confirmQuitButtonBounds.x, confirmQuitButtonBounds.y, confirmQuitButtonBounds.width, confirmQuitButtonBounds.height);
                sr.setColor(160f / 255f, 185f / 255f, 225f / 255f, 1f);
                sr.rect(cancelQuitButtonBounds.x, cancelQuitButtonBounds.y, cancelQuitButtonBounds.width, cancelQuitButtonBounds.height);
            }
            sr.end();
        }

        if (batch != null && fontManager != null) {
            batch.begin();
            BitmapFont titleFont = fontManager.fontTitleLarge != null ? fontManager.fontTitleLarge : fontManager.fontRegular;
            BitmapFont boldFont = fontManager.fontBoldRegular != null ? fontManager.fontBoldRegular : fontManager.fontRegular;
            BitmapFont smallFont = fontManager.fontSmall != null ? fontManager.fontSmall : fontManager.fontRegular;
            BitmapFont regularFont = fontManager.fontRegular != null ? fontManager.fontRegular : fontManager.fontSmall;

            float centerX = panelBounds.x + panelBounds.width * 0.5f;

            if (titleFont != null) {
                titleFont.setColor(255f / 255f, 215f / 255f, 85f / 255f, 1.0f);
                GlyphLayout gl = fontManager.measure(titleFont, "PAUSED");
                titleFont.draw(batch, "PAUSED", centerX - gl.width * 0.5f, panelBounds.y + 34.0f);
            }

            if (smallFont != null) {
                int totalSec = Math.max(0, (int) elapsedTime);
                String timeStr = String.format("%02d:%02d", totalSec / 60, totalSec % 60);
                String stats = String.format("TIME %s  |  KILLS %d  |  LV %d", timeStr, enemiesDefeated, level);
                smallFont.setColor(200f / 255f, 215f / 255f, 235f / 255f, 1.0f);
                GlyphLayout glStats = fontManager.measure(smallFont, stats);
                smallFont.draw(batch, stats, centerX - glStats.width * 0.5f, panelBounds.y + 82.0f);

                String seedLine = "RUN SEED: " + seed;
                smallFont.setColor(145f / 255f, 175f / 255f, 215f / 255f, 1.0f);
                GlyphLayout glSeed = fontManager.measure(smallFont, seedLine);
                smallFont.draw(batch, seedLine, centerX - glSeed.width * 0.5f, panelBounds.y + 106.0f);
            }

            if (boldFont != null) {
                drawButtonLabel(batch, fontManager, boldFont, resumeButtonBounds, "RESUME  [ESC]");
                drawButtonLabel(batch, fontManager, boldFont, restartButtonBounds, "RESTART RUN  [R]");
                drawButtonLabel(batch, fontManager, boldFont, settingsButtonBounds, "SETTINGS");
                drawButtonLabel(batch, fontManager, boldFont, quitToTitleButtonBounds, "QUIT TO TITLE");

                if (quitConfirmOpen) {
                    float cCenterX = confirmPanelBounds.x + confirmPanelBounds.width * 0.5f;
                    boldFont.setColor(Color.WHITE);
                    String prompt = "RETURN TO TITLE?";
                    GlyphLayout glP = fontManager.measure(boldFont, prompt);
                    boldFont.draw(batch, prompt, cCenterX - glP.width * 0.5f, confirmPanelBounds.y + 34.0f);

                    if (regularFont != null) {
                        regularFont.setColor(185f / 255f, 195f / 255f, 215f / 255f, 1.0f);
                        String note = "Current run progress will be lost.";
                        GlyphLayout glN = fontManager.measure(regularFont, note);
                        regularFont.draw(batch, note, cCenterX - glN.width * 0.5f, confirmPanelBounds.y + 72.0f);
                    }

                    drawButtonLabel(batch, fontManager, boldFont, confirmQuitButtonBounds, "CONFIRM");
                    drawButtonLabel(batch, fontManager, boldFont, cancelQuitButtonBounds, "CANCEL");
                }
            }
            batch.end();
        }
    }

    private void drawButton(ShapeRenderer sr, Rectangle r, boolean hovered, Color normal, Color hover) {
        sr.setColor(hovered ? hover : normal);
        sr.rect(r.x, r.y, r.width, r.height);
    }

    private void drawButtonLabel(SpriteBatch batch, FontManager fm, BitmapFont font, Rectangle bounds, String text) {
        font.setColor(Color.WHITE);
        GlyphLayout gl = fm.measure(font, text);
        font.draw(batch, text, bounds.x + (bounds.width - gl.width) * 0.5f, bounds.y + (bounds.height - gl.height) * 0.5f);
    }

    private void clearHover() {
        resumeHovered = false;
        restartHovered = false;
        settingsHovered = false;
        quitHovered = false;
        confirmHovered = false;
        cancelHovered = false;
    }

    public Rectangle getPanelBounds() { return new Rectangle(panelBounds); }
    public Rectangle getResumeButtonBounds() { return new Rectangle(resumeButtonBounds); }
    public Rectangle getRestartButtonBounds() { return new Rectangle(restartButtonBounds); }
    public Rectangle getSettingsButtonBounds() { return new Rectangle(settingsButtonBounds); }
    public Rectangle getQuitToTitleButtonBounds() { return new Rectangle(quitToTitleButtonBounds); }
    public Rectangle getConfirmQuitButtonBounds() { return new Rectangle(confirmQuitButtonBounds); }
    public Rectangle getCancelQuitButtonBounds() { return new Rectangle(cancelQuitButtonBounds); }
}
