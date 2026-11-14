package com.spellstorm;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical challenger test harness for Milestone 2:
 * Math, Camera & Coordinate Transformations.
 *
 * Authored by m2_challenger_1 to verify edge cases, numerical stability,
 * boundary clamping, and coordinate transformations.
 */
class Milestone2ChallengerTest {

    private static final float TOLERANCE = 1e-4f;
    private static final float PI = MathUtils.PI;
    private static final float TWO_PI = MathUtils.TWO_PI;

    @BeforeAll
    static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. Vector2D Normalization & Division Edge Cases
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 1.1: Vector2D.nor() on (0, 0), sub-epsilon vectors, and extremely large vectors")
    void testVectorNormalizationStress() {
        // (0, 0)
        Vector2D zero = new Vector2D(0.0f, 0.0f);
        zero.nor();
        assertFalse(Float.isNaN(zero.x), "Zero vector nor().x must not be NaN");
        assertFalse(Float.isNaN(zero.y), "Zero vector nor().y must not be NaN");
        assertEquals(0.0f, zero.x, TOLERANCE);
        assertEquals(0.0f, zero.y, TOLERANCE);

        // Sub-epsilon vectors (magnitude < 1e-5f)
        float[][] subEpsilons = {
            {1e-6f, 1e-6f},
            {-1e-6f, 1e-6f},
            {1e-6f, -1e-6f},
            {-1e-6f, -1e-6f},
            {1e-7f, 0.0f},
            {0.0f, 1e-7f},
            {-5e-6f, 0.0f},
            {0.0f, -5e-6f},
            {1e-15f, 1e-15f},
            {1e-30f, 1e-30f}
        };

        for (float[] pt : subEpsilons) {
            Vector2D sub = new Vector2D(pt[0], pt[1]);
            sub.nor();
            assertFalse(Float.isNaN(sub.x), "Sub-epsilon vector nor().x must not be NaN for (" + pt[0] + ", " + pt[1] + ")");
            assertFalse(Float.isNaN(sub.y), "Sub-epsilon vector nor().y must not be NaN for (" + pt[0] + ", " + pt[1] + ")");
            assertEquals(0.0f, sub.x, TOLERANCE);
            assertEquals(0.0f, sub.y, TOLERANCE);
        }

        // Extremely large vectors: (1e15, 1e15)
        Vector2D large15 = new Vector2D(1e15f, 1e15f);
        large15.nor();
        assertFalse(Float.isNaN(large15.x), "1e15 vector nor().x must not be NaN");
        assertFalse(Float.isNaN(large15.y), "1e15 vector nor().y must not be NaN");
        assertFalse(Float.isInfinite(large15.x), "1e15 vector nor().x must not be infinite");
        assertFalse(Float.isInfinite(large15.y), "1e15 vector nor().y must not be infinite");
        float expectedNorm = (float) (1.0 / Math.sqrt(2.0));
        assertEquals(expectedNorm, large15.x, 1e-3f, "1e15 vector normalized x must be ~1/sqrt(2)");
        assertEquals(expectedNorm, large15.y, 1e-3f, "1e15 vector normalized y must be ~1/sqrt(2)");
        assertEquals(1.0f, large15.len(), 1e-3f, "1e15 normalized length must be 1.0f");

        // Extremely large vectors along axes: (1e18, 0) and (0, -1e18)
        Vector2D largeX = new Vector2D(1e18f, 0.0f);
        largeX.nor();
        assertEquals(1.0f, largeX.x, TOLERANCE);
        assertEquals(0.0f, largeX.y, TOLERANCE);

        Vector2D largeY = new Vector2D(0.0f, -1e18f);
        largeY.nor();
        assertEquals(0.0f, largeY.x, TOLERANCE);
        assertEquals(-1.0f, largeY.y, TOLERANCE);
    }

    @Test
    @DisplayName("CHALLENGE 1.2: Vector2D division by zero and sub-epsilon divisors")
    void testVectorDivisionByZeroSafety() {
        Vector2D v1 = new Vector2D(100.0f, -250.0f);
        v1.div(0.0f);
        assertFalse(Float.isNaN(v1.x));
        assertFalse(Float.isNaN(v1.y));
        assertFalse(Float.isInfinite(v1.x));
        assertFalse(Float.isInfinite(v1.y));
        assertEquals(0.0f, v1.x, TOLERANCE);
        assertEquals(0.0f, v1.y, TOLERANCE);

        // Sub-epsilon scalar divisors (|s| <= 1e-5f)
        float[] subScalars = {1e-6f, -1e-6f, 1e-10f, -1e-15f, 0.0f, -0.0f};
        for (float s : subScalars) {
            Vector2D vs = new Vector2D(50.0f, 75.0f);
            vs.div(s);
            assertFalse(Float.isNaN(vs.x));
            assertFalse(Float.isNaN(vs.y));
            assertEquals(0.0f, vs.x, TOLERANCE);
            assertEquals(0.0f, vs.y, TOLERANCE);
        }

        // Component-wise division by zero
        Vector2D vCompX = new Vector2D(10.0f, 20.0f);
        vCompX.div(0.0f, 4.0f);
        assertEquals(0.0f, vCompX.x, TOLERANCE);
        assertEquals(5.0f, vCompX.y, TOLERANCE);

        Vector2D vCompY = new Vector2D(10.0f, 20.0f);
        vCompY.div(2.0f, 0.0f);
        assertEquals(5.0f, vCompY.x, TOLERANCE);
        assertEquals(0.0f, vCompY.y, TOLERANCE);

        // Div by null Vector2D
        Vector2D vNull = new Vector2D(10.0f, 20.0f);
        vNull.div((Vector2D) null);
        assertEquals(0.0f, vNull.x, TOLERANCE);
        assertEquals(0.0f, vNull.y, TOLERANCE);

        // Div by zero Vector2D
        Vector2D vOtherZero = new Vector2D(10.0f, 20.0f);
        vOtherZero.div(new Vector2D(0.0f, 0.0f));
        assertEquals(0.0f, vOtherZero.x, TOLERANCE);
        assertEquals(0.0f, vOtherZero.y, TOLERANCE);
    }

    // =========================================================================
    // 2. MathUtils Angle Normalization Across Multiple Revolutions
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 1.3: Angle normalization across multiple revolutions (-10*PI, +15*PI, etc.)")
    void testAngleNormalizationMultipleRevolutions() {
        // -10 * PI: exactly 5 revolutions in negative direction -> must be 0
        float normNeg10Pi = MathUtils.normalizeAngle(-10.0f * PI);
        assertEquals(0.0f, normNeg10Pi, 1e-4f, "normalizeAngle(-10*PI) must be 0.0");

        // +15 * PI: 7 full revolutions (14*PI) + PI -> must be PI or -PI
        float normPos15Pi = MathUtils.normalizeAngle(15.0f * PI);
        assertTrue(Math.abs(normPos15Pi - PI) < 1e-4f || Math.abs(normPos15Pi + PI) < 1e-4f,
                "normalizeAngle(+15*PI) must normalize to +-PI but was " + normPos15Pi);

        // normalizeAnglePositive:
        // -10 * PI -> On circle, equivalent to 0.0 or TWO_PI (within float epsilon of 2*PI boundary)
        float posNeg10Pi = MathUtils.normalizeAnglePositive(-10.0f * PI);
        assertTrue(Math.abs(posNeg10Pi) < 1e-4f || Math.abs(posNeg10Pi - TWO_PI) < 1e-4f,
                "normalizeAnglePositive(-10*PI) must be congruent to 0 on circle but was " + posNeg10Pi);

        // +15 * PI -> PI
        float pos15Pi = MathUtils.normalizeAnglePositive(15.0f * PI);
        assertEquals(PI, pos15Pi, 1e-4f, "normalizeAnglePositive(+15*PI) must be PI");

        // Specific fractional revolutions:
        // -10.5 * PI = -10*PI - 0.5*PI -> -0.5*PI (-PI/2)
        float normNeg10_5Pi = MathUtils.normalizeAngle(-10.5f * PI);
        assertEquals(-0.5f * PI, normNeg10_5Pi, 1e-4f);

        // +15.5 * PI = +14*PI + 1.5*PI = -0.5*PI (-PI/2)
        float normPos15_5Pi = MathUtils.normalizeAngle(15.5f * PI);
        assertEquals(-0.5f * PI, normPos15_5Pi, 1e-4f);

        // Comprehensive revolution sweep: [-100*PI, +100*PI] in steps of 0.25*PI
        for (int k = -400; k <= 400; k++) {
            float testAngle = k * 0.25f * PI;
            float norm = MathUtils.normalizeAngle(testAngle);
            assertTrue(norm >= -PI - 1e-4f && norm <= PI + 1e-4f,
                    "Normalized angle " + norm + " for input " + testAngle + " out of [-PI, PI]");

            float normPos = MathUtils.normalizeAnglePositive(testAngle);
            assertTrue(normPos >= -1e-4f && normPos < TWO_PI + 1e-4f,
                    "Normalized positive angle " + normPos + " for input " + testAngle + " out of [0, 2*PI)");

            // Trigonometric invariance check
            float expectedCos = (float) Math.cos(testAngle);
            float actualCos = (float) Math.cos(norm);
            assertEquals(expectedCos, actualCos, 1e-3f, "cos(testAngle) must match cos(normalized)");

            float expectedSin = (float) Math.sin(testAngle);
            float actualSin = (float) Math.sin(norm);
            assertEquals(expectedSin, actualSin, 1e-3f, "sin(testAngle) must match sin(normalized)");
        }
    }

    // =========================================================================
    // 3. GameCamera Zoom Boundaries & Clamping
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 2.1: GameCamera zoom boundaries strictly clamped to [0.75, 2.25]")
    void testZoomBoundariesStrictClamping() {
        GameCamera camera = new GameCamera();

        // Attempts to set zoom below 0.75
        float[] underZooms = {-100.0f, -1.0f, 0.0f, 0.1f, 0.5f, 0.7499f};
        for (float z : underZooms) {
            camera.setZoom(z);
            assertEquals(GameCamera.MIN_ZOOM, camera.getZoom(), 1e-5f, "setZoom(" + z + ") must clamp to MIN_ZOOM (0.75)");
            assertEquals(GameCamera.MIN_ZOOM, camera.getTargetZoom(), 1e-5f);

            camera.setTargetZoom(z);
            assertEquals(GameCamera.MIN_ZOOM, camera.getTargetZoom(), 1e-5f, "setTargetZoom(" + z + ") must clamp to MIN_ZOOM (0.75)");
        }

        // Attempts to set zoom above 2.25
        float[] overZooms = {2.2501f, 2.3f, 3.0f, 5.0f, 50.0f, 1000.0f};
        for (float z : overZooms) {
            camera.setZoom(z);
            assertEquals(GameCamera.MAX_ZOOM, camera.getZoom(), 1e-5f, "setZoom(" + z + ") must clamp to MAX_ZOOM (2.25)");
            assertEquals(GameCamera.MAX_ZOOM, camera.getTargetZoom(), 1e-5f);

            camera.setTargetZoom(z);
            assertEquals(GameCamera.MAX_ZOOM, camera.getTargetZoom(), 1e-5f, "setTargetZoom(" + z + ") must clamp to MAX_ZOOM (2.25)");
        }

        // Relative zoom adjustments: adjustZoom / zoomBy
        camera.setZoom(1.0f);
        camera.adjustZoom(-10.0f);
        assertEquals(GameCamera.MIN_ZOOM, camera.getTargetZoom(), 1e-5f);

        camera.setZoom(1.0f);
        camera.adjustZoom(10.0f);
        assertEquals(GameCamera.MAX_ZOOM, camera.getTargetZoom(), 1e-5f);

        // Boundary values exactly
        camera.setZoom(GameCamera.MIN_ZOOM);
        assertEquals(0.75f, camera.getZoom(), 1e-5f);

        camera.setZoom(GameCamera.MAX_ZOOM);
        assertEquals(2.25f, camera.getZoom(), 1e-5f);
    }

    // =========================================================================
    // 4. GameCamera Frustum Clamping at World Corners & Beyond
    // =========================================================================

    @ParameterizedTest
    @ValueSource(floats = {0.75f, 1.0f, 1.35f, 2.25f})
    @DisplayName("CHALLENGE 2.2: Frustum clamping at world corners and outside boundaries across zoom levels {0.75, 1.0, 1.35, 2.25}")
    void testFrustumClampingCornersAndBeyond(float zoomLevel) {
        GameCamera camera = new GameCamera();
        camera.setZoom(zoomLevel);

        float halfW = camera.getViewWidth() * 0.5f;
        float halfH = camera.getViewHeight() * 0.5f;

        // Test points: corners and points far outside world bounds [0, 4096] x [0, 4096]
        float[][] testPoints = {
            // Exact corners
            {0.0f, 0.0f},
            {4096.0f, 4096.0f},
            {0.0f, 4096.0f},
            {4096.0f, 0.0f},
            // Far outside corners
            {-1000.0f, -1000.0f},
            {5000.0f, 5000.0f},
            {-500.0f, 4500.0f},
            {4500.0f, -500.0f},
            // Far outside edges
            {-2000.0f, 2048.0f},
            {6000.0f, 2048.0f},
            {2048.0f, -2000.0f},
            {2048.0f, 6000.0f}
        };

        for (float[] pt : testPoints) {
            float desiredX = pt[0];
            float desiredY = pt[1];

            // 1. Instant teleportation via snapTo / setCenter
            camera.snapTo(desiredX, desiredY);
            verifyFrustumBounds(camera, zoomLevel, desiredX, desiredY);

            // 2. Continuous update with positive dt
            camera.update(desiredX, desiredY, 0.016f);
            verifyFrustumBounds(camera, zoomLevel, desiredX, desiredY);

            // 3. Update with dt = 1.0s to ensure full convergence
            camera.update(desiredX, desiredY, 1.0f);
            verifyFrustumBounds(camera, zoomLevel, desiredX, desiredY);
        }
    }

    private void verifyFrustumBounds(GameCamera camera, float zoom, float desiredX, float desiredY) {
        Rectangle bounds = camera.getVisibleBounds();
        float viewW = camera.getViewWidth();
        float viewH = camera.getViewHeight();

        // 1. Center must be within [halfW, 4096 - halfW] x [halfH, 4096 - halfH]
        float halfW = viewW * 0.5f;
        float halfH = viewH * 0.5f;

        assertTrue(camera.getCenterX() >= halfW - 1e-3f,
                "Camera centerX (" + camera.getCenterX() + ") < halfW (" + halfW + ") for desired (" + desiredX + ", " + desiredY + ") at zoom " + zoom);
        assertTrue(camera.getCenterX() <= 4096.0f - halfW + 1e-3f,
                "Camera centerX (" + camera.getCenterX() + ") > (4096 - halfW) (" + (4096.0f - halfW) + ") for desired (" + desiredX + ", " + desiredY + ") at zoom " + zoom);

        assertTrue(camera.getCenterY() >= halfH - 1e-3f,
                "Camera centerY (" + camera.getCenterY() + ") < halfH (" + halfH + ") for desired (" + desiredX + ", " + desiredY + ") at zoom " + zoom);
        assertTrue(camera.getCenterY() <= 4096.0f - halfH + 1e-3f,
                "Camera centerY (" + camera.getCenterY() + ") > (4096 - halfH) (" + (4096.0f - halfH) + ") for desired (" + desiredX + ", " + desiredY + ") at zoom " + zoom);

        // 2. Visible frustum must NEVER leak outside [0, 0, 4096, 4096]
        assertTrue(bounds.x >= -1e-3f, "Visible left (" + bounds.x + ") < 0.0 for desired (" + desiredX + ", " + desiredY + ")");
        assertTrue(bounds.y >= -1e-3f, "Visible top (" + bounds.y + ") < 0.0 for desired (" + desiredX + ", " + desiredY + ")");
        assertTrue(bounds.x + bounds.width <= 4096.0f + 1e-3f,
                "Visible right (" + (bounds.x + bounds.width) + ") > 4096.0 for desired (" + desiredX + ", " + desiredY + ")");
        assertTrue(bounds.y + bounds.height <= 4096.0f + 1e-3f,
                "Visible bottom (" + (bounds.y + bounds.height) + ") > 4096.0 for desired (" + desiredX + ", " + desiredY + ")");

        // 3. getVisibleLeft / getVisibleTop helpers must match bounds
        assertEquals(bounds.x, camera.getVisibleLeft(), 1e-3f);
        assertEquals(bounds.y, camera.getVisibleTop(), 1e-3f);
    }

    // =========================================================================
    // 5. GameCamera Coordinate Unprojection Round-Trip Invariance
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 2.3: Coordinate unprojection round-trip worldToScreen(screenToWorld(p)) == p across zoom levels & viewport offsets")
    void testCoordinateUnprojectionRoundTripStress() {
        float[] zoomLevels = {0.75f, 1.0f, 1.35f, 1.8f, 2.0f, 2.25f};
        float[][] offsets = {
            {0.0f, 0.0f},
            {120.0f, 60.0f},
            {-80.0f, -40.0f},
            {640.0f, 360.0f}
        };

        Vector2D temp = new Vector2D();

        for (float zoom : zoomLevels) {
            for (float[] offset : offsets) {
                GameCamera camera = new GameCamera();
                camera.setZoom(zoom);
                camera.setViewportOffset(offset[0], offset[1]);
                camera.setCenter(2048.0f, 2048.0f);

                // Sweep screen coordinates (both inside and outside standard 1280x720 window)
                for (float sx = -300.0f; sx <= 1600.0f; sx += 95.5f) {
                    for (float sy = -200.0f; sy <= 1000.0f; sy += 65.25f) {
                        // Forward round-trip: Screen -> World -> Screen
                        Vector2D world = camera.screenToWorld(sx, sy);
                        Vector2D screen = camera.worldToScreen(world.x, world.y, temp);

                        assertEquals(sx, screen.x, 0.005f,
                                "Screen X round-trip mismatch at zoom=" + zoom + ", offset=(" + offset[0] + ", " + offset[1] + ")");
                        assertEquals(sy, screen.y, 0.005f,
                                "Screen Y round-trip mismatch at zoom=" + zoom + ", offset=(" + offset[0] + ", " + offset[1] + ")");
                    }
                }

                // Reverse round-trip: World -> Screen -> World
                for (float wx = -500.0f; wx <= 4600.0f; wx += 250.0f) {
                    for (float wy = -500.0f; wy <= 4600.0f; wy += 250.0f) {
                        Vector2D screen = camera.worldToScreen(wx, wy);
                        Vector2D world = camera.screenToWorld(screen.x, screen.y, temp);

                        assertEquals(wx, world.x, 0.005f,
                                "World X reverse round-trip mismatch at zoom=" + zoom + ", offset=(" + offset[0] + ", " + offset[1] + ")");
                        assertEquals(wy, world.y, 0.005f,
                                "World Y reverse round-trip mismatch at zoom=" + zoom + ", offset=(" + offset[0] + ", " + offset[1] + ")");
                    }
                }
            }
        }
    }

    // =========================================================================
    // 6. Camera Position Tracking Dynamics & Smooth Zoom Under Adversarial dt
    // =========================================================================

    @Test
    @DisplayName("CHALLENGE 2.4: Camera update with adversarial dt (negative, zero, large dt) and smooth tracking")
    void testCameraTrackingAdversarialDt() {
        GameCamera camera = new GameCamera();
        camera.snapTo(2048.0f, 2048.0f);
        camera.setTargetZoom(2.0f);

        // Negative dt: zoom and position must snap or remain stable, never NaN
        camera.update(3000.0f, 3000.0f, -0.016f);
        assertFalse(Float.isNaN(camera.getCenterX()));
        assertFalse(Float.isNaN(camera.getCenterY()));
        assertFalse(Float.isNaN(camera.getZoom()));
        assertEquals(2.0f, camera.getZoom(), TOLERANCE);

        // Zero dt: snaps target zoom, maintains valid position
        camera.setTargetZoom(1.0f);
        camera.update(3000.0f, 3000.0f, 0.0f);
        assertEquals(1.0f, camera.getZoom(), TOLERANCE);
        assertFalse(Float.isNaN(camera.getCenterX()));

        // Enormous dt (1000.0s): must converge fully without overflow or NaN
        camera.setTargetZoom(2.25f);
        // Inside boundary range: target 3500 must converge to 3500
        camera.update(3500.0f, 3500.0f, 1000.0f);
        assertEquals(2.25f, camera.getZoom(), TOLERANCE);
        assertEquals(3500.0f, camera.getCenterX(), 1e-3f);
        assertEquals(3500.0f, camera.getCenterY(), 1e-3f);

        // Outside boundary: target 5000 must strictly clamp to maxCenter
        camera.update(5000.0f, 5000.0f, 1000.0f);
        float maxCenterX = 4096.0f - (camera.getViewWidth() * 0.5f);
        float maxCenterY = 4096.0f - (camera.getViewHeight() * 0.5f);
        assertEquals(maxCenterX, camera.getCenterX(), 1e-3f);
        assertEquals(maxCenterY, camera.getCenterY(), 1e-3f);
    }
}
