#include "systems/EffectSystem.hpp"

#include "core/ColorUtils.hpp"
#include "core/DrawUtils.hpp"
#include "core/MathUtils.hpp"

#include <algorithm>
#include <cmath>

namespace ss {

void EffectSystem::update(float dt)
{
    // Update particles
    for (auto& p : m_particles) {
        p.position += p.velocity * dt;
        p.velocity *= std::exp(-2.5f * dt);
        p.lifetime -= dt;
    }
    m_particles.erase(
        std::remove_if(m_particles.begin(), m_particles.end(), [](const Particle& p) { return p.lifetime <= 0.f; }),
        m_particles.end());

    // Update rings
    for (auto& r : m_rings) {
        r.lifetime -= dt;
    }
    m_rings.erase(
        std::remove_if(m_rings.begin(), m_rings.end(), [](const RingEffect& r) { return r.lifetime <= 0.f; }),
        m_rings.end());

    // Update beams
    for (auto& b : m_beams) {
        b.lifetime -= dt;
    }
    m_beams.erase(
        std::remove_if(m_beams.begin(), m_beams.end(), [](const BeamEffect& b) { return b.lifetime <= 0.f; }),
        m_beams.end());
}

void EffectSystem::draw(sf::RenderTarget& target) const
{
    // Draw beams
    for (const auto& b : m_beams) {
        const float alpha = std::clamp(b.lifetime / b.maxLifetime, 0.f, 1.f);
        draw::line(target, b.from, b.to, b.thickness, color::withAlpha(b.color, alpha));
    }

    // Draw rings
    for (const auto& r : m_rings) {
        const float progress = 1.f - std::clamp(r.lifetime / r.maxLifetime, 0.f, 1.f);
        const float radius = r.maxRadius * progress;
        const float alpha = 1.f - progress;
        draw::circle(target, r.center, radius, sf::Color::Transparent, color::withAlpha(r.color, alpha), 2.5f);
    }

    // Draw particles
    for (const auto& p : m_particles) {
        const float alpha = std::clamp(p.lifetime / p.maxLifetime, 0.f, 1.f);
        draw::circle(target, p.position, p.radius * alpha, color::withAlpha(p.color, alpha));
    }
}

void EffectSystem::clear()
{
    m_particles.clear();
    m_rings.clear();
    m_beams.clear();
}

void EffectSystem::burst(sf::Vector2f center, sf::Color color, int count, float speed, float duration, float size)
{
    if (count <= 0) return;
    const float step = (2.f * math::kPi) / static_cast<float>(count);
    for (int i = 0; i < count; ++i) {
        const float angle = step * static_cast<float>(i) + static_cast<float>(i % 3) * 0.1f;
        const sf::Vector2f vel = math::fromAngle(angle) * speed;
        m_particles.push_back({center, vel, color, size, duration, duration});
    }
}

void EffectSystem::ring(sf::Vector2f center, float radius, sf::Color color, float duration)
{
    m_rings.push_back({center, radius, color, duration, duration});
}

void EffectSystem::beam(sf::Vector2f from, sf::Vector2f to, sf::Color color, float duration, float thickness)
{
    m_beams.push_back({from, to, color, thickness, duration, duration});
}

} // namespace ss
