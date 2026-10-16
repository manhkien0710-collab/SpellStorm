#pragma once

#include "spells/Element.hpp"
#include "spells/SpellDefinition.hpp"

#include <cstdint>
#include <memory>
#include <unordered_map>
#include <vector>

namespace ss {

using SpellPtr = std::shared_ptr<const SpellDefinition>;

// Catalogue of all spells: one base spell per element plus combination
// recipes keyed by an (order-independent) ElementSet.
// Adding a recipe = one registerCombination() call; no other class changes.
class SpellRegistry {
public:
    void registerBase(Element element, SpellPtr spell);
    void registerCombination(ElementSet elements, SpellPtr spell);
    void registerCombination(Element first, Element second, SpellPtr spell);

    SpellPtr baseSpell(Element element) const;                  // nullptr if missing
    SpellPtr combination(const ElementSet& elements) const;     // nullptr if no recipe
    SpellPtr combination(Element first, Element second) const;  // nullptr if no recipe

    // Spell produced by an ordered element mix (oldest -> newest). Two
    // elements with a recipe give the combination, otherwise the base
    // spell of the newest element is used.
    SpellPtr resolve(const std::vector<Element>& mix) const;

    std::vector<SpellPtr> combinations() const; // stable order (registration)

private:
    std::unordered_map<std::uint32_t, SpellPtr> m_base;
    std::unordered_map<std::uint32_t, SpellPtr> m_combinations;
    std::vector<SpellPtr> m_combinationOrder;
};

} // namespace ss
