#include "environment/TileMap.hpp"
#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/Random.hpp"

#include <SFML/Graphics/RectangleShape.hpp>
#include <SFML/Graphics/Sprite.hpp>
#include <SFML/Graphics/Text.hpp>

#include <algorithm>
#include <cmath>

namespace ss {

TileMap::TileMap(unsigned int seed)
    : m_tiles(kWidth * kHeight, TileType::Grass)
{
    generate(seed);
}

bool TileMap::isInsideGrid(int tx, int ty) const
{
    return tx >= 0 && tx < kWidth && ty >= 0 && ty < kHeight;
}

TileType TileMap::tileAt(int tx, int ty) const
{
    if (!isInsideGrid(tx, ty)) {
        return TileType::Water; // Outside world is considered impassable water/void
    }
    return m_tiles[ty * kWidth + tx];
}

void TileMap::setTile(int tx, int ty, TileType type)
{
    if (isInsideGrid(tx, ty)) {
        m_tiles[ty * kWidth + tx] = type;
    }
}

sf::Vector2i TileMap::worldToTile(sf::Vector2f worldPos) const
{
    return {
        static_cast<int>(std::floor(worldPos.x / kTileSize)),
        static_cast<int>(std::floor(worldPos.y / kTileSize))
    };
}

sf::Vector2f TileMap::tileToWorld(int tx, int ty) const
{
    return {
        (static_cast<float>(tx) + 0.5f) * kTileSize,
        (static_cast<float>(ty) + 0.5f) * kTileSize
    };
}

bool TileMap::isSolidTile(int tx, int ty) const
{
    if (!isInsideGrid(tx, ty)) {
        return true;
    }
    const TileType t = tileAt(tx, ty);
    // Water blocks movement unless a bridge crosses over it
    return (t == TileType::Water);
}

bool TileMap::isWaterTile(int tx, int ty) const
{
    return tileAt(tx, ty) == TileType::Water;
}

bool TileMap::isBridgeTile(int tx, int ty) const
{
    const TileType t = tileAt(tx, ty);
    return t == TileType::BridgeH || t == TileType::BridgeV;
}

void TileMap::addDecoration(DecoType type, sf::Vector2f pos, bool solid)
{
    DecoObject obj;
    obj.type = type;
    obj.position = pos;
    obj.isSolid = solid;

    switch (type) {
        case DecoType::TreeOak:
            obj.collisionRadius = solid ? 12.f : 0.f;
            obj.collisionOffset = {0.f, -8.f}; // Base trunk collision
            obj.visualBounds = {pos.x - 16.f, pos.y - 48.f, 32.f, 48.f};
            break;
        case DecoType::TreePine:
            obj.collisionRadius = solid ? 10.f : 0.f;
            obj.collisionOffset = {0.f, -8.f};
            obj.visualBounds = {pos.x - 12.f, pos.y - 48.f, 24.f, 48.f};
            break;
        case DecoType::Rock:
            obj.collisionRadius = solid ? 9.f : 0.f;
            obj.collisionOffset = {0.f, -4.f};
            obj.visualBounds = {pos.x - 12.f, pos.y - 24.f, 24.f, 24.f};
            break;
        case DecoType::RockLarge:
            obj.collisionRadius = solid ? 14.f : 0.f;
            obj.collisionOffset = {0.f, -6.f};
            obj.visualBounds = {pos.x - 16.f, pos.y - 32.f, 32.f, 32.f};
            break;
        case DecoType::Bush:
            obj.collisionRadius = 0.f; // Passable
            obj.isSolid = false;
            obj.visualBounds = {pos.x - 10.f, pos.y - 20.f, 20.f, 20.f};
            break;
        case DecoType::Log:
            obj.collisionRadius = 0.f; // Passable
            obj.isSolid = false;
            obj.visualBounds = {pos.x - 14.f, pos.y - 18.f, 28.f, 18.f};
            break;
        case DecoType::Ruins:
            obj.collisionRadius = solid ? 14.f : 0.f;
            obj.collisionOffset = {0.f, -10.f};
            obj.visualBounds = {pos.x - 16.f, pos.y - 48.f, 32.f, 48.f};
            break;
        case DecoType::Tent:
            obj.collisionRadius = solid ? 16.f : 0.f;
            obj.collisionOffset = {0.f, -8.f};
            obj.visualBounds = {pos.x - 20.f, pos.y - 36.f, 40.f, 36.f};
            break;
        case DecoType::Campfire:
            obj.collisionRadius = solid ? 10.f : 0.f;
            obj.collisionOffset = {0.f, -4.f};
            obj.visualBounds = {pos.x - 12.f, pos.y - 24.f, 24.f, 24.f};
            break;
    }

    m_decorations.push_back(obj);
}

bool TileMap::isTraversable(sf::Vector2f pos, float radius) const
{
    // 1. World bounds containment check
    const float mapW = kWidth * kTileSize;
    const float mapH = kHeight * kTileSize;
    if (pos.x - radius < 0.f || pos.x + radius > mapW ||
        pos.y - radius < 0.f || pos.y + radius > mapH) {
        return false;
    }

    // 2. Check nearby tiles for impassable water
    const int minTx = std::max(0, static_cast<int>(std::floor((pos.x - radius) / kTileSize)));
    const int maxTx = std::min(kWidth - 1, static_cast<int>(std::floor((pos.x + radius) / kTileSize)));
    const int minTy = std::max(0, static_cast<int>(std::floor((pos.y - radius) / kTileSize)));
    const int maxTy = std::min(kHeight - 1, static_cast<int>(std::floor((pos.y + radius) / kTileSize)));

    for (int ty = minTy; ty <= maxTy; ++ty) {
        for (int tx = minTx; tx <= maxTx; ++tx) {
            if (isSolidTile(tx, ty)) {
                const float tileLeft = static_cast<float>(tx) * kTileSize;
                const float tileTop = static_cast<float>(ty) * kTileSize;
                // Circle-AABB overlap test
                const float closestX = std::clamp(pos.x, tileLeft, tileLeft + kTileSize);
                const float closestY = std::clamp(pos.y, tileTop, tileTop + kTileSize);
                const float dx = pos.x - closestX;
                const float dy = pos.y - closestY;
                if ((dx * dx + dy * dy) < (radius * radius)) {
                    return false;
                }
            }
        }
    }

    // 3. Check solid obstacle collision
    for (const auto& deco : m_decorations) {
        if (!deco.isSolid || deco.collisionRadius <= 0.f) {
            continue;
        }
        const sf::Vector2f colCenter = deco.position + deco.collisionOffset;
        const float totalRadius = radius + deco.collisionRadius;
        if (math::distanceSq(pos, colCenter) < totalRadius * totalRadius) {
            return false;
        }
    }

    return true;
}

sf::Vector2f TileMap::resolveCollision(sf::Vector2f oldPos, sf::Vector2f newPos, float radius) const
{
    const float mapW = kWidth * kTileSize;
    const float mapH = kHeight * kTileSize;

    // Clamp candidate to world bounds
    sf::Vector2f candidate = newPos;
    candidate.x = std::clamp(candidate.x, radius, mapW - radius);
    candidate.y = std::clamp(candidate.y, radius, mapH - radius);

    if (isTraversable(candidate, radius)) {
        return candidate;
    }

    // Axis-aligned sliding test: try moving on X only
    sf::Vector2f tryX(candidate.x, oldPos.y);
    if (isTraversable(tryX, radius)) {
        return tryX;
    }

    // Try moving on Y only
    sf::Vector2f tryY(oldPos.x, candidate.y);
    if (isTraversable(tryY, radius)) {
        return tryY;
    }

    // Blocked on both axes: retain old position
    return oldPos;
}

void TileMap::generate(unsigned int seed)
{
    m_tiles.assign(kWidth * kHeight, TileType::Grass);
    m_decorations.clear();

    placeRiver(seed);
    placePaths();
    placeRuinsAndCamps();
    placeNaturalDecorations(seed);
}

void TileMap::placeRiver(unsigned int seed)
{
    Random rng(seed != 0 ? seed : 42);

    // Meandering river from North to South
    for (int y = 0; y < kHeight; ++y) {
        const float fy = static_cast<float>(y);
        // Smooth undulating river center around x = 42
        const float riverCenter = 42.0f + std::sin(fy * 0.08f) * 7.5f + std::cos(fy * 0.16f) * 3.0f;
        const int rc = static_cast<int>(std::round(riverCenter));

        // Water channel (4 tiles wide: rc - 2 to rc + 1)
        for (int dx = -2; dx <= 1; ++dx) {
            const int tx = rc + dx;
            setTile(tx, y, TileType::Water);
        }

        // Sandy riverbanks (2 tiles on each side for smooth, gentle terrain transition)
        for (int d = 1; d <= 2; ++d) {
            if (tileAt(rc - 2 - d, y) != TileType::Water) setTile(rc - 2 - d, y, TileType::Sand);
            if (tileAt(rc + 1 + d, y) != TileType::Water) setTile(rc + 1 + d, y, TileType::Sand);
        }
    }
}

void TileMap::placePaths()
{
    // Helper lambda to construct a complete, valid wooden river bridge crossing
    // with connecting flared dirt approach paths on both riverbanks
    auto buildRiverBridge = [this](int startY, int endY) {
        int minWaterX = 999;
        int maxWaterX = -1;
        for (int y = startY; y <= endY; ++y) {
            for (int x = 20; x <= 65; ++x) {
                if (isWaterTile(x, y) || tileAt(x, y) == TileType::Sand) {
                    minWaterX = std::min(minWaterX, x);
                    maxWaterX = std::max(maxWaterX, x);
                }
            }
        }
        if (minWaterX <= maxWaterX && minWaterX >= 0) {
            // Bridge spans the full rectangular channel
            for (int y = startY; y <= endY; ++y) {
                for (int x = minWaterX; x <= maxWaterX; ++x) {
                    setTile(x, y, TileType::BridgeH);
                }
            }
            // Flared dirt approach landings on both riverbanks (startY - 1 to endY + 1)
            // extending 4 tiles inland, ensuring no sharp water notches or dead-end pockets
            for (int y = startY - 1; y <= endY + 1; ++y) {
                if (y < 0 || y >= kHeight) continue;
                for (int d = 1; d <= 4; ++d) {
                    const int westX = minWaterX - d;
                    const int eastX = maxWaterX + d;
                    if (westX >= 0) {
                        setTile(westX, y, TileType::Dirt);
                    }
                    if (eastX < kWidth) {
                        setTile(eastX, y, TileType::Dirt);
                    }
                }
            }
        }
    };

    // 1. Main East-West Highway across entire world at y = 62..66 (5 tiles wide)
    for (int x = 0; x < kWidth; ++x) {
        for (int dy = -2; dy <= 2; ++dy) {
            const int y = 64 + dy;
            if (isWaterTile(x, y)) {
                setTile(x, y, TileType::BridgeH);
            } else {
                setTile(x, y, TileType::Dirt);
            }
        }
    }

    // 2. North-South Trail at x = 78..80
    for (int y = 0; y < kHeight; ++y) {
        for (int dx = -1; dx <= 1; ++dx) {
            const int x = 79 + dx;
            if (tileAt(x, y) != TileType::BridgeH) {
                setTile(x, y, TileType::Dirt);
            }
        }
    }

    // 3. Path to Ancient Ruins (branches from (79, 64) towards (88, 35))
    for (int y = 35; y <= 64; ++y) {
        const float t = static_cast<float>(y - 35) / 29.0f;
        const int px = static_cast<int>(std::round(88.0f - t * 9.0f));
        setTile(px, y, TileType::Dirt);
        setTile(px + 1, y, TileType::Dirt);
    }

    // 4. Path to South-East Campsite (branches towards (95, 88))
    for (int y = 64; y <= 88; ++y) {
        const float t = static_cast<float>(y - 64) / 24.0f;
        const int px = static_cast<int>(std::round(79.0f + t * 16.0f));
        setTile(px, y, TileType::Dirt);
        setTile(px + 1, y, TileType::Dirt);
    }

    // 5. Build distributed 5-tile-wide river bridge network across the entire world:
    // Large enough to comfortably accommodate the colossal Boss (r=50) and ordinary enemies
    // - Far North Crossing (y = 11..15, 5 tiles wide)
    buildRiverBridge(11, 15);
    // - Northern Crossing (y = 28..32, 5 tiles wide)
    buildRiverBridge(28, 32);
    // - Central Highway Crossing (y = 62..66, 5 tiles wide)
    buildRiverBridge(62, 66);
    // - Southern Crossing (y = 85..89, 5 tiles wide)
    buildRiverBridge(85, 89);
    // - Far South Crossing (y = 109..113, 5 tiles wide)
    buildRiverBridge(109, 113);
}

void TileMap::placeRuinsAndCamps()
{
    // 1. Ancient Stone Ruins Sanctuary at (88, 35)
    for (int dy = -6; dy <= 6; ++dy) {
        for (int dx = -6; dx <= 6; ++dx) {
            const int tx = 88 + dx;
            const int ty = 35 + dy;
            if (dx * dx + dy * dy <= 36) {
                setTile(tx, ty, TileType::Stone);
            }
        }
    }

    // Place stone pillars & ancient ruins props around the sanctuary
    addDecoration(DecoType::Ruins, tileToWorld(84, 30), true);
    addDecoration(DecoType::Ruins, tileToWorld(92, 30), true);
    addDecoration(DecoType::Ruins, tileToWorld(84, 40), true);
    addDecoration(DecoType::Ruins, tileToWorld(92, 40), true);
    addDecoration(DecoType::RockLarge, tileToWorld(88, 29), true);
    addDecoration(DecoType::RockLarge, tileToWorld(88, 41), true);

    // 2. Wilderness Campsite at (95, 88)
    for (int dy = -4; dy <= 4; ++dy) {
        for (int dx = -4; dx <= 4; ++dx) {
            const int tx = 95 + dx;
            const int ty = 88 + dy;
            if (dx * dx + dy * dy <= 16) {
                setTile(tx, ty, TileType::Dirt);
            }
        }
    }

    // Campsite props: tents, campfire, logs
    addDecoration(DecoType::Tent, tileToWorld(92, 86), true);
    addDecoration(DecoType::Tent, tileToWorld(98, 86), true);
    addDecoration(DecoType::Campfire, tileToWorld(95, 88), true);
    addDecoration(DecoType::Log, tileToWorld(95, 90), false);
    addDecoration(DecoType::Log, tileToWorld(93, 89), false);
}

void TileMap::placeNaturalDecorations(unsigned int seed)
{
    Random rng(seed + 101);

    // Center clearing safe-zone (radius ~12 tiles around (64, 64))
    const float centerWorldX = 64.f * kTileSize;
    const float centerWorldY = 64.f * kTileSize;
    const float safeRadiusSq = (12.f * kTileSize) * (12.f * kTileSize);

    // Track placed solid obstacles to prevent clumping and choking choke-points
    std::vector<sf::Vector2f> placedSolids;
    placedSolids.reserve(200);

    // Reduced candidate count (from 480 down to 290, ~40% reduction)
    for (int i = 0; i < 290; ++i) {
        const int tx = static_cast<int>(rng.range(3, kWidth - 4));
        const int ty = static_cast<int>(rng.range(3, kHeight - 4));
        const TileType t = tileAt(tx, ty);

        // Do not place on water, bridge, stone plaza, or dirt roads
        if (t != TileType::Grass) {
            continue;
        }

        const sf::Vector2f worldPos = tileToWorld(tx, ty);
        if (math::distanceSq(worldPos, {centerWorldX, centerWorldY}) < safeRadiusSq) {
            continue; // Keep player starting arena open and traversable
        }

        // Keep 4 tiles clear around any river bridge to ensure unblocked river crossing access
        bool nearBridge = false;
        for (int bdy = -4; bdy <= 4 && !nearBridge; ++bdy) {
            for (int bdx = -4; bdx <= 4; ++bdx) {
                if (isBridgeTile(tx + bdx, ty + bdy)) {
                    nearBridge = true;
                    break;
                }
            }
        }
        if (nearBridge) {
            continue;
        }

        const float roll = rng.range(0.f, 1.f);
        // Balanced distribution: 40% passable decorative flora (bushes & logs),
        // 60% solid obstacles with anti-clumping spacing
        if (roll < 0.25f) { // Passable bush
            addDecoration(DecoType::Bush, worldPos, false);
        } else if (roll < 0.40f) { // Passable fallen log
            addDecoration(DecoType::Log, worldPos, false);
        } else {
            // Solid obstacle: verify minimum spacing from previously placed solid obstacles (>= 44 units)
            bool tooClose = false;
            for (const auto& existing : placedSolids) {
                if (math::distanceSq(worldPos, existing) < (44.f * 44.f)) {
                    tooClose = true;
                    break;
                }
            }
            if (tooClose) {
                // Turn into passable bush rather than leaving dead space or causing a collision trap
                addDecoration(DecoType::Bush, worldPos, false);
                continue;
            }

            placedSolids.push_back(worldPos);
            const float solidRoll = rng.range(0.f, 1.f);
            if (solidRoll < 0.45f) {
                addDecoration(DecoType::TreeOak, worldPos, true);
            } else if (solidRoll < 0.75f) {
                addDecoration(DecoType::TreePine, worldPos, true);
            } else if (solidRoll < 0.90f) {
                addDecoration(DecoType::Rock, worldPos, true);
            } else {
                addDecoration(DecoType::RockLarge, worldPos, true);
            }
        }
    }
}

void TileMap::drawTerrain(sf::RenderTarget& target, const ResourceManager* resources, const Camera& camera) const
{
    const sf::FloatRect vis = camera.visibleBounds();
    const int minTx = std::max(0, static_cast<int>(std::floor(vis.left / kTileSize)) - 2);
    const int maxTx = std::min(kWidth - 1, static_cast<int>(std::ceil((vis.left + vis.width) / kTileSize)) + 2);
    const int minTy = std::max(0, static_cast<int>(std::floor(vis.top / kTileSize)) - 2);
    const int maxTy = std::min(kHeight - 1, static_cast<int>(std::ceil((vis.top + vis.height) / kTileSize)) + 2);

    // Reusable sprite for rendering tiles
    sf::Sprite sprite;
    sf::RectangleShape fallbackTile({kTileSize, kTileSize});

    for (int ty = minTy; ty <= maxTy; ++ty) {
        for (int tx = minTx; tx <= maxTx; ++tx) {
            const TileType t = tileAt(tx, ty);
            const sf::Vector2f pos(static_cast<float>(tx) * kTileSize, static_cast<float>(ty) * kTileSize);

            TextureId texId = TextureId::TileGrass;
            sf::Color fallbackColor(55, 125, 45);

            switch (t) {
                case TileType::Grass:
                    texId = TextureId::TileGrass;
                    fallbackColor = ((tx + ty) % 2 == 0) ? sf::Color(55, 125, 45) : sf::Color(50, 115, 40);
                    break;
                case TileType::Dirt:
                    texId = TextureId::TileDirt;
                    fallbackColor = sf::Color(115, 95, 60);
                    break;
                case TileType::Stone:
                    texId = TextureId::TileStone;
                    fallbackColor = sf::Color(75, 85, 95);
                    break;
                case TileType::Water:
                    texId = TextureId::TileWater;
                    fallbackColor = sf::Color(30, 95, 160);
                    break;
                case TileType::Sand:
                    texId = TextureId::TileSand;
                    fallbackColor = sf::Color(165, 145, 105);
                    break;
                case TileType::BridgeH:
                    texId = TextureId::TileBridgeH;
                    fallbackColor = sf::Color(135, 95, 55);
                    break;
                case TileType::BridgeV:
                    texId = TextureId::TileBridgeV;
                    fallbackColor = sf::Color(135, 95, 55);
                    break;
            }

            const sf::Texture* tex = resources ? resources->getTexture(texId) : nullptr;
            if (tex) {
                sprite.setTexture(*tex, true);
                sprite.setPosition(pos);
                const sf::Vector2u sz = tex->getSize();
                if (sz.x > 0 && sz.y > 0) {
                    sprite.setScale(kTileSize / static_cast<float>(sz.x), kTileSize / static_cast<float>(sz.y));
                }
                target.draw(sprite);
            } else {
                fallbackTile.setPosition(pos);
                fallbackTile.setFillColor(fallbackColor);
                target.draw(fallbackTile);
            }
        }
    }
}

void TileMap::drawDecorations(sf::RenderTarget& target, const ResourceManager* resources, const Camera& camera) const
{
    for (const auto& deco : m_decorations) {
        if (!camera.isVisible(deco.visualBounds)) {
            continue; // Frustum cull off-screen objects
        }

        TextureId texId = TextureId::DecoTreeOak;
        switch (deco.type) {
            case DecoType::TreeOak:    texId = TextureId::DecoTreeOak; break;
            case DecoType::TreePine:   texId = TextureId::DecoTreePine; break;
            case DecoType::Rock:       texId = TextureId::DecoRock; break;
            case DecoType::RockLarge:  texId = TextureId::DecoRockLarge; break;
            case DecoType::Bush:       texId = TextureId::DecoBush; break;
            case DecoType::Log:        texId = TextureId::DecoLog; break;
            case DecoType::Ruins:      texId = TextureId::DecoRuins; break;
            case DecoType::Tent:       texId = TextureId::DecoTent; break;
            case DecoType::Campfire:   texId = TextureId::DecoCampfire; break;
        }

        const sf::Texture* tex = resources ? resources->getTexture(texId) : nullptr;
        if (tex) {
            sf::Sprite sprite(*tex);
            const sf::Vector2u sz = tex->getSize();
            // Anchor at bottom center
            sprite.setOrigin(sz.x * 0.5f, static_cast<float>(sz.y));
            sprite.setPosition(deco.position);
            target.draw(sprite);
        } else {
            // Procedural fallback
            if (deco.type == DecoType::TreeOak || deco.type == DecoType::TreePine) {
                draw::circle(target, deco.position + deco.collisionOffset, 16.f, sf::Color(35, 90, 30), sf::Color(20, 60, 20), 2.f);
            } else if (deco.type == DecoType::Rock || deco.type == DecoType::RockLarge) {
                draw::circle(target, deco.position, 12.f, sf::Color(80, 85, 90), sf::Color(55, 60, 65), 1.5f);
            } else if (deco.type == DecoType::Ruins) {
                sf::RectangleShape r({24.f, 36.f});
                r.setOrigin(12.f, 36.f);
                r.setPosition(deco.position);
                r.setFillColor(sf::Color(100, 105, 115));
                r.setOutlineColor(sf::Color(65, 70, 80));
                r.setOutlineThickness(1.5f);
                target.draw(r);
            } else {
                draw::circle(target, deco.position, 8.f, sf::Color(120, 95, 60));
            }
        }
    }
}

void TileMap::drawForeground(sf::RenderTarget& /*target*/, const ResourceManager* /*resources*/, const Camera& /*camera*/) const
{
    // Reserved for canopy overlay depth if desired in future milestones
}

void TileMap::drawMinimap(sf::RenderTarget& target, sf::Vector2f screenPos, sf::Vector2f size,
                          sf::Vector2f playerPos, const std::vector<sf::Vector2f>& enemyPositions,
                          const sf::Font* font) const
{
    // Outer frame & backdrop matching dark fantasy concept HUD
    sf::RectangleShape bg(size);
    bg.setPosition(screenPos);
    bg.setFillColor(sf::Color(12, 16, 24, 215));
    bg.setOutlineColor(sf::Color(65, 95, 140, 220));
    bg.setOutlineThickness(2.0f);
    target.draw(bg);

    const float scaleX = size.x / (kWidth * kTileSize);
    const float scaleY = size.y / (kHeight * kTileSize);

    // Sample terrain layout on minimap
    constexpr int step = 4;
    sf::RectangleShape miniTile({size.x / (kWidth / step), size.y / (kHeight / step)});

    for (int ty = 0; ty < kHeight; ty += step) {
        for (int tx = 0; tx < kWidth; tx += step) {
            const TileType t = tileAt(tx, ty);
            if (t == TileType::Water) {
                miniTile.setFillColor(sf::Color(45, 115, 195, 180));
                miniTile.setPosition(screenPos.x + tx * kTileSize * scaleX,
                                     screenPos.y + ty * kTileSize * scaleY);
                target.draw(miniTile);
            } else if (t == TileType::Dirt || t == TileType::BridgeH || t == TileType::BridgeV) {
                miniTile.setFillColor(sf::Color(120, 100, 70, 150));
                miniTile.setPosition(screenPos.x + tx * kTileSize * scaleX,
                                     screenPos.y + ty * kTileSize * scaleY);
                target.draw(miniTile);
            } else if (t == TileType::Stone) {
                miniTile.setFillColor(sf::Color(140, 150, 170, 180));
                miniTile.setPosition(screenPos.x + tx * kTileSize * scaleX,
                                     screenPos.y + ty * kTileSize * scaleY);
                target.draw(miniTile);
            }
        }
    }

    // Enemies as small red dots
    for (const auto& ePos : enemyPositions) {
        const sf::Vector2f p(
            screenPos.x + ePos.x * scaleX,
            screenPos.y + ePos.y * scaleY
        );
        draw::circle(target, p, 2.0f, sf::Color(255, 60, 60));
    }

    // Player as a bright pulsing cyan marker with crosshair ring
    const sf::Vector2f pMarker(
        screenPos.x + playerPos.x * scaleX,
        screenPos.y + playerPos.y * scaleY
    );
    draw::circle(target, pMarker, 3.5f, sf::Color(60, 220, 255), sf::Color::White, 1.0f);

    // Label banner
    if (font) {
        sf::Text lbl;
        lbl.setFont(*font);
        lbl.setString("MAP [OVERWORLD]");
        lbl.setCharacterSize(10);
        lbl.setFillColor(sf::Color(160, 190, 230));
        lbl.setPosition(screenPos.x + 6.f, screenPos.y + 4.f);
        target.draw(lbl);
    }
}

} // namespace ss
