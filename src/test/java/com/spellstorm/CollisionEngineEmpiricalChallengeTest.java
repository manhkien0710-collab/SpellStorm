package com.spellstorm;

import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Empirical Challenge and Stress Verification Test Suite for Milestone 2 Collision Engine.
 * Specifically validates all requirements in Challenge Scope:
 * 1. Circle vs Circle (touching, overlapping, separated, zero-radius, penetration, coincident)
 * 2. Circle vs AABB (inside, corner/edge, grazing corner/edge at r - eps vs r + eps, zero-radius)
 * 3. 2-pass sliding resolution:
 *    - Diagonal into horizontal wall -> slides along X
 *    - Diagonal into vertical wall -> slides along Y
 *    - Diagonal into internal corner -> blocked on both axes, retains old position
 *    - Unobstructed in traversable space -> moves freely to candidate
 * 4. Exact behavioral parity against C++ testTileMapCollisionSliding in test_world_camera.cpp.
 * 5. Monte Carlo fuzz stress testing (10,000 iterations).
 */
public class CollisionEngineEmpiricalChallengeTest {

    private static final float EPSILON = 1e-4f;
    private static final float HIGH_PRECISION_EPS = 1e-5f;

    // =========================================================================
    // Challenge Dimension 1: Circle vs Circle
    // =========================================================================

    @Test
    @DisplayName("Challenge 1.1: Touching circles at exact distance r1 + r2 along X, Y, and diagonal")
    void testCircleVsCircle_TouchingExact() {
        // Axis-aligned X: dist = 10 + 15 = 25
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 10.0f, 25.0f, 0.0f, 15.0f),
                "Touching circles along X must overlap (distSq <= (r1+r2)^2)");
        CollisionUtils.PenetrationResult penX = CollisionUtils.getCircleVsCirclePenetration(
                0.0f, 0.0f, 10.0f, 25.0f, 0.0f, 15.0f
        );
        assertNotNull(penX, "Penetration result must not be null for touching circles");
        assertEquals(0.0f, penX.depth(), EPSILON, "Penetration depth must be 0 for touching circles");
        assertEquals(-1.0f, penX.normalX(), EPSILON);
        assertEquals(0.0f, penX.normalY(), EPSILON);

        // Axis-aligned Y: dist = 20 + 30 = 50
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 20.0f, 0.0f, 50.0f, 30.0f));
        CollisionUtils.PenetrationResult penY = CollisionUtils.getCircleVsCirclePenetration(
                0.0f, 0.0f, 20.0f, 0.0f, 50.0f, 30.0f
        );
        assertNotNull(penY);
        assertEquals(0.0f, penY.depth(), EPSILON);
        assertEquals(0.0f, penY.normalX(), EPSILON);
        assertEquals(-1.0f, penY.normalY(), EPSILON);

        // Diagonal (3-4-5 triangle): c1=(0,0, r=2), c2=(3,4, r=3). dist = 5.0 == 2 + 3
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 2.0f, 3.0f, 4.0f, 3.0f));
        CollisionUtils.PenetrationResult penDiag = CollisionUtils.getCircleVsCirclePenetration(
                0.0f, 0.0f, 2.0f, 3.0f, 4.0f, 3.0f
        );
        assertNotNull(penDiag);
        assertEquals(0.0f, penDiag.depth(), EPSILON);
        assertEquals(-0.6f, penDiag.normalX(), EPSILON);
        assertEquals(-0.8f, penDiag.normalY(), EPSILON);
    }

    @Test
    @DisplayName("Challenge 1.2: Overlapping circles of varying sizes and penetrations")
    void testCircleVsCircle_Overlapping() {
        // Deep overlap: c1=(0, 0, r=20), c2=(10, 0, r=20). dist = 10, rSum = 40, depth = 30
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 20.0f, 10.0f, 0.0f, 20.0f));
        CollisionUtils.PenetrationResult pen = CollisionUtils.getCircleVsCirclePenetration(
                0.0f, 0.0f, 20.0f, 10.0f, 0.0f, 20.0f
        );
        assertNotNull(pen);
        assertEquals(30.0f, pen.depth(), EPSILON);
        assertEquals(-1.0f, pen.normalX(), EPSILON);
        assertEquals(0.0f, pen.normalY(), EPSILON);

        // Completely concentric circles: c1=(50, 50, r=10), c2=(50, 50, r=20)
        assertTrue(CollisionUtils.checkCircleVsCircle(50.0f, 50.0f, 10.0f, 50.0f, 50.0f, 20.0f));
        CollisionUtils.PenetrationResult penConc = CollisionUtils.getCircleVsCirclePenetration(
                50.0f, 50.0f, 10.0f, 50.0f, 50.0f, 20.0f
        );
        assertNotNull(penConc);
        assertEquals(30.0f, penConc.depth(), EPSILON);
        assertEquals(1.0f, penConc.normalX(), EPSILON, "Concentric circles must fallback to valid unit normal");
        assertEquals(0.0f, penConc.normalY(), EPSILON);
    }

    @Test
    @DisplayName("Challenge 1.3: Separated circles (gap > 0)")
    void testCircleVsCircle_Separated() {
        // Slightly separated: dist = 25.001, rSum = 25.0
        float rSum = 25.0f;
        float dist = rSum + 1e-3f;
        assertFalse(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 10.0f, dist, 0.0f, 15.0f));
        assertNull(CollisionUtils.getCircleVsCirclePenetration(0.0f, 0.0f, 10.0f, dist, 0.0f, 15.0f));

        // Substantially separated
        assertFalse(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 10.0f, 1000.0f, 1000.0f, 10.0f));
        assertNull(CollisionUtils.getCircleVsCirclePenetration(0.0f, 0.0f, 10.0f, 1000.0f, 1000.0f, 10.0f));
    }

    @Test
    @DisplayName("Challenge 1.4: Zero-radius circles (points) and negative radius handling")
    void testCircleVsCircle_ZeroAndNegativeRadius() {
        // Two zero-radius circles at exact same position -> dist = 0 <= 0 -> true
        assertTrue(CollisionUtils.checkCircleVsCircle(10.0f, 20.0f, 0.0f, 10.0f, 20.0f, 0.0f));
        CollisionUtils.PenetrationResult penZero = CollisionUtils.getCircleVsCirclePenetration(
                10.0f, 20.0f, 0.0f, 10.0f, 20.0f, 0.0f
        );
        assertNotNull(penZero);
        assertEquals(0.0f, penZero.depth(), EPSILON);

        // Two zero-radius circles at different positions -> dist > 0 -> false
        assertFalse(CollisionUtils.checkCircleVsCircle(10.0f, 20.0f, 0.0f, 10.1f, 20.0f, 0.0f));

        // One zero-radius circle (point) vs normal circle
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 0.0f, 5.0f, 0.0f, 10.0f), "Point inside circle");
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 0.0f, 10.0f, 0.0f, 10.0f), "Point on perimeter");
        assertFalse(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 0.0f, 10.01f, 0.0f, 10.0f), "Point outside circle");

        // Negative radius must be rejected gracefully
        assertFalse(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, -5.0f, 0.0f, 0.0f, 10.0f));
        assertFalse(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 10.0f, 0.0f, 0.0f, -5.0f));
        assertNull(CollisionUtils.getCircleVsCirclePenetration(0.0f, 0.0f, -1.0f, 0.0f, 0.0f, 1.0f));
    }

    // =========================================================================
    // Challenge Dimension 2: Circle vs AABB
    // =========================================================================

    @Test
    @DisplayName("Challenge 2.1: Circle center inside AABB")
    void testCircleVsAABB_CenterInside() {
        float rx = 100.0f, ry = 100.0f, rw = 64.0f, rh = 64.0f;
        // Exact center of AABB
        assertTrue(CollisionUtils.checkCircleVsAABB(132.0f, 132.0f, 10.0f, rx, ry, rw, rh));
        // Near edge inside AABB
        assertTrue(CollisionUtils.checkCircleVsAABB(101.0f, 101.0f, 5.0f, rx, ry, rw, rh));
        // Giant circle enclosing entire AABB
        assertTrue(CollisionUtils.checkCircleVsAABB(132.0f, 132.0f, 500.0f, rx, ry, rw, rh));
    }

    @Test
    @DisplayName("Challenge 2.2: Circle center on corner or edge of AABB")
    void testCircleVsAABB_CenterOnCornerOrEdge() {
        float rx = 200.0f, ry = 200.0f, rw = 50.0f, rh = 50.0f;
        // Center on 4 corners
        assertTrue(CollisionUtils.checkCircleVsAABB(rx, ry, 5.0f, rx, ry, rw, rh), "Top-left corner");
        assertTrue(CollisionUtils.checkCircleVsAABB(rx + rw, ry, 5.0f, rx, ry, rw, rh), "Top-right corner");
        assertTrue(CollisionUtils.checkCircleVsAABB(rx, ry + rh, 5.0f, rx, ry, rw, rh), "Bottom-left corner");
        assertTrue(CollisionUtils.checkCircleVsAABB(rx + rw, ry + rh, 5.0f, rx, ry, rw, rh), "Bottom-right corner");

        // Center on 4 edges
        assertTrue(CollisionUtils.checkCircleVsAABB(rx + 25.0f, ry, 5.0f, rx, ry, rw, rh), "Top edge");
        assertTrue(CollisionUtils.checkCircleVsAABB(rx + 25.0f, ry + rh, 5.0f, rx, ry, rw, rh), "Bottom edge");
        assertTrue(CollisionUtils.checkCircleVsAABB(rx, ry + 25.0f, 5.0f, rx, ry, rw, rh), "Left edge");
        assertTrue(CollisionUtils.checkCircleVsAABB(rx + rw, ry + 25.0f, 5.0f, rx, ry, rw, rh), "Right edge");
    }

    @Test
    @DisplayName("Challenge 2.3: Circle grazing corner at distance r - eps vs r vs r + eps")
    void testCircleVsAABB_CornerGrazing() {
        // AABB at [0, 0, 100, 100], corner at (100, 100).
        // 45-degree angle from corner: normal direction (1/sqrt(2), 1/sqrt(2))
        float cornerX = 100.0f;
        float cornerY = 100.0f;
        float r = 10.0f;
        float invSqrt2 = (float) (1.0 / Math.sqrt(2.0));

        // 1. Distance = r - eps -> Overlaps
        float distInside = r - 1e-3f;
        float cxInside = cornerX + distInside * invSqrt2;
        float cyInside = cornerY + distInside * invSqrt2;
        assertTrue(CollisionUtils.checkCircleVsAABB(cxInside, cyInside, r, 0.0f, 0.0f, 100.0f, 100.0f),
                "Circle penetrating corner at r - eps must report collision");

        // 2. Distance = exact r -> Strict inequality (dx^2 + dy^2 < r^2) means 100 < 100 is FALSE
        float cxTangent = cornerX + r * invSqrt2;
        float cyTangent = cornerY + r * invSqrt2;
        assertFalse(CollisionUtils.checkCircleVsAABB(cxTangent, cyTangent, r, 0.0f, 0.0f, 100.0f, 100.0f),
                "Circle exactly tangent at corner must report false under strict < inequality (C++ parity)");

        // 3. Distance = r + eps -> Disjoint
        float distOutside = r + 1e-3f;
        float cxOutside = cornerX + distOutside * invSqrt2;
        float cyOutside = cornerY + distOutside * invSqrt2;
        assertFalse(CollisionUtils.checkCircleVsAABB(cxOutside, cyOutside, r, 0.0f, 0.0f, 100.0f, 100.0f),
                "Circle outside corner at r + eps must report false");
    }

    @Test
    @DisplayName("Challenge 2.4: Circle grazing facing edge at distance r - eps vs r vs r + eps")
    void testCircleVsAABB_EdgeGrazing() {
        // AABB [0, 0, 100, 100], right edge at x = 100. Circle centered at y = 50.
        float r = 12.0f;

        // Penetrating by 0.01: cx = 100 + 12 - 0.01 = 111.99 -> dx = 11.99 < 12.0
        assertTrue(CollisionUtils.checkCircleVsAABB(111.99f, 50.0f, r, 0.0f, 0.0f, 100.0f, 100.0f));

        // Tangent: cx = 100 + 12 = 112.0 -> dx = 12.0. 144 < 144 is FALSE!
        assertFalse(CollisionUtils.checkCircleVsAABB(112.0f, 50.0f, r, 0.0f, 0.0f, 100.0f, 100.0f),
                "Circle tangent to edge must report false (C++ TileMap.cpp:162 parity)");

        // Separated by 0.01: cx = 112.01 -> dx = 12.01 > 12.0
        assertFalse(CollisionUtils.checkCircleVsAABB(112.01f, 50.0f, r, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    @Test
    @DisplayName("Challenge 2.5: Zero-radius circle vs AABB (C++ parity)")
    void testCircleVsAABB_ZeroRadius() {
        // In C++ TileMap.cpp: (dx*dx + dy*dy) < (0*0) is impossible, always false.
        // In Java CollisionUtils: if (r <= 0.0f) return false.
        assertFalse(CollisionUtils.checkCircleVsAABB(50.0f, 50.0f, 0.0f, 0.0f, 0.0f, 100.0f, 100.0f));
        assertFalse(CollisionUtils.checkCircleVsAABB(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 100.0f, 100.0f));
        assertFalse(CollisionUtils.checkCircleVsAABB(-10.0f, 50.0f, 0.0f, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    // =========================================================================
    // Challenge Dimension 3: 2-Pass Sliding Resolution
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: Moving diagonally into horizontal wall must slide along X")
    void testResolveSliding_DiagonalIntoHorizontalWall() {
        // Horizontal wall obstacle blocking Y in [200, 300]
        float rx = 0.0f, ry = 200.0f, rw = 500.0f, rh = 100.0f;
        float radius = 10.0f;

        // Player starts at (100, 185) (safe, clearance = 15 > 10)
        // Desired move is diagonally down-right to (120, 205) -> penetrates top wall edge
        Vector2D oldPos = new Vector2D(100.0f, 185.0f);
        Vector2D desired = new Vector2D(120.0f, 205.0f);

        // Test with single AABB resolver
        Vector2D resolvedAABB = CollisionUtils.resolveCircleVsAABBSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, radius, rx, ry, rw, rh
        );
        assertEquals(120.0f, resolvedAABB.x, EPSILON, "X movement must be preserved");
        assertEquals(185.0f, resolvedAABB.y, EPSILON, "Y movement must be canceled");
        assertFalse(CollisionUtils.checkCircleVsAABB(resolvedAABB, radius, rx, ry, rw, rh));

        // Test with higher-order 2-pass resolver
        CollisionUtils.TraversabilityPredicate wallPredicate = (x, y, r) ->
                !CollisionUtils.checkCircleVsAABB(x, y, r, rx, ry, rw, rh);

        Vector2D resolvedHigher = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, radius, 4096.0f, 4096.0f, wallPredicate
        );
        assertEquals(120.0f, resolvedHigher.x, EPSILON, "X movement must be preserved in higher-order sliding");
        assertEquals(185.0f, resolvedHigher.y, EPSILON, "Y movement must be canceled in higher-order sliding");
        assertTrue(wallPredicate.isTraversable(resolvedHigher.x, resolvedHigher.y, radius));
    }

    @Test
    @DisplayName("Challenge 3.2: Moving diagonally into vertical wall must slide along Y")
    void testResolveSliding_DiagonalIntoVerticalWall() {
        // Vertical wall obstacle blocking X in [200, 300]
        float rx = 200.0f, ry = 0.0f, rw = 100.0f, rh = 500.0f;
        float radius = 10.0f;

        // Player starts at (185, 100) (safe, clearance = 15 > 10)
        // Desired move is diagonally right-down to (205, 120) -> penetrates left wall edge
        Vector2D oldPos = new Vector2D(185.0f, 100.0f);
        Vector2D desired = new Vector2D(205.0f, 120.0f);

        // Test with single AABB resolver
        Vector2D resolvedAABB = CollisionUtils.resolveCircleVsAABBSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, radius, rx, ry, rw, rh
        );
        assertEquals(185.0f, resolvedAABB.x, EPSILON, "X movement must be canceled");
        assertEquals(120.0f, resolvedAABB.y, EPSILON, "Y movement must be preserved");
        assertFalse(CollisionUtils.checkCircleVsAABB(resolvedAABB, radius, rx, ry, rw, rh));

        // Test with higher-order 2-pass resolver
        CollisionUtils.TraversabilityPredicate wallPredicate = (x, y, r) ->
                !CollisionUtils.checkCircleVsAABB(x, y, r, rx, ry, rw, rh);

        Vector2D resolvedHigher = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, radius, 4096.0f, 4096.0f, wallPredicate
        );
        assertEquals(185.0f, resolvedHigher.x, EPSILON, "X movement must be canceled in higher-order sliding");
        assertEquals(120.0f, resolvedHigher.y, EPSILON, "Y movement must be preserved in higher-order sliding");
        assertTrue(wallPredicate.isTraversable(resolvedHigher.x, resolvedHigher.y, radius));
    }

    @Test
    @DisplayName("Challenge 3.3: Moving directly into an internal (concave) corner halts both axes")
    void testResolveSliding_InternalCorner_BlockedBothAxes() {
        // Internal corner: impassable if X >= 200 OR Y >= 200
        // Walkable quadrant is X < 200 AND Y < 200
        float cornerX = 200.0f;
        float cornerY = 200.0f;
        float radius = 10.0f;

        // Effective walkable boundary for circle center is x <= 200 - 10 = 190, y <= 190
        CollisionUtils.TraversabilityPredicate cornerPredicate = (x, y, r) -> (x + r <= cornerX) && (y + r <= cornerY);

        // Entity is wedged in the corner at (190, 190)
        Vector2D oldPos = new Vector2D(190.0f, 190.0f);
        assertTrue(cornerPredicate.isTraversable(oldPos.x, oldPos.y, radius));

        // Moving diagonally into the corner towards (195, 195)
        Vector2D desired = new Vector2D(195.0f, 195.0f);

        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, radius, 4096.0f, 4096.0f, cornerPredicate
        );

        // Step 1: candidate (195, 195) -> blocked
        // Step 2: X-only (195, 190) -> blocked on X
        // Step 3: Y-only (190, 195) -> blocked on Y
        // Step 4: fallback to oldPos (190, 190)
        assertEquals(190.0f, resolved.x, EPSILON, "Must retain oldPos.x when wedged in internal corner");
        assertEquals(190.0f, resolved.y, EPSILON, "Must retain oldPos.y when wedged in internal corner");
        assertTrue(cornerPredicate.isTraversable(resolved.x, resolved.y, radius));
    }

    @Test
    @DisplayName("Challenge 3.4: Moving within traversable space moves freely to candidate position")
    void testResolveSliding_UnobstructedMovement() {
        CollisionUtils.TraversabilityPredicate openPredicate = (x, y, r) -> true;

        Vector2D oldPos = new Vector2D(500.0f, 500.0f);
        Vector2D desired = new Vector2D(535.4f, 482.1f);
        float radius = 16.0f;

        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, radius, 4096.0f, 4096.0f, openPredicate
        );

        assertEquals(535.4f, resolved.x, EPSILON);
        assertEquals(482.1f, resolved.y, EPSILON);
    }

    @Test
    @DisplayName("Challenge 3.5: Null traversability predicate fails open safely")
    void testResolveSliding_NullPredicateSafety() {
        Vector2D resolved = CollisionUtils.resolveSliding(10.0f, 10.0f, 50.0f, 50.0f, 10.0f, 4096.0f, 4096.0f, null);
        assertEquals(50.0f, resolved.x, EPSILON);
        assertEquals(50.0f, resolved.y, EPSILON);
    }

    @Test
    @DisplayName("Challenge 3.6: World boundary clamping under sliding resolution")
    void testResolveSliding_BoundaryClamping() {
        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> true;
        float r = 20.0f;
        float mapSize = 4096.0f;

        // Moving outside left boundary: desired = (-10, 500)
        Vector2D resLeft = CollisionUtils.resolveSliding(100.0f, 500.0f, -10.0f, 500.0f, r, mapSize, mapSize, checker);
        assertEquals(r, resLeft.x, EPSILON, "Must clamp to radius on left boundary");
        assertEquals(500.0f, resLeft.y, EPSILON);

        // Moving outside bottom-right boundary: desired = (5000, 5000)
        Vector2D resBR = CollisionUtils.resolveSliding(4000.0f, 4000.0f, 5000.0f, 5000.0f, r, mapSize, mapSize, checker);
        assertEquals(mapSize - r, resBR.x, EPSILON, "Must clamp to mapW - radius");
        assertEquals(mapSize - r, resBR.y, EPSILON, "Must clamp to mapH - radius");
    }

    // =========================================================================
    // Challenge Dimension 4: Exact C++ testTileMapCollisionSliding Parity
    // =========================================================================

    @Test
    @DisplayName("Challenge 4: Exact parity with C++ testTileMapCollisionSliding (test_world_camera.cpp:97-117)")
    void testExactTileMapCollisionSlidingParity() {
        // Large rock at (2000, 2000) with collisionOffset=(0, -6) -> colCenter=(2000, 1994), radius=14
        final float rockCenterX = 2000.0f;
        final float rockCenterY = 1994.0f;
        final float rockRadius = 14.0f;
        final float entityRadius = 10.0f;

        // In C++ TileMap.cpp:176:
        // if (math::distanceSq(pos, colCenter) < totalRadius * totalRadius) return false;
        // Traversable means distanceSq >= totalRadius^2
        final float totalRadius = rockRadius + entityRadius; // 24.0
        final float totalRadiusSq = totalRadius * totalRadius; // 576.0

        CollisionUtils.TraversabilityPredicate cPlusPlusTraversable = (x, y, r) -> {
            float dx = x - rockCenterX;
            float dy = y - rockCenterY;
            return (dx * dx + dy * dy) >= totalRadiusSq;
        };

        // C++ test line 106: const sf::Vector2f oldPos(2030.f, 1994.f);
        Vector2D oldPos = new Vector2D(2030.0f, 1994.0f);
        assertTrue(cPlusPlusTraversable.isTraversable(oldPos.x, oldPos.y, entityRadius),
                "Initial position (2030, 1994) must be traversable in C++ test");

        // C++ test line 110: const sf::Vector2f desiredPos(2010.f, 1999.f);
        Vector2D desiredPos = new Vector2D(2010.0f, 1999.0f);

        // Run resolution
        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desiredPos.x, desiredPos.y,
                entityRadius, 4096.0f, 4096.0f, cPlusPlusTraversable
        );

        // C++ test lines 114-116:
        // EXPECT_EQ(resolved.x, oldPos.x); -> 2030.f
        // EXPECT_EQ(resolved.y, desiredPos.y); -> 1999.f
        // EXPECT_TRUE(map.isTraversable(resolved, r));
        assertEquals(oldPos.x, resolved.x, EPSILON, "Parity check: resolved.x == oldPos.x (2030.0f)");
        assertEquals(desiredPos.y, resolved.y, EPSILON, "Parity check: resolved.y == desiredPos.y (1999.0f)");
        assertTrue(cPlusPlusTraversable.isTraversable(resolved.x, resolved.y, entityRadius),
                "Parity check: resolved position must be traversable");
    }

    // =========================================================================
    // Challenge Dimension 5: Fuzz / Stress Generator (10,000 iterations)
    // =========================================================================

    @Test
    @DisplayName("Challenge 5: 10,000 Iteration Monte Carlo Fuzz Stress Generator")
    void testMonteCarloFuzzStressSliding() {
        // Environment with 5 circular obstacles and 5 rectangular obstacles
        float[][] circles = {
                {100.0f, 100.0f, 25.0f},
                {300.0f, 150.0f, 35.0f},
                {500.0f, 500.0f, 50.0f},
                {800.0f, 200.0f, 20.0f},
                {250.0f, 750.0f, 40.0f}
        };

        float[][] boxes = {
                {50.0f, 200.0f, 60.0f, 60.0f},
                {400.0f, 100.0f, 100.0f, 40.0f},
                {600.0f, 600.0f, 120.0f, 80.0f},
                {150.0f, 400.0f, 50.0f, 150.0f},
                {700.0f, 300.0f, 80.0f, 80.0f}
        };

        float mapW = 1000.0f;
        float mapH = 1000.0f;

        CollisionUtils.TraversabilityPredicate complexEnv = (x, y, r) -> {
            for (float[] c : circles) {
                float dx = x - c[0];
                float dy = y - c[1];
                float rSum = r + c[2];
                if (dx * dx + dy * dy < rSum * rSum) {
                    return false;
                }
            }
            for (float[] b : boxes) {
                if (CollisionUtils.checkCircleVsAABB(x, y, r, b[0], b[1], b[2], b[3])) {
                    return false;
                }
            }
            return true;
        };

        Random rng = new Random(1337);
        int iterations = 10_000;

        for (int i = 0; i < iterations; i++) {
            float radius = 5.0f + rng.nextFloat() * 20.0f; // radius in [5, 25]

            // Find valid random starting position
            float oldX = 0, oldY = 0;
            boolean validStart = false;
            for (int attempt = 0; attempt < 50; attempt++) {
                oldX = radius + rng.nextFloat() * (mapW - 2 * radius);
                oldY = radius + rng.nextFloat() * (mapH - 2 * radius);
                if (complexEnv.isTraversable(oldX, oldY, radius)) {
                    validStart = true;
                    break;
                }
            }
            if (!validStart) continue;

            // Generate desired displacement in random direction with speed [0, 50]
            float angle = rng.nextFloat() * (float) (2.0 * Math.PI);
            float dist = rng.nextFloat() * 50.0f;
            float newX = oldX + (float) Math.cos(angle) * dist;
            float newY = oldY + (float) Math.sin(angle) * dist;

            Vector2D resolved = CollisionUtils.resolveSliding(oldX, oldY, newX, newY, radius, mapW, mapH, complexEnv);

            // Invariants that MUST NEVER be violated:
            // 1. Result must not be NaN or Infinite
            assertFalse(Float.isNaN(resolved.x) || Float.isNaN(resolved.y), "Result coordinates must not be NaN");
            assertFalse(Float.isInfinite(resolved.x) || Float.isInfinite(resolved.y), "Result coordinates must not be Infinite");

            // 2. Result must respect world bounds [radius, mapW - radius] x [radius, mapH - radius]
            assertTrue(resolved.x >= radius - HIGH_PRECISION_EPS, "Resolved X must not tunnel outside world left bound");
            assertTrue(resolved.x <= mapW - radius + HIGH_PRECISION_EPS, "Resolved X must not tunnel outside world right bound");
            assertTrue(resolved.y >= radius - HIGH_PRECISION_EPS, "Resolved Y must not tunnel outside world top bound");
            assertTrue(resolved.y <= mapH - radius + HIGH_PRECISION_EPS, "Resolved Y must not tunnel outside world bottom bound");

            // 3. Resolved position must be traversable (or retain old position if cornered)
            boolean isTraversable = complexEnv.isTraversable(resolved.x, resolved.y, radius);
            boolean isOldPos = Math.abs(resolved.x - oldX) < EPSILON && Math.abs(resolved.y - oldY) < EPSILON;
            assertTrue(isTraversable || isOldPos,
                    "Resolved position must be traversable in environment, or safely retained at old position");
        }
    }
}
