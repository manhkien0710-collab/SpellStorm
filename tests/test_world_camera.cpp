#include "TestHelper.hpp"

#include "core/Arena.hpp"
#include "core/Camera.hpp"
#include "core/GameSession.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "environment/TileMap.hpp"
#include "spells/SpellRegistry.hpp"

#include <cmath>
#include <iostream>
#include <memory>

void testTileMapGenerationAndDimensions()
{
    ss::TileMap map(42);

    EXPECT_EQ(map.width(), 128);
    EXPECT_EQ(map.height(), 128);
    EXPECT_EQ(map.tileSize(), 32.f);

    EXPECT_EQ(map.worldSize().x, 4096.f);
    EXPECT_EQ(map.worldSize().y, 4096.f);
    EXPECT_EQ(map.center().x, 2048.f);
    EXPECT_EQ(map.center().y, 2048.f);

    // Player spawn point at center must be traversable and clear of obstacles
    EXPECT_TRUE(map.isTraversable(map.center(), 20.f));

    // Outside bounds must not be traversable
    EXPECT_FALSE(map.isTraversable({-10.f, 2048.f}, 15.f));
    EXPECT_FALSE(map.isTraversable({4100.f, 2048.f}, 15.f));
    EXPECT_FALSE(map.isTraversable({2048.f, -10.f}, 15.f));
    EXPECT_FALSE(map.isTraversable({2048.f, 4100.f}, 15.f));
}

void testTileMapRiverAndBridges()
{
    ss::TileMap map(42);

    // River presence: check that water tiles exist between x=30 and x=55
    bool foundWater = false;
    for (int y = 10; y < 120; y += 10) {
        for (int x = 30; x < 55; ++x) {
            if (map.tileAt(x, y) == ss::TileType::Water) {
                foundWater = true;
                EXPECT_TRUE(map.isSolidTile(x, y)); // Water blocks traversal
                break;
            }
        }
    }
    EXPECT_TRUE(foundWater);

    // Bridge presence: check highway crossing at y=64
    bool foundBridgeH = false;
    for (int x = 30; x < 55; ++x) {
        if (map.tileAt(x, 64) == ss::TileType::BridgeH) {
            foundBridgeH = true;
            EXPECT_FALSE(map.isSolidTile(x, 64)); // Bridges must allow traversal!
            const sf::Vector2f bridgeWorld = map.tileToWorld(x, 64);
            EXPECT_TRUE(map.isTraversable(bridgeWorld, 12.f));
        }
    }
    EXPECT_TRUE(foundBridgeH);
}

void testTileMapCollisionSliding()
{
    ss::TileMap map(42);

    // Add a solid obstacle (large rock) at (2000.f, 2000.f)
    map.addDecoration(ss::DecoType::RockLarge, {2000.f, 2000.f}, true);

    const float r = 10.f;
    // Entity is directly to the right of the rock at (2030.f, 1994.f)
    const sf::Vector2f oldPos(2030.f, 1994.f);
    EXPECT_TRUE(map.isTraversable(oldPos, r));

    // Desired diagonal movement: left towards rock (blocked) and slightly down (clear)
    const sf::Vector2f desiredPos(2010.f, 1999.f);
    const sf::Vector2f resolved = map.resolveCollision(oldPos, desiredPos, r);

    // X should be held at oldPos.x (2030.f), Y should slide to desiredPos.y (1999.f)
    EXPECT_EQ(resolved.x, oldPos.x);
    EXPECT_EQ(resolved.y, desiredPos.y);
    EXPECT_TRUE(map.isTraversable(resolved, r));
}

void testCameraClampingAndBounds()
{
    const sf::Vector2f viewport(1280.f, 720.f);
    const sf::FloatRect worldBounds(0.f, 0.f, 4096.f, 4096.f);

    ss::Camera camera(viewport, worldBounds);

    // 1. Center of world
    camera.setCenter({2048.f, 2048.f});
    EXPECT_EQ(camera.center().x, 2048.f);
    EXPECT_EQ(camera.center().y, 2048.f);

    // 2. Clamping near top-left edge
    camera.setCenter({100.f, 50.f});
    EXPECT_EQ(camera.center().x, 640.f); // Half viewport width
    EXPECT_EQ(camera.center().y, 360.f); // Half viewport height

    sf::FloatRect vis = camera.visibleBounds();
    EXPECT_GE(vis.left, 0.f);
    EXPECT_GE(vis.top, 0.f);

    // 3. Clamping near bottom-right edge
    camera.setCenter({4000.f, 4050.f});
    EXPECT_EQ(camera.center().x, 4096.f - 640.f);
    EXPECT_EQ(camera.center().y, 4096.f - 360.f);

    vis = camera.visibleBounds();
    EXPECT_LE(vis.left + vis.width, 4096.f);
    EXPECT_LE(vis.top + vis.height, 4096.f);

    // 4. Frustum visibility queries
    EXPECT_TRUE(camera.isVisible(vis));
    EXPECT_TRUE(camera.isVisible(camera.center(), 20.f));
    EXPECT_FALSE(camera.isVisible({100.f, 100.f}, 20.f)); // Top-left corner is offscreen
}

void testWorldCoordinatesMouseAiming()
{
    auto tileMap = std::make_shared<ss::TileMap>(42);
    ss::Arena arena(tileMap->center(), tileMap->worldSize());
    arena.setTileMap(tileMap);

    ss::SpellRegistry registry;
    auto testSpell = std::make_shared<ss::SpellDefinition>(
        "TestFire", "Test Description",
        ss::ElementSet{ss::Element::Fire}, sf::Color::Red, sf::Color::Yellow,
        ss::ProjectileProfile{1.0f, 1.0f, 10.f, 2.0f, 1.0f, 0.f, 0, 0}
    );
    registry.registerBase(ss::Element::Fire, testSpell);

    ss::GameSession session(arena, registry, 42);

    const sf::Vector2f playerPos = session.player().position();
    EXPECT_EQ(playerPos.x, 2048.f);
    EXPECT_EQ(playerPos.y, 2048.f);

    // Mouse aiming to the East in world coordinates
    const sf::Vector2f targetWorld = playerPos + sf::Vector2f(150.f, 0.f);
    session.player().setAimTarget(targetWorld);
    EXPECT_NEAR(session.player().aimAngle(), 0.f, 0.01f);

    // Cast spell towards targetWorld
    session.castSpell(targetWorld);
    EXPECT_EQ(session.combatSystem().projectiles().size(), 1u);

    if (!session.combatSystem().projectiles().empty()) {
        const auto& p = session.combatSystem().projectiles().front();
        EXPECT_NEAR(p->direction().x, 1.f, 0.01f);
        EXPECT_NEAR(p->direction().y, 0.f, 0.01f);
        // Projectile position must be in world coordinates near player
        EXPECT_GE(p->position().x, playerPos.x);
        EXPECT_LE(p->position().x, playerPos.x + 50.f);
    }
}

void testAutoShotWorldTargeting()
{
    auto tileMap = std::make_shared<ss::TileMap>(42);
    ss::Arena arena(tileMap->center(), tileMap->worldSize());
    arena.setTileMap(tileMap);

    ss::SpellRegistry registry;
    ss::GameSession session(arena, registry, 42);
    session.setShootingMode(ss::ShootingMode::Auto);

    const sf::Vector2f playerPos = session.player().position();

    // Spawn 2 enemies at different distances in world coordinates
    session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(playerPos + sf::Vector2f(250.f, 0.f))); // Near
    session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(playerPos + sf::Vector2f(500.f, 0.f))); // Far

    ss::Enemy* target = session.findTargetEnemy();
    EXPECT_TRUE(target != nullptr);
    if (target) {
        EXPECT_EQ(target->position().x, playerPos.x + 250.f);
    }
}

void testNewArtAssetsLoading()
{
    ss::ResourceManager resources;
    EXPECT_TRUE(resources.loadDefaultAssets());

    // Verify all new tile textures
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileGrass));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileDirt));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileStone));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileWater));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileSand));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileBridgeH));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TileBridgeV));

    // Verify all decorative environment object textures
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoTreeOak));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoTreePine));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoRock));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoRockLarge));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoBush));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoLog));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoRuins));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoTent));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::DecoCampfire));

    // Verify directional player textures
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::PlayerDown));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::PlayerUp));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::PlayerLeft));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::PlayerRight));

    // Non-zero dimensions
    const sf::Texture* grassTex = resources.getTexture(ss::TextureId::TileGrass);
    EXPECT_TRUE(grassTex != nullptr);
    if (grassTex) {
        EXPECT_EQ(grassTex->getSize().x, 32u);
        EXPECT_EQ(grassTex->getSize().y, 32u);
    }

    const sf::Texture* treeTex = resources.getTexture(ss::TextureId::DecoTreeOak);
    EXPECT_TRUE(treeTex != nullptr);
    if (treeTex) {
        EXPECT_EQ(treeTex->getSize().x, 32u);
        EXPECT_EQ(treeTex->getSize().y, 48u);
    }
}

void runWorldCameraTests()
{
    std::cout << "--- Running Wide World, TileMap & Camera Tests ---" << std::endl;
    testTileMapGenerationAndDimensions();
    testTileMapRiverAndBridges();
    testTileMapCollisionSliding();
    testCameraClampingAndBounds();
    testWorldCoordinatesMouseAiming();
    testAutoShotWorldTargeting();
    testNewArtAssetsLoading();
}
