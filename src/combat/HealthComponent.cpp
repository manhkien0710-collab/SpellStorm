#include "combat/HealthComponent.hpp"

namespace ss {

HealthComponent::HealthComponent(float maxHealth, float armor)
    : m_current(std::max(1.f, maxHealth))
    , m_max(std::max(1.f, maxHealth))
    , m_armor(std::max(0.f, armor))
{
}

float HealthComponent::takeDamage(const DamageInfo& damage)
{
    if (isDead() || damage.amount <= 0.f) {
        return 0.f;
    }
    const float dealt = damage.ignoreArmor ? damage.amount : mitigateDamage(damage.amount, m_armor);
    m_current -= dealt;
    if (m_current <= 0.f) {
        m_current = 0.f;
    }
    return dealt;
}

void HealthComponent::heal(float amount)
{
    if (isDead() || amount <= 0.f) {
        return;
    }
    m_current = std::min(m_max, m_current + amount);
}

void HealthComponent::setMax(float maxHealth)
{
    m_max = std::max(1.f, maxHealth);
    m_current = std::min(m_current, m_max);
}

void HealthComponent::setArmor(float armor)
{
    m_armor = std::max(0.f, armor);
}

void HealthComponent::reset(float maxHealth, float armor)
{
    m_max = std::max(1.f, maxHealth);
    m_current = m_max;
    m_armor = std::max(0.f, armor);
}

} // namespace ss
