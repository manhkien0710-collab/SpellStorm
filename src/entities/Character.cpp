#include "entities/Character.hpp"

#include "core/MathUtils.hpp"

#include <algorithm>
#include <cmath>

namespace ss {

Character::Character(sf::Vector2f position, float radius, float maxHealth, float armor, float moveSpeed)
    : Entity(position, radius)
    , m_health(maxHealth, armor)
    , m_moveSpeed(moveSpeed)
{
}

float Character::currentMoveSpeed() const
{
    const float slowed = m_statuses.speedMultiplier();
    const float effective = 1.f - (1.f - slowed) * (1.f - m_slowResistance);
    return m_moveSpeed * std::max(0.f, effective);
}

float Character::takeDamage(const DamageInfo& damage)
{
    if (!isAlive() || damage.amount <= 0.f) {
        return 0.f;
    }
    const float dealt = m_health.takeDamage(damage);
    m_hitFlash = kHitFlashDuration;
    if (m_health.isDead()) {
        destroy();
    }
    return dealt;
}

void Character::heal(float amount)
{
    if (!isAlive() || amount <= 0.f) {
        return;
    }
    m_health.heal(amount);
}

void Character::applyKnockback(sf::Vector2f impulse)
{
    m_knockback += impulse * (1.f - m_knockbackResistance);
}

void Character::addStatus(std::unique_ptr<StatusEffect> status)
{
    if (status && isAlive()) {
        m_statuses.add(std::move(status));
    }
}

void Character::update(float dt)
{
    updateCharacter(dt);
}

void Character::updateCharacter(float dt)
{
    m_hitFlash = std::max(0.f, m_hitFlash - dt);
    m_statuses.update(dt, *this);
}

void Character::moveInDirection(sf::Vector2f direction, float speedFactor, float dt)
{
    const sf::Vector2f desired = math::normalize(direction) * currentMoveSpeed() * speedFactor;
    m_velocity = desired + m_knockback;
    setPosition(position() + m_velocity * dt);

    // Knockback decays exponentially (frame-rate independent).
    m_knockback *= std::exp(-7.f * dt);
    if (math::lengthSq(m_knockback) < 1.f) {
        m_knockback = {0.f, 0.f};
    }
}

void Character::setBaseMoveSpeed(float value) { m_moveSpeed = std::max(0.f, value); }
void Character::setSlowResistance(float value) { m_slowResistance = std::clamp(value, 0.f, 1.f); }
void Character::setKnockbackResistance(float value) { m_knockbackResistance = std::clamp(value, 0.f, 1.f); }

void Character::resetCharacter(sf::Vector2f position, float maxHealth, float armor, float moveSpeed)
{
    setPosition(position);
    setAlive(true);
    m_health.reset(maxHealth, armor);
    m_moveSpeed = std::max(0.f, moveSpeed);
    m_slowResistance = 0.f;
    m_knockbackResistance = 0.f;
    m_hitFlash = 0.f;
    m_knockback = {0.f, 0.f};
    m_velocity = {0.f, 0.f};
    m_statuses.clear();
}

} // namespace ss
