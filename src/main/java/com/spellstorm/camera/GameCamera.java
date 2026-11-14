package com.spellstorm.camera;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.FloatingOriginAware;

/**
 * Smooth 2D gameplay camera following target entities (Player) in world coordinates.
 * Clamps visible camera frustum within world boundaries to eliminate void border exposure.
 *
 * Implements smooth exponential position tracking, smooth exponential zoom interpolation,
 * and bidirectional coordinate unprojection between screen pixels and world coordinates.
 *
 * Dual-Mode Design:
 * - Pure simulation mode: Zero graphics context dependencies for 100% headless JUnit 5 tests.
 * - Hardware render mode: Synchronizes with LibGDX OrthographicCamera with yDown=true.
 */
public class GameCamera implements FloatingOriginAware {

    // Zoom limits and defaults
    public static final float MIN_ZOOM = 0.75f;
    public static final float MAX_ZOOM = 2.25f;
    public static final float DEFAULT_ZOOM = 1.35f;

    // Dynamics constants
    public static final float DEFAULT_SMOOTH_SPEED = 14.0f;
    public static final float DEFAULT_FOLLOW_SPEED = 14.0f;
    public static final float ZOOM_INTERPOLATION_SPEED = 10.0f;
    public static final float ZOOM_EPSILON = 1e-4f;

    // Default dimensions
    public static final float DEFAULT_VIEWPORT_WIDTH = 1280.0f;
    public static final float DEFAULT_VIEWPORT_HEIGHT = 720.0f;
    public static final float DEFAULT_WORLD_WIDTH = 4096.0f;
    public static final float DEFAULT_WORLD_HEIGHT = 4096.0f;

    // Input step constants
    public static final float MOUSE_WHEEL_ZOOM_STEP = 0.12f;
    public static final float KEYBOARD_ZOOM_STEP = 0.15f;

    // Viewport dimensions (pixels)
    private float viewportWidth;
    private float viewportHeight;
    private float viewportOffsetX = 0.0f;
    private float viewportOffsetY = 0.0f;

    // World boundaries (world coordinates)
    private float worldLeft = 0.0f;
    private float worldTop = 0.0f;
    private float worldWidth = DEFAULT_WORLD_WIDTH;
    private float worldHeight = DEFAULT_WORLD_HEIGHT;

    // Camera center position (clamped world coordinates)
    private float centerX;
    private float centerY;

    // Zoom state
    private float zoom;
    private float targetZoom;
    private float followSpeed = DEFAULT_FOLLOW_SPEED;
    private boolean clampingEnabled = true;

    // Reusable objects for zero-allocation performance
    private final Rectangle visibleBounds = new Rectangle();
    private OrthographicCamera gdxCamera;

    public GameCamera() {
        this(DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT, 0.0f, 0.0f, DEFAULT_WORLD_WIDTH, DEFAULT_WORLD_HEIGHT, DEFAULT_ZOOM);
    }

    public GameCamera(float viewportWidth, float viewportHeight) {
        this(viewportWidth, viewportHeight, 0.0f, 0.0f, DEFAULT_WORLD_WIDTH, DEFAULT_WORLD_HEIGHT, DEFAULT_ZOOM);
    }

    public GameCamera(float viewportWidth, float viewportHeight, float worldWidth, float worldHeight) {
        this(viewportWidth, viewportHeight, 0.0f, 0.0f, worldWidth, worldHeight, DEFAULT_ZOOM);
    }

    public GameCamera(float viewportWidth, float viewportHeight, float worldLeft, float worldTop,
                      float worldWidth, float worldHeight, float initialZoom) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.worldLeft = worldLeft;
        this.worldTop = worldTop;
        this.worldWidth = worldWidth;
        this.worldHeight = worldHeight;
        this.zoom = MathUtils.clamp(initialZoom, MIN_ZOOM, MAX_ZOOM);
        this.targetZoom = this.zoom;

        // Default initial center to center of world bounds
        float initCenterX = worldLeft + worldWidth * 0.5f;
        float initCenterY = worldTop + worldHeight * 0.5f;
        setCenter(initCenterX, initCenterY);
    }

    // =========================================================================
    // Core Update & Positioning
    // =========================================================================

    /**
     * Updates camera position and zoom towards target with default follow speed (14.0f).
     */
    public void update(float targetX, float targetY, float dt) {
        update(targetX, targetY, dt, this.followSpeed);
    }

    /**
     * Updates camera position following target coordinates with specified smoothing speed,
     * and smoothly interpolates zoom towards targetZoom.
     */
    public void update(float targetX, float targetY, float dt, float smoothSpeed) {
        // 1. Smooth zoom interpolation
        if (dt > 0.0f && Math.abs(zoom - targetZoom) > ZOOM_EPSILON) {
            float zoomT = 1.0f - (float) Math.exp(-ZOOM_INTERPOLATION_SPEED * dt);
            zoom += (targetZoom - zoom) * zoomT;
            if (Math.abs(zoom - targetZoom) < ZOOM_EPSILON) {
                zoom = targetZoom;
            }
        } else if (dt <= 0.0f) {
            zoom = targetZoom;
        }

        // 2. Position tracking
        float targetCenterX = clampCenterX(targetX);
        float targetCenterY = clampCenterY(targetY);

        if (smoothSpeed <= 0.0f || dt <= 0.0f) {
            this.centerX = targetCenterX;
            this.centerY = targetCenterY;
        } else {
            float t = 1.0f - (float) Math.exp(-smoothSpeed * dt);
            float smoothedX = this.centerX + (targetCenterX - this.centerX) * t;
            float smoothedY = this.centerY + (targetCenterY - this.centerY) * t;
            this.centerX = clampCenterX(smoothedX);
            this.centerY = clampCenterY(smoothedY);
        }

        updateVisibleBounds();
    }

    /**
     * Explicitly teleports camera center to given coordinates (clamped to world bounds).
     */
    public void setCenter(float x, float y) {
        this.centerX = clampCenterX(x);
        this.centerY = clampCenterY(y);
        updateVisibleBounds();
    }

    public void setCenter(Vector2D center) {
        if (center != null) {
            setCenter(center.x, center.y);
        }
    }

    public void snapTo(float x, float y) {
        setCenter(x, y);
    }

    public void snapTo(Vector2D center) {
        setCenter(center);
    }

    // =========================================================================
    // Zoom Controls
    // =========================================================================

    public void setZoom(float zoom) {
        this.zoom = MathUtils.clamp(zoom, MIN_ZOOM, MAX_ZOOM);
        this.targetZoom = this.zoom;
        setCenter(this.centerX, this.centerY); // Re-clamp center for new zoom bounds
    }

    public void setTargetZoom(float targetZoom) {
        this.targetZoom = MathUtils.clamp(targetZoom, MIN_ZOOM, MAX_ZOOM);
    }

    public void adjustZoom(float delta) {
        setTargetZoom(this.targetZoom + delta);
    }

    public void zoomBy(float deltaZoom) {
        adjustZoom(deltaZoom);
    }

    public void resetZoom() {
        setTargetZoom(DEFAULT_ZOOM);
    }

    public float getZoom() {
        return zoom;
    }

    public float getTargetZoom() {
        return targetZoom;
    }

    // =========================================================================
    // Boundary Clamping
    // =========================================================================

    public float clampCenterX(float desiredX) {
        if (!clampingEnabled) {
            return desiredX;
        }
        float halfW = getViewWidth() * 0.5f;
        if (worldWidth <= getViewWidth()) {
            return worldLeft + worldWidth * 0.5f;
        }
        float minX = worldLeft + halfW;
        float maxX = worldLeft + worldWidth - halfW;
        return MathUtils.clamp(desiredX, minX, maxX);
    }

    public float clampCenterY(float desiredY) {
        if (!clampingEnabled) {
            return desiredY;
        }
        float halfH = getViewHeight() * 0.5f;
        if (worldHeight <= getViewHeight()) {
            return worldTop + worldHeight * 0.5f;
        }
        float minY = worldTop + halfH;
        float maxY = worldTop + worldHeight - halfH;
        return MathUtils.clamp(desiredY, minY, maxY);
    }

    public boolean isClampingEnabled() {
        return clampingEnabled;
    }

    public void setClampingEnabled(boolean clampingEnabled) {
        this.clampingEnabled = clampingEnabled;
    }

    /**
     * Shifts the camera center and boundary coordinates by (-deltaX, -deltaY)
     * during a centralized atomic floating-origin shift.
     */
    public void rebase(float deltaX, float deltaY) {
        this.centerX -= deltaX;
        this.centerY -= deltaY;
        this.visibleBounds.x -= deltaX;
        this.visibleBounds.y -= deltaY;
        this.worldLeft -= deltaX;
        this.worldTop -= deltaY;
        if (this.gdxCamera != null) {
            this.gdxCamera.position.set(this.centerX, this.centerY, 0.0f);
            this.gdxCamera.update();
        }
    }

    @Override
    public void onOriginShift(long deltaX, long deltaY) {
        rebase((float) deltaX, (float) deltaY);
    }

    // =========================================================================
    // Frustum & Visibility
    // =========================================================================

    public float getViewWidth() {
        return viewportWidth / zoom;
    }

    public float getViewHeight() {
        return viewportHeight / zoom;
    }

    public float getVisibleLeft() {
        return centerX - getViewWidth() * 0.5f;
    }

    public float getVisibleTop() {
        return centerY - getViewHeight() * 0.5f;
    }

    public Rectangle getVisibleBounds() {
        updateVisibleBounds();
        return visibleBounds;
    }

    private void updateVisibleBounds() {
        float vw = getViewWidth();
        float vh = getViewHeight();
        visibleBounds.set(centerX - vw * 0.5f, centerY - vh * 0.5f, vw, vh);
    }

    public boolean isVisible(float x, float y, float width, float height) {
        float vl = getVisibleLeft();
        float vt = getVisibleTop();
        float vw = getViewWidth();
        float vh = getViewHeight();
        return (x + width >= vl && x <= vl + vw && y + height >= vt && y <= vt + vh);
    }

    public boolean isVisible(Rectangle rect) {
        if (rect == null) return false;
        return isVisible(rect.x, rect.y, rect.width, rect.height);
    }

    public boolean isVisible(float posX, float posY, float radius) {
        float vl = getVisibleLeft();
        float vt = getVisibleTop();
        float vw = getViewWidth();
        float vh = getViewHeight();
        return (posX + radius >= vl && posX - radius <= vl + vw &&
                posY + radius >= vt && posY - radius <= vt + vh);
    }

    public boolean isVisible(Vector2D pos, float radius) {
        if (pos == null) return false;
        return isVisible(pos.x, pos.y, radius);
    }

    // =========================================================================
    // Coordinate Unprojection & Projection
    // =========================================================================

    public float screenToWorldX(float screenX) {
        return centerX + ((screenX - viewportOffsetX) - viewportWidth * 0.5f) / zoom;
    }

    public float screenToWorldY(float screenY) {
        return centerY + ((screenY - viewportOffsetY) - viewportHeight * 0.5f) / zoom;
    }

    public Vector2D screenToWorld(float screenX, float screenY) {
        return new Vector2D(screenToWorldX(screenX), screenToWorldY(screenY));
    }

    public Vector2D screenToWorld(float screenX, float screenY, Vector2D out) {
        if (out == null) {
            return screenToWorld(screenX, screenY);
        }
        out.x = screenToWorldX(screenX);
        out.y = screenToWorldY(screenY);
        return out;
    }

    public float worldToScreenX(float worldX) {
        return (worldX - centerX) * zoom + viewportWidth * 0.5f + viewportOffsetX;
    }

    public float worldToScreenY(float worldY) {
        return (worldY - centerY) * zoom + viewportHeight * 0.5f + viewportOffsetY;
    }

    public Vector2D worldToScreen(float worldX, float worldY) {
        return new Vector2D(worldToScreenX(worldX), worldToScreenY(worldY));
    }

    public Vector2D worldToScreen(float worldX, float worldY, Vector2D out) {
        if (out == null) {
            return worldToScreen(worldX, worldY);
        }
        out.x = worldToScreenX(worldX);
        out.y = worldToScreenY(worldY);
        return out;
    }

    // =========================================================================
    // LibGDX Rendering Integration
    // =========================================================================

    public OrthographicCamera getOrthographicCamera() {
        if (gdxCamera == null) {
            gdxCamera = new OrthographicCamera();
            gdxCamera.setToOrtho(true, viewportWidth, viewportHeight);
        }
        syncTo(gdxCamera);
        return gdxCamera;
    }

    public void syncTo(OrthographicCamera targetCamera) {
        if (targetCamera == null) return;
        targetCamera.viewportWidth = viewportWidth;
        targetCamera.viewportHeight = viewportHeight;
        targetCamera.zoom = 1.0f / zoom;
        targetCamera.position.set(centerX, centerY, 0.0f);
        targetCamera.update();
    }

    // =========================================================================
    // Setters & Getters
    // =========================================================================

    public float getCenterX() {
        return centerX;
    }

    public float getCenterY() {
        return centerY;
    }

    public Vector2D getCenter() {
        return new Vector2D(centerX, centerY);
    }

    public float getViewportWidth() {
        return viewportWidth;
    }

    public float getViewportHeight() {
        return viewportHeight;
    }

    public void setViewportSize(float width, float height) {
        this.viewportWidth = width;
        this.viewportHeight = height;
        setCenter(this.centerX, this.centerY);
    }

    public float getViewportOffsetX() {
        return viewportOffsetX;
    }

    public float getViewportOffsetY() {
        return viewportOffsetY;
    }

    public void setViewportOffset(float offsetX, float offsetY) {
        this.viewportOffsetX = offsetX;
        this.viewportOffsetY = offsetY;
    }

    public float getWorldLeft() {
        return worldLeft;
    }

    public float getWorldTop() {
        return worldTop;
    }

    public float getWorldWidth() {
        return worldWidth;
    }

    public float getWorldHeight() {
        return worldHeight;
    }

    public void setWorldBounds(float left, float top, float width, float height) {
        this.worldLeft = left;
        this.worldTop = top;
        this.worldWidth = width;
        this.worldHeight = height;
        setCenter(this.centerX, this.centerY);
    }

    public float getFollowSpeed() {
        return followSpeed;
    }

    public void setFollowSpeed(float followSpeed) {
        this.followSpeed = followSpeed;
    }
}
