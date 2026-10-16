#pragma once

#include <SFML/Graphics/Color.hpp>

#include <algorithm>

namespace ss::color {

inline sf::Color withAlpha(sf::Color c, float alpha)
{
    c.a = static_cast<sf::Uint8>(std::clamp(alpha, 0.f, 1.f) * 255.f);
    return c;
}

inline sf::Color lerp(sf::Color a, sf::Color b, float t)
{
    t = std::clamp(t, 0.f, 1.f);
    const auto mix = [t](sf::Uint8 x, sf::Uint8 y) {
        return static_cast<sf::Uint8>(static_cast<float>(x) + (static_cast<float>(y) - static_cast<float>(x)) * t);
    };
    return {mix(a.r, b.r), mix(a.g, b.g), mix(a.b, b.b), mix(a.a, b.a)};
}

inline sf::Color scale(sf::Color c, float factor)
{
    const auto s = [factor](sf::Uint8 v) {
        return static_cast<sf::Uint8>(std::clamp(static_cast<float>(v) * factor, 0.f, 255.f));
    };
    return {s(c.r), s(c.g), s(c.b), c.a};
}

} // namespace ss::color
