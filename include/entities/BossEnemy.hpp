#pragma once

#include "entities/Enemy.hpp"
#include <memory>
#include <string>
#include <vector>

namespace ss {

class Projectile;

// Imposing, high-durability boss encounter.
// Possesses massive health (8-15x ordinary tank enemy), heavy armor, devastating contact damage,
// and periodic special arcane shockwave projectiles.
class BossEnemy : public Enemy {
public:
    explicit BossEnemy(sf::Vector2f position);

    EnemyType type() const override { return EnemyType::Boss; }
    bool isBoss() const override { return true; }
    std::string name() const override { return "ANCIENT GOLEM OVERLORD"; }

    void updateAI(float dt, sf::Vector2f targetPos, const class Arena* arena = nullptr) override;
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

    // Boss special attack mechanics
    bool canFireVolley() const { return m_volleyTimer <= 0.f; }
    std::vector<std::unique_ptr<Projectile>> fireVolley(sf::Vector2f targetPos);

    static constexpr float kBossRadius = 50.f;
    static constexpr float kBossMaxHealth = 1800.f;
    static constexpr float kBossArmor = 45.f;
    static constexpr float kBossMoveSpeed = 55.f;
    static constexpr float kBossContactDamage = 45.f;
    static constexpr float kBossAttackCooldown = 1.0f;
    static constexpr int kBossExpReward = 250;

private:
    float m_animTimer = 0.f;
    float m_volleyTimer = 0.f;
};

} // namespace ss
