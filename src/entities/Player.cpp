#include "entities/Player.hpp"

#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"
#include "core/ResourceManager.hpp"

#include <SFML/Graphics/Sprite.hpp>
#include <SFML/Window/Keyboard.hpp>

#include <algorithm>
#include <cmath>

namespace ss {

Player::Player(sf::Vector2f position)
    : Character(position, 20.f, kDefaultHealth, kDefaultArmor, kDefaultMoveSpeed)
{
}

void Player::reset(sf::Vector2f position)
{
    resetCharacter(position, kDefaultHealth, kDefaultArmor, kDefaultMoveSpeed);
    m_aimAngle = 0.f;
    m_animTimer = 0.f;
    m_baseCastCooldown = kDefaultCastCooldown;
    m_castTimer = 0.f;
    m_damageMultiplier = 1.0f;
    m_projectileSpeedMultiplier = 1.0f;
    m_projectileLifetimeMultiplier = 1.0f;
    m_pickupRadius = kDefaultPickupRadius;
    m_bonusPierce = 0;
}

void Player::handleInput(float dt)
{
    if (!isAlive()) {
        return;
    }

    sf::Vector2f dir(0.f, 0.f);
    if (sf::Keyboard::isKeyPressed(sf::Keyboard::W) || sf::Keyboard::isKeyPressed(sf::Keyboard::Up)) dir.y -= 1.f;
    if (sf::Keyboard::isKeyPressed(sf::Keyboard::S) || sf::Keyboard::isKeyPressed(sf::Keyboard::Down)) dir.y += 1.f;
    if (sf::Keyboard::isKeyPressed(sf::Keyboard::A) || sf::Keyboard::isKeyPressed(sf::Keyboard::Left)) dir.x -= 1.f;
    if (sf::Keyboard::isKeyPressed(sf::Keyboard::D) || sf::Keyboard::isKeyPressed(sf::Keyboard::Right)) dir.x += 1.f;

    moveInDirection(dir, 1.f, dt);
}

void Player::setAimTarget(sf::Vector2f mousePos)
{
    if (!isAlive()) {
        return;
    }

    const sf::Vector2f delta = mousePos - position();
    if (math::lengthSq(delta) > 1e-4f) {
        m_aimAngle = math::angleOf(delta);
    }
}

void Player::constrainToBounds(const sf::FloatRect& bounds)
{
    sf::Vector2f pos = position();
    const float r = radius();
    pos.x = std::clamp(pos.x, bounds.left + r, bounds.left + bounds.width - r);
    pos.y = std::clamp(pos.y, bounds.top + r, bounds.top + bounds.height - r);
    setPosition(pos);
}

void Player::update(float dt)
{
    if (!isAlive()) {
        return;
    }

    Character::update(dt);
    if (m_castTimer > 0.f) {
        m_castTimer = std::max(0.f, m_castTimer - dt);
    }
    if (math::lengthSq(velocity()) > 1.f) {
        m_animTimer += dt * 8.f;
    }
}

void Player::draw(sf::RenderTarget& target) const
{
    draw(target, nullptr);
}

void Player::draw(sf::RenderTarget& target, const ResourceManager* resources) const
{
    const sf::Vector2f pos = position();
    const float r = radius();

    // 1. Soft Shadow
    draw::shadow(target, pos, r);

    // Determine primary facing direction from aim angle
    TextureId activeTexId = TextureId::Player;
    bool flipXAxis = false;

    if (resources) {
        const float pi = math::kPi;
        if (m_aimAngle >= pi * 0.25f && m_aimAngle < pi * 0.75f) {
            // Facing Down (South)
            activeTexId = resources->hasTexture(TextureId::PlayerDown) ? TextureId::PlayerDown : TextureId::Player;
        } else if (m_aimAngle <= -pi * 0.25f && m_aimAngle > -pi * 0.75f) {
            // Facing Up (North)
            activeTexId = resources->hasTexture(TextureId::PlayerUp) ? TextureId::PlayerUp : TextureId::Player;
        } else if (std::abs(m_aimAngle) >= pi * 0.75f) {
            // Facing Left (West)
            if (resources->hasTexture(TextureId::PlayerLeft)) {
                activeTexId = TextureId::PlayerLeft;
            } else if (resources->hasTexture(TextureId::PlayerRight)) {
                activeTexId = TextureId::PlayerRight;
                flipXAxis = true;
            } else {
                activeTexId = TextureId::Player;
                flipXAxis = true;
            }
        } else {
            // Facing Right (East)
            activeTexId = resources->hasTexture(TextureId::PlayerRight) ? TextureId::PlayerRight : TextureId::Player;
        }
    }

    const sf::Texture* tex = resources ? resources->getTexture(activeTexId) : nullptr;
    if (!tex && resources) {
        tex = resources->getTexture(TextureId::Player);
        activeTexId = TextureId::Player;
        flipXAxis = (std::cos(m_aimAngle) < 0.f);
    }

    if (tex) {
        // Multi-frame animation: frame 0 (idle), frame 1 (stride)
        const int frame = (math::lengthSq(velocity()) > 1.f) ? (static_cast<int>(m_animTimer) % 2) : 0;
        sf::IntRect frameRect = resources->getFrameRect(activeTexId, frame);
        if (frameRect.width <= 0 || frameRect.height <= 0) {
            frameRect = sf::IntRect(0, 0, 32, 32);
        }

        sf::Sprite sprite(*tex, frameRect);
        sprite.setOrigin(frameRect.width * 0.5f, frameRect.height * 0.5f);
        sprite.setPosition(pos);

        // Decoupled uniform scale: visual size matches collision envelope (2.35 * r) cleanly without distortion
        const float visualDiameter = r * 2.35f;
        const float scale = visualDiameter / static_cast<float>(frameRect.height);
        const float scaleX = flipXAxis ? -scale : scale;
        sprite.setScale(scaleX, scale);

        // Status effect tints & hitflashes apply dynamically
        const sf::Color tint = draw::characterTint(*this, sf::Color::White);
        sprite.setColor(tint);

        target.draw(sprite);

        // Orbiting spellcasting catalyst orb
        const float pulse = 1.f + 0.15f * std::sin(m_animTimer);
        const sf::Vector2f orbPos = pos + math::fromAngle(m_aimAngle) * (r + 11.f);
        draw::circle(target, orbPos, 6.f * pulse, sf::Color(100, 215, 255), sf::Color::White, 1.5f);
        return;
    }

    // Procedural Fallback
    // 2. Wizard Robe (Main body)
    const sf::Color bodyCol = draw::characterTint(*this, sf::Color(65, 120, 235));
    draw::circle(target, pos, r, bodyCol, sf::Color(210, 235, 255), 2.0f);

    // 3. Wizard Cowl / Hood
    draw::circle(target, pos, r * 0.7f, sf::Color(45, 80, 175), sf::Color(160, 200, 255), 1.5f);

    // 4. Arcane Jewel Core
    draw::circle(target, pos, r * 0.35f, sf::Color(255, 215, 0), sf::Color(255, 240, 140), 1.0f);

    // 5. Catalyst Orb / Magic Focus (Orbiting along aim angle)
    const float pulse = 1.f + 0.15f * std::sin(m_animTimer);
    const sf::Vector2f orbPos = pos + math::fromAngle(m_aimAngle) * (r + 11.f);
    draw::circle(target, orbPos, 6.f * pulse, sf::Color(100, 215, 255), sf::Color::White, 1.5f);
}

} // namespace ss
