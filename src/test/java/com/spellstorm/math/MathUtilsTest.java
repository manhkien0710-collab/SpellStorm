package com.spellstorm.math;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for {@link MathUtils}.
 * Covers Tiers 1, 2, 3, and 5 per TEST_INFRA.md and M2 Explorer blueprint.
 */
class MathUtilsTest {

    private static final float TOLERANCE = 1e-5f;

    // =========================================================================
    // Tier 1: Equivalence Class Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Clamping utilities for float, int, and double")
    void testClamping() {
        // Float clamping
        assertEquals(5.0f, MathUtils.clamp(5.0f, 0.0f, 10.0f), TOLERANCE);
        assertEquals(0.0f, MathUtils.clamp(-5.0f, 0.0f, 10.0f), TOLERANCE);
        assertEquals(10.0f, MathUtils.clamp(15.0f, 0.0f, 10.0f), TOLERANCE);

        // Int clamping
        assertEquals(5, MathUtils.clamp(5, 0, 10));
        assertEquals(0, MathUtils.clamp(-5, 0, 10));
        assertEquals(10, MathUtils.clamp(15, 0, 10));

        // Double clamping
        assertEquals(5.0, MathUtils.clamp(5.0, 0.0, 10.0), 1e-9);
        assertEquals(0.0, MathUtils.clamp(-5.0, 0.0, 10.0), 1e-9);
        assertEquals(10.0, MathUtils.clamp(15.0, 0.0, 10.0), 1e-9);
    }

    @Test
    @DisplayName("Tier 1: Linear interpolation (lerp and lerpClamped)")
    void testLinearInterpolation() {
        // Basic lerp
        assertEquals(10.0f, MathUtils.lerp(10.0f, 20.0f, 0.0f), TOLERANCE);
        assertEquals(15.0f, MathUtils.lerp(10.0f, 20.0f, 0.5f), TOLERANCE);
        assertEquals(20.0f, MathUtils.lerp(10.0f, 20.0f, 1.0f), TOLERANCE);

        // Extrapolation with unclamped lerp
        assertEquals(25.0f, MathUtils.lerp(10.0f, 20.0f, 1.5f), TOLERANCE);
        assertEquals(5.0f, MathUtils.lerp(10.0f, 20.0f, -0.5f), TOLERANCE);

        // Clamped lerp
        assertEquals(20.0f, MathUtils.lerpClamped(10.0f, 20.0f, 1.5f), TOLERANCE);
        assertEquals(10.0f, MathUtils.lerpClamped(10.0f, 20.0f, -0.5f), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Degree and Radian conversions")
    void testDegRadConversions() {
        assertEquals(MathUtils.PI, MathUtils.degToRad(180.0f), 1e-5f);
        assertEquals(MathUtils.HALF_PI, MathUtils.degToRad(90.0f), 1e-5f);
        assertEquals(MathUtils.TWO_PI, MathUtils.degToRad(360.0f), 1e-5f);
        assertEquals(0.0f, MathUtils.degToRad(0.0f), 1e-5f);

        assertEquals(180.0f, MathUtils.radToDeg(MathUtils.PI), 1e-4f);
        assertEquals(90.0f, MathUtils.radToDeg(MathUtils.HALF_PI), 1e-4f);
        assertEquals(360.0f, MathUtils.radToDeg(MathUtils.TWO_PI), 1e-4f);

        // Roundtrip precision
        float angle = 45.123f;
        assertEquals(angle, MathUtils.radToDeg(MathUtils.degToRad(angle)), 1e-4f);
    }

    @Test
    @DisplayName("Tier 1: Angle normalization in [-PI, PI] and [0, 2*PI)")
    void testAngleNormalization() {
        assertEquals(0.0f, MathUtils.normalizeAngle(0.0f), TOLERANCE);
        assertEquals(MathUtils.PI, MathUtils.normalizeAngle(MathUtils.PI), TOLERANCE);
        assertEquals(-MathUtils.PI, MathUtils.normalizeAngle(-MathUtils.PI), TOLERANCE);

        // 3*PI should normalize to PI (or -PI)
        float norm3Pi = MathUtils.normalizeAngle(3.0f * MathUtils.PI);
        assertTrue(Math.abs(norm3Pi - MathUtils.PI) < 1e-4f || Math.abs(norm3Pi + MathUtils.PI) < 1e-4f);

        // 5*PI/2 (450 deg) should normalize to PI/2 (90 deg)
        assertEquals(MathUtils.HALF_PI, MathUtils.normalizeAngle(2.5f * MathUtils.PI), 1e-4f);

        // Negative wrap: -3*PI/2 should normalize to PI/2
        assertEquals(MathUtils.HALF_PI, MathUtils.normalizeAngle(-1.5f * MathUtils.PI), 1e-4f);

        // normalizeAnglePositive [0, 2*PI)
        assertEquals(0.0f, MathUtils.normalizeAnglePositive(0.0f), TOLERANCE);
        assertEquals(MathUtils.PI, MathUtils.normalizeAnglePositive(MathUtils.PI), TOLERANCE);
        assertEquals(MathUtils.HALF_PI, MathUtils.normalizeAnglePositive(2.5f * MathUtils.PI), 1e-4f);
        assertEquals(1.5f * MathUtils.PI, MathUtils.normalizeAnglePositive(-MathUtils.HALF_PI), 1e-4f);
    }

    @Test
    @DisplayName("Tier 1: Degree angle normalization")
    void testDegreeNormalization() {
        assertEquals(0.0f, MathUtils.normalizeAngleDeg(0.0f), TOLERANCE);
        assertEquals(180.0f, MathUtils.normalizeAngleDeg(180.0f), TOLERANCE);
        assertEquals(-180.0f, MathUtils.normalizeAngleDeg(-180.0f), TOLERANCE);
        assertEquals(90.0f, MathUtils.normalizeAngleDeg(450.0f), 1e-4f);
        assertEquals(-90.0f, MathUtils.normalizeAngleDeg(-450.0f), 1e-4f);

        assertEquals(0.0f, MathUtils.normalizeAngleDegPositive(0.0f), TOLERANCE);
        assertEquals(90.0f, MathUtils.normalizeAngleDegPositive(450.0f), 1e-4f);
        assertEquals(270.0f, MathUtils.normalizeAngleDegPositive(-90.0f), 1e-4f);
    }

    @Test
    @DisplayName("Tier 1: Circles overlap geometric query")
    void testCirclesOverlap() {
        // Intersecting
        assertTrue(MathUtils.circlesOverlap(0.0f, 0.0f, 10.0f, 15.0f, 0.0f, 10.0f));

        // Touching (tangent)
        assertTrue(MathUtils.circlesOverlap(0.0f, 0.0f, 10.0f, 20.0f, 0.0f, 10.0f));

        // Disjoint
        assertFalse(MathUtils.circlesOverlap(0.0f, 0.0f, 10.0f, 21.0f, 0.0f, 10.0f));

        // Concentric
        assertTrue(MathUtils.circlesOverlap(50.0f, 50.0f, 5.0f, 50.0f, 50.0f, 20.0f));

        // Vector2D overload
        Vector2D a = new Vector2D(0.0f, 0.0f);
        Vector2D b = new Vector2D(15.0f, 0.0f);
        assertTrue(MathUtils.circlesOverlap(a, 10.0f, b, 10.0f));

        // Negative radii protection
        assertFalse(MathUtils.circlesOverlap(0.0f, 0.0f, -5.0f, 0.0f, 0.0f, 10.0f));
    }

    // =========================================================================
    // Tier 2: Boundary Value Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 2: Hermite smoothstep curvature")
    void testSmoothstepCurvature() {
        assertEquals(0.0f, MathUtils.smoothstep(0.0f, 10.0f, 0.0f), TOLERANCE);
        assertEquals(1.0f, MathUtils.smoothstep(0.0f, 10.0f, 10.0f), TOLERANCE);
        assertEquals(0.5f, MathUtils.smoothstep(0.0f, 10.0f, 5.0f), TOLERANCE);

        // Outside bounds clamping
        assertEquals(0.0f, MathUtils.smoothstep(0.0f, 10.0f, -5.0f), TOLERANCE);
        assertEquals(1.0f, MathUtils.smoothstep(0.0f, 10.0f, 15.0f), TOLERANCE);

        // Single parameter smoothstep [0, 1]
        assertEquals(0.0f, MathUtils.smoothstep(0.0f), TOLERANCE);
        assertEquals(1.0f, MathUtils.smoothstep(1.0f), TOLERANCE);
        assertEquals(0.5f, MathUtils.smoothstep(0.5f), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Smoothstep with identical edges")
    void testSmoothstepIdenticalEdges() {
        assertEquals(1.0f, MathUtils.smoothstep(5.0f, 5.0f, 5.0f), TOLERANCE);
        assertEquals(1.0f, MathUtils.smoothstep(5.0f, 5.0f, 10.0f), TOLERANCE);
        assertEquals(0.0f, MathUtils.smoothstep(5.0f, 5.0f, 3.0f), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Shortest angle difference seam crossing")
    void testShortestAngleDifference() {
        // From 10 degrees to 350 degrees: difference should be -20 degrees, not +340!
        float fromRad = MathUtils.degToRad(10.0f);
        float toRad = MathUtils.degToRad(350.0f);
        float diffRad = MathUtils.shortestAngleDifference(fromRad, toRad);
        assertEquals(MathUtils.degToRad(-20.0f), diffRad, 1e-4f);

        // Degree version
        assertEquals(-20.0f, MathUtils.angleDifferenceDeg(10.0f, 350.0f), 1e-4f);
        assertEquals(20.0f, MathUtils.angleDifferenceDeg(350.0f, 10.0f), 1e-4f);

        // Same angle
        assertEquals(0.0f, MathUtils.angleDifferenceDeg(45.0f, 45.0f), 1e-4f);

        // Opposite angle (180 degrees)
        assertEquals(180.0f, Math.abs(MathUtils.angleDifferenceDeg(0.0f, 180.0f)), 1e-4f);
    }

    @Test
    @DisplayName("Tier 2: Frame-rate independent exponential decay")
    void testExpDecay() {
        float current = 100.0f;
        float target = 200.0f;
        float speed = 14.0f;

        // dt <= 0 returns current value immediately
        assertEquals(current, MathUtils.expDecay(current, target, speed, 0.0f), TOLERANCE);
        assertEquals(current, MathUtils.expDecay(current, target, speed, -0.016f), TOLERANCE);

        // dt > 0 moves towards target monotonically
        float next = MathUtils.expDecay(current, target, speed, 0.016f);
        assertTrue(next > current, "Value should increase towards target");
        assertTrue(next < target, "Value should not overshoot target");

        // Over 1.0 second with speed 14.0, exp(-14) is approx 8.3e-7, almost exact convergence
        float converged = MathUtils.expDecay(current, target, speed, 1.0f);
        assertEquals(target, converged, 1e-3f);
    }

    @Test
    @DisplayName("Tier 2: Distance and angle queries")
    void testDistanceAndAngleQueries() {
        assertEquals(5.0f, MathUtils.distance(0.0f, 0.0f, 3.0f, 4.0f), TOLERANCE);
        assertEquals(25.0f, MathUtils.distanceSq(0.0f, 0.0f, 3.0f, 4.0f), TOLERANCE);

        assertEquals(0.0f, MathUtils.angleOf(1.0f, 0.0f), TOLERANCE);
        assertEquals(MathUtils.HALF_PI, MathUtils.angleOf(0.0f, 1.0f), TOLERANCE);

        assertEquals(0.0f, MathUtils.angleBetween(0.0f, 0.0f, 10.0f, 0.0f), TOLERANCE);
        assertEquals(MathUtils.HALF_PI, MathUtils.angleBetween(0.0f, 0.0f, 0.0f, 10.0f), TOLERANCE);
    }

    // =========================================================================
    // Tier 3: Combinatorial & Seam Crossing Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 3: Angle interpolation (lerpAngle) crossing 0/360 boundary")
    void testLerpAngle() {
        // Interpolating from 350 degrees to 10 degrees at alpha 0.5 should yield 0 (or 360) degrees,
        // traversing the short 20-degree arc rather than the 340-degree long arc!
        float fromDeg = 350.0f;
        float toDeg = 10.0f;
        float midDeg = MathUtils.lerpAngleDeg(fromDeg, toDeg, 0.5f);
        assertEquals(0.0f, MathUtils.normalizeAngleDegPositive(midDeg), 1e-4f);

        // Radian version
        float fromRad = MathUtils.degToRad(350.0f);
        float toRad = MathUtils.degToRad(10.0f);
        float midRad = MathUtils.lerpAngle(fromRad, toRad, 0.5f);
        assertEquals(0.0f, MathUtils.normalizeAngle(midRad), 1e-4f);
    }

    @Test
    @DisplayName("Tier 3: Floating point comparison helpers")
    void testFloatComparisons() {
        assertTrue(MathUtils.isZero(0.0f));
        assertTrue(MathUtils.isZero(1e-6f));
        assertFalse(MathUtils.isZero(0.01f));
        assertTrue(MathUtils.isZero(0.01f, 0.02f));

        assertTrue(MathUtils.isEqual(5.0f, 5.0f));
        assertTrue(MathUtils.isEqual(5.0f, 5.000001f));
        assertFalse(MathUtils.isEqual(5.0f, 5.01f));
        assertTrue(MathUtils.isEqual(5.0f, 5.01f, 0.05f));
    }

    // =========================================================================
    // Tier 5: Adversarial & Extreme Values
    // =========================================================================

    @Test
    @DisplayName("Tier 5: Extreme angle normalization with large multiples")
    void testExtremeAngleNormalization() {
        // 1000 * PI should normalize to 0.0 (even multiple of PI)
        float normalized = MathUtils.normalizeAngle(1000.0f * MathUtils.PI);
        assertEquals(0.0f, normalized, 1e-3f);

        // 1001 * PI should normalize to PI (or -PI)
        float normalizedOdd = MathUtils.normalizeAngle(1001.0f * MathUtils.PI);
        assertTrue(Math.abs(normalizedOdd - MathUtils.PI) < 1e-3f || Math.abs(normalizedOdd + MathUtils.PI) < 1e-3f);

        // Large negative angle
        float negNormalized = MathUtils.normalizeAngle(-1000.0f * MathUtils.PI);
        assertEquals(0.0f, negNormalized, 1e-3f);
    }
}
