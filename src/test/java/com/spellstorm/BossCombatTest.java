package com.spellstorm;

import com.spellstorm.combat.DamageInfo;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Automated test suite covering BossEnemy stats, archetype classification,
 * durability and size ratios vs TankEnemy, 3-way spread volley attack mechanics,
 * armor mitigation, and idempotent defeat reward claiming.
 * Ports all assertions from C++ test_combat.cpp:61-100.
 */
public class BossCombatTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // 1. Boss Identity, Archetype Classification, and Naming
    // =========================================================================

    @Test
    @DisplayName("Boss identity, type, boss flag, and name parity (C++ test_combat.cpp:63-70)")
    public void testBossIdentityAndArchetype() {
        BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 500.0f));
        TankEnemy tank = new TankEnemy(new Vector2D(600.0f, 600.0f));

        assertTrue(boss.isBoss(), "BossEnemy.isBoss() must return true");
        assertFalse(tank.isBoss(), "TankEnemy.isBoss() must return false");
        assertEquals(EnemyType.BOSS, boss.getType());
        assertEquals(EnemyType.BOSS, boss.type());

        // Name verification: C++ reference specifies "ANCIENT GOLEM OVERLORD"
        assertEquals("ANCIENT GOLEM OVERLORD", boss.getName());
        assertEquals("ANCIENT GOLEM OVERLORD", boss.name());
    }

    // =========================================================================
    // 2. Boss Durability & Collision Size vs Tank
    // =========================================================================

    @Test
    @DisplayName("Boss durability ratio: 8-15x ordinary tank enemy health (C++ test_combat.cpp:72-76)")
    public void testBossDurabilityVsTank() {
        BossEnemy boss = new BossEnemy(new Vector2D(0.0f, 0.0f));
        TankEnemy tank = new TankEnemy(new Vector2D(0.0f, 0.0f), true); // legacy tank has 200 HP

        assertEquals(1800.0f, boss.getMaxHealth(), 0.01f);
        assertEquals(1800.0f, boss.maxHealth(), 0.01f);
        assertEquals(45.0f, boss.getArmor(), 0.01f);
        assertEquals(55.0f, boss.getBaseMoveSpeed(), 0.01f);
        assertEquals(45.0f, boss.getContactDamage(), 0.01f);
        assertEquals(1.0f, boss.getAttackCooldown(), 0.01f);

        // 8x to 15x Tank health range
        assertTrue(boss.getMaxHealth() >= tank.getMaxHealth() * 8.0f,
                "Boss health must be >= 8x Tank health");
        assertTrue(boss.getMaxHealth() <= tank.getMaxHealth() * 15.0f,
                "Boss health must be <= 15x Tank health");
    }

    @Test
    @DisplayName("Boss size verification: substantially larger collision radius (C++ test_combat.cpp:78-79)")
    public void testBossRadiusAndHitbox() {
        BossEnemy boss = new BossEnemy(new Vector2D(0.0f, 0.0f));
        TankEnemy tank = new TankEnemy(new Vector2D(0.0f, 0.0f));

        assertEquals(50.0f, boss.getRadius(), 0.01f);
        assertEquals(50.0f, boss.radius(), 0.01f);
        assertTrue(boss.getRadius() >= tank.getRadius() * 1.8f,
                "Boss radius must be >= 1.8x Tank radius");
    }

    // =========================================================================
    // 3. 3-Way Arcane Volley Attack
    // =========================================================================

    @Test
    @DisplayName("Boss 3-way spread volley attack: count=3, cooldown=4s, 24 deg spread, faction=Enemy (C++ test_combat.cpp:81-89)")
    public void testBossVolleyMechanics() {
        BossEnemy boss = new BossEnemy(new Vector2D(500.0f, 300.0f));
        assertTrue(boss.canFireVolley());

        Vector2D targetPos = new Vector2D(500.0f, 100.0f); // Directly above boss
        List<Projectile> volley = boss.fireVolley(targetPos);

        assertEquals(3, volley.size(), "Boss volley must produce exactly 3 projectiles");
        assertFalse(boss.canFireVolley(), "canFireVolley() must become false immediately after firing");
        assertEquals(4.0f, boss.getVolleyTimer(), 0.01f);

        // Verify projectile attributes
        for (Projectile p : volley) {
            assertEquals(ProjectileFaction.ENEMY, p.getFaction());
            assertEquals(20.0f, p.getDamage(), 0.01f);
            assertEquals(210.0f, p.getSpeed(), 0.01f);
            assertEquals(10.0f, p.getRadius(), 0.01f);
            assertEquals(3.2f, p.getLifetime(), 0.01f);
            assertEquals(0, p.getPierceRemaining());
            assertTrue(p.isAlive());
        }

        // Verify angular spread: middle projectile aims toward target (angle ~ -90° / -PI/2)
        // Left and right projectiles are spaced by exactly 24 degrees (0.418879 rad)
        float spreadRad = MathUtils.degToRad(24.0f);
        float angle0 = volley.get(0).getDirection().angleRad();
        float angle1 = volley.get(1).getDirection().angleRad();
        float angle2 = volley.get(2).getDirection().angleRad();

        assertEquals(spreadRad, Math.abs(angle1 - angle0), 0.01f);
        assertEquals(spreadRad, Math.abs(angle2 - angle1), 0.01f);

        // Advance 2.0s: volleyTimer drops to 2.0s, still cannot fire
        boss.update(2.0f);
        assertFalse(boss.canFireVolley());
        assertEquals(2.0f, boss.getVolleyTimer(), 0.01f);

        // Advance another 2.1s: volleyTimer expires, ready to fire again
        boss.update(2.1f);
        assertTrue(boss.canFireVolley());
    }

    // =========================================================================
    // 4. Damage Mitigation, Defeat, and Reward Claiming
    // =========================================================================

    @Test
    @DisplayName("Boss damage mitigation and finishing blow (C++ test_combat.cpp:92-99)")
    public void testBossDamageAndDefeat() {
        BossEnemy boss = new BossEnemy(new Vector2D(0.0f, 0.0f));

        // 900 raw damage mitigated by 45 armor: 900 * 100 / 145 = 620.69
        // Remaining health = 1800 - 620.69 = 1179.31
        // Ratio = 1179.31 / 1800 = 0.65517 (within 0.05 of reference ratio 0.697)
        float dealt = boss.takeDamage(new DamageInfo(900.0f, false));
        assertEquals(620.69f, dealt, 0.5f);
        assertEquals(1179.31f, boss.getHealth(), 0.5f);
        assertTrue(boss.isAlive());
        assertEquals(0.655f, boss.getHealthRatio(), 0.05f);

        // Critical finishing blow: 2000 unmitigated true damage
        boss.takeDamage(new DamageInfo(2000.0f, true));
        assertFalse(boss.isAlive(), "Boss must be defeated when health drops to 0");
        assertEquals(0.0f, boss.getHealth(), 0.01f);

        // Experience reward is 250 and idempotent
        assertEquals(250, boss.getExpReward());
        assertEquals(250, boss.claimReward());
        assertEquals(0, boss.claimReward(), "Claiming reward again must return 0 (idempotent guarantee)");
    }
}
