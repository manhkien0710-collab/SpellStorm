#include "combat/Damage.hpp"
#include "combat/HealthComponent.hpp"
#include "combat/StatusEffect.hpp"
#include "core/Arena.hpp"
#include "core/DrawUtils.hpp"
#include "core/GameOptions.hpp"
#include "core/GameSession.hpp"
#include "core/MathUtils.hpp"
#include "core/Random.hpp"
#include "core/ResourceManager.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "spells/Element.hpp"
#include "spells/HitEffect.hpp"
#include "spells/SpellDefinition.hpp"
#include "spells/SpellRegistry.hpp"

#include <SFML/Graphics.hpp>
#include <iostream>
#include <memory>
#include <vector>

int main(int argc, char* argv[])
{
    ss::GameOptions options;
    bool headless = false;
    bool testLevelUp = false;
    bool testGameOver = false;
    bool testRestartFlow = false;
    bool testAutoShot = false;
    bool testShowcase = false;

    for (int i = 1; i < argc; ++i) {
        std::string arg = argv[i];
        if (arg == "--autoplay") {
            options.autoplay = true;
        } else if (arg == "--headless") {
            headless = true;
        } else if (arg == "--test-level-up") {
            testLevelUp = true;
        } else if (arg == "--test-game-over") {
            testGameOver = true;
        } else if (arg == "--test-auto-shot") {
            testAutoShot = true;
        } else if (arg == "--test-showcase") {
            testShowcase = true;
        } else if (arg == "--test-restart-flow") {
            testRestartFlow = true;
            options.autoplay = true;
        } else if (arg == "--screenshot" && i + 1 < argc) {
            options.screenshotDir = argv[++i];
        } else if (arg == "--quit-after" && i + 1 < argc) {
            options.quitAfter = std::stof(argv[++i]);
        }
    }

    std::cout << "===========================================\n"
              << "       Spell Storm - Arena Wizard\n"
              << "===========================================\n";

    // Initialize Resource Manager
    ss::ResourceManager resources;
    bool hasFont = resources.loadDefaultAssets();
    if (!hasFont) {
        std::cerr << "[Warning] Could not load font from assets or system.\n";
    }

    // Set up Spell Registry with fundamental elements & combinations
    ss::SpellRegistry registry;

    // 1. Base Spells
    auto fireSpell = std::make_shared<ss::SpellDefinition>(
        "Firebolt", "Ignites foes with continuous burning flame",
        ss::ElementSet{ss::Element::Fire}, sf::Color(255, 90, 20), sf::Color(255, 200, 50),
        ss::ProjectileProfile{1.0f, 1.2f, 9.f, 2.0f, 1.0f, 0.f, 0, 0}
    );
    fireSpell->addEffect(std::make_unique<ss::BurnEffect>(0.5f, 3.0f));
    registry.registerBase(ss::Element::Fire, fireSpell);

    auto iceSpell = std::make_shared<ss::SpellDefinition>(
        "Frost Shard", "Chills enemies, slowing movement by 50%",
        ss::ElementSet{ss::Element::Ice}, sf::Color(100, 210, 255), sf::Color(220, 245, 255),
        ss::ProjectileProfile{0.8f, 1.4f, 8.f, 2.0f, 0.8f, 0.f, 1, 0}
    );
    iceSpell->addEffect(std::make_unique<ss::SlowEffect>(0.5f, 2.5f));
    registry.registerBase(ss::Element::Ice, iceSpell);

    auto lightningSpell = std::make_shared<ss::SpellDefinition>(
        "Spark Arc", "Arches of high-voltage lightning",
        ss::ElementSet{ss::Element::Lightning}, sf::Color(255, 240, 70), sf::Color(255, 255, 200),
        ss::ProjectileProfile{1.1f, 1.8f, 7.f, 1.5f, 0.9f, 0.f, 0, 0}
    );
    auto chain = std::make_unique<ss::ChainEffect>(3, 160.f);
    chain->then(std::make_unique<ss::DamageEffect>(0.7f));
    lightningSpell->addEffect(std::move(chain));
    registry.registerBase(ss::Element::Lightning, lightningSpell);

    auto windSpell = std::make_shared<ss::SpellDefinition>(
        "Gale Burst", "Knocks enemies back with violent gusts",
        ss::ElementSet{ss::Element::Wind}, sf::Color(120, 255, 180), sf::Color(200, 255, 230),
        ss::ProjectileProfile{0.7f, 1.5f, 10.f, 1.8f, 0.7f, 0.f, 2, 0}
    );
    windSpell->addEffect(std::make_unique<ss::KnockbackEffect>(280.f));
    registry.registerBase(ss::Element::Wind, windSpell);

    auto earthSpell = std::make_shared<ss::SpellDefinition>(
        "Boulder Slam", "Heavy physical shockwave crushing armor",
        ss::ElementSet{ss::Element::Earth}, sf::Color(190, 130, 80), sf::Color(130, 85, 45),
        ss::ProjectileProfile{1.6f, 0.8f, 13.f, 2.2f, 1.4f, 0.f, 0, 0}
    );
    earthSpell->addEffect(std::make_unique<ss::KnockbackEffect>(180.f));
    registry.registerBase(ss::Element::Earth, earthSpell);

    // 2. Combination Spells
    auto firestorm = std::make_shared<ss::SpellDefinition>(
        "Firestorm (Fire + Wind)", "Catastrophic vortex burning a wide area",
        ss::ElementSet{ss::Element::Fire, ss::Element::Wind}, sf::Color(255, 80, 20), sf::Color(120, 255, 180),
        ss::ProjectileProfile{1.5f, 1.3f, 12.f, 2.0f, 1.2f, 0.f, 1, 0}
    );
    auto areaExplosion = std::make_unique<ss::AreaEffect>(90.f, true);
    areaExplosion->then(std::make_unique<ss::DamageEffect>(1.2f));
    areaExplosion->then(std::make_unique<ss::BurnEffect>(0.4f, 2.5f));
    firestorm->addEffect(std::move(areaExplosion));
    registry.registerCombination(ss::Element::Fire, ss::Element::Wind, firestorm);

    auto blizzard = std::make_shared<ss::SpellDefinition>(
        "Frost Tempest (Ice + Lightning)", "Freezing blast conducting electric shocks",
        ss::ElementSet{ss::Element::Ice, ss::Element::Lightning}, sf::Color(130, 220, 255), sf::Color(255, 240, 80),
        ss::ProjectileProfile{1.3f, 1.4f, 10.f, 2.0f, 1.1f, 0.f, 1, 0}
    );
    auto frostArea = std::make_unique<ss::AreaEffect>(75.f, true);
    frostArea->then(std::make_unique<ss::SlowEffect>(0.6f, 3.0f));
    blizzard->addEffect(std::move(frostArea));
    registry.registerCombination(ss::Element::Ice, ss::Element::Lightning, blizzard);

    auto lava = std::make_shared<ss::SpellDefinition>(
        "Magma Core (Earth + Fire)", "Molten volcanic rock dealing heavy lingering burn",
        ss::ElementSet{ss::Element::Earth, ss::Element::Fire}, sf::Color(220, 70, 30), sf::Color(255, 160, 40),
        ss::ProjectileProfile{1.8f, 0.9f, 14.f, 2.4f, 1.3f, 0.f, 0, 0}
    );
    lava->addEffect(std::make_unique<ss::BurnEffect>(0.7f, 4.0f));
    lava->addEffect(std::make_unique<ss::KnockbackEffect>(220.f));
    registry.registerCombination(ss::Element::Earth, ss::Element::Fire, lava);

    if (headless) {
        std::cout << "[SpellStorm] Headless mode: verified registry and resources successfully.\n";
        return 0;
    }

    // Create SFML RenderWindow
    sf::RenderWindow window(sf::VideoMode(1280, 720), "Spell Storm - 2D Magic Arena (OOP Assignment)");
    window.setFramerateLimit(60);

    ss::Arena arena({640.f, 360.f}, {1180.f, 630.f});
    ss::GameSession session(arena, registry, options.seed != 0 ? options.seed : 42, &resources);

    if (testLevelUp) {
        session.progression().addExp(30);
        ss::Random rng(999);
        auto choices = session.upgradeManager().getAvailableUpgrades(3, rng);
        if (!choices.empty()) {
            session.levelUpOverlay().open(choices);
        }
    } else if (testGameOver) {
        session.progression().addExp(85);
        session.triggerGameOver();
        session.gameOverOverlay().open(84.f, 26, 3);
    } else if (testAutoShot) {
        session.toggleShootingMode();
    } else if (testShowcase) {
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(520.f, 330.f)));
        session.enemies().push_back(std::make_unique<ss::RangedEnemy>(sf::Vector2f(780.f, 290.f)));
        session.enemies().push_back(std::make_unique<ss::TankEnemy>(sf::Vector2f(720.f, 430.f)));
        session.experienceSystem().spawnPickup(sf::Vector2f(580.f, 410.f), 10); // Cyan gem
        session.experienceSystem().spawnPickup(sf::Vector2f(640.f, 440.f), 25); // Emerald gem
        session.experienceSystem().spawnPickup(sf::Vector2f(700.f, 410.f), 50); // Gold gem
        session.castSpell(sf::Vector2f(720.f, 430.f));
    }

    sf::Clock clock;
    float realElapsed = 0.f;

    std::cout << "[SpellStorm] Window initialized.\n"
              << "Controls:\n"
              << " - Movement: WASD or Arrow Keys\n"
              << " - Elements: 1:Fire, 2:Ice, 3:Lightning, 4:Wind, 5:Earth\n"
              << " - Cast Spell: Left Mouse Button (in Manual mode)\n"
              << " - Toggle Shooting Mode (Manual/Auto): V\n"
              << " - Test Damage: Space (damages player by 25)\n"
              << " - Test Heal: H (heals player by 20)\n"
              << " - Test Level-Up: L (adds 30 EXP)\n"
              << " - In Game Over: Click 'Restart' or press R / Enter; Click 'Quit' or press Esc\n"
              << " - Exit: Escape\n";

    while (window.isOpen()) {
        const float dt = clock.restart().asSeconds();
        realElapsed += dt;

        if (options.quitAfter > 0.f && realElapsed >= options.quitAfter) {
            std::cout << "[SpellStorm] Reached quit-after timeout (" << options.quitAfter << "s). Exiting.\n";
            window.close();
            break;
        }

        const sf::Vector2i mousePixel = sf::Mouse::getPosition(window);
        const sf::Vector2f worldMousePos = window.mapPixelToCoords(mousePixel);

        sf::Event event;
        while (window.pollEvent(event)) {
            if (event.type == sf::Event::Closed) {
                window.close();
            } else {
                ss::SessionEventResult res = session.handleEvent(event, worldMousePos);
                if (res == ss::SessionEventResult::RequestQuit) {
                    window.close();
                }
            }
        }

        if (testRestartFlow) {
            if (realElapsed >= 1.2f && realElapsed < 1.8f && !session.isGameOver()) {
                session.triggerGameOver();
            }
            if (realElapsed >= 1.8f && session.isGameOver()) {
                session.restartMatch();
                std::cout << "[SpellStorm] Automated restart triggered successfully.\n";
            }
        }

        // Run simulation / UI tick
        session.update(dt, worldMousePos, options.autoplay);

        // Render entire frame
        window.clear(sf::Color(14, 16, 24));
        session.render(window, hasFont ? &resources.font(ss::FontId::Body) : nullptr, &resources);
        window.display();

        if (!options.screenshotDir.empty() && (options.quitAfter <= 0.f || realElapsed >= options.quitAfter - 0.1f)) {
            sf::Texture screenshot;
            if (screenshot.create(window.getSize().x, window.getSize().y)) {
                screenshot.update(window);
                if (screenshot.copyToImage().saveToFile(options.screenshotDir)) {
                    std::cout << "[SpellStorm] Saved screenshot to " << options.screenshotDir << "\n";
                }
            }
            options.screenshotDir.clear();
        }
    }

    std::cout << "[SpellStorm] Exiting game session cleanly.\n";
    return 0;
}
