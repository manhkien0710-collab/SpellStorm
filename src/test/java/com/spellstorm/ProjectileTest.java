package com.spellstorm;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.combat.CombatSystem;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite covering Projectile kinematics, delta-time physics,
 * max 3 piercing limit, 20% damage falloff per hit, 25% damage floor clamping,
 * multi-hit entity tracking, and CombatSystem bounds pruning.
 */
public class ProjectileTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. Kinematics, Lifetime, and Zero-Vector Safety
    // =========================================================================

    @Test
    @DisplayName("Projectile velocity integration and lifetime decay (C++ test_simulation.cpp parity)")
    public void testProjectileKinematicsAndLifetime() {
        Vector2D startPos = new Vector2D(100.0f, 150.0f);
        Vector2D dir = new Vector2D(1.0f, 0.0f);
        Projectile p = new Projectile(startPos, dir, 300.0f, 8.0f, 25.0f, 1.5f, 2, ProjectileFaction.PLAYER);

        assertEquals(100.0f, p.getX(), 0.001f);
        assertEquals(150.0f, p.getY(), 0.001f);
        assertEquals(300.0f, p.getSpeed(), 0.001f);
        assertEquals(8.0f, p.getRadius(), 0.001f);
        assertEquals(25.0f, p.getDamage(), 0.001f);
        assertEquals(1.5f, p.getLifetime(), 0.001f);
        assertEquals(2, p.getPierceRemaining());
        assertTrue(p.isAlive());

        // Step 0.1s: pos moves +30 in x, lifetime decreases to 1.4s
        p.update(0.1f);
        assertEquals(130.0f, p.getX(), 0.001f);
        assertEquals(150.0f, p.getY(), 0.001f);
        assertEquals(1.4f, p.getLifetime(), 0.001f);
        assertTrue(p.isAlive());

        // Step 1.45s: total 1.55s > 1.5s -> lifetime expired and destroyed
        p.update(1.45f);
        assertFalse(p.isAlive());
    }

    @Test
    @DisplayName("Zero-length direction falls back safely to (1, 0) without NaN or division by zero")
    public void testZeroDirectionSafety() {
        Projectile p = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(0.0f, 0.0f), 200.0f, 8.0f, 20.0f, 1.0f, 0, ProjectileFaction.PLAYER);
        Vector2D dir = p.getDirection();
        assertEquals(1.0f, dir.x, 1e-4f);
        assertEquals(0.0f, dir.y, 1e-4f);
        assertFalse(Float.isNaN(p.getVelocity().x));
        assertFalse(Float.isNaN(p.getVelocity().y));
    }

    // =========================================================================
    // 2. Piercing Limits, 20% Damage Falloff, and 25% Damage Floor
    // =========================================================================

    @Test
    @DisplayName("Balancing constants match C++ specification exactly")
    public void testBalancingConstants() {
        assertEquals(3, Projectile.MAX_ALLOWED_PIERCE);
        assertEquals(3, Projectile.kMaxAllowedPierce);
        assertEquals(0.80f, Projectile.PIERCE_DAMAGE_FALLOFF, 0.001f);
        assertEquals(0.80f, Projectile.kPierceDamageFalloff, 0.001f);
        assertEquals(0.25f, Projectile.MIN_DAMAGE_MULTIPLIER, 0.001f);
        assertEquals(0.25f, Projectile.kMinDamageMultiplier, 0.001f);
    }

    @Test
    @DisplayName("Non-piercing projectile (pierce = 0) is destroyed on first hit (C++ test_progression.cpp parity)")
    public void testNonPiercingProjectileDestruction() {
        Projectile p = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(1.0f, 0.0f),
                100.0f, 10.0f, 50.0f, 2.0f, 0, ProjectileFaction.PLAYER);

        assertEquals(0, p.getPierceRemaining());
        assertEquals(0, p.getHitCount());
        assertEquals(50.0f, p.getDamage(), 0.01f);
        assertTrue(p.isAlive());

        p.recordHit(10);
        p.decrementPierce();
        assertFalse(p.isAlive());
        assertEquals(1, p.getHitCount());
    }

    @Test
    @DisplayName("Piercing projectile (pierce = 2, base damage = 100) sequence with 20% falloff")
    public void testPiercingSequenceFalloff() {
        Projectile p = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(1.0f, 0.0f),
                100.0f, 10.0f, 100.0f, 2.0f, 2, ProjectileFaction.PLAYER);

        assertEquals(2, p.getPierceRemaining());
        assertEquals(100.0f, p.getDamage(), 0.01f);

        // Hit 1: pierce goes 2 -> 1, damage reduced by 20% (100 * 0.8 = 80)
        p.recordHit(21);
        p.decrementPierce();
        assertTrue(p.isAlive());
        assertEquals(1, p.getPierceRemaining());
        assertEquals(1, p.getHitCount());
        assertEquals(80.0f, p.getDamage(), 0.01f);

        // Hit 2: pierce goes 1 -> 0, damage reduced by another 20% (80 * 0.8 = 64)
        p.recordHit(22);
        p.decrementPierce();
        assertTrue(p.isAlive());
        assertEquals(0, p.getPierceRemaining());
        assertEquals(2, p.getHitCount());
        assertEquals(64.0f, p.getDamage(), 0.01f);

        // Hit 3: pierce 0 exhausted -> projectile destroyed
        p.recordHit(23);
        p.decrementPierce();
        assertFalse(p.isAlive());
        assertEquals(3, p.getHitCount());
    }

    @Test
    @DisplayName("Damage floor clamping: damage never drops below 25% of initial damage")
    public void testDamageFloorClamping() {
        // Artificially high pierce (10) is clamped to MAX_ALLOWED_PIERCE (3) in constructor
        Projectile p = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(1.0f, 0.0f),
                100.0f, 10.0f, 100.0f, 2.0f, 10, ProjectileFaction.PLAYER);
        assertEquals(3, p.getPierceRemaining(), "Constructor must clamp pierce to MAX_ALLOWED_PIERCE (3)");

        // Decrement multiple times
        for (int i = 0; i < 3; i++) {
            p.decrementPierce();
        }
        assertTrue(p.getDamage() >= 25.0f, "Damage must not fall below 25% of initial damage");
    }

    // =========================================================================
    // 3. Multi-Hit Entity Tracking & Collision Resolution
    // =========================================================================

    @Test
    @DisplayName("Multi-hit prevention: entity struck once cannot be hit again by the same projectile")
    public void testMultiHitPrevention() {
        Projectile p = new Projectile(new Vector2D(0.0f, 0.0f), new Vector2D(1.0f, 0.0f),
                100.0f, 10.0f, 50.0f, 2.0f, 2, ProjectileFaction.PLAYER);

        assertTrue(p.canHit(101));
        p.recordHit(101);
        assertFalse(p.canHit(101), "Target 101 has already been struck and must return false");
        assertTrue(p.canHit(102), "Different target 102 must return true");
    }

    @Test
    @DisplayName("CombatSystem out-of-bounds missile boundary pruning")
    public void testCombatSystemBoundaryPruning() {
        CombatSystem cs = new CombatSystem(new Rectangle(0, 0, 1000, 1000));
        Player player = new Player(new Vector2D(500, 500));
        List<com.spellstorm.entities.Enemy> enemies = new ArrayList<>();

        // Inside bounds projectile
        Projectile pInside = new Projectile(new Vector2D(500, 500), new Vector2D(1, 0), 100, 10, 20, 2.0f, 0, ProjectileFaction.PLAYER);
        // Outside bounds projectile
        Projectile pOutside = new Projectile(new Vector2D(1500, 500), new Vector2D(1, 0), 100, 10, 20, 2.0f, 0, ProjectileFaction.PLAYER);

        cs.addProjectile(pInside);
        cs.addProjectile(pOutside);
        assertEquals(2, cs.projectileCount());

        cs.update(0.05f, player, enemies);
        assertEquals(1, cs.projectileCount(), "Outside projectile should be pruned from combat system");
        assertSame(pInside, cs.projectiles().get(0));
    }
}
