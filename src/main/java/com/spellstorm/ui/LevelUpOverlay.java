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
import com.spellstorm.systems.UpgradeInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Interactive Level-Up selection modal with explicit two-step confirmation.
 * Step 1: Click a card to select it (or switch selection). Returns false.
 * Step 2: Click the 'Confirm Upgrade' button (or press Enter) to apply it. Returns true.
 *
 * Visual & Behavioral Specs:
 * - Cards: 280 x 330 px, 35 px horizontal spacing.
 * - Usable description content width: 280 - 36 = 244 px.
 * - Grace timer: 0.15s upon opening to prevent misclicks.
 * - Text wrapping: wrapText breaks descriptions into lines <= 244 px.
 */
public class LevelUpOverlay {

    public static final float CARD_WIDTH = 280.0f;
    public static final float CARD_HEIGHT = 330.0f;
    public static final float CARD_SPACING = 35.0f;
    public static final float CONTENT_WIDTH = 244.0f; // 280 - 36 px horizontal padding
    public static final float CONFIRM_BTN_WIDTH = 340.0f;
    public static final float CONFIRM_BTN_HEIGHT = 46.0f;
    public static final float GRACE_TIMER_DURATION = 0.15f;

    private boolean open = false;
    private final List<UpgradeInfo> options = new ArrayList<>();
    private final List<Rectangle> cardBounds = new ArrayList<>();
    private final Rectangle confirmButtonBounds = new Rectangle();

    private int hoveredIndex = -1;
    private int selectedIndex = -1;
    private boolean confirmHovered = false;
    private float openGraceTimer = 0.0f;
    private final Vector2D lastViewSize = new Vector2D(1280.0f, 720.0f);

    public LevelUpOverlay() {
    }

    public void open(List<UpgradeInfo> upgradeOptions) {
        this.options.clear();
        if (upgradeOptions != null) {
            this.options.addAll(upgradeOptions);
        }
        this.open = true;
        this.hoveredIndex = -1;
        this.selectedIndex = -1; // Explicitly start with NO selection!
        this.confirmHovered = false;
        this.openGraceTimer = GRACE_TIMER_DURATION;
        updateLayout(lastViewSize.x, lastViewSize.y);
    }

    public void close() {
        this.open = false;
        this.options.clear();
        this.cardBounds.clear();
        this.hoveredIndex = -1;
        this.selectedIndex = -1;
        this.confirmHovered = false;
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

    public void updateLayout(float viewW, float viewH) {
        lastViewSize.set(viewW, viewH);
        cardBounds.clear();

        final int n = options.size();
        if (n == 0) {
            confirmButtonBounds.set(0, 0, 0, 0);
            return;
        }

        final float totalW = n * CARD_WIDTH + (n - 1) * CARD_SPACING;
        final float startX = (viewW - totalW) * 0.5f;
        final float startY = (viewH - CARD_HEIGHT) * 0.5f - 18.0f;

        for (int i = 0; i < n; i++) {
            float cardX = startX + i * (CARD_WIDTH + CARD_SPACING);
            cardBounds.add(new Rectangle(cardX, startY, CARD_WIDTH, CARD_HEIGHT));
        }

        final float btnX = (viewW - CONFIRM_BTN_WIDTH) * 0.5f;
        final float btnY = startY + CARD_HEIGHT + 24.0f;
        confirmButtonBounds.set(btnX, btnY, CONFIRM_BTN_WIDTH, CONFIRM_BTN_HEIGHT);
    }

    public void handleMouseMove(Vector2D mousePos) {
        if (!open || mousePos == null) {
            hoveredIndex = -1;
            confirmHovered = false;
            return;
        }

        hoveredIndex = -1;
        for (int i = 0; i < cardBounds.size(); i++) {
            if (cardBounds.get(i).contains(mousePos.x, mousePos.y)) {
                hoveredIndex = i;
                break;
            }
        }

        confirmHovered = confirmButtonBounds.contains(mousePos.x, mousePos.y);
    }

    /**
     * Handles mouse clicks with strict two-step selection:
     * - Clicking a card selects it and returns FALSE (does not commit).
     * - Clicking the confirm button returns TRUE only if a card is selected.
     * - Clicks during grace timer (<0.15s) are rejected and return FALSE.
     */
    public boolean handleMouseClick(Vector2D mousePos) {
        if (!open || openGraceTimer > 0.0f || mousePos == null) {
            return false;
        }

        // Step 1: Clicking a card selects it (returns false, NEVER confirms)
        for (int i = 0; i < cardBounds.size(); i++) {
            if (cardBounds.get(i).contains(mousePos.x, mousePos.y)) {
                selectedIndex = i;
                return false;
            }
        }

        // Step 2: Clicking the confirmation button
        if (confirmButtonBounds.contains(mousePos.x, mousePos.y)) {
            if (hasValidSelection()) {
                return true; // Confirmed!
            }
            return false; // Disabled when no card selected
        }

        return false; // Clicked outside
    }

    public boolean handleKeyPress(int gdxKeyCode) {
        if (!open) {
            return false;
        }

        // Number keys 1, 2, 3 to select cards
        if (gdxKeyCode == Keys.NUM_1 || gdxKeyCode == Keys.NUMPAD_1) {
            selectCard(0);
            return false;
        }
        if (gdxKeyCode == Keys.NUM_2 || gdxKeyCode == Keys.NUMPAD_2) {
            selectCard(1);
            return false;
        }
        if (gdxKeyCode == Keys.NUM_3 || gdxKeyCode == Keys.NUMPAD_3) {
            selectCard(2);
            return false;
        }

        // Enter or Space confirms current selection
        if (gdxKeyCode == Keys.ENTER || gdxKeyCode == Keys.SPACE) {
            return tryConfirm();
        }

        return false;
    }

    public void selectCard(int index) {
        if (index >= 0 && index < options.size()) {
            selectedIndex = index;
        }
    }

    public boolean hasValidSelection() {
        return selectedIndex >= 0 && selectedIndex < options.size();
    }

    public boolean tryConfirm() {
        return hasValidSelection();
    }

    public UpgradeInfo getSelectedUpgrade() {
        if (hasValidSelection()) {
            return options.get(selectedIndex);
        }
        return null;
    }

    public UpgradeInfo selectedUpgrade() {
        return getSelectedUpgrade();
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public int selectedIndex() {
        return selectedIndex;
    }

    public int getHoveredIndex() {
        return hoveredIndex;
    }

    public boolean isConfirmHovered() {
        return confirmHovered;
    }

    public float getOpenGraceTimer() {
        return openGraceTimer;
    }

    public List<UpgradeInfo> getOptions() {
        return Collections.unmodifiableList(options);
    }

    public List<UpgradeInfo> options() {
        return Collections.unmodifiableList(options);
    }

    public Rectangle getConfirmButtonBounds() {
        return new Rectangle(confirmButtonBounds);
    }

    public List<Rectangle> getCardBounds() {
        return Collections.unmodifiableList(cardBounds);
    }

    /**
     * Splits description text into lines whose width does not exceed maxWidth (244.0 px).
     * Works both with active LibGDX fonts and in headless mock environments.
     */
    public static List<String> wrapText(String text, BitmapFont font, float maxWidth) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty() || maxWidth <= 0.0f) {
            return lines;
        }

        GlyphLayout layout = null;
        if (font != null) {
            try {
                layout = new GlyphLayout();
            } catch (Throwable ignored) {
                layout = null;
            }
        }

        // Fallback max chars per line if font measurement is unavailable in headless tests
        // At 13px font size, average character width is ~7.0px -> ~34 chars for 244px
        final int fallbackMaxChars = Math.max(10, (int) (maxWidth / 7.0f));

        String[] paragraphs = text.split("\n");
        for (String paragraph : paragraphs) {
            String[] words = paragraph.split("\\s+");
            StringBuilder currentLine = new StringBuilder();

            for (String word : words) {
                if (word.isEmpty()) {
                    continue;
                }

                String candidate = currentLine.length() == 0 ? word : currentLine + " " + word;

                boolean fits = false;
                if (layout != null) {
                    try {
                        layout.setText(font, candidate);
                        fits = layout.width <= maxWidth;
                    } catch (Throwable t) {
                        fits = candidate.length() <= fallbackMaxChars;
                    }
                } else {
                    fits = candidate.length() <= fallbackMaxChars;
                }

                if (fits) {
                    currentLine = new StringBuilder(candidate);
                } else {
                    if (currentLine.length() > 0) {
                        lines.add(currentLine.toString());
                        currentLine = new StringBuilder(word);
                    } else {
                        // Single word exceeds maxWidth, must put it on its own line
                        lines.add(word);
                        currentLine = new StringBuilder();
                    }
                }
            }

            if (currentLine.length() > 0) {
                lines.add(currentLine.toString());
            }
        }

        return lines;
    }

    public void render(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager, float viewW, float viewH) {
        if (!open) {
            return;
        }

        updateLayout(viewW, viewH);

        // 1. Shapes pass
        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            // Backdrop
            shapeRenderer.setColor(10f / 255f, 12f / 255f, 18f / 255f, 220f / 255f);
            shapeRenderer.rect(0, 0, viewW, viewH);

            // Cards
            for (int i = 0; i < cardBounds.size() && i < options.size(); i++) {
                Rectangle card = cardBounds.get(i);
                UpgradeInfo info = options.get(i);
                boolean hovered = (hoveredIndex == i);
                boolean selected = (selectedIndex == i);

                // Card panel background
                if (selected) {
                    shapeRenderer.setColor(36f / 255f, 48f / 255f, 70f / 255f, 1.0f);
                } else if (hovered) {
                    shapeRenderer.setColor(28f / 255f, 34f / 255f, 48f / 255f, 1.0f);
                } else {
                    shapeRenderer.setColor(20f / 255f, 24f / 255f, 35f / 255f, 1.0f);
                }
                shapeRenderer.rect(card.x, card.y, card.width, card.height);

                // Accent ribbon
                Color accent = (info != null && info.accentColor != null) ? info.accentColor : Color.GOLD;
                shapeRenderer.setColor(accent);
                shapeRenderer.rect(card.x, card.y, card.width, 10f);

                // Gem icon circle
                float gemX = card.x + card.width * 0.5f;
                float gemY = card.y + 46f;
                float gemR = selected ? 18f : (hovered ? 16f : 14f);
                shapeRenderer.setColor(accent);
                shapeRenderer.circle(gemX, gemY, gemR);

                // Selection button inside card
                if (selected) {
                    shapeRenderer.setColor(255f / 255f, 215f / 255f, 60f / 255f, 1.0f);
                } else if (hovered) {
                    shapeRenderer.setColor(55f / 255f, 68f / 255f, 92f / 255f, 1.0f);
                } else {
                    shapeRenderer.setColor(32f / 255f, 38f / 255f, 52f / 255f, 1.0f);
                }
                shapeRenderer.rect(card.x + 18f, card.y + card.height - 48f, card.width - 36f, 32f);
            }

            // Confirm button
            boolean canConfirm = hasValidSelection();
            if (canConfirm) {
                if (confirmHovered) {
                    shapeRenderer.setColor(255f / 255f, 230f / 255f, 95f / 255f, 1.0f);
                } else {
                    shapeRenderer.setColor(255f / 255f, 210f / 255f, 50f / 255f, 1.0f);
                }
            } else {
                shapeRenderer.setColor(42f / 255f, 48f / 255f, 60f / 255f, 1.0f);
            }
            shapeRenderer.rect(confirmButtonBounds.x, confirmButtonBounds.y, confirmButtonBounds.width, confirmButtonBounds.height);

            shapeRenderer.end();

            // Card borders pass
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            for (int i = 0; i < cardBounds.size(); i++) {
                Rectangle card = cardBounds.get(i);
                boolean hovered = (hoveredIndex == i);
                boolean selected = (selectedIndex == i);

                if (selected) {
                    shapeRenderer.setColor(255f / 255f, 215f / 255f, 60f / 255f, 1.0f);
                } else if (hovered) {
                    shapeRenderer.setColor(140f / 255f, 160f / 255f, 200f / 255f, 1.0f);
                } else {
                    shapeRenderer.setColor(65f / 255f, 75f / 255f, 100f / 255f, 1.0f);
                }
                shapeRenderer.rect(card.x, card.y, card.width, card.height);
            }

            if (canConfirm) {
                shapeRenderer.setColor(Color.WHITE);
            } else {
                shapeRenderer.setColor(65f / 255f, 72f / 255f, 85f / 255f, 1.0f);
            }
            shapeRenderer.rect(confirmButtonBounds.x, confirmButtonBounds.y, confirmButtonBounds.width, confirmButtonBounds.height);
            shapeRenderer.end();
        }

        // 2. Text pass
        if (batch != null && fontManager != null) {
            batch.begin();

            BitmapFont titleFont = fontManager.fontTitleLarge != null ? fontManager.fontTitleLarge : fontManager.fontRegular;
            BitmapFont regularFont = fontManager.fontRegular != null ? fontManager.fontRegular : fontManager.fontSmall;
            BitmapFont boldFont = fontManager.fontBoldRegular != null ? fontManager.fontBoldRegular : fontManager.fontRegular;
            BitmapFont vitalsFont = fontManager.fontVitals != null ? fontManager.fontVitals : fontManager.fontRegular;

            // Header Banner
            if (titleFont != null) {
                titleFont.setColor(255f / 255f, 215f / 255f, 60f / 255f, 1.0f);
                GlyphLayout gl = fontManager.measure(titleFont, "ARCANE SURGE - LEVEL UP!");
                titleFont.draw(batch, "ARCANE SURGE - LEVEL UP!", (viewW - gl.width) * 0.5f, 80f);
            }

            if (regularFont != null) {
                regularFont.setColor(175f / 255f, 190f / 255f, 215f / 255f, 1.0f);
                String sub = "1. Click a card to select  |  2. Confirm your choice below";
                GlyphLayout gl = fontManager.measure(regularFont, sub);
                regularFont.draw(batch, sub, (viewW - gl.width) * 0.5f, 122f);
            }

            // Cards text
            for (int i = 0; i < cardBounds.size() && i < options.size(); i++) {
                Rectangle card = cardBounds.get(i);
                UpgradeInfo info = options.get(i);
                boolean hovered = (hoveredIndex == i);
                boolean selected = (selectedIndex == i);

                // Card Title
                if (boldFont != null && info != null) {
                    boldFont.setColor(selected ? Color.WHITE : (hovered ? new Color(240f/255f, 245f/255f, 255f/255f, 1f) : new Color(220f/255f, 225f/255f, 235f/255f, 1f)));
                    GlyphLayout gl = fontManager.measure(boldFont, info.title);
                    boldFont.draw(batch, info.title, card.x + (card.width - gl.width) * 0.5f, card.y + 78f);
                }

                // Rank indicator
                if (regularFont != null && info != null) {
                    Color accent = (info.accentColor != null) ? info.accentColor : Color.GOLD;
                    regularFont.setColor(accent);
                    String rankStr = "Rank " + info.currentRank + " -> " + (info.currentRank + 1) + " (Max " + info.maxRank + ")";
                    GlyphLayout gl = fontManager.measure(regularFont, rankStr);
                    regularFont.draw(batch, rankStr, card.x + (card.width - gl.width) * 0.5f, card.y + 110f);
                }

                // Description lines
                if (vitalsFont != null && info != null) {
                    vitalsFont.setColor(185f / 255f, 200f / 255f, 220f / 255f, 1.0f);
                    List<String> descLines = wrapText(info.description, vitalsFont, 244f);
                    float lineH = 18f;
                    float startY = card.y + 138f;
                    for (int l = 0; l < descLines.size(); l++) {
                        String line = descLines.get(l);
                        GlyphLayout gl = fontManager.measure(vitalsFont, line);
                        vitalsFont.draw(batch, line, card.x + (card.width - gl.width) * 0.5f, startY + l * lineH);
                    }
                }

                // Button label inside card
                if (boldFont != null) {
                    boldFont.setColor(selected ? new Color(15f/255f, 20f/255f, 30f/255f, 1f) : Color.WHITE);
                    String btnLabel = selected ? "SELECTED" : (hovered ? "CLICK TO SELECT" : "SELECT");
                    GlyphLayout gl = fontManager.measure(boldFont, btnLabel);
                    float btnY = card.y + card.height - 48f;
                    float btnTextY = btnY + (32f - gl.height) * 0.5f;
                    boldFont.draw(batch, btnLabel, card.x + (card.width - gl.width) * 0.5f, btnTextY);
                }
            }

            // Confirm button label
            if (boldFont != null) {
                boolean canConfirm = hasValidSelection();
                if (canConfirm) {
                    boldFont.setColor(15f / 255f, 20f / 255f, 30f / 255f, 1.0f);
                    String btnText = "CONFIRM UPGRADE  [ENTER]";
                    GlyphLayout gl = fontManager.measure(boldFont, btnText);
                    float confirmTextY = confirmButtonBounds.y + (confirmButtonBounds.height - gl.height) * 0.5f;
                    boldFont.draw(batch, btnText, confirmButtonBounds.x + (confirmButtonBounds.width - gl.width) * 0.5f, confirmTextY);
                } else {
                    boldFont.setColor(140f / 255f, 150f / 255f, 165f / 255f, 1.0f);
                    String btnText = "SELECT AN UPGRADE TO CONFIRM";
                    GlyphLayout gl = fontManager.measure(boldFont, btnText);
                    float confirmTextY = confirmButtonBounds.y + (confirmButtonBounds.height - gl.height) * 0.5f;
                    boldFont.draw(batch, btnText, confirmButtonBounds.x + (confirmButtonBounds.width - gl.width) * 0.5f, confirmTextY);
                }
            }

            batch.end();
        }
    }
}
