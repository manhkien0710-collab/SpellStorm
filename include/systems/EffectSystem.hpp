#pragma once

#include <SFML/Graphics/Color.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>

#include <vector>

namespace ss {

struct Particle {
    sf::Vector2f position;
    sf::Vector2f velocity;
    sf::Color color;
    float radius = 3.f;
    float lifetime = 0.5f;
    float maxLifetime = 0.5f;
};

struct RingEffect {
    sf::Vector2f center;
    float maxRadius = 30.f;
    sf::Color color;
    float lifetime = 0.35f;
    float maxLifetime = 0.35f;
};

struct BeamEffect {
    sf::Vector2f from;
    sf::Vector2f to;
    sf::Color color;
    float thickness = 3.f;
    float lifetime = 0.18f;
    float maxLifetime = 0.18f;
};

// Manages transient visual effects such as spell impacts, lightning arcs,
// particle bursts, and area-of-effect rings.
class EffectSystem {
public:
    void update(float dt);
    void draw(sf::RenderTarget& target) const;
    void clear();

    void burst(sf::Vector2f center, sf::Color color, int count = 12, float speed = 200.f, float duration = 0.45f, float size = 3.f);
    void ring(sf::Vector2f center, float radius, sf::Color color, float duration = 0.35f);
    void beam(sf::Vector2f from, sf::Vector2f to, sf::Color color, float duration = 0.18f, float thickness = 3.f);

private:
    std::vector<Particle> m_particles;
    std::vector<RingEffect> m_rings;
    std::vector<BeamEffect> m_beams;
};

} // namespace ss
