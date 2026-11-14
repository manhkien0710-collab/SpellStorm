package com.spellstorm.world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.spellstorm.assets.TextureManager;
import java.util.List;

/**
 * Standard spatial chunk interface representing a 32x32 tile unit (1024x1024 px).
 *
 * <p>Chunks are deterministically generated from 64-bit coordinates and world seed,
 * with mutable player modifications captured in {@link ChunkDelta}.
 */
public interface Chunk {

    int CHUNK_TILES = 32;
    float CHUNK_SIZE_PX = CHUNK_TILES * TileMap.TILE_SIZE; // 1024.0f
    int TILE_COUNT = CHUNK_TILES * CHUNK_TILES; // 1024

    int getChunkX();

    int getChunkY();

    long getChunkKey();

    TileType getLocalTile(int lx, int ly);

    void setLocalTile(int lx, int ly, TileType type);

    byte[] getTileData();

    List<Obstacle> getObstacles();

    int getObstacleCount();

    Obstacle getObstacle(int index);

    void addObstacle(Obstacle obstacle);

    boolean isTraversableLocal(float localX, float localY, float radius);

    boolean collidesWithLocalObstacle(float localX, float localY, float radius);

    List<Obstacle> getWorldObstacles(long originShiftX, long originShiftY);

    void draw(SpriteBatch batch, TextureManager textureManager, float worldOriginX, float worldOriginY, float elapsed);

    void applyDelta(ChunkDelta delta);
}
