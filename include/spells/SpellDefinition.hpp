#pragma once

#include "spells/Element.hpp"
#include "spells/HitEffect.hpp"

#include <SFML/Graphics/Color.hpp>

#include <memory>
#include <string>

namespace ss {

// How the projectiles of a spell behave, relative to the caster's stats.
struct ProjectileProfile {
    float damageMultiplier = 1.f;
    float speedMultiplier = 1.f;
    float radius = 7.f;
    float lifetime = 1.6f;
    float cooldownMultiplier = 1.f;
    float spreadDegrees = 12.f; // angle between multiple projectiles
    int pierce = 0;             // extra enemies a projectile can pass through
    int extraProjectiles = 0;
};

// Immutable description of a spell: data + a list of composed HitEffects.
// Shared (via shared_ptr<const>) by every projectile currently in flight,
// so changing the active spell never invalidates projectiles already fired.
class SpellDefinition {
public:
    SpellDefinition(std::string name, std::string description, ElementSet elements, sf::Color primaryColor,
                    sf::Color secondaryColor, ProjectileProfile profile);

    SpellDefinition(const SpellDefinition&) = delete;
    SpellDefinition& operator=(const SpellDefinition&) = delete;

    SpellDefinition& addEffect(std::unique_ptr<HitEffect> effect);
    void applyOnHit(const HitContext& context) const;

    const std::string& name() const { return m_name; }
    const std::string& description() const { return m_description; }
    const ElementSet& elements() const { return m_elements; }
    sf::Color primaryColor() const { return m_primaryColor; }
    sf::Color secondaryColor() const { return m_secondaryColor; }
    const ProjectileProfile& profile() const { return m_profile; }
    bool isCombination() const { return m_elements.count() > 1; }
    std::size_t effectCount() const { return m_effects.size(); }

private:
    std::string m_name;
    std::string m_description;
    ElementSet m_elements;
    sf::Color m_primaryColor;
    sf::Color m_secondaryColor;
    ProjectileProfile m_profile;
    HitEffectList m_effects;
};

} // namespace ss
