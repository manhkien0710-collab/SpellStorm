package com.spellstorm.math;

/**
 * Pure mathematical collision detection and sliding resolution engine for SpellStorm.
 *
 * Implements:
 * - Circle vs Circle collision with exact C++ parity (distSq &lt;= (r1 + r2)^2).
 * - Circle vs AABB collision with exact C++ parity (distSq &lt; r^2 with closest-point clamping).
 * - Point vs AABB, Point vs Circle, and AABB vs AABB primitive queries.
 * - Single-obstacle axis-aligned 1D sliding resolution (resolveCircleVsAABBSliding).
 * - Higher-order 2-pass sliding collision resolution (resolveSliding) with TraversabilityPredicate.
 * - Zero graphics dependencies; 100% headless JUnit 5 compatible.
 */
public final class CollisionUtils {

    public static final float EPSILON = 1e-5f;

    private CollisionUtils() {
        // Utility class
    }

    // =========================================================================
    // Functional Traversability Predicate
    // =========================================================================

    @FunctionalInterface
    public interface TraversabilityPredicate {
        /**
         * Checks whether an entity with the given collision radius can legally occupy (x, y).
         *
         * @param x world X position
         * @param y world Y position
         * @param radius entity collision radius
         * @return true if position is traversable (unblocked), false if obstructed
         */
        boolean isTraversable(float x, float y, float radius);
    }

    // =========================================================================
    // Circle vs Circle
    // =========================================================================

    /**
     * Checks whether two 2D circles overlap.
     * Returns true if distance between centers is &lt;= sum of radii, matching C++ ss::math::circlesOverlap.
     */
    public static boolean checkCircleVsCircle(float c1x, float c1y, float r1,
                                              float c2x, float c2y, float r2) {
        if (r1 < 0.0f || r2 < 0.0f) return false;
        float dx = c1x - c2x;
        float dy = c1y - c2y;
        float totalRadius = r1 + r2;
        return (dx * dx + dy * dy) <= (totalRadius * totalRadius);
    }

    public static boolean checkCircleVsCircle(Vector2D c1, float r1, Vector2D c2, float r2) {
        if (c1 == null || c2 == null) return false;
        return checkCircleVsCircle(c1.x, c1.y, r1, c2.x, c2.y, r2);
    }

    /**
     * Computes penetration depth and separation normal for two overlapping circles.
     *
     * @return PenetrationResult pointing from C2 towards C1, or null if circles do not overlap.
     */
    public static PenetrationResult getCircleVsCirclePenetration(float c1x, float c1y, float r1,
                                                                 float c2x, float c2y, float r2) {
        if (r1 < 0.0f || r2 < 0.0f) return null;
        float dx = c1x - c2x;
        float dy = c1y - c2y;
        float distSq = dx * dx + dy * dy;
        float rSum = r1 + r2;
        if (distSq > rSum * rSum) {
            return null;
        }

        float dist = (float) Math.sqrt(distSq);
        float depth = rSum - dist;
        float nx;
        float ny;
        if (dist > EPSILON) {
            nx = dx / dist;
            ny = dy / dist;
        } else {
            // Degenerate case: coincident centers fallback to arbitrary valid unit normal
            nx = 1.0f;
            ny = 0.0f;
        }
        return new PenetrationResult(depth, nx, ny);
    }

    public static PenetrationResult getCircleVsCirclePenetration(Vector2D c1, float r1, Vector2D c2, float r2) {
        if (c1 == null || c2 == null) return null;
        return getCircleVsCirclePenetration(c1.x, c1.y, r1, c2.x, c2.y, r2);
    }

    // =========================================================================
    // Circle vs AABB
    // =========================================================================

    /**
     * Checks whether a circle overlaps an Axis-Aligned Bounding Box (AABB).
     * Uses strict inequality &lt; matching C++ TileMap.cpp:162 ((dx*dx + dy*dy) &lt; (radius*radius)).
     */
    public static boolean checkCircleVsAABB(float cx, float cy, float r,
                                            float rx, float ry, float rw, float rh) {
        if (r <= 0.0f || rw <= 0.0f || rh <= 0.0f) return false;
        float closestX = Math.max(rx, Math.min(cx, rx + rw));
        float closestY = Math.max(ry, Math.min(cy, ry + rh));
        float dx = cx - closestX;
        float dy = cy - closestY;
        return (dx * dx + dy * dy) < (r * r);
    }

    public static boolean checkCircleVsAABB(Vector2D center, float r,
                                            float rx, float ry, float rw, float rh) {
        if (center == null) return false;
        return checkCircleVsAABB(center.x, center.y, r, rx, ry, rw, rh);
    }

    // =========================================================================
    // Point Tests
    // =========================================================================

    /**
     * Checks whether a 2D point is inside or on the boundary of an AABB.
     */
    public static boolean checkPointVsAABB(float px, float py,
                                           float rx, float ry, float rw, float rh) {
        if (rw < 0.0f || rh < 0.0f) return false;
        return px >= rx && px <= rx + rw && py >= ry && py <= ry + rh;
    }

    public static boolean checkPointVsAABB(Vector2D p, float rx, float ry, float rw, float rh) {
        if (p == null) return false;
        return checkPointVsAABB(p.x, p.y, rx, ry, rw, rh);
    }

    /**
     * Checks whether a 2D point is inside or on the boundary of a circle.
     */
    public static boolean checkPointVsCircle(float px, float py, float cx, float cy, float r) {
        if (r < 0.0f) return false;
        float dx = px - cx;
        float dy = py - cy;
        return (dx * dx + dy * dy) <= (r * r);
    }

    public static boolean checkPointVsCircle(Vector2D p, Vector2D center, float r) {
        if (p == null || center == null) return false;
        return checkPointVsCircle(p.x, p.y, center.x, center.y, r);
    }

    // =========================================================================
    // AABB vs AABB
    // =========================================================================

    /**
     * Checks whether two AABBs overlap.
     */
    public static boolean checkAABBVsAABB(float x1, float y1, float w1, float h1,
                                          float x2, float y2, float w2, float h2) {
        if (w1 <= 0.0f || h1 <= 0.0f || w2 <= 0.0f || h2 <= 0.0f) return false;
        return x1 < x2 + w2 && x1 + w1 > x2 && y1 < y2 + h2 && y1 + h1 > y2;
    }

    // =========================================================================
    // Sliding Collision Resolution
    // =========================================================================

    /**
     * Resolves movement of a circle against a single AABB obstacle using axis-aligned sliding:
     * 1. Try full movement (newX, newY).
     * 2. If blocked, try X-only movement (newX, oldY).
     * 3. If blocked, try Y-only movement (oldX, newY).
     * 4. If blocked, retain (oldX, oldY).
     */
    public static Vector2D resolveCircleVsAABBSliding(float oldX, float oldY,
                                                      float newX, float newY,
                                                      float radius,
                                                      float rx, float ry, float rw, float rh) {
        // Fast path: if desired new position does not intersect obstacle
        if (!checkCircleVsAABB(newX, newY, radius, rx, ry, rw, rh)) {
            return new Vector2D(newX, newY);
        }

        // Try X-only movement (sliding along vertical wall face)
        if (!checkCircleVsAABB(newX, oldY, radius, rx, ry, rw, rh)) {
            return new Vector2D(newX, oldY);
        }

        // Try Y-only movement (sliding along horizontal wall face)
        if (!checkCircleVsAABB(oldX, newY, radius, rx, ry, rw, rh)) {
            return new Vector2D(oldX, newY);
        }

        // Both axes blocked: stay at old position
        return new Vector2D(oldX, oldY);
    }

    public static Vector2D resolveCircleVsAABBSliding(Vector2D oldPos, Vector2D newPos,
                                                      float radius,
                                                      float rx, float ry, float rw, float rh) {
        float ox = oldPos != null ? oldPos.x : 0.0f;
        float oy = oldPos != null ? oldPos.y : 0.0f;
        float nx = newPos != null ? newPos.x : 0.0f;
        float ny = newPos != null ? newPos.y : 0.0f;
        return resolveCircleVsAABBSliding(ox, oy, nx, ny, radius, rx, ry, rw, rh);
    }

    /**
     * Higher-order 2-pass sliding collision resolution against an arbitrary environment,
     * matching C++ TileMap::resolveCollision:
     * 1. Clamps candidate position within world boundaries [radius, mapW - radius] x [radius, mapH - radius].
     * 2. Tests full candidate position (candX, candY) via traversability predicate.
     * 3. If blocked, tests X-only movement (candX, oldY).
     * 4. If blocked, tests Y-only movement (oldX, candY).
     * 5. If blocked on both axes, returns old position (oldX, oldY).
     */
    public static Vector2D resolveSliding(float oldX, float oldY,
                                          float newX, float newY,
                                          float radius,
                                          float mapW, float mapH,
                                          TraversabilityPredicate traversable) {
        if (traversable == null) {
            return new Vector2D(newX, newY);
        }

        // Clamp candidate to world bounds
        float candX = Math.max(radius, Math.min(newX, mapW - radius));
        float candY = Math.max(radius, Math.min(newY, mapH - radius));

        // 1. Try full diagonal move
        if (traversable.isTraversable(candX, candY, radius)) {
            return new Vector2D(candX, candY);
        }

        // 2. Try X movement only
        if (traversable.isTraversable(candX, oldY, radius)) {
            return new Vector2D(candX, oldY);
        }

        // 3. Try Y movement only
        if (traversable.isTraversable(oldX, candY, radius)) {
            return new Vector2D(oldX, candY);
        }

        // 4. Blocked on both axes: retain old position
        return new Vector2D(oldX, oldY);
    }

    public static Vector2D resolveSliding(Vector2D oldPos, Vector2D newPos,
                                          float radius,
                                          float mapW, float mapH,
                                          TraversabilityPredicate traversable) {
        float ox = oldPos != null ? oldPos.x : 0.0f;
        float oy = oldPos != null ? oldPos.y : 0.0f;
        float nx = newPos != null ? newPos.x : 0.0f;
        float ny = newPos != null ? newPos.y : 0.0f;
        return resolveSliding(ox, oy, nx, ny, radius, mapW, mapH, traversable);
    }

    /**
     * Unbounded 2-pass sliding collision resolution without boundary clamping,
     * supporting infinite 4-quadrant exploration (including negative coordinates).
     *
     * 1. Tests full candidate position (newX, newY) via traversability predicate.
     * 2. If blocked, tests X-only movement (newX, oldY).
     * 3. If blocked, tests Y-only movement (oldX, newY).
     * 4. If blocked on both axes, returns old position (oldX, oldY).
     */
    public static Vector2D resolveSlidingUnbounded(float oldX, float oldY,
                                                  float newX, float newY,
                                                  float radius,
                                                  TraversabilityPredicate traversable) {
        if (traversable == null) {
            return new Vector2D(newX, newY);
        }

        // 1. Try full diagonal move
        if (traversable.isTraversable(newX, newY, radius)) {
            return new Vector2D(newX, newY);
        }

        // 2. Try X movement only
        if (traversable.isTraversable(newX, oldY, radius)) {
            return new Vector2D(newX, oldY);
        }

        // 3. Try Y movement only
        if (traversable.isTraversable(oldX, newY, radius)) {
            return new Vector2D(oldX, newY);
        }

        // 4. Blocked on both axes: retain old position
        return new Vector2D(oldX, oldY);
    }

    public static Vector2D resolveSlidingUnbounded(Vector2D oldPos, Vector2D newPos,
                                                  float radius,
                                                  TraversabilityPredicate traversable) {
        float ox = oldPos != null ? oldPos.x : 0.0f;
        float oy = oldPos != null ? oldPos.y : 0.0f;
        float nx = newPos != null ? newPos.x : 0.0f;
        float ny = newPos != null ? newPos.y : 0.0f;
        return resolveSlidingUnbounded(ox, oy, nx, ny, radius, traversable);
    }

    // =========================================================================
    // Spatial Bounds Clamping
    // =========================================================================

    /**
     * Clamps circle center coordinates so the circle remains strictly within the bounding box.
     */
    public static Vector2D clampToBounds(float x, float y, float radius,
                                         float minX, float minY, float width, float height) {
        float cx = Math.max(minX + radius, Math.min(x, minX + width - radius));
        float cy = Math.max(minY + radius, Math.min(y, minY + height - radius));
        return new Vector2D(cx, cy);
    }

    // =========================================================================
    // Penetration Result Record
    // =========================================================================

    public record PenetrationResult(float depth, float normalX, float normalY) {
        public Vector2D normal() {
            return new Vector2D(normalX, normalY);
        }

        public Vector2D pushVector() {
            return new Vector2D(normalX * depth, normalY * depth);
        }
    }
}
