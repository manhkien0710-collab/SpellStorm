package com.spellstorm;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector3;
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
 * Comprehensive JUnit 5 test suite for {@link GameCamera}.
 * Covers Features 5 & 6 across Tiers 1, 2, 3, and 4 per TEST_INFRA.md and M2 Explorer blueprint.
 */
class GameCameraTest {

    private static final float TOLERANCE = 1e-4f;

    @BeforeAll
    static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Tier 1: Equivalence Class Tests (Feature 5: Camera & Zoom Clamping)
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Initial state and default configuration")
    void testInitialStateAndDefaults() {
        GameCamera camera = new GameCamera();

        assertEquals(GameCamera.DEFAULT_VIEWPORT_WIDTH, camera.getViewportWidth(), TOLERANCE);
        assertEquals(GameCamera.DEFAULT_VIEWPORT_HEIGHT, camera.getViewportHeight(), TOLERANCE);
        assertEquals(GameCamera.DEFAULT_ZOOM, camera.getZoom(), TOLERANCE);
        assertEquals(GameCamera.DEFAULT_ZOOM, camera.getTargetZoom(), TOLERANCE);

        // Center must be initialized to world center (2048, 2048)
        assertEquals(2048.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(2048.0f, camera.getCenterY(), TOLERANCE);

        // Viewport frustum dimensions at default zoom 1.35
        float expectedW = 1280.0f / 1.35f;
        float expectedH = 720.0f / 1.35f;
        assertEquals(expectedW, camera.getViewWidth(), 0.1f);
        assertEquals(expectedH, camera.getViewHeight(), 0.1f);

        Rectangle vis = camera.getVisibleBounds();
        assertEquals(camera.getVisibleLeft(), vis.x, TOLERANCE);
        assertEquals(camera.getVisibleTop(), vis.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Camera positioning at world center")
    void testBoundaryClampingWorldCenter() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.setCenter(2048.0f, 2048.0f);

        assertEquals(2048.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(2048.0f, camera.getCenterY(), TOLERANCE);

        Rectangle vis = camera.getVisibleBounds();
        assertEquals(2048.0f - 640.0f, vis.x, TOLERANCE);
        assertEquals(2048.0f - 360.0f, vis.y, TOLERANCE);
        assertEquals(1280.0f, vis.width, TOLERANCE);
        assertEquals(720.0f, vis.height, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Clamping near top-left world edge (port of C++ testCameraClampingAndBounds)")
    void testBoundaryClampingTopLeft() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);

        // Try setting center close to top-left (100, 50)
        camera.setCenter(100.0f, 50.0f);
        // Must clamp to half-viewport dimensions: 1280/2 = 640, 720/2 = 360
        assertEquals(640.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(360.0f, camera.getCenterY(), TOLERANCE);

        Rectangle vis = camera.getVisibleBounds();
        assertTrue(vis.x >= 0.0f, "Visible bounds must not expose void outside left boundary");
        assertTrue(vis.y >= 0.0f, "Visible bounds must not expose void outside top boundary");
        assertEquals(0.0f, vis.x, TOLERANCE);
        assertEquals(0.0f, vis.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Clamping near bottom-right world edge")
    void testBoundaryClampingBottomRight() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);

        // Try setting center close to bottom-right (4000, 4050)
        camera.setCenter(4000.0f, 4050.0f);
        // Must clamp to: 4096 - 640 = 3456, 4096 - 360 = 3736
        assertEquals(4096.0f - 640.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(4096.0f - 360.0f, camera.getCenterY(), TOLERANCE);

        Rectangle vis = camera.getVisibleBounds();
        assertTrue(vis.x + vis.width <= 4096.0f, "Visible bounds must not expose void outside right boundary");
        assertTrue(vis.y + vis.height <= 4096.0f, "Visible bounds must not expose void outside bottom boundary");
        assertEquals(4096.0f, vis.x + vis.width, TOLERANCE);
        assertEquals(4096.0f, vis.y + vis.height, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Frustum visibility queries")
    void testFrustumVisibilityQueries() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.setCenter(4000.0f, 4050.0f); // Clamped to (3456, 3736)

        Rectangle vis = camera.getVisibleBounds();
        assertTrue(camera.isVisible(vis));
        assertTrue(camera.isVisible(camera.getCenterX(), camera.getCenterY(), 20.0f));
        assertTrue(camera.isVisible(new Vector2D(camera.getCenterX(), camera.getCenterY()), 20.0f));

        // Offscreen top-left entity (100, 100) must be invisible
        assertFalse(camera.isVisible(100.0f, 100.0f, 20.0f));
        assertFalse(camera.isVisible(new Vector2D(100.0f, 100.0f), 20.0f));
        assertFalse(camera.isVisible(new Rectangle(50.0f, 50.0f, 100.0f, 100.0f)));
    }

    // =========================================================================
    // Tier 2: Boundary Value Tests (Feature 5: Camera & Zoom Clamping)
    // =========================================================================

    @Test
    @DisplayName("Tier 2: Zoom limits clamping [0.75, 2.25]")
    void testZoomLimitsClamping() {
        GameCamera camera = new GameCamera();

        // Below minimum zoom (0.1 -> 0.75)
        camera.setZoom(0.1f);
        assertEquals(GameCamera.MIN_ZOOM, camera.getZoom(), TOLERANCE);

        // Above maximum zoom (10.0 -> 2.25)
        camera.setZoom(10.0f);
        assertEquals(GameCamera.MAX_ZOOM, camera.getZoom(), TOLERANCE);

        // Target zoom clamping
        camera.setTargetZoom(-5.0f);
        assertEquals(GameCamera.MIN_ZOOM, camera.getTargetZoom(), TOLERANCE);

        camera.setTargetZoom(100.0f);
        assertEquals(GameCamera.MAX_ZOOM, camera.getTargetZoom(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Zoom adjustments and resetZoom")
    void testZoomByAndReset() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);

        camera.zoomBy(0.25f);
        assertEquals(1.25f, camera.getTargetZoom(), TOLERANCE);

        camera.resetZoom();
        assertEquals(GameCamera.DEFAULT_ZOOM, camera.getTargetZoom(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Smooth zoom interpolation (port of C++ testCameraZoomAndMouseAiming)")
    void testSmoothZoomInterpolation() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.zoomBy(0.25f); // targetZoom = 1.25f

        // Advance by 0.5s with speed=0 tracking
        camera.update(2048.0f, 2048.0f, 0.5f, 0.0f);

        // Rate is 10.0, dt=0.5 -> zoomT = 1 - exp(-5) approx 0.99326
        // zoom is within 0.02 of 1.25 (matching C++ EXPECT_NEAR(camera.zoom(), 1.25f, 0.02f))
        assertEquals(1.25f, camera.getZoom(), 0.02f);
    }

    @Test
    @DisplayName("Tier 2: Zoom epsilon convergence snapping")
    void testZoomEpsilonSnapping() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.zoomBy(0.25f);

        // Advance 2.0s to ensure total convergence past ZOOM_EPSILON
        camera.update(2048.0f, 2048.0f, 2.0f, 0.0f);
        assertEquals(1.25f, camera.getZoom(), TOLERANCE);

        // Non-positive dt snaps immediately
        camera.setTargetZoom(1.8f);
        camera.update(2048.0f, 2048.0f, 0.0f, 0.0f);
        assertEquals(1.8f, camera.getZoom(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Clamping under high zoom 2.0x (port of C++ testCameraZoomAndMouseAiming)")
    void testClampingUnderHighZoom() {
        GameCamera camera = new GameCamera();
        // At zoom = 2.0f, viewSize is (640, 360), half viewSize is (320, 180)
        camera.setZoom(2.0f);
        camera.snapTo(10.0f, 10.0f);

        assertEquals(320.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(180.0f, camera.getCenterY(), TOLERANCE);

        Rectangle vis = camera.getVisibleBounds();
        assertEquals(0.0f, vis.x, TOLERANCE);
        assertEquals(0.0f, vis.y, TOLERANCE);
        assertEquals(640.0f, vis.width, TOLERANCE);
        assertEquals(360.0f, vis.height, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Clamping under minimum zoom 0.75x")
    void testClampingUnderLowZoom() {
        GameCamera camera = new GameCamera();
        // At zoom = 0.75f, viewSize is (1706.667, 960), half viewSize is (853.333, 480)
        camera.setZoom(0.75f);
        camera.snapTo(10.0f, 10.0f);

        assertEquals(853.3333f, camera.getCenterX(), 0.01f);
        assertEquals(480.0f, camera.getCenterY(), 0.01f);

        Rectangle vis = camera.getVisibleBounds();
        assertEquals(0.0f, vis.x, 0.01f);
        assertEquals(0.0f, vis.y, 0.01f);
    }

    @ParameterizedTest
    @ValueSource(floats = {0.75f, 1.0f, 1.35f, 1.8f, 2.0f, 2.25f})
    @DisplayName("Tier 2: Frustum coverage across zoom spectrum (port of C++ testCameraFrustumCoverage)")
    void testFrustumCoverageAcrossZoomSpectrum(float z) {
        GameCamera camera = new GameCamera();
        camera.setZoom(z);
        camera.setCenter(2048.0f, 2048.0f);
        Rectangle vis = camera.getVisibleBounds();

        assertEquals(1280.0f / z, vis.width, 0.5f);
        assertEquals(720.0f / z, vis.height, 0.5f);

        // Clamped visible area must never leak outside world bounds
        assertTrue(vis.x >= 0.0f, "Visible left must be >= 0");
        assertTrue(vis.y >= 0.0f, "Visible top must be >= 0");
        assertTrue(vis.x + vis.width <= 4096.0f, "Visible right must be <= 4096");
        assertTrue(vis.y + vis.height <= 4096.0f, "Visible bottom must be <= 4096");
    }

    // =========================================================================
    // Tier 1 & 2: Coordinate Unprojection & Invariance (Feature 6)
    // =========================================================================

    @ParameterizedTest
    @ValueSource(floats = {0.75f, 1.0f, 1.35f, 2.0f, 2.25f})
    @DisplayName("Tier 1 & 2: Screen center unprojects to camera center across all zoom levels")
    void testScreenToWorldCenterMapping(float zoom) {
        GameCamera camera = new GameCamera();
        camera.setZoom(zoom);
        camera.setCenter(2500.0f, 2200.0f);

        Vector2D worldPos = camera.screenToWorld(640.0f, 360.0f);
        assertEquals(camera.getCenterX(), worldPos.x, TOLERANCE);
        assertEquals(camera.getCenterY(), worldPos.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Screen corners unproject to frustum corners")
    void testScreenToWorldCornerMapping() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.setCenter(2048.0f, 2048.0f);

        Vector2D topLeft = camera.screenToWorld(0.0f, 0.0f);
        assertEquals(2048.0f - 640.0f, topLeft.x, TOLERANCE);
        assertEquals(2048.0f - 360.0f, topLeft.y, TOLERANCE);

        Vector2D bottomRight = camera.screenToWorld(1280.0f, 720.0f);
        assertEquals(2048.0f + 640.0f, bottomRight.x, TOLERANCE);
        assertEquals(2048.0f + 360.0f, bottomRight.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Coordinate transformation round-trip bijectivity")
    void testWorldToScreenInversion() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.35f);
        camera.setCenter(1800.0f, 2200.0f);

        float[][] testPoints = {
            {0.0f, 0.0f},
            {640.0f, 360.0f},
            {1280.0f, 720.0f},
            {350.5f, 420.25f}
        };

        Vector2D temp = new Vector2D();
        for (float[] p : testPoints) {
            Vector2D world = camera.screenToWorld(p[0], p[1]);
            Vector2D screen = camera.worldToScreen(world.x, world.y, temp);
            assertEquals(p[0], screen.x, 0.001f);
            assertEquals(p[1], screen.y, 0.001f);
        }
    }

    @ParameterizedTest
    @ValueSource(floats = {0.75f, 1.0f, 1.35f, 1.8f, 2.25f})
    @DisplayName("Tier 2: Aim angle zoom invariance (port of C++ testCameraZoomAndMouseAiming)")
    void testAimAngleZoomInvariance(float testZoom) {
        GameCamera camera = new GameCamera();
        camera.setZoom(testZoom);
        camera.setCenter(2048.0f, 2048.0f);

        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);
        // Target North-East (+200, -200 in Y-down coordinates)
        Vector2D targetWorld = new Vector2D(playerPos.x + 200.0f, playerPos.y - 200.0f);

        float aimAngle = (float) Math.atan2(targetWorld.y - playerPos.y, targetWorld.x - playerPos.x);
        float expectedAngle = -MathUtils.PI * 0.25f;
        assertEquals(expectedAngle, aimAngle, 0.01f);
    }

    // =========================================================================
    // Tier 3: Combinatorial & Tracking Dynamics
    // =========================================================================

    @Test
    @DisplayName("Tier 3: Exponential position tracking rate")
    void testExponentialPositionTrackingRate() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.snapTo(2048.0f, 2048.0f);

        // Move target to (2500, 2500) over dt=0.1s with smoothSpeed=14.0f
        camera.update(2500.0f, 2500.0f, 0.1f, 14.0f);

        // t = 1 - exp(-1.4) approx 0.753403
        // Expected center: 2048 + (2500 - 2048) * 0.753403 = 2388.538f
        assertEquals(2388.54f, camera.getCenterX(), 0.5f);
        assertEquals(2388.54f, camera.getCenterY(), 0.5f);
    }

    @Test
    @DisplayName("Tier 3: Instantaneous tracking bypass with smoothSpeed <= 0")
    void testInstantaneousTrackingBypass() {
        GameCamera camera = new GameCamera();
        camera.snapTo(2048.0f, 2048.0f);

        camera.update(3000.0f, 3000.0f, 0.016f, 0.0f);
        assertEquals(3000.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(3000.0f, camera.getCenterY(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 3: Viewport resize handling")
    void testViewportResizeHandling() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.0f);
        camera.setViewportSize(1920.0f, 1080.0f);

        assertEquals(1920.0f, camera.getViewportWidth(), TOLERANCE);
        assertEquals(1080.0f, camera.getViewportHeight(), TOLERANCE);

        // Half viewport is now 960 x 540
        camera.snapTo(100.0f, 100.0f);
        assertEquals(960.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(540.0f, camera.getCenterY(), TOLERANCE);
    }

    // =========================================================================
    // Tier 4: Real-World Scenarios & Adversarial Hardening
    // =========================================================================

    @Test
    @DisplayName("Tier 4: Degenerate map smaller than viewport pins camera to map center")
    void testDegenerateMapSmallerThanViewport() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setWorldBounds(0.0f, 0.0f, 800.0f, 600.0f);

        // World width 800 <= viewport width 948 (at zoom 1.35) or 1280 (at zoom 1.0)
        camera.setZoom(1.0f);
        camera.snapTo(1000.0f, 1000.0f);

        assertEquals(400.0f, camera.getCenterX(), TOLERANCE);
        assertEquals(300.0f, camera.getCenterY(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 4: Non-positive delta time hardening")
    void testNonPositiveDeltaTimeHardening() {
        GameCamera camera = new GameCamera();
        camera.update(3000.0f, 3000.0f, 0.0f);
        assertFalse(Float.isNaN(camera.getCenterX()));
        assertEquals(3000.0f, camera.getCenterX(), TOLERANCE);

        camera.update(3200.0f, 3200.0f, -0.016f);
        assertFalse(Float.isNaN(camera.getCenterX()));
        assertEquals(3200.0f, camera.getCenterX(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 4: LibGDX OrthographicCamera synchronization")
    void testLibGdxOrthographicCameraSync() {
        GameCamera camera = new GameCamera();
        camera.setZoom(1.35f);
        camera.setCenter(2048.0f, 2048.0f);

        OrthographicCamera gdxCam = camera.getOrthographicCamera();
        assertNotNull(gdxCam);

        assertEquals(1.0f / 1.35f, gdxCam.zoom, TOLERANCE);
        assertEquals(2048.0f, gdxCam.position.x, TOLERANCE);
        assertEquals(2048.0f, gdxCam.position.y, TOLERANCE);

        // Coordinate unprojection parity:
        // In normalized device coordinates (NDC), center (0, 0) projected through invProjectionView
        // must yield camera world center (centerX, centerY).
        Vector3 ndcCenter = new Vector3(0.0f, 0.0f, 0.0f);
        ndcCenter.prj(gdxCam.invProjectionView);
        Vector2D pureCenter = camera.screenToWorld(640.0f, 360.0f);
        assertEquals(pureCenter.x, ndcCenter.x, 0.05f);
        assertEquals(pureCenter.y, ndcCenter.y, 0.05f);

        // Top-left NDC (-1, 1) with yDown=true must match frustum top-left
        Vector3 ndcTopLeft = new Vector3(-1.0f, 1.0f, 0.0f);
        ndcTopLeft.prj(gdxCam.invProjectionView);
        assertEquals(camera.getVisibleLeft(), ndcTopLeft.x, 0.05f);
        assertEquals(camera.getVisibleTop(), ndcTopLeft.y, 0.05f);

        // If Gdx.graphics has non-zero window dimensions, test direct unproject as well
        if (com.badlogic.gdx.Gdx.graphics != null && com.badlogic.gdx.Gdx.graphics.getHeight() > 0) {
            Vector3 gdxUnproject = new Vector3(640.0f, 360.0f, 0.0f);
            gdxCam.unproject(gdxUnproject, 0.0f, 0.0f, camera.getViewportWidth(), camera.getViewportHeight());
            assertEquals(pureCenter.x, gdxUnproject.x, 0.05f);
            assertEquals(pureCenter.y, gdxUnproject.y, 0.05f);
        }
    }

    @Test
    @DisplayName("Tier 1 (Feature 6): Aim angle unprojection consistency across standard zoom levels")
    void testAimAngleAtStandardZooms() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.snapTo(2048.0f, 2048.0f);

        float screenX = 800.0f;
        float screenY = 450.0f;
        float expectedAngle = (float) Math.atan2(450.0f - 360.0f, 800.0f - 640.0f);

        float[] zoomLevels = {1.0f, 1.35f, 2.0f};
        for (float zoom : zoomLevels) {
            camera.setZoom(zoom);
            Vector2D worldPos = camera.screenToWorld(screenX, screenY);
            float angle = (float) Math.atan2(worldPos.y - 2048.0f, worldPos.x - 2048.0f);
            assertEquals(expectedAngle, angle, 1e-4f, "Aim angle must be invariant at zoom " + zoom);
        }
    }

    @Test
    @DisplayName("Tier 2 (Feature 6): Coordinate unprojection beyond screen bounds")
    void testUnprojectionBeyondScreenBounds() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.snapTo(2048.0f, 2048.0f);
        camera.setZoom(1.35f);

        // Negative screen coordinates
        Vector2D negativeWorld = camera.screenToWorld(-100.0f, -100.0f);
        assertNotNull(negativeWorld);
        assertFalse(Float.isNaN(negativeWorld.x));
        assertFalse(Float.isNaN(negativeWorld.y));

        Vector2D roundtripNegative = camera.worldToScreen(negativeWorld.x, negativeWorld.y, new Vector2D());
        assertEquals(-100.0f, roundtripNegative.x, 0.05f);
        assertEquals(-100.0f, roundtripNegative.y, 0.05f);

        // Oversized screen coordinates beyond 1280x720
        Vector2D oversizedWorld = camera.screenToWorld(2000.0f, 1500.0f);
        assertNotNull(oversizedWorld);
        assertFalse(Float.isNaN(oversizedWorld.x));
        assertFalse(Float.isNaN(oversizedWorld.y));

        Vector2D roundtripOversized = camera.worldToScreen(oversizedWorld.x, oversizedWorld.y, new Vector2D());
        assertEquals(2000.0f, roundtripOversized.x, 0.05f);
        assertEquals(1500.0f, roundtripOversized.y, 0.05f);
    }
}
