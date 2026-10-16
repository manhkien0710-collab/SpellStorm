#pragma once

#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>

namespace ss {

// Manages the arena geometry, playable boundaries, and procedural rendering.
// Provides spatial clamping and collision boundary queries for Player and Enemies.
class Arena {
public:
    Arena(sf::Vector2f center, sf::Vector2f size);

    const sf::FloatRect& bounds() const { return m_bounds; }
    sf::Vector2f center() const { return m_center; }
    sf::Vector2f size() const { return m_size; }

    // Clamps an entity's position so its circular bounding volume (radius)
    // stays strictly inside the playable arena bounds.
    sf::Vector2f clampPosition(sf::Vector2f position, float radius) const;

    // Checks if a circular entity is completely or partially within the arena.
    bool contains(sf::Vector2f position, float radius = 0.f) const;

    // Procedural rendering using SFML geometry:
    // - Deep void backdrop outside the arena walls
    // - Flagstone floor with subtle checkered tone variation
    // - Concentric arcane sanctuary runes in the center
    // - Fortified stone boundary walls and glowing runic trim
    // - Corner bastions with arcane energy cores
    void draw(sf::RenderTarget& target, const class ResourceManager* resources = nullptr) const;

private:
    sf::Vector2f m_center;
    sf::Vector2f m_size;
    sf::FloatRect m_bounds;
};

} // namespace ss
