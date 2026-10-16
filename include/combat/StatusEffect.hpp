#pragma once

#include <cstddef>
#include <memory>
#include <vector>

namespace ss {

class Character;

enum class StatusKind { Burn, Slow };

// A timed condition attached to a Character (burning, slowed, ...).
// Modifies gameplay attributes or applies damage over time.
// Pure gameplay logic: independent of rendering and graphics APIs.
class StatusEffect {
public:
    explicit StatusEffect(float duration);
    virtual ~StatusEffect() = default;

    virtual StatusKind kind() const = 0;
    virtual float speedMultiplier() const { return 1.f; }

    void update(float dt, Character& owner);
    bool expired() const { return m_remaining <= 0.f; }
    float remaining() const { return m_remaining; }

protected:
    virtual void onTick(float dt, Character& owner);

private:
    float m_remaining;
};

class BurnStatus final : public StatusEffect {
public:
    BurnStatus(float damagePerSecond, float duration);

    StatusKind kind() const override { return StatusKind::Burn; }
    float damagePerSecond() const { return m_damagePerSecond; }

    static constexpr float kTickInterval = 0.5f;

protected:
    void onTick(float dt, Character& owner) override;

private:
    float m_damagePerSecond;
    float m_tickTimer = kTickInterval;
};

class SlowStatus final : public StatusEffect {
public:
    SlowStatus(float slowFraction, float duration); // 0.4 = 40% slower

    StatusKind kind() const override { return StatusKind::Slow; }
    float speedMultiplier() const override { return 1.f - m_slowFraction; }

private:
    float m_slowFraction;
};

// Container owned by each Character. Re-applying an effect of the same
// kind replaces (refreshes) the old one instead of stacking forever.
class StatusEffectList {
public:
    void add(std::unique_ptr<StatusEffect> effect);
    void update(float dt, Character& owner);
    float speedMultiplier() const;
    bool has(StatusKind kind) const;
    void clear() { m_effects.clear(); }
    std::size_t size() const { return m_effects.size(); }

private:
    std::vector<std::unique_ptr<StatusEffect>> m_effects;
};

} // namespace ss
