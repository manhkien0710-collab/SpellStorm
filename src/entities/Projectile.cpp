#include "entities/Projectile.hpp"

#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <algorithm>
#include <cmath>

namespace ss {

Projectile::Projectile(sf::Vector2f position, sf::Vector2f direction, float speed,
                       float radius, float damage, float lifetime, int pierce,
                       ProjectileFaction faction, sf::Color primaryColor, sf::Color secondaryColor,
                       std::shared_ptr<const SpellDefinition> spell)
    : Entity(position, std::max(2.f, radius))
    , m_speed(std::max(10.f, speed))
    , m_damage(std::max(0.f, damage))
    , m_initialDamage(std::max(0.f, damage))
    , m_lifetime(std::max(0.05f, lifetime))
    , m_pierceRemaining(std::clamp(pierce, 0, kMaxAllowedPierce))
    , m_faction(faction)
    , m_primaryColor(primaryColor)
    , m_secondaryColor(secondaryColor)
    , m_spell(std::move(spell))
{
    // Safe normalization against zero-length direction
    if (math::lengthSq(direction) > 1e-4f) {
        m_direction = math::normalize(direction);
    } else {
        m_direction = {1.f, 0.f};
    }
    m_velocity = m_direction * m_speed;
}

bool Projectile::canHit(EntityId entityId) const
{
    return isAlive() && std::find(m_hitEntities.begin(), m_hitEntities.end(), entityId) == m_hitEntities.end();
}

void Projectile::recordHit(EntityId entityId)
{
    m_hitEntities.push_back(entityId);
}

void Projectile::decrementPierce()
{
    if (m_pierceRemaining > 0) {
        --m_pierceRemaining;
        m_damage = std::max(m_initialDamage * kMinDamageMultiplier, m_damage * kPierceDamageFalloff);
    } else {
        destroy();
    }
}

void Projectile::update(float dt)
{
    if (!isAlive()) {
        return;
    }

    m_animTimer += dt;
    m_lifetime -= dt;

    if (m_lifetime <= 0.f) {
        destroy();
        return;
    }

    setPosition(position() + m_velocity * dt);
}

void Projectile::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void Projectile::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    const sf::Vector2f pos = position();
    const float r = radius();

    const sf::Texture* tex = nullptr;
    if (resources) {
        if (m_faction == ProjectileFaction::Enemy) {
            tex = resources->getTexture(TextureId::ProjectileEnemy);
        } else if (m_spell) {
            const auto& el = m_spell->elements();
            if (el.contains(Element::Fire)) tex = resources->getTexture(TextureId::ProjectileFire);
            else if (el.contains(Element::Ice)) tex = resources->getTexture(TextureId::ProjectileIce);
            else if (el.contains(Element::Lightning)) tex = resources->getTexture(TextureId::ProjectileLightning);
            else if (el.contains(Element::Wind)) tex = resources->getTexture(TextureId::ProjectileWind);
            else if (el.contains(Element::Earth)) tex = resources->getTexture(TextureId::ProjectileEarth);
        }
        if (!tex) {
            tex = resources->getTexture(TextureId::ProjectileFire);
        }
    }

    if (tex) {
        // 1. Soft atmospheric energy glow
        const sf::Color glowColor(m_primaryColor.r, m_primaryColor.g, m_primaryColor.b, 90);
        draw::circle(target, pos, r * 1.6f, glowColor);

        // 2. Trailing comet tail
        const sf::Vector2f tailEnd = pos - m_direction * (r * 2.2f);
        draw::line(target, pos, tailEnd, r * 0.9f, glowColor);

        // 3. Sprite with rotation along flight direction
        sf::Sprite sprite(*tex);
        const sf::Vector2u size = tex->getSize();
        sprite.setOrigin(size.x * 0.5f, size.y * 0.5f);
        sprite.setPosition(pos);

        const float visualDiameter = r * 2.2f;
        const float scale = visualDiameter / static_cast<float>(size.x);
        sprite.setScale(scale, scale);

        const float angleDeg = math::toDegrees(math::angleOf(m_direction));
        sprite.setRotation(angleDeg);

        target.draw(sprite);
        return;
    }

    // Procedural Fallback
    // 1. Soft atmospheric energy glow
    const sf::Color glowColor(m_primaryColor.r, m_primaryColor.g, m_primaryColor.b, 85);
    draw::circle(target, pos, r * 1.65f, glowColor);

    // 2. Trailing comet tail
    const sf::Vector2f tailEnd = pos - m_direction * (r * 2.5f);
    draw::line(target, pos, tailEnd, r * 1.0f, glowColor);

    // 3. Dense elemental core
    draw::circle(target, pos, r, m_primaryColor, m_secondaryColor, 1.4f);

    // 4. White-hot energy nucleus
    const float nucleusPulse = 0.45f + 0.1f * std::sin(m_animTimer * 12.f);
    draw::circle(target, pos, r * nucleusPulse, sf::Color(255, 255, 255, 240));
}

} // namespace ss
