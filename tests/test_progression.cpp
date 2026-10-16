#include "TestHelper.hpp"
#include "core/Arena.hpp"
#include "core/Random.hpp"
#include "entities/Enemy.hpp"
#include "entities/ExperiencePickup.hpp"
#include "entities/MeleeEnemy.hpp"
#include "entities/Player.hpp"
#include "entities/Projectile.hpp"
#include "entities/RangedEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "systems/CombatSystem.hpp"
#include "systems/EffectSystem.hpp"
#include "systems/EnemySpawner.hpp"
#include "systems/ExperienceSystem.hpp"
#include "systems/PlayerProgression.hpp"
#include "systems/UpgradeSystem.hpp"
#include "ui/LevelUpOverlay.hpp"

#include <cmath>
#include <vector>

void runProgressionTests()
{
    // =========================================================
    // 1. Experience System Tests
    // =========================================================

    // Correct EXP values and initialization
    ss::ExperiencePickup pickup1({100.f, 100.f}, 10);
    EXPECT_EQ(pickup1.expValue(), 10);
    EXPECT_FALSE(pickup1.isCollected());
    EXPECT_TRUE(pickup1.isAlive());

    ss::ExperiencePickup pickup2({150.f, 150.f}, 15);
    EXPECT_EQ(pickup2.expValue(), 15);

    ss::ExperiencePickup pickup3({200.f, 200.f}, 30);
    EXPECT_EQ(pickup3.expValue(), 30);

    // Pickup can be collected only once
    pickup1.collect();
    EXPECT_TRUE(pickup1.isCollected());
    EXPECT_FALSE(pickup1.isAlive());

    pickup1.collect(); // Re-collecting is idempotent
    EXPECT_TRUE(pickup1.isCollected());

    // Enemy EXP values & single reward guarantee
    ss::MeleeEnemy melee({300.f, 300.f});
    EXPECT_EQ(melee.expReward(), 10);
    EXPECT_FALSE(melee.isRewardClaimed());
    melee.claimReward();
    EXPECT_TRUE(melee.isRewardClaimed());

    ss::RangedEnemy ranged({400.f, 400.f});
    EXPECT_EQ(ranged.expReward(), 15);
    EXPECT_FALSE(ranged.isRewardClaimed());

    ss::TankEnemy tank({500.f, 500.f});
    EXPECT_EQ(tank.expReward(), 30);
    EXPECT_FALSE(tank.isRewardClaimed());

    // ExperienceSystem collection and removal
    ss::ExperienceSystem expSys;
    ss::PlayerProgression prog;
    ss::EffectSystem fx;
    ss::Player testPlayer({500.f, 500.f});

    // Spawn pickup far from player
    expSys.spawnPickup({100.f, 100.f}, 20);
    EXPECT_EQ(expSys.pickupCount(), static_cast<std::size_t>(1));

    // Update with player out of pickup range
    expSys.update(0.016f, testPlayer, prog, fx);
    EXPECT_EQ(expSys.pickupCount(), static_cast<std::size_t>(1));
    EXPECT_EQ(prog.currentExp(), 0);

    // Move player inside collection radius of pickup
    testPlayer.setPosition({105.f, 100.f});
    expSys.update(0.016f, testPlayer, prog, fx);
    // Pickup collected and removed
    EXPECT_EQ(expSys.pickupCount(), static_cast<std::size_t>(0));
    EXPECT_EQ(prog.currentExp(), 20);

    // =========================================================
    // 2. Player Leveling & Progression Tests
    // =========================================================

    ss::PlayerProgression pProg(1);
    EXPECT_EQ(pProg.level(), 1);
    EXPECT_EQ(pProg.currentExp(), 0);
    EXPECT_EQ(pProg.expForNextLevel(), 25);
    EXPECT_FALSE(pProg.hasPendingLevelUp());

    // A. EXP below threshold does not level up
    int lUp = pProg.addExp(20);
    EXPECT_EQ(lUp, 0);
    EXPECT_EQ(pProg.level(), 1);
    EXPECT_EQ(pProg.currentExp(), 20);
    EXPECT_FALSE(pProg.hasPendingLevelUp());
    EXPECT_NEAR(pProg.progressRatio(), 20.f / 25.f, 0.001f);

    // B. EXP exactly equal to threshold levels up correctly
    lUp = pProg.addExp(5); // 20 + 5 = 25
    EXPECT_EQ(lUp, 1);
    EXPECT_EQ(pProg.level(), 2);
    EXPECT_EQ(pProg.currentExp(), 0);
    EXPECT_EQ(pProg.expForNextLevel(), 45); // Level 2 requires 25 + 20 = 45
    EXPECT_TRUE(pProg.hasPendingLevelUp());
    EXPECT_EQ(pProg.pendingLevelUps(), 1);
    EXPECT_NEAR(pProg.progressRatio(), 0.f, 0.001f);

    pProg.consumeLevelUp();
    EXPECT_FALSE(pProg.hasPendingLevelUp());
    EXPECT_EQ(pProg.pendingLevelUps(), 0);

    // C. Excess EXP is preserved
    // Current level 2, requires 45 EXP. We add 55 EXP: 45 consumed for level-up, 10 carried over!
    lUp = pProg.addExp(55);
    EXPECT_EQ(lUp, 1);
    EXPECT_EQ(pProg.level(), 3);
    EXPECT_EQ(pProg.currentExp(), 10);
    EXPECT_EQ(pProg.expForNextLevel(), 65); // Level 3 requires 25 + 40 = 65
    EXPECT_TRUE(pProg.hasPendingLevelUp());
    pProg.consumeLevelUp();

    // D. One award can trigger multiple level-ups
    ss::PlayerProgression multiProg(1);
    // Level 1 -> 2 needs 25
    // Level 2 -> 3 needs 45
    // Level 3 -> 4 needs 65
    // Total for level 4 = 135. Adding 145 gives level 4 with 10 leftover EXP!
    int multiGained = multiProg.addExp(145);
    EXPECT_EQ(multiGained, 3);
    EXPECT_EQ(multiProg.level(), 4);
    EXPECT_EQ(multiProg.currentExp(), 10);
    EXPECT_EQ(multiProg.pendingLevelUps(), 3);

    multiProg.consumeLevelUp();
    EXPECT_EQ(multiProg.pendingLevelUps(), 2);
    multiProg.consumeLevelUp();
    EXPECT_EQ(multiProg.pendingLevelUps(), 1);
    multiProg.consumeLevelUp();
    EXPECT_EQ(multiProg.pendingLevelUps(), 0);
    EXPECT_FALSE(multiProg.hasPendingLevelUp());

    // E. Invalid / negative EXP awards handled safely
    const int expBefore = pProg.currentExp();
    const int lvlBefore = pProg.level();
    EXPECT_EQ(pProg.addExp(-100), 0);
    EXPECT_EQ(pProg.addExp(0), 0);
    EXPECT_EQ(pProg.currentExp(), expBefore);
    EXPECT_EQ(pProg.level(), lvlBefore);

    // Progress consistency
    EXPECT_TRUE(pProg.progressRatio() >= 0.f && pProg.progressRatio() <= 1.f);

    // =========================================================
    // 3. Upgrade System Tests
    // =========================================================

    ss::UpgradeManager upMgr;
    ss::Player upPlayer({640.f, 360.f});

    // A. Every supported upgrade modifies the correct statistic
    // 1. Max Health
    const float hpMaxBefore = upPlayer.maxHealth();
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::MaxHealth, upPlayer));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::MaxHealth), 1);
    EXPECT_NEAR(upPlayer.maxHealth(), hpMaxBefore + 40.f, 0.001f);

    // 2. Move Speed
    const float spdBefore = upPlayer.baseMoveSpeed();
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::MoveSpeed, upPlayer));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::MoveSpeed), 1);
    EXPECT_NEAR(upPlayer.baseMoveSpeed(), spdBefore * 1.12f, 0.001f);

    // 3. Damage
    const float dmgBefore = upPlayer.damageMultiplier();
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::Damage, upPlayer));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::Damage), 1);
    EXPECT_NEAR(upPlayer.damageMultiplier(), dmgBefore * 1.20f, 0.001f);

    // 4. Cast Cooldown
    const float cdBefore = upPlayer.baseCastCooldown();
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::CastCooldown, upPlayer));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::CastCooldown), 1);
    EXPECT_NEAR(upPlayer.baseCastCooldown(), cdBefore * 0.88f, 0.001f);

    // 5. Projectile Speed
    const float projSpdBefore = upPlayer.projectileSpeedMultiplier();
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::ProjectileSpeed, upPlayer));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::ProjectileSpeed), 1);
    EXPECT_NEAR(upPlayer.projectileSpeedMultiplier(), projSpdBefore * 1.20f, 0.001f);

    // 6. Projectile Lifetime
    const float projLifeBefore = upPlayer.projectileLifetimeMultiplier();
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::ProjectileLifetime, upPlayer));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::ProjectileLifetime), 1);
    EXPECT_NEAR(upPlayer.projectileLifetimeMultiplier(), projLifeBefore * 1.25f, 0.001f);

    // B. Upgrade Caps Respected
    // MaxHealth is rank 1. Max rank is 5. Apply 4 more times:
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::MaxHealth, upPlayer)); // 2
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::MaxHealth, upPlayer)); // 3
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::MaxHealth, upPlayer)); // 4
    EXPECT_TRUE(upMgr.applyUpgrade(ss::UpgradeType::MaxHealth, upPlayer)); // 5
    EXPECT_TRUE(upMgr.isMaxRank(ss::UpgradeType::MaxHealth));
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::MaxHealth), 5);

    // Applying beyond cap fails and does not modify stat
    const float cappedHp = upPlayer.maxHealth();
    EXPECT_FALSE(upMgr.applyUpgrade(ss::UpgradeType::MaxHealth, upPlayer));
    EXPECT_EQ(upPlayer.maxHealth(), cappedHp);
    EXPECT_EQ(upMgr.getRank(ss::UpgradeType::MaxHealth), 5);

    // C. Duplicate choices prevented and maxed perks excluded
    ss::Random upRng(999);
    auto perkOptions = upMgr.getAvailableUpgrades(3, upRng);
    EXPECT_TRUE(perkOptions.size() <= 3);

    bool hasMaxHealth = false;
    for (std::size_t i = 0; i < perkOptions.size(); ++i) {
        if (perkOptions[i].type == ss::UpgradeType::MaxHealth) {
            hasMaxHealth = true;
        }
        for (std::size_t j = i + 1; j < perkOptions.size(); ++j) {
            EXPECT_TRUE(perkOptions[i].type != perkOptions[j].type); // Distinct choices!
        }
    }
    EXPECT_FALSE(hasMaxHealth); // Capped perk not offered

    // =========================================================
    // 4. Pause and Resumption Verification
    // =========================================================

    ss::Arena simArena({640.f, 360.f}, {1000.f, 600.f});
    ss::EffectSystem simFx;
    ss::CombatSystem simCombat(simArena, simFx);
    ss::Random simRng(42);
    ss::EnemySpawner simSpawner(simArena, simRng);
    std::vector<std::unique_ptr<ss::Enemy>> simEnemies;

    simEnemies.push_back(std::make_unique<ss::MeleeEnemy>(sf::Vector2f(200.f, 200.f)));
    simEnemies.push_back(std::make_unique<ss::RangedEnemy>(sf::Vector2f(400.f, 400.f)));
    simCombat.addProjectile(std::make_unique<ss::Projectile>(
        sf::Vector2f(300.f, 300.f), sf::Vector2f(1.f, 0.f), 200.f, 8.f, 25.f, 2.0f, 0,
        ss::ProjectileFaction::Player, sf::Color::Red, sf::Color::Yellow
    ));

    const sf::Vector2f e0PosBefore = simEnemies[0]->position();
    const sf::Vector2f e1PosBefore = simEnemies[1]->position();
    const std::size_t enemyCountBefore = simEnemies.size();
    const std::size_t projCountBefore = simCombat.projectileCount();
    const sf::Vector2f projPosBefore = simCombat.projectiles()[0]->position();
    const float spawnerTimerBefore = simSpawner.spawnTimer();

    // Simulate 10 paused frames (when LevelUpOverlay is active, simulation is paused)
    bool isLevelUpActive = true;
    for (int tick = 0; tick < 10; ++tick) {
        if (!isLevelUpActive) {
            // Normal simulation tick (skipped during pause)
            simSpawner.update(0.016f, testPlayer.position(), simEnemies);
            for (auto& e : simEnemies) {
                e->updateAI(0.016f, testPlayer.position());
                e->update(0.016f);
            }
            simCombat.update(0.016f, testPlayer, simEnemies);
        }
    }

    // Assert absolute stillness during pause
    EXPECT_EQ(simEnemies[0]->position().x, e0PosBefore.x);
    EXPECT_EQ(simEnemies[0]->position().y, e0PosBefore.y);
    EXPECT_EQ(simEnemies[1]->position().x, e1PosBefore.x);
    EXPECT_EQ(simEnemies[1]->position().y, e1PosBefore.y);
    EXPECT_EQ(simEnemies.size(), enemyCountBefore);
    EXPECT_EQ(simCombat.projectileCount(), projCountBefore);
    EXPECT_EQ(simCombat.projectiles()[0]->position().x, projPosBefore.x);
    EXPECT_EQ(simCombat.projectiles()[0]->position().y, projPosBefore.y);
    EXPECT_EQ(simSpawner.spawnTimer(), spawnerTimerBefore);

    // Resume gameplay
    isLevelUpActive = false;
    for (int tick = 0; tick < 5; ++tick) {
        if (!isLevelUpActive) {
            simSpawner.update(0.016f, testPlayer.position(), simEnemies);
            for (auto& e : simEnemies) {
                e->updateAI(0.016f, testPlayer.position());
                e->update(0.016f);
            }
            simCombat.update(0.016f, testPlayer, simEnemies);
        }
    }

    // Now simulation has cleanly resumed
    EXPECT_TRUE(simCombat.projectiles()[0]->position().x > projPosBefore.x);

    // =========================================================
    // 5. Regression Check: Melee Enemy Movement Speed
    // =========================================================
    // Preserve user's manually adjusted melee enemy speed (105.f)
    ss::MeleeEnemy speedCheckEnemy({0.f, 0.f});
    EXPECT_EQ(speedCheckEnemy.baseMoveSpeed(), 105.f);

    // =========================================================
    // 6. Projectile Piercing Limits and Damage Falloff Tests
    // =========================================================
    {
        // Piercing balance constants
        EXPECT_EQ(ss::Projectile::kMaxAllowedPierce, 3);
        EXPECT_NEAR(ss::Projectile::kPierceDamageFalloff, 0.80f, 0.001f);
        EXPECT_NEAR(ss::Projectile::kMinDamageMultiplier, 0.25f, 0.001f);

        // Non-piercing projectile (pierce = 0)
        ss::Projectile pSingle({0.f, 0.f}, {1.f, 0.f}, 100.f, 10.f, 50.f, 2.0f, 0,
                               ss::ProjectileFaction::Player, sf::Color::White, sf::Color::White);
        EXPECT_EQ(pSingle.pierceRemaining(), 0);
        EXPECT_EQ(pSingle.hitCount(), 0);
        EXPECT_NEAR(pSingle.damage(), 50.f, 0.01f);
        EXPECT_TRUE(pSingle.isAlive());

        // First hit consumes non-piercing projectile
        pSingle.recordHit(10);
        pSingle.decrementPierce();
        EXPECT_FALSE(pSingle.isAlive());
        EXPECT_EQ(pSingle.hitCount(), 1);

        // Piercing projectile (pierce = 2, base damage = 100)
        ss::Projectile pPierce({0.f, 0.f}, {1.f, 0.f}, 100.f, 10.f, 100.f, 2.0f, 2,
                               ss::ProjectileFaction::Player, sf::Color::White, sf::Color::White);
        EXPECT_EQ(pPierce.pierceRemaining(), 2);
        EXPECT_EQ(pPierce.hitCount(), 0);
        EXPECT_NEAR(pPierce.damage(), 100.f, 0.01f);

        // Hit 1: pierce remaining goes 2 -> 1, damage reduced by 20% (100 * 0.8 = 80)
        pPierce.recordHit(21);
        pPierce.decrementPierce();
        EXPECT_TRUE(pPierce.isAlive());
        EXPECT_EQ(pPierce.pierceRemaining(), 1);
        EXPECT_EQ(pPierce.hitCount(), 1);
        EXPECT_NEAR(pPierce.damage(), 80.f, 0.01f);

        // Hit 2: pierce remaining goes 1 -> 0, damage reduced by another 20% (80 * 0.8 = 64)
        pPierce.recordHit(22);
        pPierce.decrementPierce();
        EXPECT_TRUE(pPierce.isAlive());
        EXPECT_EQ(pPierce.pierceRemaining(), 0);
        EXPECT_EQ(pPierce.hitCount(), 2);
        EXPECT_NEAR(pPierce.damage(), 64.f, 0.01f);

        // Hit 3: pierce remaining 0 exhausted -> projectile destroyed
        pPierce.recordHit(23);
        pPierce.decrementPierce();
        EXPECT_FALSE(pPierce.isAlive());
        EXPECT_EQ(pPierce.hitCount(), 3);

        // Damage floor clamping: damage never drops below 25% of initial damage
        ss::Projectile pFloor({0.f, 0.f}, {1.f, 0.f}, 100.f, 10.f, 100.f, 2.0f, 10,
                              ss::ProjectileFaction::Player, sf::Color::White, sf::Color::White);
        for (int i = 0; i < 10; ++i) {
            pFloor.decrementPierce();
        }
        EXPECT_TRUE(pFloor.damage() >= 25.f);

        // Multi-hit prevention against same enemy instance
        ss::Projectile pHitTrack({0.f, 0.f}, {1.f, 0.f}, 100.f, 10.f, 50.f, 2.0f, 2,
                                 ss::ProjectileFaction::Player, sf::Color::White, sf::Color::White);
        EXPECT_TRUE(pHitTrack.canHit(101));
        pHitTrack.recordHit(101);
        EXPECT_FALSE(pHitTrack.canHit(101));
        EXPECT_TRUE(pHitTrack.canHit(102));

        // Player bonus pierce limits
        ss::Player pTest({0.f, 0.f});
        EXPECT_EQ(pTest.bonusPierce(), 0);
        pTest.addBonusPierce(1);
        EXPECT_EQ(pTest.bonusPierce(), 1);
        pTest.addBonusPierce(1);
        EXPECT_EQ(pTest.bonusPierce(), 2);
        // Exceeding cap of 2 is clamped
        pTest.addBonusPierce(5);
        EXPECT_EQ(pTest.bonusPierce(), 2);

        // Piercing upgrade perk in UpgradeManager
        ss::UpgradeManager upPierceMgr;
        EXPECT_EQ(upPierceMgr.getRank(ss::UpgradeType::Piercing), 0);
        EXPECT_FALSE(upPierceMgr.isMaxRank(ss::UpgradeType::Piercing));

        ss::Player playerUpgradeTest({0.f, 0.f});
        EXPECT_TRUE(upPierceMgr.applyUpgrade(ss::UpgradeType::Piercing, playerUpgradeTest));
        EXPECT_EQ(playerUpgradeTest.bonusPierce(), 1);
        EXPECT_EQ(upPierceMgr.getRank(ss::UpgradeType::Piercing), 1);

        EXPECT_TRUE(upPierceMgr.applyUpgrade(ss::UpgradeType::Piercing, playerUpgradeTest));
        EXPECT_EQ(playerUpgradeTest.bonusPierce(), 2);
        EXPECT_EQ(upPierceMgr.getRank(ss::UpgradeType::Piercing), 2);
        EXPECT_TRUE(upPierceMgr.isMaxRank(ss::UpgradeType::Piercing));

        // Exceeding max rank fails
        EXPECT_FALSE(upPierceMgr.applyUpgrade(ss::UpgradeType::Piercing, playerUpgradeTest));
        EXPECT_EQ(playerUpgradeTest.bonusPierce(), 2);
    }

    // =========================================================
    // 7. Spawner Difficulty Progression Tests
    // =========================================================
    {
        ss::Arena diffArena({640.f, 360.f}, {1200.f, 700.f});
        ss::Random diffRng(12345);
        ss::EnemySpawner diffSpawner(diffArena, diffRng);

        // Initial spawner state
        EXPECT_NEAR(diffSpawner.baseInterval(), 2.2f, 0.001f);
        EXPECT_NEAR(diffSpawner.minInterval(), 0.55f, 0.001f);
        EXPECT_EQ(diffSpawner.baseMaxEnemies(), static_cast<std::size_t>(20));
        EXPECT_EQ(diffSpawner.maxEnemiesCap(), static_cast<std::size_t>(36));
        EXPECT_EQ(diffSpawner.maxEnemies(), static_cast<std::size_t>(20));

        // Advance 60s (halfway to 120s max difficulty)
        std::vector<std::unique_ptr<ss::Enemy>> dummyEnemies;
        diffSpawner.update(60.f, {640.f, 360.f}, dummyEnemies);
        EXPECT_NEAR(diffSpawner.elapsedTime(), 60.f, 0.01f);
        // Interval has tightened: 2.2 - 0.5 * (2.2 - 0.55) = 1.375f
        EXPECT_NEAR(diffSpawner.currentInterval(), 1.375f, 0.05f);
        // Enemy cap has scaled up: 20 + 0.5 * 16 = 28
        EXPECT_EQ(diffSpawner.maxEnemies(), static_cast<std::size_t>(28));

        // Advance past 120s (full difficulty scaling)
        diffSpawner.update(70.f, {640.f, 360.f}, dummyEnemies);
        EXPECT_TRUE(diffSpawner.elapsedTime() >= 120.f);
        EXPECT_NEAR(diffSpawner.currentInterval(), 0.55f, 0.01f);
        EXPECT_EQ(diffSpawner.maxEnemies(), static_cast<std::size_t>(36));

        // Enemy health scaling in late match (at >50s)
        auto lateEnemy = diffSpawner.spawnEnemy({640.f, 360.f}, ss::EnemyType::Tank);
        // Base tank health is 180. Late game scaling at 130s: 1.0 + (130-50)/600 = 1.133x -> ~204 HP
        EXPECT_TRUE(lateEnemy->maxHealth() > 180.f);
        EXPECT_TRUE(lateEnemy->maxHealth() <= 180.f * 1.26f); // Capped at +25%
    }

    // =========================================================
    // 8. Level-Up UI Two-Step Selection and Confirmation Tests
    // =========================================================
    {
        ss::LevelUpOverlay ui;
        EXPECT_FALSE(ui.isOpen());

        std::vector<ss::UpgradeInfo> sampleOptions = {
            {ss::UpgradeType::Piercing, "Piercing Lance", "+1 projectile piercing", 1, 2},
            {ss::UpgradeType::Damage, "Arcane Power", "+15% spell damage", 2, 5},
            {ss::UpgradeType::MoveSpeed, "Swiftness", "+10% move speed", 1, 5}
        };

        ui.open(sampleOptions);
        EXPECT_TRUE(ui.isOpen());
        EXPECT_EQ(ui.selectedIndex(), -1);
        EXPECT_FALSE(ui.hasValidSelection());
        EXPECT_TRUE(ui.selectedUpgrade() == nullptr);

        // Before grace timer expires (0.15s), clicks must be ignored
        sf::Vector2f card0Pos(250.f, 300.f);
        sf::Vector2f card1Pos(565.f, 300.f);

        // During grace period (dt = 0), click is rejected
        EXPECT_FALSE(ui.handleMouseClick(card0Pos));
        EXPECT_EQ(ui.selectedIndex(), -1);

        // Advance past grace timer
        ui.update(0.2f);

        // STEP 1: Clicking Card 0 SELECTS it, but does NOT confirm / apply
        bool confirmedOnCardClick = ui.handleMouseClick(card0Pos);
        EXPECT_FALSE(confirmedOnCardClick); // Must NOT apply upgrade!
        EXPECT_TRUE(ui.isOpen());            // Modal stays open!
        EXPECT_EQ(ui.selectedIndex(), 0);   // Selected card is 0
        EXPECT_TRUE(ui.hasValidSelection());
        EXPECT_EQ(ui.selectedUpgrade()->type, ss::UpgradeType::Piercing);

        // Clicking Card 1 SWITCHES selection, does NOT confirm
        bool confirmedOnSwitch = ui.handleMouseClick(card1Pos);
        EXPECT_FALSE(confirmedOnSwitch);
        EXPECT_TRUE(ui.isOpen());
        EXPECT_EQ(ui.selectedIndex(), 1);
        EXPECT_EQ(ui.selectedUpgrade()->type, ss::UpgradeType::Damage);

        // Clicking outside any card or button returns false, preserves selection
        sf::Vector2f outsidePos(10.f, 10.f);
        EXPECT_FALSE(ui.handleMouseClick(outsidePos));
        EXPECT_EQ(ui.selectedIndex(), 1);
        EXPECT_TRUE(ui.isOpen());

        // STEP 2: Explicit Confirmation via Confirm Button
        const sf::FloatRect btnBounds = ui.confirmButtonBounds();
        const sf::Vector2f btnCenter(btnBounds.left + btnBounds.width * 0.5f,
                                     btnBounds.top + btnBounds.height * 0.5f);

        bool confirmedOnBtnClick = ui.handleMouseClick(btnCenter);
        EXPECT_TRUE(confirmedOnBtnClick); // Confirmed!
        EXPECT_EQ(ui.selectedUpgrade()->type, ss::UpgradeType::Damage);

        // Test Enter / Space key confirmation
        ui.open(sampleOptions);
        ui.update(0.2f);
        // Pressing Return with NO card selected does NOT confirm
        EXPECT_FALSE(ui.handleKeyPress(sf::Keyboard::Return));
        EXPECT_FALSE(ui.tryConfirm());

        // Number keys 1, 2, 3 select cards
        ui.handleKeyPress(sf::Keyboard::Num3);
        EXPECT_EQ(ui.selectedIndex(), 2);
        EXPECT_TRUE(ui.hasValidSelection());

        // Pressing Space WITH card selected CONFIRMS
        EXPECT_TRUE(ui.handleKeyPress(sf::Keyboard::Space));
        EXPECT_EQ(ui.selectedUpgrade()->type, ss::UpgradeType::MoveSpeed);

        // Close overlay
        ui.close();
        EXPECT_FALSE(ui.isOpen());
        EXPECT_EQ(ui.selectedIndex(), -1);
    }
}
