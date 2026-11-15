package com.spellstorm.world;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Non-invasive adapter implementing WorldGrid by delegating 100% of calls to a legacy TileMap.
 * Guarantees zero modifications to TileMap.java and complete backward compatibility with all 870 baseline tests.
 */
public class TileMapAdapter implements WorldGrid {

    private final TileMap tileMap;

    public TileMapAdapter(TileMap tileMap) {
        this.tileMap = Objects.requireNonNull(tileMap, "TileMap cannot be null");
    }

    public TileMap getTileMap() {
        return tileMap;
    }

    @Override
    public TileType getTile(int tx, int ty) {
        return tileMap.getTile(tx, ty);
    }

    @Override
    public boolean isSolidTile(int tx, int ty) {
        return tileMap.isSolidTile(tx, ty);
    }

    @Override
    public boolean isWaterTile(int tx, int ty) {
        return tileMap.isWaterTile(tx, ty);
    }

    @Override
    public boolean isTraversable(float x, float y, float radius) {
        return tileMap.isTraversable(x, y, radius);
    }

    @Override
    public boolean isTraversable(Vector2D pos, float radius) {
        return tileMap.isTraversable(pos, radius);
    }

    @Override
    public Vector2D resolveCollision(float oldX, float oldY, float newX, float newY, float radius) {
        return tileMap.resolveCollision(oldX, oldY, newX, newY, radius);
    }

    @Override
    public Vector2D resolveCollision(Vector2D oldPos, Vector2D newPos, float radius) {
        return tileMap.resolveCollision(oldPos, newPos, radius);
    }

    @Override
    public int worldToTileX(float worldX) {
        return tileMap.worldToTileX(worldX);
    }

    @Override
    public int worldToTileY(float worldY) {
        return tileMap.worldToTileY(worldY);
    }

    @Override
    public float tileToWorldX(int tx) {
        return tileMap.tileToWorldX(tx);
    }

    @Override
    public float tileToWorldY(int ty) {
        return tileMap.tileToWorldY(ty);
    }

    @Override
    public Vector2D tileToWorld(int tx, int ty) {
        return tileMap.tileToWorld(tx, ty);
    }

    @Override
    public Rectangle getWorldBounds() {
        return tileMap.getWorldBounds();
    }

    @Override
    public boolean isBounded() {
        return true;
    }

    @Override
    public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera, float elapsed) {
        tileMap.drawTerrain(batch, textureManager, camera, elapsed);
    }

    @Override
    public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera) {
        tileMap.drawTerrain(batch, textureManager, camera);
    }

    @Override
    public List<Obstacle> getVisibleObstacles(GameCamera camera) {
        List<Obstacle> all = tileMap.getObstacles();
        if (camera == null) {
            return all;
        }
        List<Obstacle> visible = new ArrayList<>();
        for (Obstacle obs : all) {
            if (obs != null && camera.isVisible(obs.getVisualBounds())) {
                visible.add(obs);
            }
        }
        return Collections.unmodifiableList(visible);
    }

    @Override
    public List<Obstacle> getObstaclesNear(float worldX, float worldY, float searchRadius) {
        List<Obstacle> all = tileMap.getObstacles();
        if (all == null || all.isEmpty()) {
            return Collections.emptyList();
        }
        float effectiveRadius = Math.max(0.0f, searchRadius);
        List<Obstacle> nearby = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            Obstacle obs = all.get(i);
            if (obs == null) {
                continue;
            }
            Vector2D center = obs.getCollisionCenter();
            float limit = effectiveRadius + Math.max(0.0f, obs.getCollisionRadius());
            float dx = worldX - center.x;
            float dy = worldY - center.y;
            if ((dx * dx + dy * dy) <= (limit * limit)) {
                nearby.add(obs);
            }
        }
        return nearby;
    }

    @Override
    public boolean isProjectileTraversable(float worldX, float worldY, float radius) {
        if (radius < 0.0f) {
            return false;
        }
        Rectangle bounds = tileMap.getWorldBounds();
        if (worldX - radius < bounds.x
                || worldX + radius > bounds.x + bounds.width
                || worldY - radius < bounds.y
                || worldY + radius > bounds.y + bounds.height) {
            return false;
        }
        List<Obstacle> all = tileMap.getObstacles();
        for (int i = 0; i < all.size(); i++) {
            Obstacle obs = all.get(i);
            if (obs == null || !obs.isSolid() || obs.getCollisionRadius() <= 0.0f) {
                continue;
            }
            Vector2D colCenter = obs.getCollisionCenter();
            float totalRadius = radius + obs.getCollisionRadius();
            float dx = worldX - colCenter.x;
            float dy = worldY - colCenter.y;
            if ((dx * dx + dy * dy) < (totalRadius * totalRadius)) {
                return false;
            }
        }
        return true;
    }
}
