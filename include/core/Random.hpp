#pragma once

#include <SFML/System/Vector2.hpp>

#include <random>

namespace ss {

// Thin wrapper over std::mt19937. Passing a fixed seed makes a whole match
// reproducible, which the simulation tests rely on.
class Random {
public:
    explicit Random(unsigned seed = 0); // 0 = non-deterministic seed

    float range(float min, float max);
    int rangeInt(int min, int max); // inclusive
    bool chance(float probability);
    float angle();
    sf::Vector2f unitVector();
    unsigned nextSeed();

private:
    std::mt19937 m_engine;
};

} // namespace ss
