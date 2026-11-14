package com.spellstorm.combat;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.FloatingOriginAware;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * Coordinates in-flight projectiles, hit collisions, on-hit spell effects,
 * arena boundary pruning, and implements CombatServices for area/chain elemental spells.
 */
public class CombatSystem implements CombatServices, FloatingOriginAware {

    private Rectangle arenaBounds = new Rectangle(0, 0, 4096, 4096);
    private final List<Projectile> projectiles = new ArrayList<>();
    private List<Enemy> currentActiveEnemies = null;

    public CombatSystem() {
    }

    public CombatSystem(Rectangle arenaBounds) {
        if (arenaBounds != null) {
            this.arenaBounds = new Rectangle(arenaBounds);
        }
    }

    public void setArenaBounds(Rectangle bounds) {
        if (bounds != null) {
            this.arenaBounds = new Rectangle(bounds);
        }
    }

    public Rectangle getArenaBounds() {
        return new Rectangle(arenaBounds);
    }

    public void addProjectile(Projectile projectile) {
        if (projectile != null) {
            projectiles.add(projectile);
        }
    }

    public void clearProjectiles() {
        projectiles.clear();
    }

    public int projectileCount() {
        return projectiles.size();
    }

    public int getProjectileCount() {
        return projectiles.size();
    }

    public List<Projectile> projectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    public List<Projectile> getProjectiles() {
        return Collections.unmodifiableList(projectiles);
    }

    /**
     * Updates all projectiles, checks collisions with opposing factions, triggers
     * on-hit spell effects, decrements pierce or consumes missiles, and prunes destroyed missiles.
     */
    public void update(float dt, Player player, List<Enemy> enemies) {
        this.currentActiveEnemies = enemies;

        // 1. Update in-flight projectile positions and boundaries
        float margin = 15.0f; // 15px exterior culling margin matching C++ reference
        float minX = arenaBounds.x - margin;
        float maxX = arenaBounds.x + arenaBounds.width + margin;
        float minY = arenaBounds.y - margin;
        float maxY = arenaBounds.y + arenaBounds.height + margin;

        for (Projectile p : projectiles) {
            p.update(dt);
            // Destroy missiles that travel outside arena walls
            Vector2D pos = p.getPosition();
            if (pos.x < minX || pos.x > maxX || pos.y < minY || pos.y > maxY) {
                p.destroy();
            }
        }

        // 2. Collision detection and damage application
        for (Projectile p : projectiles) {
            if (!p.isAlive()) {
                continue;
            }

            if (p.getFaction() == ProjectileFaction.PLAYER) {
                if (enemies != null) {
                    for (Enemy enemy : enemies) {
                        if (enemy == null || !enemy.isAlive()) {
                            continue;
                        }

                        if (p.canHit(enemy.getId()) &&
                                CollisionUtils.checkCircleVsCircle(
                                        p.getPosition().x, p.getPosition().y, p.getRadius(),
                                        enemy.getPosition().x, enemy.getPosition().y, enemy.getRadius())) {

                            p.recordHit(enemy.getId());

                            damageEnemy(enemy, new DamageInfo(p.getDamage(), false), p.getPrimaryColor());

                            if (p.getSpell() != null) {
                                HitContext ctx = new HitContext(
                                        enemy, p.getPosition().cpy(), p.getDirection(),
                                        p.getDamage(), p.getPrimaryColor(), this
                                );
                                p.getSpell().applyOnHit(ctx);
                            }

                            if (p.getPierceRemaining() > 0) {
                                p.decrementPierce();
                            } else {
                                p.destroy();
                                break;
                            }
                        }
                    }
                }
            } else if (p.getFaction() == ProjectileFaction.ENEMY) {
                if (player != null && player.isAlive() &&
                        CollisionUtils.checkCircleVsCircle(
                                p.getPosition().x, p.getPosition().y, p.getRadius(),
                                player.getPosition().x, player.getPosition().y, player.getRadius())) {

                    player.takeDamage(new DamageInfo(p.getDamage(), false));
                    p.destroy();
                }
            }
        }

        // 3. Prune destroyed projectiles
        projectiles.removeIf(p -> !p.isAlive());

        this.currentActiveEnemies = null;
    }

    // =========================================================================
    // CombatServices Implementation
    // =========================================================================

    @Override
    public void damageEnemy(Enemy enemy, DamageInfo damage, Color color) {
        if (enemy != null && enemy.isAlive()) {
            enemy.takeDamage(damage);
        }
    }

    @Override
    public Enemy findNearestEnemy(Vector2D from, float range, List<Integer> exclude) {
        if (currentActiveEnemies == null || from == null) {
            return null;
        }

        Enemy best = null;
        float bestDistSq = range * range;

        for (Enemy enemy : currentActiveEnemies) {
            if (enemy == null || !enemy.isAlive()) {
                continue;
            }
            if (exclude != null && exclude.contains(enemy.getId())) {
                continue;
            }
            float dSq = from.dist2(enemy.getPosition());
            if (dSq <= bestDistSq) {
                bestDistSq = dSq;
                best = enemy;
            }
        }
        return best;
    }

    @Override
    public void forEachEnemyInRadius(Vector2D center, float radius, Consumer<Enemy> action) {
        if (currentActiveEnemies == null || center == null || action == null) {
            return;
        }

        float rSq = radius * radius;
        for (Enemy enemy : currentActiveEnemies) {
            if (enemy != null && enemy.isAlive() && center.dist2(enemy.getPosition()) <= rSq) {
                action.accept(enemy);
            }
        }
    }

    public void rebase(float deltaX, float deltaY) {
        for (int i = 0; i < projectiles.size(); i++) {
            Projectile p = projectiles.get(i);
            if (p != null && p.isAlive()) {
                p.rebase(deltaX, deltaY);
            }
        }
        if (arenaBounds != null) {
            arenaBounds.x -= deltaX;
            arenaBounds.y -= deltaY;
        }
    }

    @Override
    public void onOriginShift(long deltaX, long deltaY) {
        rebase((float) deltaX, (float) deltaY);
    }
}
