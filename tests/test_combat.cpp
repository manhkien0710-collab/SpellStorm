#include "TestHelper.hpp"
#include "combat/Damage.hpp"
#include "combat/StatusEffect.hpp"
#include "entities/Character.hpp"

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
}
