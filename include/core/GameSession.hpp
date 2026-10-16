#pragma once

#include "core/Arena.hpp"
#include "core/Random.hpp"
#include "entities/Enemy.hpp"
#include "entities/Player.hpp"
#include "spells/Element.hpp"
#include "spells/SpellDefinition.hpp"
#include "spells/SpellRegistry.hpp"
#include "systems/CombatSystem.hpp"
#include "systems/EffectSystem.hpp"
#include "systems/EnemySpawner.hpp"
#include "systems/ExperienceSystem.hpp"
#include "systems/PlayerProgression.hpp"
#include "systems/UpgradeSystem.hpp"
#include "ui/GameOverOverlay.hpp"
#include "ui/LevelUpOverlay.hpp"

#include <SFML/Graphics/Font.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/Window/Event.hpp>
#include <memory>
#include <vector>

namespace ss {

enum class SessionEventResult {
    None,
    Handled,
    RequestQuit
};

enum class ShootingMode {
    Manual,
    Auto
};

// Orchestrates a complete match of Spell Storm:
// - Manages player, arena, enemies, spawner, combat, pickups, progression, and upgrades
// - Controls state transitions between normal gameplay, level-up selection, and game-over
// - Coordinates deterministic restart resetting all match state safely
class GameSession {
public:
    GameSession(const Arena& arena, const SpellRegistry& registry, unsigned int seed = 42,
                const class ResourceManager* resources = nullptr);

    // Completely resets all match-specific state (player, enemies, missiles, timers, upgrades, statistics)
    void restartMatch();

    // Simulation update tick:
    // When Game Over is active: gameplay remains completely frozen.
    // When Level-Up is active: gameplay remains completely frozen.
    // When Playing: moves entities, advances AI/spawner, checks collisions, updates timers.
    void update(float dt, sf::Vector2f mousePos, bool autoplay = false);

    // Continuous attack handling (when mouse button held down while playing)
    void handleContinuousInput(sf::Vector2f mousePos);

    // Routes window events to appropriate subsystems (Game Over modal, Level-Up modal, or gameplay)
    SessionEventResult handleEvent(const sf::Event& event, sf::Vector2f mousePos);

    // Casts currently selected spell towards target world position
    void castSpell(sf::Vector2f targetPos);

    // Renders arena, entities, projectiles, effects, HUD, and overlays
    void render(sf::RenderTarget& target, const sf::Font* font = nullptr,
                const class ResourceManager* resources = nullptr) const;

    void setResourceManager(const class ResourceManager* resources) { m_resources = resources; }
    const class ResourceManager* resourceManager() const { return m_resources; }

    // Subsystem and entity accessors
    const Arena& arena() const { return m_arena; }
    Player& player() { return m_player; }
    const Player& player() const { return m_player; }

    CombatSystem& combatSystem() { return m_combatSystem; }
    const CombatSystem& combatSystem() const { return m_combatSystem; }

    ExperienceSystem& experienceSystem() { return m_expSystem; }
    const ExperienceSystem& experienceSystem() const { return m_expSystem; }

    PlayerProgression& progression() { return m_progression; }
    const PlayerProgression& progression() const { return m_progression; }

    UpgradeManager& upgradeManager() { return m_upgradeManager; }
    const UpgradeManager& upgradeManager() const { return m_upgradeManager; }

    EnemySpawner& spawner() { return m_spawner; }
    const EnemySpawner& spawner() const { return m_spawner; }

    std::vector<std::unique_ptr<Enemy>>& enemies() { return m_enemies; }
    const std::vector<std::unique_ptr<Enemy>>& enemies() const { return m_enemies; }

    EffectSystem& effects() { return m_effects; }
    const EffectSystem& effects() const { return m_effects; }

    LevelUpOverlay& levelUpOverlay() { return m_levelUpOverlay; }
    const LevelUpOverlay& levelUpOverlay() const { return m_levelUpOverlay; }

    GameOverOverlay& gameOverOverlay() { return m_gameOverOverlay; }
    const GameOverOverlay& gameOverOverlay() const { return m_gameOverOverlay; }

    float matchElapsed() const { return m_matchElapsed; }
    int enemiesDefeated() const { return m_enemiesDefeated; }

    bool isGameOver() const { return m_gameOverOverlay.isOpen(); }
    bool isLevelUpOpen() const { return m_levelUpOverlay.isOpen(); }
    bool isPlaying() const { return !isGameOver() && !isLevelUpOpen(); }

    SpellPtr currentSpell() const { return m_currentSpell; }
    const std::vector<Element>& activeMix() const { return m_activeMix; }

    // Manually trigger Game Over (for testing and verification)
    void triggerGameOver();

    // Infuses an element into active spell mixture
    void infuseElement(Element element);

    // Shooting Mode controls
    ShootingMode shootingMode() const { return m_shootingMode; }
    void setShootingMode(ShootingMode mode) { m_shootingMode = mode; }
    void toggleShootingMode();

    // Auto-shot enemy selection policy (nearest living enemy)
    Enemy* findTargetEnemy() const;

    float modeNotificationTimer() const { return m_modeNotificationTimer; }

private:
    void applySelectedLevelUpUpgrade();

    const Arena& m_arena;
    const SpellRegistry& m_registry;
    Random m_rng;

    Player m_player;
    EffectSystem m_effects;
    CombatSystem m_combatSystem;
    ExperienceSystem m_expSystem;
    PlayerProgression m_progression;
    UpgradeManager m_upgradeManager;
    EnemySpawner m_spawner;
    std::vector<std::unique_ptr<Enemy>> m_enemies;

    std::vector<Element> m_activeMix;
    SpellPtr m_currentSpell;

    LevelUpOverlay m_levelUpOverlay;
    GameOverOverlay m_gameOverOverlay;

    ShootingMode m_shootingMode = ShootingMode::Manual;
    bool m_toggleKeyHeld = false;
    float m_modeNotificationTimer = 0.f;
    std::string m_modeNotificationText;
    sf::Color m_modeNotificationColor = sf::Color::White;

    float m_matchElapsed = 0.f;
    int m_enemiesDefeated = 0;
    float m_overlayAutoTimer = 0.f;
    const class ResourceManager* m_resources = nullptr;
};

} // namespace ss
