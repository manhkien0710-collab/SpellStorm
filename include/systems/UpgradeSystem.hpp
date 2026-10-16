#pragma once

#include "core/Random.hpp"
#include "entities/Player.hpp"

#include <SFML/Graphics/Color.hpp>
#include <string>
#include <vector>

namespace ss {

enum class UpgradeType {
    MaxHealth,
    MoveSpeed,
    Damage,
    CastCooldown,
    ProjectileSpeed,
    ProjectileLifetime,
    Piercing
};

struct UpgradeInfo {
    UpgradeType type;
    std::string title;
    std::string description;
    int currentRank = 0;
    int maxRank = 5;
    sf::Color accentColor = sf::Color::White;
};

// Manages available player perks, upgrade ranks, randomized selection without
// duplicates, and applying modifiers to the Player character.
class UpgradeManager {
public:
    UpgradeManager();

    // Returns up to 'count' distinct, non-maxed upgrade choices
    std::vector<UpgradeInfo> getAvailableUpgrades(int count, Random& rng);

    // Applies the chosen upgrade to the player. Returns true on success, false if capped or invalid.
    bool applyUpgrade(UpgradeType type, Player& player);

    int getRank(UpgradeType type) const;
    int getMaxRank(UpgradeType type) const;
    bool isMaxRank(UpgradeType type) const;
    const UpgradeInfo* getInfo(UpgradeType type) const;

    void reset();

private:
    std::vector<UpgradeInfo> m_upgrades;
};

} // namespace ss
