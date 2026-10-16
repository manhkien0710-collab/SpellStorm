#include "ui/GameOverOverlay.hpp"
#include "core/DrawUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/RectangleShape.hpp>
#include <SFML/Graphics/Text.hpp>
#include <algorithm>
#include <iomanip>
#include <sstream>
#include <string>

namespace ss {

GameOverOverlay::GameOverOverlay()
{
}

void GameOverOverlay::open(float survivalTime, int enemiesDefeated, int levelReached)
{
    m_survivalTime = std::max(0.f, survivalTime);
    m_enemiesDefeated = std::max(0, enemiesDefeated);
    m_levelReached = std::max(1, levelReached);
    m_isOpen = true;
    m_restartHovered = false;
    m_restartPressed = false;
    m_quitHovered = false;
    m_quitPressed = false;
    m_openGraceTimer = 0.2f; // Prevent accidental click-through from rapid action input
    updateLayout(m_lastViewSize);
}

void GameOverOverlay::close()
{
    m_isOpen = false;
    m_restartHovered = false;
    m_restartPressed = false;
    m_quitHovered = false;
    m_quitPressed = false;
    m_openGraceTimer = 0.f;
}

void GameOverOverlay::update(float dt)
{
    if (m_openGraceTimer > 0.f) {
        m_openGraceTimer = std::max(0.f, m_openGraceTimer - dt);
    }
}

std::string GameOverOverlay::formattedSurvivalTime() const
{
    int totalSec = static_cast<int>(m_survivalTime);
    int minutes = totalSec / 60;
    int seconds = totalSec % 60;

    std::ostringstream ss;
    ss << std::setfill('0') << std::setw(2) << minutes << ":"
       << std::setfill('0') << std::setw(2) << seconds;
    return ss.str();
}

void GameOverOverlay::updateLayout(sf::Vector2f viewSize) const
{
    m_lastViewSize = viewSize;

    const float panelW = 520.f;
    const float panelH = 440.f;
    const float panelX = (viewSize.x - panelW) * 0.5f;
    const float panelY = (viewSize.y - panelH) * 0.5f;
    m_panelBounds = sf::FloatRect(panelX, panelY, panelW, panelH);

    const float btnW = 210.f;
    const float btnH = 48.f;
    const float spacing = 24.f;
    const float totalBtnW = btnW * 2.f + spacing;
    const float btnStartX = panelX + (panelW - totalBtnW) * 0.5f;
    const float btnY = panelY + panelH - 72.f;

    m_restartButtonBounds = sf::FloatRect(btnStartX, btnY, btnW, btnH);
    m_quitButtonBounds = sf::FloatRect(btnStartX + btnW + spacing, btnY, btnW, btnH);
}

void GameOverOverlay::handleMouseMove(sf::Vector2f mousePos)
{
    if (!m_isOpen) {
        m_restartHovered = false;
        m_quitHovered = false;
        return;
    }

    m_restartHovered = m_restartButtonBounds.contains(mousePos);
    m_quitHovered = m_quitButtonBounds.contains(mousePos);

    if (!m_restartHovered) {
        m_restartPressed = false;
    }
    if (!m_quitHovered) {
        m_quitPressed = false;
    }
}

void GameOverOverlay::handleMouseButtonPressed(sf::Vector2f mousePos)
{
    if (!m_isOpen || m_openGraceTimer > 0.f) {
        return;
    }

    if (m_restartButtonBounds.contains(mousePos)) {
        m_restartPressed = true;
    } else if (m_quitButtonBounds.contains(mousePos)) {
        m_quitPressed = true;
    }
}

GameOverAction GameOverOverlay::handleMouseButtonReleased(sf::Vector2f mousePos)
{
    if (!m_isOpen || m_openGraceTimer > 0.f) {
        m_restartPressed = false;
        m_quitPressed = false;
        return GameOverAction::None;
    }

    GameOverAction action = GameOverAction::None;
    if (m_restartPressed && m_restartButtonBounds.contains(mousePos)) {
        action = GameOverAction::Restart;
    } else if (m_quitPressed && m_quitButtonBounds.contains(mousePos)) {
        action = GameOverAction::Quit;
    }

    m_restartPressed = false;
    m_quitPressed = false;
    return action;
}

GameOverAction GameOverOverlay::handleMouseClick(sf::Vector2f mousePos)
{
    if (!m_isOpen || m_openGraceTimer > 0.f) {
        return GameOverAction::None;
    }

    if (m_restartButtonBounds.contains(mousePos)) {
        return GameOverAction::Restart;
    }
    if (m_quitButtonBounds.contains(mousePos)) {
        return GameOverAction::Quit;
    }
    return GameOverAction::None;
}

GameOverAction GameOverOverlay::handleKeyPress(sf::Keyboard::Key key)
{
    if (!m_isOpen) {
        return GameOverAction::None;
    }

    if (key == sf::Keyboard::R || key == sf::Keyboard::Return || key == sf::Keyboard::Space) {
        return GameOverAction::Restart;
    }
    if (key == sf::Keyboard::Escape || key == sf::Keyboard::Q) {
        return GameOverAction::Quit;
    }

    return GameOverAction::None;
}

void GameOverOverlay::draw(sf::RenderTarget& target, const sf::Font* font, const ResourceManager* resources) const
{
    if (!m_isOpen) {
        return;
    }

    const sf::Vector2f viewSize = target.getView().getSize();
    updateLayout(viewSize);

    // 1. Full-screen dark crimson vignette backdrop
    sf::RectangleShape backdrop(viewSize);
    backdrop.setPosition(0.f, 0.f);
    backdrop.setFillColor(sf::Color(14, 8, 14, 230));
    target.draw(backdrop);

    // 2. Main Dialog Panel
    sf::RectangleShape panel({m_panelBounds.width, m_panelBounds.height});
    panel.setPosition(m_panelBounds.left, m_panelBounds.top);
    panel.setFillColor(sf::Color(22, 16, 26));
    panel.setOutlineColor(sf::Color(210, 48, 64));
    panel.setOutlineThickness(3.0f);
    target.draw(panel);

    // Header Accent Ribbon
    sf::RectangleShape ribbon({m_panelBounds.width, 8.f});
    ribbon.setPosition(m_panelBounds.left, m_panelBounds.top);
    ribbon.setFillColor(sf::Color(235, 55, 70));
    target.draw(ribbon);

    const float centerX = m_panelBounds.left + m_panelBounds.width * 0.5f;

    // 3. Arcane Death Sigil (Procedural Icon)
    const sf::Vector2f sigilPos(centerX, m_panelBounds.top + 46.f);
    draw::circle(target, sigilPos, 22.f, sf::Color(38, 20, 30), sf::Color(215, 50, 65), 2.2f);
    draw::circle(target, sigilPos, 12.f, sf::Color(190, 35, 50), sf::Color(255, 120, 130), 1.5f);
    draw::circle(target, sigilPos, 4.f, sf::Color(255, 220, 230));

    const sf::Font* titleFont = (resources && resources->hasFont(FontId::Title)) ? resources->getFont(FontId::Title) : font;
    const sf::Font* boldFont = (resources && resources->hasFont(FontId::Bold)) ? resources->getFont(FontId::Bold) : font;
    const sf::Font* bodyFont = (resources && resources->hasFont(FontId::Body)) ? resources->getFont(FontId::Body) : font;

    if (bodyFont || titleFont) {
        // Title Text: "GAME OVER"
        sf::Text titleText;
        titleText.setFont(titleFont ? *titleFont : *bodyFont);
        titleText.setString("GAME OVER");
        titleText.setCharacterSize(36);
        titleText.setStyle(sf::Text::Bold);
        titleText.setFillColor(sf::Color(255, 65, 75));
        const sf::FloatRect titleBounds = titleText.getLocalBounds();
        titleText.setOrigin(titleBounds.left + titleBounds.width * 0.5f, titleBounds.top);
        titleText.setPosition(centerX, m_panelBounds.top + 76.f);
        target.draw(titleText);

        // Subtitle Text
        sf::Text subText;
        subText.setFont(bodyFont ? *bodyFont : *titleFont);
        subText.setString("Your arcane flame has been extinguished");
        subText.setCharacterSize(14);
        subText.setFillColor(sf::Color(195, 180, 205));
        const sf::FloatRect subBounds = subText.getLocalBounds();
        subText.setOrigin(subBounds.left + subBounds.width * 0.5f, subBounds.top);
        subText.setPosition(centerX, m_panelBounds.top + 120.f);
        target.draw(subText);
    }

    // 4. Match Summary Stats Box
    const float statsBoxW = 444.f;
    const float statsBoxH = 145.f;
    const float statsBoxX = m_panelBounds.left + (m_panelBounds.width - statsBoxW) * 0.5f;
    const float statsBoxY = m_panelBounds.top + 148.f;

    sf::RectangleShape statsBox({statsBoxW, statsBoxH});
    statsBox.setPosition(statsBoxX, statsBoxY);
    statsBox.setFillColor(sf::Color(14, 11, 18));
    statsBox.setOutlineColor(sf::Color(65, 50, 75));
    statsBox.setOutlineThickness(1.5f);
    target.draw(statsBox);

    if (bodyFont) {
        // Stats Header
        sf::Text statsHeader;
        statsHeader.setFont(boldFont ? *boldFont : *bodyFont);
        statsHeader.setString("- BATTLE STATISTICS -");
        statsHeader.setCharacterSize(13);
        statsHeader.setStyle(sf::Text::Bold);
        statsHeader.setFillColor(sf::Color(255, 215, 60)); // Gold
        const sf::FloatRect hBounds = statsHeader.getLocalBounds();
        statsHeader.setOrigin(hBounds.left + hBounds.width * 0.5f, hBounds.top);
        statsHeader.setPosition(centerX, statsBoxY + 12.f);
        target.draw(statsHeader);

        // Stat Row 1: Survival Time
        draw::circle(target, {statsBoxX + 28.f, statsBoxY + 46.f}, 5.f, sf::Color(100, 215, 255));
        sf::Text timeLabel;
        timeLabel.setFont(*bodyFont);
        timeLabel.setString("Survival Time:");
        timeLabel.setCharacterSize(15);
        timeLabel.setFillColor(sf::Color(185, 195, 215));
        timeLabel.setPosition(statsBoxX + 44.f, statsBoxY + 38.f);
        target.draw(timeLabel);

        sf::Text timeVal;
        timeVal.setFont(boldFont ? *boldFont : *bodyFont);
        timeVal.setString(formattedSurvivalTime() + "  (" + std::to_string(static_cast<int>(m_survivalTime)) + "s)");
        timeVal.setCharacterSize(15);
        timeVal.setStyle(sf::Text::Bold);
        timeVal.setFillColor(sf::Color(255, 255, 255));
        const sf::FloatRect tvBounds = timeVal.getLocalBounds();
        timeVal.setPosition(statsBoxX + statsBoxW - tvBounds.width - 24.f, statsBoxY + 38.f);
        target.draw(timeVal);

        // Stat Row 2: Enemies Defeated
        draw::circle(target, {statsBoxX + 28.f, statsBoxY + 78.f}, 5.f, sf::Color(255, 80, 90));
        sf::Text foesLabel;
        foesLabel.setFont(*bodyFont);
        foesLabel.setString("Enemies Defeated:");
        foesLabel.setCharacterSize(15);
        foesLabel.setFillColor(sf::Color(185, 195, 215));
        foesLabel.setPosition(statsBoxX + 44.f, statsBoxY + 70.f);
        target.draw(foesLabel);

        sf::Text foesVal;
        foesVal.setFont(boldFont ? *boldFont : *bodyFont);
        foesVal.setString(std::to_string(m_enemiesDefeated));
        foesVal.setCharacterSize(15);
        foesVal.setStyle(sf::Text::Bold);
        foesVal.setFillColor(sf::Color(255, 255, 255));
        const sf::FloatRect fvBounds = foesVal.getLocalBounds();
        foesVal.setPosition(statsBoxX + statsBoxW - fvBounds.width - 24.f, statsBoxY + 70.f);
        target.draw(foesVal);

        // Stat Row 3: Level Reached
        draw::circle(target, {statsBoxX + 28.f, statsBoxY + 110.f}, 5.f, sf::Color(80, 240, 140));
        sf::Text lvlLabel;
        lvlLabel.setFont(*bodyFont);
        lvlLabel.setString("Level Reached:");
        lvlLabel.setCharacterSize(15);
        lvlLabel.setFillColor(sf::Color(185, 195, 215));
        lvlLabel.setPosition(statsBoxX + 44.f, statsBoxY + 102.f);
        target.draw(lvlLabel);

        sf::Text lvlVal;
        lvlVal.setFont(boldFont ? *boldFont : *bodyFont);
        lvlVal.setString("Level " + std::to_string(m_levelReached));
        lvlVal.setCharacterSize(15);
        lvlVal.setStyle(sf::Text::Bold);
        lvlVal.setFillColor(sf::Color(255, 255, 255));
        const sf::FloatRect lvBounds = lvlVal.getLocalBounds();
        lvlVal.setPosition(statsBoxX + statsBoxW - lvBounds.width - 24.f, statsBoxY + 102.f);
        target.draw(lvlVal);
    }

    // 5. Restart Button
    sf::RectangleShape restartBtn({m_restartButtonBounds.width, m_restartButtonBounds.height});
    restartBtn.setPosition(m_restartButtonBounds.left, m_restartButtonBounds.top);
    if (m_restartPressed) {
        restartBtn.setFillColor(sf::Color(25, 95, 55));
        restartBtn.setOutlineColor(sf::Color(160, 230, 180));
        restartBtn.setOutlineThickness(2.0f);
    } else if (m_restartHovered) {
        restartBtn.setFillColor(sf::Color(45, 165, 95));
        restartBtn.setOutlineColor(sf::Color(220, 255, 235));
        restartBtn.setOutlineThickness(2.8f);
    } else {
        restartBtn.setFillColor(sf::Color(35, 125, 75));
        restartBtn.setOutlineColor(sf::Color(70, 185, 115));
        restartBtn.setOutlineThickness(1.8f);
    }
    target.draw(restartBtn);

    if (bodyFont) {
        sf::Text restartText;
        restartText.setFont(boldFont ? *boldFont : *bodyFont);
        restartText.setString("RESTART  [R]");
        restartText.setCharacterSize(15);
        restartText.setStyle(sf::Text::Bold);
        restartText.setFillColor(sf::Color::White);
        const sf::FloatRect rtBounds = restartText.getLocalBounds();
        restartText.setOrigin(rtBounds.left + rtBounds.width * 0.5f, rtBounds.top);
        const float offsetY = m_restartPressed ? 1.f : 0.f;
        restartText.setPosition(m_restartButtonBounds.left + m_restartButtonBounds.width * 0.5f,
                                m_restartButtonBounds.top + (m_restartButtonBounds.height - rtBounds.height) * 0.5f - 2.f + offsetY);
        target.draw(restartText);
    }

    // 6. Quit Button
    sf::RectangleShape quitBtn({m_quitButtonBounds.width, m_quitButtonBounds.height});
    quitBtn.setPosition(m_quitButtonBounds.left, m_quitButtonBounds.top);
    if (m_quitPressed) {
        quitBtn.setFillColor(sf::Color(75, 22, 30));
        quitBtn.setOutlineColor(sf::Color(180, 70, 80));
        quitBtn.setOutlineThickness(2.0f);
    } else if (m_quitHovered) {
        quitBtn.setFillColor(sf::Color(145, 40, 55));
        quitBtn.setOutlineColor(sf::Color(255, 190, 200));
        quitBtn.setOutlineThickness(2.8f);
    } else {
        quitBtn.setFillColor(sf::Color(95, 30, 40));
        quitBtn.setOutlineColor(sf::Color(160, 50, 65));
        quitBtn.setOutlineThickness(1.8f);
    }
    target.draw(quitBtn);

    if (bodyFont) {
        sf::Text quitText;
        quitText.setFont(boldFont ? *boldFont : *bodyFont);
        quitText.setString("QUIT  [ESC]");
        quitText.setCharacterSize(15);
        quitText.setStyle(sf::Text::Bold);
        quitText.setFillColor(sf::Color::White);
        const sf::FloatRect qtBounds = quitText.getLocalBounds();
        quitText.setOrigin(qtBounds.left + qtBounds.width * 0.5f, qtBounds.top);
        const float offsetY = m_quitPressed ? 1.f : 0.f;
        quitText.setPosition(m_quitButtonBounds.left + m_quitButtonBounds.width * 0.5f,
                             m_quitButtonBounds.top + (m_quitButtonBounds.height - qtBounds.height) * 0.5f - 2.f + offsetY);
        target.draw(quitText);
    }
}

} // namespace ss
