package com.spellstorm.navigation;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.HeadlessTestRunner;
import com.spellstorm.core.EngineConfig;
import com.spellstorm.core.GameSession;
import com.spellstorm.core.SpellStormGame;
import com.spellstorm.entities.BossEnemy;
import com.spellstorm.entities.Enemy;
import com.spellstorm.entities.EnemyType;
import com.spellstorm.entities.MeleeEnemy;
import com.spellstorm.entities.Player;
import com.spellstorm.entities.Projectile;
import com.spellstorm.entities.ProjectileFaction;
import com.spellstorm.entities.RangedEnemy;
import com.spellstorm.entities.TankEnemy;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.systems.BalanceConfig;
import com.spellstorm.systems.EnemySeparationSystem;
import com.spellstorm.systems.EnemySpawner;
import com.spellstorm.world.Chunk;
import com.spellstorm.world.ChunkImpl;
import com.spellstorm.world.ChunkManagerImpl;
import com.spellstorm.world.DecoType;
import com.spellstorm.world.FloatingOriginManager;
import com.spellstorm.world.InfiniteWorld;
import com.spellstorm.world.Obstacle;
import com.spellstorm.world.TerrainGenerator;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileMapAdapter;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGrid;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.camera.GameCamera;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Gate 2 Remediation: Enemy River Navigation, Bridges, Ranged LOS & Origin Rebasing")
public class EnemyRiverNavigationRemediationTest {

    private static final float DT = 1.0f / 60.0f;

    @BeforeAll
    static void initHeadless() {
        HeadlessTestRunner.init();
    }

    // =========================================================================
    // Test Helpers & Controlled Unbounded WorldGrid Fixtures
    // =========================================================================

    @FunctionalInterface
    interface TileRule {
        TileType tileAt(int tx, int ty);
    }

    /**
     * Deterministic, unbounded WorldGrid fixture supporting arbitrary negative/positive
     * coordinates, solid obstacles, and optional per-tile lookup counting.
     */
    static final class ControlledUnboundedGrid implements WorldGrid {
        private final TileRule tileRule;
        private final List<Obstacle> obstacles = new ArrayList<>();
        private final Map<Long, TileType> overrides = new HashMap<>();
        private int getTileCallCount = 0;

        ControlledUnboundedGrid(TileRule tileRule) {
            this.tileRule = tileRule;
        }

        void setTileOverride(int tx, int ty, TileType type) {
            overrides.put(packTile(tx, ty), type);
        }

        void addObstacle(Obstacle obs) {
            if (obs != null) {
                obstacles.add(obs);
            }
        }

        void resetGetTileCallCount() {
            getTileCallCount = 0;
        }

        int getTileCallCount() {
            return getTileCallCount;
        }

        private static long packTile(int tx, int ty) {
            return (((long) tx) << 32) | (ty & 0xFFFFFFFFL);
        }

        @Override
        public TileType getTile(int tx, int ty) {
            getTileCallCount++;
            TileType ov = overrides.get(packTile(tx, ty));
            if (ov != null) {
                return ov;
            }
            return tileRule.tileAt(tx, ty);
        }

        @Override
        public boolean isSolidTile(int tx, int ty) {
            return getTile(tx, ty).isSolid();
        }

        @Override
        public boolean isWaterTile(int tx, int ty) {
            return getTile(tx, ty) == TileType.WATER;
        }

        @Override
        public boolean isTraversable(float x, float y, float radius) {
            if (radius < 0.0f || Float.isNaN(x) || Float.isNaN(y) || Float.isInfinite(x) || Float.isInfinite(y)) {
                return false;
            }
            int minTx = Math.floorDiv((long) Math.floor(x - radius), 32L) >= Integer.MIN_VALUE
                    ? (int) Math.floorDiv((long) Math.floor(x - radius), 32L) : Integer.MIN_VALUE;
            int maxTx = (int) Math.floorDiv((long) Math.floor(x + radius), 32L);
            int minTy = (int) Math.floorDiv((long) Math.floor(y - radius), 32L);
            int maxTy = (int) Math.floorDiv((long) Math.floor(y + radius), 32L);

            for (int ty = minTy; ty <= maxTy; ty++) {
                for (int tx = minTx; tx <= maxTx; tx++) {
                    TileType t = tileRule.tileAt(tx, ty);
                    TileType ov = overrides.get(packTile(tx, ty));
                    if (ov != null) {
                        t = ov;
                    }
                    if (t.isSolid()) {
                        float left = tx * 32.0f;
                        float top = ty * 32.0f;
                        if (CollisionUtils.checkCircleVsAABB(x, y, radius, left, top, 32.0f, 32.0f)) {
                            return false;
                        }
                    }
                }
            }
            for (Obstacle obs : obstacles) {
                if (obs.isSolid() && obs.getCollisionRadius() > 0.0f) {
                    Vector2D c = obs.getCollisionCenter();
                    float rSum = radius + obs.getCollisionRadius();
                    float dx = x - c.x;
                    float dy = y - c.y;
                    if ((dx * dx + dy * dy) < (rSum * rSum)) {
                        return false;
                    }
                }
            }
            return true;
        }

        @Override
        public Vector2D resolveCollision(float oldX, float oldY, float newX, float newY, float radius) {
            return CollisionUtils.resolveSlidingUnbounded(oldX, oldY, newX, newY, radius, this::isTraversable);
        }

        @Override
        public Vector2D resolveCollision(Vector2D oldPos, Vector2D newPos, float radius) {
            float ox = oldPos != null ? oldPos.x : 0.0f;
            float oy = oldPos != null ? oldPos.y : 0.0f;
            float nx = newPos != null ? newPos.x : 0.0f;
            float ny = newPos != null ? newPos.y : 0.0f;
            return resolveCollision(ox, oy, nx, ny, radius);
        }

        @Override
        public int worldToTileX(float worldX) {
            return (int) Math.floorDiv((long) Math.floor(worldX), 32L);
        }

        @Override
        public int worldToTileY(float worldY) {
            return (int) Math.floorDiv((long) Math.floor(worldY), 32L);
        }

        @Override
        public float tileToWorldX(int tx) {
            return (tx + 0.5f) * 32.0f;
        }

        @Override
        public float tileToWorldY(int ty) {
            return (ty + 0.5f) * 32.0f;
        }

        @Override
        public Vector2D tileToWorld(int tx, int ty) {
            return new Vector2D(tileToWorldX(tx), tileToWorldY(ty));
        }

        @Override
        public Rectangle getWorldBounds() {
            return null;
        }

        @Override
        public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera, float elapsed) {
        }

        @Override
        public List<Obstacle> getVisibleObstacles(GameCamera camera) {
            return obstacles;
        }

        @Override
        public List<Obstacle> getObstaclesNear(float worldX, float worldY, float searchRadius) {
            float r = Math.max(0.0f, searchRadius);
            List<Obstacle> near = new ArrayList<>();
            for (Obstacle obs : obstacles) {
                Vector2D c = obs.getCollisionCenter();
                float limit = r + Math.max(0.0f, obs.getCollisionRadius());
                float dx = worldX - c.x;
                float dy = worldY - c.y;
                if ((dx * dx + dy * dy) <= (limit * limit)) {
                    near.add(obs);
                }
            }
            return near;
        }
    }

    /**
     * Builds a real InfiniteWorld backed by ChunkManagerImpl + FloatingOriginManager
     * using a deterministic TileRule and optional global Obstacle list.
     */
    private static InfiniteWorld createDeterministicInfiniteWorld(TileRule rule, List<Obstacle> globalObstacles) {
        BiFunction<Integer, Integer, Chunk> generator = (cx, cy) -> {
            ChunkImpl chunk = new ChunkImpl(cx, cy);
            for (int ly = 0; ly < Chunk.CHUNK_TILES; ly++) {
                for (int lx = 0; lx < Chunk.CHUNK_TILES; lx++) {
                    int tx = cx * Chunk.CHUNK_TILES + lx;
                    int ty = cy * Chunk.CHUNK_TILES + ly;
                    chunk.setLocalTile(lx, ly, rule.tileAt(tx, ty));
                }
            }
            if (globalObstacles != null) {
                float chunkMinX = cx * Chunk.CHUNK_SIZE_PX;
                float chunkMaxX = chunkMinX + Chunk.CHUNK_SIZE_PX;
                float chunkMinY = cy * Chunk.CHUNK_SIZE_PX;
                float chunkMaxY = chunkMinY + Chunk.CHUNK_SIZE_PX;
                for (Obstacle obs : globalObstacles) {
                    float gx = obs.getPosition().x;
                    float gy = obs.getPosition().y;
                    if (gx >= chunkMinX && gx < chunkMaxX && gy >= chunkMinY && gy < chunkMaxY) {
                        chunk.addObstacle(new Obstacle(obs.getType(), gx - chunkMinX, gy - chunkMinY, obs.isSolid()));
                    }
                }
            }
            return chunk;
        };
        ChunkManagerImpl cm = new ChunkManagerImpl(generator, 64);
        FloatingOriginManager originMgr = new FloatingOriginManager();
        return new InfiniteWorld(cm, originMgr);
    }

    private static void assertValidGroundEntityState(Enemy enemy, WorldGrid grid, String context) {
        Vector2D pos = enemy.getPosition();
        assertFalse(Float.isNaN(pos.x) || Float.isInfinite(pos.x),
                context + ": X coordinate must be finite, was " + pos.x);
        assertFalse(Float.isNaN(pos.y) || Float.isInfinite(pos.y),
                context + ": Y coordinate must be finite, was " + pos.y);
        int tx = grid.worldToTileX(pos.x);
        int ty = grid.worldToTileY(pos.y);
        assertFalse(grid.isWaterTile(tx, ty),
                context + ": enemy entered WATER tile (" + tx + "," + ty + ") at pos=" + pos);
        assertTrue(grid.isTraversable(pos.x, pos.y, enemy.getRadius()),
                context + ": enemy position " + pos + " (r=" + enemy.getRadius() + ") must remain traversable");
    }

    private static boolean pathCrossesTileType(List<Vector2D> path, Vector2D start, WorldGrid grid, TileType expected) {
        if (path == null || path.isEmpty()) {
            return false;
        }
        Vector2D prev = start;
        for (Vector2D wp : path) {
            if (grid.getTile(grid.worldToTileX(wp.x), grid.worldToTileY(wp.y)) == expected) {
                return true;
            }
            if (prev != null) {
                float dist = prev.dist(wp);
                int steps = Math.max(1, (int) Math.ceil(dist / 16.0f));
                for (int s = 0; s <= steps; s++) {
                    float t = (float) s / (float) steps;
                    float sx = prev.x + (wp.x - prev.x) * t;
                    float sy = prev.y + (wp.y - prev.y) * t;
                    if (grid.getTile(grid.worldToTileX(sx), grid.worldToTileY(sy)) == expected) {
                        return true;
                    }
                }
            }
            prev = wp;
        }
        return false;
    }

    private static List<Enemy> createAllGroundArchetypes(Vector2D pos) {
        List<Enemy> list = new ArrayList<>();
        list.add(new MeleeEnemy(pos.cpy()));
        list.add(new TankEnemy(pos.cpy()));
        list.add(new BossEnemy(pos.cpy()));
        list.add(new RangedEnemy(pos.cpy()));
        return list;
    }

    /**
     * Finds a real BRIDGE_H river crossing along an East-West highway (gy = highwayY)
     * in a procedural InfiniteWorld where both west and east banks are traversable for radius=50f.
     */
    private static int[] findRealBridgeHCrossing(InfiniteWorld world, int highwayY, int minTx, int maxTx) {
        for (int tx = minTx; tx <= maxTx; tx++) {
            if (world.getTile(tx, highwayY) == TileType.BRIDGE_H
                    && world.getTile(tx, highwayY - 4) == TileType.WATER
                    && world.getTile(tx, highwayY + 4) == TileType.WATER) {
                int spanStart = tx;
                int spanEnd = tx;
                while (spanEnd + 1 <= maxTx && world.getTile(spanEnd + 1, highwayY) == TileType.BRIDGE_H) {
                    spanEnd++;
                }
                int westTx = spanStart - 6;
                int eastTx = spanEnd + 6;
                // Ensure pure water separates banks off the highway (e.g., at highwayY - 8 and highwayY + 8)
                if (world.isTraversable(world.tileToWorldX(westTx), world.tileToWorldY(highwayY), BossEnemy.BOSS_RADIUS)
                        && world.isTraversable(world.tileToWorldX(eastTx), world.tileToWorldY(highwayY), BossEnemy.BOSS_RADIUS)
                        && world.isTraversable(world.tileToWorldX(westTx), world.tileToWorldY(highwayY - 6), BossEnemy.BOSS_RADIUS)
                        && world.isTraversable(world.tileToWorldX(eastTx), world.tileToWorldY(highwayY - 6), BossEnemy.BOSS_RADIUS)) {
                    return new int[]{spanStart, spanEnd, westTx, eastTx};
                }
                tx = spanEnd;
            }
        }
        return null;
    }

    /**
     * Finds a real BRIDGE_V river crossing along a North-South trail (gx = trailX)
     * in a procedural InfiniteWorld where both south and north banks are traversable for TankEnemy.
     */
    private static int[] findRealBridgeVCrossing(InfiniteWorld world, int trailX, int minTy, int maxTy) {
        for (int ty = minTy; ty <= maxTy; ty++) {
            if (world.getTile(trailX, ty) == TileType.BRIDGE_V
                    && world.getTile(trailX - 3, ty) == TileType.WATER
                    && world.getTile(trailX + 3, ty) == TileType.WATER) {
                int spanStart = ty;
                int spanEnd = ty;
                while (spanEnd + 1 <= maxTy && world.getTile(trailX, spanEnd + 1) == TileType.BRIDGE_V) {
                    spanEnd++;
                }
                int southTy = spanStart - 6;
                int northTy = spanEnd + 6;
                if (world.isTraversable(world.tileToWorldX(trailX), world.tileToWorldY(southTy), TankEnemy.DEFAULT_RADIUS)
                        && world.isTraversable(world.tileToWorldX(trailX), world.tileToWorldY(northTy), TankEnemy.DEFAULT_RADIUS)) {
                    return new int[]{spanStart, spanEnd, southTy, northTy};
                }
                ty = spanEnd;
            }
        }
        return null;
    }

    /**
     * Finds a real unbridged river tile on procedural InfiniteWorld away from roads,
     * returning {waterTx, waterTy, westDryTx, westDryTy}.
     */
    private static int[] findRealRiverShoreline(InfiniteWorld world, int startTx, int endTx, int ty) {
        for (int tx = startTx; tx <= endTx; tx++) {
            if (world.getTile(tx, ty) == TileType.WATER
                    && world.getTile(tx - 3, ty) != TileType.WATER
                    && world.isTraversable(world.tileToWorldX(tx - 4), world.tileToWorldY(ty), BossEnemy.BOSS_RADIUS)) {
                return new int[]{tx, ty, tx - 4, ty};
            }
        }
        return null;
    }

    // =========================================================================
    // 1. Water Impassability Across All Ground Enemy Archetypes
    // =========================================================================

    @Nested
    @DisplayName("1. Water Impassability Across Ground Enemy Archetypes")
    class WaterImpassabilityTests {

        @Test
        @DisplayName("1.1 Direct pursuit & fallback steering across unbridged river never enters WATER (unbounded WorldGrid & TileMap)")
        void testDirectPursuitAndFallbackNeverEntersWater() {
            // A) Unbounded controlled WorldGrid: unbridged N-S river at tx = 10..13
            ControlledUnboundedGrid unbounded = new ControlledUnboundedGrid((tx, ty) ->
                    (tx >= 10 && tx <= 13) ? TileType.WATER : TileType.GRASS);
            AStarPathfinder unboundedPf = AStarPathfinder.forWorldGrid(unbounded);
            Vector2D startWest = unbounded.tileToWorld(5, 0);
            Vector2D targetEast = unbounded.tileToWorld(18, 0);

            for (Enemy enemy : createAllGroundArchetypes(startWest)) {
                // Direct pursuit with grid + pathfinder
                for (int tick = 0; tick < 180; tick++) {
                    enemy.updateAIWithGrid(DT, targetEast, unbounded, unboundedPf);
                    enemy.update(DT);
                    assertValidGroundEntityState(enemy, unbounded,
                            "Unbounded pursuit " + enemy.getType() + " tick=" + tick);
                }
            }

            // Test fallback steering (when enemy already has activeWorldGrid set and steers with null pathfinder)
            for (Enemy enemy : createAllGroundArchetypes(unbounded.tileToWorld(7, 0))) {
                enemy.setActiveWorldGrid(unbounded);
                for (int tick = 0; tick < 60; tick++) {
                    enemy.steerWithWorldGrid(DT, targetEast, unbounded, null, 1.0f);
                    enemy.update(DT);
                    assertValidGroundEntityState(enemy, unbounded,
                            "Unbounded direct-fallback steering " + enemy.getType() + " tick=" + tick);
                }
            }

            // B) Bounded TileMap: river runs N-S around x=42; test unbridged segment around y=46
            TileMap tileMap = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(tileMap);
            AStarPathfinder boundedPf = new AStarPathfinder(tileMap);

            int riverWaterTx = -1;
            for (int x = 30; x <= 55; x++) {
                if (tileMap.isWaterTile(x, 46)) {
                    riverWaterTx = x;
                    break;
                }
            }
            assertTrue(riverWaterTx > 0, "TileMap must contain river at row 46");
            Vector2D mapWest = tileMap.tileToWorld(riverWaterTx - 5, 46);
            Vector2D mapEast = tileMap.tileToWorld(riverWaterTx + 9, 46);
            assertTrue(tileMap.isTraversable(mapWest, BossEnemy.BOSS_RADIUS));
            assertTrue(tileMap.isTraversable(mapEast, BossEnemy.BOSS_RADIUS));

            for (Enemy enemy : createAllGroundArchetypes(mapWest)) {
                for (int tick = 0; tick < 180; tick++) {
                    enemy.updateAI(DT, mapEast, tileMap, boundedPf);
                    enemy.update(DT);
                    assertValidGroundEntityState(enemy, adapter,
                            "Bounded TileMap pursuit " + enemy.getType() + " tick=" + tick);
                }
            }
        }

        @Test
        @DisplayName("1.2 Strong applyKnockback toward WATER is clamped cleanly on dry land for all archetypes")
        void testStrongKnockbackTowardWaterBlocked() {
            // Unbounded InfiniteWorld with N-S river at tx in [5..9]
            InfiniteWorld infiniteWorld = createDeterministicInfiniteWorld(
                    (tx, ty) -> (tx >= 5 && tx <= 9) ? TileType.WATER : TileType.GRASS, null);
            AStarPathfinder infPf = AStarPathfinder.forWorldGrid(infiniteWorld);

            // Bounded TileMap
            TileMap tileMap = new TileMap(42L);
            TileMapAdapter mapAdapter = new TileMapAdapter(tileMap);
            AStarPathfinder mapPf = new AStarPathfinder(tileMap);

            int waterTx = -1;
            for (int x = 30; x <= 55; x++) {
                if (tileMap.isWaterTile(x, 46)) {
                    waterTx = x;
                    break;
                }
            }
            assertTrue(waterTx > 0);
            int westDryTx = waterTx - 3;
            while (!tileMap.isTraversable(tileMap.tileToWorldX(westDryTx), tileMap.tileToWorldY(46), BossEnemy.BOSS_RADIUS)) {
                westDryTx--;
            }

            // Test each archetype under repeated strong knockback impulses directed straight into WATER
            for (EnemyType type : new EnemyType[]{EnemyType.MELEE, EnemyType.TANK, EnemyType.BOSS, EnemyType.RANGED}) {
                // InfiniteWorld near shoreline x = 5*32 = 160 -> stand safely near shoreline
                float startX = 5 * 32.0f - type.getDefaultRadius() - 4.0f;
                Vector2D startPos = new Vector2D(startX, 16.0f);
                assertTrue(infiniteWorld.isTraversable(startPos, type.getDefaultRadius()));

                Enemy infEnemy = switch (type) {
                    case MELEE -> new MeleeEnemy(startPos);
                    case TANK -> new TankEnemy(startPos);
                    case BOSS -> new BossEnemy(startPos);
                    case RANGED -> new RangedEnemy(startPos);
                };
                infEnemy.setKnockbackResistance(0.0f); // Stress full impulse even on Tank
                Vector2D targetAcross = new Vector2D(12 * 32.0f, 16.0f);

                for (int tick = 0; tick < 90; tick++) {
                    if (tick % 15 == 0) {
                        infEnemy.applyKnockback(1200.0f, 0.0f); // Strong eastward push into water
                    }
                    infEnemy.updateAIWithGrid(DT, targetAcross, infiniteWorld, infPf);
                    infEnemy.update(DT);
                    assertValidGroundEntityState(infEnemy, infiniteWorld,
                            "InfiniteWorld knockback " + type + " tick=" + tick);
                }

                // Bounded TileMap near shoreline
                Vector2D mapStart = tileMap.tileToWorld(westDryTx, 46);
                Enemy mapEnemy = switch (type) {
                    case MELEE -> new MeleeEnemy(mapStart);
                    case TANK -> new TankEnemy(mapStart);
                    case BOSS -> new BossEnemy(mapStart);
                    case RANGED -> new RangedEnemy(mapStart);
                };
                mapEnemy.setKnockbackResistance(0.0f);
                Vector2D mapTargetAcross = tileMap.tileToWorld(waterTx + 8, 46);

                for (int tick = 0; tick < 90; tick++) {
                    if (tick % 15 == 0) {
                        mapEnemy.applyKnockback(1200.0f, 0.0f);
                    }
                    mapEnemy.updateAI(DT, mapTargetAcross, tileMap, mapPf);
                    mapEnemy.update(DT);
                    assertValidGroundEntityState(mapEnemy, mapAdapter,
                            "TileMap knockback " + type + " tick=" + tick);
                }
            }
        }

        @Test
        @DisplayName("1.3 RangedEnemy kiting/retreat backed against WATER and lateral strafing near WATER stay dry")
        void testRangedEnemyRetreatAndLateralStrafeNearWater() {
            // Scenario A (Retreat backed against WATER):
            // West is WATER (tx <= 0), East is GRASS (tx >= 1).
            // RangedEnemy starts on tx=2 (x=80), Player is east at x=190 (dist=110 < 205 -> RangedEnemy retreats WEST into water!).
            ControlledUnboundedGrid retreatGrid = new ControlledUnboundedGrid((tx, ty) ->
                    (tx <= 0) ? TileType.WATER : TileType.GRASS);
            AStarPathfinder retreatPf = AStarPathfinder.forWorldGrid(retreatGrid);

            RangedEnemy retreating = new RangedEnemy(new Vector2D(80.0f, 16.0f));
            Vector2D eastPlayer = new Vector2D(190.0f, 16.0f);
            assertTrue(retreatGrid.isTraversable(retreating.getPosition(), retreating.getRadius()));

            for (int tick = 0; tick < 180; tick++) {
                retreating.updateAIWithGrid(DT, eastPlayer, retreatGrid, retreatPf);
                retreating.update(DT);
                assertValidGroundEntityState(retreating, retreatGrid, "RangedEnemy retreat tick=" + tick);
            }
            // Should have backed right up near x = 32 + 15 = 47 without ever crossing into WATER
            assertTrue(retreating.getX() >= 32.0f + retreating.getRadius() - 0.5f,
                    "RangedEnemy must stay on dry land east of tx=0 water edge, x=" + retreating.getX());

            // Scenario B (Lateral strafing near WATER in sweet spot dist=240px):
            // Narrow E-W peninsula of 2 grass tiles tall (ty=0..1) flanked by WATER at ty<0 and ty>1.
            // Player is at (480, 32), RangedEnemy at (240, 32) -> dir=(1,0), strafeDir=(0, +-1) directly toward WATER!
            ControlledUnboundedGrid strafeGrid = new ControlledUnboundedGrid((tx, ty) ->
                    (ty < 0 || ty > 1) ? TileType.WATER : TileType.GRASS);
            AStarPathfinder strafePf = AStarPathfinder.forWorldGrid(strafeGrid);

            RangedEnemy strafing = new RangedEnemy(new Vector2D(240.0f, 32.0f));
            Vector2D strafePlayer = new Vector2D(480.0f, 32.0f);
            assertTrue(strafeGrid.isTraversable(strafing.getPosition(), strafing.getRadius()));

            // Run >= 250 ticks (> 4.1s) so sin(animTimer * 1.5f) exercises BOTH +0.45 and -0.45 strafe directions
            for (int tick = 0; tick < 260; tick++) {
                strafing.updateAIWithGrid(DT, strafePlayer, strafeGrid, strafePf);
                strafing.update(DT);
                assertValidGroundEntityState(strafing, strafeGrid, "RangedEnemy strafe tick=" + tick);
            }

            // Scenario C (Bounded TileMap): RangedEnemy near western shoreline retreating/strafing
            TileMap tileMap = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(tileMap);
            AStarPathfinder mapPf = new AStarPathfinder(tileMap);
            int riverTx = -1;
            for (int x = 30; x <= 55; x++) {
                if (tileMap.isWaterTile(x, 46)) {
                    riverTx = x;
                    break;
                }
            }
            assertTrue(riverTx > 0);
            // Place RangedEnemy just east of water (Wait: find max water tx on row 46)
            int maxWaterTx = riverTx;
            while (tileMap.isWaterTile(maxWaterTx + 1, 46)) {
                maxWaterTx++;
            }
            Vector2D enemyPos = tileMap.tileToWorld(maxWaterTx + 2, 46);
            Vector2D playerEast = tileMap.tileToWorld(maxWaterTx + 6, 46); // dist = 128px -> retreats WEST toward water
            RangedEnemy mapRanged = new RangedEnemy(enemyPos);
            assertTrue(tileMap.isTraversable(enemyPos, mapRanged.getRadius()));

            for (int tick = 0; tick < 180; tick++) {
                mapRanged.updateAI(DT, playerEast, tileMap, mapPf);
                mapRanged.update(DT);
                assertValidGroundEntityState(mapRanged, adapter, "TileMap RangedEnemy retreat tick=" + tick);
            }
        }

        @Test
        @DisplayName("1.4 EnemySeparationSystem.separateWithGrid crowd pushing against shoreline keeps every enemy on dry land")
        void testSeparationCrowdPushingAgainstShoreline() {
            // Unbounded InfiniteWorld with shoreline at tx >= 10 WATER
            InfiniteWorld infiniteWorld = createDeterministicInfiniteWorld(
                    (tx, ty) -> (tx >= 10) ? TileType.WATER : TileType.GRASS, null);
            AStarPathfinder infPf = AStarPathfinder.forWorldGrid(infiniteWorld);
            EnemySeparationSystem sep = new EnemySeparationSystem();

            // Pack 12 overlapping/coincident enemies right next to shoreline (x = 9*32 - 4 = 284 -> water starts at 320)
            List<Enemy> crowd = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                crowd.add(new MeleeEnemy(new Vector2D(284.0f, 16.0f + (i % 2) * 4.0f)));
                crowd.add(new RangedEnemy(new Vector2D(284.0f, 20.0f)));
                crowd.add(new TankEnemy(new Vector2D(270.0f, 24.0f)));
            }
            // Add a BossEnemy slightly inland
            crowd.add(new BossEnemy(new Vector2D(240.0f, 24.0f)));

            for (Enemy e : crowd) {
                assertTrue(infiniteWorld.isTraversable(e.getPosition(), e.getRadius()),
                        "Initial crowd position must be traversable for " + e.getType());
            }

            Vector2D targetEast = new Vector2D(500.0f, 24.0f);
            for (int tick = 0; tick < 90; tick++) {
                for (Enemy e : crowd) {
                    e.updateAIWithGrid(DT, targetEast, infiniteWorld, infPf);
                    e.update(DT);
                }
                sep.separateWithGrid(crowd, infiniteWorld, DT);
                for (Enemy e : crowd) {
                    assertValidGroundEntityState(e, infiniteWorld,
                            "InfiniteWorld separation " + e.getType() + " tick=" + tick);
                }
            }

            // Bounded TileMap crowd separation against river shoreline
            TileMap tileMap = new TileMap(42L);
            TileMapAdapter adapter = new TileMapAdapter(tileMap);
            int westShoreWaterTx = -1;
            for (int x = 30; x <= 55; x++) {
                if (tileMap.isWaterTile(x, 46)) {
                    westShoreWaterTx = x;
                    break;
                }
            }
            assertTrue(westShoreWaterTx > 0);
            Vector2D shorePos = tileMap.tileToWorld(westShoreWaterTx - 2, 46);
            List<Enemy> mapCrowd = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                mapCrowd.add(new MeleeEnemy(shorePos.cpy()));
            }
            mapCrowd.add(new TankEnemy(tileMap.tileToWorld(westShoreWaterTx - 3, 46)));

            for (int tick = 0; tick < 60; tick++) {
                sep.separate(mapCrowd, tileMap, DT);
                for (Enemy e : mapCrowd) {
                    assertValidGroundEntityState(e, adapter,
                            "TileMap separation " + e.getType() + " tick=" + tick);
                }
            }
        }
    }

    // =========================================================================
    // 2. Bridge Traversal (BRIDGE_H & BRIDGE_V)
    // =========================================================================

    @Nested
    @DisplayName("2. Bridge Traversal (BRIDGE_H & BRIDGE_V)")
    class BridgeTraversalTests {

        @Test
        @DisplayName("2.1 5-tile BRIDGE_H (160px) traversal by MeleeEnemy, TankEnemy, and BossEnemy in unbounded WorldGrid")
        void testBridgeHTraversalAllGroundEnemiesUnbounded() {
            // Unbounded N-S river at tx=10..13, 5-tile BRIDGE_H (160px) at ty=0..4
            ControlledUnboundedGrid grid = new ControlledUnboundedGrid((tx, ty) -> {
                if (tx >= 10 && tx <= 13) {
                    return (ty >= 0 && ty <= 4) ? TileType.BRIDGE_H : TileType.WATER;
                }
                return TileType.GRASS;
            });
            AStarPathfinder pf = AStarPathfinder.forWorldGrid(grid);

            // Offset in Y so straight line hits WATER, forcing routing through BRIDGE_H
            Vector2D startWest = grid.tileToWorld(4, 12);
            Vector2D targetEast = grid.tileToWorld(18, 12);

            for (Enemy enemy : new Enemy[]{
                    new MeleeEnemy(startWest),
                    new TankEnemy(startWest),
                    new BossEnemy(startWest)
            }) {
                List<Vector2D> path = pf.findPath(startWest, targetEast, enemy.getRadius());
                assertTrue(pf.didLastSearchReachGoal(),
                        enemy.getType() + " must reach goal across 5-tile BRIDGE_H");
                assertTrue(pathCrossesTileType(path, startWest, grid, TileType.BRIDGE_H),
                        enemy.getType() + " path must traverse BRIDGE_H tiles");

                boolean steppedOnBridgeH = false;
                boolean reachedOppositeBank = false;
                for (int tick = 0; tick < 1200; tick++) {
                    enemy.updateAIWithGrid(DT, targetEast, grid, pf);
                    enemy.update(DT);
                    assertValidGroundEntityState(enemy, grid,
                            "BRIDGE_H " + enemy.getType() + " tick=" + tick);
                    if (grid.getTile(grid.worldToTileX(enemy.getX()), grid.worldToTileY(enemy.getY())) == TileType.BRIDGE_H) {
                        steppedOnBridgeH = true;
                    }
                    if (enemy.getPosition().dist(targetEast) <= enemy.getRadius() + 24.0f) {
                        reachedOppositeBank = true;
                        break;
                    }
                }
                assertTrue(steppedOnBridgeH, enemy.getType() + " must physically cross BRIDGE_H tiles; pos="
                        + enemy.getPosition() + " navPath=" + enemy.getNavPath()
                        + " wpIdx=" + enemy.getNavWaypointIndex());
                assertTrue(reachedOppositeBank,
                        enemy.getType() + " must finish crossing 5-tile BRIDGE_H to opposite bank; ended at "
                                + enemy.getPosition());
            }
        }

        @Test
        @DisplayName("2.2 3-tile BRIDGE_V (96px) traversal by Melee & Tank, while BossEnemy (r=50) respects narrow bridge")
        void testBridgeVTraversalAndBossNarrowBridgeRespect() {
            // Unbounded E-W river at ty=10..13, 3-tile BRIDGE_V (96px) at tx=0..2
            ControlledUnboundedGrid grid = new ControlledUnboundedGrid((tx, ty) -> {
                if (ty >= 10 && ty <= 13) {
                    return (tx >= 0 && tx <= 2) ? TileType.BRIDGE_V : TileType.WATER;
                }
                return TileType.GRASS;
            });
            AStarPathfinder pf = AStarPathfinder.forWorldGrid(grid);

            Vector2D startSouth = grid.tileToWorld(10, 4);
            Vector2D targetNorth = grid.tileToWorld(-8, 18);

            // Melee (r=16) and Tank (r=26) fit inside 96px (48px half-width > 26px) and cross BRIDGE_V
            for (Enemy enemy : new Enemy[]{new MeleeEnemy(startSouth), new TankEnemy(startSouth)}) {
                List<Vector2D> path = pf.findPath(startSouth, targetNorth, enemy.getRadius());
                assertTrue(pf.didLastSearchReachGoal(),
                        enemy.getType() + " must find complete path across 3-tile BRIDGE_V");
                assertTrue(pathCrossesTileType(path, startSouth, grid, TileType.BRIDGE_V),
                        enemy.getType() + " path must cross BRIDGE_V tiles");

                boolean steppedOnBridgeV = false;
                boolean crossedToTarget = false;
                for (int tick = 0; tick < 1200; tick++) {
                    enemy.updateAIWithGrid(DT, targetNorth, grid, pf);
                    enemy.update(DT);
                    assertValidGroundEntityState(enemy, grid,
                            "BRIDGE_V " + enemy.getType() + " tick=" + tick);
                    if (grid.getTile(grid.worldToTileX(enemy.getX()), grid.worldToTileY(enemy.getY())) == TileType.BRIDGE_V) {
                        steppedOnBridgeV = true;
                    }
                    if (enemy.getPosition().dist(targetNorth) <= enemy.getRadius() + 24.0f) {
                        crossedToTarget = true;
                        break;
                    }
                }
                assertTrue(steppedOnBridgeV, enemy.getType() + " must physically cross BRIDGE_V tiles");
                assertTrue(crossedToTarget,
                        enemy.getType() + " must cross 3-tile BRIDGE_V to north bank; ended at " + enemy.getPosition());
            }

            // BossEnemy (BOSS_RADIUS = 50f > 48px half-width of 96px bridge) must NOT clip water or force narrow bridge
            BossEnemy boss = new BossEnemy(grid.tileToWorld(1, 5));
            Vector2D northAcrossBridge = grid.tileToWorld(1, 18);

            assertFalse(grid.isTraversable(grid.tileToWorldX(1), grid.tileToWorldY(11), BossEnemy.BOSS_RADIUS),
                    "Boss (r=50) must not be marked traversable on 3-tile (96px) BRIDGE_V");
            List<Vector2D> bossPath = pf.findPath(boss.getPosition(), northAcrossBridge, BossEnemy.BOSS_RADIUS);
            assertFalse(pf.didLastSearchReachGoal(),
                    "Boss A* must not claim goal reachable across 3-tile (96px) narrow BRIDGE_V");
            for (Vector2D wp : bossPath) {
                assertTrue(grid.isTraversable(wp, BossEnemy.BOSS_RADIUS),
                        "Every fallback waypoint for Boss must be strictly traversable");
            }

            for (int tick = 0; tick < 240; tick++) {
                boss.updateAIWithGrid(DT, northAcrossBridge, grid, pf);
                boss.update(DT);
                assertValidGroundEntityState(boss, grid, "Boss near narrow BRIDGE_V tick=" + tick);
                assertTrue(boss.getY() < 10 * 32.0f,
                        "Boss must not enter or cross the narrow 3-tile BRIDGE_V into water");
            }
        }

        @Test
        @DisplayName("2.3 Real InfiniteWorld / GameSession.createInfiniteSession routes across real BRIDGE_H and BRIDGE_V")
        void testRealInfiniteWorldAndGameSessionBridgeCrossing() {
            // Find deterministic real procedural BRIDGE_H and BRIDGE_V river crossings in InfiniteWorld
            GameSession session = GameSession.createInfiniteSession(null, 42L, false);
            session.startMatch();
            session.getSpawner().setMaxEnemies(0);
            session.getSpawner().setBossSpawnTimer(99999.0f);

            InfiniteWorld world = session.getInfiniteWorld();
            AStarPathfinder pf = session.getPathfinder();

            int[] bridgeH = findRealBridgeHCrossing(world, 0, -400, 400);
            if (bridgeH == null) {
                bridgeH = findRealBridgeHCrossing(world, 256, -400, 400);
            }
            assertNotNull(bridgeH, "Procedural InfiniteWorld seed=42 must contain a BRIDGE_H river crossing");

            int hMidTx = (bridgeH[0] + bridgeH[1]) / 2;
            int hWestTx = bridgeH[2];
            int hEastTx = bridgeH[3];
            int highwayY = (world.getTile(hMidTx, 0) == TileType.BRIDGE_H) ? 0 : 256;

            // Player on east bank, MeleeEnemy & TankEnemy on west bank
            Vector2D playerEast = world.tileToWorld(hEastTx, highwayY);
            Vector2D enemyWest = world.tileToWorld(hWestTx, highwayY);
            session.getPlayer().setMaxHealth(100_000.0f);
            session.getPlayer().setHealth(100_000.0f);
            session.getPlayer().setPosition(playerEast);

            List<Vector2D> hPath = pf.findPath(enemyWest, playerEast, MeleeEnemy.DEFAULT_RADIUS);
            assertTrue(pf.didLastSearchReachGoal(), "Real InfiniteWorld BRIDGE_H path must reach east bank");
            assertTrue(pathCrossesTileType(hPath, enemyWest, world, TileType.BRIDGE_H),
                    "Real InfiniteWorld path must cross BRIDGE_H tiles");

            MeleeEnemy melee = new MeleeEnemy(enemyWest);
            TankEnemy tank = new TankEnemy(enemyWest.cpy().add(-32.0f, 0.0f));
            session.addEnemy(melee);
            session.addEnemy(tank);

            boolean meleeSteppedBridgeH = false;
            boolean meleeArrived = false;
            boolean tankArrived = false;
            for (int tick = 0; tick < 900; tick++) {
                session.update(DT);
                assertValidGroundEntityState(melee, world, "Real InfiniteWorld BRIDGE_H melee tick=" + tick);
                assertValidGroundEntityState(tank, world, "Real InfiniteWorld BRIDGE_H tank tick=" + tick);
                if (world.getTile(world.worldToTileX(melee.getX()), world.worldToTileY(melee.getY())) == TileType.BRIDGE_H) {
                    meleeSteppedBridgeH = true;
                }
                if (melee.getPosition().dist(session.getPlayer().getPosition()) <= 48.0f) {
                    meleeArrived = true;
                }
                if (tank.getPosition().dist(session.getPlayer().getPosition()) <= 56.0f) {
                    tankArrived = true;
                }
                if (meleeArrived && tankArrived) {
                    break;
                }
            }
            assertTrue(meleeSteppedBridgeH, "MeleeEnemy must physically cross real InfiniteWorld BRIDGE_H tiles");
            assertTrue(meleeArrived, "MeleeEnemy must cross real InfiniteWorld BRIDGE_H to player");
            assertTrue(tankArrived, "TankEnemy must cross real InfiniteWorld BRIDGE_H to player; tankPos="
                    + tank.getPosition() + " meleePos=" + melee.getPosition()
                    + " playerPos=" + session.getPlayer().getPosition()
                    + " gameState=" + session.getGameState()
                    + " tankNavPath=" + tank.getNavPath()
                    + " hBridge=[" + bridgeH[0] + ".." + bridgeH[1] + "] y=" + highwayY
                    + " tankTraversable=" + world.isTraversable(world.tileToWorld(hMidTx, highwayY), TankEnemy.DEFAULT_RADIUS));

            // Also verify real procedural BRIDGE_V crossing on InfiniteWorld (try trail x=0 or x=256 or x=-256)
            int trailX = 0;
            int[] bridgeV = findRealBridgeVCrossing(world, 0, -500, 500);
            if (bridgeV == null) {
                trailX = 256;
                bridgeV = findRealBridgeVCrossing(world, 256, -500, 500);
            }
            if (bridgeV == null) {
                trailX = -256;
                bridgeV = findRealBridgeVCrossing(world, -256, -500, 500);
            }
            assertNotNull(bridgeV, "Procedural InfiniteWorld seed=42 must contain a BRIDGE_V river crossing");

            Vector2D southBank = world.tileToWorld(trailX, bridgeV[2]);
            Vector2D northBank = world.tileToWorld(trailX, bridgeV[3]);

            MeleeEnemy vMelee = new MeleeEnemy(southBank);
            List<Vector2D> vPath = pf.findPath(southBank, northBank, vMelee.getRadius());
            assertTrue(pf.didLastSearchReachGoal(), "Real InfiniteWorld BRIDGE_V path must reach north bank");
            assertTrue(pathCrossesTileType(vPath, southBank, world, TileType.BRIDGE_V),
                    "Real InfiniteWorld path must cross BRIDGE_V tiles");

            boolean crossedV = false;
            boolean steppedBridgeV = false;
            for (int tick = 0; tick < 900; tick++) {
                vMelee.updateAIWithGrid(DT, northBank, world, pf);
                vMelee.update(DT);
                assertValidGroundEntityState(vMelee, world, "Real InfiniteWorld BRIDGE_V melee tick=" + tick);
                if (world.getTile(world.worldToTileX(vMelee.getX()), world.worldToTileY(vMelee.getY())) == TileType.BRIDGE_V) {
                    steppedBridgeV = true;
                }
                if (vMelee.getPosition().dist(northBank) <= 40.0f) {
                    crossedV = true;
                    break;
                }
            }
            assertTrue(steppedBridgeV, "MeleeEnemy must physically cross real InfiniteWorld BRIDGE_V tiles");
            assertTrue(crossedV, "MeleeEnemy must cross real InfiniteWorld BRIDGE_V to north bank");
        }
    }

    // =========================================================================
    // 3. Negative Coordinates & Cross-Chunk Boundary Navigation
    // =========================================================================

    @Nested
    @DisplayName("3. Negative Coordinates & Cross-Chunk Boundary Navigation")
    class NegativeAndCrossChunkTests {

        @Test
        @DisplayName("3.1 InfiniteWorld.tileToWorldX/Y returns exact tile centers ((tx + 0.5)*32 - originShift) including negative tiles")
        void testInfiniteWorldTileCenterAndNegativeMappings() {
            InfiniteWorld world = createDeterministicInfiniteWorld((tx, ty) -> TileType.GRASS, null);
            FloatingOriginManager origin = world.getOriginManager();

            int[] sampleTiles = {-100, -33, -32, -1, 0, 1, 31, 32, 100};
            for (int tx : sampleTiles) {
                assertEquals((tx + 0.5f) * 32.0f, world.tileToWorldX(tx), 1e-4f);
                assertEquals((tx + 0.5f) * 32.0f, world.tileToWorldY(tx), 1e-4f);
                assertEquals(tx, world.worldToTileX(world.tileToWorldX(tx)));
                assertEquals(tx, world.worldToTileY(world.tileToWorldY(tx)));
            }

            // Explicit chunk seam -32 / -33 around x = -1024.0f
            assertEquals(-32, world.worldToTileX(-1024.0f));
            assertEquals(-33, world.worldToTileX(-1024.01f));
            assertEquals(-1, world.worldToTileX(-0.01f));
            assertEquals(-1, world.worldToTileY(-16.0f));

            // After floating-origin shift (-1024, -1024) and (+2048, -1024)
            origin.rebaseWorldOrigin(-1024L, -1024L);
            for (int tx : sampleTiles) {
                assertEquals((tx + 0.5f) * 32.0f - (-1024.0f), world.tileToWorldX(tx), 1e-4f);
                assertEquals((tx + 0.5f) * 32.0f - (-1024.0f), world.tileToWorldY(tx), 1e-4f);
                assertEquals(tx, world.worldToTileX(world.tileToWorldX(tx)));
                assertEquals(tx, world.worldToTileY(world.tileToWorldY(tx)));
            }

            origin.rebaseWorldOrigin(3072L, 0L); // net originShift = (+2048, -1024)
            assertEquals((-33 + 0.5f) * 32.0f - 2048.0f, world.tileToWorldX(-33), 1e-4f);
            assertEquals((-1 + 0.5f) * 32.0f - (-1024.0f), world.tileToWorldY(-1), 1e-4f);
        }

        @Test
        @DisplayName("3.2 Navigation across (-1, -1) and chunk seam -32/-33 (-1024, -1024) in deterministic InfiniteWorld & unbounded Grid")
        void testNegativeQuadrantSeamAndMinusOneTileNavigation() {
            // River sits at tx = -33..-30 (spanning the -32/-33 chunk seam at tx=-32/-33, x=-1024),
            // with a 5-tile BRIDGE_H at ty = -34..-30 (also crossing ty = -32/-33!)
            // plus a solid obstacle near (-1, -1) forcing pathing right through tx=-1, ty=-1!
            List<Obstacle> obstacles = List.of(
                    new Obstacle(DecoType.ROCK_LARGE, -48.0f, -48.0f + 6.0f, true) // collision center at (-48, -48) in (-2,-2) tile
            );
            TileRule rule = (tx, ty) -> {
                if (tx >= -34 && tx <= -31) {
                    return (ty >= -34 && ty <= -30) ? TileType.BRIDGE_H : TileType.WATER;
                }
                return TileType.GRASS;
            };

            InfiniteWorld world = createDeterministicInfiniteWorld(rule, obstacles);
            AStarPathfinder pf = AStarPathfinder.forWorldGrid(world);

            // Start in chunk (-2, -2) around (-1280, -1280) = tile (-40, -40)
            // Target right at tile (-1, -1) center (-16, -16) — wait, split into two checks:
            // 1) Cross -32/-33 seam via bridge to (-20, -20)
            Vector2D startChunkNeg2 = world.tileToWorld(-40, -24);
            Vector2D goalChunkNeg1 = world.tileToWorld(-1, -1); // Explicitly exercises tx=-1, ty=-1!

            assertTrue(world.isTraversable(startChunkNeg2, MeleeEnemy.DEFAULT_RADIUS));
            assertTrue(world.isTraversable(goalChunkNeg1, MeleeEnemy.DEFAULT_RADIUS));

            List<Vector2D> path = pf.findPath(startChunkNeg2, goalChunkNeg1, MeleeEnemy.DEFAULT_RADIUS);
            assertTrue(pf.didLastSearchReachGoal(),
                    "Path across -32/-33 chunk seam to (-1, -1) must succeed");
            assertFalse(path.isEmpty());
            for (Vector2D wp : path) {
                assertTrue(world.isTraversable(wp, MeleeEnemy.DEFAULT_RADIUS),
                        "Negative-chunk waypoint must be traversable: " + wp);
            }

            // Explicitly test short A* path directly through tx=-1, ty=-1 (verifies CellState.hasParent with tx=-1!)
            ControlledUnboundedGrid minusOneGrid = new ControlledUnboundedGrid((tx, ty) ->
                    (tx == -2 && ty >= -4 && ty <= 2 && ty != -1) ? TileType.WATER : TileType.GRASS);
            AStarPathfinder minusOnePf = AStarPathfinder.forWorldGrid(minusOneGrid);
            Vector2D from = minusOneGrid.tileToWorld(-4, -1);
            Vector2D to = minusOneGrid.tileToWorld(1, -1);
            List<Vector2D> gapPath = minusOnePf.findPath(from, to, 12.0f);
            assertTrue(minusOnePf.didLastSearchReachGoal(), "Path through (-1, -1) must reach goal");
            boolean passedThroughMinusOne = gapPath.stream().anyMatch(wp ->
                    minusOneGrid.worldToTileX(wp.x) == -1 && minusOneGrid.worldToTileY(wp.y) == -1);
            assertTrue(passedThroughMinusOne || gapPath.get(gapPath.size() - 1).x > -16.0f,
                    "Path must traverse (-1, -1) corridor cleanly");

            // Physically simulate MeleeEnemy across -32/-33 seam (-1024, -1024) to (-1, -1) in InfiniteWorld
            MeleeEnemy runner = new MeleeEnemy(startChunkNeg2);
            boolean reachedMinusOne = false;
            for (int tick = 0; tick < 1200; tick++) {
                runner.updateAIWithGrid(DT, goalChunkNeg1, world, pf);
                runner.update(DT);
                assertValidGroundEntityState(runner, world, "Negative seam crossing tick=" + tick);
                if (runner.getPosition().dist(goalChunkNeg1) <= 24.0f) {
                    reachedMinusOne = true;
                    break;
                }
            }
            assertTrue(reachedMinusOne,
                    "Enemy must physically navigate from chunk (-2,-1) across -1024 seam to tile (-1,-1); pos="
                            + runner.getPosition());
        }

        @Test
        @DisplayName("3.3 Real GameSession.createInfiniteSession centered in negative chunks (-1024, -1024) navigates across seam cleanly")
        void testRealInfiniteSessionNegativeChunksAroundMinus1024() {
            GameSession session = GameSession.createInfiniteSession(null, 42L, false);
            session.startMatch();
            session.getSpawner().setMaxEnemies(0);
            session.getSpawner().setBossSpawnTimer(99999.0f);

            InfiniteWorld world = session.getInfiniteWorld();

            // Place player near (-1024, -1024) on walkable terrain in chunk (-1, -1) / (-2, -2)
            Vector2D playerPos = findDryTileNear(world, -30, -30, BossEnemy.BOSS_RADIUS);
            Vector2D enemyPos = findDryTileNear(world, -38, -38, TankEnemy.DEFAULT_RADIUS);
            assertNotNull(playerPos);
            assertNotNull(enemyPos);

            session.getPlayer().setPosition(playerPos);
            TankEnemy tank = new TankEnemy(enemyPos);
            MeleeEnemy melee = new MeleeEnemy(findDryTileNear(world, -36, -34, MeleeEnemy.DEFAULT_RADIUS));
            session.addEnemy(tank);
            session.addEnemy(melee);

            for (int tick = 0; tick < 240; tick++) {
                session.update(DT);
                assertValidGroundEntityState(tank, world, "Real neg-chunk tank tick=" + tick);
                assertValidGroundEntityState(melee, world, "Real neg-chunk melee tick=" + tick);
            }

            // Verify tile (-1, -1) coordinate navigation in real InfiniteWorld
            Vector2D nearOriginNeg = findDryTileNear(world, -1, -1, MeleeEnemy.DEFAULT_RADIUS);
            Vector2D nearOriginNegStart = findDryTileNear(world, -6, -6, MeleeEnemy.DEFAULT_RADIUS);
            assertNotNull(nearOriginNeg);
            assertNotNull(nearOriginNegStart);
            MeleeEnemy originRunner = new MeleeEnemy(nearOriginNegStart);
            for (int tick = 0; tick < 180; tick++) {
                originRunner.updateAIWithGrid(DT, nearOriginNeg, world, session.getPathfinder());
                originRunner.update(DT);
                assertValidGroundEntityState(originRunner, world, "Real (-1,-1) runner tick=" + tick);
            }
        }
    }

    private static Vector2D findDryTileNear(WorldGrid grid, int baseTx, int baseTy, float radius) {
        for (int r = 0; r <= 20; r++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dx = -r; dx <= r; dx++) {
                    int tx = baseTx + dx;
                    int ty = baseTy + dy;
                    float wx = grid.tileToWorldX(tx);
                    float wy = grid.tileToWorldY(ty);
                    if (!grid.isWaterTile(tx, ty) && grid.isTraversable(wx, wy, radius)) {
                        return new Vector2D(wx, wy);
                    }
                }
            }
        }
        return null;
    }

    // =========================================================================
    // 4. Ranged Attacks Across Water Preserved
    // =========================================================================

    @Nested
    @DisplayName("4. Ranged Attacks Across Water Preserved")
    class RangedAcrossWaterTests {

        @Test
        @DisplayName("4.1 RangedEnemy fires across deep water within shootRange while staying dry on land")
        void testRangedEnemyShootsAcrossWaterAndStaysDry() {
            // River tx in [-1..2] (128px wide). RangedEnemy at west bank (-112, 16), Player at east bank (112, 16) -> dist = 224px <= 350px
            TileRule riverRule = (tx, ty) -> (tx >= -1 && tx <= 2) ? TileType.WATER : TileType.GRASS;
            InfiniteWorld world = createDeterministicInfiniteWorld(riverRule, null);
            AStarPathfinder pf = AStarPathfinder.forWorldGrid(world);

            Vector2D enemyPos = new Vector2D(-112.0f, 16.0f);
            Vector2D playerPos = new Vector2D(112.0f, 16.0f);

            assertTrue(pf.hasRangedLineOfSight(enemyPos, playerPos, 7.0f),
                    "Ranged LOS across open WATER must be TRUE");
            assertFalse(pf.hasLineOfSight(enemyPos, playerPos, RangedEnemy.DEFAULT_RADIUS),
                    "Ground movement LOS across WATER must be FALSE");

            RangedEnemy ranged = new RangedEnemy(enemyPos);
            assertTrue(enemyPos.dist(playerPos) <= ranged.getShootRange());

            // Simulate AI + update until ranged fires projectile
            List<Projectile> fired = new ArrayList<>();
            for (int tick = 0; tick < 120; tick++) {
                ranged.updateAIWithGrid(DT, playerPos, world, pf);
                ranged.update(DT);
                assertTrue(ranged.isInAttackRange(),
                        "RangedEnemy within 350px across water must report isInAttackRange()==true");
                assertValidGroundEntityState(ranged, world, "Ranged across water tick=" + tick);
                if (ranged.canShoot()) {
                    fired.add(ranged.shoot(playerPos));
                }
            }
            assertFalse(fired.isEmpty(), "RangedEnemy must fire projectile(s) across water");
            Projectile shot = fired.get(0);
            assertEquals(ProjectileFaction.ENEMY, shot.getFaction());
            assertTrue(shot.getDirection().x > 0.9f, "Projectile must aim eastward across river toward player");

            // Also verify in real GameSession.createInfiniteSession (with procedural river found or controlled session)
            GameSession session = GameSession.createInfiniteSession(null, 42L, false);
            session.startMatch();
            session.getSpawner().setMaxEnemies(0);
            session.getSpawner().setBossSpawnTimer(99999.0f);

            InfiniteWorld realWorld = session.getInfiniteWorld();
            int[] shoreline = null;
            for (int y = 20; y <= 150 && shoreline == null; y += 5) {
                shoreline = findRealRiverShoreline(realWorld, -200, 200, y);
            }
            if (shoreline != null) {
                int waterTx = shoreline[0];
                int ty = shoreline[1];
                int eastDryTx = waterTx + 1;
                while (eastDryTx < waterTx + 12 && (realWorld.isWaterTile(eastDryTx, ty)
                        || !realWorld.isTraversable(realWorld.tileToWorldX(eastDryTx), realWorld.tileToWorldY(ty), 20.0f))) {
                    eastDryTx++;
                }
                Vector2D wPos = realWorld.tileToWorld(shoreline[2], ty);
                Vector2D ePos = realWorld.tileToWorld(eastDryTx, ty);
                if (wPos.dist(ePos) <= RangedEnemy.DEFAULT_SHOOT_RANGE
                        && session.getPathfinder().hasRangedLineOfSight(wPos, ePos, 7.0f)) {
                    session.getPlayer().setPosition(ePos);
                    RangedEnemy realRanged = new RangedEnemy(wPos);
                    session.addEnemy(realRanged);
                    int initialHp = Math.round(session.getPlayer().getHealth());
                    boolean sawProjectile = false;
                    for (int tick = 0; tick < 180; tick++) {
                        session.update(DT);
                        assertValidGroundEntityState(realRanged, realWorld,
                                "Real InfiniteWorld ranged across river tick=" + tick);
                        if (!session.getCombatSystem().getProjectiles().isEmpty()) {
                            sawProjectile = true;
                        }
                    }
                    assertTrue(sawProjectile || session.getPlayer().getHealth() < initialHp,
                            "RangedEnemy in real GameSession must fire across procedural river");
                }
            }

            // Also test retreating/kiting near water while still shooting across water (narrow 2-tile river tx=0..1, enemy backed up by water tx=-3)
            ControlledUnboundedGrid kitingGrid = new ControlledUnboundedGrid((tx, ty) ->
                    (tx == -3 || tx == 0 || tx == 1) ? TileType.WATER : TileType.GRASS);
            AStarPathfinder kitingPf = AStarPathfinder.forWorldGrid(kitingGrid);
            RangedEnemy kiter = new RangedEnemy(kitingGrid.tileToWorld(-2, 0)); // x=-48, right next to west water tx=-3
            Vector2D closeEastPlayer = kitingGrid.tileToWorld(2, 0); // x=80, dist=128 < 205 -> retreats west toward tx=-3 water!
            for (int tick = 0; tick < 120; tick++) {
                kiter.updateAIWithGrid(DT, closeEastPlayer, kitingGrid, kitingPf);
                kiter.update(DT);
                assertTrue(kiter.isInAttackRange(), "Retreating RangedEnemy across water must stay inAttackRange");
                assertValidGroundEntityState(kiter, kitingGrid, "Kiting RangedEnemy near water tick=" + tick);
            }
        }

        @Test
        @DisplayName("4.2 Solid Obstacle between RangedEnemy and Player across water blocks ranged LOS and shooting")
        void testSolidObstacleBlocksRangedLOSAcrossWater() {
            // Part A: Single solid ROCK_LARGE directly on ray across river tx in [-1..2]
            // Note: ROCK_LARGE has collisionOffset (0, -6), so anchor (-32, 22) -> collisionCenter (-32, 16)
            List<Obstacle> singleObs = List.of(new Obstacle(DecoType.ROCK_LARGE, -32.0f, 22.0f, true));
            InfiniteWorld singleObsWorld = createDeterministicInfiniteWorld(
                    (tx, ty) -> (tx >= -1 && tx <= 2) ? TileType.WATER : TileType.GRASS, singleObs);
            AStarPathfinder singlePf = AStarPathfinder.forWorldGrid(singleObsWorld);

            Vector2D enemyPos = new Vector2D(-112.0f, 16.0f);
            Vector2D playerPos = new Vector2D(112.0f, 16.0f);

            assertFalse(singlePf.hasRangedLineOfSight(enemyPos, playerPos, 7.0f),
                    "Solid obstacle on ray across water must block hasRangedLineOfSight");

            RangedEnemy behindSingleRock = new RangedEnemy(enemyPos);
            behindSingleRock.update(2.0f); // Clear initial 1.0s shot delay so only obstacle/LOS blocks firing
            behindSingleRock.updateAIWithGrid(DT, playerPos, singleObsWorld, singlePf);
            assertFalse(behindSingleRock.isInAttackRange(),
                    "Blocked RangedEnemy directly behind ROCK_LARGE must NOT report isInAttackRange()==true");
            assertFalse(behindSingleRock.canShoot(),
                    "Blocked RangedEnemy directly behind ROCK_LARGE must NOT be allowed to shoot");

            // Part B: Multi-tick AI loop behind a shoreline wall of ROCK_LARGE boulders (prevents 1-rock sidestep)
            List<Obstacle> wallObs = new ArrayList<>();
            for (int ty = -8; ty <= 8; ty++) {
                float cy = ty * 24.0f + 16.0f;
                wallObs.add(new Obstacle(DecoType.ROCK_LARGE, -32.0f, cy + 6.0f, true));
            }
            InfiniteWorld world = createDeterministicInfiniteWorld(
                    (tx, ty) -> (tx >= -1 && tx <= 2) ? TileType.WATER : TileType.GRASS, wallObs);
            AStarPathfinder pf = AStarPathfinder.forWorldGrid(world);

            assertFalse(pf.hasRangedLineOfSight(enemyPos, playerPos, 7.0f),
                    "Obstacle barrier on shore must block hasRangedLineOfSight");

            RangedEnemy ranged = new RangedEnemy(enemyPos);
            ranged.update(2.0f);
            for (int tick = 0; tick < 90; tick++) {
                ranged.updateAIWithGrid(DT, playerPos, world, pf);
                ranged.update(DT);
                assertFalse(ranged.isInAttackRange(),
                        "Blocked RangedEnemy must NOT report isInAttackRange()==true");
                assertFalse(ranged.canShoot(),
                        "Blocked RangedEnemy must NOT be allowed to shoot through solid obstacle");
                assertValidGroundEntityState(ranged, world, "Blocked RangedEnemy tick=" + tick);
            }

            // Bounded TileMap parity: place solid TREE_OAK on ray across TileMap river
            TileMap tileMap = new TileMap(42L);
            tileMap.clearObstacles();
            AStarPathfinder mapPf = new AStarPathfinder(tileMap);
            int minWaterTx = -1;
            int maxWaterTx = -1;
            for (int x = 30; x <= 55; x++) {
                if (tileMap.isWaterTile(x, 46)) {
                    if (minWaterTx < 0) minWaterTx = x;
                    maxWaterTx = x;
                }
            }
            assertTrue(minWaterTx > 0);
            Vector2D mapEnemy = tileMap.tileToWorld(minWaterTx - 3, 46);
            Vector2D mapPlayer = tileMap.tileToWorld(maxWaterTx + 3, 46);
            // Place TREE_OAK (offset y = -8) right on the west shore along the ray
            Vector2D rockAnchor = new Vector2D(tileMap.tileToWorldX(minWaterTx - 1), mapEnemy.y + 8.0f);
            tileMap.addDecoration(DecoType.TREE_OAK, rockAnchor, true);

            assertFalse(mapPf.hasRangedLineOfSight(mapEnemy, mapPlayer, 7.0f),
                    "Bounded TileMap obstacle must block hasRangedLineOfSight");
            RangedEnemy mapRanged = new RangedEnemy(mapEnemy);
            mapRanged.updateAI(DT, mapPlayer, tileMap, mapPf);
            mapRanged.update(2.0f);
            mapRanged.updateAI(DT, mapPlayer, tileMap, mapPf);
            assertFalse(mapRanged.isInAttackRange());
            assertFalse(mapRanged.canShoot());
        }
    }

    // =========================================================================
    // 5. Anti-Stuck Shoreline Handling & Recovery
    // =========================================================================

    @Nested
    @DisplayName("5. Anti-Stuck Shoreline Handling & Recovery")
    class AntiStuckShorelineTests {

        @Test
        @DisplayName("5.1 Unbridged river triggers partial-path backoff (no per-frame A* thrash), shoreline sliding, and instant same-bank recovery")
        void testUnbridgedShorelineBackoffSlidingAndRecovery() {
            // Continuous unbridged river tx in [8..11] across all ty
            ControlledUnboundedGrid grid = new ControlledUnboundedGrid((tx, ty) ->
                    (tx >= 8 && tx <= 11) ? TileType.WATER : TileType.GRASS);
            AStarPathfinder pf = AStarPathfinder.forWorldGrid(grid);

            MeleeEnemy enemy = new MeleeEnemy(grid.tileToWorld(2, 0));
            Vector2D eastTarget = grid.tileToWorld(16, 0);

            // Phase A: Advance toward shoreline
            for (int tick = 0; tick < 180; tick++) {
                enemy.updateAIWithGrid(DT, eastTarget, grid, pf);
                enemy.update(DT);
                assertValidGroundEntityState(enemy, grid, "Phase A shoreline approach tick=" + tick);
            }
            assertFalse(pf.didLastSearchReachGoal(),
                    "Unbridged river beyond search horizon must report didLastSearchReachGoal()==false");
            assertTrue(enemy.getX() > 200.0f && enemy.getX() < 8 * 32.0f,
                    "Enemy must advance up to the west shoreline, x=" + enemy.getX());

            // Phase B: Measure A* search frequency over 300 ticks (5.0 seconds) at shoreline
            grid.resetGetTileCallCount();
            int aStarSearchTicks = 0;
            float minYObserved = enemy.getY();
            float maxYObserved = enemy.getY();

            for (int tick = 0; tick < 300; tick++) {
                int beforeCalls = grid.getTileCallCount();
                enemy.updateAIWithGrid(DT, eastTarget, grid, pf);
                enemy.update(DT);
                int deltaCalls = grid.getTileCallCount() - beforeCalls;
                if (deltaCalls > 0) {
                    aStarSearchTicks++;
                }
                assertValidGroundEntityState(enemy, grid, "Phase B shoreline patrol tick=" + tick);
                minYObserved = Math.min(minYObserved, enemy.getY());
                maxYObserved = Math.max(maxYObserved, enemy.getY());
            }

            assertTrue(aStarSearchTicks <= 10,
                    "Partial-path backoff must prevent every-frame A* thrashing over 300 ticks; ran on "
                            + aStarSearchTicks + " ticks");
            assertTrue((maxYObserved - minYObserved) > 32.0f,
                    "Enemy must slide cleanly along shoreline instead of freezing in place; Y span="
                            + (maxYObserved - minYObserved));

            // Phase C: Move target onto the SAME (west) bank with an obstacle in between (farther than 650px / blocked LOS)
            // so full recovery via A* + direct pursuit is both verified
            Vector2D sameBankGoal = new Vector2D(enemy.getX() - 280.0f, enemy.getY() + 120.0f);
            boolean reachedSameBankGoal = false;
            for (int tick = 0; tick < 400; tick++) {
                enemy.updateAIWithGrid(DT, sameBankGoal, grid, pf);
                enemy.update(DT);
                assertValidGroundEntityState(enemy, grid, "Phase C same-bank recovery tick=" + tick);
                if (enemy.getPosition().dist(sameBankGoal) <= enemy.getRadius() + 8.0f) {
                    reachedSameBankGoal = true;
                    break;
                }
            }
            assertTrue(reachedSameBankGoal,
                    "Once target moves to the same bank, enemy must recover immediately and reach target; pos="
                            + enemy.getPosition() + " goal=" + sameBankGoal);
        }
    }

    // =========================================================================
    // 6. Floating-Origin Rebasing Mid-Navigation
    // =========================================================================

    @Nested
    @DisplayName("6. Floating-Origin Rebasing Mid-Navigation")
    class FloatingOriginRebaseNavTests {

        @Test
        @DisplayName("6.1 Rebase mid-bridge shifts navPath, lastTargetPos, navLastPosition, and enemy coordinates cleanly to completion")
        void testFloatingOriginRebaseMidBridgeNavigation() {
            GameSession session = GameSession.createInfiniteSession(null, 42L, false);
            session.startMatch();
            session.getSpawner().setMaxEnemies(0);
            session.getSpawner().setBossSpawnTimer(99999.0f);

            InfiniteWorld world = session.getInfiniteWorld();
            int[] bridgeH = findRealBridgeHCrossing(world, 0, -400, 400);
            if (bridgeH == null) {
                bridgeH = findRealBridgeHCrossing(world, 256, -400, 400);
            }
            assertNotNull(bridgeH, "Real InfiniteWorld seed=42 must have a BRIDGE_H crossing");

            int highwayY = (world.getTile((bridgeH[0] + bridgeH[1]) / 2, 0) == TileType.BRIDGE_H) ? 0 : 256;
            // Offset Y slightly off highway on both sides so direct LOS is blocked by river water,
            // forcing a genuine multi-waypoint navPath across the bridge.
            Vector2D enemyStart = world.tileToWorld(bridgeH[2], highwayY - 5);
            Vector2D playerTarget = world.tileToWorld(bridgeH[3], highwayY + 5);
            assertTrue(world.isTraversable(enemyStart, MeleeEnemy.DEFAULT_RADIUS));
            assertTrue(world.isTraversable(playerTarget, 20.0f));

            session.getPlayer().setPosition(playerTarget);
            MeleeEnemy enemy = new MeleeEnemy(enemyStart);
            session.addEnemy(enemy);

            // Step until enemy has an active multi-waypoint bridge path
            for (int tick = 0; tick < 60; tick++) {
                session.update(DT);
                assertValidGroundEntityState(enemy, world, "Pre-rebase bridge nav tick=" + tick);
                if (enemy.getNavPath().size() >= 2 && tick >= 20) {
                    break;
                }
            }
            assertTrue(enemy.getNavPath().size() >= 2,
                    "Enemy must have an active multi-waypoint navPath before rebase, size=" + enemy.getNavPath().size());

            // Snapshot coordinates prior to floating-origin rebase
            Vector2D preEnemyPos = enemy.getPosition().cpy();
            Vector2D prePlayerPos = session.getPlayer().getPosition().cpy();
            Vector2D preLastTarget = enemy.getNavLastTargetPos().cpy();
            Vector2D preLastPos = enemy.getNavLastPosition().cpy();
            List<Vector2D> preNavPath = new ArrayList<>(enemy.getNavPath().size());
            for (Vector2D wp : enemy.getNavPath()) {
                preNavPath.add(wp.cpy());
            }

            long deltaX = 1024L;
            long deltaY = 1024L;
            session.getOriginManager().rebaseWorldOrigin(deltaX, deltaY);

            // Assert exact atomic origin delta shift across enemy, navPath, lastTargetPos, navLastPosition, and player
            assertEquals(preEnemyPos.x - deltaX, enemy.getPosition().x, 1e-3f);
            assertEquals(preEnemyPos.y - deltaY, enemy.getPosition().y, 1e-3f);
            assertEquals(prePlayerPos.x - deltaX, session.getPlayer().getPosition().x, 1e-3f);
            assertEquals(prePlayerPos.y - deltaY, session.getPlayer().getPosition().y, 1e-3f);
            assertEquals(preLastTarget.x - deltaX, enemy.getNavLastTargetPos().x, 1e-3f);
            assertEquals(preLastTarget.y - deltaY, enemy.getNavLastTargetPos().y, 1e-3f);
            assertEquals(preLastPos.x - deltaX, enemy.getNavLastPosition().x, 1e-3f);
            assertEquals(preLastPos.y - deltaY, enemy.getNavLastPosition().y, 1e-3f);
            assertEquals(preNavPath.size(), enemy.getNavPath().size());
            for (int i = 0; i < preNavPath.size(); i++) {
                Vector2D expectedWp = preNavPath.get(i);
                Vector2D actualWp = enemy.getNavPath().get(i);
                assertEquals(expectedWp.x - deltaX, actualWp.x, 1e-3f, "Waypoint " + i + " X must shift by -deltaX");
                assertEquals(expectedWp.y - deltaY, actualWp.y, 1e-3f, "Waypoint " + i + " Y must shift by -deltaY");
            }
            assertValidGroundEntityState(enemy, world, "Immediately post-rebase");

            // Continue stepping simulation until enemy finishes crossing the river to the player's bank
            boolean reachedTarget = false;
            for (int tick = 0; tick < 900; tick++) {
                session.update(DT);
                assertValidGroundEntityState(enemy, world, "Post-rebase bridge crossing tick=" + tick);
                if (enemy.getPosition().dist(session.getPlayer().getPosition()) <= 48.0f) {
                    reachedTarget = true;
                    break;
                }
            }
            assertTrue(reachedTarget,
                    "Enemy must complete bridge crossing to opposite bank after mid-navigation origin rebase; pos="
                            + enemy.getPosition() + " player=" + session.getPlayer().getPosition());
        }
    }

    // =========================================================================
    // 7. Unambiguous Spawner & Scenario Validation in InfiniteWorld
    // =========================================================================

    @Nested
    @DisplayName("7. Unambiguous Spawner & Scenario Validation in InfiniteWorld")
    class SpawnerAndScenarioValidationTests {

        @Test
        @DisplayName("7.1 EnemySpawner.forWorldGrid(infiniteWorld, rng) spawns across positive & negative anchors strictly on traversable land")
        void testInfiniteWorldSpawnerNeverSpawnsInWater() {
            long[] seeds = {42L, 123456789L};
            Vector2D[] anchors = {
                    new Vector2D(0.0f, 0.0f),
                    new Vector2D(-1024.0f, -1024.0f),
                    new Vector2D(2560.0f, -1536.0f)
            };

            for (long seed : seeds) {
                InfiniteWorld world = new InfiniteWorld(new ChunkManagerImpl(seed, 64), new FloatingOriginManager());
                EnemySpawner rawSpawner = EnemySpawner.forWorldGrid(world, new Random(seed + 7));
                EnemySpawner balancedSpawner = EnemySpawner.forWorldGrid(world, new Random(seed + 13), BalanceConfig.defaults());
                assertSame(world, rawSpawner.getWorldGrid());
                assertSame(world, balancedSpawner.getWorldGrid());

                for (Vector2D anchor : anchors) {
                    for (int i = 0; i < 15; i++) {
                        EnemyType override = switch (i % 4) {
                            case 0 -> EnemyType.MELEE;
                            case 1 -> EnemyType.RANGED;
                            case 2 -> EnemyType.TANK;
                            default -> null;
                        };
                        Enemy e1 = rawSpawner.spawnEnemy(anchor, override);
                        assertValidGroundEntityState(e1, world,
                                "Raw InfiniteWorld spawn seed=" + seed + " anchor=" + anchor + " i=" + i);

                        Enemy e2 = balancedSpawner.spawnEnemy(anchor, override);
                        assertValidGroundEntityState(e2, world,
                                "Balanced InfiniteWorld spawn seed=" + seed + " anchor=" + anchor + " i=" + i);
                    }
                    Enemy boss = balancedSpawner.spawnBoss(anchor);
                    assertValidGroundEntityState(boss, world,
                            "Boss InfiniteWorld spawn seed=" + seed + " anchor=" + anchor);
                }
            }
        }

        @Test
        @DisplayName("7.2 SpellStormGame exploration scenarios in InfiniteWorld spawn and simulate enemies strictly on traversable land")
        void testExplorationScenariosInfiniteWorldNeverEnterWater() {
            String[] scenarios = {"exploration-plains", "exploration-forest", "exploration-radar"};
            for (String scenario : scenarios) {
                GameSession session = GameSession.createInfiniteSession(null, 42L, false);
                EngineConfig cfg = EngineConfig.fromArgs(new String[]{"--scenario", scenario});
                SpellStormGame.applyConfiguredScenarios(session, cfg);

                InfiniteWorld world = session.getInfiniteWorld();
                assertFalse(session.getEnemies().isEmpty(), scenario + " must spawn initial enemies");
                for (Enemy e : session.getEnemies()) {
                    assertValidGroundEntityState(e, world, scenario + " initial spawn " + e.getType());
                }

                for (int tick = 0; tick < 120; tick++) {
                    session.update(DT);
                    for (Enemy e : session.getEnemies()) {
                        if (e.isAlive()) {
                            assertValidGroundEntityState(e, world,
                                    scenario + " simulation " + e.getType() + " tick=" + tick);
                        }
                    }
                }
            }
        }
    }
}
