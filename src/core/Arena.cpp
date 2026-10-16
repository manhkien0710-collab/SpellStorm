#include "core/Arena.hpp"

#include "core/Camera.hpp"
#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"
#include "environment/TileMap.hpp"
#include "systems/PathfindingSystem.hpp"

#include <SFML/Graphics/RectangleShape.hpp>
#include <SFML/Graphics/Sprite.hpp>

#include <algorithm>
#include <array>
#include <cmath>

namespace ss {

Arena::Arena(sf::Vector2f center, sf::Vector2f size)
    : m_center(center)
    , m_size(size)
    , m_bounds(center.x - size.x * 0.5f, center.y - size.y * 0.5f, size.x, size.y)
{
}

Arena::~Arena() = default;
Arena::Arena(Arena&&) noexcept = default;
Arena& Arena::operator=(Arena&&) noexcept = default;

void Arena::setTileMap(std::shared_ptr<TileMap> tileMap)
{
    m_tileMap = std::move(tileMap);
    if (m_tileMap) {
        m_pathfinder = std::make_unique<PathfindingSystem>(*m_tileMap);
    } else {
        m_pathfinder.reset();
    }
}

sf::Vector2f Arena::clampPosition(sf::Vector2f position, float radius) const
{
    const float minX = m_bounds.left + radius;
    const float maxX = m_bounds.left + m_bounds.width - radius;
    const float minY = m_bounds.top + radius;
    const float maxY = m_bounds.top + m_bounds.height - radius;

    position.x = std::clamp(position.x, minX, maxX);
    position.y = std::clamp(position.y, minY, maxY);
    return position;
}

bool Arena::contains(sf::Vector2f position, float radius) const
{
    return (position.x - radius >= m_bounds.left) &&
           (position.x + radius <= m_bounds.left + m_bounds.width) &&
           (position.y - radius >= m_bounds.top) &&
           (position.y + radius <= m_bounds.top + m_bounds.height);
}

bool Arena::isTraversable(sf::Vector2f position, float radius) const
{
    if (m_tileMap) {
        return m_tileMap->isTraversable(position, radius);
    }
    return contains(position, radius);
}

sf::Vector2f Arena::resolveCollision(sf::Vector2f oldPos, sf::Vector2f newPos, float radius) const
{
    if (m_tileMap) {
        return m_tileMap->resolveCollision(oldPos, newPos, radius);
    }
    return clampPosition(newPos, radius);
}

void Arena::draw(sf::RenderTarget& target, const ResourceManager* resources, const Camera* camera) const
{
    if (m_tileMap) {
        if (camera) {
            m_tileMap->drawTerrain(target, resources, *camera);
            m_tileMap->drawDecorations(target, resources, *camera);
        } else {
            // If no camera passed, use the active view on target directly with 1.0f zoom (no double-scaling)
            const sf::View& currentView = target.getView();
            Camera viewCam(currentView.getSize(), m_bounds, 1.0f);
            viewCam.setCenter(currentView.getCenter());
            m_tileMap->drawTerrain(target, resources, viewCam);
            m_tileMap->drawDecorations(target, resources, viewCam);
        }
        return;
    }

    // 1. Arena Floor Base
    sf::RectangleShape floorShape(m_size);
    floorShape.setPosition(m_bounds.left, m_bounds.top);
    floorShape.setFillColor(sf::Color(25, 30, 42));
    target.draw(floorShape);

    // 2. Tiled Flagstone Floor with subtle checker variation
    constexpr float tileSize = 70.f;
    const int tilesX = static_cast<int>(std::ceil(m_size.x / tileSize));
    const int tilesY = static_cast<int>(std::ceil(m_size.y / tileSize));

    const sf::Texture* tileTex = resources ? resources->getTexture(TextureId::ArenaFloor) : nullptr;
    if (tileTex) {
        sf::Sprite tileSprite(*tileTex);
        const float scaleX = (tileSize - 2.f) / static_cast<float>(tileTex->getSize().x);
        const float scaleY = (tileSize - 2.f) / static_cast<float>(tileTex->getSize().y);
        tileSprite.setScale(scaleX, scaleY);

        for (int y = 0; y < tilesY; ++y) {
            for (int x = 0; x < tilesX; ++x) {
                const float px = m_bounds.left + static_cast<float>(x) * tileSize;
                const float py = m_bounds.top + static_cast<float>(y) * tileSize;

                if (px >= m_bounds.left + m_size.x || py >= m_bounds.top + m_size.y) continue;

                tileSprite.setPosition(px + 1.f, py + 1.f);
                if ((x + y) % 2 == 0) {
                    tileSprite.setColor(sf::Color(255, 255, 255, 255));
                } else {
                    tileSprite.setColor(sf::Color(215, 225, 240, 240));
                }
                target.draw(tileSprite);
            }
        }
    } else {
        // Procedural tiles fallback
        sf::RectangleShape tileShape({tileSize - 2.f, tileSize - 2.f});

        for (int y = 0; y < tilesY; ++y) {
            for (int x = 0; x < tilesX; ++x) {
                const float px = m_bounds.left + static_cast<float>(x) * tileSize;
                const float py = m_bounds.top + static_cast<float>(y) * tileSize;

                // Clip edges to arena bounds
                const float w = std::min(tileSize - 2.f, (m_bounds.left + m_size.x) - px - 1.f);
                const float h = std::min(tileSize - 2.f, (m_bounds.top + m_size.y) - py - 1.f);
                if (w <= 0.f || h <= 0.f) continue;

                tileShape.setSize({w, h});
                tileShape.setPosition(px + 1.f, py + 1.f);

                // Subtle color alternate
                if ((x + y) % 2 == 0) {
                    tileShape.setFillColor(sf::Color(30, 36, 50));
                } else {
                    tileShape.setFillColor(sf::Color(24, 29, 41));
                }
                target.draw(tileShape);
            }
        }
    }

    // 3. Central Arcane Sanctuary Runes
    // Outer rune circle
    draw::circle(target, m_center, 180.f, sf::Color::Transparent, sf::Color(60, 90, 140, 110), 2.5f, 48);
    // Inner rune circle
    draw::circle(target, m_center, 105.f, sf::Color::Transparent, sf::Color(80, 120, 180, 130), 2.0f, 40);
    // Inner dais fill
    draw::circle(target, m_center, 40.f, sf::Color(38, 52, 75, 100), sf::Color(110, 160, 235, 170), 2.0f, 24);

    // Cardinal rune rays (North, South, East, West)
    draw::line(target, m_center + sf::Vector2f(0.f, -180.f), m_center + sf::Vector2f(0.f, -40.f), 2.f, sf::Color(70, 110, 170, 120));
    draw::line(target, m_center + sf::Vector2f(0.f, 40.f), m_center + sf::Vector2f(0.f, 180.f), 2.f, sf::Color(70, 110, 170, 120));
    draw::line(target, m_center + sf::Vector2f(-180.f, 0.f), m_center + sf::Vector2f(-40.f, 0.f), 2.f, sf::Color(70, 110, 170, 120));
    draw::line(target, m_center + sf::Vector2f(40.f, 0.f), m_center + sf::Vector2f(180.f, 0.f), 2.f, sf::Color(70, 110, 170, 120));

    // Diagonal rune nodes
    for (int i = 0; i < 4; ++i) {
        const float angle = math::kPi * 0.25f + static_cast<float>(i) * (math::kPi * 0.5f);
        const sf::Vector2f nodePos = m_center + math::fromAngle(angle) * 142.f;
        draw::circle(target, nodePos, 7.f, sf::Color(45, 70, 110, 160), sf::Color(90, 150, 230, 200), 1.5f);
    }

    // 4. Fortified Boundary Wall
    constexpr float wallThickness = 14.f;

    // Top wall
    draw::line(target, {m_bounds.left - wallThickness, m_bounds.top - wallThickness * 0.5f},
                       {m_bounds.left + m_size.x + wallThickness, m_bounds.top - wallThickness * 0.5f},
               wallThickness, sf::Color(44, 52, 70));
    // Bottom wall
    draw::line(target, {m_bounds.left - wallThickness, m_bounds.top + m_size.y + wallThickness * 0.5f},
                       {m_bounds.left + m_size.x + wallThickness, m_bounds.top + m_size.y + wallThickness * 0.5f},
               wallThickness, sf::Color(44, 52, 70));
    // Left wall
    draw::line(target, {m_bounds.left - wallThickness * 0.5f, m_bounds.top},
                       {m_bounds.left - wallThickness * 0.5f, m_bounds.top + m_size.y},
               wallThickness, sf::Color(44, 52, 70));
    // Right wall
    draw::line(target, {m_bounds.left + m_size.x + wallThickness * 0.5f, m_bounds.top},
                       {m_bounds.left + m_size.x + wallThickness * 0.5f, m_bounds.top + m_size.y},
               wallThickness, sf::Color(44, 52, 70));

    // Glowing inner boundary line
    sf::RectangleShape innerGlow(m_size);
    innerGlow.setPosition(m_bounds.left, m_bounds.top);
    innerGlow.setFillColor(sf::Color::Transparent);
    innerGlow.setOutlineColor(sf::Color(65, 125, 215, 180));
    innerGlow.setOutlineThickness(2.0f);
    target.draw(innerGlow);

    // 5. Four Corner Bastions (Pillars with glowing crystal cores)
    const std::array<sf::Vector2f, 4> corners = {{
        {m_bounds.left, m_bounds.top},
        {m_bounds.left + m_size.x, m_bounds.top},
        {m_bounds.left, m_bounds.top + m_size.y},
        {m_bounds.left + m_size.x, m_bounds.top + m_size.y}
    }};

    for (const auto& corner : corners) {
        // Outer stone bastion
        draw::circle(target, corner, 26.f, sf::Color(42, 50, 68), sf::Color(25, 30, 42), 3.f);
        // Inner stone ring
        draw::circle(target, corner, 16.f, sf::Color(32, 40, 56), sf::Color(60, 80, 115), 1.5f);
        // Crystal power node
        draw::circle(target, corner, 7.f, sf::Color(90, 180, 255), sf::Color::White, 1.f);
    }
}

} // namespace ss
