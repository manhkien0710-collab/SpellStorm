#include "TestHelper.hpp"

#include "core/Arena.hpp"
#include "core/Camera.hpp"
#include "core/GameSession.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"
#include "entities/BossEnemy.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "environment/TileMap.hpp"
#include "spells/SpellRegistry.hpp"
#include "systems/PathfindingSystem.hpp"

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

    // Verify all 5 distributed bridge crossings across the world map
    const std::vector<int> bridgeYLevels = {13, 29, 64, 87, 111};
    for (int by : bridgeYLevels) {
        bool foundBridgeAtY = false;
        for (int x = 20; x < 65; ++x) {
            if (map.tileAt(x, by) == ss::TileType::BridgeH) {
                foundBridgeAtY = true;
                EXPECT_FALSE(map.isSolidTile(x, by)); // Bridges must allow traversal!
                const sf::Vector2f bridgeWorld = map.tileToWorld(x, by);
                EXPECT_TRUE(map.isTraversable(bridgeWorld, 12.f));
            }
        }
        EXPECT_TRUE(foundBridgeAtY);
    }

    // Verify obstacle density reduction: total decorations must be <= 300 (down from ~480)
    EXPECT_LE(map.decorations().size(), 300u);

    // Verify no solid obstacles within 3 tiles of any bridge crossing
    for (const auto& deco : map.decorations()) {
        if (!deco.isSolid) continue;
        const sf::Vector2i decoTile = map.worldToTile(deco.position);
        for (int by : bridgeYLevels) {
            if (std::abs(decoTile.y - by) <= 3) {
                // Must not be near bridge x range
                for (int x = 25; x <= 60; ++x) {
                    if (map.tileAt(x, by) == ss::TileType::BridgeH) {
                        EXPECT_GE(std::abs(decoTile.x - x), 3);
                    }
                }
            }
        }
    }
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

    ss::Camera camera(viewport, worldBounds, 1.0f);

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

void testCameraZoomAndMouseAiming()
{
    const sf::Vector2f viewport(1280.f, 720.f);
    const sf::FloatRect worldBounds(0.f, 0.f, 4096.f, 4096.f);

    ss::Camera camera(viewport, worldBounds, ss::Camera::kDefaultZoom);
    EXPECT_NEAR(camera.zoom(), ss::Camera::kDefaultZoom, 0.001f);

    // Zoom limits clamping
    camera.setZoom(0.1f); // Below min
    EXPECT_NEAR(camera.zoom(), ss::Camera::kMinZoom, 0.001f);

    camera.setZoom(10.f); // Above max
    EXPECT_NEAR(camera.zoom(), ss::Camera::kMaxZoom, 0.001f);

    // ZoomBy
    camera.setZoom(1.0f);
    camera.zoomBy(0.25f);
    EXPECT_NEAR(camera.targetZoom(), 1.25f, 0.001f);

    // Smooth zoom interpolation
    camera.update({2048.f, 2048.f}, 0.5f, 0.f);
    EXPECT_NEAR(camera.zoom(), 1.25f, 0.02f);

    // Clamping near world bounds under zoom
    // At zoom = 2.0f, viewSize is (640, 360), half viewSize is (320, 180)
    camera.setZoom(2.0f);
    camera.setCenter({10.f, 10.f});
    EXPECT_EQ(camera.center().x, 320.f);
    EXPECT_EQ(camera.center().y, 180.f);
    sf::FloatRect bounds2x = camera.visibleBounds();
    EXPECT_GE(bounds2x.left, 0.f);
    EXPECT_GE(bounds2x.top, 0.f);

    // Aiming at multiple zoom levels
    auto tileMap = std::make_shared<ss::TileMap>(42);
    ss::Arena arena(tileMap->center(), tileMap->worldSize());
    arena.setTileMap(tileMap);

    ss::SpellRegistry registry;
    auto testSpell = std::make_shared<ss::SpellDefinition>(
        "ZoomFire", "Aim Test",
        ss::ElementSet{ss::Element::Fire}, sf::Color::Red, sf::Color::Yellow,
        ss::ProjectileProfile{1.0f, 1.0f, 10.f, 2.0f, 1.0f, 0.f, 0, 0}
    );
    registry.registerBase(ss::Element::Fire, testSpell);

    ss::GameSession session(arena, registry, 42);

    for (float testZoom : {0.75f, 1.0f, 1.35f, 1.8f, 2.25f}) {
        session.camera().setZoom(testZoom);
        const sf::Vector2f playerPos = session.player().position();

        // Aim North-East (45 degrees)
        const sf::Vector2f targetWorld = playerPos + sf::Vector2f(200.f, -200.f);
        session.player().setAimTarget(targetWorld);
        const float expectedAngle = -ss::math::kPi * 0.25f;
        EXPECT_NEAR(session.player().aimAngle(), expectedAngle, 0.01f);

        // Cast spell and verify projectile direction matches targetWorld
        session.combatSystem().clearProjectiles();
        session.player().reset(playerPos);
        session.castSpell(targetWorld);
        EXPECT_EQ(session.combatSystem().projectiles().size(), 1u);
        if (!session.combatSystem().projectiles().empty()) {
            const auto& p = session.combatSystem().projectiles().front();
            const sf::Vector2f dir = p->direction();
            EXPECT_NEAR(dir.x, std::cos(expectedAngle), 0.01f);
            EXPECT_NEAR(dir.y, std::sin(expectedAngle), 0.01f);
        }
    }
}

void testCameraFrustumCoverage()
{
    auto map = std::make_shared<ss::TileMap>(42);
    ss::Arena arena(map->center(), map->worldSize());
    arena.setTileMap(map);

    const sf::Vector2f winSize(1280.f, 720.f);
    ss::Camera camera(winSize, arena.bounds(), 1.35f);

    for (float z : {0.75f, 1.0f, 1.35f, 2.0f, 2.25f}) {
        camera.setZoom(z);
        camera.setCenter(map->center());
        const sf::FloatRect vis = camera.visibleBounds();

        EXPECT_NEAR(vis.width, winSize.x / z, 0.5f);
        EXPECT_NEAR(vis.height, winSize.y / z, 0.5f);

        // Clamped visible area must never leak outside world bounds
        EXPECT_GE(vis.left, 0.f);
        EXPECT_GE(vis.top, 0.f);
        EXPECT_LE(vis.left + vis.width, map->worldSize().x);
        EXPECT_LE(vis.top + vis.height, map->worldSize().y);
    }
}

void testBridgeGeometryAndBossClearance()
{
    ss::TileMap map(42);

    // Verify all 5 river bridges have 5-tile width and allow Boss (r=50) clearance
    const std::vector<int> bridgeYCenters = {13, 30, 64, 87, 111};
    for (int cy : bridgeYCenters) {
        int bridgeSpan = 0;
        int bridgeStartX = -1;
        for (int x = 20; x <= 65; ++x) {
            if (map.tileAt(x, cy) == ss::TileType::BridgeH) {
                bridgeSpan++;
                if (bridgeStartX == -1) bridgeStartX = x;
            }
        }
        EXPECT_GE(bridgeSpan, 4);

        const float bridgeWorldY = static_cast<float>(cy) * 32.f + 16.f;
        const float bridgeWorldX = static_cast<float>(bridgeStartX + bridgeSpan / 2) * 32.f + 16.f;
        const sf::Vector2f bridgeCenter(bridgeWorldX, bridgeWorldY);

        // All entity tiers must be traversable on the bridge
        EXPECT_TRUE(map.isTraversable(bridgeCenter, 16.f));
        EXPECT_TRUE(map.isTraversable(bridgeCenter, 26.f));
        EXPECT_TRUE(map.isTraversable(bridgeCenter, ss::BossEnemy::kBossRadius)); // Boss fits!
    }
}

void testPathfindingSystemAStar()
{
    auto map = std::make_shared<ss::TileMap>(42);
    ss::Arena arena(map->center(), map->worldSize());
    arena.setTileMap(map);

    EXPECT_TRUE(arena.hasPathfinder());
    const auto* pf = arena.pathfinder();
    EXPECT_NE(pf, nullptr);

    // River separation without a direct bridge (y = 1500, between bridge at y=960 and y=2048)
    const sf::Vector2f westBank(1000.f, 1500.f);
    const sf::Vector2f eastBank(1700.f, 1500.f);

    EXPECT_TRUE(arena.isTraversable(westBank, 16.f));
    EXPECT_TRUE(arena.isTraversable(eastBank, 16.f));

    // Line of sight raycasting across water must report blocked
    EXPECT_FALSE(pf->hasLineOfSight(westBank, eastBank, 16.f));

    // A* for normal enemy routes via bridge
    auto path = pf->findPath(westBank, eastBank, 16.f);
    EXPECT_FALSE(path.empty());
    EXPECT_GE(path.size(), 2u);
    EXPECT_NEAR(path.back().x, eastBank.x, 48.f);
    EXPECT_NEAR(path.back().y, eastBank.y, 48.f);

    // A* for Boss enemy across bridge
    EXPECT_TRUE(arena.isTraversable(westBank, ss::BossEnemy::kBossRadius));
    EXPECT_TRUE(arena.isTraversable(eastBank, ss::BossEnemy::kBossRadius));
    auto bossPath = pf->findPath(westBank, eastBank, ss::BossEnemy::kBossRadius);
    EXPECT_FALSE(bossPath.empty());
    EXPECT_NEAR(bossPath.back().x, eastBank.x, 48.f);

    // Line of sight along clear stretch must report true
    EXPECT_TRUE(pf->hasLineOfSight(westBank, westBank + sf::Vector2f(100.f, 0.f), 16.f));

    // Fallback for unreachable water target (find actual deep water at y=46 between bridges)
    sf::Vector2f deepWater(0.f, 0.f);
    for (int x = 30; x <= 60; ++x) {
        if (map->tileAt(x, 46) == ss::TileType::Water) {
            deepWater = map->tileToWorld(x, 46) + sf::Vector2f(16.f, 16.f);
            break;
        }
    }
    EXPECT_GT(deepWater.x, 0.f);
    EXPECT_FALSE(arena.isTraversable(deepWater, 16.f));
    auto fallback = pf->findPath(westBank, deepWater, 16.f);
    EXPECT_FALSE(fallback.empty());
}

void testEnemyPathfindingSteering()
{
    auto map = std::make_shared<ss::TileMap>(42);
    ss::Arena arena(map->center(), map->worldSize());
    arena.setTileMap(map);

    // Separated by river at y = 1500
    const sf::Vector2f westBank(1000.f, 1500.f);
    const sf::Vector2f eastBank(1700.f, 1500.f);

    // 1. MeleeEnemy navigation across river
    ss::MeleeEnemy melee(westBank);
    melee.updateAI(0.1f, eastBank, &arena);
    EXPECT_FALSE(melee.navPath().empty());

    // 2. BossEnemy navigation across river
    ss::BossEnemy boss(westBank);
    boss.updateAI(0.1f, eastBank, &arena);
    EXPECT_FALSE(boss.navPath().empty());

    // 3. RangedEnemy navigation when blocked by river
    ss::RangedEnemy ranged(westBank);
    ranged.updateAI(0.1f, eastBank, &arena);
    EXPECT_FALSE(ranged.isInAttackRange()); // LOS blocked by river
    EXPECT_FALSE(ranged.navPath().empty());
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
    testCameraZoomAndMouseAiming();
    testCameraFrustumCoverage();
    testBridgeGeometryAndBossClearance();
    testPathfindingSystemAStar();
    testEnemyPathfindingSteering();
}
