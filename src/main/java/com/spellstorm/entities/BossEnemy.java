package com.spellstorm.entities;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.navigation.AStarPathfinder;
import com.spellstorm.world.TileMap;

import java.util.ArrayList;
import java.util.List;

/**
 * Imposing, high-durability boss encounter: Colossus Overlord (Ancient Golem Overlord).
 * Features:
 * - Massive health (1800 HP), heavy armor (45), contact damage (45), and 250 EXP reward.
 * - Periodic 3-way arcane volley attack (24° spread, speed 210, radius 10, damage 20) every 4.0 seconds.
 * - M8 navigation awareness across river bridges and around obstacles with radius 50.
 */
public class BossEnemy extends Enemy {

    public static final float BOSS_RADIUS = 50.0f;
    public static final float BOSS_MAX_HEALTH = 1800.0f;
    public static final float BOSS_ARMOR = 45.0f;
    public static final float BOSS_MOVE_SPEED = 55.0f;
    public static final float BOSS_CONTACT_DAMAGE = 45.0f;
    public static final float BOSS_ATTACK_COOLDOWN = 1.0f;
    public static final int BOSS_EXP_REWARD = 250;
    public static final float VOLLEY_COOLDOWN = 4.0f;

    // C++ reference naming aliases
    public static final float kBossRadius = BOSS_RADIUS;
    public static final float kBossMaxHealth = BOSS_MAX_HEALTH;
    public static final float kBossArmor = BOSS_ARMOR;
    public static final float kBossMoveSpeed = BOSS_MOVE_SPEED;
    public static final float kBossContactDamage = BOSS_CONTACT_DAMAGE;
    public static final float kBossAttackCooldown = BOSS_ATTACK_COOLDOWN;
    public static final int kBossExpReward = BOSS_EXP_REWARD;

    private float volleyCooldown = VOLLEY_COOLDOWN;
    private float volleyTimer = 0.0f;

    public BossEnemy(Vector2D position) {
        super(EnemyType.BOSS, position, BOSS_RADIUS, BOSS_MAX_HEALTH, BOSS_ARMOR,
                BOSS_MOVE_SPEED, BOSS_CONTACT_DAMAGE, BOSS_ATTACK_COOLDOWN, BOSS_EXP_REWARD);
    }

    @Override
    public boolean isBoss() {
        return true;
    }

    @Override
    public String getName() {
        return "ANCIENT GOLEM OVERLORD";
    }

    @Override
    public String name() {
        return "ANCIENT GOLEM OVERLORD";
    }

    public float getVolleyCooldown() {
        return volleyCooldown;
    }

    public void setVolleyCooldown(float cooldown) {
        this.volleyCooldown = Math.max(0.05f, cooldown);
    }

    public boolean canFireVolley() {
        return isAlive() && volleyTimer <= 0.0f;
    }

    public float getVolleyTimer() {
        return volleyTimer;
    }

    public float volleyTimer() {
        return volleyTimer;
    }

    public void setVolleyTimer(float timer) {
        this.volleyTimer = Math.max(0.0f, timer);
    }

    public void resetVolleyTimer() {
        this.volleyTimer = this.volleyCooldown;
    }

    /**
     * Executes the boss special volley attack: fires 3 heavy arcane missiles
     * in a 24-degree fan spread centered around targetPos.
     *
     * @param targetPos target aim position
     * @return list of 3 newly created enemy projectiles
     */
    public List<Projectile> fireVolley(Vector2D targetPos) {
        this.volleyTimer = this.volleyCooldown;
        if (targetPos != null) {
            updateFacing(targetPos);
        }
        List<Projectile> projectiles = new ArrayList<>(3);

        Vector2D pPos = getPosition();
        Vector2D delta = (targetPos != null) ? Vector2D.sub(targetPos, pPos) : new Vector2D(1.0f, 0.0f);
        Vector2D baseDir = (delta.len2() > 1e-4f) ? delta.nor() : new Vector2D(1.0f, 0.0f);
        float baseAngle = baseDir.angleRad();

        float spread = MathUtils.degToRad(24.0f);
        Color primary = new Color(1.0f, 60f / 255f, 40f / 255f, 1.0f);
        Color secondary = new Color(1.0f, 180f / 255f, 50f / 255f, 1.0f);

        for (int i = -1; i <= 1; ++i) {
            float angle = baseAngle + (float) i * spread;
            Vector2D dir = Vector2D.fromAngle(angle);
            Vector2D spawnPos = pPos.cpy().mulAdd(dir, getRadius() + 8.0f);

            projectiles.add(new Projectile(
                    spawnPos, dir, 210.0f, 10.0f, 20.0f, 3.2f, 0,
                    ProjectileFaction.ENEMY, primary, secondary, null
            ));
        }

        return projectiles;
    }

    @Override
    public void update(float dt) {
        super.update(dt);
        if (volleyTimer > 0.0f) {
            volleyTimer = Math.max(0.0f, volleyTimer - dt);
        }
    }

    @Override
    public void updateAI(float dt, Vector2D targetPos, TileMap tileMap, AStarPathfinder pathfinder) {
        if (!isAlive()) {
            return;
        }
        steerWithNavigation(dt, targetPos, tileMap, pathfinder, 1.0f);
    }
}
