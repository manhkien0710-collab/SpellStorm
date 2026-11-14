package com.spellstorm.ui;

import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.FontManager;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;

import java.util.List;

/**
 * Non-blocking 3-line goal banner displayed at the start of a run.
 * Automatically fades out and completes after 8.0 seconds (DEFAULT_DURATION),
 * and can be dismissed immediately by click, SPACE, or ENTER.
 */
public class TutorialOverlay {

    public static final float DEFAULT_DURATION = 8.0f;
    public static final float FADE_WINDOW = 1.5f;
    public static final float VIRTUAL_WIDTH = 1280.0f;
    public static final float VIRTUAL_HEIGHT = 720.0f;
    public static final float BANNER_WIDTH = 560.0f;
    public static final float BANNER_HEIGHT = 96.0f;
    public static final float BANNER_TOP_Y = 78.0f;

    private static final List<String> BANNER_LINES = List.of(
            "GOAL: Survive enemy waves & bosses; collect XP orbs to level up.",
            "CONTROLS: Move [WASD/Arrows], Cast [LMB] or [V] Auto-Shot, Infuse [1-5].",
            "CODEX: Press [C] for all 15 spell recipes & power-ups. [Click/SPACE/ENTER]"
    );

    private float remainingTime = DEFAULT_DURATION;
    private boolean dismissed = false;

    public TutorialOverlay() {
    }

    public void update(float dt) {
        if (dismissed || remainingTime <= 0.0f || dt <= 0.0f) {
            return;
        }
        remainingTime = Math.max(0.0f, remainingTime - dt);
        if (remainingTime <= 0.0f) {
            dismissed = true;
        }
    }

    public void dismiss() {
        this.dismissed = true;
        this.remainingTime = 0.0f;
    }

    public void reset() {
        this.dismissed = false;
        this.remainingTime = DEFAULT_DURATION;
    }

    public boolean isActive() {
        return !dismissed && remainingTime > 0.0f;
    }

    public float getRemainingTime() {
        return remainingTime;
    }

    public float getAlpha() {
        if (!isActive()) {
            return 0.0f;
        }
        if (remainingTime >= FADE_WINDOW) {
            return 1.0f;
        }
        return MathUtils.clamp(remainingTime / FADE_WINDOW, 0.0f, 1.0f);
    }

    public List<String> getBannerLines() {
        return BANNER_LINES;
    }

    /**
     * Handles keyboard dismissal (SPACE or ENTER).
     *
     * @return true if the banner was active and dismissed by this key press.
     */
    public boolean handleKeyPress(int gdxKeyCode) {
        if (!isActive()) {
            return false;
        }
        if (gdxKeyCode == Keys.SPACE || gdxKeyCode == Keys.ENTER) {
            dismiss();
            return true;
        }
        return false;
    }

    /**
     * Handles mouse click dismissal in UI coordinates.
     *
     * @return true if the banner was active and dismissed.
     */
    public boolean handleMouseClick(Vector2D uiMouse) {
        if (!isActive() || uiMouse == null) {
            return false;
        }
        if (getBannerBounds(true).contains(uiMouse.x, uiMouse.y)) {
            dismiss();
            return true;
        }
        return false;
    }

    /**
     * Convenience input handler for GameScreen polling.
     */
    public boolean handleInput(boolean spaceOrEnterPressed, boolean mouseJustPressed, Vector2D uiMouse) {
        if (!isActive()) {
            return false;
        }
        if (spaceOrEnterPressed) {
            dismiss();
            return true;
        }
        if (mouseJustPressed) {
            if (uiMouse == null || getBannerBounds(true).contains(uiMouse.x, uiMouse.y)) {
                dismiss();
                return true;
            }
        }
        return false;
    }

    public Rectangle getBannerBounds(boolean yDown) {
        float x = (VIRTUAL_WIDTH - BANNER_WIDTH) * 0.5f;
        float y = yDown ? BANNER_TOP_Y : (VIRTUAL_HEIGHT - BANNER_TOP_Y - BANNER_HEIGHT);
        return new Rectangle(x, y, BANNER_WIDTH, BANNER_HEIGHT);
    }

    public void render(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager,
                       float screenWidth, float screenHeight) {
        if (!isActive()) {
            return;
        }

        float alpha = getAlpha();
        float viewW = screenWidth > 0 ? screenWidth : VIRTUAL_WIDTH;
        float x = (viewW - BANNER_WIDTH) * 0.5f;
        float y = BANNER_TOP_Y;

        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(16f / 255f, 22f / 255f, 34f / 255f, 0.88f * alpha);
            shapeRenderer.rect(x, y, BANNER_WIDTH, BANNER_HEIGHT);

            shapeRenderer.setColor(80f / 255f, 200f / 255f, 255f / 255f, alpha);
            shapeRenderer.rect(x, y, BANNER_WIDTH, 4.0f);
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(90f / 255f, 165f / 255f, 235f / 255f, alpha);
            shapeRenderer.rect(x, y, BANNER_WIDTH, BANNER_HEIGHT);
            shapeRenderer.end();
        }

        if (batch != null && fontManager != null) {
            batch.begin();
            BitmapFont boldSmall = fontManager.fontBoldSmall != null ? fontManager.fontBoldSmall : fontManager.fontRegular;
            BitmapFont smallFont = fontManager.fontSmall != null ? fontManager.fontSmall : fontManager.fontRegular;

            for (int i = 0; i < BANNER_LINES.size(); i++) {
                String line = BANNER_LINES.get(i);
                BitmapFont font = (i == 0 && boldSmall != null) ? boldSmall : smallFont;
                if (font != null) {
                    if (i == 0) {
                        font.setColor(1.0f, 220f / 255f, 90f / 255f, alpha);
                    } else if (i == 1) {
                        font.setColor(225f / 255f, 235f / 255f, 250f / 255f, alpha);
                    } else {
                        font.setColor(160f / 255f, 215f / 255f, 255f / 255f, alpha);
                    }
                    GlyphLayout gl = fontManager.measure(font, line);
                    font.draw(batch, line, x + (BANNER_WIDTH - gl.width) * 0.5f, y + 26.0f + i * 24.0f);
                }
            }
            batch.end();
        }
    }
}
