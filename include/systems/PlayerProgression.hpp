#pragma once

#include <climits>
#include <algorithm>

namespace ss {

// Manages player level, experience accumulation, progression thresholds,
// and queued level-up events.
// Designed with strict bounds-checking, overflow protection, and excess EXP retention.
class PlayerProgression {
public:
    explicit PlayerProgression(int startingLevel = 1);

    int level() const { return m_level; }
    int currentExp() const { return m_currentExp; }
    int expForNextLevel() const { return expRequiredForLevel(m_level); }
    float progressRatio() const;

    // Grants experience. Returns number of level-ups triggered (>= 0).
    // Ignores negative or invalid values. Safely handles multiple level-ups and excess EXP.
    int addExp(int amount);

    // Queue of pending level-up choices waiting to be picked
    bool hasPendingLevelUp() const { return m_pendingLevelUps > 0; }
    int pendingLevelUps() const { return m_pendingLevelUps; }
    void consumeLevelUp();

    // Progression curve formula: 25 base EXP, increasing by 20 per level
    static int expRequiredForLevel(int lvl);

    void reset(int startingLevel = 1);

private:
    int m_level = 1;
    int m_currentExp = 0;
    int m_pendingLevelUps = 0;
};

} // namespace ss
