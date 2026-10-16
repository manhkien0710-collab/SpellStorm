#include "entities/MeleeEnemy.hpp"

#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <cmath>

namespace ss {

MeleeEnemy::MeleeEnemy(sf::Vector2f position)
    : Enemy(position, 16.f, 60.f, 0.f, 105.f, 18.f, 0.8f, 10)
{
}

void MeleeEnemy::updateAI(float dt, sf::Vector2f targetPos, const Arena* arena)
{
    if (!isAlive()) {
        return;
    }

    m_animTimer += dt;
    steerWithNavigation(dt, targetPos, arena, 1.0f);
}

void MeleeEnemy::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void MeleeEnemy::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    const sf::Vector2f pos = position();
    const float r = radius();

    // Drop shadow
    draw::shadow(target, pos, r * 0.9f);

    // Direction angle facing target or movement
    float angle = 0.f;
    if (math::lengthSq(velocity()) > 1.f) {
        angle = math::angleOf(velocity());
    } else {
        angle = std::sin(m_animTimer * 4.f) * 0.2f;
    }

    const sf::Texture* tex = resources ? resources->getTexture(TextureId::MeleeEnemy) : nullptr;
    if (tex) {
        sf::Sprite sprite(*tex);
        const sf::Vector2u size = tex->getSize();
        sprite.setOrigin(size.x * 0.5f, size.y * 0.5f);
        sprite.setPosition(pos);

        // Uniform scaling decoupled from collision geometry
        const float visualDiameter = r * 2.2f;
        const float scale = visualDiameter / static_cast<float>(size.x);
        sprite.setScale(scale, scale);

        // Facing direction rotation
        sprite.setRotation(math::toDegrees(angle) + 90.f);

        // Hitflash and status effect tinting
        const sf::Color tint = draw::characterTint(*this, sf::Color::White);
        sprite.setColor(tint);

        target.draw(sprite);

        // Mini health bar when injured
        if (healthRatio() < 1.f && isAlive()) {
            const sf::Vector2f barStart(pos.x - 14.f, pos.y - r - 9.f);
            draw::line(target, barStart, barStart + sf::Vector2f(28.f, 0.f), 3.5f, sf::Color(35, 35, 45));
            draw::line(target, barStart, barStart + sf::Vector2f(28.f * healthRatio(), 0.f), 3.5f, sf::Color(220, 50, 50));
        }
        return;
    }

    // Dynamic coloring with hitflash & status tints
    const sf::Color baseBodyColor(220, 45, 55);
    const sf::Color bodyColor = draw::characterTint(*this, baseBodyColor);
    const sf::Color innerColor = draw::characterTint(*this, sf::Color(120, 20, 30));
    const sf::Color eyeColor = isHitFlashing() ? sf::Color::White : sf::Color(255, 215, 50);

    // Sharp diamond silhouette
    const sf::Vector2f pHead = pos + math::fromAngle(angle) * (r * 1.25f);
    const sf::Vector2f pRight = pos + math::fromAngle(angle + math::kPi * 0.5f) * (r * 0.85f);
    const sf::Vector2f pTail = pos + math::fromAngle(angle + math::kPi) * (r * 0.7f);
    const sf::Vector2f pLeft = pos + math::fromAngle(angle - math::kPi * 0.5f) * (r * 0.85f);

    draw::polygon(target, {pHead, pRight, pTail, pLeft}, bodyColor, sf::Color(255, 90, 100), 1.5f);

    // Inner demonic core & eye
    draw::circle(target, pos, r * 0.45f, innerColor);
    draw::circle(target, pos + math::fromAngle(angle) * (r * 0.35f), 3.2f, eyeColor);

    // Mini health bar when injured
    if (healthRatio() < 1.f && isAlive()) {
        const sf::Vector2f barStart(pos.x - 14.f, pos.y - r - 9.f);
        draw::line(target, barStart, barStart + sf::Vector2f(28.f, 0.f), 3.5f, sf::Color(35, 35, 45));
        draw::line(target, barStart, barStart + sf::Vector2f(28.f * healthRatio(), 0.f), 3.5f, sf::Color(220, 50, 50));
    }
}

} // namespace ss
