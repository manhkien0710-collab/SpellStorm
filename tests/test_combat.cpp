#include "TestHelper.hpp"
#include "combat/Damage.hpp"
#include "combat/StatusEffect.hpp"
#include "entities/Character.hpp"
#include "entities/BossEnemy.hpp"
#include "entities/TankEnemy.hpp"
#include "entities/Projectile.hpp"

namespace {

class DummyCharacter : public ss::Character {
public:
    DummyCharacter(float hp, float armor, float speed)
        : ss::Character({0.f, 0.f}, 15.f, hp, armor, speed)
    {
    }

    void draw(sf::RenderTarget&) const override {}
    void update(float dt) { updateCharacter(dt); }
};

} // namespace

void runCombatTests()
{
    // Test damage mitigation formula
    EXPECT_NEAR(ss::mitigateDamage(100.f, 0.f), 100.f, 0.01f);
    EXPECT_NEAR(ss::mitigateDamage(100.f, 100.f), 50.f, 0.01f);
    EXPECT_NEAR(ss::mitigateDamage(100.f, 300.f), 25.f, 0.01f);

    // Test character taking damage
    DummyCharacter ch(100.f, 50.f, 100.f);
    EXPECT_EQ(ch.health(), 100.f);
    EXPECT_TRUE(ch.isAlive());

    // 100 dmg mitigated by 50 armor => 100 * (100 / 150) = 66.6667
    ch.takeDamage({100.f, false});
    EXPECT_NEAR(ch.health(), 33.333f, 0.1f);

    // Test healing
    ch.heal(20.f);
    EXPECT_NEAR(ch.health(), 53.333f, 0.1f);

    // Test status effects (slow)
    ch.addStatus(std::make_unique<ss::SlowStatus>(0.4f, 2.0f));
    EXPECT_TRUE(ch.hasStatus(ss::StatusKind::Slow));
    EXPECT_NEAR(ch.currentMoveSpeed(), 60.f, 0.01f);

    // Test HealthComponent in isolation (pure unit test, SRP)
    ss::HealthComponent health(200.f, 100.f);
    EXPECT_EQ(health.current(), 200.f);
    EXPECT_EQ(health.max(), 200.f);
    EXPECT_EQ(health.ratio(), 1.f);
    // 100 damage with 100 armor => 50 dealt
    float dealt = health.takeDamage({100.f, false});
    EXPECT_NEAR(dealt, 50.f, 0.01f);
    EXPECT_NEAR(health.current(), 150.f, 0.01f);
    EXPECT_NEAR(health.ratio(), 0.75f, 0.01f);

    // =========================================================
    // Boss Size, Durability & Combat Mechanics Tests
    // =========================================================
    ss::BossEnemy boss({500.f, 500.f});
    ss::TankEnemy tank({600.f, 600.f});

    EXPECT_TRUE(boss.isBoss());
    EXPECT_FALSE(tank.isBoss());
    EXPECT_EQ(boss.type(), ss::EnemyType::Boss);
    EXPECT_EQ(boss.name(), "ANCIENT GOLEM OVERLORD");

    // Durability verification: 8-15x ordinary tank enemy health
    EXPECT_EQ(boss.maxHealth(), 1800.f);
    EXPECT_EQ(tank.maxHealth(), 200.f);
    EXPECT_GE(boss.maxHealth(), tank.maxHealth() * 8.f);
    EXPECT_LE(boss.maxHealth(), tank.maxHealth() * 15.f);

    // Size verification: substantially larger collision radius
    EXPECT_EQ(boss.radius(), 50.f);
    EXPECT_GE(boss.radius(), tank.radius() * 1.8f);

    // Boss special attack volley mechanics
    EXPECT_TRUE(boss.canFireVolley());
    auto volley = boss.fireVolley({500.f, 300.f});
    EXPECT_EQ(volley.size(), 3u);
    EXPECT_FALSE(boss.canFireVolley());
    for (const auto& p : volley) {
        EXPECT_EQ(p->faction(), ss::ProjectileFaction::Enemy);
        EXPECT_GT(p->damage(), 0.f);
    }

    // Damage & defeat verification
    boss.takeDamage({900.f, false});
    EXPECT_NEAR(boss.healthRatio(), 0.697f, 0.05f);
    EXPECT_TRUE(boss.isAlive());

    // Defeat boss
    boss.takeDamage({2000.f, true}); // Critical finishing blow
    EXPECT_FALSE(boss.isAlive());
    EXPECT_EQ(boss.expReward(), 250);
}
