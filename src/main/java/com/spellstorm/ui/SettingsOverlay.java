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
 * Settings overlay reachable from TitleScreen and PauseMenuOverlay.
 * Provides Fullscreen toggle, ControlsOverlay reference list, and Back button (no audio settings).
 */
public class SettingsOverlay {

    public enum Action {
        NONE,
        TOGGLE_FULLSCREEN,
        BACK
    }

    public static final float PANEL_WIDTH = 640.0f;
    public static final float PANEL_HEIGHT = 500.0f;

    private boolean open = false;
    private boolean fullscreen = false;
    private boolean fullscreenHovered = false;
    private boolean backHovered = false;

    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle fullscreenButtonBounds = new Rectangle();
    private final Rectangle controlsAreaBounds = new Rectangle();
    private final Rectangle backButtonBounds = new Rectangle();

    public SettingsOverlay() {
        updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
    }

    public void open() {
        this.open = true;
        this.fullscreenHovered = false;
        this.backHovered = false;
        updateLayout(ViewportManager.VIRTUAL_WIDTH, ViewportManager.VIRTUAL_HEIGHT);
    }

    public void close() {
        this.open = false;
        this.fullscreenHovered = false;
        this.backHovered = false;
    }

    public boolean isOpen() {
        return open;
    }

    public boolean isFullscreen() {
        return fullscreen;
    }

    public void setFullscreen(boolean fullscreen) {
        this.fullscreen = fullscreen;
    }

    public void toggleFullscreen() {
        this.fullscreen = !this.fullscreen;
    }

    public void updateLayout(float viewW, float viewH) {
        float px = (viewW - PANEL_WIDTH) * 0.5f;
        float py = (viewH - PANEL_HEIGHT) * 0.5f;
        panelBounds.set(px, py, PANEL_WIDTH, PANEL_HEIGHT);

        fullscreenButtonBounds.set(px + 60.0f, py + 84.0f, PANEL_WIDTH - 120.0f, 44.0f);
        controlsAreaBounds.set(px + 40.0f, py + 144.0f, PANEL_WIDTH - 80.0f, 270.0f);
        backButtonBounds.set((viewW - 220.0f) * 0.5f, py + PANEL_HEIGHT - 64.0f, 220.0f, 44.0f);
    }

    public void handleMouseMove(Vector2D uiMouse) {
        if (!open || uiMouse == null) {
            fullscreenHovered = false;
            backHovered = false;
            return;
        }
        fullscreenHovered = fullscreenButtonBounds.contains(uiMouse.x, uiMouse.y);
        backHovered = backButtonBounds.contains(uiMouse.x, uiMouse.y);
    }

    public Action handleMouseClick(Vector2D uiMouse) {
        if (!open || uiMouse == null) {
            return Action.NONE;
        }
        if (fullscreenButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            toggleFullscreen();
            return Action.TOGGLE_FULLSCREEN;
        }
        if (backButtonBounds.contains(uiMouse.x, uiMouse.y)) {
            return Action.BACK;
        }
        return Action.NONE;
    }

    public Action handleKeyPress(int keycode) {
        if (!open) {
            return Action.NONE;
        }
        if (keycode == Keys.ESCAPE) {
            return Action.BACK;
        }
        if (keycode == Keys.F11) {
            toggleFullscreen();
            return Action.TOGGLE_FULLSCREEN;
        }
        return Action.NONE;
    }

    public void render(ShapeRenderer sr, SpriteBatch batch, FontManager fontManager,
                       float viewW, float viewH) {
        if (!open) {
            return;
        }
        updateLayout(viewW, viewH);

        if (sr != null) {
            sr.begin(ShapeRenderer.ShapeType.Filled);
            sr.setColor(8f / 255f, 10f / 255f, 18f / 255f, 0.84f);
            sr.rect(0, 0, viewW, viewH);

            sr.setColor(20f / 255f, 24f / 255f, 38f / 255f, 0.98f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

            sr.setColor(90f / 255f, 175f / 255f, 255f / 255f, 1.0f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, 6.0f);

            sr.setColor(fullscreenHovered
                    ? new Color(65f / 255f, 100f / 255f, 165f / 255f, 1f)
                    : new Color(42f / 255f, 62f / 255f, 104f / 255f, 1f));
            sr.rect(fullscreenButtonBounds.x, fullscreenButtonBounds.y,
                    fullscreenButtonBounds.width, fullscreenButtonBounds.height);

            sr.setColor(backHovered
                    ? new Color(65f / 255f, 88f / 255f, 132f / 255f, 1f)
                    : new Color(42f / 255f, 55f / 255f, 84f / 255f, 1f));
            sr.rect(backButtonBounds.x, backButtonBounds.y,
                    backButtonBounds.width, backButtonBounds.height);
            sr.end();

            sr.begin(ShapeRenderer.ShapeType.Line);
            sr.setColor(100f / 255f, 155f / 255f, 235f / 255f, 0.95f);
            sr.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);
            sr.setColor(135f / 255f, 185f / 255f, 255f / 255f, 1.0f);
            sr.rect(fullscreenButtonBounds.x, fullscreenButtonBounds.y,
                    fullscreenButtonBounds.width, fullscreenButtonBounds.height);
            sr.setColor(145f / 255f, 175f / 255f, 230f / 255f, 1.0f);
            sr.rect(backButtonBounds.x, backButtonBounds.y,
                    backButtonBounds.width, backButtonBounds.height);
            sr.end();
        }

        if (batch != null && fontManager != null) {
            batch.begin();
            BitmapFont titleFont = fontManager.fontTitleLarge != null ? fontManager.fontTitleLarge : fontManager.fontRegular;
            BitmapFont boldFont = fontManager.fontBoldRegular != null ? fontManager.fontBoldRegular : fontManager.fontRegular;

            float centerX = panelBounds.x + panelBounds.width * 0.5f;
            if (titleFont != null) {
                titleFont.setColor(125f / 255f, 210f / 255f, 255f / 255f, 1.0f);
                GlyphLayout gl = fontManager.measure(titleFont, "SETTINGS");
                titleFont.draw(batch, "SETTINGS", centerX - gl.width * 0.5f, panelBounds.y + 28.0f);
            }

            if (boldFont != null) {
                String fsText = "DISPLAY MODE: " + (fullscreen ? "FULLSCREEN [F11]" : "WINDOWED [F11]");
                boldFont.setColor(Color.WHITE);
                GlyphLayout glFs = fontManager.measure(boldFont, fsText);
                boldFont.draw(batch, fsText,
                        fullscreenButtonBounds.x + (fullscreenButtonBounds.width - glFs.width) * 0.5f,
                        fullscreenButtonBounds.y + (fullscreenButtonBounds.height - glFs.height) * 0.5f);

                String backText = "BACK  [ESC]";
                GlyphLayout glBack = fontManager.measure(boldFont, backText);
                boldFont.draw(batch, backText,
                        backButtonBounds.x + (backButtonBounds.width - glBack.width) * 0.5f,
                        backButtonBounds.y + (backButtonBounds.height - glBack.height) * 0.5f);
            }
            batch.end();
        }

        ControlsOverlay.renderControlsList(
                sr, batch, fontManager,
                controlsAreaBounds.x, controlsAreaBounds.y,
                controlsAreaBounds.width, controlsAreaBounds.height);
    }

    public Rectangle getPanelBounds() { return new Rectangle(panelBounds); }
    public Rectangle getFullscreenButtonBounds() { return new Rectangle(fullscreenButtonBounds); }
    public Rectangle getControlsAreaBounds() { return new Rectangle(controlsAreaBounds); }
    public Rectangle getBackButtonBounds() { return new Rectangle(backButtonBounds); }
}
