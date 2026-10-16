#include "systems/PlayerProgression.hpp"

namespace ss {

PlayerProgression::PlayerProgression(int startingLevel)
{
    reset(startingLevel);
}

void PlayerProgression::reset(int startingLevel)
{
    m_level = std::max(1, startingLevel);
    m_currentExp = 0;
    m_pendingLevelUps = 0;
}

int PlayerProgression::expRequiredForLevel(int lvl)
{
    const int clampedLvl = std::max(1, lvl);
    // Linear scaling progression curve: Level 1: 25, Level 2: 45, Level 3: 65, Level 4: 85...
    return 25 + (clampedLvl - 1) * 20;
}

float PlayerProgression::progressRatio() const
{
    const int needed = expForNextLevel();
    if (needed <= 0) {
        return 1.f;
    }
    return std::clamp(static_cast<float>(m_currentExp) / static_cast<float>(needed), 0.f, 1.f);
}

int PlayerProgression::addExp(int amount)
{
    // Ignore invalid or negative EXP awards
    if (amount <= 0) {
        return 0;
    }

    // Guard against integer overflow
    if (amount > INT_MAX - m_currentExp) {
        m_currentExp = INT_MAX;
    } else {
        m_currentExp += amount;
    }

    int levelsGained = 0;
    // Multi-level up support with excess EXP retention and infinite loop guard
    while (true) {
        const int needed = expForNextLevel();
        if (needed <= 0) {
            break; // Guard against non-positive progression configuration
        }

        if (m_currentExp >= needed) {
            m_currentExp -= needed;
            ++m_level;
            ++m_pendingLevelUps;
            ++levelsGained;
        } else {
            break;
        }
    }

    return levelsGained;
}

void PlayerProgression::consumeLevelUp()
{
    if (m_pendingLevelUps > 0) {
        --m_pendingLevelUps;
    }
}

} // namespace ss
