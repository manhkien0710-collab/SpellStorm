package com.spellstorm.combat;

import com.badlogic.gdx.graphics.Color;

/**
 * Fundamental elemental affinities for spells and projectile combat.
 */
public enum Element {
    FIRE(0, "Fire", "Burns targets over time", new Color(1.0f, 120f / 255f, 40f / 255f, 1.0f)),
    ICE(1, "Ice", "Slows targets", new Color(120f / 255f, 210f / 255f, 1.0f, 1.0f)),
    LIGHTNING(2, "Lightning", "Chains to nearby foes", new Color(1.0f, 236f / 255f, 90f / 255f, 1.0f)),
    WIND(3, "Wind", "Fast, piercing, knockback", new Color(150f / 255f, 1.0f, 190f / 255f, 1.0f)),
    EARTH(4, "Earth", "Heavy hits, big knockback", new Color(196f / 255f, 140f / 255f, 84f / 255f, 1.0f));

    private final int index;
    private final String displayName;
    private final String trait;
    private final Color color;

    Element(int index, String displayName, String trait, Color color) {
        this.index = index;
        this.displayName = displayName;
        this.trait = trait;
        this.color = color;
    }

    public int getIndex() {
        return index;
    }

    public int indexOf() {
        return index;
    }

    public static int indexOf(Element element) {
        return element != null ? element.index : -1;
    }

    public static Element fromIndex(int index) {
        for (Element e : values()) {
            if (e.index == index) {
                return e;
            }
        }
        throw new IndexOutOfBoundsException("Invalid element index: " + index);
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTrait() {
        return trait;
    }

    public Color getColor() {
        return color.cpy();
    }
}
