#pragma once

#include <SFML/System/Vector2.hpp>

#include <cmath>

// Small vector-math helpers used across gameplay code.
namespace ss::math {

constexpr float kPi = 3.14159265358979f;
constexpr float kTwoPi = 6.283185307179586f;

inline float lengthSq(sf::Vector2f v) { return v.x * v.x + v.y * v.y; }
inline float length(sf::Vector2f v) { return std::sqrt(lengthSq(v)); }

inline sf::Vector2f normalize(sf::Vector2f v)
{
    const float len = length(v);
    return len > 1e-5f ? v / len : sf::Vector2f(0.f, 0.f);
}

inline float distanceSq(sf::Vector2f a, sf::Vector2f b) { return lengthSq(b - a); }
inline float distance(sf::Vector2f a, sf::Vector2f b) { return length(b - a); }

inline float angleOf(sf::Vector2f v) { return std::atan2(v.y, v.x); }
inline sf::Vector2f fromAngle(float radians) { return {std::cos(radians), std::sin(radians)}; }
inline float toRadians(float degrees) { return degrees * kPi / 180.f; }
inline float toDegrees(float radians) { return radians * 180.f / kPi; }

inline sf::Vector2f perpendicular(sf::Vector2f v) { return {-v.y, v.x}; }

inline sf::Vector2f rotate(sf::Vector2f v, float radians)
{
    const float c = std::cos(radians);
    const float s = std::sin(radians);
    return {v.x * c - v.y * s, v.x * s + v.y * c};
}

inline bool circlesOverlap(sf::Vector2f a, float radiusA, sf::Vector2f b, float radiusB)
{
    const float r = radiusA + radiusB;
    return distanceSq(a, b) <= r * r;
}

} // namespace ss::math
