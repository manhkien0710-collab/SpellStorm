package com.spellstorm.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.FontManager;
import com.spellstorm.combat.Element;
import com.spellstorm.combat.ProjectileProfile;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import com.spellstorm.core.GameSession;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ActiveTemporaryAbility;
import com.spellstorm.systems.PowerUpType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Toggleable Spell & Power-Up Codex overlay ('C' key).
 *
 * Displays all 15 elemental spell slots from SpellRegistry (5 single-element base spells
 * + all 10 dual-element combinations) with element color recipes, spell name, damage,
 * cooldown, speed/range, and effect trait, alongside all 3 PowerUpType entries with
 * primary/secondary swatches and descriptions.
 *
 * Operates in virtual 1280x720 y-down UI coordinates.
 */
public class CodexOverlay {

    public static final float VIRTUAL_WIDTH = 1280.0f;
    public static final float VIRTUAL_HEIGHT = 720.0f;
    public static final float PANEL_WIDTH = 1120.0f;
    public static final float PANEL_HEIGHT = 640.0f;
    public static final float CLOSE_BTN_WIDTH = 112.0f;
    public static final float CLOSE_BTN_HEIGHT = 30.0f;

    private static final float BASE_SPELL_DAMAGE = 35.0f;
    private static final float BASE_PROJECTILE_SPEED = GameSession.BASE_PLAYER_PROJECTILE_SPEED;

    /**
     * Immutable display entry for one spell or dual-element recipe slot in the Codex.
     */
    public static class SpellEntry {
        public final List<Element> elements;
        public final SpellDefinition spell;
        public final boolean combination;
        public final boolean registered;
        public final String keyHint;
        public final String recipeLabel;
        public final String spellName;
        public final String damageText;
        public final String cooldownText;
        public final String speedRangeText;
        public final String effectTrait;
        public final Color primaryColor;
        public final Color secondaryColor;

        public SpellEntry(List<Element> elements, SpellDefinition spell, boolean combination,
                          boolean registered, String keyHint, String recipeLabel, String spellName,
                          String damageText, String cooldownText, String speedRangeText,
                          String effectTrait, Color primaryColor, Color secondaryColor) {
            this.elements = Collections.unmodifiableList(new ArrayList<>(elements));
            this.spell = spell;
            this.combination = combination;
            this.registered = registered;
            this.keyHint = keyHint;
            this.recipeLabel = recipeLabel;
            this.spellName = spellName;
            this.damageText = damageText;
            this.cooldownText = cooldownText;
            this.speedRangeText = speedRangeText;
            this.effectTrait = effectTrait;
            this.primaryColor = primaryColor.cpy();
            this.secondaryColor = secondaryColor.cpy();
        }
    }

    /**
     * Immutable display entry for one PowerUpType in the Codex.
     */
    public static class PowerUpEntry {
        public final PowerUpType type;
        public final String name;
        public final String description;
        public final Color primaryColor;
        public final Color secondaryColor;

        public PowerUpEntry(PowerUpType type, String name, String description,
                            Color primaryColor, Color secondaryColor) {
            this.type = type;
            this.name = name;
            this.description = description;
            this.primaryColor = primaryColor.cpy();
            this.secondaryColor = secondaryColor.cpy();
        }
    }

    private boolean visible = false;

    public CodexOverlay() {
    }

    public boolean isVisible() {
        return visible;
    }

    public void setVisible(boolean visible) {
        this.visible = visible;
    }

    public void toggle() {
        this.visible = !this.visible;
    }

    /**
     * Processes modal input in virtual 1280x720 y-down UI coordinates.
     *
     * @return true if the overlay is open or consumed the input (opening/closing), false otherwise.
     */
    public boolean handleInput(boolean togglePressed, boolean escapePressed,
                               Vector2D uiMouse, boolean mouseJustPressed) {
        if (!visible) {
            if (togglePressed) {
                visible = true;
                return true;
            }
            return false;
        }

        if (togglePressed || escapePressed) {
            visible = false;
            return true;
        }

        if (mouseJustPressed && uiMouse != null && getCloseButtonBounds(true).contains(uiMouse.x, uiMouse.y)) {
            visible = false;
            return true;
        }

        return true;
    }

    public Rectangle getPanelBounds(boolean yDown) {
        float x = (VIRTUAL_WIDTH - PANEL_WIDTH) * 0.5f;
        float yDownTop = (VIRTUAL_HEIGHT - PANEL_HEIGHT) * 0.5f;
        float y = yDown ? yDownTop : (VIRTUAL_HEIGHT - yDownTop - PANEL_HEIGHT);
        return new Rectangle(x, y, PANEL_WIDTH, PANEL_HEIGHT);
    }

    public Rectangle getCloseButtonBounds(boolean yDown) {
        Rectangle panel = getPanelBounds(true);
        float x = panel.x + panel.width - CLOSE_BTN_WIDTH - 18.0f;
        float yDownTop = panel.y + 14.0f;
        float y = yDown ? yDownTop : (VIRTUAL_HEIGHT - yDownTop - CLOSE_BTN_HEIGHT);
        return new Rectangle(x, y, CLOSE_BTN_WIDTH, CLOSE_BTN_HEIGHT);
    }

    /**
     * Builds all 15 spell entries (5 single-element base spells + 10 dual-element pairs).
     */
    public List<SpellEntry> getSpellEntries(SpellRegistry registry) {
        List<SpellEntry> entries = new ArrayList<>(15);
        Element[] elements = Element.values();

        // 1. 5 single-element base spells
        for (Element e : elements) {
            SpellDefinition spell = (registry != null) ? registry.baseSpell(e) : null;
            String keyHint = "[" + (e.getIndex() + 1) + "]";
            String recipeLabel = e.getDisplayName();
            if (spell != null) {
                ProjectileProfile p = spell.getProfile();
                float dmg = BASE_SPELL_DAMAGE * p.getDamageMultiplier();
                float cd = Player.DEFAULT_CAST_COOLDOWN * p.getCooldownMultiplier();
                float spd = BASE_PROJECTILE_SPEED * p.getSpeedMultiplier();
                float rng = spd * p.getLifetime();
                String dmgText = String.format("DMG %.0f (%.1fx)", dmg, p.getDamageMultiplier());
                String cdText = String.format("CD %.2fs", cd);
                String srText = String.format("SPD %.0f | RNG %.0f", spd, rng);
                String trait = spell.getDescription().isEmpty() ? e.getTrait() : spell.getDescription();
                entries.add(new SpellEntry(
                        List.of(e), spell, false, true, keyHint, recipeLabel, spell.getName(),
                        dmgText, cdText, srText, trait, e.getColor(), spell.getSecondaryColor()
                ));
            } else {
                entries.add(new SpellEntry(
                        List.of(e), null, false, false, keyHint, recipeLabel, e.getDisplayName() + " Spell",
                        "DMG --", "CD --", "SPD -- | RNG --", e.getTrait(), e.getColor(), e.getColor()
                ));
            }
        }

        // 2. All 10 dual-element combinations (5 choose 2)
        for (int i = 0; i < elements.length; i++) {
            for (int j = i + 1; j < elements.length; j++) {
                Element e1 = elements[i];
                Element e2 = elements[j];
                String keyHint = "[" + (e1.getIndex() + 1) + "+" + (e2.getIndex() + 1) + "]";
                String recipeLabel = e1.getDisplayName() + " + " + e2.getDisplayName();
                SpellDefinition combo = (registry != null) ? registry.combination(e1, e2) : null;

                if (combo != null) {
                    ProjectileProfile p = combo.getProfile();
                    float dmg = BASE_SPELL_DAMAGE * p.getDamageMultiplier();
                    float cd = Player.DEFAULT_CAST_COOLDOWN * p.getCooldownMultiplier();
                    float spd = BASE_PROJECTILE_SPEED * p.getSpeedMultiplier();
                    float rng = spd * p.getLifetime();
                    String dmgText = String.format("DMG %.1f (%.1fx)", dmg, p.getDamageMultiplier());
                    String cdText = String.format("CD %.2fs", cd);
                    String srText = String.format("SPD %.0f | RNG %.0f", spd, rng);
                    String trait = combo.getDescription().isEmpty()
                            ? (e1.getTrait() + " + " + e2.getTrait())
                            : combo.getDescription();
                    entries.add(new SpellEntry(
                            List.of(e1, e2), combo, true, true, keyHint, recipeLabel, combo.getName(),
                            dmgText, cdText, srText, trait, e1.getColor(), e2.getColor()
                    ));
                } else {
                    entries.add(new SpellEntry(
                            List.of(e1, e2), null, true, false, keyHint, recipeLabel,
                            "No Fusion (" + recipeLabel + ")",
                            "DMG Base", "CD Base", "SPD/RNG Base",
                            "Casts newest element's base spell", e1.getColor(), e2.getColor()
                    ));
                }
            }
        }

        return entries;
    }

    /**
     * Returns all 3 PowerUpType entries with display swatches and descriptions.
     */
    public List<PowerUpEntry> getPowerUpEntries() {
        List<PowerUpEntry> entries = new ArrayList<>(PowerUpType.values().length);
        ActiveTemporaryAbility helper = new ActiveTemporaryAbility();
        for (PowerUpType type : PowerUpType.values()) {
            helper.activate(type);
            entries.add(new PowerUpEntry(
                    type,
                    type.getDisplayName(),
                    type.getDescription(),
                    helper.getPrimaryColor(),
                    helper.getSecondaryColor()
            ));
        }
        return entries;
    }

    public int getDisplayedSpellCount(SpellRegistry registry) {
        return getSpellEntries(registry).size();
    }

    public int getDisplayedPowerUpCount() {
        return getPowerUpEntries().size();
    }

    public void render(ShapeRenderer shapeRenderer, SpriteBatch batch, FontManager fontManager,
                       SpellRegistry spellRegistry, float screenWidth, float screenHeight) {
        if (!visible) {
            return;
        }

        float viewW = screenWidth > 0 ? screenWidth : VIRTUAL_WIDTH;
        float viewH = screenHeight > 0 ? screenHeight : VIRTUAL_HEIGHT;
        Rectangle panel = getPanelBounds(true);
        Rectangle closeBtn = getCloseButtonBounds(true);

        List<SpellEntry> spells = getSpellEntries(spellRegistry);
        List<SpellEntry> baseEntries = new ArrayList<>(5);
        List<SpellEntry> comboEntries = new ArrayList<>(10);
        for (SpellEntry entry : spells) {
            if (entry.combination) {
                comboEntries.add(entry);
            } else {
                baseEntries.add(entry);
            }
        }
        List<PowerUpEntry> powerUps = getPowerUpEntries();

        final float leftColX = panel.x + 18.0f;
        final float leftColW = 438.0f;
        final float rightColX = leftColX + leftColW + 18.0f;
        final float rightColW = panel.width - 36.0f - leftColW - 18.0f;

        final float baseHeaderY = panel.y + 72.0f;
        final float baseRowStartY = baseHeaderY + 24.0f;
        final float baseRowH = 44.0f;

        final float powerHeaderY = baseRowStartY + baseEntries.size() * baseRowH + 14.0f;
        final float powerRowStartY = powerHeaderY + 24.0f;
        final float powerRowH = 74.0f;

        final float comboHeaderY = panel.y + 72.0f;
        final float comboRowStartY = comboHeaderY + 24.0f;
        final float comboRowH = 51.0f;

        // 1. Shapes pass
        if (shapeRenderer != null) {
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

            // Dimmed backdrop
            shapeRenderer.setColor(8f / 255f, 10f / 255f, 16f / 255f, 222f / 255f);
            shapeRenderer.rect(0, 0, viewW, viewH);

            // Panel surface & top accent strip
            shapeRenderer.setColor(18f / 255f, 22f / 255f, 34f / 255f, 0.98f);
            shapeRenderer.rect(panel.x, panel.y, panel.width, panel.height);
            shapeRenderer.setColor(90f / 255f, 185f / 255f, 255f / 255f, 1.0f);
            shapeRenderer.rect(panel.x, panel.y, panel.width, 5.0f);

            // Close button fill
            shapeRenderer.setColor(95f / 255f, 34f / 255f, 44f / 255f, 1.0f);
            shapeRenderer.rect(closeBtn.x, closeBtn.y, closeBtn.width, closeBtn.height);

            // Base spell rows & element color swatches
            for (int i = 0; i < baseEntries.size(); i++) {
                SpellEntry entry = baseEntries.get(i);
                float ry = baseRowStartY + i * baseRowH;
                shapeRenderer.setColor(26f / 255f, 32f / 255f, 48f / 255f, 0.95f);
                shapeRenderer.rect(leftColX, ry, leftColW, baseRowH - 4.0f);

                shapeRenderer.setColor(entry.primaryColor);
                shapeRenderer.rect(leftColX + 8.0f, ry + 11.0f, 16.0f, 16.0f);
            }

            // Power-up rows & dual-tone swatches
            for (int i = 0; i < powerUps.size(); i++) {
                PowerUpEntry pu = powerUps.get(i);
                float ry = powerRowStartY + i * powerRowH;
                shapeRenderer.setColor(24f / 255f, 28f / 255f, 42f / 255f, 0.95f);
                shapeRenderer.rect(leftColX, ry, leftColW, powerRowH - 6.0f);

                shapeRenderer.setColor(pu.primaryColor);
                shapeRenderer.rect(leftColX + 10.0f, ry + 14.0f, 24.0f, 24.0f);
                shapeRenderer.setColor(pu.secondaryColor);
                shapeRenderer.rect(leftColX + 16.0f, ry + 20.0f, 12.0f, 12.0f);
            }

            // Dual combination rows & dual element recipe swatches
            for (int i = 0; i < comboEntries.size(); i++) {
                SpellEntry entry = comboEntries.get(i);
                float ry = comboRowStartY + i * comboRowH;
                if (entry.registered) {
                    shapeRenderer.setColor(30f / 255f, 40f / 255f, 62f / 255f, 0.98f);
                } else {
                    shapeRenderer.setColor(22f / 255f, 26f / 255f, 38f / 255f, 0.85f);
                }
                shapeRenderer.rect(rightColX, ry, rightColW, comboRowH - 4.0f);

                // Two element swatches
                shapeRenderer.setColor(entry.primaryColor);
                shapeRenderer.rect(rightColX + 10.0f, ry + 14.0f, 14.0f, 14.0f);
                shapeRenderer.setColor(entry.secondaryColor);
                shapeRenderer.rect(rightColX + 28.0f, ry + 14.0f, 14.0f, 14.0f);
            }

            shapeRenderer.end();

            // Outline pass
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            shapeRenderer.setColor(80f / 255f, 130f / 255f, 200f / 255f, 1.0f);
            shapeRenderer.rect(panel.x, panel.y, panel.width, panel.height);

            shapeRenderer.setColor(220f / 255f, 110f / 255f, 125f / 255f, 1.0f);
            shapeRenderer.rect(closeBtn.x, closeBtn.y, closeBtn.width, closeBtn.height);

            for (int i = 0; i < baseEntries.size(); i++) {
                SpellEntry entry = baseEntries.get(i);
                float ry = baseRowStartY + i * baseRowH;
                shapeRenderer.setColor(entry.primaryColor);
                shapeRenderer.rect(leftColX, ry, leftColW, baseRowH - 4.0f);
            }

            for (int i = 0; i < powerUps.size(); i++) {
                PowerUpEntry pu = powerUps.get(i);
                float ry = powerRowStartY + i * powerRowH;
                shapeRenderer.setColor(pu.primaryColor);
                shapeRenderer.rect(leftColX, ry, leftColW, powerRowH - 6.0f);
            }

            for (int i = 0; i < comboEntries.size(); i++) {
                SpellEntry entry = comboEntries.get(i);
                float ry = comboRowStartY + i * comboRowH;
                shapeRenderer.setColor(entry.registered
                        ? new Color(255f / 255f, 210f / 255f, 80f / 255f, 0.9f)
                        : new Color(55f / 255f, 65f / 255f, 85f / 255f, 0.7f));
                shapeRenderer.rect(rightColX, ry, rightColW, comboRowH - 4.0f);
            }
            shapeRenderer.end();
        }

        // 2. Text pass
        if (batch != null && fontManager != null) {
            batch.begin();

            BitmapFont titleFont = fontManager.fontTitleMedium != null ? fontManager.fontTitleMedium : fontManager.fontRegular;
            BitmapFont boldFont = fontManager.fontBoldRegular != null ? fontManager.fontBoldRegular : fontManager.fontRegular;
            BitmapFont boldSmall = fontManager.fontBoldSmall != null ? fontManager.fontBoldSmall : fontManager.fontSmall;
            BitmapFont smallFont = fontManager.fontSmall != null ? fontManager.fontSmall : fontManager.fontRegular;

            if (titleFont != null) {
                titleFont.setColor(1.0f, 215f / 255f, 70f / 255f, 1.0f);
                titleFont.draw(batch, "SPELL & POWER-UP CODEX", panel.x + 20.0f, panel.y + 34.0f);
            }

            if (smallFont != null) {
                smallFont.setColor(175f / 255f, 195f / 255f, 225f / 255f, 1.0f);
                smallFont.draw(batch, "Press [1-5] to infuse elements. Last 2 elements determine your active spell.",
                        panel.x + 20.0f, panel.y + 56.0f);
            }

            if (boldSmall != null) {
                boldSmall.setColor(Color.WHITE);
                GlyphLayout glClose = fontManager.measure(boldSmall, "CLOSE [C]");
                boldSmall.draw(batch, "CLOSE [C]",
                        closeBtn.x + (closeBtn.width - glClose.width) * 0.5f,
                        closeBtn.y + (closeBtn.height - glClose.height) * 0.5f);

                boldSmall.setColor(120f / 255f, 210f / 255f, 1.0f, 1.0f);
                boldSmall.draw(batch, "SINGLE ELEMENT SPELLS (5)", leftColX, baseHeaderY + 14.0f);
                boldSmall.draw(batch, "POWER-UP PICKUPS (3)", leftColX, powerHeaderY + 14.0f);
                boldSmall.draw(batch, "DUAL-ELEMENT COMBINATIONS (10 PAIRS)", rightColX, comboHeaderY + 14.0f);
            }

            // Base spells text
            for (int i = 0; i < baseEntries.size(); i++) {
                SpellEntry entry = baseEntries.get(i);
                float ry = baseRowStartY + i * baseRowH;
                if (boldSmall != null) {
                    boldSmall.setColor(Color.WHITE);
                    boldSmall.draw(batch, entry.keyHint + " " + entry.spellName + " (" + entry.recipeLabel + ")",
                            leftColX + 32.0f, ry + 15.0f);
                }
                if (smallFont != null) {
                    smallFont.setColor(185f / 255f, 205f / 255f, 230f / 255f, 1.0f);
                    String baseLine = entry.damageText + " | " + entry.cooldownText + " | " + entry.effectTrait;
                    smallFont.draw(batch, fitText(fontManager, smallFont, baseLine, leftColW - 40.0f),
                            leftColX + 32.0f, ry + 32.0f);
                }
            }

            // Power-up text
            for (int i = 0; i < powerUps.size(); i++) {
                PowerUpEntry pu = powerUps.get(i);
                float ry = powerRowStartY + i * powerRowH;
                if (boldFont != null) {
                    boldFont.setColor(pu.secondaryColor);
                    boldFont.draw(batch, pu.name + "  (5.0s)", leftColX + 44.0f, ry + 24.0f);
                }
                if (smallFont != null) {
                    smallFont.setColor(210f / 255f, 220f / 255f, 235f / 255f, 1.0f);
                    smallFont.draw(batch, pu.description, leftColX + 44.0f, ry + 48.0f);
                }
            }

            // Dual combinations text
            for (int i = 0; i < comboEntries.size(); i++) {
                SpellEntry entry = comboEntries.get(i);
                float ry = comboRowStartY + i * comboRowH;
                if (boldSmall != null) {
                    boldSmall.setColor(entry.registered ? new Color(1.0f, 225f / 255f, 110f / 255f, 1.0f)
                            : new Color(150f / 255f, 165f / 255f, 185f / 255f, 1.0f));
                    boldSmall.draw(batch, entry.keyHint + " " + entry.spellName,
                            rightColX + 52.0f, ry + 17.0f);
                }
                if (smallFont != null) {
                    smallFont.setColor(entry.registered ? new Color(200f / 255f, 220f / 255f, 240f / 255f, 1.0f)
                            : new Color(130f / 255f, 140f / 255f, 160f / 255f, 1.0f));
                    String line2 = entry.registered
                            ? (entry.damageText + " | " + entry.cooldownText + " | " + entry.speedRangeText + " | " + entry.effectTrait)
                            : (entry.recipeLabel + " -> " + entry.effectTrait);
                    smallFont.draw(batch, fitText(fontManager, smallFont, line2, rightColW - 62.0f),
                            rightColX + 52.0f, ry + 36.0f);
                }
            }

            batch.end();
        }
    }

    private static String fitText(FontManager fm, BitmapFont font, String text, float maxWidth) {
        if (text == null || fm == null || font == null || fm.measure(font, text).width <= maxWidth) {
            return text != null ? text : "";
        }
        String ellipsis = "...";
        int end = text.length();
        while (end > 3 && fm.measure(font, text.substring(0, end) + ellipsis).width > maxWidth) {
            end--;
        }
        return text.substring(0, Math.max(0, end)) + ellipsis;
    }
}
