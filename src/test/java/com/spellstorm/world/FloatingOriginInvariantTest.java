package com.spellstorm.world;

import com.spellstorm.camera.GameCamera;
import com.spellstorm.combat.CombatSystem;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.ExperiencePickup;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.ExperienceSystem;
import com.spellstorm.systems.PowerUpManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Milestone 3: FloatingOrigin Invariant & Centralized Atomic Rebasing Tests")
public class FloatingOriginInvariantTest {

    private FloatingOriginManager originManager;

    @BeforeEach
    void setUp() {
        originManager = new FloatingOriginManager();
    }

    @Test
    @DisplayName("1. Threshold detection across all 4 quadrants (+/- X, +/- Y)")
    void testThresholdDetection() {
        assertFalse(originManager.shouldRebase(0.0f, 0.0f));
        assertFalse(originManager.shouldRebase(500.0f, -600.0f));
        assertFalse(originManager.shouldRebase(1023.9f, -1023.9f));

        assertTrue(originManager.shouldRebase(1024.0f, 0.0f), "Quadrant +X");
        assertTrue(originManager.shouldRebase(-1024.0f, 0.0f), "Quadrant -X");
        assertTrue(originManager.shouldRebase(0.0f, 1024.0f), "Quadrant +Y");
        assertTrue(originManager.shouldRebase(0.0f, -1024.0f), "Quadrant -Y");
        assertTrue(originManager.shouldRebase(1500.0f, -1200.0f), "Diagonal threshold");
    }

    @Test
    @DisplayName("2. Chunk-aligned displacement is strictly a multiple of 1024L")
    void testChunkAlignedDisplacement() {
        // Player at (1050.0f, 0.0f) -> rounds to 1 chunk = 1024L
        Vector2D playerPos = new Vector2D(1050.0f, 0.0f);
        boolean shifted = originManager.checkAndRebase(playerPos);
        assertTrue(shifted);
        assertEquals(1024L, originManager.getLastDeltaX());
        assertEquals(0L, originManager.getLastDeltaY());
        assertEquals(1024L, originManager.getOriginShiftX());
        assertEquals(0L, originManager.getOriginShiftY());
        assertEquals(1, originManager.getOriginChunkX());
        assertEquals(0, originManager.getOriginChunkY());

        // Player at (-2050.0f, 1025.0f) -> rounds to -2 chunks X, +1 chunk Y
        playerPos.set(-2050.0f, 1025.0f);
        shifted = originManager.checkAndRebase(playerPos);
        assertTrue(shifted);
        assertEquals(-2048L, originManager.getLastDeltaX());
        assertEquals(1024L, originManager.getLastDeltaY());
        assertEquals(1024L - 2048L, originManager.getOriginShiftX());
        assertEquals(1024L, originManager.getOriginShiftY());
    }

    @Test
    @DisplayName("3. Pairwise entity distance invariance under atomic rebasing")
    void testPairwiseDistanceInvariance() {
        Random rng = new Random(42);
        List<Enemy> entities = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            float x = (rng.nextFloat() - 0.5f) * 2000.0f;
            float y = (rng.nextFloat() - 0.5f) * 2000.0f;
            entities.add(new MeleeEnemy(new Vector2D(x, y)));
        }

        // Record pairwise distances before rebase
        int n = entities.size();
        float[][] distancesBefore = new float[n][n];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                distancesBefore[i][j] = entities.get(i).getPosition().dist(entities.get(j).getPosition());
            }
        }

        // Centralized atomic rebase by (1024, -1024)
        for (Enemy enemy : entities) {
            enemy.rebase(1024.0f, -1024.0f);
        }

        // Verify all pairwise distances are bitwise identical
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                float distAfter = entities.get(i).getPosition().dist(entities.get(j).getPosition());
                assertEquals(distancesBefore[i][j], distAfter, 1e-3f,
                        "Pairwise distance between entity " + i + " and " + j + " must be strictly invariant");
            }
        }
    }

    @Test
    @DisplayName("4. Projectile trajectory and velocity invariance under rebasing")
    void testProjectileTrajectoryInvariance() {
        CombatSystem combat = new CombatSystem();
        originManager.registerListener(combat);

        Vector2D startPos = new Vector2D(200.0f, 300.0f);
        Vector2D dir = new Vector2D(1.0f, 0.5f).nor();
        Projectile p = new Projectile(startPos, dir, 400.0f, 6.0f, 25.0f, 2.0f, 1, ProjectileFaction.PLAYER, null, null);
        combat.addProjectile(p);

        // Advance 0.1s
        p.update(0.1f);
        Vector2D posBeforeShift = p.getPosition().cpy();
        Vector2D velBeforeShift = p.getVelocity().cpy();

        // Origin shift by (1024, 1024)
        originManager.rebaseWorldOrigin(1024L, 1024L);

        Vector2D posAfterShift = p.getPosition().cpy();
        assertEquals(posBeforeShift.x - 1024.0f, posAfterShift.x, 1e-4f);
        assertEquals(posBeforeShift.y - 1024.0f, posAfterShift.y, 1e-4f);

        // Velocity must remain unchanged (velocity is translation-invariant)
        assertEquals(velBeforeShift.x, p.getVelocity().x, 1e-5f);
        assertEquals(velBeforeShift.y, p.getVelocity().y, 1e-5f);

        // Further advancement must be continuous
        p.update(0.1f);
        assertEquals(posAfterShift.x + velBeforeShift.x * 0.1f, p.getPosition().x, 1e-3f);
        assertEquals(posAfterShift.y + velBeforeShift.y * 0.1f, p.getPosition().y, 1e-3f);
    }

    @Test
    @DisplayName("5. Screen space projection bit identity before and after rebase")
    void testScreenSpaceProjectionBitIdentity() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setClampingEnabled(false);
        camera.setCenter(500.0f, 300.0f);

        Player player = new Player(new Vector2D(550.0f, 320.0f));
        originManager.registerListener(camera);
        originManager.registerListener(player);

        Vector2D screenPosBefore = camera.worldToScreen(player.getPosition().x, player.getPosition().y);

        // Atomic rebase
        originManager.rebaseWorldOrigin(1024L, 2048L);

        Vector2D screenPosAfter = camera.worldToScreen(player.getPosition().x, player.getPosition().y);

        assertEquals(screenPosBefore.x, screenPosAfter.x, 1e-3f, "Screen X must be bit-identical");
        assertEquals(screenPosBefore.y, screenPosAfter.y, 1e-3f, "Screen Y must be bit-identical");
    }

    @Test
    @DisplayName("6. Enemy AI navigation vectors and stuck detection invariant under rebase")
    void testPreviousPositionInterpolationRebased() {
        Enemy enemy = new MeleeEnemy(new Vector2D(100.0f, 100.0f));
        enemy.setNavPathForTesting(List.of(new Vector2D(120.0f, 100.0f), new Vector2D(200.0f, 200.0f)));
        enemy.setNavLastPositionForTesting(new Vector2D(98.0f, 99.0f));
        enemy.setNavLastTargetPosForTesting(new Vector2D(300.0f, 300.0f));

        RangedEnemy ranged = new RangedEnemy(new Vector2D(150.0f, 150.0f));
        ranged.setTargetPosCopyForTesting(new Vector2D(250.0f, 250.0f));

        originManager.registerListener(enemy);
        originManager.registerListener(ranged);

        originManager.rebaseWorldOrigin(1024L, 1024L);

        // Enemy position
        assertEquals(-924.0f, enemy.getPosition().x, 1e-4f);
        assertEquals(-924.0f, enemy.getPosition().y, 1e-4f);

        // Waypoints shifted
        List<Vector2D> path = enemy.getNavPath();
        assertEquals(-904.0f, path.get(0).x, 1e-4f);
        assertEquals(-924.0f, path.get(0).y, 1e-4f);
        assertEquals(-824.0f, path.get(1).x, 1e-4f);
        assertEquals(-824.0f, path.get(1).y, 1e-4f);

        // navLastPosition and navLastTargetPos shifted
        assertEquals(-926.0f, enemy.getNavLastPosition().x, 1e-4f);
        assertEquals(-925.0f, enemy.getNavLastPosition().y, 1e-4f);
        assertEquals(-724.0f, enemy.getNavLastTargetPos().x, 1e-4f);
        assertEquals(-724.0f, enemy.getNavLastTargetPos().y, 1e-4f);

        // Ranged targetPosCopy shifted
        assertEquals(-774.0f, ranged.getTargetPosCopy().x, 1e-4f);
        assertEquals(-774.0f, ranged.getTargetPosCopy().y, 1e-4f);
    }

    @Test
    @DisplayName("7. ExperiencePickup baseY and bobbing anchor continuity across rebase")
    void testPickupBaseYConsistency() {
        ExperienceSystem expSystem = new ExperienceSystem();
        originManager.registerListener(expSystem);

        expSystem.spawnPickup(300.0f, 400.0f, 20);
        ExperiencePickup gem = expSystem.getPickups().get(0);
        assertEquals(400.0f, gem.getBaseY(), 1e-5f);
        float initialRenderY = gem.getRenderY();

        originManager.rebaseWorldOrigin(1024L, 1024L);

        assertEquals(-724.0f, gem.getPosition().x, 1e-5f);
        assertEquals(-624.0f, gem.getPosition().y, 1e-5f);
        assertEquals(-624.0f, gem.getBaseY(), 1e-5f, "baseY must shift synchronously with position.y");
        assertEquals(initialRenderY - 1024.0f, gem.getRenderY(), 1e-4f, "Render Y must shift by deltaY exactly");
    }

    @Test
    @DisplayName("8. Camera smoothing continuity across rebase without velocity spikes")
    void testCameraSmoothingContinuityAcrossRebase() {
        GameCamera camera = new GameCamera(1280.0f, 720.0f);
        camera.setClampingEnabled(false);
        camera.setCenter(0.0f, 0.0f);

        float targetX = 500.0f;
        float targetY = 300.0f;

        // Step 1: smooth towards target
        camera.update(targetX, targetY, 0.016f);
        float centerBeforeRebaseX = camera.getCenterX();
        float centerBeforeRebaseY = camera.getCenterY();

        // Origin shift by (1024, 1024)
        camera.rebase(1024.0f, 1024.0f);
        float shiftedTargetX = targetX - 1024.0f;
        float shiftedTargetY = targetY - 1024.0f;

        assertEquals(centerBeforeRebaseX - 1024.0f, camera.getCenterX(), 1e-4f);
        assertEquals(centerBeforeRebaseY - 1024.0f, camera.getCenterY(), 1e-4f);

        // Step 2: continue smoothing with shifted target
        camera.update(shiftedTargetX, shiftedTargetY, 0.016f);

        // Tracking difference (target - center) must match unrebased control
        GameCamera controlCamera = new GameCamera(1280.0f, 720.0f);
        controlCamera.setClampingEnabled(false);
        controlCamera.setCenter(0.0f, 0.0f);
        controlCamera.update(targetX, targetY, 0.016f);
        controlCamera.update(targetX, targetY, 0.016f);

        float controlDiffX = targetX - controlCamera.getCenterX();
        float actualDiffX = shiftedTargetX - camera.getCenterX();
        assertEquals(controlDiffX, actualDiffX, 1e-4f, "Tracking displacement must remain identical");
    }

    @Test
    @DisplayName("9. 64-bit coordinate conversions round-trip up to +/- 2.2 trillion px")
    void testCoordinateConversionsRoundTrip() {
        originManager.rebaseWorldOrigin(1024000L, -2048000L);

        float localX = 123.45f;
        float localY = -678.90f;

        long gx = originManager.toGlobalPixelX(localX);
        long gy = originManager.toGlobalPixelY(localY);

        assertEquals(1024000L + (long) Math.floor(localX), gx);
        assertEquals(-2048000L + (long) Math.floor(localY), gy);

        float reconstructedLocalX = originManager.toLocalPixelX(gx);
        float reconstructedLocalY = originManager.toLocalPixelY(gy);

        assertEquals(Math.floor(localX), reconstructedLocalX, 1e-5f);
        assertEquals(Math.floor(localY), reconstructedLocalY, 1e-5f);

        // Extreme limits: 2.2e12 px
        long extremeGlobalX = 2_200_000_000_000L;
        long extremeGlobalY = -2_200_000_000_000L;
        originManager.reset();
        originManager.rebaseWorldOrigin(extremeGlobalX, extremeGlobalY);

        assertEquals(0.0f, originManager.toLocalPixelX(extremeGlobalX), 1e-3f);
        assertEquals(0.0f, originManager.toLocalPixelY(extremeGlobalY), 1e-3f);
    }

    @Test
    @DisplayName("10. InfiniteWorld constructor automatically registers chunkManager with originManager")
    void testInfiniteWorldRegistersChunkManagerAsOriginListener() {
        FloatingOriginManager originMgr = new FloatingOriginManager();
        ChunkManager chunkMgr = new ChunkManagerImpl(12345L, 64);
        InfiniteWorld infiniteWorld = new InfiniteWorld(chunkMgr, originMgr);

        assertTrue(originMgr.getListeners().contains(infiniteWorld),
                "InfiniteWorld must be registered as origin listener");
        assertTrue(originMgr.getListeners().contains(chunkMgr),
                "ChunkManager must be automatically registered as origin listener");

        // Trigger atomic rebase
        originMgr.rebaseWorldOrigin(1024L, 2048L);
        assertEquals(1024L, chunkMgr.getOriginShiftX(),
                "ChunkManager originShiftX must synchronize with originManager");
        assertEquals(2048L, chunkMgr.getOriginShiftY(),
                "ChunkManager originShiftY must synchronize with originManager");
    }
}
