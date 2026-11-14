package com.spellstorm.combat;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable definition of a spell: metadata, element recipe key, projectile profile,
 * and composed on-hit HitEffects.
 */
public class SpellDefinition {

    private final String name;
    private final String description;
    private final ElementSet elements;
    private final Color primaryColor;
    private final Color secondaryColor;
    private final ProjectileProfile profile;
    private final List<HitEffect> effects = new ArrayList<>();

    public SpellDefinition(String name, String description, ElementSet elements,
                           Color primaryColor, Color secondaryColor, ProjectileProfile profile) {
        this.name = name != null ? name : "Spell";
        this.description = description != null ? description : "";
        this.elements = elements != null ? elements : new ElementSet();
        this.primaryColor = primaryColor != null ? primaryColor.cpy() : Color.WHITE.cpy();
        this.secondaryColor = secondaryColor != null ? secondaryColor.cpy() : Color.WHITE.cpy();
        this.profile = profile != null ? profile : new ProjectileProfile();
    }

    public SpellDefinition addEffect(HitEffect effect) {
        if (effect != null) {
            effects.add(effect);
        }
        return this;
    }

    public void applyOnHit(HitContext context) {
        if (context == null || context.target == null) {
            return;
        }
        for (HitEffect effect : effects) {
            if (!context.target.isAlive()) {
                return; // Target died: stop applying further effects
            }
            effect.apply(context);
        }
    }

    public String name() { return name; }
    public String getName() { return name; }

    public String description() { return description; }
    public String getDescription() { return description; }

    public ElementSet elements() { return elements; }
    public ElementSet getElements() { return elements; }

    public Color primaryColor() { return primaryColor.cpy(); }
    public Color getPrimaryColor() { return primaryColor.cpy(); }

    public Color secondaryColor() { return secondaryColor.cpy(); }
    public Color getSecondaryColor() { return secondaryColor.cpy(); }

    public ProjectileProfile profile() { return profile; }
    public ProjectileProfile getProfile() { return profile; }

    public boolean isCombination() {
        return elements.count() > 1;
    }

    public int effectCount() {
        return effects.size();
    }

    public List<HitEffect> getEffects() {
        return Collections.unmodifiableList(effects);
    }
}
