package com.spellstorm.world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Chunk-streamed infinite procedural world grid for SpellStorm.
 *
 * <p>Implements {@link WorldGrid} and {@link FloatingOriginAware}:
 * <ul>
 *   <li>Coordinates multi-chunk traversability and collision resolution across chunk boundaries without seams.</li>
 *   <li>Strictly unbounded: supports infinite 4-quadrant exploration (negative & positive coordinates).</li>
 *   <li>Two-tier spatial partitioning for zero-allocation, high-frequency collision queries.</li>
 *   <li>Caches visible obstacles in world-local coordinates for rendering without per-frame heap allocations.</li>
 *   <li>Atomic floating-origin rebasing response.</li>
 * </ul>
 */
public class InfiniteWorld implements WorldGrid, FloatingOriginAware {

    public static final float MAX_OBSTACLE_RADIUS = 16.0f;

    private final ChunkManager chunkManager;
    private final FloatingOriginManager originManager;

    // Zero-allocation rendering cache for visible obstacles in world-local coordinates
    private final List<Obstacle> cachedVisibleObstacles = new ArrayList<>(256);
    private final List<Obstacle> unmodifiableVisibleObstacles = Collections.unmodifiableList(cachedVisibleObstacles);

    public InfiniteWorld(ChunkManager chunkManager) {
        this(chunkManager, null);
    }

    public InfiniteWorld(ChunkManager chunkManager, FloatingOriginManager originManager) {
        this.chunkManager = Objects.requireNonNull(chunkManager, "ChunkManager cannot be null");
        this.originManager = originManager;
        if (originManager != null) {
            originManager.registerListener(this);
        }
    }

    public ChunkManager getChunkManager() {
        return chunkManager;
    }

    public FloatingOriginManager getOriginManager() {
        return originManager;
    }

    // =========================================================================
    // FloatingOriginAware Implementation
    // =========================================================================

    @Override
    public void onOriginShift(long deltaX, long deltaY) {
        cachedVisibleObstacles.clear();
    }

    // =========================================================================
    // WorldGrid Grid Queries
    // =========================================================================

    @Override
    public TileType getTile(int tx, int ty) {
        int cx = Math.floorDiv(tx, Chunk.CHUNK_TILES);
        int cy = Math.floorDiv(ty, Chunk.CHUNK_TILES);
        int lx = Math.floorMod(tx, Chunk.CHUNK_TILES);
        int ly = Math.floorMod(ty, Chunk.CHUNK_TILES);
        Chunk chunk = chunkManager.getChunk(cx, cy);
        return chunk.getLocalTile(lx, ly);
    }

    @Override
    public boolean isSolidTile(int tx, int ty) {
        return getTile(tx, ty).isSolid();
    }

    @Override
    public boolean isWaterTile(int tx, int ty) {
        return getTile(tx, ty) == TileType.WATER;
    }

    // =========================================================================
    // Traversability & Sliding Collision (Seam-Free Multi-Chunk Resolution)
    // =========================================================================

    @Override
    public boolean isTraversable(float worldX, float worldY, float radius) {
        if (radius < 0.0f) {
            return false;
        }

        long originX = chunkManager.getOriginShiftX();
        long originY = chunkManager.getOriginShiftY();

        // 1. Circle-AABB test against solid tiles across touched chunks
        long gMinX = (long) Math.floor(worldX - radius) + originX;
        long gMaxX = (long) Math.floor(worldX + radius) + originX;
        long gMinY = (long) Math.floor(worldY - radius) + originY;
        long gMaxY = (long) Math.floor(worldY + radius) + originY;

        int minTx = (int) Math.floorDiv(gMinX, (long) TileMap.TILE_SIZE);
        int maxTx = (int) Math.floorDiv(gMaxX, (long) TileMap.TILE_SIZE);
        int minTy = (int) Math.floorDiv(gMinY, (long) TileMap.TILE_SIZE);
        int maxTy = (int) Math.floorDiv(gMaxY, (long) TileMap.TILE_SIZE);

        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                int cx = Math.floorDiv(tx, Chunk.CHUNK_TILES);
                int cy = Math.floorDiv(ty, Chunk.CHUNK_TILES);
                int lx = Math.floorMod(tx, Chunk.CHUNK_TILES);
                int ly = Math.floorMod(ty, Chunk.CHUNK_TILES);
                Chunk chunk = chunkManager.getChunk(cx, cy);
                if (chunk.getLocalTile(lx, ly).isSolid()) {
                    float tileWorldLeft = (float) (((long) tx * TileMap.TILE_SIZE) - originX);
                    float tileWorldTop = (float) (((long) ty * TileMap.TILE_SIZE) - originY);
                    if (CollisionUtils.checkCircleVsAABB(worldX, worldY, radius,
                            tileWorldLeft, tileWorldTop, TileMap.TILE_SIZE, TileMap.TILE_SIZE)) {
                        return false;
                    }
                }
            }
        }

        // 2. Circle-circle test against obstacles across touched chunks
        // Expanded by MAX_OBSTACLE_RADIUS to evaluate obstacles crossing chunk seams
        long obsGMinX = (long) Math.floor(worldX - radius - MAX_OBSTACLE_RADIUS) + originX;
        long obsGMaxX = (long) Math.floor(worldX + radius + MAX_OBSTACLE_RADIUS) + originX;
        long obsGMinY = (long) Math.floor(worldY - radius - MAX_OBSTACLE_RADIUS) + originY;
        long obsGMaxY = (long) Math.floor(worldY + radius + MAX_OBSTACLE_RADIUS) + originY;

        int minCx = (int) Math.floorDiv(obsGMinX, (long) Chunk.CHUNK_SIZE_PX);
        int maxCx = (int) Math.floorDiv(obsGMaxX, (long) Chunk.CHUNK_SIZE_PX);
        int minCy = (int) Math.floorDiv(obsGMinY, (long) Chunk.CHUNK_SIZE_PX);
        int maxCy = (int) Math.floorDiv(obsGMaxY, (long) Chunk.CHUNK_SIZE_PX);

        for (int cy = minCy; cy <= maxCy; cy++) {
            long chunkOriginY = ((long) cy * (long) Chunk.CHUNK_SIZE_PX) - originY;
            float localY = worldY - (float) chunkOriginY;

            for (int cx = minCx; cx <= maxCx; cx++) {
                long chunkOriginX = ((long) cx * (long) Chunk.CHUNK_SIZE_PX) - originX;
                float localX = worldX - (float) chunkOriginX;

                Chunk chunk = chunkManager.getChunk(cx, cy);
                if (chunk.collidesWithLocalObstacle(localX, localY, radius)) {
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

    // =========================================================================
    // Coordinate Conversions
    // =========================================================================

    @Override
    public int worldToTileX(float worldX) {
        long gx = (long) Math.floor(worldX) + chunkManager.getOriginShiftX();
        return (int) Math.floorDiv(gx, (long) TileMap.TILE_SIZE);
    }

    @Override
    public int worldToTileY(float worldY) {
        long gy = (long) Math.floor(worldY) + chunkManager.getOriginShiftY();
        return (int) Math.floorDiv(gy, (long) TileMap.TILE_SIZE);
    }

    @Override
    public float tileToWorldX(int tx) {
        return (float) (((long) tx * TileMap.TILE_SIZE) - chunkManager.getOriginShiftX());
    }

    @Override
    public float tileToWorldY(int ty) {
        return (float) (((long) ty * TileMap.TILE_SIZE) - chunkManager.getOriginShiftY());
    }

    @Override
    public Vector2D tileToWorld(int tx, int ty) {
        return new Vector2D(tileToWorldX(tx), tileToWorldY(ty));
    }

    @Override
    public Rectangle getWorldBounds() {
        return null; // Unbounded infinite world
    }

    @Override
    public boolean isBounded() {
        return false;
    }

    // =========================================================================
    // Rendering & Visible Obstacle Caching (Zero GC Allocations)
    // =========================================================================

    @Override
    public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera, float elapsed) {
        if (batch == null || textureManager == null) {
            return;
        }
        List<Chunk> visibleChunks = chunkManager.getVisibleChunks(camera);
        long originX = chunkManager.getOriginShiftX();
        long originY = chunkManager.getOriginShiftY();

        for (int i = 0; i < visibleChunks.size(); i++) {
            Chunk chunk = visibleChunks.get(i);
            float drawX = (float) (((long) chunk.getChunkX() * (long) Chunk.CHUNK_SIZE_PX) - originX);
            float drawY = (float) (((long) chunk.getChunkY() * (long) Chunk.CHUNK_SIZE_PX) - originY);
            chunk.draw(batch, textureManager, drawX, drawY, elapsed);
        }
    }

    @Override
    public List<Obstacle> getVisibleObstacles(GameCamera camera) {
        cachedVisibleObstacles.clear();

        List<Chunk> chunks = (camera != null)
                ? chunkManager.getVisibleChunks(camera)
                : chunkManager.getActiveSimulationChunks();

        long originX = chunkManager.getOriginShiftX();
        long originY = chunkManager.getOriginShiftY();

        for (int i = 0; i < chunks.size(); i++) {
            Chunk chunk = chunks.get(i);
            List<Obstacle> worldObstacles = chunk.getWorldObstacles(originX, originY);
            for (int j = 0; j < worldObstacles.size(); j++) {
                Obstacle obs = worldObstacles.get(j);
                if (camera == null || camera.isVisible(obs.getVisualBounds())) {
                    cachedVisibleObstacles.add(obs);
                }
            }
        }

        return unmodifiableVisibleObstacles;
    }

    public void clearCache() {
        cachedVisibleObstacles.clear();
    }
}
