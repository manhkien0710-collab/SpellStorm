#pragma once

#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/System/Vector2.hpp>

#include <cstdint>

namespace ss {

using EntityId = std::uint32_t;

// Root of every object that lives in the arena. Each entity receives a
// unique id; systems remember ids (not raw pointers) to refer to entities
// across frames, so a destroyed entity can never be dereferenced by mistake.
class Entity {
public:
    virtual ~Entity() = default;

    EntityId id() const { return m_id; }
    sf::Vector2f position() const { return m_position; }
    void setPosition(sf::Vector2f position) { m_position = position; }
    float radius() const { return m_radius; }

    bool isAlive() const { return m_alive; }
    void destroy() { m_alive = false; }
    void setAlive(bool alive) { m_alive = alive; }
    void revive() { m_alive = true; }

    static void resetIdCounter(EntityId startId = 1);

    virtual void update(float dt) = 0;
    virtual void draw(sf::RenderTarget& target) const = 0;
    virtual void draw(sf::RenderTarget& target, const class ResourceManager* resources) const {
        (void)resources;
        draw(target);
    }

protected:
    Entity(sf::Vector2f position, float radius);

    Entity(const Entity&) = default;
    Entity& operator=(const Entity&) = default;
    Entity(Entity&&) noexcept = default;
    Entity& operator=(Entity&&) noexcept = default;

private:
    EntityId m_id;
    sf::Vector2f m_position;
    float m_radius;
    bool m_alive = true;
};

} // namespace ss
