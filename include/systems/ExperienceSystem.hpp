#pragma once

#include "entities/ExperiencePickup.hpp"
#include "entities/Player.hpp"
#include "systems/EffectSystem.hpp"
#include "systems/PlayerProgression.hpp"

#include <memory>
#include <vector>

namespace ss {

// Manages all active experience pickups on the battlefield,
// proximity attraction towards the player, collection collision checks,
// and granting EXP to PlayerProgression.
class ExperienceSystem {
public:
    ExperienceSystem() = default;

    // Spawns a new pickup at the specified position
    void spawnPickup(sf::Vector2f position, int expValue);

    // Updates pickup bobbing, magnet pull, collection detection, and cleans up collected gems
    void update(float dt, Player& player, PlayerProgression& progression, EffectSystem& effects);

    // Draws all active gems
    void draw(sf::RenderTarget& target, const class ResourceManager* resources = nullptr) const;

    std::size_t pickupCount() const { return m_pickups.size(); }
    const std::vector<std::unique_ptr<ExperiencePickup>>& pickups() const { return m_pickups; }

    void clear() { m_pickups.clear(); }

private:
    std::vector<std::unique_ptr<ExperiencePickup>> m_pickups;
};

} // namespace ss
