#include "systems/UpgradeSystem.hpp"

#include <algorithm>

namespace ss {

UpgradeManager::UpgradeManager()
{
    reset();
}

void UpgradeManager::reset()
{
    m_upgrades.clear();
    m_upgrades.push_back({
        UpgradeType::MaxHealth,
        "Vitality Surge",
        "+40 Max Health & instant heal",
        0, 5,
        sf::Color(255, 80, 100)
    });
    m_upgrades.push_back({
        UpgradeType::MoveSpeed,
        "Fleet Footwork",
        "+12% Movement Speed",
        0, 5,
        sf::Color(80, 240, 140)
    });
    m_upgrades.push_back({
        UpgradeType::Damage,
        "Arcane Might",
        "+20% Spell Damage",
        0, 5,
        sf::Color(255, 160, 40)
    });
    m_upgrades.push_back({
        UpgradeType::CastCooldown,
        "Quickcast",
        "-12% Spell Cast Cooldown",
        0, 5,
        sf::Color(190, 100, 255)
    });
    m_upgrades.push_back({
        UpgradeType::ProjectileSpeed,
        "Velocity Shard",
        "+20% Missile Speed",
        0, 5,
        sf::Color(60, 215, 255)
    });
    m_upgrades.push_back({
        UpgradeType::ProjectileLifetime,
        "Far-reaching Arc",
        "+25% Missile Lifetime / Range",
        0, 5,
        sf::Color(255, 225, 75)
    });
    m_upgrades.push_back({
        UpgradeType::Piercing,
        "Piercing Lance",
        "+1 Missile Pierce (max 2 ranks, -20% dmg/hit)",
        0, 2,
        sf::Color(255, 120, 200)
    });
}

std::vector<UpgradeInfo> UpgradeManager::getAvailableUpgrades(int count, Random& rng)
{
    std::vector<UpgradeInfo> available;
    for (const auto& up : m_upgrades) {
        if (up.currentRank < up.maxRank) {
            available.push_back(up);
        }
    }

    if (available.size() <= static_cast<std::size_t>(count)) {
        return available;
    }

    // Fisher-Yates shuffle using Random generator to pick distinct options
    for (std::size_t i = available.size() - 1; i > 0; --i) {
        std::size_t j = static_cast<std::size_t>(rng.rangeInt(0, static_cast<int>(i)));
        std::swap(available[i], available[j]);
    }

    available.resize(static_cast<std::size_t>(count));
    return available;
}

bool UpgradeManager::applyUpgrade(UpgradeType type, Player& player)
{
    for (auto& up : m_upgrades) {
        if (up.type == type) {
            if (up.currentRank >= up.maxRank) {
                return false;
            }

            // Apply specific modification to Player
            switch (type) {
            case UpgradeType::MaxHealth:
                player.increaseMaxHealth(40.f);
                break;
            case UpgradeType::MoveSpeed:
                player.multiplyMoveSpeed(1.12f);
                break;
            case UpgradeType::Damage:
                player.multiplyDamage(1.20f);
                break;
            case UpgradeType::CastCooldown:
                player.multiplyCastCooldown(0.88f);
                break;
            case UpgradeType::ProjectileSpeed:
                player.multiplyProjectileSpeed(1.20f);
                break;
            case UpgradeType::ProjectileLifetime:
                player.multiplyProjectileLifetime(1.25f);
                break;
            case UpgradeType::Piercing:
                player.addBonusPierce(1);
                break;
            }

            ++up.currentRank;
            return true;
        }
    }
    return false;
}

int UpgradeManager::getRank(UpgradeType type) const
{
    for (const auto& up : m_upgrades) {
        if (up.type == type) {
            return up.currentRank;
        }
    }
    return 0;
}

int UpgradeManager::getMaxRank(UpgradeType type) const
{
    for (const auto& up : m_upgrades) {
        if (up.type == type) {
            return up.maxRank;
        }
    }
    return 0;
}

bool UpgradeManager::isMaxRank(UpgradeType type) const
{
    for (const auto& up : m_upgrades) {
        if (up.type == type) {
            return up.currentRank >= up.maxRank;
        }
    }
    return true;
}

const UpgradeInfo* UpgradeManager::getInfo(UpgradeType type) const
{
    for (const auto& up : m_upgrades) {
        if (up.type == type) {
            return &up;
        }
    }
    return nullptr;
}

} // namespace ss
