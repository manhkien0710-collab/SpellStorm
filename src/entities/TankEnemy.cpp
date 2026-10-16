#include "entities/TankEnemy.hpp"

#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <cmath>

namespace ss {

TankEnemy::TankEnemy(sf::Vector2f position)
    : Enemy(position, 26.f, 200.f, 25.f, 65.f, 32.f, 1.2f, 30)
{
}

void TankEnemy::updateAI(float dt, sf::Vector2f targetPos)
{
    if (!isAlive()) {
        return;
    }

    m_animTimer += dt;
    const sf::Vector2f diff = targetPos - position();
    const float distSq = math::lengthSq(diff);

    // Safe handling: avoid division by zero or NaN if overlapping
    if (distSq > 1e-4f) {
        const sf::Vector2f dir = math::normalize(diff);
        moveInDirection(dir, 1.f, dt);
    }
}

void TankEnemy::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void TankEnemy::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    const sf::Vector2f pos = position();
    const float r = radius();

    // Heavy drop shadow
    draw::shadow(target, pos, r * 1.15f);

    // Direction angle facing target or movement
    float angle = 0.f;
    if (math::lengthSq(velocity()) > 1.f) {
        angle = math::angleOf(velocity());
    }

    const sf::Texture* tex = resources ? resources->getTexture(TextureId::TankEnemy) : nullptr;
    if (tex) {
        sf::Sprite sprite(*tex);
        const sf::Vector2u size = tex->getSize();
        sprite.setOrigin(size.x * 0.5f, size.y * 0.5f);
        sprite.setPosition(pos);

        // Uniform scaling decoupled from collision geometry
        const float visualDiameter = r * 2.2f;
        const float scale = visualDiameter / static_cast<float>(size.x);
        sprite.setScale(scale, scale);

        // Rotate slightly with movement
        sprite.setRotation(math::toDegrees(angle) + 90.f);

        const sf::Color tint = draw::characterTint(*this, sf::Color::White);
        sprite.setColor(tint);

        target.draw(sprite);

        // Pulsating magma glow overlay
        const float corePulse = 7.f + std::sin(m_animTimer * 4.f) * 1.5f;
        draw::circle(target, pos, corePulse, sf::Color(255, 180, 50, 160));

        // Health bar with armor indicator
        if (healthRatio() < 1.f && isAlive()) {
            const sf::Vector2f barStart(pos.x - 20.f, pos.y - r - 10.f);
            draw::line(target, barStart, barStart + sf::Vector2f(40.f, 0.f), 4.5f, sf::Color(35, 35, 45));
            draw::line(target, barStart, barStart + sf::Vector2f(40.f * healthRatio(), 0.f), 4.5f, sf::Color(220, 50, 50));
            // Blue armor pip on the left
            draw::circle(target, barStart + sf::Vector2f(-3.f, 0.f), 3.f, sf::Color(80, 160, 240));
        }
        return;
    }

    // Dynamic coloring with hitflash & status tints
    const sf::Color baseRockColor(80, 85, 96);
    const sf::Color rockColor = draw::characterTint(*this, baseRockColor);
    const sf::Color plateColor = draw::characterTint(*this, sf::Color(105, 110, 122));
    const sf::Color coreColor = isHitFlashing() ? sf::Color::White : sf::Color(240, 140, 30);
    const sf::Color innerGlowColor = isHitFlashing() ? sf::Color::White : sf::Color(255, 215, 70);

    // Octagonal armored stone carapace (8 vertices)
    constexpr int kSegments = 8;
    std::vector<sf::Vector2f> outerPoints;
    outerPoints.reserve(kSegments);
    for (int i = 0; i < kSegments; ++i) {
        const float a = (static_cast<float>(i) * 2.f * math::kPi) / kSegments + math::kPi / 8.f;
        outerPoints.push_back(pos + math::fromAngle(a) * r);
    }
    draw::polygon(target, {outerPoints[0], outerPoints[1], outerPoints[2], outerPoints[3],
                           outerPoints[4], outerPoints[5], outerPoints[6], outerPoints[7]},
                  rockColor, sf::Color(45, 48, 55), 2.5f);

    // Interlocking heavy armor plates
    for (int i = 0; i < 4; ++i) {
        const float plateAngle = angle + (static_cast<float>(i) * math::kPi * 0.5f) + math::kPi * 0.25f;
        const sf::Vector2f platePos = pos + math::fromAngle(plateAngle) * (r * 0.55f);
        draw::circle(target, platePos, r * 0.32f, plateColor, sf::Color(55, 58, 65), 1.2f);
    }

    // Molten magma core at center
    const float corePulse = r * 0.38f + std::sin(m_animTimer * 3.f) * 1.5f;
    draw::circle(target, pos, corePulse, coreColor);
    draw::circle(target, pos, corePulse * 0.55f, innerGlowColor);

    // Glowing magma visor slits facing front
    const sf::Vector2f forward = math::fromAngle(angle);
    const sf::Vector2f perp = math::perpendicular(forward);
    const sf::Vector2f eyeLeft = pos + forward * (r * 0.6f) - perp * 5.f;
    const sf::Vector2f eyeRight = pos + forward * (r * 0.6f) + perp * 5.f;
    draw::circle(target, eyeLeft, 2.5f, innerGlowColor);
    draw::circle(target, eyeRight, 2.5f, innerGlowColor);

    // Health bar with armor indicator
    if (healthRatio() < 1.f && isAlive()) {
        const sf::Vector2f barStart(pos.x - 20.f, pos.y - r - 10.f);
        draw::line(target, barStart, barStart + sf::Vector2f(40.f, 0.f), 4.5f, sf::Color(35, 35, 45));
        draw::line(target, barStart, barStart + sf::Vector2f(40.f * healthRatio(), 0.f), 4.5f, sf::Color(220, 50, 50));
        // Blue armor pip on the left
        draw::circle(target, barStart + sf::Vector2f(-3.f, 0.f), 3.f, sf::Color(80, 160, 240));
    }
}

} // namespace ss
