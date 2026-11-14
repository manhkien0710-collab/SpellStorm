package com.spellstorm.combat;

import com.badlogic.gdx.graphics.Color;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Catalogue and recipe resolver for all elemental spells.
 * Maps base spells by element and combination spells by order-independent ElementSet.
 */
public class SpellRegistry {

    private final Map<Integer, SpellDefinition> baseSpells = new HashMap<>();
    private final Map<Integer, SpellDefinition> combinations = new HashMap<>();
    private final List<SpellDefinition> combinationOrder = new ArrayList<>();

    public void registerBase(Element element, SpellDefinition spell) {
        if (element != null && spell != null) {
            baseSpells.put(element.getIndex(), spell);
        }
    }

    public void registerCombination(ElementSet elements, SpellDefinition spell) {
        if (elements != null && spell != null) {
            combinations.put(elements.getMask(), spell);
            if (!combinationOrder.contains(spell)) {
                combinationOrder.add(spell);
            }
        }
    }

    public void registerCombination(Element first, Element second, SpellDefinition spell) {
        if (first != null && second != null && spell != null) {
            registerCombination(new ElementSet(first, second), spell);
        }
    }

    public SpellDefinition baseSpell(Element element) {
        if (element == null) {
            return null;
        }
        return baseSpells.get(element.getIndex());
    }

    public SpellDefinition getBaseSpell(Element element) {
        return baseSpell(element);
    }

    public SpellDefinition combination(ElementSet elements) {
        if (elements == null) {
            return null;
        }
        return combinations.get(elements.getMask());
    }

    public SpellDefinition getCombination(ElementSet elements) {
        return combination(elements);
    }

    public SpellDefinition combination(Element first, Element second) {
        if (first == null || second == null) {
            return null;
        }
        return combination(new ElementSet(first, second));
    }

    public SpellDefinition getCombination(Element first, Element second) {
        return combination(first, second);
    }

    /**
     * Resolves a spell from an ordered sequence of element selections (oldest -> newest).
     * If the last two elements form a registered combination recipe, returns that combination.
     * Otherwise, returns the base spell of the most recently chosen element.
     */
    public SpellDefinition resolve(List<Element> mix) {
        if (mix == null || mix.isEmpty()) {
            return null;
        }
        if (mix.size() >= 2) {
            Element prev = mix.get(mix.size() - 2);
            Element curr = mix.get(mix.size() - 1);
            SpellDefinition combo = combination(prev, curr);
            if (combo != null) {
                return combo;
            }
        }
        return baseSpell(mix.get(mix.size() - 1));
    }

    public List<SpellDefinition> combinations() {
        return Collections.unmodifiableList(combinationOrder);
    }

    public List<SpellDefinition> getCombinations() {
        return Collections.unmodifiableList(combinationOrder);
    }

    /**
     * Factory creating the default SpellStorm spell registry populated with the 5 fundamental
     * elemental base spells and 3 elemental combination recipes.
     */
    public static SpellRegistry createDefaultRegistry() {
        SpellRegistry registry = new SpellRegistry();

        // 1. Base Spells
        SpellDefinition firebolt = new SpellDefinition(
                "Firebolt", "Ignites foes with continuous burning flame",
                new ElementSet(Element.FIRE),
                new Color(1.0f, 90f / 255f, 20f / 255f, 1.0f),
                new Color(1.0f, 200f / 255f, 50f / 255f, 1.0f),
                new ProjectileProfile(1.0f, 1.2f, 9.0f, 2.0f, 1.0f, 0.0f, 0, 0)
        );
        firebolt.addEffect(new BurnEffect(0.5f, 3.0f));
        registry.registerBase(Element.FIRE, firebolt);

        SpellDefinition frostShard = new SpellDefinition(
                "Frost Shard", "Chills enemies, slowing movement by 50%",
                new ElementSet(Element.ICE),
                new Color(100f / 255f, 210f / 255f, 1.0f, 1.0f),
                new Color(220f / 255f, 245f / 255f, 1.0f, 1.0f),
                new ProjectileProfile(0.8f, 1.4f, 8.0f, 2.0f, 0.8f, 0.0f, 1, 0)
        );
        frostShard.addEffect(new SlowEffect(0.5f, 2.5f));
        registry.registerBase(Element.ICE, frostShard);

        SpellDefinition sparkArc = new SpellDefinition(
                "Spark Arc", "Arches of high-voltage lightning",
                new ElementSet(Element.LIGHTNING),
                new Color(1.0f, 240f / 255f, 70f / 255f, 1.0f),
                new Color(1.0f, 1.0f, 200f / 255f, 1.0f),
                new ProjectileProfile(1.1f, 1.8f, 7.0f, 1.5f, 0.9f, 0.0f, 0, 0)
        );
        ChainEffect chain = new ChainEffect(3, 160.0f);
        chain.then(new DamageEffect(0.7f));
        sparkArc.addEffect(chain);
        registry.registerBase(Element.LIGHTNING, sparkArc);

        SpellDefinition galeBurst = new SpellDefinition(
                "Gale Burst", "Knocks enemies back with violent gusts",
                new ElementSet(Element.WIND),
                new Color(120f / 255f, 1.0f, 180f / 255f, 1.0f),
                new Color(200f / 255f, 1.0f, 230f / 255f, 1.0f),
                new ProjectileProfile(0.7f, 1.5f, 10.0f, 1.8f, 0.7f, 0.0f, 2, 0)
        );
        galeBurst.addEffect(new KnockbackEffect(280.0f));
        registry.registerBase(Element.WIND, galeBurst);

        SpellDefinition boulderSlam = new SpellDefinition(
                "Boulder Slam", "Heavy physical shockwave crushing armor",
                new ElementSet(Element.EARTH),
                new Color(190f / 255f, 130f / 255f, 80f / 255f, 1.0f),
                new Color(130f / 255f, 85f / 255f, 45f / 255f, 1.0f),
                new ProjectileProfile(1.6f, 0.8f, 13.0f, 2.2f, 1.4f, 0.0f, 0, 0)
        );
        boulderSlam.addEffect(new KnockbackEffect(180.0f));
        registry.registerBase(Element.EARTH, boulderSlam);

        // 2. Combination Spells
        SpellDefinition firestorm = new SpellDefinition(
                "Firestorm (Fire + Wind)", "Catastrophic vortex burning a wide area",
                new ElementSet(Element.FIRE, Element.WIND),
                new Color(1.0f, 80f / 255f, 20f / 255f, 1.0f),
                new Color(120f / 255f, 1.0f, 180f / 255f, 1.0f),
                new ProjectileProfile(1.5f, 1.3f, 12.0f, 2.0f, 1.2f, 0.0f, 1, 0)
        );
        AreaEffect areaExplosion = new AreaEffect(90.0f, true);
        areaExplosion.then(new DamageEffect(1.2f));
        areaExplosion.then(new BurnEffect(0.4f, 2.5f));
        firestorm.addEffect(areaExplosion);
        registry.registerCombination(Element.FIRE, Element.WIND, firestorm);

        SpellDefinition frostTempest = new SpellDefinition(
                "Frost Tempest (Ice + Lightning)", "Freezing blast conducting electric shocks",
                new ElementSet(Element.ICE, Element.LIGHTNING),
                new Color(130f / 255f, 220f / 255f, 1.0f, 1.0f),
                new Color(1.0f, 240f / 255f, 80f / 255f, 1.0f),
                new ProjectileProfile(1.3f, 1.4f, 10.0f, 2.0f, 1.1f, 0.0f, 1, 0)
        );
        AreaEffect frostArea = new AreaEffect(75.0f, true);
        frostArea.then(new SlowEffect(0.6f, 3.0f));
        frostTempest.addEffect(frostArea);
        registry.registerCombination(Element.ICE, Element.LIGHTNING, frostTempest);

        SpellDefinition magmaCore = new SpellDefinition(
                "Magma Core (Earth + Fire)", "Molten volcanic rock dealing heavy lingering burn",
                new ElementSet(Element.EARTH, Element.FIRE),
                new Color(220f / 255f, 70f / 255f, 30f / 255f, 1.0f),
                new Color(1.0f, 160f / 255f, 40f / 255f, 1.0f),
                new ProjectileProfile(1.8f, 0.9f, 14.0f, 2.4f, 1.3f, 0.0f, 0, 0)
        );
        magmaCore.addEffect(new BurnEffect(0.7f, 4.0f));
        magmaCore.addEffect(new KnockbackEffect(220.0f));
        registry.registerCombination(Element.EARTH, Element.FIRE, magmaCore);

        return registry;
    }
}
