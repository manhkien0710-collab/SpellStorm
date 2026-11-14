package com.spellstorm.entities;

import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for Shooting Modes and Aiming Subsystem.
 * Ports all 14 scenarios from C++ reference test_shooting_mode.cpp,
 * plus boundary value analysis and camera zoom invariance tests.
 */
public class ShootingModeTest {

    // Headless test session simulating shooting mode lifecycle & combat triggers
    public static class TestGameSession {
        private final Player player;
        private final List<Enemy> enemies = new ArrayList<>();
        private int projectileCount = 0;
        private Vector2D lastFiredDir = new Vector2D();
        private float lastFiredDamage = 0.0f;
        private float lastFiredSpeed = 0.0f;
        private float lastFiredLifetime = 0.0f;
        private int lastFiredPierce = 0;

        private float modeNotificationTimer = 0.0f;
        private String modeNotificationText = "";
        private boolean isLevelUpOpen = false;
        private boolean isGameOver = false;

        public TestGameSession() {
            this.player = new Player(new Vector2D(640.0f, 360.0f));
        }

        public Player getPlayer() {
            return player;
        }

        public List<Enemy> getEnemies() {
            return enemies;
        }

        public int getProjectileCount() {
            return projectileCount;
        }

        public Vector2D getLastFiredDir() {
            return lastFiredDir.cpy();
        }

        public float getLastFiredDamage() {
            return lastFiredDamage;
        }

        public float getLastFiredSpeed() {
            return lastFiredSpeed;
        }

        public float getLastFiredLifetime() {
            return lastFiredLifetime;
        }

        public int getLastFiredPierce() {
            return lastFiredPierce;
        }

        public ShootingMode getShootingMode() {
            return player.getShootingMode();
        }

        public void setShootingMode(ShootingMode mode) {
            player.setShootingMode(mode);
        }

        public float getModeNotificationTimer() {
            return modeNotificationTimer;
        }

        public boolean isPlaying() {
            return !isLevelUpOpen && !isGameOver && player.isAlive();
        }

        public boolean isLevelUpOpen() {
            return isLevelUpOpen;
        }

        public boolean isGameOver() {
            return isGameOver;
        }

        public void triggerLevelUp() {
            this.isLevelUpOpen = true;
        }

        public void closeLevelUp() {
            this.isLevelUpOpen = false;
        }

        public void triggerGameOver() {
            this.isGameOver = true;
        }

        public void toggleShootingMode() {
            if (!isPlaying()) return;
            player.toggleShootingMode();
            this.modeNotificationTimer = 1.8f;
            this.modeNotificationText = player.getShootingMode().getNotificationText();
        }

        public void handleKeyPressV() {
            if (!isPlaying()) return;
            if (player.tryToggleShootingMode()) {
                this.modeNotificationTimer = 1.8f;
                this.modeNotificationText = player.getShootingMode().getNotificationText();
            }
        }

        public void handleKeyReleaseV() {
            player.onToggleKeyReleased();
        }

        public void handleMouseClick(Vector2D targetPos) {
            if (!isPlaying()) return;
            player.setAimTarget(targetPos);
            if (player.canCast()) {
                fireProjectile(player.getAimDirection());
            }
        }

        public Enemy findTargetEnemy() {
            return AimingSubsystem.findClosestEnemy(player.getPosition(), enemies);
        }

        private void fireProjectile(Vector2D dir) {
            player.triggerCast();
            projectileCount++;
            lastFiredDir.set(dir);
            lastFiredDamage = 25.0f * player.getDamageMultiplier();
            lastFiredSpeed = 400.0f * player.getProjectileSpeedMultiplier();
            lastFiredLifetime = 2.0f * player.getProjectileLifetimeMultiplier();
            lastFiredPierce = player.getBonusPierce();
        }

        public void update(float dt, Vector2D worldMousePos, boolean autoplay) {
            if (!isPlaying()) return;

            if (modeNotificationTimer > 0.0f) {
                modeNotificationTimer = Math.max(0.0f, modeNotificationTimer - dt);
            }

            player.update(dt);

            if (autoplay) {
                Enemy target = findTargetEnemy();
                if (target != null) {
                    player.setAimTarget(target.getPosition());
                    if (player.canCast()) {
                        fireProjectile(player.getAimDirection());
                    }
                } else {
                    player.setAimTarget(worldMousePos);
                }
            } else if (player.getShootingMode() == ShootingMode.AUTO) {
                Vector2D aimDir = AimingSubsystem.calculateAutoAim(
                        player.getPosition(), worldMousePos, enemies, player.getAimDirection()
                );
                player.setAimDirection(aimDir);
                if (player.canCast()) {
                    fireProjectile(aimDir);
                }
            } else {
                player.setAimTarget(worldMousePos);
            }
        }

        public void update(float dt, Vector2D worldMousePos) {
            update(dt, worldMousePos, false);
        }

        public void restartMatch() {
            player.resetPlayer(new Vector2D(640.0f, 360.0f));
            enemies.clear();
            projectileCount = 0;
            modeNotificationTimer = 0.0f;
            modeNotificationText = "";
            isLevelUpOpen = false;
            isGameOver = false;
        }
    }

    @BeforeAll
    public static void setupHeadless() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    public void setUp() {
        Entity.resetIdCounter(1);
    }

    // =========================================================================
    // Scenario 1: Default Shooting Mode
    // =========================================================================

    @Test
    @DisplayName("Case 1: Match starts strictly in Manual mode with zero notification timer and LMB firing")
    public void testDefaultShootingMode() {
        TestGameSession session = new TestGameSession();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
        assertEquals(0.0f, session.getModeNotificationTimer(), 1e-4f);

        // In Manual mode, running update without mouse clicks does not shoot even if enemies are near
        session.getEnemies().add(new MeleeEnemy(new Vector2D(740.0f, 360.0f)));
        assertEquals(0, session.getProjectileCount());

        session.update(0.016f, new Vector2D(800.0f, 360.0f));
        assertEquals(0, session.getProjectileCount());
        assertTrue(session.getPlayer().canCast());

        // In Manual mode, explicit mouse button press triggers cast
        session.handleMouseClick(new Vector2D(800.0f, 360.0f));
        assertEquals(1, session.getProjectileCount());
        assertFalse(session.getPlayer().canCast());
    }

    // =========================================================================
    // Scenario 2: Mode Switching via Key Toggle & Repeated-Key Debounce
    // =========================================================================

    @Test
    @DisplayName("Case 2: Mode switching via key 'V' toggle, key-held suppression, and programmatic toggle")
    public void testModeSwitchingAndKeyDebounce() {
        TestGameSession session = new TestGameSession();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());

        // First press of 'V'
        session.handleKeyPressV();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());
        assertTrue(session.getModeNotificationTimer() > 0.0f);

        // Repeated KeyPressed events while 'V' key is held must remain AUTO (no oscillation)
        session.handleKeyPressV();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());
        session.handleKeyPressV();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());

        // Release the 'V' key
        session.handleKeyReleaseV();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());
        session.getPlayer().updateCooldowns(0.3f);

        // Press 'V' again -> toggles back to MANUAL
        session.handleKeyPressV();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());

        // Repeated press while held remains MANUAL
        session.handleKeyPressV();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());

        // Release key again
        session.handleKeyReleaseV();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
        session.getPlayer().updateCooldowns(0.3f);

        // Verify programmatic toggle API
        session.toggleShootingMode();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());
        session.toggleShootingMode();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
    }

    // =========================================================================
    // Scenario 3: Switching back to Manual restores manual behavior
    // =========================================================================

    @Test
    @DisplayName("Case 3: Switching back to Manual restores manual behavior")
    public void testSwitchingBackToManualRestoresManualBehavior() {
        TestGameSession session = new TestGameSession();
        session.getEnemies().add(new MeleeEnemy(new Vector2D(740.0f, 360.0f)));

        // Switch to Auto
        session.setShootingMode(ShootingMode.AUTO);
        assertEquals(ShootingMode.AUTO, session.getShootingMode());

        // Switch back to Manual
        session.setShootingMode(ShootingMode.MANUAL);
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());

        // Update tick in Manual must NOT automatically fire at enemy
        session.update(0.016f, new Vector2D(640.0f, 100.0f));
        assertEquals(0, session.getProjectileCount());
        assertTrue(session.getPlayer().canCast());

        // In Manual mode, mouse click deliberately fires towards mouse cursor
        session.handleMouseClick(new Vector2D(640.0f, 100.0f));
        assertEquals(1, session.getProjectileCount());
        assertTrue(session.getLastFiredDir().y < -0.9f); // Fired upwards towards mouse cursor
    }

    // =========================================================================
    // Scenario 4: Auto-Shot Dynamic Aim and Shooting Direction Follows Player's Mouse
    // =========================================================================

    @Test
    @DisplayName("Case 4: Auto-shot dynamic aim and shooting direction follows player's mouse when no enemies")
    public void testAutoShotDynamicAimFollowsMouseWhenNoEnemies() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);
        Vector2D playerPos = session.getPlayer().getPosition();

        assertEquals(0, session.getProjectileCount());
        assertTrue(session.getPlayer().canCast());

        // Step A: Mouse to the right of the player (+200, 0)
        Vector2D mouseRight = Vector2D.add(playerPos, new Vector2D(200.0f, 0.0f));
        session.update(0.016f, mouseRight);

        assertEquals(1, session.getProjectileCount());
        assertEquals(1.0f, session.getLastFiredDir().x, 0.05f);
        assertEquals(0.0f, session.getLastFiredDir().y, 0.05f);
        assertEquals(0.0f, session.getPlayer().getAimAngle(), 0.1f);

        // Advance cooldown until ready
        while (!session.getPlayer().canCast()) {
            session.getPlayer().update(0.05f);
        }

        // Step B: Player moves mouse to the top (upwards) (0, -200)
        Vector2D mouseUp = Vector2D.add(playerPos, new Vector2D(0.0f, -200.0f));
        session.update(0.016f, mouseUp);

        assertEquals(2, session.getProjectileCount());
        assertEquals(0.0f, session.getLastFiredDir().x, 0.05f);
        assertEquals(-1.0f, session.getLastFiredDir().y, 0.05f);
        assertEquals(-MathUtils.HALF_PI, session.getPlayer().getAimAngle(), 0.1f);
    }

    // =========================================================================
    // Scenario 5: Bot Autoplay Mode Automatically Acquires and Targets Enemies
    // =========================================================================

    @Test
    @DisplayName("Case 5: Bot autoplay mode automatically acquires and targets enemies")
    public void testAutoAimAutomaticallyAcquiresAndTargetsEnemies() {
        TestGameSession session = new TestGameSession();
        Vector2D playerPos = session.getPlayer().getPosition();
        Vector2D enemyPos = Vector2D.add(playerPos, new Vector2D(150.0f, 0.0f));
        session.getEnemies().add(new MeleeEnemy(enemyPos));

        // In autoplay, mouse cursor is dummy at (10, 10), but bot aims directly at enemy
        session.update(0.016f, new Vector2D(10.0f, 10.0f), /*autoplay=*/true);

        assertEquals(1, session.getProjectileCount());
        assertEquals(1.0f, session.getLastFiredDir().x, 0.05f);
        assertEquals(0.0f, session.getLastFiredDir().y, 0.05f);
    }

    // =========================================================================
    // Scenario 6: Auto-Shot Strictly Respects Attack Cooldowns
    // =========================================================================

    @Test
    @DisplayName("Case 6: Auto-shot strictly respects attack cooldowns")
    public void testAutoShotStrictlyRespectsAttackCooldowns() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);
        session.getEnemies().add(new MeleeEnemy(new Vector2D(1050.0f, 360.0f)));

        // First tick casts spell
        session.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(1, session.getProjectileCount());
        assertFalse(session.getPlayer().canCast());

        // Subsequent ticks while cooldown is active must NOT spawn new projectiles
        for (int i = 0; i < 5; i++) {
            session.update(0.016f, new Vector2D(640.0f, 360.0f));
            assertEquals(1, session.getProjectileCount());
        }

        // Advance time until cooldown has elapsed
        while (!session.getPlayer().canCast()) {
            session.getPlayer().update(0.05f);
        }
        assertTrue(session.getPlayer().canCast());

        // Now next tick triggers next projectile
        session.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(2, session.getProjectileCount());
    }

    // =========================================================================
    // Scenario 7: Nearest Living Enemy Policy & Deterministic Tie-Breaking
    // =========================================================================

    @Test
    @DisplayName("Case 7: Nearest living enemy policy and deterministic tie-breaking by lowest ID")
    public void testNearestLivingEnemyPolicyAndTieBreaking() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);
        Vector2D pPos = session.getPlayer().getPosition();

        // Spawn 3 enemies at varying distances
        MeleeEnemy eNear = new MeleeEnemy(Vector2D.add(pPos, new Vector2D(80.0f, 0.0f)));
        MeleeEnemy eMid  = new MeleeEnemy(Vector2D.add(pPos, new Vector2D(160.0f, 0.0f)));
        MeleeEnemy eFar  = new MeleeEnemy(Vector2D.add(pPos, new Vector2D(240.0f, 0.0f)));

        session.getEnemies().add(eNear);
        session.getEnemies().add(eMid);
        session.getEnemies().add(eFar);

        // Target nearest
        assertSame(eNear, session.findTargetEnemy());

        // Kill nearest enemy
        eNear.takeDamage(9999.0f, true);
        assertFalse(eNear.isAlive());

        // Now target is the mid enemy
        assertSame(eMid, session.findTargetEnemy());

        // Kill mid enemy
        eMid.takeDamage(9999.0f, true);
        assertFalse(eMid.isAlive());

        // Now target is the far enemy
        assertSame(eFar, session.findTargetEnemy());

        // Kill far enemy
        eFar.takeDamage(9999.0f, true);
        assertNull(session.findTargetEnemy());

        // Tie-breaking check with two equidistant enemies
        session.getEnemies().clear();
        MeleeEnemy e1 = new MeleeEnemy(Vector2D.add(pPos, new Vector2D(100.0f, 0.0f)));
        MeleeEnemy e2 = new MeleeEnemy(Vector2D.add(pPos, new Vector2D(-100.0f, 0.0f)));
        MeleeEnemy expected = (e1.getId() < e2.getId()) ? e1 : e2;

        session.getEnemies().add(e1);
        session.getEnemies().add(e2);

        assertSame(expected, session.findTargetEnemy());
    }

    // =========================================================================
    // Scenario 8: Auto-Shot Utilizes Player Upgrades
    // =========================================================================

    @Test
    @DisplayName("Case 8: Auto-shot utilizes player upgrades (damage, speed, lifetime, pierce)")
    public void testAutoShotUtilizesPlayerUpgrades() {
        TestGameSession sessionBase = new TestGameSession();
        sessionBase.setShootingMode(ShootingMode.AUTO);
        sessionBase.getEnemies().add(new MeleeEnemy(Vector2D.add(sessionBase.getPlayer().getPosition(), new Vector2D(100.0f, 0.0f))));
        sessionBase.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(1, sessionBase.getProjectileCount());

        float baseDmg = sessionBase.getLastFiredDamage();
        float baseSpd = sessionBase.getLastFiredSpeed();
        float baseLife = sessionBase.getLastFiredLifetime();
        int basePierce = sessionBase.getLastFiredPierce();

        // Upgraded session
        TestGameSession sessionUp = new TestGameSession();
        sessionUp.setShootingMode(ShootingMode.AUTO);
        sessionUp.getPlayer().multiplyDamage(1.5f);
        sessionUp.getPlayer().multiplyProjectileSpeed(1.4f);
        sessionUp.getPlayer().multiplyProjectileLifetime(1.6f);
        sessionUp.getPlayer().addBonusPierce(1);

        sessionUp.getEnemies().add(new MeleeEnemy(Vector2D.add(sessionUp.getPlayer().getPosition(), new Vector2D(100.0f, 0.0f))));
        sessionUp.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(1, sessionUp.getProjectileCount());

        assertTrue(sessionUp.getLastFiredDamage() > baseDmg);
        assertTrue(sessionUp.getLastFiredSpeed() > baseSpd);
        assertTrue(sessionUp.getLastFiredLifetime() > baseLife);
        assertTrue(sessionUp.getLastFiredPierce() > basePierce);
    }

    // =========================================================================
    // Scenario 9: Target Removal / Mid-Simulation Death Memory Safety
    // =========================================================================

    @Test
    @DisplayName("Case 9: Target removal / mid-simulation death memory safety")
    public void testTargetRemovalMidSimulationSafety() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);

        for (int i = 0; i < 5; i++) {
            session.getEnemies().add(new MeleeEnemy(
                    Vector2D.add(session.getPlayer().getPosition(), new Vector2D(50.0f + 25.0f * i, 0.0f))
            ));
        }

        // Mid-frame mark multiple dead
        session.getEnemies().get(0).destroy();
        session.getEnemies().get(2).destroy();

        // Update tick runs cleanly without exception
        assertDoesNotThrow(() -> session.update(0.016f, new Vector2D(640.0f, 360.0f)));
        assertTrue(session.isPlaying());

        // Clear enemies vector mid-match and update
        session.getEnemies().clear();
        assertDoesNotThrow(() -> session.update(0.016f, new Vector2D(640.0f, 360.0f)));
        assertTrue(session.isPlaying());
    }

    // =========================================================================
    // Scenario 10: Mode Switching State Preservation
    // =========================================================================

    @Test
    @DisplayName("Case 10: Mode switching preserves player state and attributes")
    public void testModeSwitchingStatePreservation() {
        TestGameSession session = new TestGameSession();
        session.getPlayer().takeDamage(25.0f);
        session.getEnemies().add(new MeleeEnemy(new Vector2D(700.0f, 360.0f)));

        float hpBefore = session.getPlayer().getHealth();
        int enemiesBefore = session.getEnemies().size();

        // Toggle Manual -> Auto
        session.toggleShootingMode();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());
        assertEquals(hpBefore, session.getPlayer().getHealth(), 1e-4f);
        assertEquals(enemiesBefore, session.getEnemies().size());

        // Toggle Auto -> Manual
        session.toggleShootingMode();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
        assertEquals(hpBefore, session.getPlayer().getHealth(), 1e-4f);
        assertEquals(enemiesBefore, session.getEnemies().size());
    }

    // =========================================================================
    // Scenario 11: Auto-Shot and Toggle Safety During Level-Up Pause
    // =========================================================================

    @Test
    @DisplayName("Case 11: Auto-shot and toggle safety during level-up pause")
    public void testAutoShotAndToggleSafetyDuringLevelUpPause() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);
        session.getEnemies().add(new MeleeEnemy(Vector2D.add(session.getPlayer().getPosition(), new Vector2D(60.0f, 0.0f))));

        // Open level up overlay
        session.triggerLevelUp();
        assertTrue(session.isLevelUpOpen());

        // Update while paused: must NOT fire auto-shot
        session.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(0, session.getProjectileCount());

        // 'V' key press while level up is open must NOT toggle shooting mode
        session.handleKeyPressV();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());

        // Close modal
        session.closeLevelUp();
        assertFalse(session.isLevelUpOpen());
        assertTrue(session.isPlaying());

        // Resuming gameplay enables Auto-shot firing
        session.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(1, session.getProjectileCount());
    }

    // =========================================================================
    // Scenario 12: Auto-Shot and Toggle Safety During Game Over
    // =========================================================================

    @Test
    @DisplayName("Case 12: Auto-shot and toggle safety during game over")
    public void testAutoShotAndToggleSafetyDuringGameOver() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);
        session.getEnemies().add(new MeleeEnemy(Vector2D.add(session.getPlayer().getPosition(), new Vector2D(60.0f, 0.0f))));

        session.triggerGameOver();
        assertTrue(session.isGameOver());

        // Update tick while Game Over: gameplay remains frozen, no firing
        session.update(0.016f, new Vector2D(640.0f, 360.0f));
        assertEquals(0, session.getProjectileCount());

        // 'V' key press during Game Over does NOT toggle shooting mode
        session.handleKeyPressV();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());
    }

    // =========================================================================
    // Scenario 13: Match Restart Restores Manual Mode
    // =========================================================================

    @Test
    @DisplayName("Case 13: Match restart restores Manual mode")
    public void testMatchRestartRestoresManualMode() {
        TestGameSession session = new TestGameSession();
        session.setShootingMode(ShootingMode.AUTO);
        assertEquals(ShootingMode.AUTO, session.getShootingMode());

        // Restarting match strictly restores Manual mode
        session.restartMatch();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
        assertEquals(0.0f, session.getModeNotificationTimer(), 1e-4f);

        // Can switch to Auto again in new match
        session.toggleShootingMode();
        assertEquals(ShootingMode.AUTO, session.getShootingMode());

        // Second restart also restores Manual mode cleanly
        session.restartMatch();
        assertEquals(ShootingMode.MANUAL, session.getShootingMode());
    }

    // =========================================================================
    // Scenario 14: Melee Enemy Movement Speed Regression Test (105.f legacy, 140.f default)
    // =========================================================================

    @Test
    @DisplayName("Case 14: Melee enemy movement speed regression test (105.f legacy and 140.f default)")
    public void testMeleeEnemyMovementSpeedRegression() {
        MeleeEnemy legacyMelee = MeleeEnemy.createLegacyReference(new Vector2D(0, 0));
        assertEquals(105.0f, legacyMelee.getBaseMoveSpeed(), 1e-4f);
        assertEquals(105.0f, legacyMelee.getCurrentMoveSpeed(), 1e-4f);

        MeleeEnemy defaultMelee = new MeleeEnemy(new Vector2D(0, 0));
        assertEquals(140.0f, defaultMelee.getBaseMoveSpeed(), 1e-4f);
    }

    // =========================================================================
    // Boundary & Zoom Invariance Tests
    // =========================================================================

    @Test
    @DisplayName("Aiming zero distance protection preserves fallback without NaN")
    public void testAimingZeroDistanceProtection() {
        Vector2D playerPos = new Vector2D(500.0f, 500.0f);
        Vector2D fallback = new Vector2D(0.0f, 1.0f);

        // Target exactly coincident with player
        Vector2D manualAim = AimingSubsystem.calculateManualAim(playerPos, new Vector2D(500.0f, 500.0f), fallback);
        assertFalse(Float.isNaN(manualAim.x));
        assertFalse(Float.isNaN(manualAim.y));
        assertEquals(0.0f, manualAim.x, 1e-4f);
        assertEquals(1.0f, manualAim.y, 1e-4f);

        Vector2D autoAim = AimingSubsystem.calculateAutoAim(playerPos, new Vector2D(500.0f, 500.0f), List.of(), fallback);
        assertFalse(Float.isNaN(autoAim.x));
        assertFalse(Float.isNaN(autoAim.y));
        assertEquals(0.0f, autoAim.x, 1e-4f);
        assertEquals(1.0f, autoAim.y, 1e-4f);
    }

    @Test
    @DisplayName("Aiming camera zoom invariance across zoom levels 0.75, 1.35, and 2.25")
    public void testAimingCameraZoomInvariance() {
        GameCamera camera = new GameCamera(1280, 720);
        camera.setCenter(2048.0f, 2048.0f);
        Vector2D playerPos = new Vector2D(2048.0f, 2048.0f);

        float[] zoomLevels = {0.75f, 1.35f, 2.25f};

        for (float zoom : zoomLevels) {
            camera.setTargetZoom(zoom);
            camera.update(playerPos.x, playerPos.y, 1.0f);

            // Screen mouse positioned 100 pixels to the right of screen center (640 + 100, 360)
            Vector2D worldMouse = camera.screenToWorld(740.0f, 360.0f);

            // World mouse must be strictly to the right (+X) of playerPos
            assertTrue(worldMouse.x > playerPos.x);
            assertEquals(playerPos.y, worldMouse.y, 1.0f);

            Vector2D aim = AimingSubsystem.calculateManualAim(playerPos, worldMouse, new Vector2D(1, 0));
            assertEquals(1.0f, aim.x, 1e-3f);
            assertEquals(0.0f, aim.y, 1e-3f);
        }
    }

    @Test
    @DisplayName("4 equidistant enemies in cross pattern consistently pick lowest entity ID")
    public void testMultiEnemyEquidistantTieBreak() {
        Vector2D playerPos = new Vector2D(500.0f, 500.0f);
        List<Enemy> enemies = new ArrayList<>();

        MeleeEnemy north = new MeleeEnemy(new Vector2D(500.0f, 400.0f)); // ID 1
        MeleeEnemy south = new MeleeEnemy(new Vector2D(500.0f, 600.0f)); // ID 2
        MeleeEnemy east  = new MeleeEnemy(new Vector2D(600.0f, 500.0f)); // ID 3
        MeleeEnemy west  = new MeleeEnemy(new Vector2D(400.0f, 500.0f)); // ID 4

        enemies.add(south);
        enemies.add(east);
        enemies.add(west);
        enemies.add(north);

        Enemy best = AimingSubsystem.findClosestEnemy(playerPos, enemies);
        assertNotNull(best);
        assertEquals(north.getId(), best.getId());
    }

    @Test
    @DisplayName("Debounce timer blocks rapid key toggles within 0.25s window")
    public void testDebounceTimerBlocksRapidKeyEvents() {
        Player p = new Player(new Vector2D(0, 0));
        assertEquals(ShootingMode.MANUAL, p.getShootingMode());

        assertTrue(p.tryToggleShootingMode());
        assertEquals(ShootingMode.AUTO, p.getShootingMode());
        p.onToggleKeyReleased();

        // Immediate subsequent toggle attempt is blocked by debounce timer (0.25s)
        assertFalse(p.tryToggleShootingMode());
        assertEquals(ShootingMode.AUTO, p.getShootingMode());

        // Advance 0.15s (< 0.25s) -> still blocked
        p.updateCooldowns(0.15f);
        assertFalse(p.tryToggleShootingMode());

        // Advance another 0.15s (total 0.30s > 0.25s) -> succeeds
        p.updateCooldowns(0.15f);
        assertTrue(p.tryToggleShootingMode());
        assertEquals(ShootingMode.MANUAL, p.getShootingMode());
    }
}
