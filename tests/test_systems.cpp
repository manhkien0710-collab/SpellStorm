#include "TestHelper.hpp"
#include "core/Arena.hpp"
#include "core/GameSession.hpp"
#include "core/MathUtils.hpp"
#include "core/Random.hpp"
#include "entities/Player.hpp"
#include "spells/SpellRegistry.hpp"
#include "systems/EffectSystem.hpp"
#include "systems/PowerUpManager.hpp"

static void testPowerUpActiveAbilityTiming()
{
    ss::ActiveTemporaryAbility ability;
    EXPECT_FALSE(ability.active);
    EXPECT_NEAR(ability.remainingDuration, 0.f, 0.001f);
    EXPECT_NEAR(ability.durationRatio(), 0.f, 0.001f);

    // Activate Arcane Overdrive
    ability.activate(ss::PowerUpType::ArcaneOverdrive);
    EXPECT_TRUE(ability.active);
    EXPECT_EQ(ability.type, ss::PowerUpType::ArcaneOverdrive);
    EXPECT_NEAR(ability.remainingDuration, 5.0f, 0.001f);
    EXPECT_NEAR(ability.durationRatio(), 1.0f, 0.001f);

    // Update by 1.5 seconds
    ability.update(1.5f);
    EXPECT_TRUE(ability.active);
    EXPECT_NEAR(ability.remainingDuration, 3.5f, 0.001f);
    EXPECT_NEAR(ability.durationRatio(), 0.7f, 0.001f);

    // Update past expiration
    ability.update(3.6f);
    EXPECT_FALSE(ability.active);
    EXPECT_NEAR(ability.remainingDuration, 0.f, 0.001f);
    EXPECT_NEAR(ability.durationRatio(), 0.f, 0.001f);
}

static void testPowerUpRecollectionPolicy()
{
    ss::ActiveTemporaryAbility ability;
    ability.activate(ss::PowerUpType::ArcaneOverdrive);
    ability.update(2.0f); // 3.0s remaining
    EXPECT_NEAR(ability.remainingDuration, 3.0f, 0.001f);

    // Recollecting same type: refreshes back to 5.0s
    ability.refresh();
    EXPECT_TRUE(ability.active);
    EXPECT_EQ(ability.type, ss::PowerUpType::ArcaneOverdrive);
    EXPECT_NEAR(ability.remainingDuration, 5.0f, 0.001f);

    // Collecting a DIFFERENT type replaces currently active ability
    ability.update(1.0f); // 4.0s remaining
    ability.activate(ss::PowerUpType::PiercingSurge);
    EXPECT_TRUE(ability.active);
    EXPECT_EQ(ability.type, ss::PowerUpType::PiercingSurge);
    EXPECT_NEAR(ability.remainingDuration, 5.0f, 0.001f);

    // Switch to Elemental Burst
    ability.activate(ss::PowerUpType::ElementalBurst);
    EXPECT_TRUE(ability.active);
    EXPECT_EQ(ability.type, ss::PowerUpType::ElementalBurst);
    EXPECT_NEAR(ability.remainingDuration, 5.0f, 0.001f);
}

static void testPowerUpCombatModifiers()
{
    ss::Arena arena({500.f, 500.f}, {1000.f, 1000.f});
    ss::Random rng(123);
    ss::PowerUpManager manager(arena, rng);

    // Default: no active ability
    EXPECT_FALSE(manager.hasActiveAbility());
    EXPECT_NEAR(manager.castCooldownMultiplier(), 1.0f, 0.001f);
    EXPECT_EQ(manager.bonusPierce(), 0);
    EXPECT_FALSE(manager.isElementalBurstActive());

    // Arcane Overdrive (1.8x cast rate -> cooldown multiplier = 1/1.8)
    manager.activeAbility().activate(ss::PowerUpType::ArcaneOverdrive);
    EXPECT_TRUE(manager.hasActiveAbility());
    EXPECT_NEAR(manager.castCooldownMultiplier(), 1.0f / 1.8f, 0.001f);
    EXPECT_EQ(manager.bonusPierce(), 0);
    EXPECT_FALSE(manager.isElementalBurstActive());

    // Piercing Surge (+2 projectile pierce hits)
    manager.activeAbility().activate(ss::PowerUpType::PiercingSurge);
    EXPECT_TRUE(manager.hasActiveAbility());
    EXPECT_NEAR(manager.castCooldownMultiplier(), 1.0f, 0.001f);
    EXPECT_EQ(manager.bonusPierce(), 2);
    EXPECT_FALSE(manager.isElementalBurstActive());

    // Elemental Burst (Empowered twin burst)
    manager.activeAbility().activate(ss::PowerUpType::ElementalBurst);
    EXPECT_TRUE(manager.hasActiveAbility());
    EXPECT_NEAR(manager.castCooldownMultiplier(), 1.0f, 0.001f);
    EXPECT_EQ(manager.bonusPierce(), 0);
    EXPECT_TRUE(manager.isElementalBurstActive());
}

static void testPowerUpWorldSpawningAndCollection()
{
    ss::Arena arena({500.f, 500.f}, {1000.f, 1000.f});
    ss::Random rng(42);
    ss::PowerUpManager manager(arena, rng);
    ss::Player player({500.f, 500.f});
    ss::EffectSystem effects;

    EXPECT_EQ(manager.pickups().size(), 0u);

    // Explicit spawn near player
    manager.spawnPickup({510.f, 500.f}, ss::PowerUpType::PiercingSurge);
    EXPECT_EQ(manager.pickups().size(), 1u);
    EXPECT_TRUE(manager.pickups()[0].isAlive());

    // Update triggers collection because player position (500,500) overlaps pickup (510,500)
    manager.update(0.1f, player, effects);
    EXPECT_TRUE(manager.hasActiveAbility());
    EXPECT_EQ(manager.activeAbility().type, ss::PowerUpType::PiercingSurge);
    EXPECT_NEAR(manager.activeAbility().remainingDuration, 5.0f, 0.001f);
    EXPECT_EQ(manager.pickups().size(), 0u); // Collected item removed

    // Subsequent tick counts down
    manager.update(0.1f, player, effects);
    EXPECT_NEAR(manager.activeAbility().remainingDuration, 4.9f, 0.001f);
}

static void testPowerUpPauseSuspension()
{
    ss::Arena arena({500.f, 500.f}, {1000.f, 1000.f});
    ss::SpellRegistry registry;
    ss::GameSession session(arena, registry, 777);

    // Give player an active ability
    session.powerUpManager().activeAbility().activate(ss::PowerUpType::ArcaneOverdrive);
    EXPECT_TRUE(session.powerUpManager().hasActiveAbility());
    EXPECT_NEAR(session.powerUpManager().activeAbility().remainingDuration, 5.0f, 0.001f);

    // Open Level Up modal -> Game is paused
    std::vector<ss::UpgradeInfo> dummyChoices = {
        {ss::UpgradeType::Damage, "Might", "Boost", 0, 5, sf::Color::Red}
    };
    session.levelUpOverlay().open(dummyChoices);
    EXPECT_TRUE(session.isLevelUpOpen());
    EXPECT_FALSE(session.isPlaying());

    // Run game update for 2 seconds while paused
    session.update(2.0f, {500.f, 500.f}, false);

    // The active ability remaining duration MUST NOT decrease while paused!
    EXPECT_NEAR(session.powerUpManager().activeAbility().remainingDuration, 5.0f, 0.001f);

    // Close modal -> unpause
    session.levelUpOverlay().close();
    EXPECT_TRUE(session.isPlaying());

    // Run game update for 1 second while unpaused
    session.update(1.0f, {500.f, 500.f}, false);
    EXPECT_NEAR(session.powerUpManager().activeAbility().remainingDuration, 4.0f, 0.001f);
}

void runSystemsTests()
{
    // Test math utilities
    sf::Vector2f v{3.f, 4.f};
    EXPECT_NEAR(ss::math::length(v), 5.f, 0.001f);
    sf::Vector2f norm = ss::math::normalize(v);
    EXPECT_NEAR(norm.x, 0.6f, 0.001f);
    EXPECT_NEAR(norm.y, 0.8f, 0.001f);

    // Test EffectSystem updates
    ss::EffectSystem effects;
    effects.burst({0.f, 0.f}, sf::Color::Red, 10, 100.f, 0.5f);
    effects.update(0.3f);
    effects.update(0.3f);

    // Test Power-Up & Temporary Ability Subsystem
    testPowerUpActiveAbilityTiming();
    testPowerUpRecollectionPolicy();
    testPowerUpCombatModifiers();
    testPowerUpWorldSpawningAndCollection();
    testPowerUpPauseSuspension();
}

