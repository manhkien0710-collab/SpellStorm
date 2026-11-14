package com.spellstorm.combat;

import com.badlogic.gdx.graphics.Color;
import com.spellstorm.entities.Enemy;
import com.spellstorm.math.Vector2D;

import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;

/**
 * Interface through which spell effects interact with the battlefield.
 * Decouples on-hit behaviour from specific systems for headless testing.
 */
public interface CombatServices {

    void damageEnemy(Enemy enemy, DamageInfo damage, Color color);

    default void damageEnemy(Enemy enemy, DamageInfo damage) {
        damageEnemy(enemy, damage, Color.WHITE);
    }

    Enemy findNearestEnemy(Vector2D from, float range, List<Integer> excludeIds);

    void forEachEnemyInRadius(Vector2D center, float radius, Consumer<Enemy> action);
}
