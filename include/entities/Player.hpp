#pragma once

#include "entities/Character.hpp"
#include <SFML/Graphics/Rect.hpp>

namespace ss {

// Controllable player avatar wielding elemental magic.
// Manages keyboard input, aiming orientation, and arena boundary constraints.
class Player : public Character {
public:
    explicit Player(sf::Vector2f position);

    // Reads WASD and arrow key states to update velocity
    void handleInput(float dt);

    // Updates aim direction towards target position (e.g. mouse cursor)
    void setAimTarget(sf::Vector2f mousePos);
    float aimAngle() const { return m_aimAngle; }

    // Ensures player bounding circle stays strictly inside arena bounds
    void constrainToBounds(const sf::FloatRect& bounds);

    static constexpr float kDefaultHealth = 250.f;
    static constexpr float kDefaultArmor = 30.f;
    static constexpr float kDefaultMoveSpeed = 230.f;
    static constexpr float kDefaultCastCooldown = 0.28f;
    static constexpr float kDefaultPickupRadius = 45.0f;

    // Resets all player match state to default
    void reset(sf::Vector2f position);

    // Spell cast cooldown management
    bool canCast() const { return isAlive() && m_castTimer <= 0.f; }
    void triggerCast(float cooldownMultiplier = 1.f) {
        if (!isAlive()) return;
        m_castTimer = m_baseCastCooldown * std::max(0.1f, cooldownMultiplier);
    }
    float castTimer() const { return m_castTimer; }
    float baseCastCooldown() const { return m_baseCastCooldown; }
    void setBaseCastCooldown(float cd) { m_baseCastCooldown = cd; }

    // Progression & Upgrade Modifiers
    void increaseMaxHealth(float amount) {
        setMaxHealth(maxHealth() + amount);
        heal(amount);
    }
    void multiplyMoveSpeed(float factor) {
        setBaseMoveSpeed(baseMoveSpeed() * factor);
    }
    float damageMultiplier() const { return m_damageMultiplier; }
    void setDamageMultiplier(float m) { m_damageMultiplier = m; }
    void multiplyDamage(float factor) { m_damageMultiplier *= factor; }

    void multiplyCastCooldown(float factor) {
        m_baseCastCooldown = std::max(0.06f, m_baseCastCooldown * factor);
    }

    float projectileSpeedMultiplier() const { return m_projectileSpeedMultiplier; }
    void setProjectileSpeedMultiplier(float m) { m_projectileSpeedMultiplier = m; }
    void multiplyProjectileSpeed(float factor) { m_projectileSpeedMultiplier *= factor; }

    float projectileLifetimeMultiplier() const { return m_projectileLifetimeMultiplier; }
    void setProjectileLifetimeMultiplier(float m) { m_projectileLifetimeMultiplier = m; }
    void multiplyProjectileLifetime(float factor) { m_projectileLifetimeMultiplier *= factor; }

    float pickupRadius() const { return m_pickupRadius; }
    void setPickupRadius(float r) { m_pickupRadius = r; }
    void increasePickupRadius(float amount) { m_pickupRadius += amount; }

    int bonusPierce() const { return m_bonusPierce; }
    void addBonusPierce(int amount = 1) { m_bonusPierce = std::min(2, m_bonusPierce + amount); }
    void setBonusPierce(int amount) { m_bonusPierce = std::clamp(amount, 0, 2); }

    // Simulation update (animation timer, character hitflash/statuses, cast cooldown)
    void update(float dt) override;

    // Procedural rendering:
    // - Drop shadow
    // - Flowing wizard robe with team/status tint
    // - Arcane hood and focal gemstone
    // - Orbiting spellcasting catalyst orb oriented toward aim target
    void draw(sf::RenderTarget& target) const override;
    void draw(sf::RenderTarget& target, const class ResourceManager* resources) const override;

private:
    float m_aimAngle = 0.f;
    float m_animTimer = 0.f;
    float m_baseCastCooldown = 0.28f;
    float m_castTimer = 0.f;
    float m_damageMultiplier = 1.0f;
    float m_projectileSpeedMultiplier = 1.0f;
    float m_projectileLifetimeMultiplier = 1.0f;
    float m_pickupRadius = 45.0f;
    int m_bonusPierce = 0;
};

} // namespace ss
