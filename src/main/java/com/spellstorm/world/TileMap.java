package com.spellstorm.world;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.TextureManager;
import com.spellstorm.assets.TextureManager.CompleteSheetId;
import com.spellstorm.assets.TextureManager.ExtendedSheetId;
import com.spellstorm.camera.GameCamera;
import com.spellstorm.math.CollisionUtils;
import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Spatial representation and collision grid for the SpellStorm fantasy world.
 *
 * Characteristics:
 * - 128x128 grid (4096x4096px at 32px per tile).
 * - 16,384-element contiguous 1D flat array for O(1) cache-optimal tile access.
 * - Out-of-bounds queries safely return TileType.WATER, ensuring boundary containment.
 * - Circle-AABB water collision and circle-circle obstacle collision with axis-aligned sliding resolution.
 * - Camera-based frustum culling with +/-2 tile expansion to eliminate black flashing artifacts.
 * - 100% headless safe: all geometric queries operate without an active OpenGL display.
 */
public class TileMap {

    public static final int WIDTH = 128;
    public static final int HEIGHT = 128;
    public static final float TILE_SIZE = 32.0f;
    public static final float WORLD_WIDTH = WIDTH * TILE_SIZE;   // 4096.0f
    public static final float WORLD_HEIGHT = HEIGHT * TILE_SIZE; // 4096.0f
    public static final Vector2D WORLD_CENTER = new Vector2D(WORLD_WIDTH * 0.5f, WORLD_HEIGHT * 0.5f); // (2048, 2048)

    private final TileType[] tiles = new TileType[WIDTH * HEIGHT];
    private final List<Obstacle> obstacles = new ArrayList<>();
    private final Rectangle worldBounds = new Rectangle(0.0f, 0.0f, WORLD_WIDTH, WORLD_HEIGHT);

    public TileMap() {
        this(42);
    }

    public TileMap(long seed) {
        Arrays.fill(tiles, TileType.GRASS);
        generate(seed);
    }

    /**
     * Procedurally populates the world grid and obstacles from a deterministic seed.
     */
    public void generate(long seed) {
        WorldGenerator.generate(this, seed);
    }

    // =========================================================================
    // Grid Dimensions & World Bounds
    // =========================================================================

    public int getWidth() {
        return WIDTH;
    }

    public int getHeight() {
        return HEIGHT;
    }

    public float getTileSize() {
        return TILE_SIZE;
    }

    public float getWorldWidth() {
        return WORLD_WIDTH;
    }

    public float getWorldHeight() {
        return WORLD_HEIGHT;
    }

    public Vector2D getWorldSize() {
        return new Vector2D(WORLD_WIDTH, WORLD_HEIGHT);
    }

    public Vector2D getCenter() {
        return new Vector2D(WORLD_CENTER.x, WORLD_CENTER.y);
    }

    public Rectangle getWorldBounds() {
        return worldBounds;
    }

    // =========================================================================
    // Grid & Tile Queries (Out-of-Bounds Void Fallback to WATER)
    // =========================================================================

    public boolean isInsideGrid(int tx, int ty) {
        return tx >= 0 && tx < WIDTH && ty >= 0 && ty < HEIGHT;
    }

    /**
     * Retrieves the tile at the given coordinates.
     * Any query outside the grid [0, 127] x [0, 127] returns TileType.WATER.
     */
    public TileType tileAt(int tx, int ty) {
        if (!isInsideGrid(tx, ty)) {
            return TileType.WATER;
        }
        return tiles[ty * WIDTH + tx];
    }

    public TileType getTile(int tx, int ty) {
        return tileAt(tx, ty);
    }

    public void setTile(int tx, int ty, TileType type) {
        if (isInsideGrid(tx, ty)) {
            tiles[ty * WIDTH + tx] = (type != null) ? type : TileType.GRASS;
        }
    }

    public boolean isSolidTile(int tx, int ty) {
        if (!isInsideGrid(tx, ty)) {
            return true;
        }
        return tileAt(tx, ty) == TileType.WATER;
    }

    public boolean isWaterTile(int tx, int ty) {
        if (!isInsideGrid(tx, ty)) {
            return true;
        }
        return tileAt(tx, ty) == TileType.WATER;
    }

    public boolean isWater(int tx, int ty) {
        return isWaterTile(tx, ty);
    }

    public boolean isBridgeTile(int tx, int ty) {
        if (!isInsideGrid(tx, ty)) {
            return false;
        }
        TileType t = tileAt(tx, ty);
        return t == TileType.BRIDGE_H || t == TileType.BRIDGE_V;
    }

    public boolean isBridge(int tx, int ty) {
        return isBridgeTile(tx, ty);
    }

    public boolean isWalkable(int tx, int ty) {
        return !isSolidTile(tx, ty);
    }

    // =========================================================================
    // Coordinate Transformations
    // =========================================================================

    public int worldToTileX(float worldX) {
        return (int) Math.floor(worldX / TILE_SIZE);
    }

    public int worldToTileY(float worldY) {
        return (int) Math.floor(worldY / TILE_SIZE);
    }

    public int[] worldToTile(float worldX, float worldY) {
        return new int[]{worldToTileX(worldX), worldToTileY(worldY)};
    }

    public int[] worldToTile(Vector2D pos) {
        if (pos == null) {
            return new int[]{0, 0};
        }
        return worldToTile(pos.x, pos.y);
    }

    public float tileToWorldX(int tx) {
        return (tx + 0.5f) * TILE_SIZE;
    }

    public float tileToWorldY(int ty) {
        return (ty + 0.5f) * TILE_SIZE;
    }

    public Vector2D tileToWorld(int tx, int ty) {
        return new Vector2D(tileToWorldX(tx), tileToWorldY(ty));
    }

    // =========================================================================
    // Environmental Obstacle Management
    // =========================================================================

    public void addDecoration(DecoType type, Vector2D pos, boolean solid) {
        if (type != null && pos != null) {
            obstacles.add(new Obstacle(type, pos, solid));
        }
    }

    public void addDecoration(DecoType type, float posX, float posY, boolean solid) {
        if (type != null) {
            obstacles.add(new Obstacle(type, posX, posY, solid));
        }
    }

    public void addObstacle(Obstacle obstacle) {
        if (obstacle != null) {
            obstacles.add(obstacle);
        }
    }

    public List<Obstacle> getObstacles() {
        return Collections.unmodifiableList(obstacles);
    }

    public List<Obstacle> getDecorations() {
        return getObstacles();
    }

    public void clearObstacles() {
        obstacles.clear();
    }

    /**
     * Pre-sorts obstacles by ground anchor Y ascending for 2.5D visual depth sorting.
     */
    public void sortObstaclesByDepth() {
        obstacles.sort(Comparator.comparingDouble(o -> o.getPosition().y));
    }

    // =========================================================================
    // Continuous Traversability & Collision Resolution
    // =========================================================================

    /**
     * Evaluates whether an entity circle of given radius can legally occupy (posX, posY).
     *
     * Three-phase validation:
     * 1. World bounds containment: [radius, WORLD_WIDTH - radius] x [radius, WORLD_HEIGHT - radius].
     * 2. Circle-AABB test against nearby water tiles.
     * 3. Circle-circle test against all solid environmental obstacles.
     */
    public boolean isTraversable(float posX, float posY, float radius) {
        // 1. World bounds containment check
        if (posX - radius < 0.0f || posX + radius > WORLD_WIDTH ||
            posY - radius < 0.0f || posY + radius > WORLD_HEIGHT) {
            return false;
        }

        // 2. Nearby water tiles circle-AABB test
        int minTx = Math.max(0, (int) Math.floor((posX - radius) / TILE_SIZE));
        int maxTx = Math.min(WIDTH - 1, (int) Math.floor((posX + radius) / TILE_SIZE));
        int minTy = Math.max(0, (int) Math.floor((posY - radius) / TILE_SIZE));
        int maxTy = Math.min(HEIGHT - 1, (int) Math.floor((posY + radius) / TILE_SIZE));

        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                if (isSolidTile(tx, ty)) {
                    float tileLeft = tx * TILE_SIZE;
                    float tileTop = ty * TILE_SIZE;
                    if (CollisionUtils.checkCircleVsAABB(posX, posY, radius, tileLeft, tileTop, TILE_SIZE, TILE_SIZE)) {
                        return false;
                    }
                }
            }
        }

        // 3. Solid environmental obstacle circle-circle test
        float rSq = radius * radius;
        for (Obstacle deco : obstacles) {
            if (!deco.isSolid() || deco.getCollisionRadius() <= 0.0f) {
                continue;
            }
            Vector2D colCenter = deco.getCollisionCenter();
            float totalRadius = radius + deco.getCollisionRadius();
            float dx = posX - colCenter.x;
            float dy = posY - colCenter.y;
            // Strict inequality matching C++ reference TileMap.cpp:176
            if ((dx * dx + dy * dy) < (totalRadius * totalRadius)) {
                return false;
            }
        }

        return true;
    }

    public boolean isTraversable(Vector2D pos, float radius) {
        if (pos == null) {
            return false;
        }
        return isTraversable(pos.x, pos.y, radius);
    }

    /**
     * Resolves movement from oldPos towards newPos preserving velocity along unblocked axes.
     * Delegates directly to CollisionUtils.resolveSliding.
     */
    public Vector2D resolveCollision(float oldX, float oldY, float newX, float newY, float radius) {
        return CollisionUtils.resolveSliding(
                oldX, oldY, newX, newY, radius,
                WORLD_WIDTH, WORLD_HEIGHT,
                this::isTraversable
        );
    }

    public Vector2D resolveCollision(Vector2D oldPos, Vector2D newPos, float radius) {
        float ox = oldPos != null ? oldPos.x : 0.0f;
        float oy = oldPos != null ? oldPos.y : 0.0f;
        float nx = newPos != null ? newPos.x : 0.0f;
        float ny = newPos != null ? newPos.y : 0.0f;
        return resolveCollision(ox, oy, nx, ny, radius);
    }

    // =========================================================================
    // Camera Frustum Culling (+/- 2 Margin Elimination of Visual Flashing)
    // =========================================================================

    /**
     * Calculates the bounding range of visible tile indices [minTx, maxTx, minTy, maxTy]
     * expanded by a +/-2 tile margin to eliminate border seams and black flashing during zoom/pan.
     */
    public int[] getVisibleTileRange(GameCamera camera, int[] outRange) {
        if (outRange == null || outRange.length < 4) {
            outRange = new int[4];
        }

        Rectangle vis = camera != null ? camera.getVisibleBounds() : worldBounds;
        outRange[0] = Math.max(0, (int) Math.floor(vis.x / TILE_SIZE) - 2);
        outRange[1] = Math.min(WIDTH - 1, (int) Math.ceil((vis.x + vis.width) / TILE_SIZE) + 2);
        outRange[2] = Math.max(0, (int) Math.floor(vis.y / TILE_SIZE) - 2);
        outRange[3] = Math.min(HEIGHT - 1, (int) Math.ceil((vis.y + vis.height) / TILE_SIZE) + 2);
        return outRange;
    }

    public int[] getVisibleTileRange(GameCamera camera) {
        return getVisibleTileRange(camera, new int[4]);
    }

    // =========================================================================
    // Decoupled Headless-Safe Rendering & Pure Transition Helpers
    // =========================================================================

    public static final int DIR_N = 1;
    public static final int DIR_E = 1 << 1;
    public static final int DIR_S = 1 << 2;
    public static final int DIR_W = 1 << 3;

    private static final float WATER_FRAME_DURATION = 0.16f;
    private static final float CAMPFIRE_FRAME_DURATION = 0.12f;

    /** Deterministic integer hash of tile grid coordinates for terrain variety. */
    public static int computeTileHash(int tx, int ty) {
        int h = tx * 374761393 + ty * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        return h ^ (h >>> 16);
    }

    /**
     * Selects a deterministic {@link CompleteSheetId#TERRAIN_VARIANTS} frame index
     * (`0..7`) for subtle terrain variety, or {@code -1} to render the canonical base tile.
     */
    public static int selectTerrainVariantFrame(TileType type, int tx, int ty) {
        if (type == null) {
            return -1;
        }
        int bucket = (computeTileHash(tx, ty) & 0x7fffffff) % 100;
        switch (type) {
            case GRASS:
                if (bucket < 55) return -1;
                if (bucket < 70) return 0;
                if (bucket < 85) return 1;
                return 2;
            case DIRT:
                if (bucket < 55) return -1;
                if (bucket < 70) return 3;
                if (bucket < 85) return 4;
                return 5;
            case STONE:
                return (bucket < 60) ? -1 : 6;
            case SAND:
                return (bucket < 60) ? -1 : 7;
            default:
                return -1;
        }
    }

    /**
     * Returns cardinal bitmask ({@link #DIR_N}, {@link #DIR_E}, {@link #DIR_S}, {@link #DIR_W})
     * of adjacent {@link TileType#GRASS} tiles that overhang onto this {@code DIRT/STONE/SAND} tile.
     */
    public int getGrassOverhangMask(int tx, int ty) {
        TileType center = tileAt(tx, ty);
        if (center != TileType.DIRT && center != TileType.STONE && center != TileType.SAND) {
            return 0;
        }
        int mask = 0;
        if (tileAt(tx, ty - 1) == TileType.GRASS) mask |= DIR_N;
        if (tileAt(tx + 1, ty) == TileType.GRASS) mask |= DIR_E;
        if (tileAt(tx, ty + 1) == TileType.GRASS) mask |= DIR_S;
        if (tileAt(tx - 1, ty) == TileType.GRASS) mask |= DIR_W;
        return mask;
    }

    /**
     * Returns cardinal bitmask ({@link #DIR_N}, {@link #DIR_E}, {@link #DIR_S}, {@link #DIR_W})
     * of shoreline transition fringes to overlay onto a {@link TileType#WATER} tile.
     * Borders adjacent to bridges are explicitly excluded so wooden bridges span water cleanly.
     */
    public int getShoreTransitionMask(int tx, int ty) {
        if (tileAt(tx, ty) != TileType.WATER) {
            return 0;
        }
        int mask = 0;
        if (isShoreBorderTile(tx, ty - 1)) mask |= DIR_N;
        if (isShoreBorderTile(tx + 1, ty)) mask |= DIR_E;
        if (isShoreBorderTile(tx, ty + 1)) mask |= DIR_S;
        if (isShoreBorderTile(tx - 1, ty)) mask |= DIR_W;
        return mask;
    }

    private boolean isShoreBorderTile(int nx, int ny) {
        return !isWaterTile(nx, ny) && !isBridgeTile(nx, ny);
    }

    /**
     * Renders terrain tiles within the frustum-culled +/-2 tile range using animated
     * water, deterministic terrain variants, grass overhangs, and water shore fringes.
     * Safely no-ops in headless environments.
     */
    public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera) {
        float elapsedSeconds = (System.currentTimeMillis() % 1_000_000L) * 0.001f;
        drawTerrain(batch, textureManager, camera, elapsedSeconds);
    }

    /**
     * Renders terrain tiles using the supplied simulation/render elapsed time in seconds.
     */
    public void drawTerrain(SpriteBatch batch, TextureManager textureManager, GameCamera camera, float elapsedSeconds) {
        if (batch == null || textureManager == null || Gdx.gl == null) {
            return;
        }

        int[] range = getVisibleTileRange(camera);
        int minTx = range[0];
        int maxTx = range[1];
        int minTy = range[2];
        int maxTy = range[3];

        boolean hasWaterAnim = textureManager.hasExtendedSheet(CompleteSheetId.WATER_ANIM);
        boolean hasVariants = textureManager.hasExtendedSheet(CompleteSheetId.TERRAIN_VARIANTS);
        boolean hasTransitions = textureManager.hasExtendedSheet(CompleteSheetId.TERRAIN_TRANSITIONS);

        float safeElapsed = Math.max(0.0f, elapsedSeconds);
        int waterFrame = ((int) (safeElapsed / WATER_FRAME_DURATION)) % 4;

        for (int ty = minTy; ty <= maxTy; ty++) {
            for (int tx = minTx; tx <= maxTx; tx++) {
                TileType type = tileAt(tx, ty);
                TextureRegion baseRegion = null;

                if (type == TileType.WATER && hasWaterAnim) {
                    baseRegion = textureManager.getExtendedFrame(CompleteSheetId.WATER_ANIM, waterFrame);
                } else if (hasVariants) {
                    int variantIdx = selectTerrainVariantFrame(type, tx, ty);
                    if (variantIdx >= 0) {
                        baseRegion = textureManager.getExtendedFrame(CompleteSheetId.TERRAIN_VARIANTS, variantIdx);
                    }
                }
                if (baseRegion == null) {
                    baseRegion = textureManager.getRegion(type.getTextureId());
                }

                float drawX = tx * TILE_SIZE;
                float drawY = ty * TILE_SIZE;
                if (baseRegion != null) {
                    drawRegionYDown(batch, baseRegion, drawX, drawY, TILE_SIZE, TILE_SIZE);
                }

                if (hasTransitions) {
                    int grassMask = getGrassOverhangMask(tx, ty);
                    if (grassMask != 0) {
                        drawTransitionMask(batch, textureManager, grassMask, 0, drawX, drawY);
                    }
                    int shoreMask = getShoreTransitionMask(tx, ty);
                    if (shoreMask != 0) {
                        drawTransitionMask(batch, textureManager, shoreMask, 4, drawX, drawY);
                    }
                }
            }
        }
    }

    private static void drawTransitionMask(SpriteBatch batch, TextureManager textureManager,
                                           int mask, int baseFrameOffset, float drawX, float drawY) {
        for (int dir = 0; dir < 4; dir++) {
            if ((mask & (1 << dir)) != 0) {
                TextureRegion overlay = textureManager.getExtendedFrame(
                        CompleteSheetId.TERRAIN_TRANSITIONS, baseFrameOffset + dir);
                if (overlay != null) {
                    drawRegionYDown(batch, overlay, drawX, drawY, TILE_SIZE, TILE_SIZE);
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

    /**
     * Draws a single map obstacle decoration using animated campfire fallback where applicable.
     */
    public void drawDecoration(SpriteBatch batch, TextureManager textureManager, Obstacle obstacle) {
        float elapsedSeconds = (System.currentTimeMillis() % 1_000_000L) * 0.001f;
        drawDecoration(batch, textureManager, obstacle, elapsedSeconds);
    }

    /**
     * Draws a single map obstacle decoration at the specified animation elapsed time.
     */
    public void drawDecoration(SpriteBatch batch, TextureManager textureManager, Obstacle obstacle, float elapsedSeconds) {
        if (batch == null || textureManager == null || obstacle == null || Gdx.gl == null) {
            return;
        }
        Rectangle bounds = obstacle.getVisualBounds();
        TextureRegion region = null;
        if (obstacle.getType() == DecoType.CAMPFIRE
                && textureManager.hasExtendedSheet(ExtendedSheetId.CAMPFIRE_ANIM)) {
            int frame = ((int) (Math.max(0.0f, elapsedSeconds) / CAMPFIRE_FRAME_DURATION)) % 4;
            region = textureManager.getExtendedFrame(ExtendedSheetId.CAMPFIRE_ANIM, frame);
        }
        if (region == null) {
            region = textureManager.getRegion(obstacle.getType().getTextureId());
        }
        if (region != null) {
            drawRegionYDown(batch, region, bounds.x, bounds.y, bounds.width, bounds.height);
        }
    }

    /**
     * Renders visible decorations sorted by ground anchor Y.
     * Safely no-ops in headless environments.
     */
    public void drawDecorations(SpriteBatch batch, TextureManager textureManager, GameCamera camera) {
        if (batch == null || textureManager == null || Gdx.gl == null) {
            return;
        }
        float elapsedSeconds = (System.currentTimeMillis() % 1_000_000L) * 0.001f;
        for (Obstacle obstacle : obstacles) {
            if (camera != null && !camera.isVisible(obstacle.getVisualBounds())) {
                continue; // Frustum culled
            }
            drawDecoration(batch, textureManager, obstacle, elapsedSeconds);
        }
    }
}
