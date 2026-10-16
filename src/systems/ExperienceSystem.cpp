#include "systems/ExperienceSystem.hpp"

#include "core/MathUtils.hpp"

#include <algorithm>

namespace ss {

void ExperienceSystem::spawnPickup(sf::Vector2f position, int expValue)
{
    m_pickups.push_back(std::make_unique<ExperiencePickup>(position, expValue));
}

void ExperienceSystem::update(float dt, Player& player, PlayerProgression& progression, EffectSystem& effects)
{
    const sf::Vector2f playerPos = player.position();
    const float collectRadius = player.pickupRadius();
    const float collectRadiusSq = collectRadius * collectRadius;
    const float magnetRadius = collectRadius * 2.8f;
    const float magnetRadiusSq = magnetRadius * magnetRadius;

    for (auto& pickup : m_pickups) {
        if (!pickup->isAlive() || pickup->isCollected()) {
            continue;
        }

        pickup->update(dt);

        if (player.isAlive()) {
            const float distSq = math::distanceSq(pickup->position(), playerPos);

            // 1. Magnet attraction towards player
            if (distSq <= magnetRadiusSq) {
                // Accelerate smoothly towards the player
                const float pullSpeed = 240.f + (magnetRadius - std::sqrt(distSq)) * 2.5f;
                pickup->magnetTowards(playerPos, pullSpeed, dt);
            }

            // 2. Collection check
            if (distSq <= collectRadiusSq) {
                const int expVal = pickup->expValue();
                pickup->collect();
                progression.addExp(expVal);

                // Collection audiovisual feedback
                effects.burst(pickup->position(), sf::Color(120, 230, 255), 7, 95.f, 0.22f);
                effects.ring(pickup->position(), 16.f, sf::Color(220, 255, 255), 0.16f);
            }
        }
    }

    // Prune collected/dead gems
    m_pickups.erase(
        std::remove_if(m_pickups.begin(), m_pickups.end(), [](const std::unique_ptr<ExperiencePickup>& p) {
            return !p->isAlive() || p->isCollected();
        }),
        m_pickups.end()
    );
}

void ExperienceSystem::draw(sf::RenderTarget& target, const class ResourceManager* resources) const
{
    for (const auto& pickup : m_pickups) {
        pickup->draw(target, resources);
    }
}

} // namespace ss
