#include "TestHelper.hpp"
#include "spells/Element.hpp"
#include "spells/SpellRegistry.hpp"

void runSpellsTests()
{
    // Test ElementSet bitmask logic
    ss::ElementSet set1{ss::Element::Fire, ss::Element::Wind};
    EXPECT_TRUE(set1.contains(ss::Element::Fire));
    EXPECT_TRUE(set1.contains(ss::Element::Wind));
    EXPECT_FALSE(set1.contains(ss::Element::Ice));
    EXPECT_EQ(set1.count(), 2);

    ss::ElementSet set2{ss::Element::Wind, ss::Element::Fire};
    EXPECT_TRUE(set1 == set2);

    // Test SpellRegistry
    ss::SpellRegistry registry;
    auto fireSpell = std::make_shared<ss::SpellDefinition>(
        "Fireball", "Shoots a fireball", ss::ElementSet{ss::Element::Fire},
        sf::Color::Red, sf::Color::Yellow, ss::ProjectileProfile{}
    );
    registry.registerBase(ss::Element::Fire, fireSpell);

    EXPECT_EQ(registry.baseSpell(ss::Element::Fire), fireSpell);
    EXPECT_EQ(registry.resolve({ss::Element::Fire}), fireSpell);

    // Test combination registration & resolution
    auto fireWindSpell = std::make_shared<ss::SpellDefinition>(
        "Firestorm", "Flaming vortex", ss::ElementSet{ss::Element::Fire, ss::Element::Wind},
        sf::Color(255, 100, 0), sf::Color::Yellow, ss::ProjectileProfile{}
    );
    registry.registerCombination(ss::ElementSet{ss::Element::Fire, ss::Element::Wind}, fireWindSpell);

    EXPECT_EQ(registry.combination(ss::Element::Fire, ss::Element::Wind), fireWindSpell);
    EXPECT_EQ(registry.combination(ss::Element::Wind, ss::Element::Fire), fireWindSpell);
    EXPECT_EQ(registry.combination(ss::ElementSet{ss::Element::Fire, ss::Element::Wind}), fireWindSpell);
    EXPECT_EQ(registry.resolve({ss::Element::Fire, ss::Element::Wind}), fireWindSpell);
    EXPECT_EQ(registry.resolve({ss::Element::Wind, ss::Element::Fire}), fireWindSpell);
}
