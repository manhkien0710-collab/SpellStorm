package com.spellstorm.p2;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.Entity;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.EnemySeparationSystem;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Phase 2 Pod 3: EnemySeparationSystem")
public class EnemySeparationTest {

    @BeforeAll
    static void init() {
        HeadlessTestRunner.init();
    }

    @BeforeEach
    void resetIds() {
        Entity.resetIdCounter(1);
    }

    @Test
    @DisplayName("Overlapping living enemies separate to non-overlapping distance")
    void testOverlappingEnemiesSeparate() {
        EnemySeparationSystem system = new EnemySeparationSystem();
        Rectangle bounds = new Rectangle(0, 0, 1000, 1000);

        MeleeEnemy e1 = new MeleeEnemy(new Vector2D(500.0f, 500.0f));
        MeleeEnemy e2 = new MeleeEnemy(new Vector2D(508.0f, 500.0f));
        List<Enemy> enemies = List.of(e1, e2);

        for (int i = 0; i < 10; i++) {
            system.separate(enemies, null, bounds, 0.016f);
        }

        float minDist = e1.getRadius() + e2.getRadius();
        float actualDist = e1.getPosition().dist(e2.getPosition());
        assertTrue(actualDist >= minDist - 0.5f,
                "Expected separated dist >= " + minDist + ", was " + actualDist);
    }

    @Test
    @DisplayName("Exact coincident enemies (dist == 0) break ties deterministically and fan out")
    void testCoincidentTieBreakingDeterministic() {
        EnemySeparationSystem system = new EnemySeparationSystem();
        Rectangle bounds = new Rectangle(0, 0, 4096, 4096);

        Entity.resetIdCounter(100);
        List<Enemy> run1 = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            run1.add(new MeleeEnemy(new Vector2D(2048.0f, 2048.0f)));
        }
        system.separate(run1, null, bounds, 0.016f);

        Entity.resetIdCounter(100);
        List<Enemy> run2 = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            run2.add(new MeleeEnemy(new Vector2D(2048.0f, 2048.0f)));
        }
        system.separate(run2, null, bounds, 0.016f);

        for (int i = 0; i < 6; i++) {
            assertEquals(run1.get(i).getX(), run2.get(i).getX(), 1e-5f);
            assertEquals(run1.get(i).getY(), run2.get(i).getY(), 1e-5f);
        }

        for (int i = 0; i < 15; i++) {
            system.separate(run1, null, bounds, 0.016f);
        }

        for (int i = 0; i < run1.size(); i++) {
            for (int j = i + 1; j < run1.size(); j++) {
                float d = run1.get(i).getPosition().dist(run1.get(j).getPosition());
                assertTrue(d > 10.0f, "Coincident enemies must remain separated, pair (" + i + "," + j + ") dist=" + d);
            }
        }
    }

    @Test
    @DisplayName("Dead enemies are ignored and zero dt does not move enemies")
    void testDeadEnemiesAndZeroDtIgnored() {
        EnemySeparationSystem system = new EnemySeparationSystem();
        MeleeEnemy alive = new MeleeEnemy(new Vector2D(500.0f, 500.0f));
        MeleeEnemy dead = new MeleeEnemy(new Vector2D(500.0f, 500.0f));
        dead.takeDamage(9999.0f, true);
        assertFalse(dead.isAlive());

        List<Enemy> enemies = List.of(alive, dead);
        system.separate(enemies, null, 0.1f);
        assertEquals(500.0f, alive.getX(), 1e-4f);
        assertEquals(500.0f, alive.getY(), 1e-4f);
        assertEquals(500.0f, dead.getX(), 1e-4f);
        assertEquals(500.0f, dead.getY(), 1e-4f);

        MeleeEnemy alive2 = new MeleeEnemy(new Vector2D(500.0f, 500.0f));
        system.separate(List.of(alive, alive2), null, 0.0f);
        assertEquals(500.0f, alive.getX(), 1e-4f);
        assertEquals(500.0f, alive2.getX(), 1e-4f);
    }

    @Test
    @DisplayName("Separation respects worldBounds and TileMap.isTraversable")
    void testRespectsWorldBoundsAndTileMap() {
        EnemySeparationSystem system = new EnemySeparationSystem();
        TileMap tileMap = new TileMap(42L);
        Rectangle tightBounds = new Rectangle(1800.0f, 1800.0f, 400.0f, 400.0f);

        // Place near left edge of tightBounds
        TankEnemy tank = new TankEnemy(new Vector2D(1826.0f, 2048.0f));
        BossEnemy boss = new BossEnemy(new Vector2D(1852.0f, 2048.0f));
        RangedEnemy ranged = new RangedEnemy(new Vector2D(1852.0f, 2048.0f));
        List<Enemy> enemies = List.of(tank, boss, ranged);

        for (int i = 0; i < 20; i++) {
            system.separate(enemies, tileMap, tightBounds, 0.05f);
        }

        for (Enemy e : enemies) {
            float r = e.getRadius();
            assertTrue(e.getX() >= tightBounds.x + r - 1e-3f, "Out of minX bound: " + e.getX());
            assertTrue(e.getX() <= tightBounds.x + tightBounds.width - r + 1e-3f, "Out of maxX bound: " + e.getX());
            assertTrue(e.getY() >= tightBounds.y + r - 1e-3f, "Out of minY bound: " + e.getY());
            assertTrue(e.getY() <= tightBounds.y + tightBounds.height - r + 1e-3f, "Out of maxY bound: " + e.getY());
            assertTrue(tileMap.isTraversable(e.getX(), e.getY(), r),
                    "Enemy separated into non-traversable position (" + e.getX() + "," + e.getY() + ")");
        }
    }
}
