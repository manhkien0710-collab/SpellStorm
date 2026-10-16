#pragma once

#include <algorithm>

namespace ss {

struct DamageInfo {
    float amount = 0.f;
    bool ignoreArmor = false; // damage-over-time ignores armor
};

// Armor gives diminishing returns: 100 armor halves incoming damage.
inline float mitigateDamage(float amount, float armor)
{
    if (amount <= 0.f) {
        return 0.f;
    }
    return amount * 100.f / (100.f + std::max(0.f, armor));
}

} // namespace ss
