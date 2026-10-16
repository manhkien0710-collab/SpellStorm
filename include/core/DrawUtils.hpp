#pragma once

#include <SFML/Graphics/Color.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>

#include <cstddef>
#include <initializer_list>

// Procedural drawing helpers: the game has no sprite assets, every
// character and effect is assembled from these primitives.
namespace ss::draw {

void circle(sf::RenderTarget& target, sf::Vector2f center, float radius, sf::Color fill,
            sf::Color outline = sf::Color::Transparent, float thickness = 0.f, std::size_t points = 24);

void ellipse(sf::RenderTarget& target, sf::Vector2f center, sf::Vector2f radii, sf::Color fill);

void shadow(sf::RenderTarget& target, sf::Vector2f center, float radius);

void line(sf::RenderTarget& target, sf::Vector2f from, sf::Vector2f to, float thickness, sf::Color color);

void polygon(sf::RenderTarget& target, std::initializer_list<sf::Vector2f> points, sf::Color fill,
             sf::Color outline = sf::Color::Transparent, float thickness = 0.f);

} // namespace ss::draw

namespace ss {
class Character;

namespace draw {
sf::Color characterTint(const Character& character, sf::Color baseColor);
}

} // namespace ss
