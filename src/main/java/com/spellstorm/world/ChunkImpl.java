package com.spellstorm.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.CompleteSheetId;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * High-performance spatial chunk implementation representing a 32x32 tile unit (1024x1024 px).
 *
 * <p>Key architectural properties:
 * <ul>
 *   <li><b>Flat 1024-byte array</b>: O(1) cache-optimal tile access with minimal memory overhead (~1.25 KB total instance footprint).</li>
 *   <li><b>Chunk-local coordinate frame</b>: Terrain and natural obstacles are stored in chunk-local space [0, 1024) px, rendering chunks invariant under floating-origin rebasing.</li>
 *   <li><b>Continuous traversability</b>: Circle-AABB tests against solid tiles and circle-circle tests against solid obstacles matching {@link TileMap#isTraversable}.</li>
 *   <li><b>Sparse delta integration</b>: In-place tile mutation, reverse-order obstacle suppression, and persistent pickup restoration from {@link ChunkDelta}.</li>
 *   <li><b>Headless-safe rendering</b>: Safely no-ops when {@code Gdx.gl == null || batch == null}, matching LibGDX headless testing requirements.</li>
 * </ul>
 */
public class ChunkImpl implements Chunk {

    private static final float WATER_FRAME_DURATION = 0.16f;

    private final int chunkX;
    private final int chunkY;
    private final long chunkKey;
    private final byte[] tiles;
    private final List<Obstacle> obstacles;
    private final List<ChunkDelta.SavedPickup> persistentPickups;
    private final List<ChunkDelta.SavedEntityState> persistentEntities;

    private List<Obstacle> cachedWorldObstacles;
    private long cachedOriginX = Long.MIN_VALUE;
    private long cachedOriginY = Long.MIN_VALUE;

    /**
     * Constructs a chunk initialized with all {@link TileType#GRASS} tiles.
     */
    public ChunkImpl(int chunkX, int chunkY) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkKey = ChunkKey.of(chunkX, chunkY);
        this.tiles = new byte[TILE_COUNT];
        // TileType.GRASS.getId() == 0, default byte[] initialization is already 0
        this.obstacles = new ArrayList<>(8);
        this.persistentPickups = new ArrayList<>(2);
        this.persistentEntities = new ArrayList<>(1);
    }

    /**
     * Constructs a chunk with initial tile data and obstacles.
     */
    public ChunkImpl(int chunkX, int chunkY, byte[] initialTiles, List<Obstacle> initialObstacles) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkKey = ChunkKey.of(chunkX, chunkY);
        this.tiles = new byte[TILE_COUNT];
        if (initialTiles != null) {
            System.arraycopy(initialTiles, 0, this.tiles, 0, Math.min(initialTiles.length, TILE_COUNT));
        }
        this.obstacles = initialObstacles != null ? new ArrayList<>(initialObstacles) : new ArrayList<>(8);
        this.persistentPickups = new ArrayList<>(2);
        this.persistentEntities = new ArrayList<>(1);
    }

    @Override
    public int getChunkX() {
        return chunkX;
    }

    @Override
    public int getChunkY() {
        return chunkY;
    }

    @Override
    public long getChunkKey() {
        return chunkKey;
    }

    @Override
    public TileType getLocalTile(int lx, int ly) {
        if (lx < 0 || lx >= CHUNK_TILES || ly < 0 || ly >= CHUNK_TILES) {
            return TileType.WATER; // Boundary containment fallback matching TileMap.tileAt
        }
        return TileType.fromId(tiles[(ly << 5) | lx] & 0xFF);
    }

    @Override
    public void setLocalTile(int lx, int ly, TileType type) {
        if (lx >= 0 && lx < CHUNK_TILES && ly >= 0 && ly < CHUNK_TILES) {
            tiles[(ly << 5) | lx] = (byte) (type != null ? type.getId() : TileType.GRASS.getId());
        }
    }

    @Override
    public byte[] getTileData() {
        return tiles;
    }

    @Override
    public List<Obstacle> getObstacles() {
        return Collections.unmodifiableList(obstacles);
    }

    @Override
    public List<Obstacle> getWorldObstacles(long originShiftX, long originShiftY) {
        if (cachedWorldObstacles != null && cachedOriginX == originShiftX && cachedOriginY == originShiftY) {
            return cachedWorldObstacles;
        }
        cachedOriginX = originShiftX;
        cachedOriginY = originShiftY;
        float chunkOriginX = (float) (((long) chunkX * (long) CHUNK_SIZE_PX) - originShiftX);
        float chunkOriginY = (float) (((long) chunkY * (long) CHUNK_SIZE_PX) - originShiftY);
        List<Obstacle> list = new ArrayList<>(obstacles.size());
        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle o = obstacles.get(i);
            list.add(new Obstacle(o.getType(), chunkOriginX + o.getPosition().x, chunkOriginY + o.getPosition().y, o.isSolid()));
        }
        this.cachedWorldObstacles = Collections.unmodifiableList(list);
        return this.cachedWorldObstacles;
    }

    @Override
    public int getObstacleCount() {
        return obstacles.size();
    }

    @Override
    public Obstacle getObstacle(int index) {
        return obstacles.get(index);
    }

    @Override
    public void addObstacle(Obstacle obstacle) {
        if (obstacle != null) {
            obstacles.add(obstacle);
            cachedWorldObstacles = null;
        }
    }

    @Override
    public boolean collidesWithLocalObstacle(float localX, float localY, float radius) {
        if (radius < 0.0f) {
            return false;
        }

        for (int i = 0; i < obstacles.size(); i++) {
            Obstacle obs = obstacles.get(i);
            if (!obs.isSolid() || obs.getCollisionRadius() <= 0.0f) {
                continue;
            }
            Vector2D colCenter = obs.getCollisionCenter();
            float obsX = colCenter.x;
            float obsY = colCenter.y;

            float totalRadius = radius + obs.getCollisionRadius();
            float dx = localX - obsX;
            float dy = localY - obsY;

            // Strict inequality matching TileMap.java and C++ reference TileMap.cpp:176
            if ((dx * dx + dy * dy) < (totalRadius * totalRadius)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Evaluates whether an entity circle of given radius can legally occupy (localX, localY)
     * within this chunk's local spatial coordinate frame.
     *
     * <p>Two-phase validation:
     * <ol>
     *   <li>Circle-AABB test against local solid tiles (e.g. {@link TileType#WATER}).</li>
     *   <li>Circle-circle test against local solid environmental obstacles.</li>
     * </ol>
     */
    @Override
    public boolean isTraversableLocal(float localX, float localY, float radius) {
        if (radius < 0.0f) {
            return false;
        }

        // 1. Circle-AABB test against overlapping local tiles
        int minLx = Math.max(0, (int) Math.floor((localX - radius) / TileMap.TILE_SIZE));
        int maxLx = Math.min(CHUNK_TILES - 1, (int) Math.floor((localX + radius) / TileMap.TILE_SIZE));
        int minLy = Math.max(0, (int) Math.floor((localY - radius) / TileMap.TILE_SIZE));
        int maxLy = Math.min(CHUNK_TILES - 1, (int) Math.floor((localY + radius) / TileMap.TILE_SIZE));

        for (int ly = minLy; ly <= maxLy; ly++) {
            int rowOffset = ly << 5;
            for (int lx = minLx; lx <= maxLx; lx++) {
                TileType tile = TileType.fromId(tiles[rowOffset | lx] & 0xFF);
                if (tile.isSolid()) {
                    float tileLeft = lx * TileMap.TILE_SIZE;
                    float tileTop = ly * TileMap.TILE_SIZE;
                    if (CollisionUtils.checkCircleVsAABB(localX, localY, radius, tileLeft, tileTop,
                            TileMap.TILE_SIZE, TileMap.TILE_SIZE)) {
                        return false;
                    }
                }
            }
        }

        // 2. Circle-circle test against local solid obstacles
        return !collidesWithLocalObstacle(localX, localY, radius);
    }

    /**
     * Applies sparse mutable player modifications from {@link ChunkDelta}:
     * in-place tile changes, obstacle suppressions, and persistent pickups/entities.
     */
    @Override
    public void applyDelta(ChunkDelta delta) {
        if (delta == null || delta.isEmpty()) {
            return;
        }

        // Apply tile modifications
        for (Map.Entry<Short, Byte> entry : delta.getTileModifications().entrySet()) {
            int idx = entry.getKey() & 0xFFFF;
            if (idx >= 0 && idx < TILE_COUNT) {
                tiles[idx] = entry.getValue();
            }
        }

        // Suppress removed obstacles in descending index order to prevent index shifting
        boolean anyRemoved = false;
        for (int i = obstacles.size() - 1; i >= 0; i--) {
            if (delta.isObstacleRemoved(i)) {
                obstacles.remove(i);
                anyRemoved = true;
            }
        }
        if (anyRemoved) {
            cachedWorldObstacles = null;
        }

        // Restore persistent pickups and entities
        if (!delta.getPersistentPickups().isEmpty()) {
            persistentPickups.clear();
            persistentPickups.addAll(delta.getPersistentPickups());
        }
        if (!delta.getPersistentEntities().isEmpty()) {
            persistentEntities.clear();
            persistentEntities.addAll(delta.getPersistentEntities());
        }
    }

    public List<ChunkDelta.SavedPickup> getPersistentPickups() {
        return Collections.unmodifiableList(persistentPickups);
    }

    public List<ChunkDelta.SavedEntityState> getPersistentEntities() {
        return Collections.unmodifiableList(persistentEntities);
    }

    /**
     * Renders ground terrain tiles at the specified world/screen draw origin.
     * Safely no-ops in headless test environments.
     */
    @Override
    public void draw(SpriteBatch batch, TextureManager textureManager,
                     float worldOriginX, float worldOriginY, float elapsed) {
        if (batch == null || textureManager == null || Gdx.gl == null) {
            return;
        }

        boolean hasWaterAnim = textureManager.hasExtendedSheet(CompleteSheetId.WATER_ANIM);
        boolean hasVariants = textureManager.hasExtendedSheet(CompleteSheetId.TERRAIN_VARIANTS);

        float safeElapsed = Math.max(0.0f, elapsed);
        int waterFrame = ((int) (safeElapsed / WATER_FRAME_DURATION)) % 4;

        for (int ly = 0; ly < CHUNK_TILES; ly++) {
            int rowOffset = ly << 5;
            float drawY = worldOriginY + ly * TileMap.TILE_SIZE;

            for (int lx = 0; lx < CHUNK_TILES; lx++) {
                TileType type = TileType.fromId(tiles[rowOffset | lx] & 0xFF);
                TextureRegion baseRegion = null;

                if (type == TileType.WATER && hasWaterAnim) {
                    baseRegion = textureManager.getExtendedFrame(CompleteSheetId.WATER_ANIM, waterFrame);
                } else if (hasVariants) {
                    // Global tile coordinates ensure continuous deterministic variants across chunk boundaries
                    long gx = ChunkKey.chunkLocalToGlobalTileX(chunkX, lx);
                    long gy = ChunkKey.chunkLocalToGlobalTileY(chunkY, ly);
                    int variantIdx = TileMap.selectTerrainVariantFrame(type, (int) gx, (int) gy);
                    if (variantIdx >= 0) {
                        baseRegion = textureManager.getExtendedFrame(CompleteSheetId.TERRAIN_VARIANTS, variantIdx);
                    }
                }
                if (baseRegion == null) {
                    baseRegion = textureManager.getRegion(type.getTextureId());
                }

                float drawX = worldOriginX + lx * TileMap.TILE_SIZE;
                if (baseRegion != null && baseRegion.getTexture() != null) {
                    drawRegionYDown(batch, baseRegion, drawX, drawY, TileMap.TILE_SIZE, TileMap.TILE_SIZE);
                }
            }
        }
    }

    private static void drawRegionYDown(SpriteBatch batch, TextureRegion region,
                                        float x, float y, float w, float h) {
        batch.draw(region.getTexture(), x, y, w, h,
                region.getRegionX(), region.getRegionY(), region.getRegionWidth(), region.getRegionHeight(),
                false, true);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChunkImpl chunk = (ChunkImpl) o;
        return chunkKey == chunk.chunkKey;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(chunkKey);
    }

    @Override
    public String toString() {
        return "ChunkImpl{" +
                "coords=(" + chunkX + ", " + chunkY + ")" +
                ", key=" + chunkKey +
                ", obstacles=" + obstacles.size() +
                '}';
    }
}
