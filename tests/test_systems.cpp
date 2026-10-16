#include "TestHelper.hpp"
#include "systems/EffectSystem.hpp"
#include "core/MathUtils.hpp"

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
    // After 0.6s all should expire
    effects.update(0.3f);
}
