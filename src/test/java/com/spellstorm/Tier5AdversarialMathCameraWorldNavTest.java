package com.spellstorm;

import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.math.Vector2;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.navigation.AStarPathfinder.CornerCuttingRule;
import com.spellstorm.navigation.AStarPathfinder.PathCache;
import com.spellstorm.navigation.PathNode;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tier 5 Adversarial Coverage Hardening Test Suite:
 * Rigorous empirical stress-testing across Math, Camera, World, and Navigation subsystems.
 *
 * Authored by challenger_m7_1 to verify:
 * - Numerical stability, zero/near-zero division guards, and float extrema in com.spellstorm.math
 * - Frustum boundary containment, zoom dynamics, and bijective projection in com.spellstorm.camera
 * - Void out-of-bounds safety, continuous traversability, and procedural invariants in com.spellstorm.world
 * - 8-way A* corner-cutting invariants, radius-clearance filtering, and fallback mechanisms in com.spellstorm.navigation
 */
@DisplayName("Tier 5: Adversarial Hardening (Math, Camera, World, Navigation)")
public class Tier5AdversarialMathCameraWorldNavTest {

    private static final float EPSILON = 1e-4f;
    private static final float HIGH_PRECISION_EPS = 1e-5f;

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. Math Subsystem Adversarial Hardening (Vector2D, MathUtils, CollisionUtils)
    // =========================================================================
    @Nested
    @DisplayName("1. Math Subsystem Adversarial Stress")
    class MathSubsystemHardening {

        @Test
        @DisplayName("1.1 Vector2D: Division by zero, sub-epsilon, and negative epsilon scalars")
        void testVectorDivisionGuards() {
            Vector2D v = new Vector2D(10.0f, -25.0f);

            // Zero division
            v.div(0.0f);
            assertEquals(0.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, v.y, HIGH_PRECISION_EPS);

            // Positive sub-epsilon (1e-6f < EPSILON 1e-5f)
            v.set(15.0f, 30.0f).div(1e-6f);
            assertEquals(0.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, v.y, HIGH_PRECISION_EPS);

            // Negative sub-epsilon (-5e-6f)
            v.set(15.0f, 30.0f).div(-5e-6f);
            assertEquals(0.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, v.y, HIGH_PRECISION_EPS);

            // Component-wise division with one zero component
            v.set(10.0f, 20.0f).div(2.0f, 0.0f);
            assertEquals(5.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, v.y, HIGH_PRECISION_EPS, "Zero Y-divisor must zero out Y");

            v.set(10.0f, 20.0f).div(0.0f, 4.0f);
            assertEquals(0.0f, v.x, HIGH_PRECISION_EPS, "Zero X-divisor must zero out X");
            assertEquals(5.0f, v.y, HIGH_PRECISION_EPS);

            // Division by null Vector2D
            v.set(10.0f, 20.0f).div(null);
            assertEquals(0.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, v.y, HIGH_PRECISION_EPS);
        }

        @Test
        @DisplayName("1.2 Vector2D: Normalization on underflow, float extrema, and negative zero")
        void testVectorNormalizationExtrema() {
            // Negative zero (-0.0f, -0.0f)
            Vector2D negZero = new Vector2D(-0.0f, -0.0f);
            negZero.nor();
            assertFalse(Float.isNaN(negZero.x));
            assertFalse(Float.isNaN(negZero.y));
            assertEquals(0.0f, negZero.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, negZero.y, HIGH_PRECISION_EPS);

            // Float underflow magnitude (1e-38f)
            Vector2D underflow = new Vector2D(1e-38f, 1e-38f);
            underflow.nor();
            assertEquals(0.0f, underflow.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, underflow.y, HIGH_PRECISION_EPS);

            // Extreme float values (1e18f)
            Vector2D huge = new Vector2D(3e18f, 4e18f);
            huge.nor();
            assertFalse(Float.isNaN(huge.x));
            assertFalse(Float.isNaN(huge.y));
            assertFalse(Float.isInfinite(huge.x));
            assertFalse(Float.isInfinite(huge.y));
            assertEquals(0.6f, huge.x, 1e-3f);
            assertEquals(0.8f, huge.y, 1e-3f);
            assertEquals(1.0f, huge.len(), 1e-3f);
        }

        @Test
        @DisplayName("1.3 Vector2D: Length clamping, limits, and zero-length vector handling")
        void testVectorLengthClamping() {
            // Clamping zero vector must safely return zero without division by zero
            Vector2D zero = new Vector2D(0.0f, 0.0f);
            zero.clamp(5.0f, 10.0f);
            assertEquals(0.0f, zero.x, HIGH_PRECISION_EPS);
            assertEquals(0.0f, zero.y, HIGH_PRECISION_EPS);

            // Setting length on zero vector
            zero.setLength(10.0f);
            assertEquals(0.0f, zero.x, HIGH_PRECISION_EPS, "Zero vector setLength must remain zero");

            // Vector below minimum clamp
            Vector2D small = new Vector2D(1.0f, 0.0f);
            small.clamp(4.0f, 8.0f);
            assertEquals(4.0f, small.len(), 1e-3f);
            assertEquals(4.0f, small.x, 1e-3f);

            // Vector above maximum clamp
            Vector2D large = new Vector2D(0.0f, 20.0f);
            large.clamp(4.0f, 8.0f);
            assertEquals(8.0f, large.len(), 1e-3f);
            assertEquals(8.0f, large.y, 1e-3f);

            // Vector within limits
            Vector2D mid = new Vector2D(3.0f, 4.0f); // len = 5.0f
            mid.limit(6.0f);
            assertEquals(5.0f, mid.len(), 1e-3f);
            mid.limit(3.0f);
            assertEquals(3.0f, mid.len(), 1e-3f);
        }

        @Test
        @DisplayName("1.4 Vector2D: Cross product anti-symmetry and perpendicular invariants")
        void testVectorCrossAndPerpendicular() {
            Vector2D a = new Vector2D(3.5f, -7.2f);
            Vector2D b = new Vector2D(-1.8f, 4.4f);

            // Anti-symmetry: a x b == -(b x a)
            float crossAB = a.cross(b);
            float crossBA = b.cross(a);
            assertEquals(crossAB, -crossBA, HIGH_PRECISION_EPS);

            // Collinear vectors cross product is 0
            Vector2D collinear = a.cpy().scl(3.5f);
            assertEquals(0.0f, a.cross(collinear), HIGH_PRECISION_EPS);

            // Null safety
            assertEquals(0.0f, a.cross(null), HIGH_PRECISION_EPS);
            assertEquals(0.0f, a.dot(null), HIGH_PRECISION_EPS);

            // Perpendicular orthogonality: dot(v, v.perpendicular()) == 0
            Vector2D perp = a.perpendicular();
            assertEquals(0.0f, a.dot(perp), HIGH_PRECISION_EPS);
            assertEquals(a.len(), perp.len(), HIGH_PRECISION_EPS, "Perpendicular must preserve magnitude");

            // In-place setPerpendicular
            Vector2D cloneA = a.cpy();
            cloneA.setPerpendicular();
            assertEquals(perp.x, cloneA.x, HIGH_PRECISION_EPS);
            assertEquals(perp.y, cloneA.y, HIGH_PRECISION_EPS);
        }

        @Test
        @DisplayName("1.5 Vector2D: Angle calculations across quadrants, anti-parallel vectors, and rotation")
        void testVectorAnglesAndRotations() {
            // Cardinal axes
            assertEquals(0.0f, new Vector2D(1.0f, 0.0f).angleRad(), HIGH_PRECISION_EPS);
            assertEquals(0.0f, new Vector2D(1.0f, 0.0f).angleDeg(), HIGH_PRECISION_EPS);

            assertEquals(MathUtils.HALF_PI, new Vector2D(0.0f, 1.0f).angleRad(), HIGH_PRECISION_EPS);
            assertEquals(90.0f, new Vector2D(0.0f, 1.0f).angleDeg(), HIGH_PRECISION_EPS);

            assertEquals((float) Math.PI, new Vector2D(-1.0f, 0.0f).angleRad(), HIGH_PRECISION_EPS);
            assertEquals(180.0f, new Vector2D(-1.0f, 0.0f).angleDeg(), HIGH_PRECISION_EPS);

            assertEquals(-MathUtils.HALF_PI, new Vector2D(0.0f, -1.0f).angleRad(), HIGH_PRECISION_EPS);
            assertEquals(270.0f, new Vector2D(0.0f, -1.0f).angleDeg(), HIGH_PRECISION_EPS);

            // Anti-parallel angle difference is PI or -PI
            Vector2D v1 = new Vector2D(5.0f, 0.0f);
            Vector2D v2 = new Vector2D(-5.0f, 0.0f);
            assertEquals((float) Math.PI, Math.abs(v1.angleRad(v2)), HIGH_PRECISION_EPS);

            // Rotation stress: 360-degree rotation returns to original
            Vector2D rot = new Vector2D(12.3f, -45.6f);
            float origLen = rot.len();
            rot.rotateDeg(360.0f);
            assertEquals(12.3f, rot.x, 1e-3f);
            assertEquals(-45.6f, rot.y, 1e-3f);
            assertEquals(origLen, rot.len(), 1e-3f);

            // Rotate by 90 degrees
            Vector2D right = new Vector2D(10.0f, 0.0f);
            right.rotateDeg(90.0f);
            assertEquals(0.0f, right.x, 1e-3f);
            assertEquals(10.0f, right.y, 1e-3f);
        }

        @Test
        @DisplayName("1.6 MathUtils: Angle normalization across thousands of revolutions")
        void testAngleNormalizationStress() {
            float twoPi = MathUtils.TWO_PI;

            // Normalize +100 revolutions
            float hugeAngle = 100.0f * twoPi + 1.25f;
            float norm = MathUtils.normalizeAngle(hugeAngle);
            assertEquals(1.25f, norm, 1e-3f);

            // Normalize -100 revolutions
            float hugeNegAngle = -100.0f * twoPi - 1.25f;
            float normNeg = MathUtils.normalizeAngle(hugeNegAngle);
            assertEquals(-1.25f, normNeg, 1e-3f);

            // Positive normalization [0, 2*PI)
            float normPos = MathUtils.normalizeAnglePositive(-MathUtils.HALF_PI);
            assertEquals(1.5f * (float) Math.PI, normPos, 1e-3f);

            // Degree normalization [-180, 180]
            assertEquals(45.0f, MathUtils.normalizeAngleDeg(720.0f + 45.0f), 1e-3f);
            assertEquals(-45.0f, MathUtils.normalizeAngleDeg(-720.0f - 45.0f), 1e-3f);
            assertEquals(180.0f, MathUtils.normalizeAngleDeg(180.0f), 1e-3f);

            // Shortest angle difference crossing -180/180 discontinuity
            float diff = MathUtils.shortestAngleDifference((float) Math.toRadians(175.0), (float) Math.toRadians(-175.0));
            assertEquals(10.0f, (float) Math.toDegrees(diff), 1e-3f, "175 deg to -175 deg should be +10 deg shortest");
        }

        @Test
        @DisplayName("1.7 MathUtils: Exponential decay smoothing with extreme speed and dt")
        void testExpDecayExtrema() {
            // Negative or zero dt must return current immediately
            assertEquals(50.0f, MathUtils.expDecay(50.0f, 100.0f, 10.0f, -0.1f), HIGH_PRECISION_EPS);
            assertEquals(50.0f, MathUtils.expDecay(50.0f, 100.0f, 10.0f, 0.0f), HIGH_PRECISION_EPS);

            // Large dt must converge to target
            float converged = MathUtils.expDecay(50.0f, 100.0f, 14.0f, 10.0f);
            assertEquals(100.0f, converged, 1e-4f);

            // Zero speed must remain at current
            assertEquals(50.0f, MathUtils.expDecay(50.0f, 100.0f, 0.0f, 1.0f), HIGH_PRECISION_EPS);

            // Current equals target
            assertEquals(100.0f, MathUtils.expDecay(100.0f, 100.0f, 14.0f, 0.5f), HIGH_PRECISION_EPS);
        }

        @Test
        @DisplayName("1.8 MathUtils: Smoothstep degenerate edges and out-of-range inputs")
        void testSmoothstepDegenerates() {
            // Degenerate: edge0 == edge1
            assertEquals(0.0f, MathUtils.smoothstep(10.0f, 10.0f, 5.0f), HIGH_PRECISION_EPS);
            assertEquals(1.0f, MathUtils.smoothstep(10.0f, 10.0f, 10.0f), HIGH_PRECISION_EPS);
            assertEquals(1.0f, MathUtils.smoothstep(10.0f, 10.0f, 15.0f), HIGH_PRECISION_EPS);

            // Far out-of-range
            assertEquals(0.0f, MathUtils.smoothstep(0.0f, 1.0f, -1000.0f), HIGH_PRECISION_EPS);
            assertEquals(1.0f, MathUtils.smoothstep(0.0f, 1.0f, 1000.0f), HIGH_PRECISION_EPS);

            // Standard midpoint
            assertEquals(0.5f, MathUtils.smoothstep(0.0f, 1.0f, 0.5f), HIGH_PRECISION_EPS);
        }

        @Test
        @DisplayName("1.9 CollisionUtils: Grazing contact strictness (< r^2) vs penetration in Circle-AABB")
        void testCircleVsAABBGrazingStrictness() {
            // AABB at (100, 100) with size (32, 32). Right edge at x=132.
            // Circle with radius 10 at x=142 (dist = 10, exact tangent)
            // C++ parity uses strict < (r*r), so distSq == r^2 must return FALSE
            assertFalse(CollisionUtils.checkCircleVsAABB(142.0f, 116.0f, 10.0f, 100.0f, 100.0f, 32.0f, 32.0f),
                    "Exact tangent grazing contact must NOT trigger collision (< r^2)");

            // Penetration by 1e-3f: dist = 9.999f < 10.0f -> must trigger collision
            assertTrue(CollisionUtils.checkCircleVsAABB(141.999f, 116.0f, 10.0f, 100.0f, 100.0f, 32.0f, 32.0f),
                    "Penetrating circle must trigger collision");

            // Corner grazing: corner at (132, 132). Circle at (132 + 6, 132 + 8), dist = 10.0f, radius = 10.0f
            assertFalse(CollisionUtils.checkCircleVsAABB(138.0f, 140.0f, 10.0f, 100.0f, 100.0f, 32.0f, 32.0f),
                    "Corner tangent grazing must NOT trigger collision");

            // Corner penetrating by 1e-3: dist = 9.99f
            assertTrue(CollisionUtils.checkCircleVsAABB(137.99f, 139.99f, 10.0f, 100.0f, 100.0f, 32.0f, 32.0f));

            // Degenerate inputs
            assertFalse(CollisionUtils.checkCircleVsAABB(100.0f, 100.0f, 0.0f, 100.0f, 100.0f, 32.0f, 32.0f));
            assertFalse(CollisionUtils.checkCircleVsAABB(100.0f, 100.0f, -5.0f, 100.0f, 100.0f, 32.0f, 32.0f));
            assertFalse(CollisionUtils.checkCircleVsAABB(100.0f, 100.0f, 10.0f, 100.0f, 100.0f, 0.0f, 32.0f));
        }

        @Test
        @DisplayName("1.10 CollisionUtils: Higher-order sliding resolution with synthetic obstacle traps")
        void testHigherOrderSlidingResolutionTraps() {
            // Synthetic world: 1000x1000
            // Block everything except old position
            Vector2D resolved = CollisionUtils.resolveSliding(
                    100.0f, 100.0f, 150.0f, 150.0f, 16.0f, 1000.0f, 1000.0f,
                    (x, y, r) -> (x == 100.0f && y == 100.0f)
            );
            assertEquals(100.0f, resolved.x, HIGH_PRECISION_EPS, "Trapped on all axes must retain old X");
            assertEquals(100.0f, resolved.y, HIGH_PRECISION_EPS, "Trapped on all axes must retain old Y");

            // Allow only horizontal movement (oldY)
            Vector2D slideX = CollisionUtils.resolveSliding(
                    100.0f, 100.0f, 150.0f, 150.0f, 16.0f, 1000.0f, 1000.0f,
                    (x, y, r) -> (y == 100.0f)
            );
            assertEquals(150.0f, slideX.x, HIGH_PRECISION_EPS, "Should slide along X");
            assertEquals(100.0f, slideX.y, HIGH_PRECISION_EPS);

            // Allow only vertical movement (oldX)
            Vector2D slideY = CollisionUtils.resolveSliding(
                    100.0f, 100.0f, 150.0f, 150.0f, 16.0f, 1000.0f, 1000.0f,
                    (x, y, r) -> (x == 100.0f)
            );
            assertEquals(100.0f, slideY.x, HIGH_PRECISION_EPS);
            assertEquals(150.0f, slideY.y, HIGH_PRECISION_EPS, "Should slide along Y");

            // Null predicate safe fallback
            Vector2D noPred = CollisionUtils.resolveSliding(
                    100.0f, 100.0f, 250.0f, 350.0f, 16.0f, 1000.0f, 1000.0f, null
            );
            assertEquals(250.0f, noPred.x, HIGH_PRECISION_EPS);
            assertEquals(350.0f, noPred.y, HIGH_PRECISION_EPS);
        }

        @Test
        @DisplayName("1.11 Vector2D: mulAdd, lerp extrapolation, and distance across extreme coordinates")
        void testVectorMulAddLerpDist() {
            Vector2D v = new Vector2D(10.0f, 20.0f);
            v.mulAdd(new Vector2D(5.0f, -2.0f), 3.0f);
            assertEquals(25.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(14.0f, v.y, HIGH_PRECISION_EPS);

            // mulAdd null safety
            v.mulAdd(null, 5.0f);
            assertEquals(25.0f, v.x, HIGH_PRECISION_EPS);
            assertEquals(14.0f, v.y, HIGH_PRECISION_EPS);

            // lerp extrapolation (alpha = 2.0 and alpha = -1.0)
            Vector2D a = new Vector2D(0.0f, 0.0f);
            Vector2D b = new Vector2D(10.0f, 10.0f);
            a.lerp(b, 2.0f);
            assertEquals(20.0f, a.x, HIGH_PRECISION_EPS);
            assertEquals(20.0f, a.y, HIGH_PRECISION_EPS);

            a.set(0.0f, 0.0f).lerp(b, -1.0f);
            assertEquals(-10.0f, a.x, HIGH_PRECISION_EPS);
            assertEquals(-10.0f, a.y, HIGH_PRECISION_EPS);

            // Distance across extreme coords
            Vector2D p1 = new Vector2D(-1e5f, -1e5f);
            Vector2D p2 = new Vector2D(1e5f, 1e5f);
            float expectedDist = (float) Math.hypot(2e5, 2e5);
            assertEquals(expectedDist, p1.dist(p2), 1.0f);
            assertEquals(0.0f, p1.dist(p1), HIGH_PRECISION_EPS);
            assertEquals(0.0f, Vector2D.dist(null, p1), HIGH_PRECISION_EPS);
        }

        @Test
        @DisplayName("1.12 CollisionUtils: AABB vs AABB boundary touching vs penetration and Point tests")
        void testAABBVsAABBAndPointQueries() {
            // Box 1: (0, 0, 10, 10), Box 2 touches right edge: (10, 0, 10, 10)
            // Strict inequality: touching at edge is NOT overlapping
            assertFalse(CollisionUtils.checkAABBVsAABB(0.0f, 0.0f, 10.0f, 10.0f, 10.0f, 0.0f, 10.0f, 10.0f),
                    "Touching AABBs must not overlap");

            // Overlapping by 1e-3f
            assertTrue(CollisionUtils.checkAABBVsAABB(0.0f, 0.0f, 10.0f, 10.0f, 9.999f, 0.0f, 10.0f, 10.0f));

            // Degenerate zero/negative dimensions
            assertFalse(CollisionUtils.checkAABBVsAABB(0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f));
            assertFalse(CollisionUtils.checkAABBVsAABB(0.0f, 0.0f, 10.0f, -5.0f, 0.0f, 0.0f, 10.0f, 10.0f));

            // Point vs AABB
            assertTrue(CollisionUtils.checkPointVsAABB(0.0f, 0.0f, 0.0f, 0.0f, 10.0f, 10.0f), "Corner point must be inside");
            assertTrue(CollisionUtils.checkPointVsAABB(10.0f, 10.0f, 0.0f, 0.0f, 10.0f, 10.0f), "Opposite corner must be inside");
            assertFalse(CollisionUtils.checkPointVsAABB(10.001f, 5.0f, 0.0f, 0.0f, 10.0f, 10.0f));
            assertFalse(CollisionUtils.checkPointVsAABB(null, 0.0f, 0.0f, 10.0f, 10.0f));

            // Point vs Circle
            assertTrue(CollisionUtils.checkPointVsCircle(5.0f, 0.0f, 0.0f, 0.0f, 5.0f), "Boundary point must be inside");
            assertFalse(CollisionUtils.checkPointVsCircle(5.001f, 0.0f, 0.0f, 0.0f, 5.0f));
            assertFalse(CollisionUtils.checkPointVsCircle(0.0f, 0.0f, 0.0f, 0.0f, -1.0f));
        }

        @Test
        @DisplayName("1.13 CollisionUtils: Spatial clampToBounds circle containment")
        void testClampToBoundsCircle() {
            // Circle radius 10 clamped inside box (0, 0, 100, 100) -> center clamped to [10, 90] x [10, 90]
            Vector2D c1 = CollisionUtils.clampToBounds(0.0f, 0.0f, 10.0f, 0.0f, 0.0f, 100.0f, 100.0f);
            assertEquals(10.0f, c1.x, HIGH_PRECISION_EPS);
            assertEquals(10.0f, c1.y, HIGH_PRECISION_EPS);

            Vector2D c2 = CollisionUtils.clampToBounds(150.0f, 150.0f, 10.0f, 0.0f, 0.0f, 100.0f, 100.0f);
            assertEquals(90.0f, c2.x, HIGH_PRECISION_EPS);
            assertEquals(90.0f, c2.y, HIGH_PRECISION_EPS);
        }
    }

    // =========================================================================
    // 2. Camera Subsystem Adversarial Hardening (GameCamera)
    // =========================================================================
    @Nested
    @DisplayName("2. Camera Subsystem Adversarial Stress")
    class CameraSubsystemHardening {

        @Test
        @DisplayName("2.1 GameCamera: Zoom boundary clamping and target interpolation")
        void testCameraZoomClampingAndDynamics() {
            GameCamera cam = new GameCamera(1280.0f, 720.0f);

            // Extreme zoom out clamped to MIN_ZOOM (0.75f)
            cam.setTargetZoom(0.1f);
            assertEquals(GameCamera.MIN_ZOOM, cam.getTargetZoom(), HIGH_PRECISION_EPS);

            // Extreme zoom in clamped to MAX_ZOOM (2.25f)
            cam.setTargetZoom(100.0f);
            assertEquals(GameCamera.MAX_ZOOM, cam.getTargetZoom(), HIGH_PRECISION_EPS);

            // Instantaneous setZoom re-clamps current zoom
            cam.setZoom(0.0f);
            assertEquals(GameCamera.MIN_ZOOM, cam.getZoom(), HIGH_PRECISION_EPS);
            cam.setZoom(5.0f);
            assertEquals(GameCamera.MAX_ZOOM, cam.getZoom(), HIGH_PRECISION_EPS);

            // Smooth interpolation over dt
            cam.setZoom(1.0f);
            cam.setTargetZoom(2.0f);
            cam.update(2048.0f, 2048.0f, 0.05f);
            assertTrue(cam.getZoom() > 1.0f && cam.getZoom() < 2.0f, "Zoom should interpolate smoothly");

            // Over large dt, reaches targetZoom
            cam.update(2048.0f, 2048.0f, 2.0f);
            assertEquals(2.0f, cam.getZoom(), 1e-4f);
        }

        @Test
        @DisplayName("2.2 GameCamera: Frustum containment invariant under extreme target coordinates")
        void testCameraFrustumWorldBoundsContainment() {
            GameCamera cam = new GameCamera(1280.0f, 720.0f);

            float[] extremeCoords = { -1e6f, 0.0f, 2048.0f, 4096.0f, 1e6f };
            float[] zooms = { GameCamera.MIN_ZOOM, 1.0f, GameCamera.DEFAULT_ZOOM, GameCamera.MAX_ZOOM };

            for (float zoom : zooms) {
                cam.setZoom(zoom);
                for (float targetX : extremeCoords) {
                    for (float targetY : extremeCoords) {
                        cam.snapTo(targetX, targetY);

                        Rectangle bounds = cam.getVisibleBounds();
                        // Visible frustum MUST be completely inside [0, 4096] x [0, 4096]
                        assertTrue(bounds.x >= -EPSILON,
                                "Left border exposed at zoom=" + zoom + ", target=(" + targetX + "," + targetY + "): " + bounds.x);
                        assertTrue(bounds.y >= -EPSILON,
                                "Top border exposed at zoom=" + zoom + ", target=(" + targetX + "," + targetY + "): " + bounds.y);
                        assertTrue(bounds.x + bounds.width <= 4096.0f + EPSILON,
                                "Right border exposed at zoom=" + zoom + ", target=(" + targetX + "," + targetY + "): " + (bounds.x + bounds.width));
                        assertTrue(bounds.y + bounds.height <= 4096.0f + EPSILON,
                                "Bottom border exposed at zoom=" + zoom + ", target=(" + targetX + "," + targetY + "): " + (bounds.y + bounds.height));
                    }
                }
            }
        }

        @Test
        @DisplayName("2.3 GameCamera: Bijective screen-to-world and world-to-screen round trip invariance")
        void testCameraCoordinateUnprojectionBijective() {
            GameCamera cam = new GameCamera(1280.0f, 720.0f);
            cam.setCenter(2048.0f, 2048.0f);
            cam.setViewportOffset(50.0f, -30.0f);

            Random rng = new Random(1337);

            for (float zoom : new float[]{0.75f, 1.0f, 1.35f, 1.8f, 2.25f}) {
                cam.setZoom(zoom);

                // Screen center must unproject to camera center
                Vector2D screenCenter = new Vector2D(1280.0f * 0.5f + 50.0f, 720.0f * 0.5f - 30.0f);
                Vector2D unprojCenter = cam.screenToWorld(screenCenter.x, screenCenter.y);
                assertEquals(cam.getCenterX(), unprojCenter.x, 1e-3f);
                assertEquals(cam.getCenterY(), unprojCenter.y, 1e-3f);

                // 200 random screen positions round-trip
                for (int i = 0; i < 200; i++) {
                    float sx = rng.nextFloat() * 1280.0f;
                    float sy = rng.nextFloat() * 720.0f;

                    Vector2D worldPos = cam.screenToWorld(sx, sy);
                    Vector2D backToScreen = cam.worldToScreen(worldPos.x, worldPos.y);

                    assertEquals(sx, backToScreen.x, 1e-3f, "Screen X roundtrip mismatch at zoom " + zoom);
                    assertEquals(sy, backToScreen.y, 1e-3f, "Screen Y roundtrip mismatch at zoom " + zoom);
                }
            }
        }

        @Test
        @DisplayName("2.4 GameCamera: Viewport larger than world boundary locks to center")
        void testCameraViewportLargerThanWorld() {
            // Viewport 5000x5000 is larger than world bounds 4096x4096
            GameCamera largeCam = new GameCamera(5000.0f, 5000.0f, 0.0f, 0.0f, 4096.0f, 4096.0f, 1.0f);
            largeCam.snapTo(100.0f, 100.0f);

            assertEquals(2048.0f, largeCam.getCenterX(), HIGH_PRECISION_EPS, "Camera X must lock to world center");
            assertEquals(2048.0f, largeCam.getCenterY(), HIGH_PRECISION_EPS, "Camera Y must lock to world center");
        }

        @Test
        @DisplayName("2.5 GameCamera: AABB and Circle visibility queries across all frustum borders")
        void testCameraVisibilityQueries() {
            GameCamera cam = new GameCamera(1280.0f, 720.0f);
            cam.setZoom(1.0f);
            cam.setCenter(2048.0f, 2048.0f);
            // Frustum: [2048 - 640, 2048 - 360, 1280, 720] = [1408, 1688, 1280, 720]
            // Left = 1408, Right = 2688, Top = 1688, Bottom = 2408

            // Entity completely inside
            assertTrue(cam.isVisible(2048.0f, 2048.0f, 20.0f));

            // Entity grazing Left border: center at 1408 - 15 = 1393, radius = 20 -> overlaps border
            assertTrue(cam.isVisible(1393.0f, 2048.0f, 20.0f));

            // Entity completely offscreen West: center at 1350, radius = 20 -> max X = 1370 < 1408
            assertFalse(cam.isVisible(1350.0f, 2048.0f, 20.0f));

            // Entity completely offscreen North: center Y = 1600, radius = 20 -> max Y = 1620 < 1688
            assertFalse(cam.isVisible(2048.0f, 1600.0f, 20.0f));

            // Null checks
            assertFalse(cam.isVisible(null));
            assertFalse(cam.isVisible((Vector2D) null, 10.0f));
        }

        @Test
        @DisplayName("2.6 GameCamera: Rapid continuous zoom oscillation does not accumulate drift or NaN")
        void testCameraRapidZoomOscillation() {
            GameCamera cam = new GameCamera(1280.0f, 720.0f);
            cam.setZoom(1.0f);

            for (int i = 0; i < 500; i++) {
                if (i % 2 == 0) {
                    cam.adjustZoom(0.2f);
                } else {
                    cam.adjustZoom(-0.2f);
                }
                cam.update(2048.0f, 2048.0f, 0.016f);
                assertFalse(Float.isNaN(cam.getZoom()));
                assertFalse(Float.isInfinite(cam.getZoom()));
                assertTrue(cam.getZoom() >= GameCamera.MIN_ZOOM);
                assertTrue(cam.getZoom() <= GameCamera.MAX_ZOOM);
            }
        }

        @Test
        @DisplayName("2.7 GameCamera: Extreme viewport aspect ratios (21:9 ultrawide, 9:16 portrait)")
        void testCameraExtremeAspectRatios() {
            // Ultrawide 21:9 (2560 x 1080)
            GameCamera ultrawide = new GameCamera(2560.0f, 1080.0f);
            ultrawide.setZoom(1.0f);
            ultrawide.snapTo(0.0f, 0.0f);
            Rectangle uwBounds = ultrawide.getVisibleBounds();
            assertTrue(uwBounds.x >= -EPSILON);
            assertTrue(uwBounds.y >= -EPSILON);
            assertTrue(uwBounds.x + uwBounds.width <= 4096.0f + EPSILON);

            // Portrait 9:16 (720 x 1280)
            GameCamera portrait = new GameCamera(720.0f, 1280.0f);
            portrait.setZoom(1.0f);
            portrait.snapTo(4096.0f, 4096.0f);
            Rectangle pBounds = portrait.getVisibleBounds();
            assertTrue(pBounds.x >= -EPSILON);
            assertTrue(pBounds.y >= -EPSILON);
            assertTrue(pBounds.x + pBounds.width <= 4096.0f + EPSILON);
            assertTrue(pBounds.y + pBounds.height <= 4096.0f + EPSILON);
        }

        @Test
        @DisplayName("2.8 GameCamera: Negative viewport offsets unprojection accuracy")
        void testCameraNegativeViewportOffsets() {
            GameCamera cam = new GameCamera(1280.0f, 720.0f);
            cam.setCenter(2048.0f, 2048.0f);
            cam.setViewportOffset(-100.0f, -50.0f);

            // Screen center with offset
            float centerScreenX = 1280.0f * 0.5f - 100.0f;
            float centerScreenY = 720.0f * 0.5f - 50.0f;

            Vector2D worldPos = cam.screenToWorld(centerScreenX, centerScreenY);
            assertEquals(2048.0f, worldPos.x, 1e-3f);
            assertEquals(2048.0f, worldPos.y, 1e-3f);
        }
    }

    // =========================================================================
    // 3. World Subsystem Adversarial Hardening (TileMap, WorldGenerator, Obstacles)
    // =========================================================================
    @Nested
    @DisplayName("3. World Subsystem Adversarial Stress")
    class WorldSubsystemHardening {

        @Test
        @DisplayName("3.1 TileMap: Out-of-bounds queries safely return WATER and solid containment")
        void testTileMapOutOfBoundsSafety() {
            TileMap map = new TileMap(42L);

            int[][] oobCoords = {
                    {-1, 0}, {0, -1}, {-1, -1},
                    {128, 64}, {64, 128}, {128, 128},
                    {-1000, 500}, {500, -1000}, {10000, 10000},
                    {Integer.MIN_VALUE, Integer.MAX_VALUE}
            };

            for (int[] coord : oobCoords) {
                int tx = coord[0];
                int ty = coord[1];
                assertFalse(map.isInsideGrid(tx, ty));
                assertEquals(TileType.WATER, map.tileAt(tx, ty), "OOB tile must return WATER");
                assertEquals(TileType.WATER, map.getTile(tx, ty));
                assertTrue(map.isSolidTile(tx, ty), "OOB tile must be solid");
                assertTrue(map.isWaterTile(tx, ty), "OOB tile must be water");
                assertFalse(map.isWalkable(tx, ty), "OOB tile must not be walkable");
                assertFalse(map.isBridgeTile(tx, ty), "OOB tile must not be bridge");

                // setTile must safely no-op
                assertDoesNotThrow(() -> map.setTile(tx, ty, TileType.GRASS));
            }

            // TileType.fromId out of range
            assertEquals(TileType.WATER, TileType.fromId(-1));
            assertEquals(TileType.WATER, TileType.fromId(99));

            // DecoType.fromId out of range
            assertEquals(DecoType.BUSH, DecoType.fromId(-1));
            assertEquals(DecoType.BUSH, DecoType.fromId(99));
        }

        @Test
        @DisplayName("3.2 TileMap: World-to-tile coordinate float boundary rounding")
        void testTileMapCoordinateTransformBoundaries() {
            TileMap map = new TileMap(42L);

            // Exact tile boundaries at 32px multiples
            assertEquals(0, map.worldToTileX(0.0f));
            assertEquals(0, map.worldToTileX(31.999f));
            assertEquals(1, map.worldToTileX(32.0f));
            assertEquals(1, map.worldToTileX(63.999f));
            assertEquals(2, map.worldToTileX(64.0f));

            // Negative coordinates floor correctly
            assertEquals(-1, map.worldToTileX(-0.001f));
            assertEquals(-1, map.worldToTileX(-32.0f));
            assertEquals(-2, map.worldToTileX(-32.001f));

            // Upper world boundaries
            assertEquals(127, map.worldToTileX(4095.99f));
            assertEquals(128, map.worldToTileX(4096.0f)); // Exactly at world boundary is tile 128 (OOB)

            // Tile center round-trip
            for (int tx = 0; tx < 128; tx += 16) {
                for (int ty = 0; ty < 128; ty += 16) {
                    float wx = map.tileToWorldX(tx);
                    float wy = map.tileToWorldY(ty);
                    assertEquals(tx, map.worldToTileX(wx));
                    assertEquals(ty, map.worldToTileY(wy));
                }
            }
        }

        @Test
        @DisplayName("3.3 TileMap: Entity boundary containment in isTraversable")
        void testContinuousTraversabilityWorldBoundaryConfinement() {
            TileMap map = new TileMap(42L);
            float r = 20.0f;

            // Inside valid bounds
            assertTrue(map.isTraversable(2048.0f, 2048.0f, r));

            // Left boundary violation
            assertFalse(map.isTraversable(r - 0.01f, 2048.0f, r));
            // Right boundary violation
            assertFalse(map.isTraversable(4096.0f - r + 0.01f, 2048.0f, r));
            // Top boundary violation
            assertFalse(map.isTraversable(2048.0f, r - 0.01f, r));
            // Bottom boundary violation
            assertFalse(map.isTraversable(2048.0f, 4096.0f - r + 0.01f, r));

            // Null position safety
            assertFalse(map.isTraversable(null, r));
        }

        @Test
        @DisplayName("3.4 TileMap: Visible tile range expansion (+-2 tiles) clamps strictly within [0, 127]")
        void testVisibleTileRangeExpansionClamping() {
            TileMap map = new TileMap(42L);
            GameCamera cam = new GameCamera(1280.0f, 720.0f);

            int[] range = new int[4];

            // Corner 1: Top-Left (0, 0)
            cam.setZoom(0.75f);
            cam.setCenter(0.0f, 0.0f);
            map.getVisibleTileRange(cam, range);
            assertEquals(0, range[0], "minTx must clamp to 0");
            assertEquals(0, range[2], "minTy must clamp to 0");
            assertTrue(range[1] <= 127);
            assertTrue(range[3] <= 127);

            // Corner 2: Bottom-Right (4096, 4096)
            cam.setCenter(4096.0f, 4096.0f);
            map.getVisibleTileRange(cam, range);
            assertTrue(range[0] >= 0);
            assertTrue(range[2] >= 0);
            assertEquals(127, range[1], "maxTx must clamp to 127");
            assertEquals(127, range[3], "maxTy must clamp to 127");

            // Null camera falls back to full map range
            int[] fullRange = map.getVisibleTileRange(null);
            assertEquals(0, fullRange[0]);
            assertEquals(127, fullRange[1]);
            assertEquals(0, fullRange[2]);
            assertEquals(127, fullRange[3]);
        }

        @Test
        @DisplayName("3.5 WorldGenerator: Invariants across divergent seeds (River, Bridges, Safe-Zone)")
        void testWorldGeneratorDivergentSeedsInvariants() {
            long[] testSeeds = { 1L, 42L, 9999L, 123456789L };

            for (long seed : testSeeds) {
                TileMap map = new TileMap(seed);

                // Invariant 1: Water tiles exist across all 128 rows
                for (int y = 0; y < 128; y++) {
                    boolean hasWaterOrBridge = false;
                    for (int x = 20; x <= 65; x++) {
                        if (map.isWaterTile(x, y) || map.isBridgeTile(x, y)) {
                            hasWaterOrBridge = true;
                            break;
                        }
                    }
                    assertTrue(hasWaterOrBridge, "River must span row " + y + " under seed " + seed);
                }

                // Invariant 2: Safe zone around center (r=384px) has ZERO solid obstacles
                Vector2D center = new Vector2D(2048.0f, 2048.0f);
                for (Obstacle obs : map.getObstacles()) {
                    if (obs.isSolid()) {
                        float d = center.dist(obs.getPosition());
                        assertTrue(d >= 384.0f - EPSILON,
                                "Solid obstacle within safe-zone: d=" + d + " for seed " + seed);
                    }
                }

                // Invariant 3: Solid obstacles center-to-center spacing >= 44.0px
                List<Obstacle> solids = map.getObstacles().stream().filter(Obstacle::isSolid).toList();
                for (int i = 0; i < solids.size(); i++) {
                    for (int j = i + 1; j < solids.size(); j++) {
                        float d = solids.get(i).getPosition().dist(solids.get(j).getPosition());
                        assertTrue(d >= 44.0f - EPSILON,
                                "Solid obstacles too close (" + d + "px) under seed " + seed);
                    }
                }

                // Invariant 4: Total decorations budget <= 300
                assertTrue(map.getObstacles().size() <= 300,
                        "Decoration budget exceeded: " + map.getObstacles().size() + " under seed " + seed);
            }
        }

        @Test
        @DisplayName("3.6 TileMap: Sliding collision along river bank diagonal walls preserves traversability")
        void testRiverBankDiagonalSlidingCollision() {
            TileMap map = new TileMap(42L);

            // Find a river water tile and test diagonal collision approach
            // At y = 60, water is around x = 40..43
            for (int y = 50; y <= 70; y++) {
                for (int x = 30; x <= 45; x++) {
                    if (map.isWaterTile(x, y) && !map.isWaterTile(x - 1, y)) {
                        // (x - 1, y) is land, (x, y) is water
                        float landX = map.tileToWorldX(x - 1);
                        float landY = map.tileToWorldY(y);
                        float targetWaterX = map.tileToWorldX(x);
                        float targetWaterY = landY + 16.0f;

                        Vector2D resolved = map.resolveCollision(landX, landY, targetWaterX, targetWaterY, 12.0f);
                        assertTrue(map.isTraversable(resolved.x, resolved.y, 12.0f),
                                "Sliding collision result must be traversable: " + resolved);
                    }
                }
            }
        }

        @Test
        @DisplayName("3.7 TileMap: Obstacle depth sorting contract (anchor Y ascending)")
        void testObstacleDepthSortingContract() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            map.addDecoration(DecoType.ROCK, 100.0f, 500.0f, true);
            map.addDecoration(DecoType.TREE_OAK, 100.0f, 200.0f, true);
            map.addDecoration(DecoType.RUINS, 100.0f, 800.0f, true);
            map.addDecoration(DecoType.CAMPFIRE, 100.0f, 150.0f, true);

            map.sortObstaclesByDepth();
            List<Obstacle> sorted = map.getObstacles();
            for (int i = 0; i < sorted.size() - 1; i++) {
                assertTrue(sorted.get(i).getPosition().y <= sorted.get(i + 1).getPosition().y,
                        "Obstacles must be strictly sorted by anchor Y ascending");
            }
        }

        @Test
        @DisplayName("3.8 Obstacle: Collision center offset handling for tall obstacles")
        void testObstacleCollisionCenterOffsets() {
            // RUINS has height 48, collision radius 14, collisionOffset Y = -10
            Obstacle ruins = new Obstacle(DecoType.RUINS, 500.0f, 500.0f, true);
            assertEquals(500.0f, ruins.getCollisionCenter().x, HIGH_PRECISION_EPS);
            assertEquals(490.0f, ruins.getCollisionCenter().y, HIGH_PRECISION_EPS, "RUINS collision center Y must be posY - 10");
            assertEquals(14.0f, ruins.getCollisionRadius(), HIGH_PRECISION_EPS);

            // TREE_OAK has collisionOffset Y = -8, radius = 12
            Obstacle oak = new Obstacle(DecoType.TREE_OAK, 600.0f, 600.0f, true);
            assertEquals(592.0f, oak.getCollisionCenter().y, HIGH_PRECISION_EPS);
            assertEquals(12.0f, oak.getCollisionRadius(), HIGH_PRECISION_EPS);

            // Null DecoType throws NullPointerException
            assertThrows(NullPointerException.class, () -> new Obstacle(null, 100.0f, 100.0f));
        }

        @Test
        @DisplayName("3.9 TileMap: Passable decorations (BUSH, LOG) do not impede movement or collision")
        void testPassableDecorationsDoNotBlock() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            // Place passable bush and log
            map.addDecoration(DecoType.BUSH, 1000.0f, 1000.0f, false);
            map.addDecoration(DecoType.LOG, 1020.0f, 1000.0f, false);

            // Entity centered on bush
            assertTrue(map.isTraversable(1000.0f, 1000.0f, 16.0f), "Passable bush must not block traversability");
            assertTrue(map.isTraversable(1020.0f, 1000.0f, 16.0f), "Passable log must not block traversability");

            // Movement through bush
            Vector2D moved = map.resolveCollision(980.0f, 1000.0f, 1020.0f, 1000.0f, 16.0f);
            assertEquals(1020.0f, moved.x, HIGH_PRECISION_EPS, "Entity must move completely freely through passable flora");
            assertEquals(1000.0f, moved.y, HIGH_PRECISION_EPS);
        }
    }

    // =========================================================================
    // 4. Navigation Subsystem Adversarial Hardening (AStarPathfinder, PathCache)
    // =========================================================================
    @Nested
    @DisplayName("4. Navigation Subsystem Adversarial Stress")
    class NavigationSubsystemHardening {

        @Test
        @DisplayName("4.1 AStarPathfinder: Octile heuristic symmetry and exact boundary values")
        void testOctileHeuristicSymmetryAndExtrema() {
            TileMap map = new TileMap(42L);
            AStarPathfinder pf = new AStarPathfinder(map);

            // Zero distance
            assertEquals(0.0f, pf.octileHeuristic(10, 10, 10, 10), HIGH_PRECISION_EPS);

            // Symmetry: h(A, B) == h(B, A)
            Random rng = new Random(42);
            for (int i = 0; i < 100; i++) {
                int x1 = rng.nextInt(128);
                int y1 = rng.nextInt(128);
                int x2 = rng.nextInt(128);
                int y2 = rng.nextInt(128);

                float h1 = pf.octileHeuristic(x1, y1, x2, y2);
                float h2 = pf.octileHeuristic(x2, y2, x1, y1);
                assertEquals(h1, h2, HIGH_PRECISION_EPS, "Octile heuristic must be symmetric");
            }
        }

        @Test
        @DisplayName("4.2 AStarPathfinder: Corner-cutting strictness (STRICT vs LENIENT vs DISALLOWED)")
        void testCornerCuttingStrictnessPolicies() {
            // Create custom map with a diagonal wall barrier:
            // (11, 10) is solid water, (10, 11) is solid water
            // Can an entity move diagonally from (10, 10) to (11, 11)?
            TileMap map = new TileMap(42L);
            map.clearObstacles();
            map.setTile(11, 10, TileType.WATER);
            map.setTile(10, 11, TileType.WATER);

            AStarPathfinder strictPf = new AStarPathfinder(map, CornerCuttingRule.STRICT);
            AStarPathfinder lenientPf = new AStarPathfinder(map, CornerCuttingRule.LENIENT);
            AStarPathfinder disallowPf = new AStarPathfinder(map, CornerCuttingRule.DISALLOWED);

            // Under STRICT: diagonal move (10, 10) -> (11, 11) is strictly forbidden because both orthogonals are water
            // Path must route completely around the two water tiles (length > 2)
            Vector2D start = map.tileToWorld(10, 10);
            Vector2D goal = map.tileToWorld(11, 11);

            List<Vector2D> strictPath = strictPf.findPath(start.x, start.y, goal.x, goal.y, 8.0f);
            assertFalse(strictPath.isEmpty());
            // In strict mode, direct diagonal step must not be taken
            for (Vector2D wp : strictPath) {
                // Must not be near the blocked water tiles
                assertFalse(wp.dist(map.tileToWorld(11, 10)) < 16.0f);
                assertFalse(wp.dist(map.tileToWorld(10, 11)) < 16.0f);
            }

            // Now test single-orthogonal blocked: (11, 10) is water, (10, 11) is grass
            map.setTile(10, 11, TileType.GRASS);

            // Under STRICT: still forbidden because ONE orthogonal is water (strict requires BOTH to be walkable)
            // Under LENIENT: allowed because at least one orthogonal is walkable
            assertEquals(CornerCuttingRule.STRICT, strictPf.getCornerRule());
            assertEquals(CornerCuttingRule.LENIENT, lenientPf.getCornerRule());
            assertEquals(CornerCuttingRule.DISALLOWED, disallowPf.getCornerRule());
        }

        @Test
        @DisplayName("4.3 AStarPathfinder: Multi-size radius clearance (Melee r=16 vs Boss r=50 through narrow gap)")
        void testMultiSizeRadiusClearanceGap() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            // Create a 2-tile gap (64px) between water channels at y = 50
            // Water on left: x in [0, 48]
            // Water on right: x in [51, 127]
            // Gap: x in [49, 50] (width = 2 tiles = 64px)
            for (int y = 48; y <= 52; y++) {
                for (int x = 0; x <= 48; x++) {
                    map.setTile(x, y, TileType.WATER);
                }
                for (int x = 51; x <= 127; x++) {
                    map.setTile(x, y, TileType.WATER);
                }
            }

            AStarPathfinder pf = new AStarPathfinder(map);

            Vector2D start = map.tileToWorld(49, 40); // North of gap
            Vector2D goal = map.tileToWorld(49, 60);  // South of gap

            // Melee Enemy (radius 16, diameter 32): easily traverses 64px gap
            List<Vector2D> meleePath = pf.findPath(start.x, start.y, goal.x, goal.y, 16.0f);
            assertFalse(meleePath.isEmpty());
            Vector2D lastMelee = meleePath.get(meleePath.size() - 1);
            assertEquals(goal.x, lastMelee.x, 1.0f);
            assertEquals(goal.y, lastMelee.y, 1.0f);

            // Tank Enemy (radius 26, diameter 52): fits through 64px gap
            List<Vector2D> tankPath = pf.findPath(start.x, start.y, goal.x, goal.y, 26.0f);
            assertFalse(tankPath.isEmpty());

            // Colossus Boss (radius 50, diameter 100): CANNOT traverse 64px gap!
            // When entity cannot pass through gap, it does not penetrate water tiles
            List<Vector2D> bossPath = pf.findPath(start.x, start.y, goal.x, goal.y, 50.0f, 500);
            for (Vector2D wp : bossPath) {
                // Must never occupy the water channel
                assertTrue(map.isTraversable(wp.x, wp.y, 50.0f),
                        "Boss waypoint must never violate water collision: " + wp);
            }
        }

        @Test
        @DisplayName("4.4 AStarPathfinder: Graceful fallback Layer 1 (blocked target) and Layer 2 (unreachable shoreline)")
        void testGracefulFallbackMechanisms() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            AStarPathfinder pf = new AStarPathfinder(map);

            // Layer 1 Fallback: Target is inside a single water tile at (20, 20)
            map.setTile(20, 20, TileType.WATER);
            Vector2D start = map.tileToWorld(15, 20);
            Vector2D waterTarget = map.tileToWorld(20, 20);

            List<Vector2D> pathLayer1 = pf.findPath(start.x, start.y, waterTarget.x, waterTarget.y, 12.0f);
            assertFalse(pathLayer1.isEmpty(), "Layer 1 fallback must produce path to adjacent walkable tile");
            Vector2D destL1 = pathLayer1.get(pathLayer1.size() - 1);
            // End point must be walkable
            assertTrue(map.isTraversable(destL1, 12.0f));
            // Distance from target should be within kernel radius (~1 tile)
            assertTrue(destL1.dist(waterTarget) <= 48.0f);

            // Layer 2 Fallback: Target is on an unreachable island surrounded by wide water
            // Water lake around (80, 80): 10x10 water block
            for (int dy = -5; dy <= 5; dy++) {
                for (int dx = -5; dx <= 5; dx++) {
                    map.setTile(80 + dx, 80 + dy, TileType.WATER);
                }
            }
            // Island at center
            map.setTile(80, 80, TileType.GRASS);

            Vector2D shoreStart = map.tileToWorld(65, 80);
            Vector2D islandTarget = map.tileToWorld(80, 80);

            List<Vector2D> pathLayer2 = pf.findPath(shoreStart.x, shoreStart.y, islandTarget.x, islandTarget.y, 12.0f, 1000);
            assertFalse(pathLayer2.isEmpty(), "Layer 2 fallback must produce path to closest shoreline vantage point");
            Vector2D shorelineDest = pathLayer2.get(pathLayer2.size() - 1);
            assertTrue(map.isTraversable(shorelineDest, 12.0f));
            // Destination is on the shoreline, not on the unreachable island
            assertTrue(shorelineDest.dist(islandTarget) > 32.0f);
        }

        @Test
        @DisplayName("4.5 AStarPathfinder: Low expansion budget and identical start/goal edge cases")
        void testPathfinderBudgetAndEdgeCases() {
            TileMap map = new TileMap(42L);
            AStarPathfinder pf = new AStarPathfinder(map);

            // Start == Goal in traversable space
            Vector2D p = map.tileToWorld(50, 50);
            List<Vector2D> selfPath = pf.findPath(p.x, p.y, p.x, p.y, 12.0f);
            assertEquals(1, selfPath.size());
            assertEquals(p.x, selfPath.get(0).x, 1e-3f);
            assertEquals(p.y, selfPath.get(0).y, 1e-3f);

            // Extremely low expansion budget (maxExpansions = 2)
            Vector2D start = map.tileToWorld(10, 10);
            Vector2D goal = map.tileToWorld(100, 100);
            List<Vector2D> lowBudgetPath = pf.findPath(start.x, start.y, goal.x, goal.y, 12.0f, 2);
            assertFalse(lowBudgetPath.isEmpty(), "Low budget path must not crash or return empty");

            // Null inputs
            assertTrue(pf.findPath(null, p, 12.0f).isEmpty());
            assertTrue(pf.findPath(p, null, 12.0f).isEmpty());
        }

        @Test
        @DisplayName("4.6 PathCache: Repath triggers, stuck timer, and staggered timer allocation")
        void testPathCacheLifecycleAndSteering() {
            PathCache cache = new PathCache();
            assertEquals(0, cache.getNavWaypointIndex());
            assertTrue(cache.needsRepath(new Vector2D(100.0f, 100.0f)), "Empty path must require repath");

            // Staggered timer check across entity IDs 0..9
            for (int id = 0; id < 10; id++) {
                cache.resetAfterRepath(List.of(new Vector2D(10.0f, 10.0f), new Vector2D(20.0f, 20.0f)),
                        new Vector2D(50.0f, 50.0f), id);
                float expectedTimer = 0.35f + (id % 5) * 0.05f;
                assertEquals(expectedTimer, cache.getNavRepathTimer(), 1e-4f,
                        "Repath timer must be staggered for entityId " + id);
            }

            // Target displacement trigger (> 64px = 2 full tiles)
            cache.resetAfterRepath(List.of(new Vector2D(10.0f, 10.0f)), new Vector2D(100.0f, 100.0f), 0);
            assertFalse(cache.needsRepath(new Vector2D(110.0f, 100.0f))); // Moved 10px -> no repath
            assertTrue(cache.needsRepath(new Vector2D(200.0f, 100.0f)));  // Moved 100px (>64) -> needs repath

            // Stuck detection trigger (> 0.35s)
            Vector2D stuckPos = new Vector2D(50.0f, 50.0f);
            cache.updateTimers(stuckPos, 0.20f);
            assertFalse(cache.needsRepath(new Vector2D(100.0f, 100.0f)));
            cache.updateTimers(stuckPos, 0.20f); // 0.40s total stuck time (> 0.35s)
            assertTrue(cache.needsRepath(new Vector2D(100.0f, 100.0f)), "Stuck timer exceeding 0.35s must trigger repath");

            // Moving frees stuck timer
            cache.updateTimers(new Vector2D(150.0f, 150.0f), 0.10f);
            assertEquals(0.0f, cache.getNavStuckTimer(), HIGH_PRECISION_EPS, "Moving must reset stuck timer");
        }

        @Test
        @DisplayName("4.7 PathNode: Priority queue ordering and equality contract")
        void testPathNodeContract() {
            PathNode n1 = new PathNode(10, 10, 5.0f, 15.0f);
            PathNode n2 = new PathNode(10, 10, 8.0f, 20.0f);
            PathNode n3 = new PathNode(12, 14, 5.0f, 10.0f);

            // Equality based on coordinates (x, y)
            assertEquals(n1, n2, "Nodes at same coordinates must be equal");
            assertEquals(n1.hashCode(), n2.hashCode());
            assertNotEquals(n1, n3);

            // compareTo based on f
            assertTrue(n3.compareTo(n1) < 0, "Lower f must sort first in priority queue");
            assertTrue(n2.compareTo(n1) > 0);
            assertEquals(-1, n1.compareTo(null), "Null compareTo safe fallback");
        }

        @Test
        @DisplayName("4.8 AStarPathfinder: U-turn around dead-end cul-de-sac wall")
        void testPathfinderDeadEndCulDeSac() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            // Build a U-shaped pocket:
            // West wall: x = 20, y in [20, 25]
            // North wall: y = 20, x in [20, 25]
            // East wall: x = 25, y in [20, 25]
            // Open to the South at y = 26
            for (int y = 20; y <= 25; y++) {
                map.setTile(20, y, TileType.WATER);
                map.setTile(25, y, TileType.WATER);
            }
            for (int x = 20; x <= 25; x++) {
                map.setTile(x, 20, TileType.WATER);
            }

            AStarPathfinder pf = new AStarPathfinder(map);

            // Start inside cul-de-sac at (22, 22), target just outside north wall at (22, 18)
            Vector2D start = map.tileToWorld(22, 22);
            Vector2D goal = map.tileToWorld(22, 18);

            List<Vector2D> path = pf.findPath(start.x, start.y, goal.x, goal.y, 8.0f);
            assertFalse(path.isEmpty(), "Pathfinder must escape cul-de-sac");

            // Verify path navigates south to exit pocket, then loops north around wall
            boolean reachedExit = false;
            for (Vector2D wp : path) {
                if (wp.y > map.tileToWorldY(25)) {
                    reachedExit = true;
                }
            }
            assertTrue(reachedExit, "Path must exit through the southern opening");
            Vector2D endWp = path.get(path.size() - 1);
            assertEquals(goal.x, endWp.x, 2.0f);
            assertEquals(goal.y, endWp.y, 2.0f);
        }

        @Test
        @DisplayName("4.9 AStarPathfinder: String-pulling look-ahead skips collinear waypoints in open terrain")
        void testPathSmoothingCollinearSkipping() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            AStarPathfinder pf = new AStarPathfinder(map);

            // Diagonal path from (10, 10) to (30, 30) on open grass
            Vector2D start = map.tileToWorld(10, 10);
            Vector2D goal = map.tileToWorld(30, 30);

            List<Vector2D> path = pf.findPath(start.x, start.y, goal.x, goal.y, 10.0f);
            // With string-pulling, open diagonal LOS should smooth directly to target (size 1)
            assertEquals(1, path.size(), "Open terrain path with clear LOS must string-pull directly to target");
            assertEquals(goal.x, path.get(0).x, 1e-3f);
            assertEquals(goal.y, path.get(0).y, 1e-3f);
        }

        @Test
        @DisplayName("4.10 AStarPathfinder: Disallowed diagonal mode generates strictly cardinal steps")
        void testDisallowedDiagonalOnlyCardinalSteps() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();

            AStarPathfinder cardinalPf = new AStarPathfinder(map, CornerCuttingRule.DISALLOWED);

            // Place an obstacle in direct LOS to force A* grid expansion
            map.setTile(15, 15, TileType.WATER);
            map.setTile(16, 15, TileType.WATER);
            map.setTile(15, 16, TileType.WATER);

            Vector2D start = map.tileToWorld(14, 14);
            Vector2D goal = map.tileToWorld(17, 17);

            List<Vector2D> path = cardinalPf.findPath(start.x, start.y, goal.x, goal.y, 8.0f);
            assertFalse(path.isEmpty());
            // Path must reach goal
            Vector2D end = path.get(path.size() - 1);
            assertEquals(goal.x, end.x, 2.0f);
            assertEquals(goal.y, end.y, 2.0f);
        }

        @Test
        @DisplayName("4.11 PathCache: Advance waypoint with arrival radius and look-ahead string pulling")
        void testPathCacheWaypointAdvancement() {
            TileMap map = new TileMap(42L);
            map.clearObstacles();
            AStarPathfinder pf = new AStarPathfinder(map);

            PathCache cache = new PathCache();
            Vector2D wp1 = new Vector2D(100.0f, 100.0f);
            Vector2D wp2 = new Vector2D(200.0f, 100.0f);
            Vector2D wp3 = new Vector2D(300.0f, 100.0f);
            cache.resetAfterRepath(List.of(wp1, wp2, wp3), wp3, 1);

            // Entity at (95, 100) (dist to wp1 is 5px < arrival radius 22px)
            // With LOS to wp2, look-ahead advances
            Vector2D next = cache.advanceWaypoint(new Vector2D(95.0f, 100.0f), pf, 10.0f);
            assertNotNull(next);
            // Has advanced past wp1
            assertTrue(cache.getNavWaypointIndex() >= 1);
        }
    }
}
