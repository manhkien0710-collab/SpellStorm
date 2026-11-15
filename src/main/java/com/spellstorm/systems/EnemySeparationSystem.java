package com.spellstorm.systems;

import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.entities.Enemy;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileMapAdapter;
import com.spellstorm.world.WorldGrid;

import java.util.ArrayList;
import java.util.List;

/**
 * Separates overlapping living enemies so crowds fan out naturally instead of collapsing
 * onto a single point. Resolves exact coincident positions deterministically and respects
 * both arena {@code worldBounds} and {@link WorldGrid#isTraversable(float, float, float)}.
 */
public class EnemySeparationSystem {

    private static final int RELAXATION_PASSES = 2;
    private static final float COINCIDENT_EPSILON_SQ = 1e-6f;
    private static final int TIE_BREAK_DIRECTIONS = 16;

    private TileMap cachedTileMap = null;
    private WorldGrid cachedAdapter = null;

    public EnemySeparationSystem() {
    }

    /**
     * Separates overlapping living enemies using the tile map's world bounds (if present).
     */
    public void separate(List<Enemy> enemies, TileMap tileMap, float dt) {
        Rectangle bounds = (tileMap != null) ? tileMap.getWorldBounds() : null;
        separate(enemies, tileMap, bounds, dt);
    }

    /**
     * Separates overlapping living enemies while constraining positions inside {@code worldBounds}
     * and keeping positions traversable on {@code tileMap}.
     */
    public void separate(List<Enemy> enemies, TileMap tileMap, Rectangle worldBounds, float dt) {
        separateWithGrid(enemies, resolveWorldGrid(tileMap), worldBounds, dt);
    }

    /**
     * Separates overlapping living enemies using the given WorldGrid's bounds (if bounded).
     */
    public void separateWithGrid(List<Enemy> enemies, WorldGrid worldGrid, float dt) {
        Rectangle bounds = (worldGrid != null && worldGrid.isBounded()) ? worldGrid.getWorldBounds() : null;
        separateWithGrid(enemies, worldGrid, bounds, dt);
    }

    /**
     * Separates overlapping living enemies while constraining positions inside {@code worldBounds}
     * and resolving displacements against water and solid obstacles on {@code worldGrid}.
     */
    public void separateWithGrid(List<Enemy> enemies, WorldGrid worldGrid, Rectangle worldBounds, float dt) {
        if (enemies == null || enemies.size() < 2 || dt <= 0.0f) {
            return;
        }

        List<Enemy> living = new ArrayList<>(enemies.size());
        for (Enemy enemy : enemies) {
            if (enemy != null && enemy.isAlive()) {
                living.add(enemy);
            }
        }

        int n = living.size();
        if (n < 2) {
            return;
        }

        float factor = MathUtils.clamp(dt * 20.0f, 0.35f, 1.0f);
        float[] dispX = new float[n];
        float[] dispY = new float[n];

        for (int pass = 0; pass < RELAXATION_PASSES; pass++) {
            for (int i = 0; i < n; i++) {
                dispX[i] = 0.0f;
                dispY[i] = 0.0f;
            }

            boolean anyOverlap = false;
            for (int i = 0; i < n; i++) {
                Enemy e1 = living.get(i);
                Vector2D p1 = e1.getPosition();
                float r1 = e1.getRadius();

                for (int j = i + 1; j < n; j++) {
                    Enemy e2 = living.get(j);
                    Vector2D p2 = e2.getPosition();
                    float minDist = r1 + e2.getRadius();
                    if (minDist <= 0.0f) {
                        continue;
                    }

                    float dx = p1.x - p2.x;
                    float dy = p1.y - p2.y;
                    float distSq = dx * dx + dy * dy;
                    if (distSq >= minDist * minDist) {
                        continue;
                    }

                    anyOverlap = true;
                    float nx;
                    float ny;
                    float overlap;

                    if (distSq < COINCIDENT_EPSILON_SQ) {
                        // Deterministic 2D tie-breaker for exact coincident centers
                        long hash = e1.getId() * 31L + e2.getId() * 17L + i * 7L + j;
                        int slot = (int) Math.floorMod(hash, TIE_BREAK_DIRECTIONS);
                        float angle = (float) (slot * (Math.PI * 2.0 / TIE_BREAK_DIRECTIONS));
                        nx = (float) Math.cos(angle);
                        ny = (float) Math.sin(angle);
                        overlap = minDist;
                    } else {
                        float dist = (float) Math.sqrt(distSq);
                        nx = dx / dist;
                        ny = dy / dist;
                        overlap = minDist - dist;
                    }

                    float push = overlap * 0.5f * factor;
                    dispX[i] += nx * push;
                    dispY[i] += ny * push;
                    dispX[j] -= nx * push;
                    dispY[j] -= ny * push;
                }
            }

            if (!anyOverlap) {
                break;
            }

            for (int i = 0; i < n; i++) {
                if (dispX[i] == 0.0f && dispY[i] == 0.0f) {
                    continue;
                }
                applyValidatedDisplacement(living.get(i), dispX[i], dispY[i], worldGrid, worldBounds);
            }
        }
    }

    private WorldGrid resolveWorldGrid(TileMap tileMap) {
        if (tileMap == null) {
            return null;
        }
        if (cachedTileMap != tileMap) {
            cachedTileMap = tileMap;
            cachedAdapter = new TileMapAdapter(tileMap);
        }
        return cachedAdapter;
    }

    private void applyValidatedDisplacement(Enemy enemy, float dx, float dy,
                                            WorldGrid worldGrid, Rectangle worldBounds) {
        Vector2D pos = enemy.getPosition();
        float oldX = pos.x;
        float oldY = pos.y;
        float candX = oldX + dx;
        float candY = oldY + dy;
        float r = enemy.getRadius();

        if (worldBounds != null) {
            candX = clampToBoundsX(candX, r, worldBounds);
            candY = clampToBoundsY(candY, r, worldBounds);
        }

        if (worldGrid == null) {
            enemy.setPosition(candX, candY);
            return;
        }

        Vector2D resolved = worldGrid.resolveCollision(oldX, oldY, candX, candY, r);
        float rx = resolved.x;
        float ry = resolved.y;

        if (worldBounds != null) {
            rx = clampToBoundsX(rx, r, worldBounds);
            ry = clampToBoundsY(ry, r, worldBounds);
        }

        if (worldGrid.isTraversable(rx, ry, r)) {
            enemy.setPosition(rx, ry);
        } else if (worldGrid.isTraversable(oldX, oldY, r)) {
            if (worldBounds != null) {
                float ox = clampToBoundsX(oldX, r, worldBounds);
                float oy = clampToBoundsY(oldY, r, worldBounds);
                if (worldGrid.isTraversable(ox, oy, r)) {
                    enemy.setPosition(ox, oy);
                }
            }
        } else if (worldBounds != null) {
            enemy.setPosition(clampToBoundsX(oldX, r, worldBounds), clampToBoundsY(oldY, r, worldBounds));
        }
    }

    private static float clampToBoundsX(float x, float radius, Rectangle bounds) {
        float minX = bounds.x + radius;
        float maxX = bounds.x + bounds.width - radius;
        if (maxX < minX) {
            return bounds.x + bounds.width * 0.5f;
        }
        return MathUtils.clamp(x, minX, maxX);
    }

    private static float clampToBoundsY(float y, float radius, Rectangle bounds) {
        float minY = bounds.y + radius;
        float maxY = bounds.y + bounds.height - radius;
        if (maxY < minY) {
            return bounds.y + bounds.height * 0.5f;
        }
        return MathUtils.clamp(y, minY, maxY);
    }
}
