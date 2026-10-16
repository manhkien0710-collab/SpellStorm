#include "entities/BossEnemy.hpp"
#include "entities/Projectile.hpp"
#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <cmath>

namespace ss {

BossEnemy::BossEnemy(sf::Vector2f position)
    : Enemy(position, kBossRadius, kBossMaxHealth, kBossArmor,
            kBossMoveSpeed, kBossContactDamage, kBossAttackCooldown, kBossExpReward)
{
}

void BossEnemy::updateAI(float dt, sf::Vector2f targetPos, const Arena* arena)
{
    if (!isAlive()) {
        return;
    }

    m_animTimer += dt;
    if (m_volleyTimer > 0.f) {
        m_volleyTimer = std::max(0.f, m_volleyTimer - dt);
    }

    steerWithNavigation(dt, targetPos, arena, 1.0f);
}

std::vector<std::unique_ptr<Projectile>> BossEnemy::fireVolley(sf::Vector2f targetPos)
{
    m_volleyTimer = 4.0f;
    std::vector<std::unique_ptr<Projectile>> projectiles;

    const sf::Vector2f delta = targetPos - position();
    const sf::Vector2f baseDir = (math::lengthSq(delta) > 1e-4f) ? math::normalize(delta) : sf::Vector2f(1.f, 0.f);
    const float baseAngle = math::angleOf(baseDir);

    const float spread = math::toRadians(24.f);
    for (int i = -1; i <= 1; ++i) {
        const float angle = baseAngle + static_cast<float>(i) * spread;
        const sf::Vector2f dir = math::fromAngle(angle);
        projectiles.push_back(std::make_unique<Projectile>(
            position() + dir * (radius() + 8.f),
            dir, 210.f, 10.f, 20.f, 3.2f, 0,
            ProjectileFaction::Enemy, sf::Color(255, 60, 40), sf::Color(255, 180, 50)
        ));
    }
    return projectiles;
}

void BossEnemy::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void BossEnemy::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    const sf::Vector2f pos = position();
    const float r = radius();

    // 1. Massive layered ground shockwave aura & shadow
    draw::shadow(target, pos, r * 1.35f);

    const float auraPulse = 1.f + 0.12f * std::sin(m_animTimer * 3.f);
    draw::circle(target, pos, (r + 14.f) * auraPulse, sf::Color(180, 30, 20, 45), sf::Color(255, 120, 40, 90), 2.5f);

    // 2. Texture Sprite Rendering (if available)
    const sf::Texture* tex = nullptr;
    if (resources) {
        if (resources->hasTexture(TextureId::BossEnemy)) {
            tex = resources->getTexture(TextureId::BossEnemy);
        } else if (resources->hasTexture("boss_colossus")) {
            tex = resources->getTexture("boss_colossus");
        } else if (resources->hasTexture(TextureId::TankEnemy)) {
            tex = resources->getTexture(TextureId::TankEnemy);
        }
    }
    if (tex) {
        sf::Sprite sprite(*tex);
        const sf::Vector2u size = tex->getSize();
        sprite.setOrigin(size.x * 0.5f, size.y * 0.5f);
        sprite.setPosition(pos);

        // Uniform scaling: boss is massive (~115 px diameter)
        const float visualDiameter = r * 2.3f;
        const float scale = visualDiameter / static_cast<float>(size.x);
        const bool flipX = (velocity().x < -0.5f);
        sprite.setScale(flipX ? -scale : scale, scale);

        // Natural subtle movement wobble
        const float wobble = (math::lengthSq(velocity()) > 1.f) ? (std::sin(m_animTimer * 6.f) * 3.5f) : 0.f;
        sprite.setRotation(wobble);

        const sf::Color tint = draw::characterTint(*this, sf::Color(255, 230, 220));
        sprite.setColor(tint);
        target.draw(sprite);

        // Imposing Molten Core and Crown Spikes
        const float corePulse = 14.f + std::sin(m_animTimer * 5.f) * 3.f;
        draw::circle(target, pos, corePulse, sf::Color(255, 90, 25, 200), sf::Color(255, 220, 60, 240), 2.0f);

        // Molten horn highlights
        for (int i = -1; i <= 1; i += 2) {
            const sf::Vector2f hornOffset(static_cast<float>(i) * (r * 0.65f), -r * 0.7f);
            draw::circle(target, pos + hornOffset, 6.f, sf::Color(255, 80, 30), sf::Color(255, 220, 50), 1.5f);
        }
        return;
    }

    // 3. Procedural Fallback: Massive Segmented Obsidian Titan
    const sf::Color baseRockColor = draw::characterTint(*this, sf::Color(45, 48, 58));
    const sf::Color plateColor = draw::characterTint(*this, sf::Color(75, 78, 92));
    const sf::Color moltenColor = isHitFlashing() ? sf::Color::White : sf::Color(255, 90, 20);

    // Heavy segmented octagonal chassis
    constexpr int kSegments = 10;
    std::vector<sf::Vector2f> outerPoints;
    outerPoints.reserve(kSegments);
    for (int i = 0; i < kSegments; ++i) {
        const float a = (static_cast<float>(i) * 2.f * math::kPi) / kSegments;
        outerPoints.push_back(pos + sf::Vector2f(std::cos(a) * r, std::sin(a) * r));
    }
    for (std::size_t i = 0; i < outerPoints.size(); ++i) {
        const std::size_t next = (i + 1) % outerPoints.size();
        draw::polygon(target, {pos, outerPoints[i], outerPoints[next]}, (i % 2 == 0) ? baseRockColor : plateColor,
                      moltenColor, 2.0f);
    }

    // Molten fissures radiating outward
    for (int i = 0; i < 6; ++i) {
        const float a = static_cast<float>(i) * (math::kPi / 3.f) + m_animTimer * 0.5f;
        const sf::Vector2f spikeTip = pos + math::fromAngle(a) * (r * 0.85f);
        draw::line(target, pos, spikeTip, 4.0f, moltenColor);
    }

    // Fiery Molten Heart
    const float corePulse = 16.f + std::sin(m_animTimer * 5.f) * 3.f;
    draw::circle(target, pos, corePulse, sf::Color(255, 80, 20), sf::Color(255, 230, 80), 2.5f);
}

} // namespace ss
