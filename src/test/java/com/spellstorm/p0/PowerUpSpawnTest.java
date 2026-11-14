package com.spellstorm.p0;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.entities.Player;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.PowerUpManager;
import com.spellstorm.systems.PowerUpType;
import com.spellstorm.world.TileMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("P0 Regression Test: Defect D6 - PowerUp Spawning Traversability & Backward Compatibility")
public class PowerUpSpawnTest {

    @BeforeAll
    public static void setup() {
        HeadlessTestRunner.init();
    }

    @Test
    @DisplayName("D6 Fix: 10,000 power-up spawns near river produce exactly 0 untraversable positions")
    public void testTenThousandRiverSpawnsProduceZeroUntraversablePositions() {
        TileMap tileMap = new TileMap(42);
        Rectangle arenaBounds = new Rectangle(0, 0, TileMap.WORLD_WIDTH, TileMap.WORLD_HEIGHT);
        Random rng = new Random(20261112);
        PowerUpManager manager = new PowerUpManager(arenaBounds, rng, tileMap);

        assertEquals(tileMap, manager.getTileMap(), "getTileMap() must return the configured TileMap");

        // The sinusoidal river is located around tile x in [30, 55], which corresponds to world coordinates [960, 1760]
        final int sampleCount = 10_000;
        int invalidCount = 0;

        for (int i = 0; i < sampleCount; i++) {
            // Sample player positions clustered closely along the river corridor
            float playerX = 1000.0f + rng.nextFloat() * 750.0f; // [1000, 1750]
            float playerY = 150.0f + rng.nextFloat() * (TileMap.WORLD_HEIGHT - 300.0f); // [150, 3796]
            Vector2D playerPos = new Vector2D(playerX, playerY);

            Vector2D spawnPos = manager.findValidSpawnPosition(playerPos);

            assertNotNull(spawnPos, "findValidSpawnPosition must never return null");

            // Verify arena boundary constraints (50px margin)
            assertTrue(spawnPos.x >= 50.0f && spawnPos.x <= TileMap.WORLD_WIDTH - 50.0f,
                    "Spawn X must be within arena bounds: " + spawnPos.x);
            assertTrue(spawnPos.y >= 50.0f && spawnPos.y <= TileMap.WORLD_HEIGHT - 50.0f,
                    "Spawn Y must be within arena bounds: " + spawnPos.y);

            // Verify traversability: power-up (r=18.0f) must not collide with water or obstacles
            boolean traversable = tileMap.isTraversable(spawnPos.x, spawnPos.y, 18.0f);
            if (!traversable) {
                invalidCount++;
            }
        }

        assertEquals(0, invalidCount, "Expected exactly 0 untraversable spawns out of 10,000 near the river");
    }

    @Test
    @DisplayName("Null-TileMap Fallback Safety: Functions gracefully without NPE")
    public void testNullTileMapFallbackSafety() {
        Rectangle arenaBounds = new Rectangle(0, 0, TileMap.WORLD_WIDTH, TileMap.WORLD_HEIGHT);
        Random rng = new Random(42);

        // Test constructor with null TileMap
        PowerUpManager manager = new PowerUpManager(arenaBounds, rng, null);
        assertNull(manager.getTileMap(), "tileMap should be null");

        // 1. Spawning with player at center
        Vector2D p1 = manager.findValidSpawnPosition(new Vector2D(2048.0f, 2048.0f));
        assertNotNull(p1);
        assertTrue(p1.x >= 50.0f && p1.x <= TileMap.WORLD_WIDTH - 50.0f);
        assertTrue(p1.y >= 50.0f && p1.y <= TileMap.WORLD_HEIGHT - 50.0f);

        // 2. Spawning with null playerPos
        Vector2D p2 = manager.findValidSpawnPosition(null);
        assertNotNull(p2);
        assertTrue(p2.x >= 50.0f && p2.x <= TileMap.WORLD_WIDTH - 50.0f);
        assertTrue(p2.y >= 50.0f && p2.y <= TileMap.WORLD_HEIGHT - 50.0f);

        // 3. Spawning with player near arena edges
        Vector2D p3 = manager.findValidSpawnPosition(new Vector2D(10.0f, 10.0f));
        assertNotNull(p3);
        assertTrue(p3.x >= 50.0f && p3.x <= TileMap.WORLD_WIDTH - 50.0f);
        assertTrue(p3.y >= 50.0f && p3.y <= TileMap.WORLD_HEIGHT - 50.0f);

        // 4. spawnRandomPickup & update lifecycle with null tileMap
        Player dummyPlayer = new Player(new Vector2D(2048.0f, 2048.0f));
        assertDoesNotThrow(() -> {
            manager.spawnRandomPickup(dummyPlayer.getPosition());
            manager.update(0.016f, dummyPlayer, false);
        });
        assertEquals(1, manager.getPickups().size());
    }

    @Test
    @DisplayName("Constructor Backward Compatibility: All 4 constructor overloads initialize properly")
    public void testConstructorOverloadsBackwardCompatibility() {
        Rectangle customBounds = new Rectangle(100, 100, 2000, 2000);
        Random customRng = new Random(999);
        TileMap tileMap = new TileMap(42);

        // 1. Default constructor
        PowerUpManager m1 = new PowerUpManager();
        assertNull(m1.getTileMap());
        assertNotNull(m1.findValidSpawnPosition(new Vector2D(1000, 1000)));

        // 2. arenaBounds constructor
        PowerUpManager m2 = new PowerUpManager(customBounds);
        assertNull(m2.getTileMap());
        Vector2D pos2 = m2.findValidSpawnPosition(new Vector2D(500, 500));
        assertNotNull(pos2);
        assertTrue(pos2.x >= 150.0f && pos2.x <= 2050.0f);

        // 3. arenaBounds + Random constructor
        PowerUpManager m3 = new PowerUpManager(customBounds, customRng);
        assertNull(m3.getTileMap());
        Vector2D pos3 = m3.findValidSpawnPosition(new Vector2D(500, 500));
        assertNotNull(pos3);

        // 4. arenaBounds + Random + TileMap constructor
        PowerUpManager m4 = new PowerUpManager(customBounds, customRng, tileMap);
        assertEquals(tileMap, m4.getTileMap());
        Vector2D pos4 = m4.findValidSpawnPosition(new Vector2D(1000, 1000));
        assertNotNull(pos4);
    }

    @Test
    @DisplayName("Setter and Getter: setTileMap dynamically enables traversability check")
    public void testSetTileMapDynamic() {
        TileMap tileMap = new TileMap(42);
        Rectangle arenaBounds = new Rectangle(0, 0, TileMap.WORLD_WIDTH, TileMap.WORLD_HEIGHT);
        Random rng = new Random(777);

        PowerUpManager manager = new PowerUpManager(arenaBounds, rng);
        assertNull(manager.getTileMap());

        // Wire tileMap via setter (contract for GameSession integration)
        manager.setTileMap(tileMap);
        assertEquals(tileMap, manager.getTileMap());

        // Run 500 samples directly on water coordinate (1344, 2048)
        Vector2D playerAtWater = new Vector2D(1344.0f, 2048.0f);
        for (int i = 0; i < 500; i++) {
            Vector2D spawn = manager.findValidSpawnPosition(playerAtWater);
            assertTrue(tileMap.isTraversable(spawn.x, spawn.y, 18.0f),
                    "Spawn position (" + spawn.x + ", " + spawn.y + ") must be traversable after setTileMap()");
        }

        // Setting back to null disables traversability constraint safely
        manager.setTileMap(null);
        assertNull(manager.getTileMap());
        assertNotNull(manager.findValidSpawnPosition(playerAtWater));
    }

    @Test
    @DisplayName("Corner / Constrained Boundary Case: Spiral search finds safe spot even at world edge")
    public void testSpiralSearchNearWorldBoundaries() {
        TileMap tileMap = new TileMap(42);
        Rectangle arenaBounds = new Rectangle(0, 0, TileMap.WORLD_WIDTH, TileMap.WORLD_HEIGHT);
        PowerUpManager manager = new PowerUpManager(arenaBounds, new Random(12345), tileMap);

        // Test all four corners of the world
        Vector2D[] edgePositions = new Vector2D[] {
                new Vector2D(50.0f, 50.0f),
                new Vector2D(TileMap.WORLD_WIDTH - 50.0f, 50.0f),
                new Vector2D(50.0f, TileMap.WORLD_HEIGHT - 50.0f),
                new Vector2D(TileMap.WORLD_WIDTH - 50.0f, TileMap.WORLD_HEIGHT - 50.0f),
                new Vector2D(1344.0f, 100.0f), // River edge at south
                new Vector2D(1344.0f, 3996.0f) // River edge at north
        };

        for (Vector2D edgePos : edgePositions) {
            for (int i = 0; i < 50; i++) {
                Vector2D spawn = manager.findValidSpawnPosition(edgePos);
                assertNotNull(spawn);
                assertTrue(spawn.x >= 50.0f && spawn.x <= TileMap.WORLD_WIDTH - 50.0f);
                assertTrue(spawn.y >= 50.0f && spawn.y <= TileMap.WORLD_HEIGHT - 50.0f);
                assertTrue(tileMap.isTraversable(spawn.x, spawn.y, 18.0f),
                        "Spawn from edge (" + edgePos.x + ", " + edgePos.y + ") must be traversable");
            }
        }
    }
}
