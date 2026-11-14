package com.spellstorm.math;

import com.badlogic.gdx.math.Vector2;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for {@link Vector2D}.
 * Covers Tiers 1, 2, 3, and 5 per TEST_INFRA.md and M2 Explorer blueprint.
 */
class Vector2DTest {

    private static final float TOLERANCE = 1e-5f;

    // =========================================================================
    // Tier 1: Equivalence Class Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Constructors, static factories, and accessors")
    void testConstructorsAndFactories() {
        Vector2D v0 = new Vector2D();
        assertEquals(0.0f, v0.x, TOLERANCE);
        assertEquals(0.0f, v0.y, TOLERANCE);

        Vector2D v1 = new Vector2D(3.5f, -4.5f);
        assertEquals(3.5f, v1.getX(), TOLERANCE);
        assertEquals(-4.5f, v1.getY(), TOLERANCE);

        Vector2D vCopy = new Vector2D(v1);
        assertEquals(3.5f, vCopy.x, TOLERANCE);
        assertEquals(-4.5f, vCopy.y, TOLERANCE);

        Vector2D vNull = new Vector2D((Vector2D) null);
        assertEquals(0.0f, vNull.x, TOLERANCE);
        assertEquals(0.0f, vNull.y, TOLERANCE);

        Vector2D vOf = Vector2D.of(12.0f, 24.0f);
        assertEquals(12.0f, vOf.x, TOLERANCE);
        assertEquals(24.0f, vOf.y, TOLERANCE);

        Vector2D vZero = Vector2D.zero();
        assertTrue(vZero.isZero());

        vZero.setX(10.0f);
        vZero.setY(20.0f);
        assertEquals(10.0f, vZero.x, TOLERANCE);
        assertEquals(20.0f, vZero.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Basic arithmetic and fluid chaining")
    void testBasicArithmeticAndChaining() {
        Vector2D v = new Vector2D(10.0f, 20.0f);

        // Fluid chaining check
        Vector2D ref = v.add(5.0f, 10.0f)
                        .sub(2.0f, 4.0f)
                        .scl(2.0f);
        assertSame(v, ref, "Fluid chaining must return the same instance");
        assertEquals(26.0f, v.x, TOLERANCE); // (10+5-2)*2 = 26
        assertEquals(52.0f, v.y, TOLERANCE); // (20+10-4)*2 = 52

        v.sub(new Vector2D(6.0f, 12.0f));
        assertEquals(20.0f, v.x, TOLERANCE);
        assertEquals(40.0f, v.y, TOLERANCE);

        v.add(new Vector2D(5.0f, -10.0f));
        assertEquals(25.0f, v.x, TOLERANCE);
        assertEquals(30.0f, v.y, TOLERANCE);

        // Multiplication aliases
        v.mul(2.0f);
        assertEquals(50.0f, v.x, TOLERANCE);
        assertEquals(60.0f, v.y, TOLERANCE);

        v.mul(0.5f, 0.5f);
        assertEquals(25.0f, v.x, TOLERANCE);
        assertEquals(30.0f, v.y, TOLERANCE);

        v.mul(new Vector2D(2.0f, 3.0f));
        assertEquals(50.0f, v.x, TOLERANCE);
        assertEquals(90.0f, v.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Static add, sub, dist helpers")
    void testStaticArithmeticHelpers() {
        Vector2D a = new Vector2D(10.0f, 20.0f);
        Vector2D b = new Vector2D(3.0f, 5.0f);

        Vector2D sum = Vector2D.add(a, b);
        assertEquals(13.0f, sum.x, TOLERANCE);
        assertEquals(25.0f, sum.y, TOLERANCE);
        assertEquals(10.0f, a.x, TOLERANCE, "Static add must be non-destructive");

        Vector2D diff = Vector2D.sub(a, b);
        assertEquals(7.0f, diff.x, TOLERANCE);
        assertEquals(15.0f, diff.y, TOLERANCE);

        assertEquals(Vector2D.dist(a, b), a.dist(b), TOLERANCE);
        assertEquals(Vector2D.dist2(a, b), a.dist2(b), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Length and distance calculations (3-4-5 right triangle)")
    void testLengthAndDistance() {
        Vector2D v = new Vector2D(3.0f, 4.0f);
        assertEquals(5.0f, v.len(), TOLERANCE);
        assertEquals(25.0f, v.len2(), TOLERANCE);

        Vector2D p1 = new Vector2D(1.0f, 2.0f);
        Vector2D p2 = new Vector2D(4.0f, 6.0f);
        assertEquals(5.0f, p1.dist(p2), TOLERANCE);
        assertEquals(5.0f, p1.dst(p2), TOLERANCE);
        assertEquals(25.0f, p1.dist2(p2), TOLERANCE);
        assertEquals(25.0f, p1.dst2(p2), TOLERANCE);
        assertEquals(5.0f, p1.dist(4.0f, 6.0f), TOLERANCE);
        assertEquals(25.0f, p1.dist2(4.0f, 6.0f), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Dot and Cross products")
    void testDotAndCrossProduct() {
        Vector2D east = new Vector2D(1.0f, 0.0f);
        Vector2D north = new Vector2D(0.0f, 1.0f);

        // Orthogonal vectors have dot product == 0
        assertEquals(0.0f, east.dot(north), TOLERANCE);
        assertEquals(0.0f, east.dot(0.0f, 1.0f), TOLERANCE);

        // Parallel vectors
        assertEquals(1.0f, east.dot(east), TOLERANCE);

        // Cross product: east x north == 1.0
        assertEquals(1.0f, east.cross(north), TOLERANCE);
        assertEquals(1.0f, east.crs(north), TOLERANCE);
        assertEquals(1.0f, east.cross(0.0f, 1.0f), TOLERANCE);

        // Anti-symmetry: north x east == -1.0
        assertEquals(-1.0f, north.cross(east), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: MulAdd fluid operation")
    void testMulAdd() {
        Vector2D pos = new Vector2D(100.0f, 200.0f);
        Vector2D vel = new Vector2D(10.0f, -20.0f);
        float dt = 0.5f;

        pos.mulAdd(vel, dt);
        assertEquals(105.0f, pos.x, TOLERANCE);
        assertEquals(190.0f, pos.y, TOLERANCE);
    }

    // =========================================================================
    // Tier 2: Boundary Value Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 2: Normalization edge cases and zero safety")
    void testNormalizationEdgeCases() {
        // Standard vector normalization (3, 4) -> (0.6, 0.8)
        Vector2D v = new Vector2D(3.0f, 4.0f);
        v.nor();
        assertEquals(0.6f, v.x, TOLERANCE);
        assertEquals(0.8f, v.y, TOLERANCE);
        assertTrue(v.isUnit());

        // Zero vector normalization -> (0, 0), zero safety without producing NaN
        Vector2D zero = new Vector2D(0.0f, 0.0f);
        zero.nor();
        assertFalse(Float.isNaN(zero.x));
        assertFalse(Float.isNaN(zero.y));
        assertEquals(0.0f, zero.x, TOLERANCE);
        assertEquals(0.0f, zero.y, TOLERANCE);

        // Sub-epsilon near-zero vector (1e-6f) -> resets to (0, 0)
        Vector2D nearZero = new Vector2D(1e-6f, 1e-6f);
        nearZero.nor();
        assertEquals(0.0f, nearZero.x, TOLERANCE);
        assertEquals(0.0f, nearZero.y, TOLERANCE);
        assertFalse(Float.isNaN(nearZero.x));
    }

    @Test
    @DisplayName("Tier 2: Division by zero safety")
    void testDivisionByZeroSafety() {
        Vector2D v = new Vector2D(10.0f, 20.0f);
        v.div(0.0f);
        assertFalse(Float.isInfinite(v.x));
        assertFalse(Float.isNaN(v.x));
        assertEquals(0.0f, v.x, TOLERANCE);
        assertEquals(0.0f, v.y, TOLERANCE);

        Vector2D vComp = new Vector2D(10.0f, 20.0f);
        vComp.div(0.0f, 5.0f);
        assertEquals(0.0f, vComp.x, TOLERANCE, "Zero divisor on X must result in 0.0f");
        assertEquals(4.0f, vComp.y, TOLERANCE, "Non-zero divisor on Y must compute 20/5 = 4.0f");

        Vector2D vOther = new Vector2D(15.0f, 30.0f);
        vOther.div(new Vector2D(3.0f, 0.0f));
        assertEquals(5.0f, vOther.x, TOLERANCE);
        assertEquals(0.0f, vOther.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Magnitude clamping and limiting")
    void testMagnitudeClampingAndLimiting() {
        Vector2D vLarge = new Vector2D(30.0f, 40.0f); // len = 50
        vLarge.clamp(10.0f, 25.0f);
        assertEquals(25.0f, vLarge.len(), TOLERANCE);

        Vector2D vSmall = new Vector2D(3.0f, 4.0f); // len = 5
        vSmall.clamp(10.0f, 25.0f);
        assertEquals(10.0f, vSmall.len(), TOLERANCE);

        Vector2D vMid = new Vector2D(6.0f, 8.0f); // len = 10
        vMid.clamp(5.0f, 20.0f);
        assertEquals(10.0f, vMid.len(), TOLERANCE);

        Vector2D vLimit = new Vector2D(30.0f, 40.0f); // len = 50
        vLimit.limit(35.0f);
        assertEquals(35.0f, vLimit.len(), TOLERANCE);

        Vector2D vSetLen = new Vector2D(3.0f, 4.0f);
        vSetLen.setLength(15.0f);
        assertEquals(15.0f, vSetLen.len(), TOLERANCE);
        assertEquals(9.0f, vSetLen.x, TOLERANCE);
        assertEquals(12.0f, vSetLen.y, TOLERANCE);
    }

    @ParameterizedTest
    @CsvSource({
        "1.0, 0.0, 0.0, 0.0",
        "0.0, 1.0, 1.5707963, 90.0",
        "-1.0, 0.0, 3.1415927, 180.0",
        "0.0, -1.0, -1.5707963, 270.0"
    })
    @DisplayName("Tier 2: Angle calculations and range conventions")
    void testAngleCalculations(float x, float y, float expectedRad, float expectedDeg) {
        Vector2D v = new Vector2D(x, y);
        assertEquals(expectedRad, v.angleRad(), 1e-4f);
        assertEquals(expectedDeg, v.angleDeg(), 1e-4f);
    }

    @Test
    @DisplayName("Tier 2: Zero and unit queries")
    void testZeroAndUnitQueries() {
        Vector2D zero = new Vector2D(0.0f, 0.0f);
        assertTrue(zero.isZero());
        assertTrue(zero.isZero(0.001f));
        assertFalse(zero.isUnit());

        Vector2D unit = new Vector2D(0.0f, 1.0f);
        assertFalse(unit.isZero());
        assertTrue(unit.isUnit());
        assertTrue(unit.isUnit(0.001f));

        Vector2D almostUnit = new Vector2D(0.0f, 1.0001f);
        assertFalse(almostUnit.isUnit(1e-6f));
        assertTrue(almostUnit.isUnit(0.001f));
    }

    @Test
    @DisplayName("Tier 2: Epsilon equality")
    void testEpsilonEquals() {
        Vector2D a = new Vector2D(10.0f, 20.0f);
        Vector2D b = new Vector2D(10.001f, 19.999f);

        assertTrue(a.epsilonEquals(b, 0.01f));
        assertFalse(a.epsilonEquals(b, 0.0001f));
        assertTrue(a.epsilonEquals(10.001f, 19.999f, 0.01f));
        assertFalse(a.epsilonEquals(null, 0.01f));
    }

    // =========================================================================
    // Tier 3: Combinatorial & Interop Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 3: 2D Rotations and perpendicular")
    void testRotationsAndPerpendicular() {
        Vector2D v = new Vector2D(1.0f, 0.0f);

        // Rotate 90 degrees CCW -> (0, 1)
        v.rotateDeg(90.0f);
        assertEquals(0.0f, v.x, 1e-4f);
        assertEquals(1.0f, v.y, 1e-4f);

        // Rotate 90 degrees CCW -> (-1, 0)
        v.rotateDeg(90.0f);
        assertEquals(-1.0f, v.x, 1e-4f);
        assertEquals(0.0f, v.y, 1e-4f);

        // Perpendicular vector: (-y, x)
        Vector2D original = new Vector2D(3.0f, 4.0f);
        Vector2D perp = original.perpendicular();
        assertEquals(-4.0f, perp.x, TOLERANCE);
        assertEquals(3.0f, perp.y, TOLERANCE);
        assertEquals(0.0f, original.dot(perp), TOLERANCE, "Vector and perpendicular dot product must be 0");

        // In-place setPerpendicular
        original.setPerpendicular();
        assertEquals(-4.0f, original.x, TOLERANCE);
        assertEquals(3.0f, original.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 3: Linear interpolation between vectors")
    void testLerp() {
        Vector2D from = new Vector2D(10.0f, 20.0f);
        Vector2D to = new Vector2D(30.0f, 60.0f);

        from.lerp(to, 0.5f);
        assertEquals(20.0f, from.x, TOLERANCE);
        assertEquals(40.0f, from.y, TOLERANCE);

        from.lerp(to, 1.0f);
        assertEquals(30.0f, from.x, TOLERANCE);
        assertEquals(60.0f, from.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 3: LibGDX interoperability")
    void testLibGdxInterop() {
        Vector2D v = new Vector2D(12.5f, -8.25f);
        Vector2 gdx = v.toGdx();
        assertEquals(12.5f, gdx.x, TOLERANCE);
        assertEquals(-8.25f, gdx.y, TOLERANCE);

        Vector2 reused = new Vector2();
        assertSame(reused, v.toGdx(reused));
        assertEquals(12.5f, reused.x, TOLERANCE);
        assertEquals(-8.25f, reused.y, TOLERANCE);

        Vector2D fromGdx = Vector2D.fromGdx(gdx);
        assertEquals(v.x, fromGdx.x, TOLERANCE);
        assertEquals(v.y, fromGdx.y, TOLERANCE);

        Vector2D setFromGdx = new Vector2D();
        setFromGdx.set(gdx);
        assertEquals(v.x, setFromGdx.x, TOLERANCE);
        assertEquals(v.y, setFromGdx.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 3: Angle between two vectors")
    void testAngleBetweenVectors() {
        Vector2D a = new Vector2D(1.0f, 0.0f);
        Vector2D b = new Vector2D(0.0f, 1.0f);
        assertEquals(90.0f, a.angleDeg(b), 1e-4f);
        assertEquals(MathUtils.HALF_PI, a.angleRad(b), 1e-4f);
    }

    @Test
    @DisplayName("Tier 3: fromAngle factory methods")
    void testFromAngle() {
        Vector2D east = Vector2D.fromAngle(0.0f);
        assertEquals(1.0f, east.x, 1e-4f);
        assertEquals(0.0f, east.y, 1e-4f);

        Vector2D north = Vector2D.fromAngle((float) (Math.PI * 0.5));
        assertEquals(0.0f, north.x, 1e-4f);
        assertEquals(1.0f, north.y, 1e-4f);

        Vector2D degNorth = Vector2D.fromAngleDeg(90.0f);
        assertEquals(0.0f, degNorth.x, 1e-4f);
        assertEquals(1.0f, degNorth.y, 1e-4f);
    }

    // =========================================================================
    // Tier 5: Adversarial & Serialization Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 5: Value equality, hashCode, and cloning")
    void testEqualsAndHashCode() {
        Vector2D a = new Vector2D(15.5f, 25.5f);
        Vector2D b = new Vector2D(15.5f, 25.5f);
        Vector2D c = new Vector2D(15.5f, 25.6f);

        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertNotEquals(a, c);
        assertNotEquals(a, null);
        assertNotEquals(a, "not a vector");

        Vector2D clone = a.cpy();
        assertEquals(a, clone);
        assertNotSame(a, clone);

        Vector2D javaClone = a.clone();
        assertEquals(a, javaClone);
        assertNotSame(a, javaClone);

        assertEquals("(15.5, 25.5)", a.toString());
    }

    @Test
    @DisplayName("Tier 5: Serialization and deserialization roundtrip")
    void testSerialization() throws Exception {
        Vector2D original = new Vector2D(123.456f, -789.012f);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(original);
        }

        Vector2D deserialized;
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            deserialized = (Vector2D) ois.readObject();
        }

        assertEquals(original, deserialized);
        assertEquals(original.x, deserialized.x, TOLERANCE);
        assertEquals(original.y, deserialized.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 5: Extreme float values")
    void testExtremeValues() {
        Vector2D large = new Vector2D(1e18f, 1e18f);
        assertFalse(large.isZero());

        Vector2D subnormal = new Vector2D(1e-38f, 1e-38f);
        // Normalizing subnormal should not crash or produce NaN
        subnormal.nor();
        assertFalse(Float.isNaN(subnormal.x));
        assertFalse(Float.isNaN(subnormal.y));
    }
}
