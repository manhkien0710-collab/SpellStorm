#include "ui/LevelUpOverlay.hpp"
#include "core/DrawUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/RectangleShape.hpp>
#include <SFML/Graphics/Text.hpp>
#include <sstream>

namespace ss {

std::vector<std::string> LevelUpOverlay::wrapText(const std::string& text, const sf::Font& font,
                                                  unsigned int charSize, float maxWidth)
{
    std::vector<std::string> lines;
    if (text.empty() || maxWidth <= 0.f) {
        return lines;
    }

    std::stringstream paragraphStream(text);
    std::string paragraph;
    while (std::getline(paragraphStream, paragraph, '\n')) {
        std::stringstream wordStream(paragraph);
        std::string word;
        std::string currentLine;

        while (wordStream >> word) {
            std::string candidate = currentLine.empty() ? word : (currentLine + " " + word);
            sf::Text testText;
            testText.setFont(font);
            testText.setCharacterSize(charSize);
            testText.setString(candidate);

            if (testText.getLocalBounds().width <= maxWidth) {
                currentLine = candidate;
            } else {
                if (!currentLine.empty()) {
                    lines.push_back(currentLine);
                    currentLine = word;
                } else {
                    // Single word exceeds maxWidth, must put it on its own line
                    lines.push_back(word);
                    currentLine.clear();
                }
            }
        }
        if (!currentLine.empty()) {
            lines.push_back(currentLine);
        }
    }

    return lines;
}

LevelUpOverlay::LevelUpOverlay()
{
}

void LevelUpOverlay::open(const std::vector<UpgradeInfo>& options)
{
    m_options = options;
    m_isOpen = true;
    m_hoveredIndex = -1;
    m_selectedIndex = -1; // Explicitly start with no selection!
    m_confirmHovered = false;
    m_openGraceTimer = 0.15f; // Prevent accidental click-through from rapid firing
    updateLayout(m_lastViewSize);
}

void LevelUpOverlay::close()
{
    m_isOpen = false;
    m_options.clear();
    m_cards.clear();
    m_hoveredIndex = -1;
    m_selectedIndex = -1;
    m_confirmHovered = false;
    m_openGraceTimer = 0.f;
}

void LevelUpOverlay::update(float dt)
{
    if (m_openGraceTimer > 0.f) {
        m_openGraceTimer = std::max(0.f, m_openGraceTimer - dt);
    }
}

void LevelUpOverlay::updateLayout(sf::Vector2f viewSize)
{
    m_lastViewSize = viewSize;
    m_cards.clear();

    const std::size_t n = m_options.size();
    if (n == 0) {
        m_confirmButtonBounds = sf::FloatRect(0.f, 0.f, 0.f, 0.f);
        return;
    }

    const float cardW = 280.f;
    const float cardH = 330.f;
    const float spacing = 35.f;
    const float totalW = static_cast<float>(n) * cardW + static_cast<float>(n - 1) * spacing;
    const float startX = (viewSize.x - totalW) * 0.5f;
    const float startY = (viewSize.y - cardH) * 0.5f - 18.f;

    for (std::size_t i = 0; i < n; ++i) {
        sf::FloatRect bounds(startX + static_cast<float>(i) * (cardW + spacing), startY, cardW, cardH);
        m_cards.push_back({bounds, m_options[i]});
    }

    // Centered confirmation button below cards
    const float btnW = 340.f;
    const float btnH = 46.f;
    const float btnX = (viewSize.x - btnW) * 0.5f;
    const float btnY = startY + cardH + 24.f;
    m_confirmButtonBounds = sf::FloatRect(btnX, btnY, btnW, btnH);
}

void LevelUpOverlay::handleMouseMove(sf::Vector2f mousePos)
{
    if (!m_isOpen) {
        m_hoveredIndex = -1;
        m_confirmHovered = false;
        return;
    }

    m_hoveredIndex = -1;
    for (std::size_t i = 0; i < m_cards.size(); ++i) {
        if (m_cards[i].bounds.contains(mousePos)) {
            m_hoveredIndex = static_cast<int>(i);
            break;
        }
    }

    m_confirmHovered = m_confirmButtonBounds.contains(mousePos);
}

bool LevelUpOverlay::handleMouseClick(sf::Vector2f mousePos)
{
    if (!m_isOpen || m_openGraceTimer > 0.f) {
        return false;
    }

    // Step 1: Clicking a card selects it (returns false, NEVER confirms)
    for (std::size_t i = 0; i < m_cards.size(); ++i) {
        if (m_cards[i].bounds.contains(mousePos)) {
            m_selectedIndex = static_cast<int>(i);
            return false;
        }
    }

    // Step 2: Clicking the confirmation button
    if (m_confirmButtonBounds.contains(mousePos)) {
        if (hasValidSelection()) {
            return true; // Confirmed!
        }
        return false; // Disabled when no card selected
    }

    return false; // Clicked outside
}

bool LevelUpOverlay::handleKeyPress(sf::Keyboard::Key key)
{
    if (!m_isOpen) {
        return false;
    }

    // Number keys 1, 2, 3 to select cards
    if (key == sf::Keyboard::Num1 || key == sf::Keyboard::Numpad1) {
        selectCard(0);
        return false;
    }
    if (key == sf::Keyboard::Num2 || key == sf::Keyboard::Numpad2) {
        selectCard(1);
        return false;
    }
    if (key == sf::Keyboard::Num3 || key == sf::Keyboard::Numpad3) {
        selectCard(2);
        return false;
    }

    // Enter or Space confirms current selection
    if (key == sf::Keyboard::Return || key == sf::Keyboard::Space) {
        return tryConfirm();
    }

    return false;
}

void LevelUpOverlay::selectCard(int index)
{
    if (index >= 0 && index < static_cast<int>(m_options.size())) {
        m_selectedIndex = index;
    }
}

bool LevelUpOverlay::hasValidSelection() const
{
    return m_selectedIndex >= 0 && m_selectedIndex < static_cast<int>(m_options.size());
}

bool LevelUpOverlay::tryConfirm()
{
    return hasValidSelection();
}

const UpgradeInfo* LevelUpOverlay::selectedUpgrade() const
{
    if (hasValidSelection()) {
        return &m_options[static_cast<std::size_t>(m_selectedIndex)];
    }
    return nullptr;
}

void LevelUpOverlay::draw(sf::RenderTarget& target, const sf::Font* font, const ResourceManager* resources) const
{
    if (!m_isOpen) {
        return;
    }

    const sf::Vector2f viewSize = target.getView().getSize();
    const_cast<LevelUpOverlay*>(this)->updateLayout(viewSize);

    // 1. Semi-transparent dark backdrop overlay
    sf::RectangleShape backdrop(viewSize);
    backdrop.setPosition(0.f, 0.f);
    backdrop.setFillColor(sf::Color(10, 12, 18, 220));
    target.draw(backdrop);

    const sf::Font* headerFont = (resources && resources->hasFont(FontId::Title)) ? resources->getFont(FontId::Title) : font;
    const sf::Font* boldFont = (resources && resources->hasFont(FontId::Bold)) ? resources->getFont(FontId::Bold) : font;
    const sf::Font* bodyFont = (resources && resources->hasFont(FontId::Body)) ? resources->getFont(FontId::Body) : font;

    if (bodyFont || headerFont) {
        // 2. Banner Header
        sf::Text headerText;
        headerText.setFont(headerFont ? *headerFont : *bodyFont);
        headerText.setString("ARCANE SURGE - LEVEL UP!");
        headerText.setCharacterSize(30);
        headerText.setStyle(sf::Text::Bold);
        headerText.setFillColor(sf::Color(255, 215, 60));
        const sf::FloatRect headerBounds = headerText.getLocalBounds();
        headerText.setOrigin(headerBounds.left + headerBounds.width * 0.5f, headerBounds.top);
        headerText.setPosition(viewSize.x * 0.5f, 80.f);
        target.draw(headerText);

        sf::Text subText;
        subText.setFont(bodyFont ? *bodyFont : *headerFont);
        subText.setString("1. Click a card to select  |  2. Confirm your choice below");
        subText.setCharacterSize(15);
        subText.setFillColor(sf::Color(175, 190, 215));
        const sf::FloatRect subBounds = subText.getLocalBounds();
        subText.setOrigin(subBounds.left + subBounds.width * 0.5f, subBounds.top);
        subText.setPosition(viewSize.x * 0.5f, 122.f);
        target.draw(subText);
    }

    // 3. Render Choice Cards
    for (std::size_t i = 0; i < m_cards.size(); ++i) {
        const auto& card = m_cards[i];
        const bool hovered = (m_hoveredIndex == static_cast<int>(i));
        const bool selected = (m_selectedIndex == static_cast<int>(i));

        // Card Panel Body
        sf::RectangleShape panel({card.bounds.width, card.bounds.height});
        panel.setPosition(card.bounds.left, card.bounds.top);

        if (selected) {
            panel.setFillColor(sf::Color(36, 48, 70));
            panel.setOutlineColor(sf::Color(255, 215, 60));
            panel.setOutlineThickness(4.0f);
        } else if (hovered) {
            panel.setFillColor(sf::Color(28, 34, 48));
            panel.setOutlineColor(sf::Color(140, 160, 200));
            panel.setOutlineThickness(2.5f);
        } else {
            panel.setFillColor(sf::Color(20, 24, 35));
            panel.setOutlineColor(sf::Color(65, 75, 100));
            panel.setOutlineThickness(1.5f);
        }
        target.draw(panel);

        // Header Accent Ribbon
        sf::RectangleShape ribbon({card.bounds.width, 10.f});
        ribbon.setPosition(card.bounds.left, card.bounds.top);
        ribbon.setFillColor(card.info.accentColor);
        target.draw(ribbon);

        // Gem Icon in Card
        const sf::Vector2f gemPos(card.bounds.left + card.bounds.width * 0.5f, card.bounds.top + 46.f);
        draw::circle(target, gemPos, selected ? 18.f : (hovered ? 16.f : 14.f), card.info.accentColor,
                     selected ? sf::Color(255, 215, 60) : sf::Color::White, 1.8f);

        if (bodyFont) {
            // Card Title (with content-aware dynamic font sizing)
            sf::Text titleText;
            titleText.setFont(boldFont ? *boldFont : *bodyFont);
            titleText.setString(card.info.title);
            titleText.setStyle(sf::Text::Bold);
            unsigned int titleSize = 18;
            titleText.setCharacterSize(titleSize);
            const float maxTitleWidth = card.bounds.width - 24.f;
            while (titleText.getLocalBounds().width > maxTitleWidth && titleSize > 13) {
                --titleSize;
                titleText.setCharacterSize(titleSize);
            }
            titleText.setFillColor(selected ? sf::Color::White : (hovered ? sf::Color(240, 245, 255) : sf::Color(220, 225, 235)));
            const sf::FloatRect titleBounds = titleText.getLocalBounds();
            titleText.setOrigin(titleBounds.left + titleBounds.width * 0.5f, titleBounds.top);
            titleText.setPosition(card.bounds.left + card.bounds.width * 0.5f, card.bounds.top + 78.f);
            target.draw(titleText);

            // Rank indicator
            sf::Text rankText;
            rankText.setFont(*bodyFont);
            const std::string rankStr = "Rank " + std::to_string(card.info.currentRank) + " -> " +
                                        std::to_string(card.info.currentRank + 1) + " (Max " +
                                        std::to_string(card.info.maxRank) + ")";
            rankText.setString(rankStr);
            rankText.setCharacterSize(13);
            rankText.setFillColor(card.info.accentColor);
            const sf::FloatRect rankBounds = rankText.getLocalBounds();
            rankText.setOrigin(rankBounds.left + rankBounds.width * 0.5f, rankBounds.top);
            rankText.setPosition(card.bounds.left + card.bounds.width * 0.5f, card.bounds.top + 110.f);
            target.draw(rankText);

            // Description (reusable multi-line word-wrapping layout)
            const float maxDescWidth = card.bounds.width - 36.f; // 18px horizontal padding
            const std::vector<std::string> descLines = wrapText(card.info.description, *bodyFont, 13, maxDescWidth);
            const float lineHeight = std::max(16.f, bodyFont->getLineSpacing(13) * 1.05f);
            const float totalDescHeight = static_cast<float>(descLines.size()) * lineHeight;
            const float descAreaTop = card.bounds.top + 138.f;
            const float descAreaBottom = card.bounds.top + card.bounds.height - 56.f;
            const float descAreaHeight = descAreaBottom - descAreaTop;
            const float startDescY = descAreaTop + std::max(0.f, (descAreaHeight - totalDescHeight) * 0.3f);

            for (std::size_t lineIdx = 0; lineIdx < descLines.size(); ++lineIdx) {
                sf::Text descText;
                descText.setFont(*bodyFont);
                descText.setString(descLines[lineIdx]);
                descText.setCharacterSize(13);
                descText.setFillColor(sf::Color(185, 200, 220));
                const sf::FloatRect descBounds = descText.getLocalBounds();
                descText.setOrigin(descBounds.left + descBounds.width * 0.5f, descBounds.top);
                descText.setPosition(card.bounds.left + card.bounds.width * 0.5f, startDescY + static_cast<float>(lineIdx) * lineHeight);
                target.draw(descText);
            }

            // Selection indicator badge inside card
            sf::RectangleShape cardBtn({card.bounds.width - 36.f, 32.f});
            cardBtn.setPosition(card.bounds.left + 18.f, card.bounds.top + card.bounds.height - 48.f);

            if (selected) {
                cardBtn.setFillColor(sf::Color(255, 215, 60));
            } else if (hovered) {
                cardBtn.setFillColor(sf::Color(55, 68, 92));
            } else {
                cardBtn.setFillColor(sf::Color(32, 38, 52));
            }
            target.draw(cardBtn);

            sf::Text cardBtnText;
            cardBtnText.setFont(boldFont ? *boldFont : *bodyFont);
            cardBtnText.setString(selected ? "SELECTED" : (hovered ? "CLICK TO SELECT" : "SELECT"));
            cardBtnText.setCharacterSize(13);
            cardBtnText.setStyle(sf::Text::Bold);
            cardBtnText.setFillColor(selected ? sf::Color(15, 20, 30) : sf::Color::White);
            const sf::FloatRect cbtnBounds = cardBtnText.getLocalBounds();
            cardBtnText.setOrigin(cbtnBounds.left + cbtnBounds.width * 0.5f, cbtnBounds.top);
            cardBtnText.setPosition(card.bounds.left + card.bounds.width * 0.5f, card.bounds.top + card.bounds.height - 41.f);
            target.draw(cardBtnText);
        }
    }

    // 4. Render Confirm Button below cards
    const bool canConfirm = hasValidSelection();
    sf::RectangleShape confirmBtn({m_confirmButtonBounds.width, m_confirmButtonBounds.height});
    confirmBtn.setPosition(m_confirmButtonBounds.left, m_confirmButtonBounds.top);

    if (canConfirm) {
        confirmBtn.setFillColor(m_confirmHovered ? sf::Color(255, 230, 95) : sf::Color(255, 210, 50));
        confirmBtn.setOutlineColor(sf::Color::White);
        confirmBtn.setOutlineThickness(2.2f);
    } else {
        confirmBtn.setFillColor(sf::Color(42, 48, 60));
        confirmBtn.setOutlineColor(sf::Color(65, 72, 85));
        confirmBtn.setOutlineThickness(1.5f);
    }
    target.draw(confirmBtn);

    if (bodyFont) {
        sf::Text confirmText;
        confirmText.setFont(boldFont ? *boldFont : *bodyFont);
        if (canConfirm) {
            confirmText.setString("CONFIRM UPGRADE  [ENTER]");
            confirmText.setCharacterSize(16);
            confirmText.setStyle(sf::Text::Bold);
            confirmText.setFillColor(sf::Color(15, 20, 30));
        } else {
            confirmText.setString("SELECT AN UPGRADE TO CONFIRM");
            confirmText.setCharacterSize(14);
            confirmText.setFillColor(sf::Color(140, 150, 165));
        }
        const sf::FloatRect cBounds = confirmText.getLocalBounds();
        confirmText.setOrigin(cBounds.left + cBounds.width * 0.5f, cBounds.top);
        confirmText.setPosition(m_confirmButtonBounds.left + m_confirmButtonBounds.width * 0.5f,
                                m_confirmButtonBounds.top + (m_confirmButtonBounds.height - cBounds.height) * 0.5f - 2.f);
        target.draw(confirmText);
    }
}

} // namespace ss
