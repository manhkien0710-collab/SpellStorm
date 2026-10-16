#include "systems/EnemySpawner.hpp"

#include "core/MathUtils.hpp"
#include "entities/BossEnemy.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"

#include <algorithm>
#include <cmath>

namespace ss {

EnemySpawner::EnemySpawner(const Arena& arena, Random& rng)
    : m_arena(arena)
    , m_rng(rng)
{
}

sf::Vector2f EnemySpawner::generateSpawnPosition(sf::Vector2f playerPos, float minDistance, float entityRadius)
{
    // In wide tilemap worlds, spawn enemies in an active engagement ring around the player
    // on valid traversable terrain (outside screen view, avoiding water and solid obstacles)
    if (m_arena.hasTileMap()) {
        const float minSpawnDist = std::max(minDistance, 450.f);
        const float maxSpawnDist = minSpawnDist + 350.f;

        for (int attempt = 0; attempt < 32; ++attempt) {
            const float angle = m_rng.range(0.f, math::kTwoPi);
            const float dist = m_rng.range(minSpawnDist, maxSpawnDist);
            const sf::Vector2f candidate = playerPos + math::fromAngle(angle) * dist;

            if (m_arena.isTraversable(candidate, entityRadius)) {
                return candidate;
            }
        }
    }

    const sf::FloatRect& b = m_arena.bounds();
    const float margin = std::max(45.f, entityRadius + 10.f);
    const float minX = b.left + margin;
    const float maxX = b.left + b.width - margin;
    const float minY = b.top + margin;
    const float maxY = b.top + b.height - margin;

    const float minSq = minDistance * minDistance;

    for (int attempt = 0; attempt < 24; ++attempt) {
        const float x = m_rng.range(minX, maxX);
        const float y = m_rng.range(minY, maxY);
        const sf::Vector2f candidate(x, y);
        if (math::distanceSq(candidate, playerPos) >= minSq) {
            if (!m_arena.hasTileMap() || m_arena.isTraversable(candidate, entityRadius)) {
                return candidate;
            }
        }
    }

    // Deterministic fallback: place in opposite quadrant relative to player
    const sf::Vector2f center = m_arena.center();
    sf::Vector2f offset = center - playerPos;
    if (math::lengthSq(offset) < 1e-4f) {
        offset = {1.f, 0.f};
    }
    const sf::Vector2f dir = math::normalize(offset);
    sf::Vector2f fallbackPos = center + dir * (std::min(b.width, b.height) * 0.35f);
    fallbackPos = m_arena.clampPosition(fallbackPos, entityRadius);

    if (m_arena.hasTileMap() && !m_arena.isTraversable(fallbackPos, entityRadius)) {
        // Search radially outward for the closest traversable position
        for (float r = 32.f; r <= 320.f; r += 32.f) {
            for (int a = 0; a < 8; ++a) {
                const float ang = static_cast<float>(a) * (math::kTwoPi / 8.f);
                const sf::Vector2f p = m_arena.clampPosition(fallbackPos + math::fromAngle(ang) * r, entityRadius);
                if (m_arena.isTraversable(p, entityRadius)) {
                    return p;
                }
            }
        }
    }
    return fallbackPos;
}

std::unique_ptr<Enemy> EnemySpawner::spawnBoss(sf::Vector2f playerPos)
{
    const sf::Vector2f spawnPos = generateSpawnPosition(playerPos, 350.f, BossEnemy::kBossRadius);
    return std::make_unique<BossEnemy>(spawnPos);
}

std::unique_ptr<Enemy> EnemySpawner::spawnEnemy(sf::Vector2f playerPos, std::optional<EnemyType> typeOverride)
{
    EnemyType chosenType = EnemyType::Melee;

    if (typeOverride.has_value()) {
        chosenType = *typeOverride;
    } else {
        const float roll = m_rng.range(0.f, 1.f);
        if (m_elapsedTime < 25.f) {
            // Early phase: fast swarm and occasional ranged kite
            chosenType = (roll < 0.75f) ? EnemyType::Melee : EnemyType::Ranged;
        } else if (m_elapsedTime < 70.f) {
            // Mid phase: introduce armored tanks and balanced ranged support
            if (roll < 0.45f) {
                chosenType = EnemyType::Melee;
            } else if (roll < 0.85f) {
                chosenType = EnemyType::Ranged;
            } else {
                chosenType = EnemyType::Tank;
            }
        } else {
            // Late phase: heavy mixed assault with increased ranged pressure
            if (roll < 0.35f) {
                chosenType = EnemyType::Melee;
            } else if (roll < 0.80f) {
                chosenType = EnemyType::Ranged;
            } else {
                chosenType = EnemyType::Tank;
            }
        }
    }

    float entityRadius = 16.f;
    if (chosenType == EnemyType::Tank) {
        entityRadius = 26.f;
    } else if (chosenType == EnemyType::Boss) {
        entityRadius = BossEnemy::kBossRadius;
    }

    const sf::Vector2f spawnPos = generateSpawnPosition(playerPos, 220.f, entityRadius);
    std::unique_ptr<Enemy> enemy;

    switch (chosenType) {
        case EnemyType::Melee:
            enemy = std::make_unique<MeleeEnemy>(spawnPos);
            break;
        case EnemyType::Ranged:
            enemy = std::make_unique<RangedEnemy>(spawnPos);
            break;
        case EnemyType::Tank:
            enemy = std::make_unique<TankEnemy>(spawnPos);
            break;
        case EnemyType::Boss:
            enemy = std::make_unique<BossEnemy>(spawnPos);
            break;
    }

    if (enemy && m_elapsedTime > 50.f && !enemy->isBoss()) {
        // Controlled, gradual health scaling for late-game challenge (max +25%)
        const float hpScale = 1.0f + std::clamp((m_elapsedTime - 50.f) / 600.f, 0.f, 0.25f);
        enemy->scaleMaxHealth(hpScale);
    }

    return enemy ? std::move(enemy) : std::make_unique<MeleeEnemy>(spawnPos);
}

void EnemySpawner::update(float dt, sf::Vector2f playerPos, std::vector<std::unique_ptr<Enemy>>& activeEnemies)
{
    m_elapsedTime += dt;

    // Difficulty scaling: spawn interval gradually tightens from base to min
    const float progress = std::clamp(m_elapsedTime / 120.f, 0.f, 1.f);
    m_currentInterval = m_baseInterval - progress * (m_baseInterval - m_minInterval);

    // Active enemy cap scales progressively from baseMaxEnemies up to maxEnemiesCap
    if (m_maxEnemiesCap > m_baseMaxEnemies) {
        m_maxEnemies = static_cast<std::size_t>(
            static_cast<float>(m_baseMaxEnemies) + progress * static_cast<float>(m_maxEnemiesCap - m_baseMaxEnemies)
        );
    } else {
        m_maxEnemies = m_baseMaxEnemies;
    }

    m_spawnTimer -= dt;

    if (m_spawnTimer <= 0.f) {
        m_spawnTimer = m_currentInterval;

        // In late game, occasionally spawn a pair of enemies
        const int spawnCount = (m_elapsedTime > 60.f && m_rng.range(0.f, 1.f) < 0.35f) ? 2 : 1;

        for (int i = 0; i < spawnCount; ++i) {
            if (activeEnemies.size() < m_maxEnemies) {
                auto enemy = spawnEnemy(playerPos);
                if (enemy) {
                    activeEnemies.push_back(std::move(enemy));
                }
            }
        }
    }

    // Periodic Boss encounters: spawns at 60s milestone if no boss is currently active
    m_bossSpawnTimer -= dt;
    if (m_bossSpawnTimer <= 0.f) {
        bool bossPresent = false;
        for (const auto& e : activeEnemies) {
            if (e && e->isAlive() && e->isBoss()) {
                bossPresent = true;
                break;
            }
        }
        if (!bossPresent) {
            activeEnemies.push_back(spawnBoss(playerPos));
            m_bossSpawnTimer = 90.0f; // Next boss wave in 90 seconds
        } else {
            m_bossSpawnTimer = 10.0f; // Retry check in 10s if previous boss still fighting
        }
    }
}

void EnemySpawner::reset()
{
    m_elapsedTime = 0.f;
    m_spawnTimer = 1.5f;
    m_bossSpawnTimer = 60.0f;
    m_currentInterval = m_baseInterval;
    m_maxEnemies = m_baseMaxEnemies;
}

} // namespace ss
