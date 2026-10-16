#pragma once

#include "entities/Character.hpp"
#include <SFML/Graphics/Rect.hpp>
#include <string>

namespace ss {

enum class EnemyType {
    Melee,
    Ranged,
    Tank,
    Boss
};

// Abstract base class for arena enemies.
// Extends Character with contact damage, attack cooldowns, AI navigation,
// boundary constraints, and polymorphic visual presentation.
class Enemy : public Character {
public:
    Enemy(sf::Vector2f position, float radius, float maxHealth, float armor,
          float moveSpeed, float contactDamage, float attackCooldown, int expReward);
    ~Enemy() override = default;

    virtual EnemyType type() const = 0;
    virtual bool isBoss() const { return false; }
    virtual std::string name() const { return "Enemy"; }
    float contactDamage() const { return m_contactDamage; }
    float attackCooldown() const { return m_attackCooldown; }
    float attackTimer() const { return m_attackTimer; }
    bool canAttack() const { return m_attackTimer <= 0.f; }
    int expReward() const { return m_expReward; }
    bool isRewardClaimed() const { return m_rewardClaimed; }
    void claimReward() { m_rewardClaimed = true; }

    // AI navigation and steering towards target position
    virtual void updateAI(float dt, sf::Vector2f targetPos, const class Arena* arena = nullptr) = 0;

    // Overload taking target Character directly
    void updateAI(float dt, const Character& target, const class Arena* arena = nullptr) {
        updateAI(dt, target.position(), arena);
    }

    // Reusable navigation steering helper: uses LOS when clear, 8-way A* when blocked
    void steerWithNavigation(float dt, sf::Vector2f targetPos, const class Arena* arena,
                             float speedMultiplier = 1.0f);

    const std::vector<sf::Vector2f>& navPath() const { return m_navPath; }
    void clearNavPath() { m_navPath.clear(); m_navWaypointIndex = 0; }

    // Ensures entity remains strictly within the arena bounds
    void constrainToBounds(const sf::FloatRect& bounds);

    // Updates status effects, hitflash, and attack cooldown timer
    void update(float dt) override;

    // Checks contact collision with target Character and applies damage if cooldown ready.
    // Returns true if damage was dealt.
    bool tryAttack(Character& target);

protected:
    void resetAttackCooldown() { m_attackTimer = m_attackCooldown; }

    std::vector<sf::Vector2f> m_navPath;
    std::size_t m_navWaypointIndex = 0;
    float m_navRepathTimer = 0.f;
    sf::Vector2f m_navLastTargetPos{0.f, 0.f};
    sf::Vector2f m_navLastPosition{0.f, 0.f};
    float m_navStuckTimer = 0.f;

private:
    float m_contactDamage;
    float m_attackCooldown;
    float m_attackTimer = 0.f;
    int m_expReward;
    bool m_rewardClaimed = false;
};

} // namespace ss
