#include "systems/PowerUpManager.hpp"
#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"

#include <SFML/Graphics/RectangleShape.hpp>
#include <SFML/Graphics/Text.hpp>
#include <algorithm>
#include <cmath>
#include <iomanip>
#include <sstream>

namespace ss {

std::string ActiveTemporaryAbility::name() const
{
    switch (type) {
    case PowerUpType::ArcaneOverdrive:
        return "ARCANE OVERDRIVE";
    case PowerUpType::PiercingSurge:
        return "PIERCING SURGE";
    case PowerUpType::ElementalBurst:
        return "ELEMENTAL BURST";
    }
    return "POWER-UP";
}

std::string ActiveTemporaryAbility::description() const
{
    switch (type) {
    case PowerUpType::ArcaneOverdrive:
        return "+80% Spell Firing Rate";
    case PowerUpType::PiercingSurge:
        return "+2 Projectile Piercing Hits";
    case PowerUpType::ElementalBurst:
        return "Empowered Twin Elemental Blast";
    }
    return "";
}

sf::Color ActiveTemporaryAbility::primaryColor() const
{
    switch (type) {
    case PowerUpType::ArcaneOverdrive:
        return sf::Color(175, 75, 255); // Arcane Violet
    case PowerUpType::PiercingSurge:
        return sf::Color(35, 220, 255); // Radiant Cyan
    case PowerUpType::ElementalBurst:
        return sf::Color(255, 135, 30); // Molten Solar Amber
    }
    return sf::Color::White;
}

sf::Color ActiveTemporaryAbility::secondaryColor() const
{
    switch (type) {
    case PowerUpType::ArcaneOverdrive:
        return sf::Color(235, 190, 255);
    case PowerUpType::PiercingSurge:
        return sf::Color(200, 250, 255);
    case PowerUpType::ElementalBurst:
        return sf::Color(255, 235, 130);
    }
    return sf::Color::Yellow;
}

PowerUpManager::PowerUpManager(const Arena& arena, Random& rng)
    : m_arena(arena)
    , m_rng(rng)
{
    reset();
}

void PowerUpManager::reset()
{
    m_pickups.clear();
    m_activeAbility = ActiveTemporaryAbility{};
    m_nextSpawnTimer = 8.0f; // Initial first powerup appears within 8s
}

sf::Vector2f PowerUpManager::findValidSpawnPosition(sf::Vector2f playerPos)
{
    const float minSpawnDist = 180.f;
    const float maxSpawnDist = 650.f;

    for (int attempt = 0; attempt < 24; ++attempt) {
        const float angle = m_rng.range(0.f, math::kTwoPi);
        const float dist = m_rng.range(minSpawnDist, maxSpawnDist);
        const sf::Vector2f candidate = playerPos + math::fromAngle(angle) * dist;

        if (m_arena.isTraversable(candidate, 20.f)) {
            return candidate;
        }
    }

    // Fallback within arena bounds
    const sf::FloatRect& b = m_arena.bounds();
    return sf::Vector2f(
        std::clamp(playerPos.x + 220.f, b.left + 50.f, b.left + b.width - 50.f),
        std::clamp(playerPos.y + 100.f, b.top + 50.f, b.top + b.height - 50.f)
    );
}

void PowerUpManager::spawnPickup(sf::Vector2f pos, PowerUpType type)
{
    WorldPowerUp p;
    p.type = type;
    p.position = pos;
    p.radius = 18.0f;
    p.lifetime = m_groundLifetime;
    p.maxLifetime = m_groundLifetime;
    p.animTimer = m_rng.range(0.f, 6.f);
    p.collected = false;

    m_pickups.push_back(p);
}

void PowerUpManager::spawnRandomPickup(sf::Vector2f playerPos)
{
    const float roll = m_rng.range(0.f, 3.f);
    PowerUpType type = PowerUpType::ArcaneOverdrive;
    if (roll < 1.0f) {
        type = PowerUpType::ArcaneOverdrive;
    } else if (roll < 2.0f) {
        type = PowerUpType::PiercingSurge;
    } else {
        type = PowerUpType::ElementalBurst;
    }

    const sf::Vector2f spawnPos = findValidSpawnPosition(playerPos);
    spawnPickup(spawnPos, type);
}

void PowerUpManager::update(float dt, Player& player, EffectSystem& effects)
{
    // 1. Update active ability countdown timer
    m_activeAbility.update(dt);

    // 2. Spawn timer management
    m_nextSpawnTimer -= dt;
    if (m_nextSpawnTimer <= 0.f) {
        if (m_pickups.size() < m_maxActivePickups) {
            spawnRandomPickup(player.position());
        }
        m_nextSpawnTimer = m_rng.range(m_minSpawnInterval, m_maxSpawnInterval);
    }

    // 3. Update existing pickups on ground & handle collection
    for (auto& p : m_pickups) {
        if (!p.isAlive()) {
            continue;
        }

        p.lifetime -= dt;
        p.animTimer += dt * 3.f;

        // Player overlap collision check
        const float totalRadius = p.radius + player.pickupRadius();
        if (math::distanceSq(p.position, player.position()) <= totalRadius * totalRadius) {
            p.collected = true;

            // Strict recollection policy:
            // - Same pickup type refreshes active duration back to 5.0s
            // - Different pickup type replaces currently active ability with new 5.0s
            if (m_activeAbility.active && m_activeAbility.type == p.type) {
                m_activeAbility.refresh();
            } else {
                m_activeAbility.activate(p.type);
            }

            // Collection visual feedback burst & ring
            effects.burst(p.position, m_activeAbility.primaryColor(), 18, 150.f, 0.4f);
            effects.ring(player.position(), 36.f, m_activeAbility.secondaryColor(), 0.3f);
        }
    }

    // 4. Safely remove collected and expired pickups
    m_pickups.erase(
        std::remove_if(m_pickups.begin(), m_pickups.end(), [](const WorldPowerUp& p) {
            return !p.isAlive();
        }),
        m_pickups.end()
    );
}

void PowerUpManager::drawWorld(sf::RenderTarget& target, const Camera& camera) const
{
    for (const auto& p : m_pickups) {
        if (!p.isAlive()) {
            continue;
        }

        if (!camera.isVisible(p.position, p.radius + 15.f)) {
            continue;
        }

        const float bob = std::sin(p.animTimer) * 3.5f;
        const sf::Vector2f center = p.position + sf::Vector2f(0.f, bob);

        // Blinking alpha if close to ground expiration (< 4 seconds remaining)
        float alphaFactor = 1.0f;
        if (p.lifetime < 4.0f) {
            alphaFactor = 0.5f + 0.5f * std::sin(p.lifetime * 12.f);
        }
        const sf::Uint8 alpha = static_cast<sf::Uint8>(std::clamp(alphaFactor * 255.f, 40.f, 255.f));

        // Soft drop shadow on ground
        draw::shadow(target, p.position, p.radius * 0.9f);

        // Determine color scheme from pickup type
        ActiveTemporaryAbility dummy;
        dummy.type = p.type;
        sf::Color primary = dummy.primaryColor();
        sf::Color secondary = dummy.secondaryColor();
        primary.a = alpha;
        secondary.a = alpha;

        // Pulsating outer aura ring
        const float pulse = 1.f + 0.15f * std::sin(p.animTimer * 1.5f);
        sf::Color auraCol = primary;
        auraCol.a = static_cast<sf::Uint8>(alpha * 0.35f);
        draw::circle(target, center, p.radius * pulse, auraCol, primary, 1.5f);

        // Shape specific rendering for clear visual identification
        switch (p.type) {
        case PowerUpType::ArcaneOverdrive: {
            // Arcane Diamond / Bolt Gem
            std::vector<sf::Vector2f> pts = {
                center + sf::Vector2f(0.f, -p.radius * 0.9f),
                center + sf::Vector2f(p.radius * 0.7f, 0.f),
                center + sf::Vector2f(0.f, p.radius * 0.9f),
                center + sf::Vector2f(-p.radius * 0.7f, 0.f)
            };
            draw::polygon(target, {pts[0], pts[1], pts[2], pts[3]}, primary, secondary, 1.8f);
            draw::circle(target, center, 4.f, secondary);
            break;
        }
        case PowerUpType::PiercingSurge: {
            // Penetrating Spear / Lance Gem
            std::vector<sf::Vector2f> pts = {
                center + sf::Vector2f(0.f, -p.radius * 1.05f),
                center + sf::Vector2f(p.radius * 0.55f, -p.radius * 0.2f),
                center + sf::Vector2f(p.radius * 0.45f, p.radius * 0.7f),
                center + sf::Vector2f(0.f, p.radius * 1.05f),
                center + sf::Vector2f(-p.radius * 0.45f, p.radius * 0.7f),
                center + sf::Vector2f(-p.radius * 0.55f, -p.radius * 0.2f)
            };
            draw::polygon(target, {pts[0], pts[1], pts[2], pts[3], pts[4], pts[5]}, primary, secondary, 1.8f);
            draw::circle(target, center, 4.f, secondary);
            break;
        }
        case PowerUpType::ElementalBurst: {
            // Radiant 4-Pointed Solar Star Gem
            const float rOuter = p.radius * 0.95f;
            const float rInner = p.radius * 0.42f;
            std::vector<sf::Vector2f> starPts;
            starPts.reserve(8);
            for (int i = 0; i < 8; ++i) {
                const float a = static_cast<float>(i) * (math::kPi / 4.f);
                const float rad = (i % 2 == 0) ? rOuter : rInner;
                starPts.push_back(center + math::fromAngle(a) * rad);
            }
            for (std::size_t i = 0; i < 8; ++i) {
                const std::size_t next = (i + 1) % 8;
                draw::polygon(target, {center, starPts[i], starPts[next]}, primary, secondary, 1.2f);
            }
            draw::circle(target, center, 5.f, secondary);
            break;
        }
        }
    }
}

void PowerUpManager::drawHUD(sf::RenderTarget& target, const sf::Font* font, sf::Vector2f position) const
{
    if (!m_activeAbility.active || !font) {
        return;
    }

    const float badgeW = 230.f;
    const float badgeH = 34.f;

    // Container panel
    sf::RectangleShape panel({badgeW, badgeH});
    panel.setPosition(position);
    panel.setFillColor(sf::Color(16, 20, 30, 230));
    panel.setOutlineColor(m_activeAbility.primaryColor());
    panel.setOutlineThickness(1.8f);
    target.draw(panel);

    // Glowing ability icon pip
    const sf::Vector2f iconPos(position.x + 14.f, position.y + 12.f);
    draw::circle(target, iconPos, 6.f, m_activeAbility.primaryColor(), m_activeAbility.secondaryColor(), 1.5f);

    // Ability name label
    sf::Text nameText;
    nameText.setFont(*font);
    nameText.setCharacterSize(12);
    nameText.setStyle(sf::Text::Bold);
    nameText.setFillColor(sf::Color::White);
    nameText.setPosition(position.x + 28.f, position.y + 3.f);
    nameText.setString(m_activeAbility.name());
    target.draw(nameText);

    // Remaining duration text (e.g. "4.8s")
    std::ostringstream ss;
    ss << std::fixed << std::setprecision(1) << m_activeAbility.remainingDuration << "s";
    sf::Text timerText;
    timerText.setFont(*font);
    timerText.setCharacterSize(12);
    timerText.setStyle(sf::Text::Bold);
    timerText.setFillColor(m_activeAbility.secondaryColor());
    const sf::FloatRect tb = timerText.getLocalBounds();
    timerText.setOrigin(tb.left + tb.width, tb.top);
    timerText.setPosition(position.x + badgeW - 10.f, position.y + 3.f);
    timerText.setString(ss.str());
    target.draw(timerText);

    // Description subtitle
    sf::Text descText;
    descText.setFont(*font);
    descText.setCharacterSize(10);
    descText.setFillColor(sf::Color(180, 200, 225));
    descText.setPosition(position.x + 28.f, position.y + 17.f);
    descText.setString(m_activeAbility.description());
    target.draw(descText);

    // Smooth countdown progress bar along bottom of badge
    const float barH = 3.5f;
    const float barW = (badgeW - 4.f) * m_activeAbility.durationRatio();
    if (barW > 0.f) {
        sf::RectangleShape progBar({barW, barH});
        progBar.setPosition(position.x + 2.f, position.y + badgeH - barH - 2.f);
        progBar.setFillColor(m_activeAbility.primaryColor());
        target.draw(progBar);
    }
}

} // namespace ss
