#pragma once

#include "entities/Enemy.hpp"

namespace ss {

// Cunning sorcerer enemy that kites the player.
// Maintains a preferred combat range and telegraphs attacks when in range.
class RangedEnemy : public Enemy {
public:
    explicit RangedEnemy(sf::Vector2f position);

    EnemyType type() const override { return EnemyType::Ranged; }

    float preferredDistance() const { return m_preferredDistance; }
    bool isInAttackRange() const { return m_inAttackRange; }

    bool canShoot() const { return isAlive() && m_inAttackRange && m_rangedAttackTimer <= 0.f; }
    float rangedAttackCooldown() const { return m_rangedAttackCooldown; }
    float rangedAttackTimer() const { return m_rangedAttackTimer; }

    // Creates an arcane missile traveling toward targetPos and resets attack timer
    std::unique_ptr<class Projectile> shoot(sf::Vector2f targetPos);

    void update(float dt) override;
    void updateAI(float dt, sf::Vector2f targetPos, const class Arena* arena = nullptr) override;
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

private:
    float m_preferredDistance = 240.f;
    float m_distanceTolerance = 35.f;
    float m_animTimer = 0.f;
    bool m_inAttackRange = false;
    float m_rangedAttackCooldown = 1.8f;
    float m_rangedAttackTimer = 1.0f; // initial delay before first shot
    sf::Vector2f m_targetPos{0.f, 0.f};
};

} // namespace ss
