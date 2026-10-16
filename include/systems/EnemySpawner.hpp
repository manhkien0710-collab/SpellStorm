#pragma once

#include "core/Arena.hpp"
#include "core/Random.hpp"
#include "entities/Enemy.hpp"

#include <cstddef>
#include <memory>
#include <optional>
#include <vector>

namespace ss {

// Manages procedural enemy spawning in the arena over match progression.
// Features deterministic pseudo-random placement, player distance avoidance,
// dynamic difficulty scaling, and maximum enemy caps.
class EnemySpawner {
public:
    EnemySpawner(const Arena& arena, Random& rng);

    // Configuration and telemetry
    void setMaxEnemies(std::size_t maxEnemies) {
        m_baseMaxEnemies = maxEnemies;
        m_maxEnemiesCap = maxEnemies;
        m_maxEnemies = maxEnemies;
    }
    void setDynamicEnemyCap(std::size_t baseCap, std::size_t maxCap) {
        m_baseMaxEnemies = baseCap;
        m_maxEnemiesCap = std::max(baseCap, maxCap);
        m_maxEnemies = baseCap;
    }
    void setMaxEnemiesCap(std::size_t cap) {
        m_maxEnemiesCap = cap;
        if (m_maxEnemies > m_maxEnemiesCap) {
            m_maxEnemies = m_maxEnemiesCap;
        }
    }
    std::size_t maxEnemies() const { return m_maxEnemies; }
    std::size_t baseMaxEnemies() const { return m_baseMaxEnemies; }
    std::size_t maxEnemiesCap() const { return m_maxEnemiesCap; }

    void setBaseInterval(float interval) { m_baseInterval = interval; }
    float baseInterval() const { return m_baseInterval; }
    float minInterval() const { return m_minInterval; }
    float currentInterval() const { return m_currentInterval; }
    float elapsedTime() const { return m_elapsedTime; }
    float spawnTimer() const { return m_spawnTimer; }

    // Generates a valid arena position at least minDistance away from playerPos
    sf::Vector2f generateSpawnPosition(sf::Vector2f playerPos, float minDistance = 220.f, float entityRadius = 24.f);

    // Factory method creating a concrete enemy instance
    std::unique_ptr<Enemy> spawnEnemy(sf::Vector2f playerPos, std::optional<EnemyType> typeOverride = std::nullopt);

    // Factory method creating a boss encounter
    std::unique_ptr<Enemy> spawnBoss(sf::Vector2f playerPos);

    // Simulation update: increments match clock, adapts difficulty, and spawns enemies & bosses
    void update(float dt, sf::Vector2f playerPos, std::vector<std::unique_ptr<Enemy>>& activeEnemies);

    // Resets spawner timers, difficulty progress, and enemy cap back to match-start state
    void reset();

private:
    const Arena& m_arena;
    Random& m_rng;

    float m_elapsedTime = 0.f;
    float m_spawnTimer = 1.5f; // Initial grace period before first spawn
    float m_bossSpawnTimer = 60.0f; // Boss spawns at 60s milestone, then periodic
    float m_baseInterval = 2.2f;
    float m_minInterval = 0.55f;
    float m_currentInterval = 2.2f;
    std::size_t m_baseMaxEnemies = 20;
    std::size_t m_maxEnemiesCap = 36;
    std::size_t m_maxEnemies = 20;
};

} // namespace ss
