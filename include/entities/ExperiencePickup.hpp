#pragma once

#include "entities/Entity.hpp"
#include <SFML/Graphics/Color.hpp>

namespace ss {

// Collectible experience gem dropped by defeated enemies.
// Floats with a gentle bobbing animation and can be magnetically drawn
// towards the player when in proximity.
class ExperiencePickup : public Entity {
public:
    ExperiencePickup(sf::Vector2f position, int expValue);
    ~ExperiencePickup() override = default;

    int expValue() const { return m_expValue; }
    bool isCollected() const { return m_collected; }

    // Marks the pickup as collected and marks entity for removal
    void collect();

    // Attracts pickup towards target position
    void magnetTowards(sf::Vector2f targetPos, float speed, float dt);

    void update(float dt) override;
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

private:
    int m_expValue;
    bool m_collected = false;
    float m_animTimer = 0.f;
    float m_baseY = 0.f;
    sf::Color m_gemColor;
    sf::Color m_coreColor;
};

} // namespace ss
