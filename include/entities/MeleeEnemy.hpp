#pragma once

#include "entities/Enemy.hpp"

namespace ss {

// Fast, aggressive melee combatant.
// Relentlessly pursues the player and inflicts contact damage.
class MeleeEnemy : public Enemy {
public:
    explicit MeleeEnemy(sf::Vector2f position);

    EnemyType type() const override { return EnemyType::Melee; }

    void updateAI(float dt, sf::Vector2f targetPos) override;
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

private:
    float m_animTimer = 0.f;
};

} // namespace ss
