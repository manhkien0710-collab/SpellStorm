#include "spells/HitEffect.hpp"

#include "combat/CombatServices.hpp"
#include "combat/StatusEffect.hpp"
#include "core/MathUtils.hpp"
#include "entities/Enemy.hpp"
#include "systems/EffectSystem.hpp"

#include <algorithm>

namespace ss {

namespace {

void applyAll(const HitEffectList& effects, const HitContext& context)
{
    for (const auto& effect : effects) {
        if (!context.target.isAlive()) {
            return; // target died mid-way: remaining effects are pointless
        }
        effect->apply(context);
    }
}

} // namespace

// ---------------------------------------------------------------- DamageEffect
DamageEffect::DamageEffect(float multiplier) : m_multiplier(multiplier) {}

void DamageEffect::apply(const HitContext& context) const
{
    context.services.damageEnemy(context.target, {context.baseDamage * m_multiplier, false}, context.color);
}

// ------------------------------------------------------------------ BurnEffect
BurnEffect::BurnEffect(float dpsFraction, float duration) : m_dpsFraction(dpsFraction), m_duration(duration) {}

void BurnEffect::apply(const HitContext& context) const
{
    context.target.addStatus(std::make_unique<BurnStatus>(context.baseDamage * m_dpsFraction, m_duration));
}

// ------------------------------------------------------------------ SlowEffect
SlowEffect::SlowEffect(float slowFraction, float duration) : m_slowFraction(slowFraction), m_duration(duration) {}

void SlowEffect::apply(const HitContext& context) const
{
    context.target.addStatus(std::make_unique<SlowStatus>(m_slowFraction, m_duration));
}

// ------------------------------------------------------------- KnockbackEffect
KnockbackEffect::KnockbackEffect(float strength) : m_strength(strength) {}

void KnockbackEffect::apply(const HitContext& context) const
{
    context.target.applyKnockback(math::normalize(context.direction) * m_strength);
}

// ----------------------------------------------------------------- ChainEffect
ChainEffect::ChainEffect(int jumps, float range) : m_jumps(std::max(0, jumps)), m_range(range) {}

ChainEffect& ChainEffect::then(std::unique_ptr<HitEffect> effect)
{
    if (effect) {
        m_onEachTarget.push_back(std::move(effect));
    }
    return *this;
}

void ChainEffect::apply(const HitContext& context) const
{
    // Ids instead of pointers: a visited enemy may have died during the chain.
    std::vector<EntityId> visited{context.target.id()};
    sf::Vector2f from = context.target.position();

    for (int jump = 0; jump < m_jumps; ++jump) {
        Enemy* next = context.services.findNearestEnemy(from, m_range, visited);
        if (next == nullptr) {
            break;
        }
        const EntityId nextId = next->id();
        const sf::Vector2f to = next->position();
        visited.push_back(nextId);
        from = to;

        context.services.effects().beam(from, to, context.color, 0.18f);

        const HitContext jumpContext{*next, to, math::normalize(to - from), context.baseDamage, context.color,
                                     context.services};
        applyAll(m_onEachTarget, jumpContext);
    }
}

// ------------------------------------------------------------------ AreaEffect
AreaEffect::AreaEffect(float radius, bool includePrimaryTarget)
    : m_radius(radius)
    , m_includePrimary(includePrimaryTarget)
{
}

AreaEffect& AreaEffect::then(std::unique_ptr<HitEffect> effect)
{
    if (effect) {
        m_onEachTarget.push_back(std::move(effect));
    }
    return *this;
}

void AreaEffect::apply(const HitContext& context) const
{
    const sf::Vector2f center = context.impactPoint;
    const EntityId primary = context.target.id();

    context.services.effects().ring(center, m_radius, context.color, 0.35f);
    context.services.effects().burst(center, context.color, 18, 260.f, 0.45f, 4.f);

    context.services.forEachEnemyInRadius(center, m_radius, [&](Enemy& enemy) {
        if (!m_includePrimary && enemy.id() == primary) {
            return;
        }
        const HitContext areaContext{enemy, center, math::normalize(enemy.position() - center), context.baseDamage,
                                     context.color, context.services};
        applyAll(m_onEachTarget, areaContext);
    });
}

} // namespace ss
