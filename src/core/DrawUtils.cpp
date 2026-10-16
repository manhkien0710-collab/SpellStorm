#include "core/DrawUtils.hpp"

#include "core/ColorUtils.hpp"
#include "core/MathUtils.hpp"
#include "entities/Character.hpp"

#include <SFML/Graphics/CircleShape.hpp>
#include <SFML/Graphics/ConvexShape.hpp>
#include <SFML/Graphics/RectangleShape.hpp>

namespace ss::draw {

void circle(sf::RenderTarget& target, sf::Vector2f center, float radius, sf::Color fill, sf::Color outline,
            float thickness, std::size_t points)
{
    sf::CircleShape shape(radius, points);
    shape.setOrigin(radius, radius);
    shape.setPosition(center);
    shape.setFillColor(fill);
    if (thickness > 0.f) {
        shape.setOutlineColor(outline);
        shape.setOutlineThickness(thickness);
    }
    target.draw(shape);
}

void ellipse(sf::RenderTarget& target, sf::Vector2f center, sf::Vector2f radii, sf::Color fill)
{
    sf::CircleShape shape(radii.x, 20);
    shape.setOrigin(radii.x, radii.x);
    shape.setScale(1.f, radii.y / radii.x);
    shape.setPosition(center);
    shape.setFillColor(fill);
    target.draw(shape);
}

void shadow(sf::RenderTarget& target, sf::Vector2f center, float radius)
{
    ellipse(target, center + sf::Vector2f(0.f, radius * 0.85f), {radius, radius * 0.4f}, sf::Color(0, 0, 0, 90));
}

void line(sf::RenderTarget& target, sf::Vector2f from, sf::Vector2f to, float thickness, sf::Color color)
{
    const sf::Vector2f delta = to - from;
    sf::RectangleShape rect({math::length(delta), thickness});
    rect.setOrigin(0.f, thickness * 0.5f);
    rect.setPosition(from);
    rect.setRotation(math::toDegrees(math::angleOf(delta)));
    rect.setFillColor(color);
    target.draw(rect);
}

void polygon(sf::RenderTarget& target, std::initializer_list<sf::Vector2f> points, sf::Color fill, sf::Color outline,
             float thickness)
{
    sf::ConvexShape shape(points.size());
    std::size_t i = 0;
    for (const sf::Vector2f& p : points) {
        shape.setPoint(i++, p);
    }
    shape.setFillColor(fill);
    if (thickness > 0.f) {
        shape.setOutlineColor(outline);
        shape.setOutlineThickness(thickness);
    }
    target.draw(shape);
}

sf::Color characterTint(const Character& character, sf::Color baseColor)
{
    sf::Color result = baseColor;
    if (character.hasStatus(StatusKind::Burn)) {
        result = color::lerp(result, sf::Color(255, 140, 60), 0.45f);
    }
    if (character.hasStatus(StatusKind::Slow)) {
        result = color::lerp(result, sf::Color(140, 210, 255), 0.45f);
    }
    if (character.isHitFlashing()) {
        const float flashFraction = std::clamp(character.hitFlashTimer() / 0.12f, 0.f, 1.f);
        result = color::lerp(result, sf::Color::White, flashFraction * 0.8f);
    }
    return result;
}

} // namespace ss::draw
