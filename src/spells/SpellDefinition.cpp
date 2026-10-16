#include "spells/SpellDefinition.hpp"

#include "entities/Enemy.hpp"

namespace ss {

SpellDefinition::SpellDefinition(std::string name, std::string description, ElementSet elements,
                                 sf::Color primaryColor, sf::Color secondaryColor, ProjectileProfile profile)
    : m_name(std::move(name))
    , m_description(std::move(description))
    , m_elements(elements)
    , m_primaryColor(primaryColor)
    , m_secondaryColor(secondaryColor)
    , m_profile(profile)
{
}

SpellDefinition& SpellDefinition::addEffect(std::unique_ptr<HitEffect> effect)
{
    if (effect) {
        m_effects.push_back(std::move(effect));
    }
    return *this;
}

void SpellDefinition::applyOnHit(const HitContext& context) const
{
    for (const auto& effect : m_effects) {
        // All effects run even if the primary target just died: an explosion
        // or a chain must still trigger. Single-target effects are harmless on
        // a dead target (Character ignores damage/statuses once dead).
        effect->apply(context);
    }
}

} // namespace ss
