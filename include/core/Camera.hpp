#pragma once

#include <SFML/Graphics/Rect.hpp>
#include <SFML/Graphics/RenderTarget.hpp>
#include <SFML/Graphics/View.hpp>
#include <SFML/System/Vector2.hpp>

namespace ss {

// Smooth 2D gameplay camera following target entity (Player) in world coordinates.
// Clamps camera view within world boundaries to eliminate void border exposure.
class Camera {
public:
    static constexpr float kMinZoom = 0.75f;
    static constexpr float kMaxZoom = 2.25f;
    static constexpr float kDefaultZoom = 1.35f;

    Camera(sf::Vector2f viewportSize = {1280.f, 720.f},
           sf::FloatRect worldBounds = {0.f, 0.f, 4096.f, 4096.f},
           float initialZoom = kDefaultZoom);

    // Updates camera position following the target, clamped to world boundaries.
    // Also smoothly interpolates zoom towards targetZoom.
    // If smoothSpeed > 0, interpolates smoothly; if smoothSpeed <= 0, tracks directly.
    void update(sf::Vector2f targetPos, float dt = 0.f, float smoothSpeed = 0.f);

    // Explicitly teleports camera center to given position (clamped).
    void setCenter(sf::Vector2f center);
    sf::Vector2f center() const { return m_view.getCenter(); }

    const sf::View& view() const { return m_view; }
    sf::View& view() { return m_view; }

    // Zoom controls & telemetry
    void setZoom(float zoom);
    void setTargetZoom(float targetZoom);
    void zoomBy(float deltaZoom);
    float zoom() const { return m_zoom; }
    float targetZoom() const { return m_targetZoom; }
    void resetZoom() { setTargetZoom(kDefaultZoom); }

    // Viewport and window resize handling
    void setViewportSize(sf::Vector2f size);
    sf::Vector2f viewportSize() const { return m_viewportSize; }
    sf::Vector2f viewSize() const { return m_view.getSize(); }

    // World boundary configuration
    void setWorldBounds(const sf::FloatRect& bounds);
    const sf::FloatRect& worldBounds() const { return m_worldBounds; }

    // Computes the current visible axis-aligned rectangle in world coordinates.
    sf::FloatRect visibleBounds() const;

    // Checks whether an entity or bounding box intersects the visible camera frustum.
    bool isVisible(const sf::FloatRect& rect) const;
    bool isVisible(sf::Vector2f pos, float radius) const;

    // Clamps an arbitrary center point so the view rectangle stays strictly within worldBounds.
    sf::Vector2f clampCenter(sf::Vector2f desiredCenter) const;

private:
    void applyZoom();

    sf::View m_view;
    sf::FloatRect m_worldBounds;
    sf::Vector2f m_viewportSize{1280.f, 720.f};
    float m_zoom = kDefaultZoom;
    float m_targetZoom = kDefaultZoom;
};

} // namespace ss
