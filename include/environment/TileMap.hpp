#pragma once

#include "core/Camera.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Color.hpp>
#include <SFML/Graphics/Font.hpp>
#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>

#include <cstdint>
#include <memory>
#include <vector>

namespace ss {

enum class TileType : uint8_t {
    Grass = 0,
    Dirt,
    Stone,
    Water,
    Sand,
    BridgeH,
    BridgeV
};

enum class DecoType : uint8_t {
    TreeOak = 0,
    TreePine,
    Rock,
    RockLarge,
    Bush,
    Log,
    Ruins,
    Tent,
    Campfire
};

struct DecoObject {
    DecoType type;
    sf::Vector2f position;           // World coordinates of object anchor (bottom center)
    float collisionRadius = 0.f;     // Solid obstacle collision radius (0 if passable)
    sf::Vector2f collisionOffset{0.f, 0.f}; // Offset from anchor to collision center
    bool isSolid = false;
    sf::FloatRect visualBounds;      // Precomputed AABB for fast frustum culling
};

// Represents a 2D tile-based explorable fantasy world matching the concept sheet:
// - Meandering river with wooden bridges
// - Winding dirt thoroughfares and landmark paths
// - Lush grassy plains, wildflower fields, and dense tree groves
// - Ancient stone ruins and campsite settlements
// - Solid obstacle collision with axis-aligned sliding resolution
// - Efficient frustum-culled rendering and corner minimap HUD
class TileMap {
public:
    static constexpr int kWidth = 128;
    static constexpr int kHeight = 128;
    static constexpr float kTileSize = 32.f;

    explicit TileMap(unsigned int seed = 42);

    int width() const { return kWidth; }
    int height() const { return kHeight; }
    float tileSize() const { return kTileSize; }
    sf::Vector2f worldSize() const { return {kWidth * kTileSize, kHeight * kTileSize}; }
    sf::FloatRect worldBounds() const { return {0.f, 0.f, kWidth * kTileSize, kHeight * kTileSize}; }
    sf::Vector2f center() const { return worldSize() * 0.5f; }

    // Generates map deterministically from seed
    void generate(unsigned int seed);

    // Grid queries
    TileType tileAt(int tx, int ty) const;
    void setTile(int tx, int ty, TileType type);
    bool isInsideGrid(int tx, int ty) const;

    // World coordinate helpers
    sf::Vector2i worldToTile(sf::Vector2f worldPos) const;
    sf::Vector2f tileToWorld(int tx, int ty) const;

    // Collision & Traversability
    bool isSolidTile(int tx, int ty) const;
    bool isWaterTile(int tx, int ty) const;
    bool isBridgeTile(int tx, int ty) const;
    bool isTraversable(sf::Vector2f pos, float radius) const;

    // Smooth collision resolution against world edges, water, and solid obstacles.
    // Preserves velocity along unblocked axes to allow natural sliding.
    sf::Vector2f resolveCollision(sf::Vector2f oldPos, sf::Vector2f newPos, float radius) const;

    // Environmental decorations
    const std::vector<DecoObject>& decorations() const { return m_decorations; }
    void addDecoration(DecoType type, sf::Vector2f pos, bool solid = true);

    // Layered rendering with viewport frustum culling
    void drawTerrain(sf::RenderTarget& target, const ResourceManager* resources, const Camera& camera) const;
    void drawDecorations(sf::RenderTarget& target, const ResourceManager* resources, const Camera& camera) const;
    void drawForeground(sf::RenderTarget& target, const ResourceManager* resources, const Camera& camera) const;

    // Corner minimap widget matching the concept reference sheet
    void drawMinimap(sf::RenderTarget& target, sf::Vector2f screenPos, sf::Vector2f size,
                     sf::Vector2f playerPos, const std::vector<sf::Vector2f>& enemyPositions,
                     const sf::Font* font = nullptr) const;

private:
    void placeRiver(unsigned int seed);
    void placePaths();
    void placeRuinsAndCamps();
    void placeNaturalDecorations(unsigned int seed);

    std::vector<TileType> m_tiles;
    std::vector<DecoObject> m_decorations;
};

} // namespace ss
