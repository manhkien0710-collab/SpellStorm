#pragma once

#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>
#include <memory>
#include <utility>

namespace ss {

// Manages the arena geometry, playable boundaries, and procedural rendering.
// Provides spatial clamping and collision boundary queries for Player and Enemies.
class Arena {
public:
    Arena(sf::Vector2f center, sf::Vector2f size);
    ~Arena();
    Arena(Arena&&) noexcept;
    Arena& operator=(Arena&&) noexcept;

    const sf::FloatRect& bounds() const { return m_bounds; }
    sf::Vector2f center() const { return m_center; }
    sf::Vector2f size() const { return m_size; }

    // Clamps an entity's position so its circular bounding volume (radius)
    // stays strictly inside the playable arena bounds.
    sf::Vector2f clampPosition(sf::Vector2f position, float radius) const;

    // Checks if a circular entity is completely or partially within the arena.
    bool contains(sf::Vector2f position, float radius = 0.f) const;

    // Optional TileMap integration for wide explorable worlds
    void setTileMap(std::shared_ptr<class TileMap> tileMap);
    const class TileMap* tileMap() const { return m_tileMap.get(); }
    class TileMap* tileMap() { return m_tileMap.get(); }
    bool hasTileMap() const { return m_tileMap != nullptr; }

    bool hasPathfinder() const { return m_pathfinder != nullptr; }
    const class PathfindingSystem* pathfinder() const { return m_pathfinder.get(); }
    class PathfindingSystem* pathfinder() { return m_pathfinder.get(); }

    // Unified collision queries (delegates to TileMap if attached, or uses bounds)
    bool isTraversable(sf::Vector2f position, float radius) const;
    sf::Vector2f resolveCollision(sf::Vector2f oldPos, sf::Vector2f newPos, float radius) const;

    // Procedural rendering using SFML geometry or attached TileMap:
    void draw(sf::RenderTarget& target, const class ResourceManager* resources = nullptr,
              const class Camera* camera = nullptr) const;

private:
    sf::Vector2f m_center;
    sf::Vector2f m_size;
    sf::FloatRect m_bounds;
    std::shared_ptr<class TileMap> m_tileMap;
    std::unique_ptr<class PathfindingSystem> m_pathfinder;
};

} // namespace ss
