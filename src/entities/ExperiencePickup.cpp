#include "entities/ExperiencePickup.hpp"

#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <cmath>

namespace ss {

ExperiencePickup::ExperiencePickup(sf::Vector2f position, int expValue)
    : Entity(position, 8.f)
    , m_expValue(std::max(1, expValue))
    , m_baseY(position.y)
{
    if (m_expValue < 15) {
        // Cyan gem for regular melee enemies
        m_gemColor = sf::Color(60, 200, 255);
        m_coreColor = sf::Color(210, 250, 255);
    } else if (m_expValue < 30) {
        // Emerald gem for ranged / intermediate foes
        m_gemColor = sf::Color(50, 240, 150);
        m_coreColor = sf::Color(200, 255, 230);
    } else {
        // Gold / Amber gem for heavy tanks / elites
        m_gemColor = sf::Color(255, 210, 50);
        m_coreColor = sf::Color(255, 255, 220);
    }
}

void ExperiencePickup::collect()
{
    if (!m_collected) {
        m_collected = true;
        destroy();
    }
}

void ExperiencePickup::magnetTowards(sf::Vector2f targetPos, float speed, float dt)
{
    if (!isAlive() || m_collected) {
        return;
    }

    const sf::Vector2f diff = targetPos - position();
    const float distSq = math::lengthSq(diff);
    if (distSq > 1e-4f) {
        const sf::Vector2f dir = math::normalize(diff);
        const sf::Vector2f step = dir * (speed * dt);
        setPosition(position() + step);
        m_baseY = position().y;
    }
}

void ExperiencePickup::update(float dt)
{
    if (!isAlive()) {
        return;
    }

    m_animTimer += dt;
}

void ExperiencePickup::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void ExperiencePickup::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    if (!isAlive()) {
        return;
    }

    const sf::Vector2f currentPos = position();
    // Subtle vertical floating bob
    const float bobOffset = std::sin(m_animTimer * 5.f) * 3.f;
    const sf::Vector2f renderPos(currentPos.x, currentPos.y + bobOffset);
    const float r = radius();

    // 1. Soft ground shadow
    draw::shadow(target, sf::Vector2f(currentPos.x, currentPos.y + r + 3.f), r * 0.9f);

    const sf::Texture* tex = nullptr;
    if (resources) {
        if (m_expValue < 15) {
            tex = resources->getTexture(TextureId::GemCyan);
        } else if (m_expValue < 30) {
            tex = resources->getTexture(TextureId::GemEmerald);
        } else {
            tex = resources->getTexture(TextureId::GemGold);
        }
    }

    if (tex) {
        // Pulsing glow ring
        const float pulse = (std::sin(m_animTimer * 6.f) + 1.f) * 0.5f;
        const sf::Color glowColor(m_gemColor.r, m_gemColor.g, m_gemColor.b, static_cast<sf::Uint8>(60 + pulse * 60));
        draw::circle(target, renderPos, r * (1.3f + pulse * 0.25f), sf::Color::Transparent, glowColor, 1.5f);

        sf::Sprite sprite(*tex);
        const sf::Vector2u size = tex->getSize();
        sprite.setOrigin(size.x * 0.5f, size.y * 0.5f);
        sprite.setPosition(renderPos);

        const float visualDiameter = r * 2.2f;
        const float scale = visualDiameter / static_cast<float>(size.x);
        sprite.setScale(scale, scale);

        target.draw(sprite);
        return;
    }

    // Procedural Fallback
    // 2. Faint pulsing outer glow ring
    const float pulse = (std::sin(m_animTimer * 6.f) + 1.f) * 0.5f;
    const sf::Color glowColor(m_gemColor.r, m_gemColor.g, m_gemColor.b, static_cast<sf::Uint8>(60 + pulse * 60));
    draw::circle(target, renderPos, r * (1.3f + pulse * 0.25f), sf::Color::Transparent, glowColor, 1.5f);

    // 3. Faceted diamond shape
    const float rot = m_animTimer * 1.8f;
    const float w = r * (0.85f + std::cos(rot) * 0.15f);
    const float h = r * 1.35f;

    const sf::Vector2f top(renderPos.x, renderPos.y - h);
    const sf::Vector2f right(renderPos.x + w, renderPos.y);
    const sf::Vector2f bottom(renderPos.x, renderPos.y + h);
    const sf::Vector2f left(renderPos.x - w, renderPos.y);

    draw::polygon(target, {top, right, bottom, left}, m_gemColor, sf::Color::White, 1.2f);

    // 4. Inner glint / facet core
    const float innerW = w * 0.45f;
    const float innerH = h * 0.45f;
    const sf::Vector2f iTop(renderPos.x, renderPos.y - innerH);
    const sf::Vector2f iRight(renderPos.x + innerW, renderPos.y);
    const sf::Vector2f iBottom(renderPos.x, renderPos.y + innerH);
    const sf::Vector2f iLeft(renderPos.x - innerW, renderPos.y);

    draw::polygon(target, {iTop, iRight, iBottom, iLeft}, m_coreColor);
}

} // namespace ss
