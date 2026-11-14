package com.spellstorm;

import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for {@link CollisionUtils}.
 * Covers Feature 7 across Tiers 1, 2, 3, and 4 per TEST_INFRA.md and M2 Explorer blueprint.
 */
class CollisionUtilsTest {

    private static final float TOLERANCE = 1e-4f;

    // =========================================================================
    // Tier 1: Circle vs Circle Equivalence Classes
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Disjoint circles do not overlap")
    void testCircleVsCircle_Disjoint() {
        // Distance = 100, r1 + r2 = 20 + 20 = 40
        assertFalse(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 20.0f, 100.0f, 0.0f, 20.0f));
        assertNull(CollisionUtils.getCircleVsCirclePenetration(0.0f, 0.0f, 20.0f, 100.0f, 0.0f, 20.0f));
    }

    @Test
    @DisplayName("Tier 1: Intersecting circles overlap with correct penetration")
    void testCircleVsCircle_Intersecting() {
        // Centers at (100, 100) and (120, 100), distance = 20, r1 = 15, r2 = 15, rSum = 30
        assertTrue(CollisionUtils.checkCircleVsCircle(100.0f, 100.0f, 15.0f, 120.0f, 100.0f, 15.0f));

        CollisionUtils.PenetrationResult pen = CollisionUtils.getCircleVsCirclePenetration(
                100.0f, 100.0f, 15.0f, 120.0f, 100.0f, 15.0f
        );
        assertNotNull(pen);
        assertEquals(10.0f, pen.depth(), TOLERANCE);
        // Separation normal pointing from C2 (120, 100) to C1 (100, 100) -> (-1.0, 0.0)
        assertEquals(-1.0f, pen.normalX(), TOLERANCE);
        assertEquals(0.0f, pen.normalY(), TOLERANCE);

        Vector2D push = pen.pushVector();
        assertEquals(-10.0f, push.x, TOLERANCE);
        assertEquals(0.0f, push.y, TOLERANCE);
    }

    // =========================================================================
    // Tier 1: Circle vs AABB Equivalence Classes
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Circle center inside AABB")
    void testCircleVsAABB_CenterInside() {
        // AABB at [10, 10, 50, 50], Circle at (25, 25, r=10)
        assertTrue(CollisionUtils.checkCircleVsAABB(25.0f, 25.0f, 10.0f, 10.0f, 10.0f, 50.0f, 50.0f));
    }

    @Test
    @DisplayName("Tier 1: Circle overlapping AABB facing edge")
    void testCircleVsAABB_FacingEdge_Overlap() {
        // AABB [0, 0, 100, 100], Circle at (105, 50, r=10) -> penetrates right edge by 5 units
        assertTrue(CollisionUtils.checkCircleVsAABB(105.0f, 50.0f, 10.0f, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    @Test
    @DisplayName("Tier 1: Circle disjoint outside AABB facing edge")
    void testCircleVsAABB_FacingEdge_Disjoint() {
        // AABB [0, 0, 100, 100], Circle at (115, 50, r=10) -> gap of 5 units
        assertFalse(CollisionUtils.checkCircleVsAABB(115.0f, 50.0f, 10.0f, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    @Test
    @DisplayName("Tier 1: Circle overlapping AABB diagonal corner region")
    void testCircleVsAABB_CornerRegion_Overlap() {
        // AABB [0, 0, 100, 100], corner vertex at (100, 100).
        // Circle at (103, 104, r=6). Distance to corner is sqrt(3^2 + 4^2) = 5.0 < 6.0
        assertTrue(CollisionUtils.checkCircleVsAABB(103.0f, 104.0f, 6.0f, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    @Test
    @DisplayName("Tier 1: Circle disjoint outside AABB diagonal corner region")
    void testCircleVsAABB_CornerRegion_Disjoint() {
        // Circle at (105, 105, r=5). Distance to corner is sqrt(25+25) = 7.07 > 5.0
        assertFalse(CollisionUtils.checkCircleVsAABB(105.0f, 105.0f, 5.0f, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    // =========================================================================
    // Tier 1: Point & Box Primitive Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Point vs AABB queries")
    void testPointVsAABB() {
        // AABB [10, 10, 40, 40]
        assertTrue(CollisionUtils.checkPointVsAABB(20.0f, 20.0f, 10.0f, 10.0f, 40.0f, 40.0f));
        assertTrue(CollisionUtils.checkPointVsAABB(10.0f, 10.0f, 10.0f, 10.0f, 40.0f, 40.0f)); // on boundary
        assertTrue(CollisionUtils.checkPointVsAABB(50.0f, 50.0f, 10.0f, 10.0f, 40.0f, 40.0f)); // on boundary
        assertFalse(CollisionUtils.checkPointVsAABB(5.0f, 20.0f, 10.0f, 10.0f, 40.0f, 40.0f));
        assertFalse(CollisionUtils.checkPointVsAABB(55.0f, 20.0f, 10.0f, 10.0f, 40.0f, 40.0f));
    }

    @Test
    @DisplayName("Tier 1: Point vs Circle queries")
    void testPointVsCircle() {
        // Circle (100, 100, r=20)
        assertTrue(CollisionUtils.checkPointVsCircle(100.0f, 100.0f, 100.0f, 100.0f, 20.0f));
        assertTrue(CollisionUtils.checkPointVsCircle(120.0f, 100.0f, 100.0f, 100.0f, 20.0f)); // perimeter
        assertFalse(CollisionUtils.checkPointVsCircle(121.0f, 100.0f, 100.0f, 100.0f, 20.0f));
    }

    @Test
    @DisplayName("Tier 1: AABB vs AABB overlap queries")
    void testAABBVsAABB() {
        assertTrue(CollisionUtils.checkAABBVsAABB(0.0f, 0.0f, 10.0f, 10.0f, 5.0f, 5.0f, 10.0f, 10.0f));
        assertFalse(CollisionUtils.checkAABBVsAABB(0.0f, 0.0f, 10.0f, 10.0f, 20.0f, 20.0f, 10.0f, 10.0f));
    }

    // =========================================================================
    // Tier 1: Sliding Collision Equivalence Classes
    // =========================================================================

    @Test
    @DisplayName("Tier 1: Unobstructed sliding resolution accepts full movement")
    void testResolveSliding_Unobstructed() {
        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> true;

        Vector2D resolved = CollisionUtils.resolveSliding(
                100.0f, 100.0f, 110.0f, 110.0f, 15.0f, 4096.0f, 4096.0f, checker
        );
        assertEquals(110.0f, resolved.x, TOLERANCE);
        assertEquals(110.0f, resolved.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 1: Moving into horizontal wall slides along X axis")
    void testResolveSliding_HorizontalWall() {
        // Horizontal wall blocking Y >= 100.0f
        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> y <= 100.0f;

        Vector2D oldPos = new Vector2D(50.0f, 100.0f);
        Vector2D desired = new Vector2D(60.0f, 105.0f); // moves Right and Down (into wall)

        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, 10.0f, 4096.0f, 4096.0f, checker
        );
        assertEquals(60.0f, resolved.x, TOLERANCE, "X movement must be preserved");
        assertEquals(100.0f, resolved.y, TOLERANCE, "Y movement must be canceled");
    }

    @Test
    @DisplayName("Tier 1: Moving into vertical wall slides along Y axis")
    void testResolveSliding_VerticalWall() {
        // Vertical wall blocking X >= 100.0f
        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> x <= 100.0f;

        Vector2D oldPos = new Vector2D(100.0f, 50.0f);
        Vector2D desired = new Vector2D(105.0f, 60.0f); // moves Right (into wall) and Down

        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, 10.0f, 4096.0f, 4096.0f, checker
        );
        assertEquals(100.0f, resolved.x, TOLERANCE, "X movement must be canceled");
        assertEquals(60.0f, resolved.y, TOLERANCE, "Y movement must be preserved");
    }

    // =========================================================================
    // Tier 2: Boundary Value Tests
    // =========================================================================

    @Test
    @DisplayName("Tier 2: Tangent circles touch and report collision (exact C++ distSq <= rSum^2)")
    void testCircleVsCircle_Tangent() {
        // Centers at (0, 0) and (20, 0), r1 = 10, r2 = 10 -> dist = 20 == rSum
        assertTrue(CollisionUtils.checkCircleVsCircle(0.0f, 0.0f, 10.0f, 20.0f, 0.0f, 10.0f));

        CollisionUtils.PenetrationResult pen = CollisionUtils.getCircleVsCirclePenetration(
                0.0f, 0.0f, 10.0f, 20.0f, 0.0f, 10.0f
        );
        assertNotNull(pen);
        assertEquals(0.0f, pen.depth(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Concentric circles overlap with fallback normal")
    void testCircleVsCircle_Concentric() {
        // Centers identical (50, 50), r1 = 10, r2 = 20
        assertTrue(CollisionUtils.checkCircleVsCircle(50.0f, 50.0f, 10.0f, 50.0f, 50.0f, 20.0f));

        CollisionUtils.PenetrationResult pen = CollisionUtils.getCircleVsCirclePenetration(
                50.0f, 50.0f, 10.0f, 50.0f, 50.0f, 20.0f
        );
        assertNotNull(pen);
        assertEquals(30.0f, pen.depth(), TOLERANCE);
        // Fallback unit normal
        assertEquals(1.0f, pen.normalX(), TOLERANCE);
        assertEquals(0.0f, pen.normalY(), TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Circle vs AABB tangent edge uses strict inequality (distSq < r^2 -> false)")
    void testCircleVsAABB_TangentEdge() {
        // AABB [0, 0, 100, 100], Circle at (110, 50, r=10).
        // Distance to right edge = 10 == r.
        // Under strict inequality (dx*dx < r*r), 100 < 100 is FALSE, exactly matching C++ TileMap.cpp:162!
        assertFalse(CollisionUtils.checkCircleVsAABB(110.0f, 50.0f, 10.0f, 0.0f, 0.0f, 100.0f, 100.0f));
    }

    @Test
    @DisplayName("Tier 2: Blocked corner halts movement on both axes")
    void testResolveSliding_BlockedCorner() {
        // Corner where both X and Y movements are blocked
        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> x <= 100.0f && y <= 100.0f;

        Vector2D oldPos = new Vector2D(100.0f, 100.0f);
        Vector2D desired = new Vector2D(105.0f, 105.0f);

        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desired.x, desired.y, 10.0f, 4096.0f, 4096.0f, checker
        );
        assertEquals(100.0f, resolved.x, TOLERANCE, "Movement halted at corner on X");
        assertEquals(100.0f, resolved.y, TOLERANCE, "Movement halted at corner on Y");
    }

    @Test
    @DisplayName("Tier 2: World boundary clamping prevents entity tunneling outside map")
    void testResolveSliding_WorldBoundClamping() {
        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> true;

        float radius = 20.0f; // Player radius
        // Trying to move past top-left boundary (-50, -50)
        Vector2D resolved = CollisionUtils.resolveSliding(
                25.0f, 25.0f, -50.0f, -50.0f, radius, 4096.0f, 4096.0f, checker
        );
        assertEquals(radius, resolved.x, TOLERANCE);
        assertEquals(radius, resolved.y, TOLERANCE);

        // Trying to move past bottom-right boundary (5000, 5000)
        resolved = CollisionUtils.resolveSliding(
                4000.0f, 4000.0f, 5000.0f, 5000.0f, radius, 4096.0f, 4096.0f, checker
        );
        assertEquals(4096.0f - radius, resolved.x, TOLERANCE);
        assertEquals(4096.0f - radius, resolved.y, TOLERANCE);
    }

    @Test
    @DisplayName("Tier 2: Single obstacle AABB sliding resolution")
    void testResolveCircleVsAABBSliding() {
        // Obstacle AABB [100, 100, 50, 50]
        float rx = 100.0f, ry = 100.0f, rw = 50.0f, rh = 50.0f;
        float r = 10.0f;

        // Desired position penetrates top edge: moving from (80, 80) to (120, 105)
        Vector2D resolved = CollisionUtils.resolveCircleVsAABBSliding(
                80.0f, 80.0f, 120.0f, 105.0f, r, rx, ry, rw, rh
        );
        // Desired is blocked because circle at (120, 105) overlaps [100, 100, 50, 50].
        // Try X-only: (120, 80). Distance to top edge (120, 100) is 20 > r=10. Clear!
        assertEquals(120.0f, resolved.x, TOLERANCE);
        assertEquals(80.0f, resolved.y, TOLERANCE);
    }

    // =========================================================================
    // Tier 3: Reference Porting & Combat Collision Verifications
    // =========================================================================

    @Test
    @DisplayName("Tier 3 Port: C++ testTileMapCollisionSliding (Large Rock at 2000, 2000)")
    void testTileMapCollisionSliding_ReferencePort() {
        // Port of C++ test_world_camera.cpp:97-117
        // Large rock at (2000, 2000), collisionOffset = (0, -6), collisionRadius = 14
        float rockCenterX = 2000.0f;
        float rockCenterY = 1994.0f;
        float rockRadius = 14.0f;
        float entityRadius = 10.0f;

        CollisionUtils.TraversabilityPredicate checker = (x, y, r) -> {
            // Unobstructed if distance to rock center >= r + rockRadius
            return !CollisionUtils.checkCircleVsCircle(x, y, r, rockCenterX, rockCenterY, rockRadius);
        };

        // Entity is directly to the right of the rock at (2030, 1994)
        Vector2D oldPos = new Vector2D(2030.0f, 1994.0f);
        assertTrue(checker.isTraversable(oldPos.x, oldPos.y, entityRadius));

        // Desired diagonal movement: left towards rock (blocked) and slightly down (clear)
        Vector2D desiredPos = new Vector2D(2010.0f, 1999.0f);
        Vector2D resolved = CollisionUtils.resolveSliding(
                oldPos.x, oldPos.y, desiredPos.x, desiredPos.y,
                entityRadius, 4096.0f, 4096.0f, checker
        );

        // X should be held at oldPos.x (2030.0f), Y should slide to desiredPos.y (1999.0f)
        assertEquals(2030.0f, resolved.x, TOLERANCE, "X movement into rock must be blocked and retained at oldPos.x");
        assertEquals(1999.0f, resolved.y, TOLERANCE, "Y movement must slide smoothly to desiredPos.y");
        assertTrue(checker.isTraversable(resolved.x, resolved.y, entityRadius));
    }

    @Test
    @DisplayName("Tier 3 Port: Player projectile vs MeleeEnemy combat collision (test_simulation.cpp:295)")
    void testCombatCollision_PlayerProjVsEnemy() {
        // Player missile at (295, 300, r=8) vs MeleeEnemy at (300, 300, r=16)
        // Distance = 5.0, sum of radii = 24.0 -> Hit!
        Vector2D projPos = new Vector2D(295.0f, 300.0f);
        float projRadius = 8.0f;

        Vector2D enemyPos = new Vector2D(300.0f, 300.0f);
        float enemyRadius = 16.0f;

        assertTrue(CollisionUtils.checkCircleVsCircle(projPos, projRadius, enemyPos, enemyRadius));
    }

    @Test
    @DisplayName("Tier 3 Port: Enemy bolt vs Player combat collision (test_simulation.cpp:328)")
    void testCombatCollision_EnemyProjVsPlayer() {
        // Enemy bolt at (495, 300, r=7) vs Player at (500, 300, r=20)
        // Distance = 5.0, sum of radii = 27.0 -> Hit!
        Vector2D boltPos = new Vector2D(495.0f, 300.0f);
        float boltRadius = 7.0f;

        Vector2D playerPos = new Vector2D(500.0f, 300.0f);
        float playerRadius = 20.0f;

        assertTrue(CollisionUtils.checkCircleVsCircle(boltPos, boltRadius, playerPos, playerRadius));
    }

    @Test
    @DisplayName("Tier 3: EXP gem pickup magnet and collection interaction")
    void testPickupCollision_MagnetAndCollect() {
        Vector2D playerPos = new Vector2D(100.0f, 100.0f);
        float collectRadius = 45.0f;
        float magnetRadius = 126.0f; // 45 * 2.8

        Vector2D gemPos = new Vector2D(100.0f, 180.0f); // distance = 80
        float gemRadius = 8.0f;

        // At distance 80: outside collect radius (45+8=53), but inside magnet radius (126+8=134)
        assertFalse(CollisionUtils.checkCircleVsCircle(playerPos, collectRadius, gemPos, gemRadius));
        assertTrue(CollisionUtils.checkCircleVsCircle(playerPos, magnetRadius, gemPos, gemRadius));

        // When gem moves close to player (distance 30)
        gemPos.set(100.0f, 130.0f);
        assertTrue(CollisionUtils.checkCircleVsCircle(playerPos, collectRadius, gemPos, gemRadius));
    }

    @Test
    @DisplayName("Tier 3: World Power-Up pickup collision radius (r=18 + r=45 = 63)")
    void testPowerUpCollision_RadiusTotal() {
        Vector2D playerPos = new Vector2D(200.0f, 200.0f);
        float playerPickupRadius = 45.0f;
        float powerUpRadius = 18.0f; // total = 63.0f

        // Distance = 60.0f <= 63.0f -> Collides!
        Vector2D near = new Vector2D(200.0f, 260.0f);
        assertTrue(CollisionUtils.checkCircleVsCircle(playerPos, playerPickupRadius, near, powerUpRadius));

        // Distance = 65.0f > 63.0f -> Clear
        Vector2D far = new Vector2D(200.0f, 265.0f);
        assertFalse(CollisionUtils.checkCircleVsCircle(playerPos, playerPickupRadius, far, powerUpRadius));
    }

    @Test
    @DisplayName("Tier 3: Boss clearance on 5-tile river bridge (r=50 on 160-wide bridge)")
    void testBossBridgeClearance_Radius50() {
        // Bridge spans Y from [960, 1120] (height = 160), X is along bridge crossing
        // Boss is centered on bridge at Y = 1040, radius = 50
        float bridgeTop = 960.0f;
        float bridgeBottom = 1120.0f;
        float bossY = 1040.0f;
        float bossRadius = 50.0f;

        // Top edge clearance: 1040 - 50 = 990 >= 960
        assertTrue(bossY - bossRadius >= bridgeTop, "Boss must fit within top bridge boundary");
        // Bottom edge clearance: 1040 + 50 = 1090 <= 1120
        assertTrue(bossY + bossRadius <= bridgeBottom, "Boss must fit within bottom bridge boundary");
    }

    @Test
    @DisplayName("Tier 4: Spatial bounds clamping utility")
    void testClampToBounds() {
        Vector2D clamped = CollisionUtils.clampToBounds(-10.0f, 5000.0f, 20.0f, 0.0f, 0.0f, 4096.0f, 4096.0f);
        assertEquals(20.0f, clamped.x, TOLERANCE);
        assertEquals(4096.0f - 20.0f, clamped.y, TOLERANCE);
    }
}
