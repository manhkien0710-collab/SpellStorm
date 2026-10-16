#include "TestHelper.hpp"
#include "core/Arena.hpp"
#include "core/GameSession.hpp"
#include "core/Random.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "spells/HitEffect.hpp"
#include "spells/SpellDefinition.hpp"
#include "spells/SpellRegistry.hpp"
#include "ui/GameOverOverlay.hpp"
#include "ui/LevelUpOverlay.hpp"

#include <memory>
#include <vector>

void runGameOverTests()
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

    // =========================================================
    // 1. Player Death Condition and Post-Death Action Disabling
    // =========================================================
    {
        ss::Player player({640.f, 360.f});
        EXPECT_TRUE(player.isAlive());
        EXPECT_EQ(player.health(), ss::Player::kDefaultHealth);
        EXPECT_TRUE(player.canCast());

        // Player takes lethal damage
        const float dealt = player.takeDamage({ss::Player::kDefaultHealth + 100.f, true});
        EXPECT_TRUE(dealt > 0.f);
        EXPECT_EQ(player.health(), 0.f);
        EXPECT_FALSE(player.isAlive());

        // Player cannot cast after death
        EXPECT_FALSE(player.canCast());
        player.triggerCast(1.0f);
        EXPECT_FALSE(player.canCast());

        // Player cannot move after death
        player.handleInput(0.016f);
        EXPECT_EQ(player.velocity().x, 0.f);
        EXPECT_EQ(player.velocity().y, 0.f);

        // Player ignores aim updates after death
        const float prevAim = player.aimAngle();
        player.setAimTarget({10.f, 10.f});
        EXPECT_EQ(player.aimAngle(), prevAim);

        // Repeated damage calls are idempotent and dealt is 0
        EXPECT_EQ(player.takeDamage({50.f, false}), 0.f);
        EXPECT_FALSE(player.isAlive());
        EXPECT_EQ(player.health(), 0.f);
    }

    // =========================================================
    // 2. Game Over Transition and Idempotence (Exactly Once)
    // =========================================================
    {
        ss::GameSession session(arena, registry, 101);
        EXPECT_FALSE(session.isGameOver());
        EXPECT_FALSE(session.gameOverOverlay().isOpen());
        EXPECT_TRUE(session.isPlaying());

        // Kill player
        session.player().takeDamage({9999.f, true});
        EXPECT_FALSE(session.player().isAlive());

        // Next update tick detects death and transitions to Game Over
        session.update(0.016f, {640.f, 360.f});
        EXPECT_TRUE(session.isGameOver());
        EXPECT_TRUE(session.gameOverOverlay().isOpen());
        EXPECT_FALSE(session.isPlaying());

        const float survivalTimeFirst = session.gameOverOverlay().survivalTime();

        // Repeated update ticks while dead must not duplicate transition or advance time
        session.update(0.016f, {640.f, 360.f});
        session.update(0.016f, {640.f, 360.f});
        EXPECT_TRUE(session.isGameOver());
        EXPECT_EQ(session.gameOverOverlay().survivalTime(), survivalTimeFirst);
        EXPECT_EQ(session.matchElapsed(), survivalTimeFirst);
    }

    // =========================================================
    // 3. Gameplay Simulation Remains Frozen During Game Over
    // =========================================================
    {
        ss::GameSession session(arena, registry, 202);

        // Advance normal gameplay for 5 seconds to spawn enemies and missiles
        for (int i = 0; i < 300; ++i) {
            session.update(0.016f, {700.f, 360.f});
        }
        EXPECT_TRUE(session.matchElapsed() > 4.5f);

        // Ensure we have active entities
        if (session.enemies().empty()) {
            session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(200.f, 200.f)));
        }
        session.combatSystem().addProjectile(std::make_unique<ss::Projectile>(
            sf::Vector2f(300.f, 300.f), sf::Vector2f(1.f, 0.f), 200.f, 8.f, 30.f, 2.0f, 0,
            ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow
        ));
        session.experienceSystem().spawnPickup({150.f, 150.f}, 15);

        // Record positions and counts before death
        const float timeBefore = session.matchElapsed();
        const float spawnerTimerBefore = session.spawner().spawnTimer();
        const std::size_t enemyCountBefore = session.enemies().size();
        const std::size_t projCountBefore = session.combatSystem().projectileCount();
        const std::size_t pickupCountBefore = session.experienceSystem().pickupCount();
        const sf::Vector2f enemyPosBefore = session.enemies()[0]->position();
        const sf::Vector2f projPosBefore = session.combatSystem().projectiles()[0]->position();

        // Trigger Game Over
        session.triggerGameOver();
        EXPECT_TRUE(session.isGameOver());

        // Run 20 simulation ticks while Game Over is active
        for (int i = 0; i < 20; ++i) {
            session.update(0.016f, {700.f, 360.f});
        }

        // Assert that simulation did NOT advance at all
        EXPECT_EQ(session.matchElapsed(), timeBefore);
        EXPECT_EQ(session.spawner().spawnTimer(), spawnerTimerBefore);
        EXPECT_EQ(session.enemies().size(), enemyCountBefore);
        EXPECT_EQ(session.combatSystem().projectileCount(), projCountBefore);
        EXPECT_EQ(session.experienceSystem().pickupCount(), pickupCountBefore);
        EXPECT_EQ(session.enemies()[0]->position().x, enemyPosBefore.x);
        EXPECT_EQ(session.enemies()[0]->position().y, enemyPosBefore.y);
        EXPECT_EQ(session.combatSystem().projectiles()[0]->position().x, projPosBefore.x);
        EXPECT_EQ(session.combatSystem().projectiles()[0]->position().y, projPosBefore.y);
    }

    // =========================================================
    // 4. Game Over Overlay UI Interaction & Button Feedback
    // =========================================================
    {
        ss::GameOverOverlay overlay;
        EXPECT_FALSE(overlay.isOpen());

        overlay.open(85.5f, 42, 5);
        EXPECT_TRUE(overlay.isOpen());
        EXPECT_NEAR(overlay.survivalTime(), 85.5f, 0.001f);
        EXPECT_EQ(overlay.enemiesDefeated(), 42);
        EXPECT_EQ(overlay.levelReached(), 5);
        EXPECT_EQ(overlay.formattedSurvivalTime(), "01:25");

        // Before grace timer expires (0.2s), clicks are ignored
        const sf::Vector2f outsidePos(5.f, 5.f);
        EXPECT_EQ(overlay.handleMouseClick(outsidePos), ss::GameOverAction::None);

        // Advance past grace timer
        overlay.update(0.3f);
        EXPECT_EQ(overlay.openGraceTimer(), 0.f);

        // Clicking outside buttons produces no action
        EXPECT_EQ(overlay.handleMouseClick(outsidePos), ss::GameOverAction::None);

        // Restart button interaction test
        const sf::FloatRect rBounds = overlay.restartButtonBounds();
        const sf::Vector2f rCenter(rBounds.left + rBounds.width * 0.5f, rBounds.top + rBounds.height * 0.5f);

        overlay.handleMouseMove(rCenter);
        EXPECT_TRUE(overlay.isRestartHovered());
        EXPECT_FALSE(overlay.isQuitHovered());

        overlay.handleMouseButtonPressed(rCenter);
        EXPECT_TRUE(overlay.isRestartPressed());

        ss::GameOverAction act = overlay.handleMouseButtonReleased(rCenter);
        EXPECT_EQ(act, ss::GameOverAction::Restart);
        EXPECT_FALSE(overlay.isRestartPressed());

        // Releasing outside cancels pressed button
        overlay.handleMouseButtonPressed(rCenter);
        EXPECT_TRUE(overlay.isRestartPressed());
        act = overlay.handleMouseButtonReleased(outsidePos);
        EXPECT_EQ(act, ss::GameOverAction::None);
        EXPECT_FALSE(overlay.isRestartPressed());

        // Quit button interaction test
        const sf::FloatRect qBounds = overlay.quitButtonBounds();
        const sf::Vector2f qCenter(qBounds.left + qBounds.width * 0.5f, qBounds.top + qBounds.height * 0.5f);

        overlay.handleMouseMove(qCenter);
        EXPECT_TRUE(overlay.isQuitHovered());
        EXPECT_FALSE(overlay.isRestartHovered());

        overlay.handleMouseButtonPressed(qCenter);
        EXPECT_TRUE(overlay.isQuitPressed());

        act = overlay.handleMouseButtonReleased(qCenter);
        EXPECT_EQ(act, ss::GameOverAction::Quit);
        EXPECT_FALSE(overlay.isQuitPressed());

        // Keyboard navigation
        EXPECT_EQ(overlay.handleKeyPress(sf::Keyboard::R), ss::GameOverAction::Restart);
        EXPECT_EQ(overlay.handleKeyPress(sf::Keyboard::Return), ss::GameOverAction::Restart);
        EXPECT_EQ(overlay.handleKeyPress(sf::Keyboard::Space), ss::GameOverAction::Restart);
        EXPECT_EQ(overlay.handleKeyPress(sf::Keyboard::Escape), ss::GameOverAction::Quit);
        EXPECT_EQ(overlay.handleKeyPress(sf::Keyboard::Q), ss::GameOverAction::Quit);

        // Close overlay
        overlay.close();
        EXPECT_FALSE(overlay.isOpen());
    }

    // =========================================================
    // 5. Restart Restoring All Match-Specific State
    // =========================================================
    {
        ss::GameSession session(arena, registry, 303);

        // Modify match state extensively:
        // 1. Player damage and upgrades
        session.player().takeDamage({80.f, false});
        session.upgradeManager().applyUpgrade(ss::UpgradeType::MaxHealth, session.player());
        session.upgradeManager().applyUpgrade(ss::UpgradeType::Damage, session.player());
        session.upgradeManager().applyUpgrade(ss::UpgradeType::MoveSpeed, session.player());
        session.progression().addExp(100);

        // 2. Add enemies and missiles
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(100.f, 100.f)));
        session.enemies().push_back(std::make_unique<ss::TankEnemy>(sf::Vector2f(200.f, 200.f)));
        session.combatSystem().addProjectile(std::make_unique<ss::Projectile>(
            sf::Vector2f(400.f, 400.f), sf::Vector2f(1.f, 0.f), 200.f, 8.f, 25.f, 2.0f, 0,
            ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow
        ));
        session.experienceSystem().spawnPickup({300.f, 300.f}, 30);

        // 3. Advance spawner and match elapsed
        for (int i = 0; i < 150; ++i) {
            session.update(0.016f, {640.f, 360.f});
        }
        session.triggerGameOver();
        EXPECT_TRUE(session.isGameOver());

        // NOW RESTART
        session.restartMatch();

        // 1. Assert Player state
        EXPECT_TRUE(session.player().isAlive());
        EXPECT_EQ(session.player().health(), ss::Player::kDefaultHealth);
        EXPECT_EQ(session.player().maxHealth(), ss::Player::kDefaultHealth);
        EXPECT_EQ(session.player().armor(), ss::Player::kDefaultArmor);
        EXPECT_EQ(session.player().baseMoveSpeed(), ss::Player::kDefaultMoveSpeed);
        EXPECT_EQ(session.player().damageMultiplier(), 1.0f);
        EXPECT_EQ(session.player().projectileSpeedMultiplier(), 1.0f);
        EXPECT_EQ(session.player().projectileLifetimeMultiplier(), 1.0f);
        EXPECT_EQ(session.player().bonusPierce(), 0);
        EXPECT_EQ(session.player().position().x, arena.center().x);
        EXPECT_EQ(session.player().position().y, arena.center().y);
        EXPECT_TRUE(session.player().canCast());

        // 2. Assert Progression & Upgrades
        EXPECT_EQ(session.progression().level(), 1);
        EXPECT_EQ(session.progression().currentExp(), 0);
        EXPECT_EQ(session.progression().pendingLevelUps(), 0);
        EXPECT_FALSE(session.progression().hasPendingLevelUp());
        EXPECT_EQ(session.upgradeManager().getRank(ss::UpgradeType::MaxHealth), 0);
        EXPECT_EQ(session.upgradeManager().getRank(ss::UpgradeType::Damage), 0);
        EXPECT_EQ(session.upgradeManager().getRank(ss::UpgradeType::MoveSpeed), 0);

        // 3. Assert Battlefield Entities
        EXPECT_EQ(session.enemies().size(), static_cast<std::size_t>(0));
        EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));
        EXPECT_EQ(session.experienceSystem().pickupCount(), static_cast<std::size_t>(0));

        // 4. Assert Timers & Statistics
        EXPECT_EQ(session.matchElapsed(), 0.f);
        EXPECT_EQ(session.enemiesDefeated(), 0);
        EXPECT_EQ(session.spawner().elapsedTime(), 0.f);
        EXPECT_EQ(session.spawner().currentInterval(), session.spawner().baseInterval());
        EXPECT_EQ(session.spawner().maxEnemies(), session.spawner().baseMaxEnemies());

        // 5. Assert Overlays
        EXPECT_FALSE(session.gameOverOverlay().isOpen());
        EXPECT_FALSE(session.levelUpOverlay().isOpen());
        EXPECT_TRUE(session.isPlaying());
    }

    // =========================================================
    // 6. Repeated Restarts Safety (No Memory Leaks / Stale State)
    // =========================================================
    {
        ss::GameSession session(arena, registry, 404);

        for (int cycle = 0; cycle < 8; ++cycle) {
            // Run a mini match
            for (int t = 0; t < 60; ++t) {
                session.update(0.016f, {640.f, 360.f});
            }
            session.player().takeDamage({9999.f, true});
            session.update(0.016f, {640.f, 360.f});
            EXPECT_TRUE(session.isGameOver());

            // Restart match
            session.restartMatch();
            EXPECT_TRUE(session.isPlaying());
            EXPECT_TRUE(session.player().isAlive());
            EXPECT_EQ(session.player().health(), ss::Player::kDefaultHealth);
            EXPECT_EQ(session.enemies().size(), static_cast<std::size_t>(0));
            EXPECT_EQ(session.combatSystem().projectileCount(), static_cast<std::size_t>(0));
            EXPECT_EQ(session.experienceSystem().pickupCount(), static_cast<std::size_t>(0));
            EXPECT_EQ(session.matchElapsed(), 0.f);
            EXPECT_EQ(session.enemiesDefeated(), 0);
        }
    }

    // =========================================================
    // 7. Level-Up Pause & Death Immunity While Paused
    // =========================================================
    {
        ss::GameSession session(arena, registry, 505);

        // Grant EXP and open level up
        session.progression().addExp(30);
        EXPECT_TRUE(session.progression().hasPendingLevelUp());

        ss::Random rng(777);
        auto choices = session.upgradeManager().getAvailableUpgrades(3, rng);
        session.levelUpOverlay().open(choices);
        EXPECT_TRUE(session.levelUpOverlay().isOpen());
        EXPECT_TRUE(session.isLevelUpOpen());

        // Spawn a melee enemy on top of the player
        session.enemies().push_back(std::make_unique<ss::MeleeEnemy>(session.player().position()));
        const float playerHpBefore = session.player().health();

        // Run 10 ticks while level-up is open
        for (int i = 0; i < 10; ++i) {
            session.update(0.016f, {640.f, 360.f});
        }

        // Player must NOT take damage or die while paused for level up
        EXPECT_EQ(session.player().health(), playerHpBefore);
        EXPECT_TRUE(session.player().isAlive());
        EXPECT_FALSE(session.isGameOver());

        // Restarting while level up is open cleans it up cleanly
        session.restartMatch();
        EXPECT_FALSE(session.levelUpOverlay().isOpen());
        EXPECT_FALSE(session.progression().hasPendingLevelUp());
        EXPECT_EQ(session.progression().pendingLevelUps(), 0);
    }

    // =========================================================
    // 8. Regression Check: Melee Enemy Movement Speed Preserved
    // =========================================================
    {
        ss::MeleeEnemy melee({0.f, 0.f});
        EXPECT_EQ(melee.baseMoveSpeed(), 105.f);
    }
}
