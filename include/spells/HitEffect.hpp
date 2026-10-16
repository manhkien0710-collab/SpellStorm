#pragma once

#include <SFML/Graphics/Color.hpp>
#include <SFML/System/Vector2.hpp>

#include <memory>
#include <vector>

namespace ss {

class Enemy;
class CombatServices;

// Everything an effect needs to know about one hit.
struct HitContext {
    Enemy& target;
    sf::Vector2f impactPoint;
    sf::Vector2f direction; // normalised travel direction of the hit
    float baseDamage;       // damage of the projectile that caused the hit
    sf::Color color;
    CombatServices& services;
};

// Strategy interface: one reusable piece of on-hit behaviour.
// Spells are composed from a list of HitEffects (see SpellDefinition).
class HitEffect {
public:
    virtual ~HitEffect() = default;
    virtual void apply(const HitContext& context) const = 0;
};

using HitEffectList = std::vector<std::unique_ptr<HitEffect>>;

// Extra damage = baseDamage * multiplier.
class DamageEffect final : public HitEffect {
public:
    explicit DamageEffect(float multiplier);
    void apply(const HitContext& context) const override;

private:
    float m_multiplier;
};

// Fire: damage over time = baseDamage * dpsFraction per second.
class BurnEffect final : public HitEffect {
public:
    BurnEffect(float dpsFraction, float duration);
    void apply(const HitContext& context) const override;

private:
    float m_dpsFraction;
    float m_duration;
};

// Ice: reduces movement speed.
class SlowEffect final : public HitEffect {
public:
    SlowEffect(float slowFraction, float duration);
    void apply(const HitContext& context) const override;

private:
    float m_slowFraction;
    float m_duration;
};

// Wind / Earth: pushes the target along the hit direction.
class KnockbackEffect final : public HitEffect {
public:
    explicit KnockbackEffect(float strength);
    void apply(const HitContext& context) const override;

private:
    float m_strength;
};

// Lightning: jumps to up to N nearby enemies, applying child effects to each.
// Composite: the effects applied on each jump are themselves HitEffects.
class ChainEffect final : public HitEffect {
public:
    ChainEffect(int jumps, float range);
    ChainEffect& then(std::unique_ptr<HitEffect> effect);
    void apply(const HitContext& context) const override;

private:
    int m_jumps;
    float m_range;
    HitEffectList m_onEachTarget;
};

// Explosion: applies child effects to every enemy within a radius.
class AreaEffect final : public HitEffect {
public:
    AreaEffect(float radius, bool includePrimaryTarget);
    AreaEffect& then(std::unique_ptr<HitEffect> effect);
    void apply(const HitContext& context) const override;

private:
    float m_radius;
    bool m_includePrimary;
    HitEffectList m_onEachTarget;
};

} // namespace ss
