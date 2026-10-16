#include "core/Random.hpp"

#include "core/MathUtils.hpp"

namespace ss {

Random::Random(unsigned seed)
    : m_engine(seed == 0 ? std::random_device{}() : seed)
{
}

float Random::range(float min, float max)
{
    if (max <= min) {
        return min;
    }
    return std::uniform_real_distribution<float>(min, max)(m_engine);
}

int Random::rangeInt(int min, int max)
{
    if (max <= min) {
        return min;
    }
    return std::uniform_int_distribution<int>(min, max)(m_engine);
}

bool Random::chance(float probability)
{
    return range(0.f, 1.f) < probability;
}

float Random::angle()
{
    return range(0.f, 2.f * math::kPi);
}

sf::Vector2f Random::unitVector()
{
    return math::fromAngle(angle());
}

unsigned Random::nextSeed()
{
    // Never return 0, because 0 means "random seed" for the constructor.
    const unsigned value = static_cast<unsigned>(m_engine());
    return value == 0 ? 1u : value;
}

} // namespace ss
