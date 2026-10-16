#include "entities/Enemy.hpp"
#include "core/Arena.hpp"
#include "core/MathUtils.hpp"
#include "systems/PathfindingSystem.hpp"

#include <algorithm>

namespace ss {

Enemy::Enemy(sf::Vector2f position, float radius, float maxHealth, float armor,
             float moveSpeed, float contactDamage, float attackCooldown, int expReward)
    : Character(position, radius, maxHealth, armor, moveSpeed)
    , m_contactDamage(std::max(0.f, contactDamage))
    , m_attackCooldown(std::max(0.05f, attackCooldown))
    , m_attackTimer(0.f)
    , m_expReward(std::max(0, expReward))
{
}

void Enemy::constrainToBounds(const sf::FloatRect& bounds)
{
    sf::Vector2f pos = position();
    const float r = radius();
    const float minX = bounds.left + r;
    const float maxX = bounds.left + bounds.width - r;
    const float minY = bounds.top + r;
    const float maxY = bounds.top + bounds.height - r;

    pos.x = std::clamp(pos.x, minX, maxX);
    pos.y = std::clamp(pos.y, minY, maxY);
    setPosition(pos);
}

void Enemy::update(float dt)
{
    updateCharacter(dt);
    if (m_attackTimer > 0.f) {
        m_attackTimer = std::max(0.f, m_attackTimer - dt);
    }
}

bool Enemy::tryAttack(Character& target)
{
    if (!isAlive() || !target.isAlive() || m_attackTimer > 0.f) {
        return false;
    }

    const float totalRadius = radius() + target.radius();
    if (math::distanceSq(position(), target.position()) <= totalRadius * totalRadius) {
        target.takeDamage({m_contactDamage, false});
        m_attackTimer = m_attackCooldown;
        return true;
    }
    return false;
}

void Enemy::steerWithNavigation(float dt, sf::Vector2f targetPos, const Arena* arena, float speedMultiplier)
{
    if (!isAlive()) {
        return;
    }

    // Direct pursuit fallback if no TileMap arena or pathfinder is attached
    if (!arena || !arena->hasTileMap() || !arena->hasPathfinder()) {
        const sf::Vector2f diff = targetPos - position();
        if (math::lengthSq(diff) > 1e-4f) {
            moveInDirection(math::normalize(diff), speedMultiplier, dt);
        }
        return;
    }

    const auto* pathfinder = arena->pathfinder();
    m_navRepathTimer -= dt;

    // Movement progress tracking to detect being stuck against a corner or obstacle
    if (dt > 1e-4f && math::distanceSq(position(), m_navLastPosition) < (2.0f * 2.0f)) {
        m_navStuckTimer += dt;
    } else {
        m_navStuckTimer = 0.f;
    }
    m_navLastPosition = position();

    // Check direct Line of Sight to target
    const bool hasLOS = pathfinder->hasLineOfSight(position(), targetPos, radius());

    // When line of sight is clear and target is within active engagement range, charge directly
    if (hasLOS && math::distanceSq(position(), targetPos) < (650.f * 650.f)) {
        m_navPath.clear();
        m_navWaypointIndex = 0;
        const sf::Vector2f diff = targetPos - position();
        if (math::lengthSq(diff) > 1e-4f) {
            moveInDirection(math::normalize(diff), speedMultiplier, dt);
        }
        return;
    }

    // Line of sight blocked: evaluate whether a new path calculation is needed
    const bool needRepath = m_navPath.empty() ||
                            (m_navWaypointIndex >= m_navPath.size()) ||
                            (m_navRepathTimer <= 0.f) ||
                            (math::distanceSq(targetPos, m_navLastTargetPos) > (64.f * 64.f)) ||
                            (m_navStuckTimer > 0.35f);

    if (needRepath) {
        m_navPath = pathfinder->findPath(position(), targetPos, radius());
        m_navWaypointIndex = 0;
        m_navLastTargetPos = targetPos;
        m_navStuckTimer = 0.f;
        // Stagger repath cadence (0.35s to 0.55s) across entity IDs to distribute CPU load
        m_navRepathTimer = 0.35f + static_cast<float>(id() % 5) * 0.05f;
    }

    if (m_navWaypointIndex < m_navPath.size()) {
        sf::Vector2f wp = m_navPath[m_navWaypointIndex];

        // Advance to next waypoint if close to current
        while (m_navWaypointIndex + 1 < m_navPath.size() &&
               math::distanceSq(position(), wp) < (22.f * 22.f)) {
            ++m_navWaypointIndex;
            wp = m_navPath[m_navWaypointIndex];
        }

        // Path shortcutting / string-pulling: if clear line of sight to waypoint + 1, advance directly
        if (m_navWaypointIndex + 1 < m_navPath.size()) {
            if (pathfinder->hasLineOfSight(position(), m_navPath[m_navWaypointIndex + 1], radius())) {
                ++m_navWaypointIndex;
                wp = m_navPath[m_navWaypointIndex];
            }
        }

        const sf::Vector2f diff = wp - position();
        if (math::lengthSq(diff) > 1e-4f) {
            moveInDirection(math::normalize(diff), speedMultiplier, dt);
        }
    } else {
        // Fallback: steer towards target
        const sf::Vector2f diff = targetPos - position();
        if (math::lengthSq(diff) > 1e-4f) {
            moveInDirection(math::normalize(diff), speedMultiplier, dt);
        }
    }
}

} // namespace ss
