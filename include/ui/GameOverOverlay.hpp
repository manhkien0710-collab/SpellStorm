#pragma once

#include <SFML/Graphics/Font.hpp>
#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/Window/Keyboard.hpp>
#include <string>

namespace ss {

enum class GameOverAction {
    None,
    Restart,
    Quit
};

// Interactive Game Over overlay modal.
// Appears upon player death to display match telemetry (survival time,
// enemies defeated, level reached) and provides visual buttons to Restart or Quit.
class GameOverOverlay {
public:
    GameOverOverlay();

    // Opens the overlay with match results
    void open(float survivalTime, int enemiesDefeated, int levelReached);

    // Closes the overlay and clears statistics
    void close();

    bool isOpen() const { return m_isOpen; }

    // Advances UI animations and grace timer
    void update(float dt);

    // Cursor position tracking for hover states
    void handleMouseMove(sf::Vector2f mousePos);

    // Mouse button press for pressed states
    void handleMouseButtonPressed(sf::Vector2f mousePos);

    // Mouse button release to activate buttons (releases outside cancel action)
    GameOverAction handleMouseButtonReleased(sf::Vector2f mousePos);

    // Direct click shortcut (calls press + release)
    GameOverAction handleMouseClick(sf::Vector2f mousePos);

    // Keyboard navigation (R/Enter/Space -> Restart, Esc/Q -> Quit)
    GameOverAction handleKeyPress(sf::Keyboard::Key key);

    // Procedural and typography rendering
    void draw(sf::RenderTarget& target, const sf::Font* font, const class ResourceManager* resources = nullptr) const;

    // Telemetry and testing getters
    float survivalTime() const { return m_survivalTime; }
    std::string formattedSurvivalTime() const;
    int enemiesDefeated() const { return m_enemiesDefeated; }
    int levelReached() const { return m_levelReached; }
    float openGraceTimer() const { return m_openGraceTimer; }

    bool isRestartHovered() const { return m_restartHovered; }
    bool isRestartPressed() const { return m_restartPressed; }
    bool isQuitHovered() const { return m_quitHovered; }
    bool isQuitPressed() const { return m_quitPressed; }

    sf::FloatRect panelBounds() const { return m_panelBounds; }
    sf::FloatRect restartButtonBounds() const { return m_restartButtonBounds; }
    sf::FloatRect quitButtonBounds() const { return m_quitButtonBounds; }

private:
    void updateLayout(sf::Vector2f viewSize) const;

    bool m_isOpen = false;
    float m_survivalTime = 0.f;
    int m_enemiesDefeated = 0;
    int m_levelReached = 1;
    float m_openGraceTimer = 0.f;

    bool m_restartHovered = false;
    bool m_restartPressed = false;
    bool m_quitHovered = false;
    bool m_quitPressed = false;

    mutable sf::FloatRect m_panelBounds;
    mutable sf::FloatRect m_restartButtonBounds;
    mutable sf::FloatRect m_quitButtonBounds;
    mutable sf::Vector2f m_lastViewSize{1280.f, 720.f};
};

} // namespace ss
