#pragma once

#include "combat/Damage.hpp"
#include "combat/HealthComponent.hpp"
#include "combat/StatusEffect.hpp"
#include "entities/Entity.hpp"

#include <memory>

namespace ss {

// Base class for mobile living entities (Player, Enemy).
// Composes HealthComponent for vital statistics and damage logic.
class Character : public Entity {
public:
    float health() const { return m_health.current(); }
    float maxHealth() const { return m_health.max(); }
    float healthRatio() const { return m_health.ratio(); }
    float armor() const { return m_health.armor(); }
    float baseMoveSpeed() const { return m_moveSpeed; }
    float currentMoveSpeed() const; // after slows
    sf::Vector2f velocity() const { return m_velocity; }

    // Returns damage actually dealt. Virtual so Player can add invulnerability frames.
    virtual float takeDamage(const DamageInfo& damage);
    void heal(float amount);
    void scaleMaxHealth(float factor) {
        if (factor > 0.f) {
            setMaxHealth(maxHealth() * factor);
            heal(maxHealth());
        }
    }
    void applyKnockback(sf::Vector2f impulse);

    void addStatus(std::unique_ptr<StatusEffect> status);
    bool hasStatus(StatusKind kind) const { return m_statuses.has(kind); }
    float hitFlashTimer() const { return m_hitFlash; }
    bool isHitFlashing() const { return m_hitFlash > 0.f; }

    void update(float dt) override;
    void resetCharacter(sf::Vector2f position, float maxHealth, float armor, float moveSpeed);

protected:
    Character(sf::Vector2f position, float radius, float maxHealth, float armor, float moveSpeed);

    void updateCharacter(float dt);
    void moveInDirection(sf::Vector2f direction, float speedFactor, float dt);

    void setMaxHealth(float value) { m_health.setMax(value); }
    void setArmor(float value) { m_health.setArmor(value); }
    void setBaseMoveSpeed(float value);
    void setSlowResistance(float value);
    void setKnockbackResistance(float value);

private:
    static constexpr float kHitFlashDuration = 0.12f;

    HealthComponent m_health;
    float m_moveSpeed;
    float m_slowResistance = 0.f;
    float m_knockbackResistance = 0.f;
    float m_hitFlash = 0.f;
    sf::Vector2f m_knockback;
    sf::Vector2f m_velocity;
    StatusEffectList m_statuses;
};

} // namespace ss
