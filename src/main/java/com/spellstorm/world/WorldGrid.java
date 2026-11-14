package com.spellstorm.world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;
import java.util.List;

/**
 * Universal spatial representation and collision contract for SpellStorm.
 *
 * Implemented by:
 * - {@link TileMapAdapter}: Non-invasive backward-compatible wrapper for the legacy 128x128 TileMap.
 * - {@code InfiniteWorld}: Chunk-streamed infinite procedural world grid.
 */
public interface WorldGrid {

    TileType getTile(int tx, int ty);

    boolean isSolidTile(int tx, int ty);

    boolean isWaterTile(int tx, int ty);

    boolean isTraversable(float x, float y, float radius);

    default boolean isTraversable(Vector2D pos, float radius) {
        if (pos == null) return false;
        return isTraversable(pos.x, pos.y, radius);
    }

    Vector2D resolveCollision(float oldX, float oldY, float newX, float newY, float radius);

    Vector2D resolveCollision(Vector2D oldPos, Vector2D newPos, float radius);

    int worldToTileX(float worldX);

    int worldToTileY(float worldY);

    default int[] worldToTile(float worldX, float worldY) {
        return new int[]{worldToTileX(worldX), worldToTileY(worldY)};
    }

    default int[] worldToTile(Vector2D pos) {
        if (pos == null) return new int[]{0, 0};
        return worldToTile(pos.x, pos.y);
    }

    float tileToWorldX(int tx);

    float tileToWorldY(int ty);

    Vector2D tileToWorld(int tx, int ty);

    /**
     * Returns the physical bounding box for bounded maps, or null for unbounded infinite worlds.
     */
    Rectangle getWorldBounds();

    /**
     * Indicates whether the world has fixed boundaries.
     */
    default boolean isBounded() {
        return getWorldBounds() != null;
    }

    void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera, float elapsed);

    default void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera) {
        float elapsedSeconds = (System.currentTimeMillis() % 1_000_000L) * 0.001f;
        drawTerrain(batch, textureManager, camera, elapsedSeconds);
    }

    /**
     * Returns obstacles currently within or intersecting the camera frustum.
     * For bounded maps without camera, returns all placed obstacles.
     */
    List<Obstacle> getVisibleObstacles(GameCamera camera);
}
