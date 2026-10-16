#pragma once

#include "entities/Entity.hpp"
#include "spells/SpellDefinition.hpp"

#include <SFML/Graphics/Color.hpp>

#include <memory>
#include <vector>

namespace ss {

enum class ProjectileFaction {
    Player,
    Enemy
};

// Dynamic projectile entity traveling through the arena.
// Supports delta-time physics, pierce limits, multi-hit prevention,
// faction filtering, and elemental on-hit effect delegation.
class Projectile : public Entity {
public:
    Projectile(sf::Vector2f position, sf::Vector2f direction, float speed,
               float radius, float damage, float lifetime, int pierce,
               ProjectileFaction faction, sf::Color primaryColor, sf::Color secondaryColor,
               std::shared_ptr<const SpellDefinition> spell = nullptr);

    // Balancing constants
    static constexpr int kMaxAllowedPierce = 3;
    static constexpr float kPierceDamageFalloff = 0.80f;
    static constexpr float kMinDamageMultiplier = 0.25f;

    ProjectileFaction faction() const { return m_faction; }
    sf::Vector2f direction() const { return m_direction; }
    sf::Vector2f velocity() const { return m_velocity; }
    float speed() const { return m_speed; }
    float damage() const { return m_damage; }
    float initialDamage() const { return m_initialDamage; }
    float lifetime() const { return m_lifetime; }
    int pierceRemaining() const { return m_pierceRemaining; }
    int hitCount() const { return static_cast<int>(m_hitEntities.size()); }
    sf::Color primaryColor() const { return m_primaryColor; }
    sf::Color secondaryColor() const { return m_secondaryColor; }
    std::shared_ptr<const SpellDefinition> spell() const { return m_spell; }

    // Checks whether the entity has already been struck by this projectile
    bool canHit(EntityId entityId) const;
    void recordHit(EntityId entityId);
    void decrementPierce();

    // Simulation update: moves projectile and decays lifetime
    void update(float dt) override;

    // Procedural rendering: glowing aura, bright core, and particle trail
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

private:
    sf::Vector2f m_direction;
    sf::Vector2f m_velocity;
    float m_speed;
    float m_damage;
    float m_initialDamage;
    float m_lifetime;
    int m_pierceRemaining;
    ProjectileFaction m_faction;
    sf::Color m_primaryColor;
    sf::Color m_secondaryColor;
    std::shared_ptr<const SpellDefinition> m_spell;
    std::vector<EntityId> m_hitEntities;
    float m_animTimer = 0.f;
};

} // namespace ss
