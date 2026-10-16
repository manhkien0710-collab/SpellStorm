#include "core/Camera.hpp"
#include "core/MathUtils.hpp"

#include <algorithm>
#include <cmath>

namespace ss {

Camera::Camera(sf::Vector2f viewportSize, sf::FloatRect worldBounds, float initialZoom)
    : m_worldBounds(worldBounds)
    , m_viewportSize(viewportSize)
    , m_zoom(std::clamp(initialZoom, kMinZoom, kMaxZoom))
    , m_targetZoom(m_zoom)
{
    applyZoom();
    const sf::Vector2f initialCenter(
        worldBounds.left + worldBounds.width * 0.5f,
        worldBounds.top + worldBounds.height * 0.5f
    );
    setCenter(initialCenter);
}

void Camera::applyZoom()
{
    m_view.setSize(m_viewportSize.x / m_zoom, m_viewportSize.y / m_zoom);
}

void Camera::setZoom(float zoom)
{
    m_zoom = std::clamp(zoom, kMinZoom, kMaxZoom);
    m_targetZoom = m_zoom;
    applyZoom();
    m_view.setCenter(clampCenter(m_view.getCenter()));
}

void Camera::setTargetZoom(float targetZoom)
{
    m_targetZoom = std::clamp(targetZoom, kMinZoom, kMaxZoom);
}

void Camera::zoomBy(float deltaZoom)
{
    setTargetZoom(m_targetZoom + deltaZoom);
}

sf::Vector2f Camera::clampCenter(sf::Vector2f desiredCenter) const
{
    const sf::Vector2f viewSize = m_view.getSize();
    const float halfW = viewSize.x * 0.5f;
    const float halfH = viewSize.y * 0.5f;

    sf::Vector2f clamped = desiredCenter;

    // Handle maps narrower than viewport safely
    if (m_worldBounds.width <= viewSize.x) {
        clamped.x = m_worldBounds.left + m_worldBounds.width * 0.5f;
    } else {
        const float minX = m_worldBounds.left + halfW;
        const float maxX = m_worldBounds.left + m_worldBounds.width - halfW;
        clamped.x = std::clamp(clamped.x, minX, maxX);
    }

    // Handle maps shorter than viewport safely
    if (m_worldBounds.height <= viewSize.y) {
        clamped.y = m_worldBounds.top + m_worldBounds.height * 0.5f;
    } else {
        const float minY = m_worldBounds.top + halfH;
        const float maxY = m_worldBounds.top + m_worldBounds.height - halfH;
        clamped.y = std::clamp(clamped.y, minY, maxY);
    }

    return clamped;
}

void Camera::update(sf::Vector2f targetPos, float dt, float smoothSpeed)
{
    // Smooth zoom interpolation
    if (dt > 0.f && std::abs(m_zoom - m_targetZoom) > 1e-4f) {
        const float zoomT = 1.0f - std::exp(-10.f * dt);
        m_zoom += (m_targetZoom - m_zoom) * zoomT;
        if (std::abs(m_zoom - m_targetZoom) < 1e-4f) {
            m_zoom = m_targetZoom;
        }
        applyZoom();
    } else if (dt <= 0.f) {
        m_zoom = m_targetZoom;
        applyZoom();
    }

    const sf::Vector2f targetCenter = clampCenter(targetPos);

    if (smoothSpeed <= 0.f || dt <= 0.f) {
        m_view.setCenter(targetCenter);
    } else {
        const sf::Vector2f currentCenter = m_view.getCenter();
        const float t = 1.0f - std::exp(-smoothSpeed * dt);
        const sf::Vector2f smoothed = currentCenter + (targetCenter - currentCenter) * t;
        m_view.setCenter(clampCenter(smoothed));
    }
}

void Camera::setCenter(sf::Vector2f center)
{
    m_view.setCenter(clampCenter(center));
}

void Camera::setViewportSize(sf::Vector2f size)
{
    m_viewportSize = size;
    applyZoom();
    m_view.setCenter(clampCenter(m_view.getCenter()));
}

void Camera::setWorldBounds(const sf::FloatRect& bounds)
{
    m_worldBounds = bounds;
    m_view.setCenter(clampCenter(m_view.getCenter()));
}

sf::FloatRect Camera::visibleBounds() const
{
    const sf::Vector2f center = m_view.getCenter();
    const sf::Vector2f size = m_view.getSize();
    return sf::FloatRect(
        center.x - size.x * 0.5f,
        center.y - size.y * 0.5f,
        size.x,
        size.y
    );
}

bool Camera::isVisible(const sf::FloatRect& rect) const
{
    const sf::FloatRect visible = visibleBounds();
    return (rect.left + rect.width >= visible.left &&
            rect.left <= visible.left + visible.width &&
            rect.top + rect.height >= visible.top &&
            rect.top <= visible.top + visible.height);
}

bool Camera::isVisible(sf::Vector2f pos, float radius) const
{
    const sf::FloatRect visible = visibleBounds();
    return (pos.x + radius >= visible.left &&
            pos.x - radius <= visible.left + visible.width &&
            pos.y + radius >= visible.top &&
            pos.y - radius <= visible.top + visible.height);
}

} // namespace ss
