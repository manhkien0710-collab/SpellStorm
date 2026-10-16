#include "TestHelper.hpp"
#include "core/Arena.hpp"
#include "core/Random.hpp"
#include "entities/Enemy.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "spells/HitEffect.hpp"
#include "spells/SpellDefinition.hpp"
#include "systems/CombatSystem.hpp"
#include "systems/EffectSystem.hpp"
#include "systems/EnemySpawner.hpp"

#include <cmath>

void runSimulationTests()
{
    // Test random generator reproducibility
    ss::Random rng1(12345);
    ss::Random rng2(12345);

    EXPECT_EQ(rng1.rangeInt(1, 100), rng2.rangeInt(1, 100));
    EXPECT_NEAR(rng1.range(0.f, 1.f), rng2.range(0.f, 1.f), 0.0001f);

    // Test Arena geometry and containment
    ss::Arena arena({640.f, 360.f}, {1000.f, 600.f});
    EXPECT_EQ(arena.bounds().left, 140.f);
    EXPECT_EQ(arena.bounds().top, 60.f);
    EXPECT_EQ(arena.bounds().width, 1000.f);
    EXPECT_EQ(arena.bounds().height, 600.f);

    EXPECT_TRUE(arena.contains({640.f, 360.f}));
    EXPECT_TRUE(arena.contains({200.f, 200.f}));
    EXPECT_FALSE(arena.contains({50.f, 360.f}));
    EXPECT_FALSE(arena.contains({1200.f, 360.f}));

    // Test Arena clamping with radius
    sf::Vector2f clampedOutside = arena.clampPosition({50.f, 20.f}, 25.f);
    EXPECT_NEAR(clampedOutside.x, 140.f + 25.f, 0.001f);
    EXPECT_NEAR(clampedOutside.y, 60.f + 25.f, 0.001f);

    sf::Vector2f clampedInside = arena.clampPosition({500.f, 300.f}, 20.f);
    EXPECT_NEAR(clampedInside.x, 500.f, 0.001f);
    EXPECT_NEAR(clampedInside.y, 300.f, 0.001f);

    // Test Player entity
    ss::Player player({640.f, 360.f});
    EXPECT_EQ(player.health(), 250.f);
    EXPECT_EQ(player.maxHealth(), 250.f);
    EXPECT_EQ(player.armor(), 30.f);
    EXPECT_TRUE(player.isAlive());

    // Test Player aim angle calculation
    player.setAimTarget({740.f, 360.f}); // Directly to the right
    EXPECT_NEAR(player.aimAngle(), 0.f, 0.001f);

    player.setAimTarget({640.f, 460.f}); // Directly downwards (+Y in SFML)
    EXPECT_NEAR(player.aimAngle(), 3.14159265f * 0.5f, 0.01f);

    // Test Player boundary clamping
    player.setPosition({0.f, 0.f});
    player.constrainToBounds(arena.bounds());
    EXPECT_NEAR(player.position().x, arena.bounds().left + player.radius(), 0.001f);
    EXPECT_NEAR(player.position().y, arena.bounds().top + player.radius(), 0.001f);

    // Test simulation update tick
    player.update(0.016f);
    EXPECT_TRUE(player.isAlive());

    // =========================================================
    // Milestone 2 Tests: Enemy AI, Concrete Types, and Spawner
    // =========================================================

    // 1. Enemy initialization and statistics
    ss::MeleeEnemy melee({300.f, 300.f});
    EXPECT_EQ(melee.type(), ss::EnemyType::Melee);
    EXPECT_EQ(melee.health(), 60.f);
    EXPECT_EQ(melee.maxHealth(), 60.f);
    EXPECT_EQ(melee.armor(), 0.f);
    EXPECT_EQ(melee.baseMoveSpeed(), 105.f);
    EXPECT_EQ(melee.contactDamage(), 18.f);
    EXPECT_EQ(melee.attackCooldown(), 0.8f);
    EXPECT_EQ(melee.expReward(), 10);
    EXPECT_TRUE(melee.isAlive());

    ss::RangedEnemy ranged({400.f, 400.f});
    EXPECT_EQ(ranged.type(), ss::EnemyType::Ranged);
    EXPECT_EQ(ranged.health(), 45.f);
    EXPECT_EQ(ranged.baseMoveSpeed(), 120.f);
    EXPECT_EQ(ranged.contactDamage(), 8.f);
    EXPECT_EQ(ranged.attackCooldown(), 1.4f);
    EXPECT_EQ(ranged.preferredDistance(), 240.f);
    EXPECT_TRUE(ranged.isAlive());

    ss::TankEnemy tank({500.f, 500.f});
    EXPECT_EQ(tank.type(), ss::EnemyType::Tank);
    EXPECT_EQ(tank.health(), 200.f);
    EXPECT_EQ(tank.maxHealth(), 200.f);
    EXPECT_EQ(tank.armor(), 25.f);
    EXPECT_EQ(tank.baseMoveSpeed(), 65.f);
    EXPECT_EQ(tank.radius(), 26.f);
    EXPECT_EQ(tank.contactDamage(), 32.f);
    EXPECT_TRUE(tank.isAlive());

    // 2. Tank enemy health vs Melee health & speed
    EXPECT_TRUE(tank.maxHealth() > melee.maxHealth());
    EXPECT_TRUE(tank.baseMoveSpeed() < melee.baseMoveSpeed());

    // 3. Melee enemy moves toward player
    ss::MeleeEnemy chaser({200.f, 300.f});
    const float distBefore = std::abs(400.f - chaser.position().x);
    chaser.updateAI(0.5f, {400.f, 300.f});
    const float distAfter = std::abs(400.f - chaser.position().x);
    EXPECT_TRUE(chaser.position().x > 200.f);
    EXPECT_TRUE(distAfter < distBefore);

    // 4. Ranged enemy distance maintenance
    // Case A: Player too far (> 275px) -> ranged approaches
    ss::RangedEnemy kiterFar({500.f, 200.f});
    kiterFar.updateAI(0.5f, {100.f, 200.f}); // Distance = 400
    EXPECT_TRUE(kiterFar.position().x < 500.f); // Moved left toward player

    // Case B: Player too close (< 205px) -> ranged retreats
    ss::RangedEnemy kiterClose({200.f, 200.f});
    kiterClose.updateAI(0.5f, {100.f, 200.f}); // Distance = 100
    EXPECT_TRUE(kiterClose.position().x > 200.f); // Moved right away from player

    // 5. Arena boundary constraints
    tank.setPosition({-100.f, -100.f});
    tank.constrainToBounds(arena.bounds());
    EXPECT_NEAR(tank.position().x, arena.bounds().left + tank.radius(), 0.001f);
    EXPECT_NEAR(tank.position().y, arena.bounds().top + tank.radius(), 0.001f);

    // 6. Safe handling of overlapping player/enemy positions (no division by zero / NaN)
    ss::MeleeEnemy overlapMelee({350.f, 350.f});
    overlapMelee.updateAI(0.1f, {350.f, 350.f});
    EXPECT_FALSE(std::isnan(overlapMelee.position().x));
    EXPECT_FALSE(std::isnan(overlapMelee.position().y));
    EXPECT_FALSE(std::isinf(overlapMelee.position().x));
    EXPECT_FALSE(std::isinf(overlapMelee.position().y));

    ss::RangedEnemy overlapRanged({350.f, 350.f});
    overlapRanged.updateAI(0.1f, {350.f, 350.f});
    EXPECT_FALSE(std::isnan(overlapRanged.position().x));
    EXPECT_FALSE(std::isnan(overlapRanged.position().y));

    // 7. Contact damage cooldown
    ss::Player combatPlayer({300.f, 300.f});
    ss::MeleeEnemy attacker({315.f, 300.f}); // Distance 15 < radius 20 + 16 = 36
    const float hp0 = combatPlayer.health();
    const bool firstHit = attacker.tryAttack(combatPlayer);
    EXPECT_TRUE(firstHit);
    EXPECT_TRUE(combatPlayer.health() < hp0);
    const float hp1 = combatPlayer.health();

    // Immediate second hit must be blocked by cooldown
    const bool immediateSecondHit = attacker.tryAttack(combatPlayer);
    EXPECT_FALSE(immediateSecondHit);
    EXPECT_EQ(combatPlayer.health(), hp1);

    // Advance time past cooldown (0.8s)
    attacker.update(0.85f);
    EXPECT_TRUE(attacker.canAttack());
    const bool thirdHit = attacker.tryAttack(combatPlayer);
    EXPECT_TRUE(thirdHit);
    EXPECT_TRUE(combatPlayer.health() < hp1);

    // 8. Spawner timing and maximum active enemy count
    ss::Random spawnerRng(999);
    ss::EnemySpawner spawner(arena, spawnerRng);
    spawner.setMaxEnemies(2);

    std::vector<std::unique_ptr<ss::Enemy>> enemyList;

    // Grace period check: should NOT spawn at dt = 0.5s (initial grace is 1.5s)
    spawner.update(0.5f, combatPlayer.position(), enemyList);
    EXPECT_EQ(enemyList.size(), static_cast<std::size_t>(0));

    // Elapse past initial timer: spawns 1st enemy
    spawner.update(1.2f, combatPlayer.position(), enemyList);
    EXPECT_EQ(enemyList.size(), static_cast<std::size_t>(1));

    // Elapse past next interval: spawns 2nd enemy
    spawner.update(spawner.baseInterval() + 0.1f, combatPlayer.position(), enemyList);
    EXPECT_EQ(enemyList.size(), static_cast<std::size_t>(2));

    // Cap test: even after 10 seconds, active enemies must NOT exceed maxEnemies (2)
    spawner.update(10.f, combatPlayer.position(), enemyList);
    EXPECT_EQ(enemyList.size(), static_cast<std::size_t>(2));

    // 9. Reproducibility with fixed random seed
    ss::Random rngA(4242);
    ss::EnemySpawner spawnerA(arena, rngA);

    ss::Random rngB(4242);
    ss::EnemySpawner spawnerB(arena, rngB);

    auto enemyA = spawnerA.spawnEnemy({640.f, 360.f});
    auto enemyB = spawnerB.spawnEnemy({640.f, 360.f});

    // 10. Safe enemy death processing and removal (regression test for nullptr dereference)
    std::vector<std::unique_ptr<ss::Enemy>> deathList;
    deathList.push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(100.f, 100.f)));
    deathList.push_back(std::make_unique<ss::RangedEnemy>(sf::Vector2f(200.f, 200.f)));
    deathList.push_back(std::make_unique<ss::TankEnemy>(sf::Vector2f(300.f, 300.f)));
    EXPECT_EQ(deathList.size(), static_cast<std::size_t>(3));

    // Overkill 2nd enemy
    deathList[1]->takeDamage({100.f, true});
    EXPECT_FALSE(deathList[1]->isAlive());

    // Process death visuals before erase-remove
    int deadCount = 0;
    for (const auto& e : deathList) {
        if (!e->isAlive()) {
            ++deadCount;
            EXPECT_EQ(e->type(), ss::EnemyType::Ranged);
        }
    }
    EXPECT_EQ(deadCount, 1);

    deathList.erase(
        std::remove_if(deathList.begin(), deathList.end(), [](const std::unique_ptr<ss::Enemy>& e) {
            return !e->isAlive();
        }),
        deathList.end()
    );

    EXPECT_EQ(deathList.size(), static_cast<std::size_t>(2));
    EXPECT_EQ(deathList[0]->type(), ss::EnemyType::Melee);
    EXPECT_EQ(deathList[1]->type(), ss::EnemyType::Tank);

    // =========================================================
    // Milestone 3 Tests: Projectile System, Combat, and Spells
    // =========================================================

    // 1. Projectile initialization & stats
    ss::Projectile pInit({100.f, 150.f}, {1.f, 0.f}, 300.f, 8.f, 25.f, 1.5f, 2,
                         ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow);
    EXPECT_EQ(pInit.position().x, 100.f);
    EXPECT_EQ(pInit.position().y, 150.f);
    EXPECT_EQ(pInit.direction().x, 1.f);
    EXPECT_EQ(pInit.direction().y, 0.f);
    EXPECT_EQ(pInit.velocity().x, 300.f);
    EXPECT_EQ(pInit.velocity().y, 0.f);
    EXPECT_EQ(pInit.speed(), 300.f);
    EXPECT_EQ(pInit.radius(), 8.f);
    EXPECT_EQ(pInit.damage(), 25.f);
    EXPECT_EQ(pInit.lifetime(), 1.5f);
    EXPECT_EQ(pInit.pierceRemaining(), 2);
    EXPECT_TRUE(pInit.faction() == ss::ProjectileFaction::Player);
    EXPECT_TRUE(pInit.isAlive());

    // 2. Projectile movement over delta time
    pInit.update(0.1f);
    EXPECT_NEAR(pInit.position().x, 130.f, 0.001f);
    EXPECT_NEAR(pInit.position().y, 150.f, 0.001f);
    EXPECT_NEAR(pInit.lifetime(), 1.4f, 0.001f);

    // 3. Projectile lifetime expiration
    pInit.update(1.45f);
    EXPECT_FALSE(pInit.isAlive());

    // 4. Safe handling of zero-length direction
    ss::Projectile pZero({200.f, 200.f}, {0.f, 0.f}, 250.f, 6.f, 15.f, 1.0f, 0,
                         ss::ProjectileFaction::Player, sf::Color::Cyan, sf::Color::White);
    EXPECT_FALSE(std::isnan(pZero.direction().x));
    EXPECT_FALSE(std::isnan(pZero.direction().y));
    EXPECT_FALSE(std::isinf(pZero.direction().x));
    EXPECT_FALSE(std::isinf(pZero.direction().y));
    EXPECT_EQ(pZero.direction().x, 1.f);
    EXPECT_EQ(pZero.direction().y, 0.f);

    // 5. Projectile expiration outside arena
    ss::EffectSystem testEffects;
    ss::CombatSystem testCombat(arena, testEffects);
    testCombat.addProjectile(std::make_unique<ss::Projectile>(
        sf::Vector2f(20.f, 20.f), sf::Vector2f(-1.f, 0.f), 200.f, 6.f, 20.f, 2.0f, 0,
        ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::White
    ));
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(1));
    std::vector<std::unique_ptr<ss::Enemy>> dummyEnemies;
    ss::Player dummyPlayer({640.f, 360.f});
    testCombat.update(0.016f, dummyPlayer, dummyEnemies);
    // Projectile at (20, 20) is outside arena bounds (left is 140) -> destroyed
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(0));

    // 6. Player projectile collision with enemy, damage application, and removal
    std::vector<std::unique_ptr<ss::Enemy>> combatEnemies;
    combatEnemies.push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(300.f, 300.f)));
    const float enemyHpBefore = combatEnemies[0]->health();

    testCombat.addProjectile(std::make_unique<ss::Projectile>(
        sf::Vector2f(295.f, 300.f), sf::Vector2f(1.f, 0.f), 100.f, 8.f, 30.f, 1.0f, 0,
        ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow
    ));
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(1));

    testCombat.update(0.016f, dummyPlayer, combatEnemies);
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(0)); // Non-piercing missile consumed
    EXPECT_TRUE(combatEnemies[0]->health() < enemyHpBefore);
    EXPECT_NEAR(combatEnemies[0]->health(), enemyHpBefore - 30.f, 0.001f);

    // 7. Pierce and multi-hit prevention (single missile does NOT hit same enemy repeatedly)
    combatEnemies.push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(340.f, 300.f)));
    auto piercingMissile = std::make_unique<ss::Projectile>(
        sf::Vector2f(295.f, 300.f), sf::Vector2f(1.f, 0.f), 100.f, 8.f, 20.f, 1.0f, 1,
        ss::ProjectileFaction::Player, sf::Color::Green, sf::Color::White
    );
    testCombat.addProjectile(std::move(piercingMissile));
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(1));

    // Frame 1: hits enemy 0, decrements pierce from 1 to 0
    testCombat.update(0.016f, dummyPlayer, combatEnemies);
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(1)); // Still alive due to pierce=1
    const float enemy0Hp = combatEnemies[0]->health();

    // Frame 2: missile still overlapping enemy 0, but must NOT deal damage again
    testCombat.update(0.016f, dummyPlayer, combatEnemies);
    EXPECT_EQ(combatEnemies[0]->health(), enemy0Hp);

    // 8. Enemy projectile collision with player
    testCombat.clearProjectiles();
    dummyPlayer.setPosition({500.f, 300.f});
    const float playerHpBefore = dummyPlayer.health();
    testCombat.addProjectile(std::make_unique<ss::Projectile>(
        sf::Vector2f(495.f, 300.f), sf::Vector2f(1.f, 0.f), 100.f, 7.f, 20.f, 1.0f, 0,
        ss::ProjectileFaction::Enemy, sf::Color::Magenta, sf::Color::White
    ));
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(1));
    testCombat.update(0.016f, dummyPlayer, combatEnemies);
    EXPECT_EQ(testCombat.projectileCount(), static_cast<std::size_t>(0)); // Consumed on player impact
    EXPECT_TRUE(dummyPlayer.health() < playerHpBefore);

    // 9. RangedEnemy shooting & attack cooldown
    ss::RangedEnemy shooter({400.f, 400.f});
    shooter.updateAI(0.1f, {450.f, 400.f}); // Within attack range
    shooter.update(shooter.rangedAttackTimer() + 0.1f); // Elapse initial timer
    EXPECT_TRUE(shooter.canShoot());

    auto shotMissile = shooter.shoot({450.f, 400.f});
    EXPECT_TRUE(shotMissile != nullptr);
    EXPECT_TRUE(shotMissile->faction() == ss::ProjectileFaction::Enemy);
    EXPECT_FALSE(shooter.canShoot()); // Now on cooldown!

    // 10. Armor and health mitigation interaction
    ss::TankEnemy armorTank({600.f, 300.f}); // Armor = 25
    const float rawDamage = 50.f;
    const float dealtDamage = armorTank.takeDamage({rawDamage, false});
    // Mitigated damage = 50 * (100 / (100 + 25)) = 40
    EXPECT_NEAR(dealtDamage, 40.f, 0.01f);
    EXPECT_NEAR(armorTank.health(), 200.f - 40.f, 0.01f);

    // 11. Elemental Spell On-Hit Behavior: Fire (Burn DoT)
    auto fireSpellDef = std::make_shared<ss::SpellDefinition>(
        "TestFire", "Burns", ss::ElementSet{ss::Element::Fire},
        sf::Color::Red, sf::Color::Yellow,
        ss::ProjectileProfile{1.0f, 1.0f, 8.f, 1.0f, 1.0f, 0.f, 0, 0}
    );
    fireSpellDef->addEffect(std::make_unique<ss::BurnEffect>(0.5f, 2.0f));

    ss::MeleeEnemy burnVictim({700.f, 300.f});
    testCombat.clearProjectiles();
    testCombat.addProjectile(std::make_unique<ss::Projectile>(
        sf::Vector2f(695.f, 300.f), sf::Vector2f(1.f, 0.f), 100.f, 8.f, 20.f, 1.0f, 0,
        ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow, fireSpellDef
    ));
    std::vector<std::unique_ptr<ss::Enemy>> fireVictims;
    fireVictims.push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(700.f, 300.f)));
    testCombat.update(0.016f, dummyPlayer, fireVictims);

    EXPECT_TRUE(fireVictims[0]->hasStatus(ss::StatusKind::Burn));
    const float hpAfterHit = fireVictims[0]->health();
    fireVictims[0]->update(0.5f); // Burn DoT ticks
    EXPECT_TRUE(fireVictims[0]->health() < hpAfterHit);

    // 12. Elemental Spell On-Hit Behavior: Ice (Slow)
    auto iceSpellDef = std::make_shared<ss::SpellDefinition>(
        "TestIce", "Slows", ss::ElementSet{ss::Element::Ice},
        sf::Color::Blue, sf::Color::White,
        ss::ProjectileProfile{1.0f, 1.0f, 8.f, 1.0f, 1.0f, 0.f, 0, 0}
    );
    iceSpellDef->addEffect(std::make_unique<ss::SlowEffect>(0.5f, 2.0f));

    std::vector<std::unique_ptr<ss::Enemy>> iceVictims;
    iceVictims.push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(700.f, 300.f)));
    const float speedNormal = iceVictims[0]->currentMoveSpeed();

    testCombat.clearProjectiles();
    testCombat.addProjectile(std::make_unique<ss::Projectile>(
        sf::Vector2f(695.f, 300.f), sf::Vector2f(1.f, 0.f), 100.f, 8.f, 15.f, 1.0f, 0,
        ss::ProjectileFaction::Player, sf::Color::Blue, sf::Color::White, iceSpellDef
    ));
    testCombat.update(0.016f, dummyPlayer, iceVictims);

    EXPECT_TRUE(iceVictims[0]->hasStatus(ss::StatusKind::Slow));
    EXPECT_TRUE(iceVictims[0]->currentMoveSpeed() < speedNormal);
    EXPECT_NEAR(iceVictims[0]->currentMoveSpeed(), speedNormal * 0.5f, 0.01f);
}

