package com.spellstorm.math;

/**
 * Pure engine mathematical utilities, interpolation, angle normalization,
 * and geometric queries for SpellStorm.
 *
 * All methods operate on scalar primitives or Vector2D with zero external graphics dependencies.
 */
public final class MathUtils {

    private MathUtils() {
        // Utility class
    }

    // =========================================================================
    // Mathematical Constants
    // =========================================================================

    public static final float PI = 3.141592653589793f;
    public static final float TWO_PI = 6.283185307179586f;
    public static final float HALF_PI = 1.5707963267948966f;
    public static final float DEG_TO_RAD = PI / 180.0f;
    public static final float RAD_TO_DEG = 180.0f / PI;
    public static final float EPSILON = 1e-5f;

    // =========================================================================
    // Clamping Utilities
    // =========================================================================

    public static float clamp(float value, float min, float max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static int clamp(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    public static double clamp(double value, double min, double max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    // =========================================================================
    // Linear & Non-Linear Interpolation
    // =========================================================================

    /**
     * Unclamped linear interpolation: a + (b - a) * t.
     */
    public static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    /**
     * Clamped linear interpolation where t is clamped to [0.0, 1.0].
     */
    public static float lerpClamped(float a, float b, float t) {
        return lerp(a, b, clamp(t, 0.0f, 1.0f));
    }

    /**
     * Standard Hermite smoothstep interpolation between edge0 and edge1.
     */
    public static float smoothstep(float edge0, float edge1, float x) {
        if (edge0 == edge1) {
            return x >= edge1 ? 1.0f : 0.0f;
        }
        float t = clamp((x - edge0) / (edge1 - edge0), 0.0f, 1.0f);
        return t * t * (3.0f - 2.0f * t);
    }

    /**
     * Hermite smoothstep for normalized t in [0, 1].
     */
    public static float smoothstep(float t) {
        float clamped = clamp(t, 0.0f, 1.0f);
        return clamped * clamped * (3.0f - 2.0f * clamped);
    }

    /**
     * Frame-rate independent exponential decay smoothing:
     * target + (current - target) * exp(-speed * dt).
     * Returns current immediately if dt &lt;= 0.
     */
    public static float expDecay(float current, float target, float speed, float dt) {
        if (dt <= 0.0f) {
            return current;
        }
        return target + (current - target) * (float) Math.exp(-speed * dt);
    }

    // =========================================================================
    // Angle Conversions & Normalization
    // =========================================================================

    public static float degToRad(float degrees) {
        return degrees * DEG_TO_RAD;
    }

    public static float radToDeg(float radians) {
        return radians * RAD_TO_DEG;
    }

    /**
     * Normalizes an angle in radians to [-PI, PI].
     */
    public static float normalizeAngle(float radians) {
        float a = radians % TWO_PI;
        if (a > PI) {
            a -= TWO_PI;
        } else if (a < -PI) {
            a += TWO_PI;
        }
        return a;
    }

    /**
     * Normalizes an angle in radians to [0, 2*PI).
     */
    public static float normalizeAnglePositive(float radians) {
        float a = radians % TWO_PI;
        if (a < 0.0f) {
            a += TWO_PI;
        }
        return a;
    }

    /**
     * Normalizes an angle in degrees to [-180, 180].
     */
    public static float normalizeAngleDeg(float degrees) {
        float a = degrees % 360.0f;
        if (a > 180.0f) {
            a -= 360.0f;
        } else if (a < -180.0f) {
            a += 360.0f;
        }
        return a;
    }

    /**
     * Normalizes an angle in degrees to [0, 360).
     */
    public static float normalizeAngleDegPositive(float degrees) {
        float a = degrees % 360.0f;
        if (a < 0.0f) {
            a += 360.0f;
        }
        return a;
    }

    /**
     * Calculates the shortest signed angular difference from fromRad to toRad in [-PI, PI].
     */
    public static float shortestAngleDifference(float fromRad, float toRad) {
        return normalizeAngle(toRad - fromRad);
    }

    public static float angleDifference(float fromRad, float toRad) {
        return shortestAngleDifference(fromRad, toRad);
    }

    public static float angleDifferenceDeg(float fromDeg, float toDeg) {
        return normalizeAngleDeg(toDeg - fromDeg);
    }

    /**
     * Interpolates between two angles in radians along the shortest path.
     */
    public static float lerpAngle(float fromRad, float toRad, float progress) {
        return fromRad + shortestAngleDifference(fromRad, toRad) * progress;
    }

    /**
     * Interpolates between two angles in degrees along the shortest path.
     */
    public static float lerpAngleDeg(float fromDeg, float toDeg, float progress) {
        return fromDeg + angleDifferenceDeg(fromDeg, toDeg) * progress;
    }

    // =========================================================================
    // Distance & Geometric Queries
    // =========================================================================

    public static float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public static float distanceSq(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return dx * dx + dy * dy;
    }

    public static float angleOf(float x, float y) {
        return (float) Math.atan2(y, x);
    }

    public static float angleBetween(float x1, float y1, float x2, float y2) {
        return (float) Math.atan2(y2 - y1, x2 - x1);
    }

    public static Vector2D fromAngle(float radians) {
        return Vector2D.fromAngle(radians);
    }

    public static Vector2D fromAngleDeg(float degrees) {
        return Vector2D.fromAngleDeg(degrees);
    }

    /**
     * Checks if two circles overlap (distanceSq &lt;= (r1 + r2)^2), matching C++ ss::math::circlesOverlap.
     */
    public static boolean circlesOverlap(float x1, float y1, float r1, float x2, float y2, float r2) {
        if (r1 < 0.0f || r2 < 0.0f) return false;
        float totalRadius = r1 + r2;
        return distanceSq(x1, y1, x2, y2) <= totalRadius * totalRadius;
    }

    public static boolean circlesOverlap(Vector2D a, float radiusA, Vector2D b, float radiusB) {
        if (a == null || b == null) return false;
        return circlesOverlap(a.x, a.y, radiusA, b.x, b.y, radiusB);
    }

    // =========================================================================
    // Float Comparison Helpers
    // =========================================================================

    public static boolean isZero(float value) {
        return Math.abs(value) <= EPSILON;
    }

    public static boolean isZero(float value, float tolerance) {
        return Math.abs(value) <= tolerance;
    }

    public static boolean isEqual(float a, float b) {
        return Math.abs(a - b) <= EPSILON;
    }

    public static boolean isEqual(float a, float b, float tolerance) {
        return Math.abs(a - b) <= tolerance;
    }
}
