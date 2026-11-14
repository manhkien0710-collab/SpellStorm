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
 * Title screen rendered in virtual 1280x720 y-down UI coordinates when GameState == START.
 * Provides Play, Settings, Quit buttons, a Quit confirmation modal, and displays the active Run Seed.
 */
public class TitleScreen {

    public enum Action {
        NONE,
        PLAY,
        SETTINGS,
        QUIT
    }

    public static final float BTN_WIDTH = 280.0f;
    public static final float BTN_HEIGHT = 50.0f;
    public static final float BTN_SPACING = 18.0f;

    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle playButtonBounds = new Rectangle();
    private final Rectangle settingsButtonBounds = new Rectangle();
    private final Rectangle quitButtonBounds = new Rectangle();
    private final Rectangle confirmPanelBounds = new Rectangle();
    private final Rectangle confirmQuitButtonBounds = new Rectangle();
    private final Rectangle cancelQuitButtonBounds = new Rectangle();

    private boolean playHovered = false;
    private boolean settingsHovered = false;
    private boolean quitHovered = false;
    private boolean confirmHovered = false;
    private boolean cancelHovered = false;
    private boolean quitConfirmOpen = false;

    public TitleScreen() {
        updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
    }

    public void updateLayout(float viewW, float viewH) {
        float panelW = 540.0f;
        float panelH = 440.0f;
        float panelX = (viewW - panelW) * 0.5f;
        float panelY = (viewH - panelH) * 0.5f;
        panelBounds.set(panelX, panelY, panelW, panelH);

        float btnX = (viewW - BTN_WIDTH) * 0.5f;
        float startY = panelY + 190.0f;
        playButtonBounds.set(btnX, startY, BTN_WIDTH, BTN_HEIGHT);
        settingsButtonBounds.set(btnX, startY + BTN_HEIGHT + BTN_SPACING, BTN_WIDTH, BTN_HEIGHT);
        quitButtonBounds.set(btnX, startY + (BTN_HEIGHT + BTN_SPACING) * 2.0f, BTN_WIDTH, BTN_HEIGHT);

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
        if (uiMouse == null) {
            clearHover();
            return;
        }
        if (quitConfirmOpen) {
            playHovered = false;
            settingsHovered = false;
            quitHovered = false;
            confirmHovered = confirmQuitButtonBounds.contains(uiMouse.x, uiMouse.y);
            cancelHovered = cancelQuitButtonBounds.contains(uiMouse.x, uiMouse.y);
        } else {
            playHovered = playButtonBounds.contains(uiMouse.x, uiMouse.y);
            settingsHovered = settingsButtonBounds.contains(uiMouse.x, uiMouse.y);
            quitHovered = quitButtonBounds.contains(uiMouse.x, uiMouse.y);
            confirmHovered = false;
            cancelHovered = false;
        }
    }

    public Action handleMouseClick(Vector2D uiMouse) {
        if (uiMouse == null) {
            return Action.NONE;
        }
        if (quitConfirmOpen) {
            if (confirmQuitButtonBounds.contains(uiMouse.x, uiMouse.y)) {
                return Action.QUIT;
            }
            if (cancelQuitButtonBounds.contains(uiMouse.x, uiMouse.y)) {
                quitConfirmOpen = false;
                return Action.NONE;
            }
            return Action.NONE;
        }

        if (playButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            return Action.PLAY;
        }
        if (settingsButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            return Action.SETTINGS;
        }
        if (quitButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            quitConfirmOpen = true;
            return Action.NONE;
        }
        return Action.NONE;
    }

    public Action handleKeyPress(int keycode) {
        if (quitConfirmOpen) {
            if (keycode == Keys.ENTER || keycode == Keys.Y) {
                return Action.QUIT;
            }
            if (keycode == Keys.ESCAPE || keycode == Keys.N) {
                quitConfirmOpen = false;
                return Action.NONE;
            }
            return Action.NONE;
        }

        if (keycode == Keys.ENTER || keycode == Keys.SPACE) {
            return Action.PLAY;
        }
        if (keycode == Keys.ESCAPE) {
            quitConfirmOpen = true;
            return Action.NONE;
        }
        return Action.NONE;
    }

    public void render(ShapeRenderer sr, SpriteBatch batch, FontManager fontManager,
                       long seed, float viewW, float viewH) {
        updateLayout(viewW, viewH);

        if (sr != null) {
            sr.begin(ShapeRenderer.ShapeType.Filled);

            // Dimmed backdrop over arena preview
            sr.setColor(8f / 255f, 10f / 255f, 18f / 255f, 0.80f);
            sr.rect(0, 0, viewW, viewH);

            // Central arcane card
            sr.setColor(18f / 255f, 22f / 255f, 36f / 255f, 0.96f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

            // Top accent ribbon
            sr.setColor(90f / 255f, 175f / 255f, 255f / 255f, 1.0f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, 8.0f);

            // Buttons
            drawButtonFill(sr, playButtonBounds, playHovered,
                    new Color(45f / 255f, 145f / 255f, 95f / 255f, 1f),
                    new Color(65f / 255f, 185f / 255f, 120f / 255f, 1f));
            drawButtonFill(sr, settingsButtonBounds, settingsHovered,
                    new Color(40f / 255f, 58f / 255f, 96f / 255f, 1f),
                    new Color(60f / 255f, 88f / 255f, 140f / 255f, 1f));
            drawButtonFill(sr, quitButtonBounds, quitHovered,
                    new Color(105f / 255f, 34f / 255f, 44f / 255f, 1f),
                    new Color(150f / 255f, 50f / 255f, 64f / 255f, 1f));

            if (quitConfirmOpen) {
                sr.setColor(5f / 255f, 6f / 255f, 12f / 255f, 0.72f);
                sr.rect(0, 0, viewW, viewH);

                sr.setColor(24f / 255f, 26f / 255f, 40f / 255f, 1.0f);
                sr.rect(confirmPanelBounds.x, confirmPanelBounds.y, confirmPanelBounds.width, confirmPanelBounds.height);

                drawButtonFill(sr, confirmQuitButtonBounds, confirmHovered,
                        new Color(140f / 255f, 42f / 255f, 52f / 255f, 1f),
                        new Color(190f / 255f, 60f / 255f, 72f / 255f, 1f));
                drawButtonFill(sr, cancelQuitButtonBounds, cancelHovered,
                        new Color(48f / 255f, 58f / 255f, 82f / 255f, 1f),
                        new Color(72f / 255f, 88f / 255f, 122f / 255f, 1f));
            }
            sr.end();

            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(100f / 255f, 165f / 255f, 255f / 255f, 0.9f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);
            sr.setColor(145f / 255f, 235f / 255f, 180f / 255f, 1f);
            sr.rect(playButtonBounds.x, playButtonBounds.y, playButtonBounds.width, playButtonBounds.height);
            sr.setColor(125f / 255f, 165f / 255f, 235f / 255f, 1f);
            sr.rect(settingsButtonBounds.x, settingsButtonBounds.y, settingsButtonBounds.width, settingsButtonBounds.height);
            sr.setColor(215f / 255f, 95f / 255f, 110f / 255f, 1f);
            sr.rect(quitButtonBounds.x, quitButtonBounds.y, quitButtonBounds.width, quitButtonBounds.height);

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
            BitmapFont titleFont = fontManager.fontTitleHuge != null ? fontManager.fontTitleHuge : fontManager.fontRegular;
            BitmapFont regularFont = fontManager.fontRegular != null ? fontManager.fontRegular : fontManager.fontSmall;
            BitmapFont boldFont = fontManager.fontBoldRegular != null ? fontManager.fontBoldRegular : fontManager.fontRegular;
            BitmapFont smallFont = fontManager.fontSmall != null ? fontManager.fontSmall : fontManager.fontRegular;

            float centerX = panelBounds.x + panelBounds.width * 0.5f;

            if (titleFont != null) {
                titleFont.setColor(125f / 255f, 210f / 255f, 255f / 255f, 1.0f);
                GlyphLayout gl = fontManager.measure(titleFont, "SPELL STORM");
                titleFont.draw(batch, "SPELL STORM", centerX - gl.width * 0.5f, panelBounds.y + 44.0f);
            }

            if (regularFont != null) {
                regularFont.setColor(195f / 255f, 208f / 255f, 232f / 255f, 1.0f);
                String sub = "Elemental Arena Survivor";
                GlyphLayout gl = fontManager.measure(regularFont, sub);
                regularFont.draw(batch, sub, centerX - gl.width * 0.5f, panelBounds.y + 104.0f);
            }

            if (smallFont != null) {
                smallFont.setColor(150f / 255f, 175f / 255f, 215f / 255f, 1.0f);
                String seedText = "RUN SEED: " + seed;
                GlyphLayout gl = fontManager.measure(smallFont, seedText);
                smallFont.draw(batch, seedText, centerX - gl.width * 0.5f, panelBounds.y + 140.0f);
            }

            if (boldFont != null) {
                drawButtonLabel(batch, fontManager, boldFont, playButtonBounds, "PLAY  [ENTER]");
                drawButtonLabel(batch, fontManager, boldFont, settingsButtonBounds, "SETTINGS");
                drawButtonLabel(batch, fontManager, boldFont, quitButtonBounds, "QUIT");

                if (quitConfirmOpen) {
                    float cCenterX = confirmPanelBounds.x + confirmPanelBounds.width * 0.5f;
                    boldFont.setColor(Color.WHITE);
                    String prompt = "QUIT SPELL STORM?";
                    GlyphLayout glP = fontManager.measure(boldFont, prompt);
                    boldFont.draw(batch, prompt, cCenterX - glP.width * 0.5f, confirmPanelBounds.y + 34.0f);

                    if (regularFont != null) {
                        regularFont.setColor(185f / 255f, 195f / 255f, 215f / 255f, 1.0f);
                        String note = "Are you sure you want to exit?";
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

    private void drawButtonFill(ShapeRenderer sr, Rectangle r, boolean hovered, Color normal, Color hover) {
        sr.setColor(hovered ? hover : normal);
        sr.rect(r.x, r.y, r.width, r.height);
    }

    private void drawButtonLabel(SpriteBatch batch, FontManager fm, BitmapFont font, Rectangle bounds, String text) {
        font.setColor(Color.WHITE);
        GlyphLayout gl = fm.measure(font, text);
        font.draw(batch, text, bounds.x + (bounds.width - gl.width) * 0.5f, bounds.y + (bounds.height - gl.height) * 0.5f);
    }

    private void clearHover() {
        playHovered = false;
        settingsHovered = false;
        quitHovered = false;
        confirmHovered = false;
        cancelHovered = false;
    }

    public boolean isQuitConfirmOpen() {
        return quitConfirmOpen;
    }

    public void setQuitConfirmOpen(boolean quitConfirmOpen) {
        this.quitConfirmOpen = quitConfirmOpen;
    }

    public Rectangle getPanelBounds() { return new Rectangle(panelBounds); }
    public Rectangle getPlayButtonBounds() { return new Rectangle(playButtonBounds); }
    public Rectangle getSettingsButtonBounds() { return new Rectangle(settingsButtonBounds); }
    public Rectangle getQuitButtonBounds() { return new Rectangle(quitButtonBounds); }
    public Rectangle getConfirmQuitButtonBounds() { return new Rectangle(confirmQuitButtonBounds); }
    public Rectangle getCancelQuitButtonBounds() { return new Rectangle(cancelQuitButtonBounds); }
}
