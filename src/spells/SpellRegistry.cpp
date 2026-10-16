#include "spells/SpellRegistry.hpp"

#include <stdexcept>

namespace ss {

void SpellRegistry::registerBase(Element element, SpellPtr spell)
{
    if (!spell) {
        throw std::invalid_argument("SpellRegistry::registerBase: null spell");
    }
    m_base[ElementSet{element}.mask()] = std::move(spell);
}

void SpellRegistry::registerCombination(ElementSet elements, SpellPtr spell)
{
    if (!spell) {
        throw std::invalid_argument("SpellRegistry::registerCombination: null spell");
    }
    if (elements.count() < 2) {
        throw std::invalid_argument("SpellRegistry::registerCombination: needs at least two elements");
    }
    const std::uint32_t key = elements.mask();
    if (m_combinations.count(key) == 0) {
        m_combinationOrder.push_back(spell);
    }
    m_combinations[key] = std::move(spell);
}

void SpellRegistry::registerCombination(Element first, Element second, SpellPtr spell)
{
    if (first == second) {
        throw std::invalid_argument("SpellRegistry::registerCombination: needs two different elements");
    }
    registerCombination(ElementSet{first, second}, std::move(spell));
}

SpellPtr SpellRegistry::baseSpell(Element element) const
{
    const auto it = m_base.find(ElementSet{element}.mask());
    return it != m_base.end() ? it->second : nullptr;
}

SpellPtr SpellRegistry::combination(const ElementSet& elements) const
{
    if (elements.count() < 2) {
        return nullptr;
    }
    const auto it = m_combinations.find(elements.mask());
    return it != m_combinations.end() ? it->second : nullptr;
}

SpellPtr SpellRegistry::combination(Element first, Element second) const
{
    if (first == second) {
        return nullptr;
    }
    return combination(ElementSet{first, second});
}

SpellPtr SpellRegistry::resolve(const std::vector<Element>& mix) const
{
    if (mix.empty()) {
        return nullptr;
    }
    if (mix.size() >= 2) {
        if (SpellPtr combo = combination(mix[mix.size() - 2], mix.back())) {
            return combo;
        }
    }
    return baseSpell(mix.back());
}

std::vector<SpellPtr> SpellRegistry::combinations() const
{
    return m_combinationOrder;
}

} // namespace ss
