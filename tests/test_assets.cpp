#include "TestHelper.hpp"

#include "core/Arena.hpp"
#include "core/GameSession.hpp"
#include "core/ResourceManager.hpp"
#include "entities/ExperiencePickup.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "spells/SpellRegistry.hpp"

#include <SFML/Graphics/RenderTexture.hpp>

void testAssetCandidatePaths()
{
    auto paths = ss::ResourceManager::candidatePaths("textures", "characters/wizard.png");
    EXPECT_TRUE(!paths.empty());

    // Should include relative assets/ and ../assets/
    bool hasRelative = false;
    for (const auto& p : paths) {
        if (p.find("assets/textures/characters/wizard.png") != std::string::npos) {
            hasRelative = true;
            break;
        }
    }
    EXPECT_TRUE(hasRelative);
}

void testFontLoadingAndOwnership()
{
    ss::ResourceManager resources;

    bool bodyLoaded = resources.loadFont(ss::FontId::Body, "DejaVuSans.ttf",
                                         "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf");
    EXPECT_TRUE(bodyLoaded);
    EXPECT_TRUE(resources.hasFont(ss::FontId::Body));
    EXPECT_TRUE(resources.getFont(ss::FontId::Body) != nullptr);

    // Repeated call should return true without reallocating
    const sf::Font* ptr1 = resources.getFont(ss::FontId::Body);
    bool reload = resources.loadFont(ss::FontId::Body, "DejaVuSans.ttf");
    EXPECT_TRUE(reload);
    const sf::Font* ptr2 = resources.getFont(ss::FontId::Body);
    EXPECT_EQ(ptr1, ptr2);

    // Bold & Title fonts
    resources.loadFont(ss::FontId::Bold, "DejaVuSans-Bold.ttf",
                       "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf");
    resources.loadFont(ss::FontId::Title, "DejaVuSerif-Bold.ttf",
                       "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf");
    EXPECT_TRUE(resources.hasFont(ss::FontId::Bold));
    EXPECT_TRUE(resources.hasFont(ss::FontId::Title));
}

void testTextureLoadingAndCaching()
{
    ss::ResourceManager resources;
    EXPECT_TRUE(resources.loadDefaultAssets());

    // Check all prioritized textures
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::Player));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::MeleeEnemy));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::RangedEnemy));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::TankEnemy));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ProjectileFire));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ProjectileIce));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ProjectileLightning));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ProjectileWind));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ProjectileEarth));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ProjectileEnemy));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::GemCyan));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::GemEmerald));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::GemGold));
    EXPECT_TRUE(resources.hasTexture(ss::TextureId::ArenaFloor));

    // Valid dimensions
    const sf::Texture* pTex = resources.getTexture(ss::TextureId::Player);
    EXPECT_TRUE(pTex != nullptr);
    if (pTex) {
        EXPECT_EQ(pTex->getSize().x, 64u);
        EXPECT_EQ(pTex->getSize().y, 32u);
    }

    const sf::Texture* mTex = resources.getTexture(ss::TextureId::MeleeEnemy);
    EXPECT_TRUE(mTex != nullptr);
    if (mTex) {
        EXPECT_EQ(mTex->getSize().x, 32u);
        EXPECT_EQ(mTex->getSize().y, 32u);
    }

    const sf::Texture* tTex = resources.getTexture(ss::TextureId::TankEnemy);
    EXPECT_TRUE(tTex != nullptr);
    if (tTex) {
        EXPECT_EQ(tTex->getSize().x, 48u);
        EXPECT_EQ(tTex->getSize().y, 48u);
    }

    // Repeated load does not duplicate
    EXPECT_TRUE(resources.loadTexture(ss::TextureId::Player, "characters/wizard.png"));
    EXPECT_EQ(resources.getTexture(ss::TextureId::Player), pTex);
}

void testMissingAssetGracefulFallback()
{
    ss::ResourceManager resources;

    // Loading a non-existent texture returns false gracefully (no throw, no crash)
    bool ok = resources.loadTexture(ss::TextureId::Player, "bogus_missing_path_xyz123.png");
    EXPECT_FALSE(ok);
    EXPECT_FALSE(resources.hasTexture(ss::TextureId::Player));
    EXPECT_TRUE(resources.getTexture(ss::TextureId::Player) == nullptr);

    // Named texture non-existent
    EXPECT_FALSE(resources.hasTexture("missing_named_texture"));
    EXPECT_TRUE(resources.getTexture("missing_named_texture") == nullptr);
}

void testSpriteSheetFrameMath()
{
    ss::ResourceManager resources;
    resources.loadTexture(ss::TextureId::Player, "characters/wizard.png");
    resources.registerSpriteSheet(ss::TextureId::Player, 32, 32, 2, 1, 2);

    const ss::SpriteSheet* sheet = resources.getSpriteSheet(ss::TextureId::Player);
    EXPECT_TRUE(sheet != nullptr);
    if (sheet) {
        EXPECT_EQ(sheet->frameWidth, 32);
        EXPECT_EQ(sheet->frameHeight, 32);
        EXPECT_EQ(sheet->totalFrames, 2);
    }

    // Frame 0: (0, 0, 32, 32)
    sf::IntRect r0 = resources.getFrameRect(ss::TextureId::Player, 0);
    EXPECT_EQ(r0.left, 0);
    EXPECT_EQ(r0.top, 0);
    EXPECT_EQ(r0.width, 32);
    EXPECT_EQ(r0.height, 32);

    // Frame 1: (32, 0, 32, 32)
    sf::IntRect r1 = resources.getFrameRect(ss::TextureId::Player, 1);
    EXPECT_EQ(r1.left, 32);
    EXPECT_EQ(r1.top, 0);
    EXPECT_EQ(r1.width, 32);
    EXPECT_EQ(r1.height, 32);

    // Frame 2 wraps around to frame 0
    sf::IntRect r2 = resources.getFrameRect(ss::TextureId::Player, 2);
    EXPECT_EQ(r2.left, 0);
    EXPECT_EQ(r2.top, 0);

    // Negative frame clamps to 0 safely
    sf::IntRect rNeg = resources.getFrameRect(ss::TextureId::Player, -5);
    EXPECT_EQ(rNeg.left, 0);
    EXPECT_EQ(rNeg.top, 0);
}

void testEntityDecoupledDimensionsAndDrawing()
{
    ss::ResourceManager resources;
    resources.loadDefaultAssets();

    sf::RenderTexture rt;
    if (!rt.create(400, 400)) {
        // Fallback for headless environments without full OpenGL framebuffers
        std::cout << "[Note] RenderTexture not available in headless context, skipping draw execution." << std::endl;
    } else {
        rt.clear(sf::Color::Black);

        // 1. Player
        ss::Player player({100.f, 100.f});
        EXPECT_EQ(player.radius(), 20.f);

        // Draw with nullptr (procedural fallback)
        player.draw(rt, nullptr);
        // Draw with resources (sprite rendering)
        player.draw(rt, &resources);

        // Collision radius must remain invariant!
        EXPECT_EQ(player.radius(), 20.f);

        // 2. MeleeEnemy (Preserve manually tuned speed 105.f and radius 16.f)
        ss::MeleeEnemy melee({150.f, 150.f});
        EXPECT_EQ(melee.radius(), 16.f);
        EXPECT_EQ(melee.baseMoveSpeed(), 105.f);

        melee.draw(rt, nullptr);
        melee.draw(rt, &resources);

        EXPECT_EQ(melee.radius(), 16.f);
        EXPECT_EQ(melee.baseMoveSpeed(), 105.f);

        // 3. RangedEnemy
        ss::RangedEnemy ranged({200.f, 200.f});
        EXPECT_EQ(ranged.radius(), 15.f);
        ranged.draw(rt, nullptr);
        ranged.draw(rt, &resources);
        EXPECT_EQ(ranged.radius(), 15.f);

        // 4. TankEnemy
        ss::TankEnemy tank({250.f, 250.f});
        EXPECT_EQ(tank.radius(), 26.f);
        EXPECT_EQ(tank.baseMoveSpeed(), 65.f);
        tank.draw(rt, nullptr);
        tank.draw(rt, &resources);
        EXPECT_EQ(tank.radius(), 26.f);

        // 5. Projectiles
        ss::Projectile proj({50.f, 50.f}, {1.f, 0.f}, 200.f, 10.f, 25.f, 2.f, 0,
                            ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow);
        EXPECT_EQ(proj.radius(), 10.f);
        proj.draw(rt, nullptr);
        proj.draw(rt, &resources);
        EXPECT_EQ(proj.radius(), 10.f);

        // 6. Experience Pickup
        ss::ExperiencePickup pickup({75.f, 75.f}, 10);
        EXPECT_EQ(pickup.radius(), 8.f);
        pickup.draw(rt, nullptr);
        pickup.draw(rt, &resources);
        EXPECT_EQ(pickup.radius(), 8.f);

        // 7. Arena
        ss::Arena arena({200.f, 200.f}, {380.f, 380.f});
        arena.draw(rt, nullptr);
        arena.draw(rt, &resources);

        rt.display();
    }
}

void testGameSessionRenderSafety()
{
    ss::ResourceManager resources;
    resources.loadDefaultAssets();

    ss::Arena arena({640.f, 360.f}, {1180.f, 630.f});
    ss::SpellRegistry registry;

    // Create a game session configured with resources
    ss::GameSession session(arena, registry, 12345, &resources);
    EXPECT_TRUE(session.resourceManager() == &resources);

    sf::RenderTexture rt;
    if (rt.create(640, 360)) {
        // Normal gameplay render
        session.render(rt, nullptr, &resources);

        // Level up modal render
        session.progression().addExp(50);
        session.render(rt, nullptr, &resources);

        // Game over screen render
        session.triggerGameOver();
        EXPECT_TRUE(session.isGameOver());
        session.render(rt, nullptr, &resources);
    }
}

void runAssetPipelineTests()
{
    std::cout << "--- Running Asset Pipeline & Visual Tests ---" << std::endl;
    testAssetCandidatePaths();
    testFontLoadingAndOwnership();
    testTextureLoadingAndCaching();
    testMissingAssetGracefulFallback();
    testSpriteSheetFrameMath();
    testEntityDecoupledDimensionsAndDrawing();
    testGameSessionRenderSafety();
}
