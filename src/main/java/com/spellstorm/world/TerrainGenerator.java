package com.spellstorm.world;

import com.spellstorm.world.noise.FractalNoise;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Deterministic multi-layer procedural terrain generator for SpellStorm.
 *
 * <p>Evaluation hierarchy:
 * <ol>
 *   <li>Arterial East-West highway and North-South trail.</li>
 *   <li>Ridge noise river channel and sandy riverbanks.</li>
 *   <li>Arterial bridge crossings spanning water/sand riverbanks.</li>
 *   <li>Continuous elevation noise (mountains / rock outcrops).</li>
 *   <li>Whittaker biome primary tiles (Plains, Forest, Taiga, Tundra, Desert).</li>
 * </ol>
 *
 * <p>Natural decorations are placed via an 8x8 macro-grid with boundary margins,
 * mathematically guaranteeing minimum Poisson spacing of &ge; 44.0 px without cross-chunk queries.
 */
public class TerrainGenerator {

    public static final float DEFAULT_RIVER_SCALE = 0.005f;
    public static final float DEFAULT_HEIGHT_SCALE = 0.005f;
    public static final float DEFAULT_WATER_WIDTH = 0.035f;
    public static final float DEFAULT_SAND_WIDTH = 0.035f;
    public static final float DEFAULT_ELEVATION_THRESHOLD = 0.72f;

    public static final int ROAD_PERIOD = 256;       // Arterial road every 8 chunks
    public static final int HIGHWAY_HALF_WIDTH = 2;  // 5-tile wide E-W highway
    public static final int TRAIL_HALF_WIDTH = 1;    // 3-tile wide N-S trail

    public static final float MIN_OBSTACLE_SPACING = 44.0f;
    public static final float SAFE_ZONE_RADIUS = 384.0f; // 12-tile spawn clearance

    private final long worldSeed;
    private final BiomeClassifier biomeClassifier;
    private final FractalNoise riverNoise;
    private final FractalNoise heightNoise;

    public TerrainGenerator(long worldSeed) {
        this.worldSeed = worldSeed;
        this.biomeClassifier = new BiomeClassifier(worldSeed);
        this.riverNoise = new FractalNoise(worldSeed ^ 0x52697665724E6F69L, 2, 0.5f, 2.0f, DEFAULT_RIVER_SCALE);
        this.heightNoise = new FractalNoise(worldSeed ^ 0x4865696768744E6FL, 2, 0.5f, 2.0f, DEFAULT_HEIGHT_SCALE);
    }

    public TerrainGenerator(long worldSeed, BiomeClassifier biomeClassifier,
                            FractalNoise riverNoise, FractalNoise heightNoise) {
        this.worldSeed = worldSeed;
        this.biomeClassifier = Objects.requireNonNull(biomeClassifier, "BiomeClassifier cannot be null");
        this.riverNoise = Objects.requireNonNull(riverNoise, "RiverNoise cannot be null");
        this.heightNoise = Objects.requireNonNull(heightNoise, "HeightNoise cannot be null");
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    public BiomeClassifier getBiomeClassifier() {
        return biomeClassifier;
    }

    public boolean isEastWestHighway(int gx, int gy) {
        int ym = Math.floorMod(gy, ROAD_PERIOD);
        return (ym <= HIGHWAY_HALF_WIDTH || ym >= ROAD_PERIOD - HIGHWAY_HALF_WIDTH);
    }

    public boolean isNorthSouthTrail(int gx, int gy) {
        int xm = Math.floorMod(gx, ROAD_PERIOD);
        return (xm <= TRAIL_HALF_WIDTH || xm >= ROAD_PERIOD - TRAIL_HALF_WIDTH);
    }

    public boolean isRoad(int gx, int gy) {
        return isEastWestHighway(gx, gy) || isNorthSouthTrail(gx, gy);
    }

    public float getRiverNoise(int gx, int gy) {
        return riverNoise.getRidgeNoise(gx, gy);
    }

    public boolean isRiverWater(int gx, int gy) {
        return getRiverNoise(gx, gy) < DEFAULT_WATER_WIDTH;
    }

    public boolean isRiverSand(int gx, int gy) {
        float r = getRiverNoise(gx, gy);
        return r >= DEFAULT_WATER_WIDTH && r < (DEFAULT_WATER_WIDTH + DEFAULT_SAND_WIDTH);
    }

    public float getHeight(int gx, int gy) {
        return heightNoise.getNoiseNormalized(gx, gy);
    }

    public BiomeType getBiome(int gx, int gy) {
        return biomeClassifier.getBiome(gx, gy);
    }

    /**
     * Pure O(1) deterministic evaluation of tile at global coordinates (gx, gy).
     */
    public TileType sampleTileType(int gx, int gy) {
        boolean isEW = isEastWestHighway(gx, gy);
        boolean isNS = isNorthSouthTrail(gx, gy);

        float r = getRiverNoise(gx, gy);
        boolean isWater = r < DEFAULT_WATER_WIDTH;
        boolean isSand = r >= DEFAULT_WATER_WIDTH && r < (DEFAULT_WATER_WIDTH + DEFAULT_SAND_WIDTH);

        if (isEW) {
            return (isWater || isSand) ? TileType.BRIDGE_H : TileType.DIRT;
        }
        if (isNS) {
            return (isWater || isSand) ? TileType.BRIDGE_V : TileType.DIRT;
        }

        if (isWater) return TileType.WATER;
        if (isSand) return TileType.SAND;

        if (getHeight(gx, gy) > DEFAULT_ELEVATION_THRESHOLD) {
            return TileType.STONE;
        }

        return getBiome(gx, gy).getPrimaryTile();
    }

    /**
     * Generates all 1024 tiles and deterministic decorations for a chunk.
     */
    public void generateChunk(int cx, int cy, Chunk chunk) {
        for (int ly = 0; ly < Chunk.CHUNK_TILES; ly++) {
            for (int lx = 0; lx < Chunk.CHUNK_TILES; lx++) {
                int gx = (int) ChunkKey.chunkLocalToGlobalTileX(cx, lx);
                int gy = (int) ChunkKey.chunkLocalToGlobalTileY(cy, ly);
                chunk.setLocalTile(lx, ly, sampleTileType(gx, gy));
            }
        }
        List<Obstacle> decos = generateDecorations(cx, cy);
        for (Obstacle o : decos) {
            chunk.addObstacle(o);
        }
    }

    /**
     * Places decorations within chunk using 8x8 macro-grid Poisson cell jitter.
     */
    public List<Obstacle> generateDecorations(int cx, int cy) {
        List<Obstacle> obstacles = new ArrayList<>(32);
        float cellMargin = 22.0f;
        float jitterSpan = 128.0f - 2.0f * cellMargin; // 84.0f

        for (int cellY = 0; cellY < 8; cellY++) {
            for (int cellX = 0; cellX < 8; cellX++) {
                int mcx = cx * 8 + cellX;
                int mcy = cy * 8 + cellY;

                long cellSeed = ChunkSeeder.hashChunkSeed(worldSeed, mcx, mcy);
                long seedSpawn = ChunkSeeder.hashSalt(cellSeed, 1);
                long seedJitterX = ChunkSeeder.hashSalt(cellSeed, 2);
                long seedJitterY = ChunkSeeder.hashSalt(cellSeed, 3);
                long seedType = ChunkSeeder.hashSalt(cellSeed, 4);

                int centerGx = mcx * 4 + 2;
                int centerGy = mcy * 4 + 2;
                BiomeType biome = getBiome(centerGx, centerGy);

                float spawnRoll = ((seedSpawn >>> 33) & 0x7FFFFFFFL) / (float) 0x7FFFFFFF;
                if (spawnRoll >= biome.getTreeDensity()) {
                    continue; // Density threshold
                }

                float jx = cellMargin + (((seedJitterX >>> 33) & 0x7FFFFFFFL) / (float) 0x7FFFFFFF) * jitterSpan;
                float jy = cellMargin + (((seedJitterY >>> 33) & 0x7FFFFFFFL) / (float) 0x7FFFFFFF) * jitterSpan;

                float localX = cellX * 128.0f + jx;
                float localY = cellY * 128.0f + jy;

                float posX = mcx * 128.0f + jx;
                float posY = mcy * 128.0f + jy;

                // Safe zone check (around spawn)
                if (posX * posX + posY * posY < SAFE_ZONE_RADIUS * SAFE_ZONE_RADIUS) {
                    continue;
                }

                int candGx = ChunkKey.worldToTileX(posX);
                int candGy = ChunkKey.worldToTileY(posY);
                TileType candTile = sampleTileType(candGx, candGy);

                // Natural walkable tile check
                if (candTile == TileType.WATER || candTile.isBridge() || candTile == TileType.DIRT) {
                    continue;
                }

                // Near bridge clearance check
                boolean nearBridge = false;
                for (int bdy = -2; bdy <= 2 && !nearBridge; bdy++) {
                    for (int bdx = -2; bdx <= 2; bdx++) {
                        if (sampleTileType(candGx + bdx, candGy + bdy).isBridge()) {
                            nearBridge = true;
                            break;
                        }
                    }
                }
                if (nearBridge) continue;

                float typeRoll = ((seedType >>> 33) & 0x7FFFFFFFL) / (float) 0x7FFFFFFF;
                DecoType type = selectPropType(biome, typeRoll);
                obstacles.add(new Obstacle(type, localX, localY, type.isDefaultSolid()));
            }
        }

        obstacles.sort(Comparator.comparingDouble(o -> o.getPosition().y));
        return obstacles;
    }

    /**
     * Helper returning decorations with global coordinates for diagnostic/test parity.
     */
    public List<Obstacle> generateDecorationsGlobal(int cx, int cy) {
        List<Obstacle> locals = generateDecorations(cx, cy);
        List<Obstacle> globals = new ArrayList<>(locals.size());
        float originX = (float) ((long) cx * 1024L);
        float originY = (float) ((long) cy * 1024L);
        for (int i = 0; i < locals.size(); i++) {
            Obstacle o = locals.get(i);
            globals.add(new Obstacle(o.getType(), originX + o.getPosition().x, originY + o.getPosition().y, o.isSolid()));
        }
        return globals;
    }

    private DecoType selectPropType(BiomeType biome, float roll) {
        return switch (biome) {
            case FOREST -> roll < 0.50f ? DecoType.TREE_OAK :
                           roll < 0.80f ? DecoType.TREE_PINE :
                           roll < 0.90f ? DecoType.ROCK : DecoType.ROCK_LARGE;
            case PLAINS -> roll < 0.40f ? DecoType.TREE_OAK :
                           roll < 0.70f ? DecoType.TREE_PINE :
                           roll < 0.90f ? DecoType.ROCK : DecoType.ROCK_LARGE;
            case TAIGA -> roll < 0.75f ? DecoType.TREE_PINE :
                          roll < 0.90f ? DecoType.ROCK : DecoType.ROCK_LARGE;
            case TUNDRA -> roll < 0.30f ? DecoType.TREE_PINE :
                           roll < 0.70f ? DecoType.ROCK : DecoType.ROCK_LARGE;
            case DESERT -> roll < 0.60f ? DecoType.ROCK : DecoType.ROCK_LARGE;
        };
    }
}
