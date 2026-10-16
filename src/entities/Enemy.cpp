#include "entities/Enemy.hpp"
#include "core/MathUtils.hpp"

#include <algorithm>

namespace ss {

Enemy::Enemy(sf::Vector2f position, float radius, float maxHealth, float armor,
             float moveSpeed, float contactDamage, float attackCooldown, int expReward)
    : Character(position, radius, maxHealth, armor, moveSpeed)
    , m_contactDamage(std::max(0.f, contactDamage))
    , m_attackCooldown(std::max(0.05f, attackCooldown))
    , m_attackTimer(0.f)
    , m_expReward(std::max(0, expReward))
{
}

void Enemy::constrainToBounds(const sf::FloatRect& bounds)
{
    sf::Vector2f pos = position();
    const float r = radius();
    const float minX = bounds.left + r;
    const float maxX = bounds.left + bounds.width - r;
    const float minY = bounds.top + r;
    const float maxY = bounds.top + bounds.height - r;

    pos.x = std::clamp(pos.x, minX, maxX);
    pos.y = std::clamp(pos.y, minY, maxY);
    setPosition(pos);
}

void Enemy::update(float dt)
{
    updateCharacter(dt);
    if (m_attackTimer > 0.f) {
        m_attackTimer = std::max(0.f, m_attackTimer - dt);
    }
}

bool Enemy::tryAttack(Character& target)
{
    if (!isAlive() || !target.isAlive() || m_attackTimer > 0.f) {
        return false;
    }

    const float totalRadius = radius() + target.radius();
    if (math::distanceSq(position(), target.position()) <= totalRadius * totalRadius) {
        target.takeDamage({m_contactDamage, false});
        m_attackTimer = m_attackCooldown;
        return true;
    }
    return false;
}

} // namespace ss
