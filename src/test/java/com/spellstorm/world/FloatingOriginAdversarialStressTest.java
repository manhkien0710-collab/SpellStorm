package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.CombatSystem;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ExperienceSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Adversarial Stress Test: FloatingOriginManager Invariants & Trajectory Preservation")
public class FloatingOriginAdversarialStressTest {

    private FloatingOriginManager originManager;
    private ChunkManager chunkManager;
    private InfiniteWorld world;

    @BeforeEach
    void setUp() {
        originManager = new FloatingOriginManager();
        chunkManager = new ChunkManagerImpl(987654321L, 64);
        world = new InfiniteWorld(chunkManager, originManager);
    }

    // =========================================================================
    // Objective 1: Multi-shift stress
    // =========================================================================

    @Test
    @DisplayName("Challenge 1: Multi-shift stress across alternating large and small shifts preserves relative distances to float precision limits")
    void testMultiShiftStressPreservesRelativeDistances() {
        Player player = new Player(new Vector2D(100.0f, 150.0f));
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setClampingEnabled(false);
        camera.setCenter(100.0f, 150.0f);

        CombatSystem combat = new CombatSystem();
        ExperienceSystem expSystem = new ExperienceSystem();

        originManager.registerListener(player);
        originManager.registerListener(camera);
        originManager.registerListener(combat);
        originManager.registerListener(expSystem);

        List<Enemy> enemies = new ArrayList<>();
        Random rng = new Random(1337);
        for (int i = 0; i < 30; i++) {
            float ex = 100.0f + (rng.nextFloat() - 0.5f) * 1500.0f;
            float ey = 150.0f + (rng.nextFloat() - 0.5f) * 1500.0f;
            Enemy enemy = (i % 2 == 0) ? new MeleeEnemy(new Vector2D(ex, ey)) : new RangedEnemy(new Vector2D(ex, ey));
            enemies.add(enemy);
            originManager.registerListener(enemy);
        }

        // Spawn pickups
        for (int i = 0; i < 20; i++) {
            float px = 100.0f + (rng.nextFloat() - 0.5f) * 1000.0f;
            float py = 150.0f + (rng.nextFloat() - 0.5f) * 1000.0f;
            expSystem.spawnPickup(px, py, 10);
        }

        // Spawn active projectiles
        for (int i = 0; i < 15; i++) {
            Vector2D origin = new Vector2D(100.0f + i * 10.0f, 150.0f - i * 10.0f);
            Vector2D dir = new Vector2D((float) Math.cos(i), (float) Math.sin(i)).nor();
            Projectile p = new Projectile(origin, dir, 350.0f, 6.0f, 20.0f, 3.0f, 1, ProjectileFaction.PLAYER, null, null);
            combat.addProjectile(p);
        }

        // Record initial pairwise distances between player and all entities
        float[] playerToEnemyDist = new float[enemies.size()];
        for (int i = 0; i < enemies.size(); i++) {
            playerToEnemyDist[i] = player.getPosition().dist(enemies.get(i).getPosition());
        }

        float[] playerToPickupDist = new float[expSystem.getPickups().size()];
        for (int i = 0; i < expSystem.getPickups().size(); i++) {
            playerToPickupDist[i] = player.getPosition().dist(expSystem.getPickups().get(i).getPosition());
        }

        float[] playerToProjDist = new float[combat.getProjectiles().size()];
        for (int i = 0; i < combat.getProjectiles().size(); i++) {
            playerToProjDist[i] = player.getPosition().dist(combat.getProjectiles().get(i).getPosition());
        }

        float camToPlayerDist = new Vector2D(camera.getCenterX(), camera.getCenterY()).dist(player.getPosition());

        // Alternating shift sequence: (+1024, +2048), (-1024, -2048), (+50000, -30000), (-50000, +30000)
        long[][] shifts = new long[][]{
                {1024L, 2048L},
                {-1024L, -2048L},
                {50000L, -30000L},
                {-50000L, 30000L},
                {1024L, 0L},
                {0L, -2048L},
                {-1024L, 2048L}
        };

        for (int s = 0; s < shifts.length; s++) {
            long dx = shifts[s][0];
            long dy = shifts[s][1];
            originManager.rebaseWorldOrigin(dx, dy);

            // Precision tolerance: float ULP scales with coordinate magnitude.
            // When traversing through 50,000 px, ULP = 1/256 = 0.0039 px.
            // Hence distance invariance tolerance is bounded by 0.005f (5e-3f).
            float tol = 5e-3f;

            // Verify Camera to player distance
            float curCamDist = new Vector2D(camera.getCenterX(), camera.getCenterY()).dist(player.getPosition());
            assertEquals(camToPlayerDist, curCamDist, tol, "Camera to player distance must remain invariant on shift " + s);

            // Verify player to enemy distances
            for (int i = 0; i < enemies.size(); i++) {
                float d = player.getPosition().dist(enemies.get(i).getPosition());
                assertEquals(playerToEnemyDist[i], d, tol, "Player to enemy " + i + " distance must remain invariant on shift " + s);
            }

            // Verify player to pickup distances
            for (int i = 0; i < expSystem.getPickups().size(); i++) {
                float d = player.getPosition().dist(expSystem.getPickups().get(i).getPosition());
                assertEquals(playerToPickupDist[i], d, tol, "Player to pickup " + i + " distance must remain invariant on shift " + s);
            }

            // Verify player to projectile distances
            for (int i = 0; i < combat.getProjectiles().size(); i++) {
                float d = player.getPosition().dist(combat.getProjectiles().get(i).getPosition());
                assertEquals(playerToProjDist[i], d, tol, "Player to projectile " + i + " distance must remain invariant on shift " + s);
            }
        }

        // Stress: 500 consecutive chunk-aligned alternating shifts (+/- 1 to 5 chunks)
        // Reset origin first
        originManager.reset();
        for (int step = 0; step < 500; step++) {
            long rdx = (rng.nextInt(11) - 5) * 1024L;
            long rdy = (rng.nextInt(11) - 5) * 1024L;
            originManager.rebaseWorldOrigin(rdx, rdy);

            float tol = (Math.abs(originManager.getOriginShiftX()) > 10000L || Math.abs(originManager.getOriginShiftY()) > 10000L)
                    ? 5e-3f : 1e-3f;

            // Spot check player to first enemy distance
            float d = player.getPosition().dist(enemies.get(0).getPosition());
            assertEquals(playerToEnemyDist[0], d, tol, "Randomized shift step " + step + " failed distance invariant");
        }
    }

    // =========================================================================
    // Objective 2: Trajectory preservation
    // =========================================================================

    @Test
    @DisplayName("Challenge 2: Projectile trajectory across origin shift does not deflect, teleport, or alter intercept point")
    void testProjectileTrajectoryPreservationAcrossRebases() {
        // Setup projectile and stationary target
        Vector2D startPos = new Vector2D(100.0f, 200.0f);
        Vector2D targetPos = new Vector2D(600.0f, 200.0f);
        Vector2D dir = new Vector2D(1.0f, 0.0f); // pure right
        float speed = 500.0f; // px/sec
        float radius = 8.0f;
        float targetRadius = 16.0f;

        // Ground truth: Unshifted simulation
        int collisionFrameUnshifted = -1;
        Vector2D unshiftedCollisionPos = null;

        {
            Vector2D pPos = startPos.cpy();
            float dt = 1.0f / 60.0f;
            for (int frame = 0; frame < 120; frame++) {
                pPos.add(dir.x * speed * dt, dir.y * speed * dt);
                if (pPos.dist(targetPos) <= (radius + targetRadius)) {
                    collisionFrameUnshifted = frame;
                    unshiftedCollisionPos = pPos.cpy();
                    break;
                }
            }
        }
        assertTrue(collisionFrameUnshifted > 0, "Unshifted projectile must collide with target");

        // Now run shifted simulation with FloatingOriginManager and CombatSystem
        FloatingOriginManager simOrigin = new FloatingOriginManager();
        CombatSystem simCombat = new CombatSystem();
        Enemy targetEnemy = new MeleeEnemy(targetPos.cpy());
        targetEnemy.setRadius(targetRadius);

        Projectile testProj = new Projectile(startPos.cpy(), dir.cpy(), speed, radius, 25.0f, 5.0f, 1,
                ProjectileFaction.PLAYER, null, null);
        simCombat.addProjectile(testProj);

        simOrigin.registerListener(simCombat);
        simOrigin.registerListener(targetEnemy);

        int collisionFrameShifted = -1;
        Vector2D shiftedCollisionPosGlobal = null;

        float dt = 1.0f / 60.0f;
        for (int frame = 0; frame < 120; frame++) {
            // Apply origin shifts at arbitrary frames during flight, ending on a non-zero origin!
            // Frame 10: rebase by (+1024, +2048)
            // Frame 25: rebase by (-1024, -2048)
            // Frame 40: rebase by (+50000, -30000)
            if (frame == 10) {
                simOrigin.rebaseWorldOrigin(1024L, 2048L);
            } else if (frame == 25) {
                simOrigin.rebaseWorldOrigin(-1024L, -2048L);
            } else if (frame == 40) {
                simOrigin.rebaseWorldOrigin(50000L, -30000L);
            }

            // Projectile velocity must remain exactly (500.0f, 0.0f) — no deflection
            assertEquals(500.0f, testProj.getVelocity().x, 1e-4f, "Velocity X must not deflect");
            assertEquals(0.0f, testProj.getVelocity().y, 1e-4f, "Velocity Y must not deflect");

            // Advance simulation
            testProj.update(dt);

            // Check collision with targetEnemy
            if (testProj.getPosition().dist(targetEnemy.getPosition()) <= (radius + targetRadius)) {
                collisionFrameShifted = frame;
                // Reconstruct continuous global position
                shiftedCollisionPosGlobal = new Vector2D(
                        testProj.getPosition().x + (float) simOrigin.getOriginShiftX(),
                        testProj.getPosition().y + (float) simOrigin.getOriginShiftY()
                );
                break;
            }
        }

        assertEquals(collisionFrameUnshifted, collisionFrameShifted,
                "Projectile must intercept target at the exact same frame regardless of mid-flight origin shifts");
        assertNotNull(shiftedCollisionPosGlobal);
        // Intercept position tolerance: at magnitude 50,000 px, 17 frames of float integration has ~0.025 px delta
        assertEquals(unshiftedCollisionPos.x, shiftedCollisionPosGlobal.x, 0.05f,
                "Continuous global intercept position X must match unshifted intercept to float precision limits");
        assertEquals(unshiftedCollisionPos.y, shiftedCollisionPosGlobal.y, 0.05f,
                "Continuous global intercept position Y must match unshifted intercept to float precision limits");
    }

    // =========================================================================
    // Objective 3: ChunkManager originShift synchronization & Traversability
    // =========================================================================

    @Test
    @DisplayName("Challenge 3.1: ChunkManager originShiftX/Y synchronizes exactly with FloatingOriginManager across single and multiple rebases")
    void testChunkManagerOriginShiftSynchronization() {
        assertEquals(0L, chunkManager.getOriginShiftX());
        assertEquals(0L, chunkManager.getOriginShiftY());
        assertEquals(0L, originManager.getOriginShiftX());
        assertEquals(0L, originManager.getOriginShiftY());

        // Single rebase
        originManager.rebaseWorldOrigin(1024L, 2048L);
        assertEquals(1024L, originManager.getOriginShiftX());
        assertEquals(2048L, originManager.getOriginShiftY());
        assertEquals(1024L, chunkManager.getOriginShiftX());
        assertEquals(2048L, chunkManager.getOriginShiftY());

        // Alternating rebase
        originManager.rebaseWorldOrigin(-2048L, -1024L);
        assertEquals(-1024L, originManager.getOriginShiftX());
        assertEquals(1024L, originManager.getOriginShiftY());
        assertEquals(-1024L, chunkManager.getOriginShiftX());
        assertEquals(1024L, chunkManager.getOriginShiftY());

        // Large jump
        originManager.rebaseWorldOrigin(500000L, -300000L);
        assertEquals(-1024L + 500000L, originManager.getOriginShiftX());
        assertEquals(1024L - 300000L, originManager.getOriginShiftY());
        assertEquals(originManager.getOriginShiftX(), chunkManager.getOriginShiftX());
        assertEquals(originManager.getOriginShiftY(), chunkManager.getOriginShiftY());

        // Teleport via checkAndRebase
        Vector2D farPlayer = new Vector2D(5120.0f, -4096.0f);
        boolean shifted = originManager.checkAndRebase(farPlayer);
        assertTrue(shifted);
        assertEquals(originManager.getOriginShiftX(), chunkManager.getOriginShiftX());
        assertEquals(originManager.getOriginShiftY(), chunkManager.getOriginShiftY());
    }

    @Test
    @DisplayName("Challenge 3.2: isTraversable queries remain completely stable before and after rebase across various chunks and seams")
    void testIsTraversableStabilityAcrossRebases() {
        // Sample points across chunks (0,0), (-1,-1), (2,3), (-3, 1)
        float[][] testPoints = new float[][]{
                {100.0f, 100.0f},
                {1023.0f, 1023.0f},  // Seam near chunk boundary
                {1024.0f, 1024.0f},  // Exact chunk boundary vertex
                {1025.0f, 1025.0f},  // Inside chunk (1, 1)
                {-50.0f, -50.0f},    // Inside chunk (-1, -1)
                {-1024.0f, -1024.0f},// Negative vertex
                {2500.0f, 3200.0f},  // Chunk (2, 3)
                {-2500.0f, 1500.0f}  // Chunk (-3, 1)
        };

        float radius = 12.0f;

        // Query each point before rebase
        boolean[] traversableBefore = new boolean[testPoints.length];
        for (int i = 0; i < testPoints.length; i++) {
            traversableBefore[i] = world.isTraversable(testPoints[i][0], testPoints[i][1], radius);
        }

        // Perform multiple consecutive origin shifts
        long[][] shifts = new long[][]{
                {1024L, 2048L},
                {-1024L, -2048L},
                {50000L, -30000L},
                {-50000L, 30000L}
        };

        for (long[] shift : shifts) {
            originManager.rebaseWorldOrigin(shift[0], shift[1]);

            // Re-query all points at their new world-local coordinates!
            // When origin shifts by (dx, dy), world-local coords shift by (-dx, -dy)
            // The cumulative shift since start is (originManager.getOriginShiftX(), originManager.getOriginShiftY())
            long cumDx = originManager.getOriginShiftX();
            long cumDy = originManager.getOriginShiftY();

            for (int i = 0; i < testPoints.length; i++) {
                float localX = testPoints[i][0] - (float) cumDx;
                float localY = testPoints[i][1] - (float) cumDy;

                boolean traversableAfter = world.isTraversable(localX, localY, radius);
                assertEquals(traversableBefore[i], traversableAfter,
                        "Traversability at point " + i + " (" + testPoints[i][0] + ", " + testPoints[i][1] + ") must be stable after rebase");
            }
        }
    }

    // =========================================================================
    // Objective 4: Listener ordering and idempotence
    // =========================================================================

    @Test
    @DisplayName("Challenge 4.1: Listener registration order and idempotence")
    void testListenerOrderingAndIdempotence() {
        List<Integer> invocationLog = new ArrayList<>();

        FloatingOriginAware l1 = (dx, dy) -> invocationLog.add(1);
        FloatingOriginAware l2 = (dx, dy) -> invocationLog.add(2);
        FloatingOriginAware l3 = (dx, dy) -> invocationLog.add(3);

        originManager.registerListener(l1);
        originManager.registerListener(l2);
        originManager.registerListener(l3);

        // Idempotence: re-registering l2 should be a no-op
        originManager.registerListener(l2);
        originManager.registerListener(l1);

        originManager.rebaseWorldOrigin(1024L, 0L);

        // Expect exact order [1, 2, 3] with NO duplicates
        assertEquals(List.of(1, 2, 3), invocationLog, "Listeners must be called in order of registration with no duplicates");

        // Unregister l2
        invocationLog.clear();
        originManager.unregisterListener(l2);
        originManager.rebaseWorldOrigin(0L, 1024L);

        assertEquals(List.of(1, 3), invocationLog, "Unregistered listener must not be called");

        // Unregistering an un-registered or null listener should not throw
        assertDoesNotThrow(() -> originManager.unregisterListener(l2));
        assertDoesNotThrow(() -> originManager.unregisterListener(null));
        assertDoesNotThrow(() -> originManager.registerListener(null));
    }

    @Test
    @DisplayName("Challenge 4.2: Self-unregistration or listener modification during onOriginShift callback causes missed callbacks")
    void testListenerModificationDuringRebase() {
        // Stress scenario: A listener unregisters itself during its onOriginShift callback
        // In FloatingOriginManager.java lines 108-114:
        //   for (int i = 0; i < listeners.size(); i++) {
        //       FloatingOriginAware listener = listeners.get(i);
        //       if (listener != null) listener.onOriginShift(deltaX, deltaY);
        //   }
        // When listener 1 unregisters, listener 2 shifts to index 0, but i increments to 1,
        // causing listener 2 to be skipped (missed callback)!
        AtomicBoolean l1Called = new AtomicBoolean(false);
        AtomicBoolean l2Called = new AtomicBoolean(false);
        AtomicBoolean l3Called = new AtomicBoolean(false);

        FloatingOriginAware l1 = new FloatingOriginAware() {
            @Override
            public void onOriginShift(long deltaX, long deltaY) {
                l1Called.set(true);
                // Self-unregistration during callback!
                originManager.unregisterListener(this);
            }
        };

        FloatingOriginAware l2 = (dx, dy) -> l2Called.set(true);
        FloatingOriginAware l3 = (dx, dy) -> l3Called.set(true);

        originManager.registerListener(l1);
        originManager.registerListener(l2);
        originManager.registerListener(l3);

        assertDoesNotThrow(() -> originManager.rebaseWorldOrigin(1024L, 1024L),
                "rebaseWorldOrigin must not throw ConcurrentModificationException during listener unregistration");

        assertTrue(l1Called.get(), "Listener 1 must be called");
        assertTrue(l2Called.get(), "Listener 2 MUST be called even if Listener 1 unregisters during callback! (No missed callbacks)");
        assertTrue(l3Called.get(), "Listener 3 MUST be called even if Listener 1 unregisters during callback! (No missed callbacks)");
    }
}
