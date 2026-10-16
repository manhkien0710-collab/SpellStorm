#include "TestHelper.hpp"
#include "core/Arena.hpp"
#include "core/GameSession.hpp"
#include "core/MathUtils.hpp"
#include "core/Random.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "spells/SpellDefinition.hpp"
#include "spells/SpellRegistry.hpp"
#include "systems/CombatSystem.hpp"
#include "systems/UpgradeSystem.hpp"

#include <cmath>
#include <memory>
#include <vector>

void runShootingModeTests()
{
    // Setup standard spell registry and arena for tests
    ss::Arena arena({640.f, 360.f}, {1180.f, 630.f});
    ss::SpellRegistry registry;

    auto fireSpell = std::make_shared<ss::SpellDefinition>(
        "Firebolt", "Ignites foes with continuous burning flame",
        ss::ElementSet{ss::Element::Fire}, sf::Color(255, 90, 20), sf::Color(255, 200, 50),
        ss::ProjectileProfile{1.0f, 1.2f, 9.f, 2.0f, 1.0f, 0.f, 0, 0}
    );
    registry.registerBase(ss::Element::Fire, fireSpell);

    // =========================================================================
    // 1. Default Shooting Mode
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1001);

        // Any new match must strictly start in Manual shooting mode
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);
        EXPECT_EQ(session.modeNotificationTimer(), 0.f);

        // In Manual mode, running update without mouse clicks does not shoot even if enemies are near
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(740.f, 360.f)));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));

        session.update(0.016f, sf::Vector2f(800.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));
        EXPECT_TRUE(session.player().canCast());

        // In Manual mode, explicit mouse button press event triggers cast
        sf::Event mouseClick;
        mouseClick.type = sf::Event::MouseButtonPressed;
        mouseClick.mouseButton.button = sf::Mouse::Left;
        mouseClick.mouseButton.x = 800;
        mouseClick.mouseButton.y = 360;

        session.handleEvent(mouseClick, sf::Vector2f(800.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
        EXPECT_FALSE(session.player().canCast());
    }

    // =========================================================================
    // 2. Mode Switching via Key Toggle & Repeated-Key Debounce
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1002);
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);

        // Simulate physical press of 'V' key
        sf::Event vPress;
        vPress.type = sf::Event::KeyPressed;
        vPress.key.code = sf::Keyboard::V;
        vPress.key.alt = false;
        vPress.key.control = false;
        vPress.key.shift = false;
        vPress.key.system = false;

        session.handleEvent(vPress, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);
        EXPECT_TRUE(session.modeNotificationTimer() > 0.f);

        // Simulating repeated KeyPressed events while 'V' key is held
        // Mode must remain Auto, NOT oscillate back to Manual!
        session.handleEvent(vPress, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        session.handleEvent(vPress, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        // Release the 'V' key
        sf::Event vRelease;
        vRelease.type = sf::Event::KeyReleased;
        vRelease.key.code = sf::Keyboard::V;
        session.handleEvent(vRelease, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        // Press 'V' again -> toggles back to Manual
        session.handleEvent(vPress, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);

        // Repeat press while held remains Manual
        session.handleEvent(vPress, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);

        // Release key again
        session.handleEvent(vRelease, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);

        // Verify programmatic toggle API
        session.toggleShootingMode();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);
        session.toggleShootingMode();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);
    }

    // =========================================================================
    // 3. Switching back to Manual restores manual behavior
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1003);
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(740.f, 360.f)));

        // Switch to Auto
        session.setShootingMode(ss::ShootingMode::Auto);
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        // Switch back to Manual
        session.setShootingMode(ss::ShootingMode::Manual);
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);

        // Update tick in Manual must NOT automatically fire at enemy
        session.update(0.016f, sf::Vector2f(640.f, 100.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));
        EXPECT_TRUE(session.player().canCast());

        // In Manual mode, mouse click deliberately fires towards mouse cursor
        sf::Event mouseClick;
        mouseClick.type = sf::Event::MouseButtonPressed;
        mouseClick.mouseButton.button = sf::Mouse::Left;
        session.handleEvent(mouseClick, sf::Vector2f(640.f, 100.f));

        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
        const auto& proj = session.combatSystem().projectiles().back();
        EXPECT_TRUE(proj->direction().y < -0.9f); // Fired upwards towards mouse cursor
    }

    // =========================================================================
    // 4. Auto-Shot Dynamic Aim and Shooting Direction Follows Player's Mouse
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1004);
        session.setShootingMode(ss::ShootingMode::Auto);

        const sf::Vector2f playerPos = session.player().position();

        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));
        EXPECT_TRUE(session.player().canCast());

        // Step A: Mouse to the right of the player
        const sf::Vector2f mouseRight = playerPos + sf::Vector2f(200.f, 0.f);
        session.update(0.016f, mouseRight);

        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
        const auto& projRight = session.combatSystem().projectiles().back();
        EXPECT_TRUE(projRight->faction() == ss::ProjectileFaction::Player);

        // Projectile direction points to the right towards the mouse cursor
        EXPECT_NEAR(projRight->direction().x, 1.0f, 0.05f);
        EXPECT_NEAR(projRight->direction().y, 0.0f, 0.05f);
        EXPECT_NEAR(session.player().aimAngle(), 0.f, 0.1f);

        // Advance cooldown until ready
        while (!session.player().canCast()) {
            session.player().update(0.05f);
        }

        // Step B: Player moves mouse to the top (upwards)
        const sf::Vector2f mouseUp = playerPos + sf::Vector2f(0.f, -200.f);
        session.update(0.016f, mouseUp);

        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(2));
        const auto& projUp = session.combatSystem().projectiles().back();

        // Projectile direction now dynamically shoots upwards following the mouse cursor
        EXPECT_NEAR(projUp->direction().x, 0.0f, 0.05f);
        EXPECT_NEAR(projUp->direction().y, -1.0f, 0.05f);
        EXPECT_NEAR(session.player().aimAngle(), -ss::math::kPi * 0.5f, 0.1f);
    }

    // =========================================================================
    // 5. Bot Autoplay Mode Automatically Acquires and Targets Enemies
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1005);
        // Autoplay bot mode targets enemies automatically
        const sf::Vector2f playerPos = session.player().position();
        const sf::Vector2f enemyPos = playerPos + sf::Vector2f(150.f, 0.f);
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(enemyPos));

        // In autoplay, mouse cursor is dummy at (10, 10), but bot aims at enemy
        session.update(0.016f, sf::Vector2f(10.f, 10.f), /*autoplay=*/true);

        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
        const auto& proj = session.combatSystem().projectiles().back();
        EXPECT_NEAR(proj->direction().x, 1.0f, 0.05f);
        EXPECT_NEAR(proj->direction().y, 0.0f, 0.05f);
    }

    // =========================================================================
    // 6. Auto-Shot Strictly Respects Attack Cooldowns
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1006);
        session.setShootingMode(ss::ShootingMode::Auto);
        // Place enemy far enough that projectile remains in flight during cooldown verification
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(1050.f, 360.f)));

        // First tick casts spell
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
        EXPECT_FALSE(session.player().canCast());

        // Subsequent ticks while cooldown is active must NOT spawn new projectiles
        for (int i = 0; i < 5; ++i) {
            session.update(0.016f, sf::Vector2f(640.f, 360.f));
            EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
        }

        // Advance time until cooldown has elapsed
        while (!session.player().canCast()) {
            session.player().update(0.05f);
        }
        EXPECT_TRUE(session.player().canCast());

        // Now next tick triggers next projectile (both in flight simultaneously)
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(2));
    }

    // =========================================================================
    // 7. Nearest Living Enemy Policy & Deterministic Tie-Breaking
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1007);
        session.setShootingMode(ss::ShootingMode::Auto);
        const sf::Vector2f pPos = session.player().position();

        // Spawn 3 enemies at varying distances
        auto eNear = std::make_unique<ss::MeleeEnemy>(pPos + sf::Vector2f(80.f, 0.f));   // dist 80
        auto eMid  = std::make_unique<ss::MeleeEnemy>(pPos + sf::Vector2f(160.f, 0.f));  // dist 160
        auto eFar  = std::make_unique<ss::MeleeEnemy>(pPos + sf::Vector2f(240.f, 0.f));  // dist 240

        ss::Enemy* pNear = eNear.get();
        ss::Enemy* pMid  = eMid.get();
        ss::Enemy* pFar  = eFar.get();

        session.enemies().push_back(std::move(eNear));
        session.enemies().push_back(std::move(eMid));
        session.enemies().push_back(std::move(eFar));

        // Target nearest
        EXPECT_EQ(session.findTargetEnemy(), pNear);

        // Kill nearest enemy
        pNear->takeDamage({9999.f, true});
        EXPECT_FALSE(pNear->isAlive());

        // Now target is the mid enemy
        EXPECT_EQ(session.findTargetEnemy(), pMid);

        // Kill mid enemy
        pMid->takeDamage({9999.f, true});
        EXPECT_FALSE(pMid->isAlive());

        // Now target is the far enemy
        EXPECT_EQ(session.findTargetEnemy(), pFar);

        // Kill far enemy
        pFar->takeDamage({9999.f, true});
        EXPECT_EQ(session.findTargetEnemy(), nullptr);

        // Tie-breaking check with two equidistant enemies
        session.enemies().clear();
        auto e1 = std::make_unique<ss::MeleeEnemy>(pPos + sf::Vector2f(100.f, 0.f));
        auto e2 = std::make_unique<ss::MeleeEnemy>(pPos + sf::Vector2f(-100.f, 0.f));
        const ss::EntityId minId = std::min(e1->id(), e2->id());
        ss::Enemy* expected = (e1->id() == minId) ? e1.get() : e2.get();

        session.enemies().push_back(std::move(e1));
        session.enemies().push_back(std::move(e2));

        EXPECT_EQ(session.findTargetEnemy(), expected);
    }

    // =========================================================================
    // 8. Auto-Shot Utilizes Player Upgrades (Damage, Speed, Lifetime, Pierce)
    // =========================================================================
    {
        // Baseline session without upgrades
        ss::GameSession sessionBase(arena, registry, 1008);
        sessionBase.setShootingMode(ss::ShootingMode::Auto);
        sessionBase.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sessionBase.player().position() + sf::Vector2f(100.f, 0.f)));
        sessionBase.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(sessionBase.combatSystem().projectileCount(), static_cast<std::size_t>(1));

        const auto& baseProj = sessionBase.combatSystem().projectiles().back();
        const float baseDamage = baseProj->damage();
        const float baseSpeed = baseProj->speed();
        const float baseLifetime = baseProj->lifetime();
        const int basePierce = baseProj->pierceRemaining();

        // Upgraded session
        ss::GameSession sessionUpgraded(arena, registry, 1009);
        sessionUpgraded.setShootingMode(ss::ShootingMode::Auto);

        // Apply Damage, ProjectileSpeed, Lifetime, and Pierce perks
        sessionUpgraded.upgradeManager().applyUpgrade(ss::UpgradeType::Damage, sessionUpgraded.player());
        sessionUpgraded.upgradeManager().applyUpgrade(ss::UpgradeType::ProjectileSpeed, sessionUpgraded.player());
        sessionUpgraded.upgradeManager().applyUpgrade(ss::UpgradeType::ProjectileLifetime, sessionUpgraded.player());
        sessionUpgraded.upgradeManager().applyUpgrade(ss::UpgradeType::Piercing, sessionUpgraded.player());

        EXPECT_TRUE(sessionUpgraded.player().damageMultiplier() > 1.0f);
        EXPECT_TRUE(sessionUpgraded.player().projectileSpeedMultiplier() > 1.0f);
        EXPECT_TRUE(sessionUpgraded.player().projectileLifetimeMultiplier() > 1.0f);
        EXPECT_TRUE(sessionUpgraded.player().bonusPierce() > 0);

        sessionUpgraded.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sessionUpgraded.player().position() + sf::Vector2f(100.f, 0.f)));
        sessionUpgraded.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(sessionUpgraded.combatSystem().projectileCount(), static_cast<std::size_t>(1));

        const auto& upProj = sessionUpgraded.combatSystem().projectiles().back();
        EXPECT_TRUE(upProj->damage() > baseDamage);
        EXPECT_TRUE(upProj->speed() > baseSpeed);
        EXPECT_TRUE(upProj->lifetime() > baseLifetime);
        EXPECT_TRUE(upProj->pierceRemaining() > basePierce);
    }

    // =========================================================================
    // 9. Target Removal / Mid-Simulation Death Memory Safety
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1010);
        session.setShootingMode(ss::ShootingMode::Auto);

        // Add 5 enemies
        for (int i = 0; i < 5; ++i) {
            session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(
                session.player().position() + sf::Vector2f(50.f + 25.f * static_cast<float>(i), 0.f)));
        }

        // Mid-frame mark multiple dead
        session.enemies()[0]->destroy();
        session.enemies()[2]->destroy();

        // Update tick runs cleanly without segfault or invalid memory access
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.isPlaying());

        // Completely clear enemies vector mid-match and update
        session.enemies().clear();
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.isPlaying());
    }

    // =========================================================================
    // 10. Mode Switching State Preservation
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1011);

        // Set specific match state
        session.player().takeDamage({25.f, false});
        session.progression().addExp(20);
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(700.f, 360.f)));
        session.update(0.016f, sf::Vector2f(640.f, 360.f));

        const float hpBefore = session.player().health();
        const int expBefore = session.progression().currentExp();
        const int levelBefore = session.progression().level();
        const std::size_t enemiesBefore = session.enemies().size();
        const float timeBefore = session.matchElapsed();
        const int defeatedBefore = session.enemiesDefeated();

        // Toggle Manual -> Auto
        session.toggleShootingMode();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);
        EXPECT_EQ(session.player().health(), hpBefore);
        EXPECT_EQ(session.progression().currentExp(), expBefore);
        EXPECT_EQ(session.progression().level(), levelBefore);
        EXPECT_EQ(session.enemies().size(), enemiesBefore);
        EXPECT_EQ(session.matchElapsed(), timeBefore);
        EXPECT_EQ(session.enemiesDefeated(), defeatedBefore);

        // Toggle Auto -> Manual
        session.toggleShootingMode();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);
        EXPECT_EQ(session.player().health(), hpBefore);
        EXPECT_EQ(session.progression().currentExp(), expBefore);
        EXPECT_EQ(session.progression().level(), levelBefore);
        EXPECT_EQ(session.enemies().size(), enemiesBefore);
        EXPECT_EQ(session.matchElapsed(), timeBefore);
        EXPECT_EQ(session.enemiesDefeated(), defeatedBefore);
    }

    // =========================================================================
    // 11. Auto-Shot and Toggle Safety During Level-Up Pause
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1012);
        session.setShootingMode(ss::ShootingMode::Auto);
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(session.player().position() + sf::Vector2f(60.f, 0.f)));

        // Open level up overlay
        session.progression().addExp(50);
        ss::Random rng(123);
        auto choices = session.upgradeManager().getAvailableUpgrades(3, rng);
        session.levelUpOverlay().open(choices);
        EXPECT_TRUE(session.isLevelUpOpen());

        // Update while paused: must NOT fire auto-shot
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));

        // 'V' key press while level up is open must NOT toggle shooting mode
        sf::Event vKey;
        vKey.type = sf::Event::KeyPressed;
        vKey.key.code = sf::Keyboard::V;
        session.handleEvent(vKey, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        // Select and confirm level up card
        sf::Event cardSelect;
        cardSelect.type = sf::Event::KeyPressed;
        cardSelect.key.code = sf::Keyboard::Num2;
        session.handleEvent(cardSelect, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(session.levelUpOverlay().selectedIndex(), 1);

        sf::Event confirmKey;
        confirmKey.type = sf::Event::KeyPressed;
        confirmKey.key.code = sf::Keyboard::Return;
        session.handleEvent(confirmKey, sf::Vector2f(640.f, 360.f));
        EXPECT_FALSE(session.isLevelUpOpen());
        EXPECT_TRUE(session.isPlaying());

        // Resuming gameplay enables Auto-shot firing
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(1));
    }

    // =========================================================================
    // 12. Auto-Shot and Toggle Safety During Game Over
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1013);
        session.setShootingMode(ss::ShootingMode::Auto);
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(session.player().position() + sf::Vector2f(60.f, 0.f)));

        session.triggerGameOver();
        EXPECT_TRUE(session.isGameOver());

        // Update tick while Game Over: gameplay remains frozen, no firing
        session.update(0.016f, sf::Vector2f(640.f, 360.f));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));

        // 'V' key press during Game Over does NOT toggle shooting mode
        sf::Event vKey;
        vKey.type = sf::Event::KeyPressed;
        vKey.key.code = sf::Keyboard::V;
        session.handleEvent(vKey, sf::Vector2f(640.f, 360.f));
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);
    }

    // =========================================================================
    // 13. Match Restart Restores Manual Mode
    // =========================================================================
    {
        ss::GameSession session(arena, registry, 1014);
        session.setShootingMode(ss::ShootingMode::Auto);
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        // Restarting match must strictly restore Manual mode
        session.restartMatch();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);
        EXPECT_EQ(session.modeNotificationTimer(), 0.f);

        // Can switch to Auto again in new match
        session.toggleShootingMode();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Auto);

        // Second restart also restores Manual mode cleanly
        session.restartMatch();
        EXPECT_TRUE(session.shootingMode() == ss::ShootingMode::Manual);
    }

    // =========================================================================
    // 14. Melee Enemy Movement Speed Regression Test (105.f)
    // =========================================================================
    {
        ss::MeleeEnemy melee({0.f, 0.f});
        EXPECT_EQ(melee.baseMoveSpeed(), 105.f);
        EXPECT_NEAR(melee.baseMoveSpeed(), 105.f, 1e-4f);
    }
}
