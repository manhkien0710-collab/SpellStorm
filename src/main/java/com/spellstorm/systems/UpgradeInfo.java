package com.spellstorm.systems;

import com.badlogic.gdx.graphics.Color;

/**
 * Data model for an upgrade perk choice.
 */
public class UpgradeInfo {

    public final UpgradeType type;
    public final String title;
    public final String description;
    public int currentRank;
    public final int maxRank;
    public final Color accentColor;

    public UpgradeInfo(UpgradeType type, String title, String description,
                       int currentRank, int maxRank, Color accentColor) {
        this.type = type;
        this.title = title;
        this.description = description;
        this.currentRank = currentRank;
        this.maxRank = maxRank;
        this.accentColor = (accentColor != null) ? accentColor.cpy() : Color.WHITE.cpy();
    }

    public UpgradeInfo cpy() {
        return new UpgradeInfo(type, title, description, currentRank, maxRank, accentColor);
    }

    public UpgradeType getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public int getCurrentRank() {
        return currentRank;
    }

    public int getMaxRank() {
        return maxRank;
    }

    public Color getAccentColor() {
        return accentColor.cpy();
    }

    public boolean isMaxRank() {
        return currentRank >= maxRank;
    }
}
