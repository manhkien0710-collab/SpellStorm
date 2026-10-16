#pragma once

#include "core/Arena.hpp"
#include "core/Camera.hpp"
#include "core/Random.hpp"
#include "entities/Player.hpp"
#include "systems/EffectSystem.hpp"

#include <SFML/Graphics/Color.hpp>
#include <SFML/Graphics/Font.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>
#include <string>
#include <vector>

namespace ss {

enum class PowerUpType {
    ArcaneOverdrive = 0, // 1.8x firing rate
    PiercingSurge,       // +2 projectile pierce hits
    ElementalBurst       // Empowered twin elemental burst (1.35x dmg, +3 radius)
};

struct ActiveTemporaryAbility {
    PowerUpType type = PowerUpType::ArcaneOverdrive;
    float remainingDuration = 0.f;
    bool active = false;

    static constexpr float kDuration = 5.0f; // Exactly 5 seconds of active gameplay

    void activate(PowerUpType newType) {
        type = newType;
        remainingDuration = kDuration;
        active = true;
    }

    void refresh() {
        remainingDuration = kDuration;
        active = true;
    }

    void update(float dt) {
        if (!active) return;
        remainingDuration -= dt;
        if (remainingDuration <= 0.f) {
            remainingDuration = 0.f;
            active = false;
        }
    }

    float durationRatio() const {
        return std::clamp(remainingDuration / kDuration, 0.f, 1.f);
    }

    std::string name() const;
    std::string description() const;
    sf::Color primaryColor() const;
    sf::Color secondaryColor() const;
};

struct WorldPowerUp {
    PowerUpType type = PowerUpType::ArcaneOverdrive;
    sf::Vector2f position{0.f, 0.f};
    float radius = 18.0f;
    float lifetime = 25.0f;
    float maxLifetime = 25.0f;
    float animTimer = 0.f;
    bool collected = false;

    bool isAlive() const { return !collected && lifetime > 0.f; }
};

// Manages random world power-up pickups and temporary 5-second combat abilities.
// Handles procedural traversable ground spawning, collision detection, 5-second
// gameplay timer updating, pause suspension, recollection policy, and HUD telemetry.
class PowerUpManager {
public:
    PowerUpManager(const Arena& arena, Random& rng);

    // Configuration
    void setSpawnIntervalRange(float minInterval, float maxInterval) {
        m_minSpawnInterval = minInterval;
        m_maxSpawnInterval = std::max(minInterval, maxInterval);
    }
    void setMaxActivePickups(std::size_t maxPickups) { m_maxActivePickups = maxPickups; }
    void setGroundLifetime(float lifetime) { m_groundLifetime = lifetime; }

    float minSpawnInterval() const { return m_minSpawnInterval; }
    float maxSpawnInterval() const { return m_maxSpawnInterval; }
    std::size_t maxActivePickups() const { return m_maxActivePickups; }
    float groundLifetime() const { return m_groundLifetime; }

    // Simulation update: ticks ground timers, spawns pickups, checks collection, updates active ability
    void update(float dt, Player& player, EffectSystem& effects);

    // Explicitly spawns a pickup of chosen type at specific or random valid location
    void spawnPickup(sf::Vector2f pos, PowerUpType type);
    void spawnRandomPickup(sf::Vector2f playerPos);

    // Active ability queries
    const ActiveTemporaryAbility& activeAbility() const { return m_activeAbility; }
    ActiveTemporaryAbility& activeAbility() { return m_activeAbility; }
    bool hasActiveAbility() const { return m_activeAbility.active; }

    // Multipliers and modifiers for combat system integration
    float castCooldownMultiplier() const {
        return (m_activeAbility.active && m_activeAbility.type == PowerUpType::ArcaneOverdrive) ? (1.0f / 1.8f) : 1.0f;
    }
    int bonusPierce() const {
        return (m_activeAbility.active && m_activeAbility.type == PowerUpType::PiercingSurge) ? 2 : 0;
    }
    bool isElementalBurstActive() const {
        return m_activeAbility.active && m_activeAbility.type == PowerUpType::ElementalBurst;
    }

    // World & HUD rendering
    void drawWorld(sf::RenderTarget& target, const Camera& camera) const;
    void drawHUD(sf::RenderTarget& target, const sf::Font* font, sf::Vector2f position) const;

    // Resets all state for a new match
    void reset();

    // Access to active pickups for tests/telemetry
    const std::vector<WorldPowerUp>& pickups() const { return m_pickups; }
    std::vector<WorldPowerUp>& pickups() { return m_pickups; }

private:
    sf::Vector2f findValidSpawnPosition(sf::Vector2f playerPos);

    const Arena& m_arena;
    Random& m_rng;

    std::vector<WorldPowerUp> m_pickups;
    ActiveTemporaryAbility m_activeAbility;

    float m_minSpawnInterval = 12.0f;
    float m_maxSpawnInterval = 20.0f;
    float m_nextSpawnTimer = 10.0f;
    float m_groundLifetime = 25.0f;
    std::size_t m_maxActivePickups = 4;
};

} // namespace ss
