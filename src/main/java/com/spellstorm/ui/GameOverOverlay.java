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
 * Game Over overlay screen displayed when the player's health drops to 0.
 *
 * Displays match statistics:
 * - Formatted survival time "MM:SS".
 * - Enemies defeated.
 * - Player level reached.
 *
 * Interaction:
 * - 0.20s open grace timer to discard accidental input.
 * - Interactive Restart and Quit buttons with hover and pressed states.
 * - Keyboard shortcuts: R / Enter / Space -> Restart, Escape / Q -> Quit.
 */
public class GameOverOverlay {

    public enum Action {
        NONE,
        RESTART,
        QUIT
    }

    public static final float PANEL_WIDTH = 520.0f;
    public static final float PANEL_HEIGHT = 440.0f;
    public static final float BTN_WIDTH = 210.0f;
    public static final float BTN_HEIGHT = 48.0f;
    public static final float GRACE_TIMER_DURATION = 0.20f;

    private boolean open = false;
    private float survivalTime = 0.0f;
    private int enemiesDefeated = 0;
    private int levelReached = 1;

    private boolean restartHovered = false;
    private boolean restartPressed = false;
    private boolean quitHovered = false;
    private boolean quitPressed = false;
    private float openGraceTimer = 0.0f;

    private final Rectangle panelBounds = new Rectangle();
    private final Rectangle restartButtonBounds = new Rectangle();
    private final Rectangle quitButtonBounds = new Rectangle();
    private final Vector2D lastViewSize = new Vector2D(1280.0f, 720.0f);

    public GameOverOverlay() {
    }

    public void open(float survivalTime, int enemiesDefeated, int levelReached) {
        this.survivalTime = Math.max(0.0f, survivalTime);
        this.enemiesDefeated = Math.max(0, enemiesDefeated);
        this.levelReached = Math.max(1, levelReached);
        this.open = true;
        this.restartHovered = false;
        this.restartPressed = false;
        this.quitHovered = false;
        this.quitPressed = false;
        this.openGraceTimer = GRACE_TIMER_DURATION;
        updateLayout(lastViewSize.x, lastViewSize.y);
    }

    public void close() {
        this.open = false;
        this.restartHovered = false;
        this.restartPressed = false;
        this.quitHovered = false;
        this.quitPressed = false;
        this.openGraceTimer = 0.0f;
    }

    public boolean isOpen() {
        return open;
    }

    public void update(float dt) {
        if (openGraceTimer > 0.0f) {
            openGraceTimer = Math.max(0.0f, openGraceTimer - dt);
        }
    }

    public String formattedSurvivalTime() {
        int totalSec = (int) survivalTime;
        int minutes = totalSec / 60;
        int seconds = totalSec % 60;
        return String.format("%02d:%02d", minutes, seconds);
    }

    public String getFormattedSurvivalTime() {
        return formattedSurvivalTime();
    }

    public void updateLayout(float viewW, float viewH) {
        lastViewSize.set(viewW, viewH);

        final float panelX = (viewW - PANEL_WIDTH) * 0.5f;
        final float panelY = (viewH - PANEL_HEIGHT) * 0.5f;
        panelBounds.set(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT);

        final float spacing = 24.0f;
        final float totalBtnW = BTN_WIDTH * 2.0f + spacing;
        final float btnStartX = panelX + (PANEL_WIDTH - totalBtnW) * 0.5f;
        final float btnY = panelY + PANEL_HEIGHT - 72.0f;

        restartButtonBounds.set(btnStartX, btnY, BTN_WIDTH, BTN_HEIGHT);
        quitButtonBounds.set(btnStartX + BTN_WIDTH + spacing, btnY, BTN_WIDTH, BTN_HEIGHT);
    }

    public void handleMouseMove(Vector2D mousePos) {
        if (!open || mousePos == null) {
            restartHovered = false;
            quitHovered = false;
            return;
        }

        restartHovered = restartButtonBounds.contains(mousePos.x, mousePos.y);
        quitHovered = quitButtonBounds.contains(mousePos.x, mousePos.y);

        if (!restartHovered) {
            restartPressed = false;
        }
        if (!quitHovered) {
            quitPressed = false;
        }
    }

    public void handleMouseButtonPressed(Vector2D mousePos) {
        if (!open || openGraceTimer > 0.0f || mousePos == null) {
            return;
        }

        if (restartButtonBounds.contains(mousePos.x, mousePos.y)) {
            restartPressed = true;
        } else if (quitButtonBounds.contains(mousePos.x, mousePos.y)) {
            quitPressed = true;
        }
    }

    public Action handleMouseButtonReleased(Vector2D mousePos) {
        if (!open || openGraceTimer > 0.0f || mousePos == null) {
            restartPressed = false;
            quitPressed = false;
            return Action.NONE;
        }

        Action action = Action.NONE;
        if (restartPressed && restartButtonBounds.contains(mousePos.x, mousePos.y)) {
            action = Action.RESTART;
        } else if (quitPressed && quitButtonBounds.contains(mousePos.x, mousePos.y)) {
            action = Action.QUIT;
        }

        restartPressed = false;
        quitPressed = false;
        return action;
    }

    public Action handleMouseClick(Vector2D mousePos) {
        if (!open || openGraceTimer > 0.0f || mousePos == null) {
            return Action.NONE;
        }

        if (restartButtonBounds.contains(mousePos.x, mousePos.y)) {
            return Action.RESTART;
        }
        if (quitButtonBounds.contains(mousePos.x, mousePos.y)) {
            return Action.QUIT;
        }

        return Action.NONE;
    }

    public Action handleKeyPress(int gdxKeyCode) {
        if (!open) {
            return Action.NONE;
        }

        if (gdxKeyCode == Keys.R || gdxKeyCode == Keys.ENTER || gdxKeyCode == Keys.SPACE) {
            return Action.RESTART;
        }
        if (gdxKeyCode == Keys.ESCAPE || gdxKeyCode == Keys.Q) {
            return Action.QUIT;
        }

        return Action.NONE;
    }

    public float getSurvivalTime() {
        return survivalTime;
    }

    public float survivalTime() {
        return survivalTime;
    }

    public int getEnemiesDefeated() {
        return enemiesDefeated;
    }

    public int enemiesDefeated() {
        return enemiesDefeated;
    }

    public int getLevelReached() {
        return levelReached;
    }

    public int levelReached() {
        return levelReached;
    }

    public float getOpenGraceTimer() {
        return openGraceTimer;
    }

    public float openGraceTimer() {
        return openGraceTimer;
    }

    public Rectangle getPanelBounds() {
        return new Rectangle(panelBounds);
    }

    public Rectangle getRestartButtonBounds() {
        return new Rectangle(restartButtonBounds);
    }

    public Rectangle getQuitButtonBounds() {
        return new Rectangle(quitButtonBounds);
    }

    public boolean isRestartHovered() {
        return restartHovered;
    }

    public boolean isRestartPressed() {
        return restartPressed;
    }

    public boolean isQuitHovered() {
        return quitHovered;
    }

    public boolean isQuitPressed() {
        return quitPressed;
    }

    public void render(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager, float viewW, float viewH) {
        if (!open) {
            return;
        }

        updateLayout(viewW, viewH);
        float centerX = panelBounds.x + panelBounds.width * 0.5f;

        // 1. Shapes pass
        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            // Full screen backdrop
            shapeRenderer.setColor(14f / 255f, 8f / 255f, 14f / 255f, 230f / 255f);
            shapeRenderer.rect(0, 0, viewW, viewH);

            // Main panel
            shapeRenderer.setColor(22f / 255f, 16f / 255f, 26f / 255f, 1.0f);
            shapeRenderer.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);

            // Top ribbon
            shapeRenderer.setColor(235f / 255f, 55f / 255f, 70f / 255f, 1.0f);
            shapeRenderer.rect(panelBounds.x, panelBounds.y, panelBounds.width, 8f);

            // Sigil icon
            float sigilY = panelBounds.y + 46f;
            shapeRenderer.setColor(38f / 255f, 20f / 255f, 30f / 255f, 1.0f);
            shapeRenderer.circle(centerX, sigilY, 22f);
            shapeRenderer.setColor(190f / 255f, 35f / 255f, 50f / 255f, 1.0f);
            shapeRenderer.circle(centerX, sigilY, 12f);
            shapeRenderer.setColor(255f / 255f, 220f / 255f, 230f / 255f, 1.0f);
            shapeRenderer.circle(centerX, sigilY, 4f);

            // Stats box
            float statsBoxW = 444f;
            float statsBoxH = 145f;
            float statsBoxX = panelBounds.x + (panelBounds.width - statsBoxW) * 0.5f;
            float statsBoxY = panelBounds.y + 148f;
            shapeRenderer.setColor(14f / 255f, 11f / 255f, 18f / 255f, 1.0f);
            shapeRenderer.rect(statsBoxX, statsBoxY, statsBoxW, statsBoxH);

            // Stat dots
            shapeRenderer.setColor(100f / 255f, 215f / 255f, 255f / 255f, 1.0f);
            shapeRenderer.circle(statsBoxX + 28f, statsBoxY + 46f, 5f);
            shapeRenderer.setColor(255f / 255f, 80f / 255f, 90f / 255f, 1.0f);
            shapeRenderer.circle(statsBoxX + 28f, statsBoxY + 78f, 5f);
            shapeRenderer.setColor(80f / 255f, 240f / 255f, 140f / 255f, 1.0f);
            shapeRenderer.circle(statsBoxX + 28f, statsBoxY + 110f, 5f);

            // Restart button
            if (restartPressed) {
                shapeRenderer.setColor(25f / 255f, 95f / 255f, 55f / 255f, 1.0f);
            } else if (restartHovered) {
                shapeRenderer.setColor(45f / 255f, 165f / 255f, 95f / 255f, 1.0f);
            } else {
                shapeRenderer.setColor(35f / 255f, 125f / 255f, 75f / 255f, 1.0f);
            }
            shapeRenderer.rect(restartButtonBounds.x, restartButtonBounds.y, restartButtonBounds.width, restartButtonBounds.height);

            // Quit button
            if (quitPressed) {
                shapeRenderer.setColor(75f / 255f, 22f / 255f, 30f / 255f, 1.0f);
            } else if (quitHovered) {
                shapeRenderer.setColor(145f / 255f, 40f / 255f, 55f / 255f, 1.0f);
            } else {
                shapeRenderer.setColor(95f / 255f, 30f / 255f, 40f / 255f, 1.0f);
            }
            shapeRenderer.rect(quitButtonBounds.x, quitButtonBounds.y, quitButtonBounds.width, quitButtonBounds.height);

            shapeRenderer.end();

            // Outline pass
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            // Panel outline
            shapeRenderer.setColor(210f / 255f, 48f / 255f, 64f / 255f, 1.0f);
            shapeRenderer.rect(panelBounds.x, panelBounds.y, panelBounds.width, panelBounds.height);
            // Stats outline
            shapeRenderer.setColor(65f / 255f, 50f / 255f, 75f / 255f, 1.0f);
            shapeRenderer.rect(statsBoxX, statsBoxY, statsBoxW, statsBoxH);
            // Button outlines
            shapeRenderer.setColor(restartHovered ? new Color(220f / 255f, 255f / 255f, 235f / 255f, 1f) : new Color(70f / 255f, 185f / 255f, 115f / 255f, 1f));
            shapeRenderer.rect(restartButtonBounds.x, restartButtonBounds.y, restartButtonBounds.width, restartButtonBounds.height);
            shapeRenderer.setColor(quitHovered ? new Color(255f / 255f, 190f / 255f, 200f / 255f, 1f) : new Color(160f / 255f, 50f / 255f, 65f / 255f, 1f));
            shapeRenderer.rect(quitButtonBounds.x, quitButtonBounds.y, quitButtonBounds.width, quitButtonBounds.height);

            shapeRenderer.end();
        }

        // 2. Text pass
        if (batch != null && fontManager != null) {
            batch.begin();

            BitmapFont titleFont = fontManager.fontTitleHuge != null ? fontManager.fontTitleHuge : fontManager.fontRegular;
            BitmapFont regularFont = fontManager.fontRegular != null ? fontManager.fontRegular : fontManager.fontSmall;
            BitmapFont boldFont = fontManager.fontBoldRegular != null ? fontManager.fontBoldRegular : fontManager.fontRegular;

            // Header "GAME OVER"
            if (titleFont != null) {
                titleFont.setColor(255f / 255f, 65f / 255f, 75f / 255f, 1.0f);
                GlyphLayout gl = fontManager.measure(titleFont, "GAME OVER");
                titleFont.draw(batch, "GAME OVER", centerX - gl.width * 0.5f, panelBounds.y + 76f);
            }

            // Subtitle
            if (regularFont != null) {
                regularFont.setColor(195f / 255f, 180f / 255f, 205f / 255f, 1.0f);
                String sub = "Your arcane flame has been extinguished";
                GlyphLayout gl = fontManager.measure(regularFont, sub);
                regularFont.draw(batch, sub, centerX - gl.width * 0.5f, panelBounds.y + 120f);
            }

            // Stats box text
            float statsBoxW = 444f;
            float statsBoxX = panelBounds.x + (panelBounds.width - statsBoxW) * 0.5f;
            float statsBoxY = panelBounds.y + 148f;

            if (boldFont != null) {
                boldFont.setColor(255f / 255f, 215f / 255f, 60f / 255f, 1.0f);
                String header = "- BATTLE STATISTICS -";
                GlyphLayout gl = fontManager.measure(boldFont, header);
                boldFont.draw(batch, header, centerX - gl.width * 0.5f, statsBoxY + 14f);
            }

            if (regularFont != null && boldFont != null) {
                regularFont.setColor(185f / 255f, 195f / 255f, 215f / 255f, 1.0f);
                boldFont.setColor(Color.WHITE);

                // Row 1
                regularFont.draw(batch, "Survival Time:", statsBoxX + 44f, statsBoxY + 40f);
                String timeVal = formattedSurvivalTime() + "  (" + (int) survivalTime + "s)";
                GlyphLayout gl1 = fontManager.measure(boldFont, timeVal);
                boldFont.draw(batch, timeVal, statsBoxX + statsBoxW - gl1.width - 24f, statsBoxY + 40f);

                // Row 2
                regularFont.draw(batch, "Enemies Defeated:", statsBoxX + 44f, statsBoxY + 72f);
                String foesVal = String.valueOf(enemiesDefeated);
                GlyphLayout gl2 = fontManager.measure(boldFont, foesVal);
                boldFont.draw(batch, foesVal, statsBoxX + statsBoxW - gl2.width - 24f, statsBoxY + 72f);

                // Row 3
                regularFont.draw(batch, "Level Reached:", statsBoxX + 44f, statsBoxY + 104f);
                String lvlVal = "Level " + levelReached;
                GlyphLayout gl3 = fontManager.measure(boldFont, lvlVal);
                boldFont.draw(batch, lvlVal, statsBoxX + statsBoxW - gl3.width - 24f, statsBoxY + 104f);
            }

            // Buttons text
            if (boldFont != null) {
                boldFont.setColor(Color.WHITE);
                GlyphLayout glR = fontManager.measure(boldFont, "RESTART  [R]");
                boldFont.draw(batch, "RESTART  [R]",
                        restartButtonBounds.x + (restartButtonBounds.width - glR.width) * 0.5f,
                        restartButtonBounds.y + (restartButtonBounds.height - glR.height) * 0.5f);

                GlyphLayout glQ = fontManager.measure(boldFont, "QUIT  [ESC]");
                boldFont.draw(batch, "QUIT  [ESC]",
                        quitButtonBounds.x + (quitButtonBounds.width - glQ.width) * 0.5f,
                        quitButtonBounds.y + (quitButtonBounds.height - glQ.height) * 0.5f);
            }

            batch.end();
        }
    }
}
