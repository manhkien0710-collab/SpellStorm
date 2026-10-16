#pragma once

#include "entities/Enemy.hpp"

namespace ss {

// Massive, heavily armored behemoth.
// Possesses high durability and armor, moves slowly, and inflicts devastating contact damage.
class TankEnemy : public Enemy {
public:
    explicit TankEnemy(sf::Vector2f position);

    EnemyType type() const override { return EnemyType::Tank; }

    void updateAI(float dt, sf::Vector2f targetPos) override;
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

private:
    float m_animTimer = 0.f;
};

} // namespace ss
