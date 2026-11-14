package com.spellstorm;

import com.spellstorm.combat.Element;
import com.spellstorm.combat.ElementSet;
import com.spellstorm.combat.SpellDefinition;
import com.spellstorm.combat.SpellRegistry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite covering ElementSet bitmask logic, SpellRegistry catalog,
 * all 5 fundamental elemental spells, 3 combination recipes, and recipe resolution order.
 */
public class SpellRegistryTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. ElementSet Bitmask & Order-Independent Equality
    // =========================================================================

    @Test
    @DisplayName("ElementSet bitmask logic and order-independent set equality (C++ test_spells.cpp parity)")
    public void testElementSetBitmaskAndEquality() {
        ElementSet set1 = new ElementSet(Element.FIRE, Element.WIND);
        assertTrue(set1.contains(Element.FIRE));
        assertTrue(set1.contains(Element.WIND));
        assertFalse(set1.contains(Element.ICE));
        assertFalse(set1.contains(Element.EARTH));
        assertEquals(2, set1.count());

        ElementSet set2 = new ElementSet(Element.WIND, Element.FIRE);
        assertEquals(set1, set2, "ElementSets created with swapped orders must be equal");
        assertEquals(set1.hashCode(), set2.hashCode());
        assertEquals(set1.getMask(), set2.getMask());
    }

    // =========================================================================
    // 2. All 5 Base Spells in Default Registry
    // =========================================================================

    @Test
    @DisplayName("Default registry contains all 5 fundamental base spells with correct profiles")
    public void testDefaultRegistryBaseSpells() {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();

        // 1. Firebolt (Fire)
        SpellDefinition firebolt = registry.baseSpell(Element.FIRE);
        assertNotNull(firebolt);
        assertEquals("Firebolt", firebolt.getName());
        assertEquals(1, firebolt.elements().count());
        assertTrue(firebolt.elements().contains(Element.FIRE));
        assertEquals(1.0f, firebolt.profile().damageMultiplier, 1e-4f);
        assertEquals(1.2f, firebolt.profile().speedMultiplier, 1e-4f);
        assertEquals(9.0f, firebolt.profile().radius, 1e-4f);
        assertEquals(0, firebolt.profile().pierce);
        assertTrue(firebolt.effectCount() >= 1);

        // 2. Frost Shard (Ice)
        SpellDefinition frostShard = registry.baseSpell(Element.ICE);
        assertNotNull(frostShard);
        assertEquals("Frost Shard", frostShard.getName());
        assertTrue(frostShard.elements().contains(Element.ICE));
        assertEquals(0.8f, frostShard.profile().damageMultiplier, 1e-4f);
        assertEquals(1.4f, frostShard.profile().speedMultiplier, 1e-4f);
        assertEquals(8.0f, frostShard.profile().radius, 1e-4f);
        assertEquals(1, frostShard.profile().pierce);

        // 3. Spark Arc (Lightning)
        SpellDefinition sparkArc = registry.baseSpell(Element.LIGHTNING);
        assertNotNull(sparkArc);
        assertEquals("Spark Arc", sparkArc.getName());
        assertTrue(sparkArc.elements().contains(Element.LIGHTNING));
        assertEquals(1.1f, sparkArc.profile().damageMultiplier, 1e-4f);
        assertEquals(1.8f, sparkArc.profile().speedMultiplier, 1e-4f);

        // 4. Gale Burst (Wind)
        SpellDefinition galeBurst = registry.baseSpell(Element.WIND);
        assertNotNull(galeBurst);
        assertEquals("Gale Burst", galeBurst.getName());
        assertTrue(galeBurst.elements().contains(Element.WIND));
        assertEquals(0.7f, galeBurst.profile().damageMultiplier, 1e-4f);
        assertEquals(1.5f, galeBurst.profile().speedMultiplier, 1e-4f);
        assertEquals(2, galeBurst.profile().pierce);

        // 5. Boulder Slam (Earth)
        SpellDefinition boulderSlam = registry.baseSpell(Element.EARTH);
        assertNotNull(boulderSlam);
        assertEquals("Boulder Slam", boulderSlam.getName());
        assertTrue(boulderSlam.elements().contains(Element.EARTH));
        assertEquals(1.6f, boulderSlam.profile().damageMultiplier, 1e-4f);
        assertEquals(0.8f, boulderSlam.profile().speedMultiplier, 1e-4f);
        assertEquals(13.0f, boulderSlam.profile().radius, 1e-4f);
    }

    // =========================================================================
    // 3. All 3 Combo Recipes in Default Registry
    // =========================================================================

    @Test
    @DisplayName("Default registry contains all 3 combination recipes with order-independent resolution")
    public void testDefaultRegistryCombinationRecipes() {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        assertEquals(3, registry.combinations().size());

        // 1. Firestorm (Fire + Wind)
        SpellDefinition firestorm1 = registry.combination(Element.FIRE, Element.WIND);
        SpellDefinition firestorm2 = registry.combination(Element.WIND, Element.FIRE);
        assertNotNull(firestorm1);
        assertSame(firestorm1, firestorm2);
        assertTrue(firestorm1.getName().contains("Firestorm"));
        assertTrue(firestorm1.isCombination());
        assertEquals(1.5f, firestorm1.profile().damageMultiplier, 1e-4f);
        assertEquals(1.3f, firestorm1.profile().speedMultiplier, 1e-4f);
        assertEquals(1, firestorm1.profile().pierce);

        // 2. Frost Tempest (Ice + Lightning)
        SpellDefinition blizzard1 = registry.combination(Element.ICE, Element.LIGHTNING);
        SpellDefinition blizzard2 = registry.combination(Element.LIGHTNING, Element.ICE);
        assertNotNull(blizzard1);
        assertSame(blizzard1, blizzard2);
        assertTrue(blizzard1.getName().contains("Frost Tempest"));
        assertTrue(blizzard1.isCombination());
        assertEquals(1.3f, blizzard1.profile().damageMultiplier, 1e-4f);

        // 3. Magma Core (Earth + Fire)
        SpellDefinition lava1 = registry.combination(Element.EARTH, Element.FIRE);
        SpellDefinition lava2 = registry.combination(Element.FIRE, Element.EARTH);
        assertNotNull(lava1);
        assertSame(lava1, lava2);
        assertTrue(lava1.getName().contains("Magma Core"));
        assertTrue(lava1.isCombination());
        assertEquals(1.8f, lava1.profile().damageMultiplier, 1e-4f);
        assertEquals(0.9f, lava1.profile().speedMultiplier, 1e-4f);
        assertEquals(14.0f, lava1.profile().radius, 1e-4f);
    }

    // =========================================================================
    // 4. Recipe Resolution via Ordered Element Selection Mix
    // =========================================================================

    @Test
    @DisplayName("Registry resolve() resolves combos for matching pairs and falls back to newest element")
    public void testSpellResolutionFromMix() {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();

        // Single element selection -> base spell
        SpellDefinition s1 = registry.resolve(List.of(Element.FIRE));
        assertEquals("Firebolt", s1.getName());

        // Valid combo pair -> combination spell
        SpellDefinition combo1 = registry.resolve(List.of(Element.FIRE, Element.WIND));
        assertTrue(combo1.getName().contains("Firestorm"));

        // Reverse pair -> same combination spell
        SpellDefinition combo2 = registry.resolve(List.of(Element.WIND, Element.FIRE));
        assertSame(combo1, combo2);

        // Pair with no combination recipe -> base spell of newest element (WIND -> Gale Burst)
        SpellDefinition fallback = registry.resolve(List.of(Element.ICE, Element.WIND));
        assertEquals("Gale Burst", fallback.getName());

        // Stream of 3 selections: takes the last 2 elements (EARTH, FIRE -> Magma Core)
        SpellDefinition stream = registry.resolve(List.of(Element.ICE, Element.EARTH, Element.FIRE));
        assertTrue(stream.getName().contains("Magma Core"));
    }
}
