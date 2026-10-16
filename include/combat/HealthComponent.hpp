#pragma once

#include "combat/Damage.hpp"

#include <algorithm>

namespace ss {

// Encapsulates health, max health, armor, and damage mitigation.
// Applies Single Responsibility Principle: handles vital stats and damage calculations
// independently of entity physics or graphics.
class HealthComponent {
public:
    explicit HealthComponent(float maxHealth, float armor = 0.f);

    float current() const { return m_current; }
    float max() const { return m_max; }
    float ratio() const { return m_max > 0.f ? std::clamp(m_current / m_max, 0.f, 1.f) : 0.f; }
    float armor() const { return m_armor; }

    bool isAlive() const { return m_current > 0.f; }
    bool isDead() const { return m_current <= 0.f; }

    // Returns damage actually dealt after armor mitigation.
    float takeDamage(const DamageInfo& damage);
    void heal(float amount);

    void setMax(float maxHealth);
    void setArmor(float armor);
    void reset(float maxHealth, float armor = 0.f);

private:
    float m_current;
    float m_max;
    float m_armor;
};

} // namespace ss
