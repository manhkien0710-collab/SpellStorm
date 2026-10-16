#include "combat/StatusEffect.hpp"

#include "entities/Character.hpp"

#include <algorithm>

namespace ss {

// ---------------------------------------------------------------- StatusEffect
StatusEffect::StatusEffect(float duration)
    : m_remaining(std::max(0.f, duration))
{
}

void StatusEffect::update(float dt, Character& owner)
{
    if (expired()) {
        return;
    }
    const float step = std::min(dt, m_remaining);
    m_remaining -= dt;
    onTick(step, owner);
}

void StatusEffect::onTick(float /*dt*/, Character& /*owner*/) {}

// ------------------------------------------------------------------ BurnStatus
BurnStatus::BurnStatus(float damagePerSecond, float duration)
    : StatusEffect(duration)
    , m_damagePerSecond(std::max(0.f, damagePerSecond))
{
}

void BurnStatus::onTick(float dt, Character& owner)
{
    m_tickTimer -= dt;
    while (m_tickTimer <= 0.f) {
        m_tickTimer += kTickInterval;
        owner.takeDamage({m_damagePerSecond * kTickInterval, true});
    }
}

// ------------------------------------------------------------------ SlowStatus
SlowStatus::SlowStatus(float slowFraction, float duration)
    : StatusEffect(duration)
    , m_slowFraction(std::clamp(slowFraction, 0.f, 0.9f))
{
}

// ------------------------------------------------------------ StatusEffectList
void StatusEffectList::add(std::unique_ptr<StatusEffect> effect)
{
    if (!effect) {
        return;
    }
    const auto it = std::find_if(m_effects.begin(), m_effects.end(),
                                 [&](const auto& e) { return e->kind() == effect->kind(); });
    if (it != m_effects.end()) {
        *it = std::move(effect);
    } else {
        m_effects.push_back(std::move(effect));
    }
}

void StatusEffectList::update(float dt, Character& owner)
{
    for (auto& effect : m_effects) {
        effect->update(dt, owner);
    }
    m_effects.erase(std::remove_if(m_effects.begin(), m_effects.end(),
                                   [](const auto& e) { return e->expired(); }),
                    m_effects.end());
}

float StatusEffectList::speedMultiplier() const
{
    float multiplier = 1.f;
    for (const auto& effect : m_effects) {
        multiplier = std::min(multiplier, effect->speedMultiplier());
    }
    return multiplier;
}

bool StatusEffectList::has(StatusKind kind) const
{
    return std::any_of(m_effects.begin(), m_effects.end(), [kind](const auto& e) { return e->kind() == kind; });
}

} // namespace ss
