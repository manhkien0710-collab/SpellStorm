#include "entities/RangedEnemy.hpp"
#include "entities/Projectile.hpp"

#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <cmath>

namespace ss {

RangedEnemy::RangedEnemy(sf::Vector2f position)
    : Enemy(position, 15.f, 45.f, 0.f, 120.f, 8.f, 1.4f, 15)
{
}

void RangedEnemy::update(float dt)
{
    Enemy::update(dt);
    if (m_rangedAttackTimer > 0.f) {
        m_rangedAttackTimer = std::max(0.f, m_rangedAttackTimer - dt);
    }
}

std::unique_ptr<Projectile> RangedEnemy::shoot(sf::Vector2f targetPos)
{
    m_rangedAttackTimer = m_rangedAttackCooldown;

    const sf::Vector2f delta = targetPos - position();
    const sf::Vector2f dir = (math::lengthSq(delta) > 1e-4f) ? math::normalize(delta) : sf::Vector2f(1.f, 0.f);

    return std::make_unique<Projectile>(
        position(), dir, 220.f, 7.f, 15.f, 2.4f, 0,
        ProjectileFaction::Enemy, sf::Color(215, 80, 245), sf::Color(255, 140, 255)
    );
}

void RangedEnemy::updateAI(float dt, sf::Vector2f targetPos)
{
    if (!isAlive()) {
        return;
    }

    m_animTimer += dt;
    m_targetPos = targetPos;

    sf::Vector2f diff = targetPos - position();
    float dist = math::length(diff);

    // Safe handling: avoid division by zero or NaN if overlapping
    if (dist < 1e-4f) {
        diff = {1.f, 0.f};
        dist = 1.f;
    }

    const sf::Vector2f dir = diff / dist;
    m_inAttackRange = (dist <= m_preferredDistance + 70.f);

    if (dist > m_preferredDistance + m_distanceTolerance) {
        // Too far away: approach player
        moveInDirection(dir, 1.f, dt);
    } else if (dist < m_preferredDistance - m_distanceTolerance) {
        // Too close: retreat / kite away from player
        moveInDirection(-dir, 1.f, dt);
    } else {
        // In preferred range sweet spot: strafe laterally around player
        const sf::Vector2f strafeDir = math::perpendicular(dir);
        const float strafeFactor = std::sin(m_animTimer * 1.5f) > 0.f ? 0.45f : -0.45f;
        moveInDirection(strafeDir, strafeFactor, dt);
    }
}

void RangedEnemy::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void RangedEnemy::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    const sf::Vector2f pos = position();
    const float r = radius();

    // Drop shadow
    draw::shadow(target, pos, r * 0.9f);

    const sf::Texture* tex = resources ? resources->getTexture(TextureId::RangedEnemy) : nullptr;
    if (tex) {
        sf::Sprite sprite(*tex);
        const sf::Vector2u size = tex->getSize();
        sprite.setOrigin(size.x * 0.5f, size.y * 0.5f);

        // Subtle floating bob
        const float bob = std::sin(m_animTimer * 3.f) * 2.f;
        sprite.setPosition(pos.x, pos.y + bob);

        const float visualDiameter = r * 2.3f;
        const float scale = visualDiameter / static_cast<float>(size.x);
        sprite.setScale(scale, scale);

        const sf::Color tint = draw::characterTint(*this, sf::Color::White);
        sprite.setColor(tint);

        target.draw(sprite);

        // Orbiting catalyst gem
        const float orbAngle = m_animTimer * 3.5f;
        const sf::Vector2f orbPos = pos + math::fromAngle(orbAngle) * (r + 7.f);
        const sf::Color orbColor = isHitFlashing() ? sf::Color::White : sf::Color(220, 130, 255);
        draw::circle(target, orbPos, 3.8f, orbColor, sf::Color::White, 1.f);

        // Attack indication when in range: aim telegraph and charging pulse
        if (m_inAttackRange && isAlive()) {
            const sf::Vector2f aimDiff = m_targetPos - orbPos;
            const float aimDist = math::length(aimDiff);
            if (aimDist > 1e-4f) {
                const sf::Vector2f aimDir = aimDiff / aimDist;
                const float lineLen = std::min(aimDist, 180.f);

                // Subtle telegraph line
                draw::line(target, orbPos, orbPos + aimDir * lineLen, 1.2f, sf::Color(210, 100, 255, 90));

                // Charging pulse ring around catalyst orb
                const float pulseRadius = 5.f + std::sin(m_animTimer * 8.f) * 2.5f;
                draw::circle(target, orbPos, pulseRadius, sf::Color::Transparent, sf::Color(220, 150, 255, 140), 1.2f);
            }
        }

        // Mini health bar when injured
        if (healthRatio() < 1.f && isAlive()) {
            const sf::Vector2f barStart(pos.x - 13.f, pos.y - r - 9.f);
            draw::line(target, barStart, barStart + sf::Vector2f(26.f, 0.f), 3.5f, sf::Color(35, 35, 45));
            draw::line(target, barStart, barStart + sf::Vector2f(26.f * healthRatio(), 0.f), 3.5f, sf::Color(220, 50, 50));
        }
        return;
    }

    // Dynamic coloring with hitflash & status tints
    const sf::Color baseMantleColor(150, 60, 225);
    const sf::Color mantleColor = draw::characterTint(*this, baseMantleColor);
    const sf::Color cowlColor = draw::characterTint(*this, sf::Color(85, 25, 140));
    const sf::Color orbColor = isHitFlashing() ? sf::Color::White : sf::Color(220, 130, 255);

    // Faceted occultist body (6-sided regular polygon)
    constexpr int kSegments = 6;
    std::vector<sf::Vector2f> polyPoints;
    polyPoints.reserve(kSegments);
    for (int i = 0; i < kSegments; ++i) {
        const float a = (static_cast<float>(i) * 2.f * math::kPi) / kSegments + math::kPi / 6.f;
        polyPoints.push_back(pos + math::fromAngle(a) * r);
    }
    draw::polygon(target, {polyPoints[0], polyPoints[1], polyPoints[2], polyPoints[3], polyPoints[4], polyPoints[5]},
                  mantleColor, sf::Color(200, 110, 255), 1.5f);

    // Inner cowl & arcane mystic eye
    draw::circle(target, pos, r * 0.5f, cowlColor);
    draw::circle(target, pos, 3.2f, isHitFlashing() ? sf::Color::White : sf::Color(100, 255, 240));

    // Orbiting catalyst gem
    const float orbAngle = m_animTimer * 3.5f;
    const sf::Vector2f orbPos = pos + math::fromAngle(orbAngle) * (r + 7.f);
    draw::circle(target, orbPos, 3.8f, orbColor, sf::Color::White, 1.f);

    // Attack indication when in range: aim telegraph and charging pulse
    if (m_inAttackRange && isAlive()) {
        const sf::Vector2f aimDiff = m_targetPos - orbPos;
        const float aimDist = math::length(aimDiff);
        if (aimDist > 1e-4f) {
            const sf::Vector2f aimDir = aimDiff / aimDist;
            const float lineLen = std::min(aimDist, 180.f);

            // Subtle telegraph line
            draw::line(target, orbPos, orbPos + aimDir * lineLen, 1.2f, sf::Color(210, 100, 255, 90));

            // Charging pulse ring around catalyst orb
            const float pulseRadius = 5.f + std::sin(m_animTimer * 8.f) * 2.5f;
            draw::circle(target, orbPos, pulseRadius, sf::Color::Transparent, sf::Color(220, 150, 255, 140), 1.2f);
        }
    }

    // Mini health bar when injured
    if (healthRatio() < 1.f && isAlive()) {
        const sf::Vector2f barStart(pos.x - 13.f, pos.y - r - 9.f);
        draw::line(target, barStart, barStart + sf::Vector2f(26.f, 0.f), 3.5f, sf::Color(35, 35, 45));
        draw::line(target, barStart, barStart + sf::Vector2f(26.f * healthRatio(), 0.f), 3.5f, sf::Color(220, 50, 50));
    }
}

} // namespace ss
