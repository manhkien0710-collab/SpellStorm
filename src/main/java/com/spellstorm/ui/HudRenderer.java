package com.spellstorm.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.AssetRegistry;
import com.spellstorm.assets.FontManager;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.CompleteSheetId;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.ShootingMode;
import com.spellstorm.math.MathUtils;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.PlayerProgression;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Screen-space fixed HUD renderer (fixed 1280x720 overlay).
 * Renders:
 * - Top-center Boss Health Bar with frame, highlight strip, and telemetry text.
 * - Health and Armor Vitals bar (260x18 px).
 * - EXP Progress bar (260x12 px).
 * - Shooting Mode HUD badge (204x22 px).
 * - Active Temporary Ability HUD badge with countdown progress bar (230x34 px).
 * - Match title, spell telemetry, and instructional footer.
 */
public class HudRenderer {

    // Boss Bar Dimensions
    public static final float BOSS_BAR_WIDTH = 560.0f;
    public static final float BOSS_BAR_HEIGHT = 22.0f;
    public static final float BOSS_BAR_BORDER_THICKNESS = 2.2f;
    public static final float BOSS_BAR_TOP_OFFSET = 28.0f;
    public static final float HIGHLIGHT_RATIO = 0.45f;

    // HUD Element Coordinates (Y-down SFML reference coordinates)
    public static final float TITLE_X = 75.0f;
    public static final float TITLE_Y = 60.0f;
    public static final float SPELL_DESC_X = 75.0f;
    public static final float SPELL_DESC_Y = 90.0f;

    public static final float VITALS_BAR_X = 75.0f;
    public static final float VITALS_BAR_Y = 120.0f;
    public static final float VITALS_BAR_WIDTH = 260.0f;
    public static final float VITALS_BAR_HEIGHT = 18.0f;

    public static final float EXP_BAR_X = 75.0f;
    public static final float EXP_BAR_Y = 148.0f;
    public static final float EXP_BAR_WIDTH = 260.0f;
    public static final float EXP_BAR_HEIGHT = 12.0f;

    public static final float SHOOTING_BADGE_X = 75.0f;
    public static final float SHOOTING_BADGE_Y = 172.0f;
    public static final float SHOOTING_BADGE_WIDTH = 204.0f;
    public static final float SHOOTING_BADGE_HEIGHT = 22.0f;

    public static final float POWERUP_BADGE_X = 75.0f;
    public static final float POWERUP_BADGE_Y = 202.0f;
    public static final float POWERUP_BADGE_WIDTH = 230.0f;
    public static final float POWERUP_BADGE_HEIGHT = 34.0f;

    public static final float INSTRUCTIONS_X = 75.0f;
    public static final float INSTRUCTIONS_Y = 630.0f;

    // Phase 2 Dedicated HUD Panel Coordinates (1280x720 Y-down)
    public static final float STATS_PANEL_X = 960.0f;
    public static final float STATS_PANEL_Y = 20.0f;
    public static final float STATS_PANEL_WIDTH = 300.0f;
    public static final float STATS_PANEL_HEIGHT = 52.0f;

    public static final float ELEMENT_PANEL_X = 75.0f;
    public static final float ELEMENT_PANEL_Y = 248.0f;
    public static final float ELEMENT_PANEL_WIDTH = 260.0f;
    public static final float ELEMENT_PANEL_HEIGHT = 54.0f;

    public static final float CONTROLS_BAR_X = 75.0f;
    public static final float CONTROLS_BAR_Y = 670.0f;
    public static final float CONTROLS_BAR_WIDTH = 560.0f;
    public static final float CONTROLS_BAR_HEIGHT = 26.0f;

    public static final float POWERUP_BLINK_THRESHOLD_SECONDS = 4.0f;
    public static final float POWERUP_BLINK_MIN_ALPHA = 0.35f;
    public static final float POWERUP_BLINK_MAX_ALPHA = 1.0f;
    public static final String CONTROLS_HINT_TEXT =
            "WASD Move | LMB Cast | 1-5 Infuse | V Auto | P Pause | C Codex";

    // Palette specifications
    public static final Color COLOR_OUTER_FRAME = new Color(220f / 255f, 50f / 255f, 50f / 255f, 1.0f); // Crimson
    public static final Color COLOR_DARK_SLATE_FILL = new Color(18f / 255f, 20f / 255f, 28f / 255f, 235f / 255f);
    public static final Color COLOR_BRIGHT_CRIMSON_HP = new Color(220f / 255f, 40f / 255f, 50f / 255f, 1.0f);
    public static final Color COLOR_HIGHLIGHT = new Color(1.0f, 120f / 255f, 120f / 255f, 110f / 255f);
    public static final Color COLOR_GOLD_NAME = new Color(1.0f, 215f / 255f, 60f / 255f, 1.0f);
    public static final Color COLOR_WHITE_HP = Color.WHITE.cpy();

    public static final Color COLOR_EXP_FILL = new Color(50f / 255f, 215f / 255f, 255f / 255f, 1.0f);
    public static final Color COLOR_EXP_FRAME = new Color(35f / 255f, 45f / 255f, 60f / 255f, 1.0f);

    public static final Color COLOR_AUTO_FILL = new Color(18f / 255f, 48f / 255f, 30f / 255f, 230f / 255f);
    public static final Color COLOR_AUTO_BORDER = new Color(65f / 255f, 225f / 255f, 115f / 255f, 1.0f);
    public static final Color COLOR_MANUAL_FILL = new Color(24f / 255f, 32f / 255f, 46f / 255f, 230f / 255f);
    public static final Color COLOR_MANUAL_BORDER = new Color(85f / 255f, 120f / 255f, 175f / 255f, 1.0f);

    public static final Color COLOR_PANEL_BORDER = new Color(85f / 255f, 115f / 255f, 160f / 255f, 0.9f);
    public static final Color COLOR_SLOT_EMPTY = new Color(30f / 255f, 34f / 255f, 46f / 255f, 1.0f);
    public static final Color COLOR_HINT_TEXT = new Color(195f / 255f, 210f / 255f, 235f / 255f, 1.0f);
    public static final Color COLOR_OVERDRIVE_BADGE = new Color(175f / 255f, 75f / 255f, 255f / 255f, 1.0f);
    public static final Color COLOR_PIERCING_BADGE = new Color(35f / 255f, 220f / 255f, 255f / 255f, 1.0f);
    public static final Color COLOR_BURST_BADGE = new Color(255f / 255f, 135f / 255f, 30f / 255f, 1.0f);

    private final GlyphLayout glyphLayout = new GlyphLayout();
    private int bossBarRenderCount = 0;
    private TextureManager textureManager;

    public void setTextureManager(TextureManager textureManager) {
        this.textureManager = textureManager;
    }

    public TextureManager getTextureManager() {
        return textureManager;
    }

    /** Maps an {@link Element} (or {@code null} for empty) to its {@link CompleteSheetId#HUD_ELEMENTS} frame index. */
    public static int getElementIconFrameIndex(Element element) {
        if (element == null) {
            return 5;
        }
        switch (element) {
            case FIRE: return 0;
            case ICE: return 1;
            case LIGHTNING: return 2;
            case WIND: return 3;
            case EARTH: return 4;
            default: return 5;
        }
    }

    /** Maps a {@link PowerUpType} to its {@link CompleteSheetId#POWERUP_ICONS} frame index. */
    public static int getPowerUpIconFrameIndex(PowerUpType type) {
        if (type == null) {
            return 0;
        }
        switch (type) {
            case ARCANE_OVERDRIVE: return 0;
            case PIERCING_SURGE: return 1;
            case ELEMENTAL_BURST: return 2;
            default: return 0;
        }
    }

    public int getBossBarRenderCount() {
        return bossBarRenderCount;
    }

    public void resetRenderCounts() {
        bossBarRenderCount = 0;
    }

    // =========================================================================
    // Bounds Getters
    // =========================================================================

    public static Rectangle getHealthBarBounds(boolean yDown) {
        float y = yDown ? VITALS_BAR_Y : (720.0f - VITALS_BAR_Y - VITALS_BAR_HEIGHT);
        return new Rectangle(VITALS_BAR_X, y, VITALS_BAR_WIDTH, VITALS_BAR_HEIGHT);
    }

    public static Rectangle getExpBarBounds(boolean yDown) {
        float y = yDown ? EXP_BAR_Y : (720.0f - EXP_BAR_Y - EXP_BAR_HEIGHT);
        return new Rectangle(EXP_BAR_X, y, EXP_BAR_WIDTH, EXP_BAR_HEIGHT);
    }

    public static Rectangle getShootingModeBadgeBounds(boolean yDown) {
        float y = yDown ? SHOOTING_BADGE_Y : (720.0f - SHOOTING_BADGE_Y - SHOOTING_BADGE_HEIGHT);
        return new Rectangle(SHOOTING_BADGE_X, y, SHOOTING_BADGE_WIDTH, SHOOTING_BADGE_HEIGHT);
    }

    public static Rectangle getPowerUpBadgeBounds(boolean yDown) {
        float y = yDown ? POWERUP_BADGE_Y : (720.0f - POWERUP_BADGE_Y - POWERUP_BADGE_HEIGHT);
        return new Rectangle(POWERUP_BADGE_X, y, POWERUP_BADGE_WIDTH, POWERUP_BADGE_HEIGHT);
    }

    public static Rectangle getStatsPanelBounds(boolean yDown) {
        float y = yDown ? STATS_PANEL_Y : (720.0f - STATS_PANEL_Y - STATS_PANEL_HEIGHT);
        return new Rectangle(STATS_PANEL_X, y, STATS_PANEL_WIDTH, STATS_PANEL_HEIGHT);
    }

    public static Rectangle getElementSlotsPanelBounds(boolean yDown) {
        float y = yDown ? ELEMENT_PANEL_Y : (720.0f - ELEMENT_PANEL_Y - ELEMENT_PANEL_HEIGHT);
        return new Rectangle(ELEMENT_PANEL_X, y, ELEMENT_PANEL_WIDTH, ELEMENT_PANEL_HEIGHT);
    }

    public static Rectangle getControlsHintBarBounds(boolean yDown) {
        float y = yDown ? CONTROLS_BAR_Y : (720.0f - CONTROLS_BAR_Y - CONTROLS_BAR_HEIGHT);
        return new Rectangle(CONTROLS_BAR_X, y, CONTROLS_BAR_WIDTH, CONTROLS_BAR_HEIGHT);
    }

    // =========================================================================
    // Boss Health Bar
    // =========================================================================

    /**
     * Finds the first active, living boss enemy. Returns null if none is found.
     */
    public static Enemy findActiveBoss(List<Enemy> enemies) {
        if (enemies == null) {
            return null;
        }
        for (Enemy e : enemies) {
            if (e != null && e.isAlive() && e.isBoss()) {
                return e;
            }
        }
        return null;
    }

    /**
     * Checks if the boss health bar should be rendered.
     */
    public static boolean isBossBarVisible(List<Enemy> enemies) {
        return findActiveBoss(enemies) != null;
    }

    /**
     * Calculates the screen bounds of the boss health bar with custom bar dimensions.
     */
    public static Rectangle getBossBarBounds(float screenWidth, float screenHeight, float barWidth, float barHeight, boolean yDown) {
        float x = (screenWidth - barWidth) * 0.5f;
        float y = yDown ? BOSS_BAR_TOP_OFFSET : (screenHeight - BOSS_BAR_TOP_OFFSET - barHeight);
        return new Rectangle(x, y, barWidth, barHeight);
    }

    /**
     * Calculates the screen bounds of the boss health bar.
     */
    public static Rectangle getBossBarBounds(float screenWidth, float screenHeight, boolean yDown) {
        return getBossBarBounds(screenWidth, screenHeight, BOSS_BAR_WIDTH, BOSS_BAR_HEIGHT, yDown);
    }

    /**
     * Calculates the screen bounds of the boss name text.
     */
    public static Rectangle getBossNameBounds(float screenWidth, float screenHeight, float textWidth, float textHeight, boolean yDown) {
        Rectangle bar = getBossBarBounds(screenWidth, screenHeight, yDown);
        float x = (screenWidth - textWidth) * 0.5f;
        float y = yDown
                ? Math.max(2.0f, bar.y - textHeight - 6.0f)
                : (bar.y + bar.height + 6.0f);
        return new Rectangle(x, y, textWidth, textHeight);
    }

    /**
     * Calculates the screen bounds of the boss numerical HP text.
     */
    public static Rectangle getBossHpTextBounds(float screenWidth, float screenHeight, float textWidth, float textHeight, boolean yDown) {
        Rectangle bar = getBossBarBounds(screenWidth, screenHeight, yDown);
        float x = (screenWidth - textWidth) * 0.5f;
        float y = bar.y + (bar.height - textHeight) * 0.5f;
        return new Rectangle(x, y, textWidth, textHeight);
    }

    public static String formatHpText(Enemy boss) {
        if (boss == null) {
            return "";
        }
        int curHp = Math.max(0, (int) Math.ceil(boss.getHealth()));
        int maxHp = (int) Math.floor(boss.getMaxHealth());
        int pct = Math.max(0, (int) Math.floor(boss.getHealthRatio() * 100f));
        return curHp + " / " + maxHp + " (" + pct + "%)";
    }

    public static String formatVitalsText(Player player) {
        if (player == null) {
            return "";
        }
        int curHp = Math.max(0, (int) Math.ceil(player.getHealth()));
        int maxHp = (int) Math.floor(player.getMaxHealth());
        int armor = (int) Math.floor(player.getArmor());
        return "HP: " + curHp + " / " + maxHp + " | ARMOR: " + armor;
    }

    public static String formatExpText(PlayerProgression prog) {
        if (prog == null) {
            return "";
        }
        int curExp = prog.getCurrentExp();
        int nextExp = prog.getExpForNextLevel();
        int pct = (int) Math.floor(prog.getProgressRatio() * 100f);
        return "LVL " + prog.getLevel() + " (" + curExp + " / " + nextExp + " - " + pct + "%)";
    }

    public static String formatPowerUpText(ActiveTemporaryAbility ability) {
        if (ability == null || !ability.isActive()) {
            return "";
        }
        return String.format(java.util.Locale.US, "%s (%04.1fs)", ability.getName(), ability.getRemainingDuration());
    }

    public static String formatCompactVitalsText(Player player) {
        if (player == null) {
            return "";
        }
        int curHp = Math.max(0, (int) Math.ceil(player.getHealth()));
        int maxHp = Math.max(1, (int) Math.floor(player.getMaxHealth()));
        int armor = Math.max(0, (int) Math.floor(player.getArmor()));
        return "HP: " + curHp + "/" + maxHp + " | ARM: " + armor;
    }

    public static String formatCompactExpText(PlayerProgression prog) {
        if (prog == null) {
            return "";
        }
        int curExp = Math.max(0, prog.getCurrentExp());
        int nextExp = Math.max(1, prog.getExpForNextLevel());
        int pct = Math.max(0, Math.min(100, (int) Math.floor(prog.getProgressRatio() * 100f)));
        return "LV " + prog.getLevel() + "  EXP " + curExp + "/" + nextExp + " (" + pct + "%)";
    }

    public static String formatShootingModeText(ShootingMode mode) {
        return (mode == ShootingMode.AUTO) ? "MODE: AUTO [V]" : "MODE: MANUAL [V]";
    }

    public static String formatCompactSpellName(SpellDefinition spell) {
        if (spell == null || spell.getName() == null) {
            return "None";
        }
        String name = spell.getName().trim();
        int parenIdx = name.indexOf(" (");
        if (parenIdx > 0) {
            name = name.substring(0, parenIdx).trim();
        }
        return name.isEmpty() ? "None" : name;
    }

    public static String formatCompactPowerUpText(ActiveTemporaryAbility ability) {
        if (ability == null || !ability.isActive()) {
            return "";
        }
        PowerUpType type = ability.getType();
        String shortName = switch (type) {
            case ARCANE_OVERDRIVE -> "OVERDRIVE";
            case PIERCING_SURGE -> "PIERCING";
            case ELEMENTAL_BURST -> "BURST";
        };
        float remaining = Math.max(0.0f, ability.getRemainingDuration());
        return String.format(Locale.US, "%s (%.1fs)", shortName, remaining);
    }

    public static Color getPowerUpBadgeColor(PowerUpType type) {
        if (type == null) {
            return Color.WHITE.cpy();
        }
        return switch (type) {
            case ARCANE_OVERDRIVE -> COLOR_OVERDRIVE_BADGE.cpy();
            case PIERCING_SURGE -> COLOR_PIERCING_BADGE.cpy();
            case ELEMENTAL_BURST -> COLOR_BURST_BADGE.cpy();
        };
    }

    public static boolean isPowerUpBlinking(ActiveTemporaryAbility ability) {
        return ability != null
                && ability.isActive()
                && ability.getRemainingDuration() < POWERUP_BLINK_THRESHOLD_SECONDS;
    }

    public static float computePowerUpBlinkAlpha(ActiveTemporaryAbility ability, float elapsedTime) {
        if (!isPowerUpBlinking(ability)) {
            return POWERUP_BLINK_MAX_ALPHA;
        }
        float wave = 0.5f + 0.5f * (float) Math.sin(elapsedTime * 10.0f);
        return MathUtils.clamp(
                POWERUP_BLINK_MIN_ALPHA + (POWERUP_BLINK_MAX_ALPHA - POWERUP_BLINK_MIN_ALPHA) * wave,
                POWERUP_BLINK_MIN_ALPHA,
                POWERUP_BLINK_MAX_ALPHA);
    }

    public static String formatSurvivalTimer(float elapsedSeconds) {
        float clamped = Math.max(0.0f, elapsedSeconds);
        int totalSeconds = (int) clamped;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    /**
     * Conservative monotonic text width estimator for 12-13pt UI fonts.
     */
    public static float estimateTextWidth(String text) {
        if (text == null || text.isEmpty()) {
            return 0.0f;
        }
        float width = 0.0f;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                width += 4.2f;
            } else if (ch == 'i' || ch == 'l' || ch == 'I' || ch == '|' || ch == '.' || ch == ':' || ch == ',' || ch == '!') {
                width += 4.5f;
            } else if (ch == 'W' || ch == 'M' || ch == 'w' || ch == 'm' || ch == '@') {
                width += 10.5f;
            } else if (Character.isUpperCase(ch)) {
                width += 8.6f;
            } else {
                width += 7.4f;
            }
        }
        return width;
    }

    public void renderBossHealthBar(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                    FontManager fontManager, List<Enemy> enemies,
                                    float screenWidth, float screenHeight, boolean yDown) {
        Enemy activeBoss = findActiveBoss(enemies);
        if (activeBoss == null) {
            return;
        }

        bossBarRenderCount++;

        Rectangle bar = getBossBarBounds(screenWidth, screenHeight, yDown);
        float fillRatio = MathUtils.clamp(activeBoss.getHealthRatio(), 0.0f, 1.0f);
        float fillWidth = bar.width * fillRatio;
        float highlightHeight = bar.height * HIGHLIGHT_RATIO;

        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(COLOR_DARK_SLATE_FILL);
            shapeRenderer.rect(bar.x, bar.y, bar.width, bar.height);

            if (fillWidth > 0.0f) {
                shapeRenderer.setColor(COLOR_BRIGHT_CRIMSON_HP);
                shapeRenderer.rect(bar.x, bar.y, fillWidth, bar.height);

                shapeRenderer.setColor(COLOR_HIGHLIGHT);
                shapeRenderer.rect(bar.x, bar.y + (bar.height - highlightHeight), fillWidth, highlightHeight);
            }
            shapeRenderer.end();

            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(COLOR_OUTER_FRAME);
            shapeRenderer.rect(bar.x, bar.y, bar.width, bar.height);
            shapeRenderer.end();
        }

        if (batch != null && fontManager != null) {
            BitmapFont boldFont = fontManager.fontBoldRegular != null
                    ? fontManager.fontBoldRegular
                    : fontManager.getFont(com.spellstorm.assets.AssetRegistry.FontId.BOLD, 16);
            BitmapFont bodyFont = fontManager.fontVitals != null
                    ? fontManager.fontVitals
                    : fontManager.getFont(com.spellstorm.assets.AssetRegistry.FontId.BODY, 13);

            if (boldFont != null && bodyFont != null) {
                batch.begin();
                String bossName = activeBoss.getName();
                glyphLayout.setText(boldFont, bossName);
                Rectangle nameBounds = getBossNameBounds(screenWidth, screenHeight, glyphLayout.width, glyphLayout.height, yDown);
                boldFont.setColor(COLOR_GOLD_NAME);
                boldFont.draw(batch, bossName, nameBounds.x, nameBounds.y);

                String hpText = formatHpText(activeBoss);
                glyphLayout.setText(bodyFont, hpText);
                Rectangle hpBounds = getBossHpTextBounds(screenWidth, screenHeight, glyphLayout.width, glyphLayout.height, yDown);
                bodyFont.setColor(COLOR_WHITE_HP);
                bodyFont.draw(batch, hpText, hpBounds.x, hpBounds.y);
                batch.end();
            }
        }
    }

    public void renderBossHealthBar(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                    FontManager fontManager, List<Enemy> enemies,
                                    float screenWidth, float screenHeight) {
        renderBossHealthBar(shapeRenderer, batch, fontManager, enemies, screenWidth, screenHeight, true);
    }

    public void renderBossHealthBar(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                    FontManager fontManager, List<Enemy> enemies) {
        renderBossHealthBar(shapeRenderer, batch, fontManager, enemies, 1280.0f, 720.0f, true);
    }

    // =========================================================================
    // Screen-Space Full Fixed HUD
    // =========================================================================

    public void renderHud(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager,
                          Player player, List<Enemy> enemies, PlayerProgression progression,
                          PowerUpManager powerUpManager, SpellDefinition currentSpell,
                          boolean yDown) {
        renderHUD(shapeRenderer, batch, fontManager, player, progression, powerUpManager, enemies, 0, currentSpell, yDown);
    }

    public void renderHUD(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager,
                          Player player, PlayerProgression progression, PowerUpManager powerUpManager,
                          List<Enemy> enemies, int enemiesDefeated, SpellDefinition currentSpell,
                          boolean yDown) {
        renderHUD(shapeRenderer, batch, fontManager, player, progression, powerUpManager,
                enemies, enemiesDefeated, currentSpell, null, 0.0f, 0L, false, yDown);
    }

    public void renderHUD(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager,
                          Player player, PlayerProgression progression, PowerUpManager powerUpManager,
                          List<Enemy> enemies, int enemiesDefeated, SpellDefinition currentSpell,
                          List<Element> activeElements, float elapsedTime, long seed,
                          boolean debugMode, boolean yDown) {
        if (player == null) {
            return;
        }

        Rectangle vitalsBar = getHealthBarBounds(yDown);
        Rectangle expBar = getExpBarBounds(yDown);
        Rectangle shootingBadge = getShootingModeBadgeBounds(yDown);
        Rectangle powerUpBadge = getPowerUpBadgeBounds(yDown);
        Rectangle statsPanel = getStatsPanelBounds(yDown);
        Rectangle elementPanel = getElementSlotsPanelBounds(yDown);
        Rectangle controlsBar = getControlsHintBarBounds(yDown);

        ActiveTemporaryAbility activeAbility = (powerUpManager != null) ? powerUpManager.getActiveAbility() : null;
        boolean powerUpActive = activeAbility != null && activeAbility.isActive();
        float powerUpAlpha = powerUpActive ? computePowerUpBlinkAlpha(activeAbility, elapsedTime) : 1.0f;
        Color powerUpColor = powerUpActive ? getPowerUpBadgeColor(activeAbility.getType()) : Color.WHITE;
        List<Element> resolvedElements = resolveDisplayedElements(activeElements, currentSpell);
        boolean hasHudElements = textureManager != null && textureManager.hasExtendedSheet(CompleteSheetId.HUD_ELEMENTS);
        boolean hasPowerUpIcons = textureManager != null && textureManager.hasExtendedSheet(CompleteSheetId.POWERUP_ICONS);

        // 1. Shapes pass
        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            // Vitals bar fill
            shapeRenderer.setColor(COLOR_DARK_SLATE_FILL);
            shapeRenderer.rect(vitalsBar.x, vitalsBar.y, vitalsBar.width, vitalsBar.height);
            float hpRatio = MathUtils.clamp(player.getHealthRatio(), 0.0f, 1.0f);
            if (hpRatio > 0.0f) {
                shapeRenderer.setColor(COLOR_BRIGHT_CRIMSON_HP);
                shapeRenderer.rect(vitalsBar.x, vitalsBar.y, vitalsBar.width * hpRatio, vitalsBar.height);
            }

            // EXP bar fill
            shapeRenderer.setColor(COLOR_EXP_FRAME);
            shapeRenderer.rect(expBar.x, expBar.y, expBar.width, expBar.height);
            float expRatio = (progression != null) ? MathUtils.clamp(progression.getProgressRatio(), 0.0f, 1.0f) : 0.0f;
            if (expRatio > 0.0f) {
                shapeRenderer.setColor(COLOR_EXP_FILL);
                shapeRenderer.rect(expBar.x, expBar.y, expBar.width * expRatio, expBar.height);
            }

            // Shooting mode badge
            boolean isAuto = player.getShootingMode() == ShootingMode.AUTO;
            shapeRenderer.setColor(isAuto ? COLOR_AUTO_FILL : COLOR_MANUAL_FILL);
            shapeRenderer.rect(shootingBadge.x, shootingBadge.y, shootingBadge.width, shootingBadge.height);

            // Active Power-up badge
            if (powerUpActive) {
                shapeRenderer.setColor(COLOR_DARK_SLATE_FILL.r, COLOR_DARK_SLATE_FILL.g,
                        COLOR_DARK_SLATE_FILL.b, COLOR_DARK_SLATE_FILL.a * powerUpAlpha);
                shapeRenderer.rect(powerUpBadge.x, powerUpBadge.y, powerUpBadge.width, powerUpBadge.height);

                // Fallback flat color emblem on left side when POWERUP_ICONS sheet is unavailable
                shapeRenderer.setColor(powerUpColor.r, powerUpColor.g, powerUpColor.b, powerUpAlpha);
                if (!hasPowerUpIcons) {
                    float iconSize = 18.0f;
                    float iconX = powerUpBadge.x + 8.0f;
                    float iconY = yDown
                            ? (powerUpBadge.y + 6.0f)
                            : (powerUpBadge.y + powerUpBadge.height - 6.0f - iconSize);
                    shapeRenderer.rect(iconX, iconY, iconSize, iconSize);
                }

                // Progress countdown bar along bottom
                float barW = (powerUpBadge.width - 4.0f) * activeAbility.getDurationRatio();
                float barY = yDown ? (powerUpBadge.y + powerUpBadge.height - 4.0f) : (powerUpBadge.y + 1.0f);
                shapeRenderer.rect(powerUpBadge.x + 2.0f, barY, barW, 3.5f);
            }

            // Stats panel fill
            shapeRenderer.setColor(COLOR_DARK_SLATE_FILL);
            shapeRenderer.rect(statsPanel.x, statsPanel.y, statsPanel.width, statsPanel.height);

            // Element slots + active spell panel fill
            shapeRenderer.setColor(COLOR_DARK_SLATE_FILL);
            shapeRenderer.rect(elementPanel.x, elementPanel.y, elementPanel.width, elementPanel.height);
            drawElementSlotFills(shapeRenderer, elementPanel, resolvedElements, yDown, hasHudElements);

            // Controls hint footer bar fill
            shapeRenderer.setColor(COLOR_DARK_SLATE_FILL);
            shapeRenderer.rect(controlsBar.x, controlsBar.y, controlsBar.width, controlsBar.height);

            shapeRenderer.end();

            // Outline pass
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(COLOR_OUTER_FRAME);
            shapeRenderer.rect(vitalsBar.x, vitalsBar.y, vitalsBar.width, vitalsBar.height);

            shapeRenderer.setColor(COLOR_EXP_FILL);
            shapeRenderer.rect(expBar.x, expBar.y, expBar.width, expBar.height);

            shapeRenderer.setColor(isAuto ? COLOR_AUTO_BORDER : COLOR_MANUAL_BORDER);
            shapeRenderer.rect(shootingBadge.x, shootingBadge.y, shootingBadge.width, shootingBadge.height);

            if (powerUpActive) {
                shapeRenderer.setColor(powerUpColor.r, powerUpColor.g, powerUpColor.b, powerUpAlpha);
                shapeRenderer.rect(powerUpBadge.x, powerUpBadge.y, powerUpBadge.width, powerUpBadge.height);
            }

            shapeRenderer.setColor(COLOR_PANEL_BORDER);
            shapeRenderer.rect(statsPanel.x, statsPanel.y, statsPanel.width, statsPanel.height);
            shapeRenderer.rect(elementPanel.x, elementPanel.y, elementPanel.width, elementPanel.height);
            drawElementSlotBorders(shapeRenderer, elementPanel, resolvedElements, yDown);
            shapeRenderer.setColor(COLOR_PANEL_BORDER);
            shapeRenderer.rect(controlsBar.x, controlsBar.y, controlsBar.width, controlsBar.height);

            shapeRenderer.end();
        }

        // 2. Icons & Text pass
        if (batch != null && (fontManager != null || hasHudElements || hasPowerUpIcons)) {
            batch.begin();

            if (hasHudElements) {
                drawElementSlotIcons(batch, textureManager, elementPanel, resolvedElements, yDown);
            }
            if (powerUpActive && hasPowerUpIcons) {
                drawPowerUpBadgeIcon(batch, textureManager, powerUpBadge, activeAbility.getType(), powerUpAlpha, yDown);
            }

            if (fontManager != null) {
                BitmapFont titleFont = (fontManager.fontTitleMedium != null) ? fontManager.fontTitleMedium : fontManager.fontRegular;
                BitmapFont vitalsFont = (fontManager.fontVitals != null) ? fontManager.fontVitals : fontManager.fontRegular;
                BitmapFont smallFont = (fontManager.fontSmall != null) ? fontManager.fontSmall : fontManager.fontRegular;
                BitmapFont expFont = fontManager.getFont(AssetRegistry.FontId.BODY, 10);
                if (expFont == null) {
                    expFont = smallFont;
                }
                BitmapFont boldSmallFont = (fontManager.fontBoldSmall != null) ? fontManager.fontBoldSmall : fontManager.fontRegular;

            if (titleFont != null) {
                titleFont.setColor(Color.WHITE);
                float titleY = yDown ? TITLE_Y : (720.0f - TITLE_Y);
                titleFont.draw(batch, "SPELLSTORM", TITLE_X, titleY);
            }

            if (vitalsFont != null) {
                vitalsFont.setColor(Color.WHITE);
                String vitalsText = formatCompactVitalsText(player);
                glyphLayout.setText(vitalsFont, vitalsText);
                float vy = centeredTextY(vitalsBar, glyphLayout.height, yDown);
                vitalsFont.draw(batch, vitalsText, vitalsBar.x + 8.0f, vy);
            }

            if (expFont != null && progression != null) {
                expFont.setColor(new Color(220f / 255f, 245f / 255f, 1.0f, 1.0f));
                String expText = formatCompactExpText(progression);
                glyphLayout.setText(expFont, expText);
                float ey = centeredTextY(expBar, glyphLayout.height, yDown);
                expFont.draw(batch, expText, expBar.x + 6.0f, ey);
            }

            if (boldSmallFont != null) {
                boolean isAuto = player.getShootingMode() == ShootingMode.AUTO;
                boldSmallFont.setColor(isAuto
                        ? new Color(170f / 255f, 255f / 255f, 200f / 255f, 1f)
                        : new Color(205f / 255f, 225f / 255f, 255f / 255f, 1f));
                String modeText = formatShootingModeText(player.getShootingMode());
                glyphLayout.setText(boldSmallFont, modeText);
                float my = centeredTextY(shootingBadge, glyphLayout.height, yDown);
                boldSmallFont.draw(batch, modeText, shootingBadge.x + 10.0f, my);

                if (powerUpActive) {
                    String compactPowerUp = formatCompactPowerUpText(activeAbility);
                    boldSmallFont.setColor(1.0f, 1.0f, 1.0f, powerUpAlpha);
                    glyphLayout.setText(boldSmallFont, compactPowerUp);
                    float textAreaTop = yDown ? powerUpBadge.y : (powerUpBadge.y + 4.0f);
                    float py = yDown
                            ? (textAreaTop + ((powerUpBadge.height - 4.0f) - glyphLayout.height) * 0.5f)
                            : (textAreaTop + ((powerUpBadge.height - 4.0f) + glyphLayout.height) * 0.5f);
                    boldSmallFont.draw(batch, compactPowerUp, powerUpBadge.x + 34.0f, py);
                }
            }

            // Stats Panel text
            if (boldSmallFont != null && smallFont != null) {
                int level = (progression != null) ? progression.getLevel() : 1;
                String timerText = "TIME " + formatSurvivalTimer(elapsedTime);
                String killsText = "KILLS " + Math.max(0, enemiesDefeated);
                String levelText = "LV " + level;

                boldSmallFont.setColor(COLOR_GOLD_NAME);
                glyphLayout.setText(boldSmallFont, timerText);
                float row1Y = yDown
                        ? (statsPanel.y + (debugMode ? 8.0f : (statsPanel.height - glyphLayout.height) * 0.5f))
                        : (statsPanel.y + statsPanel.height - (debugMode ? 8.0f : (statsPanel.height - glyphLayout.height) * 0.5f));
                boldSmallFont.draw(batch, timerText, statsPanel.x + 12.0f, row1Y);

                boldSmallFont.setColor(Color.WHITE);
                boldSmallFont.draw(batch, killsText, statsPanel.x + 118.0f, row1Y);

                boldSmallFont.setColor(COLOR_EXP_FILL);
                boldSmallFont.draw(batch, levelText, statsPanel.x + 225.0f, row1Y);

                if (debugMode) {
                    smallFont.setColor(COLOR_HINT_TEXT);
                    float row2Y = yDown ? (statsPanel.y + 30.0f) : (statsPanel.y + 22.0f);
                    smallFont.draw(batch, "SEED " + seed, statsPanel.x + 12.0f, row2Y);
                }
            }

            // Element Slots + Active Spell panel text
            if (boldSmallFont != null && smallFont != null) {
                float textX = elementPanel.x + 62.0f;
                smallFont.setColor(COLOR_HINT_TEXT);
                float headerY = yDown ? (elementPanel.y + 8.0f) : (elementPanel.y + elementPanel.height - 8.0f);
                smallFont.draw(batch, "ELEMENTS [1-5]", textX, headerY);

                String spellName = formatCompactSpellName(currentSpell);
                Color spellColor = (currentSpell != null) ? currentSpell.getPrimaryColor() : Color.WHITE;
                boldSmallFont.setColor(spellColor);
                float spellY = yDown ? (elementPanel.y + 28.0f) : (elementPanel.y + 24.0f);
                boldSmallFont.draw(batch, spellName, textX, spellY);
            }

            // Controls Hint Bar text
                if (smallFont != null) {
                    smallFont.setColor(COLOR_HINT_TEXT);
                    glyphLayout.setText(smallFont, CONTROLS_HINT_TEXT);
                    float hx = controlsBar.x + (controlsBar.width - glyphLayout.width) * 0.5f;
                    float hy = centeredTextY(controlsBar, glyphLayout.height, yDown);
                    smallFont.draw(batch, CONTROLS_HINT_TEXT, hx, hy);
                }
            }

            batch.setColor(Color.WHITE);
            batch.end();
        }

        // 3. Boss Bar if active boss exists
        renderBossHealthBar(shapeRenderer, batch, fontManager, enemies, 1280.0f, 720.0f, yDown);
    }

    private static float centeredTextY(Rectangle box, float textHeight, boolean yDown) {
        return yDown
                ? (box.y + (box.height - textHeight) * 0.5f)
                : (box.y + (box.height + textHeight) * 0.5f);
    }

    private static List<Element> resolveDisplayedElements(List<Element> activeElements, SpellDefinition currentSpell) {
        List<Element> result = new ArrayList<>(2);
        if (activeElements != null && !activeElements.isEmpty()) {
            for (Element e : activeElements) {
                if (e != null && result.size() < 2) {
                    result.add(e);
                }
            }
            return result;
        }
        if (currentSpell != null && currentSpell.getElements() != null) {
            for (Element e : Element.values()) {
                if (currentSpell.getElements().contains(e) && result.size() < 2) {
                    result.add(e);
                }
            }
        }
        return result;
    }

    private static void drawElementSlotFills(ShapeRenderer shapeRenderer, Rectangle panel,
                                             List<Element> elements, boolean yDown, boolean useSpriteIcons) {
        float slotSize = 18.0f;
        float slotY = panel.y + (panel.height - slotSize) * 0.5f;
        for (int i = 0; i < 2; i++) {
            float slotX = panel.x + 10.0f + i * (slotSize + 6.0f);
            Color c = (!useSpriteIcons && i < elements.size()) ? elements.get(i).getColor() : COLOR_SLOT_EMPTY;
            shapeRenderer.setColor(c);
            shapeRenderer.rect(slotX, slotY, slotSize, slotSize);
        }
    }

    private static void drawElementSlotIcons(SpriteBatch batch, TextureManager textureManager,
                                             Rectangle panel, List<Element> elements, boolean yDown) {
        float slotSize = 18.0f;
        float slotY = panel.y + (panel.height - slotSize) * 0.5f;
        batch.setColor(Color.WHITE);
        for (int i = 0; i < 2; i++) {
            Element elem = (i < elements.size()) ? elements.get(i) : null;
            TextureRegion region = textureManager.getExtendedFrame(
                    CompleteSheetId.HUD_ELEMENTS, getElementIconFrameIndex(elem));
            if (region != null) {
                float slotX = panel.x + 10.0f + i * (slotSize + 6.0f);
                batch.draw(region.getTexture(), slotX, slotY, slotSize, slotSize,
                        region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
                        false, yDown);
            }
        }
    }

    private static void drawPowerUpBadgeIcon(SpriteBatch batch, TextureManager textureManager,
                                             Rectangle badge, PowerUpType type, float alpha, boolean yDown) {
        TextureRegion iconRegion = textureManager.getExtendedFrame(
                CompleteSheetId.POWERUP_ICONS, getPowerUpIconFrameIndex(type));
        if (iconRegion != null) {
            float iconSize = 18.0f;
            float iconX = badge.x + 8.0f;
            float iconY = yDown ? (badge.y + 6.0f) : (badge.y + badge.height - 6.0f - iconSize);
            batch.setColor(1.0f, 1.0f, 1.0f, alpha);
            batch.draw(iconRegion.getTexture(), iconX, iconY, iconSize, iconSize,
                    iconRegion.getRegionX(), iconRegion.getRegionY(),
                    iconRegion.getRegionWidth(), iconRegion.getRegionHeight(),
                    false, yDown);
            batch.setColor(Color.WHITE);
        }
    }

    private static void drawElementSlotBorders(ShapeRenderer shapeRenderer, Rectangle panel,
                                               List<Element> elements, boolean yDown) {
        float slotSize = 18.0f;
        float slotY = panel.y + (panel.height - slotSize) * 0.5f;
        for (int i = 0; i < 2; i++) {
            float slotX = panel.x + 10.0f + i * (slotSize + 6.0f);
            shapeRenderer.setColor(i < elements.size() ? Color.WHITE : COLOR_PANEL_BORDER);
            shapeRenderer.rect(slotX, slotY, slotSize, slotSize);
        }
    }
}
