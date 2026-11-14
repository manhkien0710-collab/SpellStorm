package com.spellstorm.math;

import com.badlogic.gdx.math.Vector2;
import java.io.Serializable;
import java.util.Objects;

/**
 * High-performance 2D floating-point vector for the SpellStorm engine.
 *
 * Design features:
 * - Direct public fields (x, y) for high-performance math inner loops.
 * - Fluid mutable chaining pattern matching LibGDX Vector2 where operations return {@code this}.
 * - Defensive copy support via {@link #cpy()}.
 * - Zero/near-zero vector protection in {@link #nor()} and {@link #div(float)} preventing NaN or Infinity.
 * - Full bi-directional interoperability with LibGDX {@link Vector2}.
 * - Comprehensive trigonometry, angles (radians in [-PI, PI], degrees in [0, 360)), rotation, and clamping.
 */
public class Vector2D implements Serializable, Cloneable {

    private static final long serialVersionUID = 1L;
    public static final float EPSILON = 1e-5f;

    public float x;
    public float y;

    // =========================================================================
    // Constructors
    // =========================================================================

    public Vector2D() {
        this.x = 0.0f;
        this.y = 0.0f;
    }

    public Vector2D(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public Vector2D(Vector2D other) {
        if (other != null) {
            this.x = other.x;
            this.y = other.y;
        } else {
            this.x = 0.0f;
            this.y = 0.0f;
        }
    }

    public Vector2D(Vector2 gdx) {
        if (gdx != null) {
            this.x = gdx.x;
            this.y = gdx.y;
        } else {
            this.x = 0.0f;
            this.y = 0.0f;
        }
    }

    // =========================================================================
    // Static Factories & Helpers
    // =========================================================================

    public static Vector2D of(float x, float y) {
        return new Vector2D(x, y);
    }

    public static Vector2D zero() {
        return new Vector2D(0.0f, 0.0f);
    }

    public static Vector2D fromAngle(float radians) {
        return new Vector2D((float) Math.cos(radians), (float) Math.sin(radians));
    }

    public static Vector2D fromAngleDeg(float degrees) {
        float radians = (float) Math.toRadians(degrees);
        return fromAngle(radians);
    }

    public static Vector2D fromGdx(Vector2 gdx) {
        return new Vector2D(gdx);
    }

    public static Vector2D add(Vector2D a, Vector2D b) {
        float ax = a != null ? a.x : 0.0f;
        float ay = a != null ? a.y : 0.0f;
        float bx = b != null ? b.x : 0.0f;
        float by = b != null ? b.y : 0.0f;
        return new Vector2D(ax + bx, ay + by);
    }

    public static Vector2D sub(Vector2D a, Vector2D b) {
        float ax = a != null ? a.x : 0.0f;
        float ay = a != null ? a.y : 0.0f;
        float bx = b != null ? b.x : 0.0f;
        float by = b != null ? b.y : 0.0f;
        return new Vector2D(ax - bx, ay - by);
    }

    public static float dist(Vector2D a, Vector2D b) {
        if (a == null || b == null) return 0.0f;
        return a.dist(b);
    }

    public static float dist2(Vector2D a, Vector2D b) {
        if (a == null || b == null) return 0.0f;
        return a.dist2(b);
    }

    // =========================================================================
    // Getters & Setters
    // =========================================================================

    public float getX() {
        return x;
    }

    public void setX(float x) {
        this.x = x;
    }

    public float getY() {
        return y;
    }

    public void setY(float y) {
        this.y = y;
    }

    // =========================================================================
    // Assignment & Cloning
    // =========================================================================

    public Vector2D set(float x, float y) {
        this.x = x;
        this.y = y;
        return this;
    }

    public Vector2D set(Vector2D other) {
        if (other != null) {
            this.x = other.x;
            this.y = other.y;
        } else {
            this.x = 0.0f;
            this.y = 0.0f;
        }
        return this;
    }

    public Vector2D set(Vector2 gdx) {
        if (gdx != null) {
            this.x = gdx.x;
            this.y = gdx.y;
        } else {
            this.x = 0.0f;
            this.y = 0.0f;
        }
        return this;
    }

    public Vector2D setZero() {
        this.x = 0.0f;
        this.y = 0.0f;
        return this;
    }

    public Vector2D cpy() {
        return new Vector2D(this.x, this.y);
    }

    @Override
    public Vector2D clone() {
        return cpy();
    }

    // =========================================================================
    // Basic Arithmetic (Fluid Chaining)
    // =========================================================================

    public Vector2D add(float x, float y) {
        this.x += x;
        this.y += y;
        return this;
    }

    public Vector2D add(Vector2D other) {
        if (other != null) {
            this.x += other.x;
            this.y += other.y;
        }
        return this;
    }

    public Vector2D sub(float x, float y) {
        this.x -= x;
        this.y -= y;
        return this;
    }

    public Vector2D sub(Vector2D other) {
        if (other != null) {
            this.x -= other.x;
            this.y -= other.y;
        }
        return this;
    }

    public Vector2D scl(float scalar) {
        this.x *= scalar;
        this.y *= scalar;
        return this;
    }

    public Vector2D scl(float x, float y) {
        this.x *= x;
        this.y *= y;
        return this;
    }

    public Vector2D scl(Vector2D other) {
        if (other != null) {
            this.x *= other.x;
            this.y *= other.y;
        }
        return this;
    }

    // Aliases for scl
    public Vector2D mul(float scalar) {
        return scl(scalar);
    }

    public Vector2D mul(float x, float y) {
        return scl(x, y);
    }

    public Vector2D mul(Vector2D other) {
        return scl(other);
    }

    public Vector2D mulAdd(Vector2D vec, float scalar) {
        if (vec != null) {
            this.x += vec.x * scalar;
            this.y += vec.y * scalar;
        }
        return this;
    }

    public Vector2D div(float scalar) {
        if (Math.abs(scalar) > EPSILON) {
            this.x /= scalar;
            this.y /= scalar;
        } else {
            this.x = 0.0f;
            this.y = 0.0f;
        }
        return this;
    }

    public Vector2D div(float ox, float oy) {
        this.x = Math.abs(ox) > EPSILON ? (this.x / ox) : 0.0f;
        this.y = Math.abs(oy) > EPSILON ? (this.y / oy) : 0.0f;
        return this;
    }

    public Vector2D div(Vector2D other) {
        if (other != null) {
            return div(other.x, other.y);
        }
        this.x = 0.0f;
        this.y = 0.0f;
        return this;
    }

    // =========================================================================
    // Magnitude & Normalization
    // =========================================================================

    public float len() {
        return (float) Math.sqrt(x * x + y * y);
    }

    public float len2() {
        return x * x + y * y;
    }

    /**
     * Normalizes this vector in-place.
     * Division-by-zero protection: if magnitude is &lt;= 1e-5f, the vector is set to (0, 0),
     * matching C++ ss::math::normalize.
     */
    public Vector2D nor() {
        float length = len();
        if (length > EPSILON) {
            this.x /= length;
            this.y /= length;
        } else {
            this.x = 0.0f;
            this.y = 0.0f;
        }
        return this;
    }

    public Vector2D setLength(float length) {
        return setLength2(length * length);
    }

    public Vector2D setLength2(float length2) {
        float oldLen2 = len2();
        if (oldLen2 <= EPSILON || oldLen2 == length2) {
            return this;
        }
        return scl((float) Math.sqrt(length2 / oldLen2));
    }

    public Vector2D limit(float max) {
        return limit2(max * max);
    }

    public Vector2D limit2(float max2) {
        float currentLen2 = len2();
        if (currentLen2 > max2) {
            scl((float) Math.sqrt(max2 / currentLen2));
        }
        return this;
    }

    public Vector2D clamp(float min, float max) {
        float currentLen2 = len2();
        if (currentLen2 == 0.0f) {
            return this;
        }
        float max2 = max * max;
        if (currentLen2 > max2) {
            return scl((float) Math.sqrt(max2 / currentLen2));
        }
        float min2 = min * min;
        if (currentLen2 < min2) {
            return scl((float) Math.sqrt(min2 / currentLen2));
        }
        return this;
    }

    // =========================================================================
    // Products & Angles
    // =========================================================================

    public float dot(float ox, float oy) {
        return x * ox + y * oy;
    }

    public float dot(Vector2D other) {
        if (other == null) return 0.0f;
        return x * other.x + y * other.y;
    }

    /**
     * Computes 2D cross product: x * oy - y * ox.
     */
    public float cross(float ox, float oy) {
        return x * oy - y * ox;
    }

    public float cross(Vector2D other) {
        if (other == null) return 0.0f;
        return x * other.y - y * other.x;
    }

    public float crs(float ox, float oy) {
        return cross(ox, oy);
    }

    public float crs(Vector2D other) {
        return cross(other);
    }

    /**
     * Returns angle in radians in the range [-PI, PI], matching C++ ss::math::angleOf.
     */
    public float angleRad() {
        return (float) Math.atan2(y, x);
    }

    /**
     * Returns angle in degrees in the range [0, 360), matching LibGDX Vector2.angleDeg().
     */
    public float angleDeg() {
        float deg = (float) Math.toDegrees(Math.atan2(y, x));
        if (deg < 0.0f) {
            deg += 360.0f;
        }
        return deg;
    }

    /**
     * Returns angle in degrees signed in the range [-180, 180].
     */
    public float angleDegSigned() {
        return (float) Math.toDegrees(Math.atan2(y, x));
    }

    public float angleRad(Vector2D reference) {
        if (reference == null) return 0.0f;
        return (float) Math.atan2(cross(reference), dot(reference));
    }

    public float angleDeg(Vector2D reference) {
        float rad = angleRad(reference);
        float deg = (float) Math.toDegrees(rad);
        if (deg < 0.0f) {
            deg += 360.0f;
        }
        return deg;
    }

    // =========================================================================
    // Distance
    // =========================================================================

    public float dist(float ox, float oy) {
        float dx = ox - this.x;
        float dy = oy - this.y;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }

    public float dist(Vector2D other) {
        if (other == null) return 0.0f;
        return dist(other.x, other.y);
    }

    public float dst(float ox, float oy) {
        return dist(ox, oy);
    }

    public float dst(Vector2D other) {
        return dist(other);
    }

    public float dist2(float ox, float oy) {
        float dx = ox - this.x;
        float dy = oy - this.y;
        return dx * dx + dy * dy;
    }

    public float dist2(Vector2D other) {
        if (other == null) return 0.0f;
        return dist2(other.x, other.y);
    }

    public float dst2(float ox, float oy) {
        return dist2(ox, oy);
    }

    public float dst2(Vector2D other) {
        return dist2(other);
    }

    // =========================================================================
    // Transformations
    // =========================================================================

    /**
     * Rotates this vector counter-clockwise by the given radians in-place.
     */
    public Vector2D rotateRad(float radians) {
        float cos = (float) Math.cos(radians);
        float sin = (float) Math.sin(radians);
        float newX = this.x * cos - this.y * sin;
        float newY = this.x * sin + this.y * cos;
        this.x = newX;
        this.y = newY;
        return this;
    }

    public Vector2D rotateDeg(float degrees) {
        return rotateRad((float) Math.toRadians(degrees));
    }

    /**
     * Returns a new vector perpendicular to this vector: (-y, x), matching C++ ss::math::perpendicular.
     */
    public Vector2D perpendicular() {
        return new Vector2D(-this.y, this.x);
    }

    /**
     * Sets this vector to its perpendicular in-place: (-y, x).
     */
    public Vector2D setPerpendicular() {
        float oldX = this.x;
        this.x = -this.y;
        this.y = oldX;
        return this;
    }

    /**
     * Linear interpolation towards target vector in-place.
     */
    public Vector2D lerp(Vector2D target, float alpha) {
        if (target != null) {
            this.x += (target.x - this.x) * alpha;
            this.y += (target.y - this.y) * alpha;
        }
        return this;
    }

    // =========================================================================
    // Status Queries & Comparisons
    // =========================================================================

    public boolean isZero() {
        return x == 0.0f && y == 0.0f;
    }

    public boolean isZero(float margin) {
        return len2() <= margin * margin;
    }

    public boolean isUnit() {
        return isUnit(EPSILON);
    }

    public boolean isUnit(float margin) {
        return Math.abs(len2() - 1.0f) <= margin;
    }

    public boolean epsilonEquals(Vector2D other, float epsilon) {
        if (other == null) return false;
        return Math.abs(other.x - this.x) <= epsilon && Math.abs(other.y - this.y) <= epsilon;
    }

    public boolean epsilonEquals(float ox, float oy, float epsilon) {
        return Math.abs(ox - this.x) <= epsilon && Math.abs(oy - this.y) <= epsilon;
    }

    // =========================================================================
    // LibGDX Interoperability
    // =========================================================================

    public Vector2 toGdx() {
        return new Vector2(this.x, this.y);
    }

    public Vector2 toGdx(Vector2 out) {
        if (out == null) {
            return toGdx();
        }
        out.set(this.x, this.y);
        return out;
    }

    // =========================================================================
    // Standard Object Methods
    // =========================================================================

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Vector2D other)) return false;
        return Float.floatToIntBits(x) == Float.floatToIntBits(other.x) &&
               Float.floatToIntBits(y) == Float.floatToIntBits(other.y);
    }

    @Override
    public int hashCode() {
        return Objects.hash(x, y);
    }

    @Override
    public String toString() {
        return "(" + x + ", " + y + ")";
    }
}
