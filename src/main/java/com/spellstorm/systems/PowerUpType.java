package com.spellstorm.systems;

/**
 * Temporary combat ability power-up types.
 */
public enum PowerUpType {
    ARCANE_OVERDRIVE(0, "ARCANE OVERDRIVE", "+80% Spell Firing Rate"),
    PIERCING_SURGE(1, "PIERCING SURGE", "+2 Projectile Piercing Hits"),
    ELEMENTAL_BURST(2, "ELEMENTAL BURST", "Empowered Twin Elemental Blast");

    private final int id;
    private final String displayName;
    private final String description;

    PowerUpType(int id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public int getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
