#include "entities/Entity.hpp"

namespace ss {

namespace {
EntityId g_nextEntityId = 1;
}

Entity::Entity(sf::Vector2f position, float radius)
    : m_id(g_nextEntityId++)
    , m_position(position)
    , m_radius(radius)
{
}

void Entity::resetIdCounter(EntityId startId)
{
    g_nextEntityId = startId;
}

} // namespace ss
