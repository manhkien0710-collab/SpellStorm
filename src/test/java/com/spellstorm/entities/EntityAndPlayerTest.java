package com.spellstorm.entities;

import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive JUnit 5 test suite for Entity, Character, and Player subsystems.
 * Covers Features 16 & 17 across Tiers 1-4 with zero OpenGL/X11 dependencies.
 */
public class EntityAndPlayerTest {

    // Concrete test entity subclass
    private static class DummyEntity extends Entity {
        public DummyEntity(Vector2D pos, float radius, String tag) {
            super(pos, radius, tag);
        }

        public DummyEntity(float x, float y, float radius) {
            super(x, y, radius);
        }

        public DummyEntity() {
            super();
        }

        @Override
        public void update(float dt) {
            position.add(velocity.x * dt, velocity.y * dt);
        }
    }

    // Concrete test character subclass
    private static class DummyCharacter extends Character {
        public DummyCharacter(Vector2D pos, float radius, float maxHealth, float armor, float speed) {
            super(pos, radius, maxHealth, armor, speed, "dummy_character");
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
    // Tier 1: Entity Base Tests
    // =========================================================================

    @Test
    @DisplayName("Entity ID sequential monotonic generation")
    public void testEntityIdSequentialGeneration() {
        DummyEntity e1 = new DummyEntity(new Vector2D(10, 20), 15.0f, "test");
        DummyEntity e2 = new DummyEntity(new Vector2D(30, 40), 15.0f, "test");
        DummyEntity e3 = new DummyEntity(new Vector2D(50, 60), 15.0f, "test");

        assertEquals(1, e1.getId());
        assertEquals(2, e2.getId());
        assertEquals(3, e3.getId());
    }

    @Test
    @DisplayName("Entity resetIdCounter restores deterministic starting ID")
    public void testEntityResetIdCounter() {
        new DummyEntity();
        new DummyEntity();
        Entity.resetIdCounter(42);
        DummyEntity e42 = new DummyEntity();
        assertEquals(42, e42.getId());

        Entity.resetIdCounter();
        DummyEntity e1 = new DummyEntity();
        assertEquals(1, e1.getId());
    }

    @Test
    @DisplayName("Entity position and velocity manipulation")
    public void testEntityPositionAndVelocity() {
        DummyEntity e = new DummyEntity(new Vector2D(10, 20), 15.0f, "test");
        assertEquals(10.0f, e.getX(), 1e-4f);
        assertEquals(20.0f, e.getY(), 1e-4f);

        e.setPosition(50.0f, 60.0f);
        assertEquals(50.0f, e.getPosition().x, 1e-4f);
        assertEquals(60.0f, e.getPosition().y, 1e-4f);

        e.setVelocity(100.0f, -50.0f);
        assertEquals(100.0f, e.getVelocity().x, 1e-4f);
        assertEquals(-50.0f, e.getVelocity().y, 1e-4f);

        e.update(0.5f);
        assertEquals(100.0f, e.getX(), 1e-4f);
        assertEquals(35.0f, e.getY(), 1e-4f);
    }

    @Test
    @DisplayName("Entity active lifecycle: destroy and revive")
    public void testEntityDestroyAndRevive() {
        DummyEntity e = new DummyEntity(new Vector2D(0, 0), 10.0f, "test");
        assertTrue(e.isActive());
        assertTrue(e.isAlive());

        e.destroy();
        assertFalse(e.isActive());
        assertFalse(e.isAlive());

        e.revive();
        assertTrue(e.isActive());
        assertTrue(e.isAlive());
    }

    @Test
    @DisplayName("Entity spatial distance and collision queries")
    public void testEntityDistanceAndCollision() {
        DummyEntity e1 = new DummyEntity(new Vector2D(0, 0), 10.0f, "e1");
        DummyEntity e2 = new DummyEntity(new Vector2D(15, 0), 10.0f, "e2");
        DummyEntity e3 = new DummyEntity(new Vector2D(35, 0), 10.0f, "e3");

        assertEquals(15.0f, e1.dist(e2), 1e-4f);
        assertEquals(225.0f, e1.distSq(e2), 1e-4f);

        // e1 (r=10) and e2 (r=10) dist=15 <= 20 -> collides
        assertTrue(e1.collidesWith(e2));

        // e1 (r=10) and e3 (r=10) dist=35 > 20 -> disjoint
        assertFalse(e1.collidesWith(e3));

        // Inactive entity does not collide
        e2.destroy();
        assertFalse(e1.collidesWith(e2));

        // Safeguard on null entity
        assertEquals(Float.MAX_VALUE, e1.dist(null));
        assertEquals(Float.MAX_VALUE, e1.distSq(null));
        assertFalse(e1.collidesWith(null));
    }

    @Test
    @DisplayName("Entity equals and hashCode based strictly on ID")
    public void testEntityEqualsAndHashCode() {
        DummyEntity e1 = new DummyEntity(new Vector2D(0, 0), 10.0f, "test");
        DummyEntity e2 = new DummyEntity(new Vector2D(0, 0), 10.0f, "test");

        assertNotEquals(e1, e2);
        assertEquals(e1, e1);
        assertNotEquals(e1, null);
        assertNotEquals(e1, "not an entity");
        assertEquals(e1.hashCode(), e1.hashCode());
    }

    // =========================================================================
    // Tier 1 & 2: Character Vitality & Damage Mitigation Tests
    // =========================================================================

    @Test
    @DisplayName("Character initial stats and health bounds")
    public void testCharacterInitialStats() {
        DummyCharacter c = new DummyCharacter(new Vector2D(100, 100), 20.0f, 200.0f, 25.0f, 150.0f);
        assertEquals(200.0f, c.getHealth(), 1e-4f);
        assertEquals(200.0f, c.getMaxHealth(), 1e-4f);
        assertEquals(25.0f, c.getArmor(), 1e-4f);
        assertEquals(150.0f, c.getBaseMoveSpeed(), 1e-4f);
        assertEquals(1.0f, c.getHealthRatio(), 1e-4f);
        assertTrue(c.isAlive());
    }

    @Test
    @DisplayName("Armor damage mitigation formula: damage * 100 / (100 + max(0, armor))")
    public void testArmorDamageMitigationFormula() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 200.0f, 25.0f, 100.0f);
        // Mitigated damage = 50 * 100 / (100 + 25) = 50 * 0.8 = 40.0
        float dealt = c.takeDamage(50.0f);
        assertEquals(40.0f, dealt, 1e-4f);
        assertEquals(160.0f, c.getHealth(), 1e-4f);
        assertTrue(c.isHitFlashing());
    }

    @Test
    @DisplayName("Damage ignoring armor deals raw damage")
    public void testDamageIgnoreArmor() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 200.0f, 50.0f, 100.0f);
        float dealt = c.takeDamage(30.0f, true);
        assertEquals(30.0f, dealt, 1e-4f);
        assertEquals(170.0f, c.getHealth(), 1e-4f);
    }

    @Test
    @DisplayName("Zero or negative damage is ignored safely")
    public void testZeroOrNegativeDamageIgnored() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 10.0f, 100.0f);
        assertEquals(0.0f, c.takeDamage(0.0f));
        assertEquals(0.0f, c.takeDamage(-25.0f));
        assertEquals(100.0f, c.getHealth(), 1e-4f);
    }

    @Test
    @DisplayName("Healing restores health up to maxHealth and rejects dead characters")
    public void testHealingUpToMaxHealth() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 200.0f, 0.0f, 100.0f);
        c.takeDamage(100.0f);
        assertEquals(100.0f, c.getHealth(), 1e-4f);

        c.heal(50.0f);
        assertEquals(150.0f, c.getHealth(), 1e-4f);

        // Cap at maxHealth
        c.heal(100.0f);
        assertEquals(200.0f, c.getHealth(), 1e-4f);

        // Overkill to death
        c.takeDamage(500.0f);
        assertFalse(c.isAlive());
        assertEquals(0.0f, c.getHealth(), 1e-4f);

        // Dead character cannot heal
        c.heal(50.0f);
        assertEquals(0.0f, c.getHealth(), 1e-4f);
        assertFalse(c.isAlive());
    }

    @Test
    @DisplayName("Knockback exponential decay formula: K *= exp(-7.0 * dt) and snap to zero")
    public void testKnockbackExponentialDecay() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 0.0f, 100.0f);
        c.applyKnockback(new Vector2D(100.0f, 0.0f));
        assertEquals(100.0f, c.getKnockback().x, 1e-4f);

        // Advance 0.1s: decay = exp(-7.0 * 0.1) = exp(-0.7) ≈ 0.4965853
        c.updateKnockback(0.1f);
        float expected = 100.0f * (float) Math.exp(-0.7);
        assertEquals(expected, c.getKnockback().x, 1e-3f);

        // Snap to zero when magnitude squared < 1.0
        c.setKnockback(0.9f, 0.0f);
        c.updateKnockback(0.016f);
        assertEquals(0.0f, c.getKnockback().x, 1e-4f);
        assertEquals(0.0f, c.getKnockback().y, 1e-4f);
    }

    @Test
    @DisplayName("Knockback resistance reduces applied impulse")
    public void testKnockbackResistance() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 0.0f, 100.0f);
        c.setKnockbackResistance(0.4f);
        c.applyKnockback(new Vector2D(100.0f, 0.0f));
        assertEquals(60.0f, c.getKnockback().x, 1e-4f);

        // Full immunity
        c.setKnockback(0, 0);
        c.setKnockbackResistance(1.0f);
        c.applyKnockback(new Vector2D(100.0f, 0.0f));
        assertEquals(0.0f, c.getKnockback().x, 1e-4f);
    }

    @Test
    @DisplayName("Frame-rate independence of knockback decay")
    public void testKnockbackFrameRateIndependence() {
        DummyCharacter c1 = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 0.0f, 100.0f);
        DummyCharacter c2 = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 0.0f, 100.0f);

        c1.applyKnockback(new Vector2D(200.0f, 150.0f));
        c2.applyKnockback(new Vector2D(200.0f, 150.0f));

        // c1 updated in 1 large step of dt = 0.5s
        c1.updateKnockback(0.5f);

        // c2 updated in 50 micro-steps of dt = 0.01s
        for (int i = 0; i < 50; i++) {
            c2.updateKnockback(0.01f);
        }

        assertEquals(c1.getKnockback().x, c2.getKnockback().x, 1e-3f);
        assertEquals(c1.getKnockback().y, c2.getKnockback().y, 1e-3f);
    }

    @Test
    @DisplayName("Slow resistance speed scaling")
    public void testSlowResistanceSpeedScaling() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 0.0f, 100.0f);
        c.setSpeedMultiplier(0.5f); // 50% slow
        c.setSlowResistance(0.4f);  // 40% slow resistance

        // Effective factor = 1 - (1 - 0.5) * (1 - 0.4) = 1 - 0.5 * 0.6 = 0.70
        // Current speed = 100 * 0.70 = 70.0
        assertEquals(70.0f, c.getCurrentMoveSpeed(), 1e-4f);
    }

    @Test
    @DisplayName("Hitflash timer countdown")
    public void testHitFlashTimerCountdown() {
        DummyCharacter c = new DummyCharacter(new Vector2D(0, 0), 20.0f, 100.0f, 0.0f, 100.0f);
        c.takeDamage(10.0f);
        assertTrue(c.isHitFlashing());
        assertEquals(0.12f, c.getHitFlashTimer(), 1e-4f);

        c.update(0.06f);
        assertTrue(c.isHitFlashing());
        assertEquals(0.06f, c.getHitFlashTimer(), 1e-4f);

        c.update(0.07f);
        assertFalse(c.isHitFlashing());
        assertEquals(0.0f, c.getHitFlashTimer(), 1e-4f);
    }

    // =========================================================================
    // Tier 1 & 2: Player Controls & Mechanics Tests
    // =========================================================================

    @Test
    @DisplayName("Player default statistics match primary dispatch specification")
    public void testPlayerDefaultStatistics() {
        Player p = new Player(new Vector2D(640, 360));
        assertEquals(250.0f, p.getHealth(), 1e-4f);
        assertEquals(250.0f, p.getMaxHealth(), 1e-4f);
        assertEquals(30.0f, p.getArmor(), 1e-4f);
        assertEquals(230.0f, p.getBaseMoveSpeed(), 1e-4f);
        assertEquals(20.0f, p.getRadius(), 1e-4f);
        assertEquals(45.0f, p.getPickupRadius(), 1e-4f);
        assertEquals(0.28f, p.getBaseCastCooldown(), 1e-4f);
        assertEquals(ShootingMode.MANUAL, p.getShootingMode());
    }

    @Test
    @DisplayName("Player custom constructor initializes correctly")
    public void testPlayerCustomConstructor() {
        Player p = new Player(new Vector2D(100, 100), 100.0f, 0.0f, 180.0f, 50.0f);
        assertEquals(100.0f, p.getHealth(), 1e-4f);
        assertEquals(0.0f, p.getArmor(), 1e-4f);
        assertEquals(180.0f, p.getBaseMoveSpeed(), 1e-4f);
        assertEquals(50.0f, p.getPickupRadius(), 1e-4f);
    }

    @ParameterizedTest
    @CsvSource({
            "true, false, false, false, 0.0, -1.0",
            "false, true, false, false, 0.0, 1.0",
            "false, false, true, false, -1.0, 0.0",
            "false, false, false, true, 1.0, 0.0"
    })
    @DisplayName("WASD cardinal movement direction vectors")
    public void testWASDCardinalMovement(boolean up, boolean down, boolean left, boolean right,
                                         float expX, float expY) {
        Vector2D dir = Player.computeMovementVector(up, down, left, right);
        assertEquals(expX, dir.x, 1e-4f);
        assertEquals(expY, dir.y, 1e-4f);
    }

    @Test
    @DisplayName("WASD diagonal movement vector is strictly normalized to magnitude 1.0")
    public void testWASDDiagonalNormalization() {
        // Up + Right (W + D) -> (1/sqrt(2), -1/sqrt(2))
        Vector2D dir = Player.computeMovementVector(true, false, false, true);
        float expected = (float) (1.0 / Math.sqrt(2.0));
        assertEquals(expected, dir.x, 1e-4f);
        assertEquals(-expected, dir.y, 1e-4f);
        assertEquals(1.0f, dir.len(), 1e-4f);
    }

    @Test
    @DisplayName("Opposing keys cancel movement to exact zero vector")
    public void testOpposingKeysCancelMovement() {
        Vector2D upDown = Player.computeMovementVector(true, true, false, false);
        assertEquals(0.0f, upDown.x, 1e-4f);
        assertEquals(0.0f, upDown.y, 1e-4f);

        Vector2D leftRight = Player.computeMovementVector(false, false, true, true);
        assertEquals(0.0f, leftRight.x, 1e-4f);
        assertEquals(0.0f, leftRight.y, 1e-4f);

        Vector2D allFour = Player.computeMovementVector(true, true, true, true);
        assertEquals(0.0f, allFour.x, 1e-4f);
        assertEquals(0.0f, allFour.y, 1e-4f);
    }

    @Test
    @DisplayName("Facing direction quadrant mapping matching LibGDX y-down orientation")
    public void testFacingDirectionQuadrantMapping() {
        Player p = new Player(new Vector2D(500, 500));

        // East (+X) -> RIGHT
        p.setAimTarget(600, 500);
        assertEquals(Player.FacingDirection.RIGHT, p.getFacingDirection());

        // South (+Y) -> DOWN
        p.setAimTarget(500, 600);
        assertEquals(Player.FacingDirection.DOWN, p.getFacingDirection());

        // West (-X) -> LEFT
        p.setAimTarget(400, 500);
        assertEquals(Player.FacingDirection.LEFT, p.getFacingDirection());

        // North (-Y) -> UP
        p.setAimTarget(500, 400);
        assertEquals(Player.FacingDirection.UP, p.getFacingDirection());
    }

    @Test
    @DisplayName("Zero-length aim delta protection prevents NaN and preserves direction")
    public void testZeroLengthAimDeltaProtection() {
        Player p = new Player(new Vector2D(500, 500));
        p.setAimTarget(600, 500); // Right: angle 0
        assertEquals(0.0f, p.getAimAngle(), 1e-4f);

        // Aim directly at player position (dist = 0)
        p.setAimTarget(500, 500);
        assertFalse(Float.isNaN(p.getAimAngle()));
        assertEquals(0.0f, p.getAimAngle(), 1e-4f);
    }

    @Test
    @DisplayName("Spellcasting cooldown lifecycle and multiplier scaling")
    public void testCastCooldownLifecycle() {
        Player p = new Player(new Vector2D(0, 0));
        assertTrue(p.canCast());

        p.triggerCast();
        assertFalse(p.canCast());
        assertEquals(0.28f, p.getCastTimer(), 1e-4f);

        p.update(0.14f);
        assertFalse(p.canCast());
        assertEquals(0.14f, p.getCastTimer(), 1e-4f);

        p.update(0.15f);
        assertTrue(p.canCast());
        assertEquals(0.0f, p.getCastTimer(), 1e-4f);

        // Cooldown multiplier scaling (e.g. 0.5x cooldown perk)
        p.triggerCast(0.5f);
        assertEquals(0.14f, p.getCastTimer(), 1e-4f);
    }

    @Test
    @DisplayName("Player upgrade multipliers and bonus pierce clamping")
    public void testPlayerUpgradeMultipliers() {
        Player p = new Player(new Vector2D(0, 0));

        p.multiplyDamage(1.2f);
        assertEquals(1.2f, p.getDamageMultiplier(), 1e-4f);

        p.multiplyProjectileSpeed(1.3f);
        assertEquals(1.3f, p.getProjectileSpeedMultiplier(), 1e-4f);

        p.multiplyProjectileLifetime(1.5f);
        assertEquals(1.5f, p.getProjectileLifetimeMultiplier(), 1e-4f);

        p.increasePickupRadius(15.0f);
        assertEquals(60.0f, p.getPickupRadius(), 1e-4f);

        p.addBonusPierce(1);
        assertEquals(1, p.getBonusPierce());

        // Max pierce clamped to 2
        p.addBonusPierce(5);
        assertEquals(2, p.getBonusPierce());
    }

    @Test
    @DisplayName("Concurrent knockback and WASD movement combination")
    public void testConcurrentKnockbackAndMovement() {
        Player p = new Player(new Vector2D(500, 500));
        p.applyKnockback(new Vector2D(-100.0f, 0.0f));

        // Moving right at 230 px/s with -100 px/s knockback
        p.handleMovementInput(false, false, false, true, 0.1f, null);

        // Net velocity = 230 - 100 = 130 px/s, pos = 500 + 130 * 0.1 = 513
        assertEquals(513.0f, p.getX(), 1e-2f);
    }

    @Test
    @DisplayName("Death disables movement and casting")
    public void testDeathDisablesMovementAndCasting() {
        Player p = new Player(new Vector2D(500, 500));
        p.takeDamage(9999.0f);
        assertFalse(p.isAlive());

        p.handleMovementInput(true, false, false, false, 0.1f, null);
        assertEquals(500.0f, p.getY(), 1e-4f);
        assertEquals(0.0f, p.getVelocity().y, 1e-4f);

        p.triggerCast();
        assertFalse(p.canCast());
    }

    // =========================================================================
    // Tier 3 & 4: TileMap Collision Sliding Integration
    // =========================================================================

    @Test
    @DisplayName("Sliding collision with TileMap preserves tangential axis movement")
    public void testTileMapSlidingCollision() {
        TileMap tileMap = new TileMap(42);
        Player p = new Player(new Vector2D(2048, 2048));

        // Move player diagonally near boundary or obstacles
        Vector2D startPos = p.getPosition().cpy();
        p.moveAndResolve(new Vector2D(1, 1), 0.05f, tileMap);

        // Player must have moved and remained traversable
        assertTrue(p.getPosition().dist2(startPos) > 0.0f);
        assertTrue(tileMap.isTraversable(p.getX(), p.getY(), p.getRadius()));
    }
}
