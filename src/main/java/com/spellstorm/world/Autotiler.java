package com.spellstorm.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.CompleteSheetId;

import java.util.Objects;

/**
 * Procedural autotiler and transition bitmask evaluator for SpellStorm.
 *
 * <p>Supports:
 * <ul>
 *   <li>Grass overhang bitmasks onto dirt, stone, and sand substrates.</li>
 *   <li>Shoreline transition fringe bitmasks onto water tiles, excluding bridges.</li>
 *   <li>Seamless cross-chunk autotiling using procedural fallback ({@link TerrainGenerator#sampleTileType})
 *       when adjacent neighbor chunks are unloaded.</li>
 *   <li>Deterministic terrain visual variants matching {@link TileMap#selectTerrainVariantFrame}.</li>
 * </ul>
 */
public class Autotiler {

    public static final int DIR_N = 1;
    public static final int DIR_E = 1 << 1;
    public static final int DIR_S = 1 << 2;
    public static final int DIR_W = 1 << 3;

    private final TerrainGenerator terrainGenerator;

    public Autotiler(TerrainGenerator terrainGenerator) {
        this.terrainGenerator = Objects.requireNonNull(terrainGenerator, "TerrainGenerator cannot be null");
    }

    public TerrainGenerator getTerrainGenerator() {
        return terrainGenerator;
    }

    /**
     * Resolves tile at global coordinates (gx, gy) using procedural fallback
     * if the chunk is unloaded or world is null.
     */
    public TileType getTile(int gx, int gy, WorldGrid world) {
        if (world != null) {
            try {
                TileType t = world.getTile(gx, gy);
                if (t != null) return t;
            } catch (Exception ignored) {
                // Fall back procedurally
            }
        }
        return terrainGenerator.sampleTileType(gx, gy);
    }

    public int getGrassOverhangMask(TileType center, TileType n, TileType e, TileType s, TileType w) {
        if (center != TileType.DIRT && center != TileType.STONE && center != TileType.SAND) {
            return 0;
        }
        int mask = 0;
        if (n == TileType.GRASS) mask |= DIR_N;
        if (e == TileType.GRASS) mask |= DIR_E;
        if (s == TileType.GRASS) mask |= DIR_S;
        if (w == TileType.GRASS) mask |= DIR_W;
        return mask;
    }

    public int getShoreTransitionMask(TileType center, TileType n, TileType e, TileType s, TileType w) {
        if (center != TileType.WATER) {
            return 0;
        }
        int mask = 0;
        if (isShoreBorder(n)) mask |= DIR_N;
        if (isShoreBorder(e)) mask |= DIR_E;
        if (isShoreBorder(s)) mask |= DIR_S;
        if (isShoreBorder(w)) mask |= DIR_W;
        return mask;
    }

    private static boolean isShoreBorder(TileType t) {
        return t != null && !t.isWater() && !t.isBridge();
    }

    public int getGrassOverhangMask(Chunk chunk, int lx, int ly, WorldGrid world) {
        if (chunk == null) return 0;
        TileType center = chunk.getLocalTile(lx, ly);
        if (center != TileType.DIRT && center != TileType.STONE && center != TileType.SAND) {
            return 0;
        }
        int gx = (int) ChunkKey.chunkLocalToGlobalTileX(chunk.getChunkX(), lx);
        int gy = (int) ChunkKey.chunkLocalToGlobalTileY(chunk.getChunkY(), ly);

        TileType n = (ly > 0) ? chunk.getLocalTile(lx, ly - 1) : getTile(gx, gy - 1, world);
        TileType e = (lx < 31) ? chunk.getLocalTile(lx + 1, ly) : getTile(gx + 1, gy, world);
        TileType s = (ly < 31) ? chunk.getLocalTile(lx, ly + 1) : getTile(gx, gy + 1, world);
        TileType w = (lx > 0) ? chunk.getLocalTile(lx - 1, ly) : getTile(gx - 1, gy, world);

        return getGrassOverhangMask(center, n, e, s, w);
    }

    public int getShoreTransitionMask(Chunk chunk, int lx, int ly, WorldGrid world) {
        if (chunk == null) return 0;
        TileType center = chunk.getLocalTile(lx, ly);
        if (center != TileType.WATER) {
            return 0;
        }
        int gx = (int) ChunkKey.chunkLocalToGlobalTileX(chunk.getChunkX(), lx);
        int gy = (int) ChunkKey.chunkLocalToGlobalTileY(chunk.getChunkY(), ly);

        TileType n = (ly > 0) ? chunk.getLocalTile(lx, ly - 1) : getTile(gx, gy - 1, world);
        TileType e = (lx < 31) ? chunk.getLocalTile(lx + 1, ly) : getTile(gx + 1, gy, world);
        TileType s = (ly < 31) ? chunk.getLocalTile(lx, ly + 1) : getTile(gx, gy + 1, world);
        TileType w = (lx > 0) ? chunk.getLocalTile(lx - 1, ly) : getTile(gx - 1, gy, world);

        return getShoreTransitionMask(center, n, e, s, w);
    }

    public static int computeTileHash(int tx, int ty) {
        return TileMap.computeTileHash(tx, ty);
    }

    public static int selectTerrainVariantFrame(TileType type, int tx, int ty) {
        return TileMap.selectTerrainVariantFrame(type, tx, ty);
    }

    public void drawTileTransitions(SpriteBatch batch, TextureManager textureManager,
                                    int grassMask, int shoreMask, float drawX, float drawY) {
        if (batch == null || textureManager == null || Gdx.gl == null) return;
        if (grassMask != 0) {
            drawTransitionMask(batch, textureManager, grassMask, 0, drawX, drawY);
        }
        if (shoreMask != 0) {
            drawTransitionMask(batch, textureManager, shoreMask, 4, drawX, drawY);
        }
    }

    private static void drawTransitionMask(SpriteBatch batch, TextureManager textureManager,
                                           int mask, int baseOffset, float drawX, float drawY) {
        for (int dir = 0; dir < 4; dir++) {
            if ((mask & (1 << dir)) != 0) {
                TextureRegion overlay = textureManager.getExtendedFrame(
                        CompleteSheetId.TERRAIN_TRANSITIONS, baseOffset + dir);
                if (overlay != null && overlay.getTexture() != null) {
                    batch.draw(overlay.getTexture(), drawX, drawY, TileMap.TILE_SIZE, TileMap.TILE_SIZE,
                            overlay.getRegionX(), overlay.getRegionY(),
                            overlay.getRegionWidth(), overlay.getRegionHeight(),
                            false, true);
                }
            }
        }
    }
}
