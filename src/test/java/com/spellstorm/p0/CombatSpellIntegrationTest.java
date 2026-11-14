package com.spellstorm.p0;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.combat.*;
import com.spellstorm.entities.*;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * P0 Integration tests for Defect D1:
 * 1. Player spell projectiles deal direct damage using p.getDamage() respecting pierce falloff.
 * 2. Burn status refresh preserves tick cadence instead of resetting timer to 0.5s.
 * 3. Continuous Firebolt fire at 0.28s cadence kills a 1000 HP dummy.
 * 4. Pierce falloff properly attenuates direct damage on secondary targets.
 * 5. Combination spell Firestorm semantics (direct hit + AoE explosion).
 */
public class CombatSpellIntegrationTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    private static class DummyEnemy extends MeleeEnemy {
        public DummyEnemy(Vector2D pos, float health, float armor) {
            super(pos, health, 0.0f, 0.0f, 16.0f, 1.0f, 10);
            setArmor(armor);
        }
    }

    @ParameterizedTest
    @EnumSource(value = Element.class, names = {"FIRE", "ICE", "LIGHTNING", "WIND", "EARTH"})
    @DisplayName("D1.1: Each of the 5 base spells deals direct projectile damage to dummy on hit")
    public void testBaseSpellsDealDirectDamage(Element element) {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        SpellDefinition spell = registry.baseSpell(element);
        assertNotNull(spell, "Base spell for " + element + " must exist");

        Rectangle arena = new Rectangle(0, 0, 4096, 4096);
        CombatSystem combatSystem = new CombatSystem(arena);

        Player player = new Player(new Vector2D(500, 500));
        DummyEnemy dummy = new DummyEnemy(new Vector2D(550, 500), 500.0f, 0.0f);
        List<Enemy> enemies = new ArrayList<>(List.of(dummy));

        // Create player projectile with this spell
        ProjectileProfile profile = spell.getProfile();
        float damage = 35.0f * (profile != null ? profile.getDamageMultiplier() : 1.0f);
        float speed = 400.0f;
        float radius = 10.0f;
        int pierce = profile != null ? profile.getPierce() : 0;

        Projectile proj = new Projectile(
                new Vector2D(545, 500), new Vector2D(1, 0), speed, radius, damage, 1.0f, pierce,
                ProjectileFaction.PLAYER, spell.getPrimaryColor(), spell.getSecondaryColor(), spell
        );
        combatSystem.addProjectile(proj);

        float initialHp = dummy.getHealth();
        // Update combat system by 1 frame to trigger collision (distance is 5px, radii are 10 + 16 = 26px)
        combatSystem.update(0.016f, player, enemies);

        float damageDealt = initialHp - dummy.getHealth();
        assertTrue(damageDealt > 0.0f,
                "Spell " + spell.getName() + " must deal direct damage on hit! Actual damage: " + damageDealt);
        assertEquals(damage, damageDealt, 0.01f,
                "Spell " + spell.getName() + " direct hit must deal expected damage " + damage);
    }

    @Test
    @DisplayName("D1.2: BurnStatus refresh preserves tick cadence instead of resetting timer to 0.5s")
    public void testBurnStatusRefreshPreservesTickCadence() {
        StatusEffectList list = new StatusEffectList();
        DummyEnemy dummy = new DummyEnemy(new Vector2D(100, 100), 200.0f, 0.0f);

        // Apply initial burn: 20 DPS, 3.0s duration -> 10 damage per 0.5s tick
        BurnStatus initialBurn = new BurnStatus(20.0f, 3.0f);
        list.add(initialBurn);

        // Advance 0.3s -> tickTimer drops from 0.5s to 0.2s. No damage yet.
        list.update(0.3f, dummy);
        assertEquals(200.0f, dummy.getHealth(), 0.001f);

        // Re-apply burn at t=0.3s (simulating Firebolt re-hit at 0.28s-0.3s)
        BurnStatus refreshedBurn = new BurnStatus(20.0f, 3.0f);
        list.add(refreshedBurn);

        // Advance another 0.25s (total elapsed 0.55s).
        // If cadence was preserved, tick occurred at t=0.50s (0.20s after refresh) dealing 10 damage!
        // If timer was reset to 0.5s, remaining timer would be 0.25s and damage would be 0!
        list.update(0.25f, dummy);

        assertEquals(190.0f, dummy.getHealth(), 0.01f,
                "Burn tick should have fired at t=0.50s dealing 10 true damage even with refresh at t=0.30s");
    }

    @Test
    @DisplayName("D1.3: Continuous Firebolt at 0.28s cadence kills a 1000 HP dummy (burn ticks fire)")
    public void testContinuousFireboltKills1000HpDummy() {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        SpellDefinition firebolt = registry.baseSpell(Element.FIRE);
        assertNotNull(firebolt);

        Rectangle arena = new Rectangle(0, 0, 4096, 4096);
        CombatSystem combatSystem = new CombatSystem(arena);

        Player player = new Player(new Vector2D(500, 500));
        DummyEnemy dummy = new DummyEnemy(new Vector2D(600, 500), 1000.0f, 0.0f);
        List<Enemy> enemies = new ArrayList<>(List.of(dummy));

        float fireInterval = 0.28f;
        float timeSinceLastShot = fireInterval; // Ready to shoot immediately
        float totalSimulatedTime = 0.0f;
        float dt = 0.016f; // 60 FPS frame step
        float maxAllowedTime = 12.0f; // Sufficient time to kill 1000 HP

        int shotsFired = 0;

        while (dummy.isAlive() && totalSimulatedTime < maxAllowedTime) {
            timeSinceLastShot += dt;
            if (timeSinceLastShot >= fireInterval) {
                timeSinceLastShot -= fireInterval;
                // Spawn Firebolt projectile moving towards dummy
                Projectile p = new Projectile(
                        new Vector2D(590, 500), new Vector2D(1, 0), 400.0f, 9.0f, 35.0f, 1.2f, 0,
                        ProjectileFaction.PLAYER, firebolt.getPrimaryColor(), firebolt.getSecondaryColor(), firebolt
                );
                combatSystem.addProjectile(p);
                shotsFired++;
            }

            // Update combat system (moves projectiles, detects hits, applies damage)
            combatSystem.update(dt, player, enemies);

            // Update dummy status effects (ticks burn)
            dummy.update(dt);

            totalSimulatedTime += dt;
        }

        assertTrue(!dummy.isAlive(),
                "Continuous Firebolt must kill 1000 HP dummy within " + maxAllowedTime + "s! " +
                        "Remaining HP: " + dummy.getHealth() + " after " + shotsFired + " shots");
        assertTrue(totalSimulatedTime < maxAllowedTime, "Should kill well before timeout");
    }

    @Test
    @DisplayName("D1.4: Pierce falloff applies to direct spell damage on multi-target piercing projectiles")
    public void testPierceFalloffAppliesToDirectSpellDamage() {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        SpellDefinition frostShard = registry.baseSpell(Element.ICE); // Pierce = 1

        Rectangle arena = new Rectangle(0, 0, 4096, 4096);
        CombatSystem combatSystem = new CombatSystem(arena);

        Player player = new Player(new Vector2D(500, 500));
        DummyEnemy enemy1 = new DummyEnemy(new Vector2D(550, 500), 500.0f, 0.0f);
        DummyEnemy enemy2 = new DummyEnemy(new Vector2D(650, 500), 500.0f, 0.0f);
        List<Enemy> enemies = new ArrayList<>(List.of(enemy1, enemy2));

        float initialDamage = 35.0f * frostShard.getProfile().getDamageMultiplier(); // 35 * 0.8 = 28.0f
        Projectile proj = new Projectile(
                new Vector2D(540, 500), new Vector2D(1, 0), 500.0f, 10.0f, initialDamage, 1.0f, 1,
                ProjectileFaction.PLAYER, frostShard.getPrimaryColor(), frostShard.getSecondaryColor(), frostShard
        );
        combatSystem.addProjectile(proj);

        // Frame 1: projectile hits enemy1
        combatSystem.update(0.016f, player, enemies);
        float damage1 = 500.0f - enemy1.getHealth();
        assertEquals(initialDamage, damage1, 0.01f, "First target receives full direct spell damage");

        // Move projectile forward towards enemy2
        proj.getPosition().set(640, 500);
        combatSystem.update(0.016f, player, enemies);
        float damage2 = 500.0f - enemy2.getHealth();

        // 20% pierce falloff -> damage2 = initialDamage * 0.80
        float expectedDamage2 = initialDamage * Projectile.PIERCE_DAMAGE_FALLOFF;
        assertEquals(expectedDamage2, damage2, 0.01f,
                "Second target must receive pierce-attenuated damage (80% of initial)");
        assertEquals(0.80f, damage2 / damage1, 0.01f);
    }

    @Test
    @DisplayName("D1.5: Firestorm combination spell deals direct projectile hit damage plus AoE explosion damage")
    public void testFirestormDirectHitAndAoEExplosion() {
        SpellRegistry registry = SpellRegistry.createDefaultRegistry();
        SpellDefinition firestorm = registry.resolve(List.of(Element.FIRE, Element.WIND));
        assertNotNull(firestorm);
        assertEquals("Firestorm (Fire + Wind)", firestorm.getName());

        Rectangle arena = new Rectangle(0, 0, 4096, 4096);
        CombatSystem combatSystem = new CombatSystem(arena);

        Player player = new Player(new Vector2D(500, 500));
        DummyEnemy primaryTarget = new DummyEnemy(new Vector2D(550, 500), 1000.0f, 0.0f);
        DummyEnemy bystander = new DummyEnemy(new Vector2D(580, 500), 1000.0f, 0.0f); // Within 90px radius
        List<Enemy> enemies = new ArrayList<>(List.of(primaryTarget, bystander));

        float baseDamage = 35.0f * firestorm.getProfile().getDamageMultiplier(); // 35 * 1.5 = 52.5f
        Projectile proj = new Projectile(
                new Vector2D(545, 500), new Vector2D(1, 0), 400.0f, 12.0f, baseDamage, 1.0f, 0,
                ProjectileFaction.PLAYER, firestorm.getPrimaryColor(), firestorm.getSecondaryColor(), firestorm
        );
        combatSystem.addProjectile(proj);

        // Update 1 frame to trigger hit
        combatSystem.update(0.016f, player, enemies);

        // Primary target takes direct damage (52.5) + AreaEffect explosion (52.5 * 1.2 = 63.0) = 115.5
        float primaryDamage = 1000.0f - primaryTarget.getHealth();
        float expectedPrimaryDamage = baseDamage + (baseDamage * 1.2f);
        assertEquals(expectedPrimaryDamage, primaryDamage, 0.01f,
                "Primary target must take direct projectile damage + AoE explosion damage");

        // Bystander took only AoE explosion damage (63.0)
        float bystanderDamage = 1000.0f - bystander.getHealth();
        assertEquals(baseDamage * 1.2f, bystanderDamage, 0.01f,
                "Bystander within AoE radius must take explosion damage");
    }
}
