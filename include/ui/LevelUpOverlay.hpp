#pragma once

#include "systems/UpgradeSystem.hpp"

#include <SFML/Graphics/Font.hpp>
#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/Window/Keyboard.hpp>
#include <vector>

namespace ss {

// Interactive Level-Up selection modal with explicit two-step confirmation.
// Step 1: Click a card to select it (or switch selection).
// Step 2: Click the 'Confirm Upgrade' button (or press Enter) to apply it.
class LevelUpOverlay {
public:
    LevelUpOverlay();

    void open(const std::vector<UpgradeInfo>& options);
    void close();
    bool isOpen() const { return m_isOpen; }

    void update(float dt);

    // Updates hover states based on cursor position
    void handleMouseMove(sf::Vector2f mousePos);

    // Processes mouse press.
    // - Clicking a card selects it (returns false, never applies upgrade).
    // - Clicking the active confirm button confirms selection (returns true).
    // - Clicking outside or clicking disabled confirm button returns false.
    bool handleMouseClick(sf::Vector2f mousePos);

    // Processes key press (e.g. Enter to confirm active selection)
    bool handleKeyPress(sf::Keyboard::Key key);

    // Programmatic selection and confirmation helpers
    void selectCard(int index);
    int selectedIndex() const { return m_selectedIndex; }
    bool hasValidSelection() const;
    bool tryConfirm();
    const UpgradeInfo* selectedUpgrade() const;

    // Renders the overlay, choice cards, and confirmation button
    void draw(sf::RenderTarget& target, const sf::Font* font, const class ResourceManager* resources = nullptr) const;

    const std::vector<UpgradeInfo>& options() const { return m_options; }
    int hoveredIndex() const { return m_hoveredIndex; }
    bool isConfirmHovered() const { return m_confirmHovered; }
    sf::FloatRect confirmButtonBounds() const { return m_confirmButtonBounds; }

private:
    struct CardLayout {
        sf::FloatRect bounds;
        UpgradeInfo info;
    };

    void updateLayout(sf::Vector2f viewSize);

    bool m_isOpen = false;
    std::vector<UpgradeInfo> m_options;
    mutable std::vector<CardLayout> m_cards;
    int m_hoveredIndex = -1;
    int m_selectedIndex = -1;
    mutable sf::FloatRect m_confirmButtonBounds;
    bool m_confirmHovered = false;
    float m_openGraceTimer = 0.f;
    mutable sf::Vector2f m_lastViewSize{1280.f, 720.f};
};

} // namespace ss
