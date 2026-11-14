package com.spellstorm.world;

import com.spellstorm.math.Vector2D;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic procedural world generator for SpellStorm.
 *
 * Implements:
 * 1. Dual-harmonic sinusoidal meandering river with flanking sandy riverbanks.
 * 2. 5-crossing horizontal bridge network with 4-tile flared dirt landings ensuring
 *    ample clearance for the Colossus Boss (r=50px).
 * 3. Ancient Stone Ruins and Wilderness Campsite landmark settlements.
 * 4. Anti-clumping natural decorations with starting arena safe-zone (r=12 tiles),
 *    bridge clearance zones (4 tiles), and >= 44.0px solid spacing with passable BUSH fallback.
 */
public final class WorldGenerator {

    private WorldGenerator() {
        // Static generator
    }

    /**
     * Procedurally populates the world grid and obstacles from a seed.
     */
    public static void generate(TileMap tileMap, long seed) {
        if (tileMap == null) return;

        // Reset all tiles to Grass
        for (int y = 0; y < tileMap.getHeight(); y++) {
            for (int x = 0; x < tileMap.getWidth(); x++) {
                tileMap.setTile(x, y, TileType.GRASS);
            }
        }
        tileMap.clearObstacles();

        placeRiver(tileMap, seed);
        placePaths(tileMap);
        placeRuinsAndCamps(tileMap);
        placeNaturalDecorations(tileMap, seed);
        tileMap.sortObstaclesByDepth();
    }

    /**
     * Generates a North-to-South undulating river with dual-harmonic sinusoidal curvature
     * and 2-tile wide sandbanks on each shore.
     */
    public static void placeRiver(TileMap tileMap, long seed) {
        int height = tileMap.getHeight();

        for (int y = 0; y < height; y++) {
            float fy = (float) y;
            // Smooth undulating river center around x = 42
            float riverCenter = 42.0f + (float) Math.sin(fy * 0.08f) * 7.5f + (float) Math.cos(fy * 0.16f) * 3.0f;
            int rc = Math.round(riverCenter);

            // Water channel (4 tiles wide: rc - 2 to rc + 1)
            for (int dx = -2; dx <= 1; dx++) {
                int tx = rc + dx;
                tileMap.setTile(tx, y, TileType.WATER);
            }

            // Sandy riverbanks (2 tiles on each side for smooth, gentle terrain transition)
            for (int d = 1; d <= 2; d++) {
                int westX = rc - 2 - d;
                int eastX = rc + 1 + d;
                if (tileMap.tileAt(westX, y) != TileType.WATER) {
                    tileMap.setTile(westX, y, TileType.SAND);
                }
                if (tileMap.tileAt(eastX, y) != TileType.WATER) {
                    tileMap.setTile(eastX, y, TileType.SAND);
                }
            }
        }
    }

    /**
     * Builds thoroughfares, trails, and the 5-crossing river bridge network.
     */
    public static void placePaths(TileMap tileMap) {
        int width = tileMap.getWidth();
        int height = tileMap.getHeight();

        // 1. Build distributed 5-tile-wide river bridge network across the entire world:
        // - Far North Crossing (y = 11..15, 5 tiles wide)
        buildRiverBridge(tileMap, 11, 15);
        // - Northern Crossing (y = 28..32, 5 tiles wide)
        buildRiverBridge(tileMap, 28, 32);
        // - Central Highway Crossing (y = 62..66, 5 tiles wide)
        buildRiverBridge(tileMap, 62, 66);
        // - Southern Crossing (y = 85..89, 5 tiles wide)
        buildRiverBridge(tileMap, 85, 89);
        // - Far South Crossing (y = 109..113, 5 tiles wide)
        buildRiverBridge(tileMap, 109, 113);

        // 2. Main East-West Highway across entire world at y = 62..66 (5 tiles wide)
        for (int x = 0; x < width; x++) {
            for (int dy = -2; dy <= 2; dy++) {
                int y = 64 + dy;
                if (!tileMap.isBridgeTile(x, y)) {
                    tileMap.setTile(x, y, TileType.DIRT);
                }
            }
        }

        // 3. North-South Trail at x = 78..80 (3 tiles wide)
        for (int y = 0; y < height; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                int x = 79 + dx;
                if (!tileMap.isBridgeTile(x, y)) {
                    tileMap.setTile(x, y, TileType.DIRT);
                }
            }
        }

        // 4. Path to Ancient Ruins (branches from (79, 64) towards (88, 35))
        for (int y = 35; y <= 64; y++) {
            float t = (float) (y - 35) / 29.0f;
            int px = Math.round(88.0f - t * 9.0f);
            tileMap.setTile(px, y, TileType.DIRT);
            tileMap.setTile(px + 1, y, TileType.DIRT);
        }

        // 5. Path to South-East Campsite (branches towards (95, 88))
        for (int y = 64; y <= 88; y++) {
            float t = (float) (y - 64) / 24.0f;
            int px = Math.round(79.0f + t * 16.0f);
            tileMap.setTile(px, y, TileType.DIRT);
            tileMap.setTile(px + 1, y, TileType.DIRT);
        }
    }

    /**
     * Constructs a rectangular bridge span across water/sand and flared dirt approach landings.
     */
    public static void buildRiverBridge(TileMap tileMap, int startY, int endY) {
        int minWaterX = 999;
        int maxWaterX = -1;

        for (int y = startY; y <= endY; y++) {
            for (int x = 20; x <= 65; x++) {
                if (tileMap.isWaterTile(x, y) || tileMap.tileAt(x, y) == TileType.SAND) {
                    minWaterX = Math.min(minWaterX, x);
                    maxWaterX = Math.max(maxWaterX, x);
                }
            }
        }

        if (minWaterX <= maxWaterX && minWaterX >= 0) {
            // Bridge spans the full rectangular channel
            for (int y = startY; y <= endY; y++) {
                for (int x = minWaterX; x <= maxWaterX; x++) {
                    tileMap.setTile(x, y, TileType.BRIDGE_H);
                }
            }

            // Flared dirt approach landings on both riverbanks (startY - 1 to endY + 1)
            // extending 4 tiles inland, ensuring no sharp water notches or dead-end pockets
            for (int y = startY - 1; y <= endY + 1; y++) {
                if (y < 0 || y >= tileMap.getHeight()) continue;
                for (int d = 1; d <= 4; d++) {
                    int westX = minWaterX - d;
                    int eastX = maxWaterX + d;
                    if (westX >= 0) {
                        tileMap.setTile(westX, y, TileType.DIRT);
                    }
                    if (eastX < tileMap.getWidth()) {
                        tileMap.setTile(eastX, y, TileType.DIRT);
                    }
                }
            }
        }
    }

    /**
     * Places the Ancient Ruins plaza and Campsite settlement along with environmental props.
     */
    public static void placeRuinsAndCamps(TileMap tileMap) {
        // 1. Ancient Stone Ruins Sanctuary at (88, 35) (radius 6 circle)
        for (int dy = -6; dy <= 6; dy++) {
            for (int dx = -6; dx <= 6; dx++) {
                int tx = 88 + dx;
                int ty = 35 + dy;
                if (dx * dx + dy * dy <= 36) {
                    tileMap.setTile(tx, ty, TileType.STONE);
                }
            }
        }

        // Place stone pillars & ancient ruins props around the sanctuary
        tileMap.addDecoration(DecoType.RUINS, tileMap.tileToWorld(84, 30), true);
        tileMap.addDecoration(DecoType.RUINS, tileMap.tileToWorld(92, 30), true);
        tileMap.addDecoration(DecoType.RUINS, tileMap.tileToWorld(84, 40), true);
        tileMap.addDecoration(DecoType.RUINS, tileMap.tileToWorld(92, 40), true);
        tileMap.addDecoration(DecoType.ROCK_LARGE, tileMap.tileToWorld(88, 29), true);
        tileMap.addDecoration(DecoType.ROCK_LARGE, tileMap.tileToWorld(88, 41), true);

        // 2. Wilderness Campsite at (95, 88) (radius 4 circle)
        for (int dy = -4; dy <= 4; dy++) {
            for (int dx = -4; dx <= 4; dx++) {
                int tx = 95 + dx;
                int ty = 88 + dy;
                if (dx * dx + dy * dy <= 16) {
                    tileMap.setTile(tx, ty, TileType.DIRT);
                }
            }
        }

        // Campsite props: tents, campfire, logs
        tileMap.addDecoration(DecoType.TENT, tileMap.tileToWorld(92, 86), true);
        tileMap.addDecoration(DecoType.TENT, tileMap.tileToWorld(98, 86), true);
        tileMap.addDecoration(DecoType.CAMPFIRE, tileMap.tileToWorld(95, 88), true);
        tileMap.addDecoration(DecoType.LOG, tileMap.tileToWorld(95, 90), false);
        tileMap.addDecoration(DecoType.LOG, tileMap.tileToWorld(93, 89), false);
    }

    /**
     * Places natural flora and obstacles with anti-clumping spacing and player safe-zone filters.
     */
    public static void placeNaturalDecorations(TileMap tileMap, long seed) {
        Random rng = new Random(seed + 101);

        float centerWorldX = 64.0f * tileMap.getTileSize();
        float centerWorldY = 64.0f * tileMap.getTileSize();
        float safeRadiusSq = (12.0f * tileMap.getTileSize()) * (12.0f * tileMap.getTileSize()); // 384^2 = 147456

        List<Vector2D> placedSolids = new ArrayList<>(200);

        // Exactly 290 candidate iterations
        int width = tileMap.getWidth();
        int height = tileMap.getHeight();

        for (int i = 0; i < 290; i++) {
            int tx = 3 + rng.nextInt(width - 7);
            int ty = 3 + rng.nextInt(height - 7);
            TileType t = tileMap.tileAt(tx, ty);

            // Filter 1: Candidate must be Grass (never water, bridge, stone plaza, or dirt roads)
            if (t != TileType.GRASS) {
                continue;
            }

            Vector2D worldPos = tileMap.tileToWorld(tx, ty);
            float dxC = worldPos.x - centerWorldX;
            float dyC = worldPos.y - centerWorldY;

            // Filter 2: Safe-zone exclusion around starting arena center
            if ((dxC * dxC + dyC * dyC) < safeRadiusSq) {
                continue;
            }

            // Filter 3: 4 tiles clearance from any river bridge
            boolean nearBridge = false;
            for (int bdy = -4; bdy <= 4 && !nearBridge; bdy++) {
                for (int bdx = -4; bdx <= 4; bdx++) {
                    if (tileMap.isBridgeTile(tx + bdx, ty + bdy)) {
                        nearBridge = true;
                        break;
                    }
                }
            }
            if (nearBridge) {
                continue;
            }

            float roll = rng.nextFloat();
            // Balanced distribution: 40% passable decorative flora, 60% solid obstacles
            if (roll < 0.25f) {
                // Passable bush
                tileMap.addDecoration(DecoType.BUSH, worldPos, false);
            } else if (roll < 0.40f) {
                // Passable fallen log
                tileMap.addDecoration(DecoType.LOG, worldPos, false);
            } else {
                // Filter 4: Minimum spacing >= 44.0 units from existing solid obstacles
                boolean tooClose = false;
                for (Vector2D existing : placedSolids) {
                    float ddx = worldPos.x - existing.x;
                    float ddy = worldPos.y - existing.y;
                    if ((ddx * ddx + ddy * ddy) < (44.0f * 44.0f)) {
                        tooClose = true;
                        break;
                    }
                }
                if (tooClose) {
                    // Turn into passable bush rather than leaving dead space or causing a collision trap
                    tileMap.addDecoration(DecoType.BUSH, worldPos, false);
                    continue;
                }

                placedSolids.add(worldPos);
                float solidRoll = rng.nextFloat();
                if (solidRoll < 0.45f) {
                    tileMap.addDecoration(DecoType.TREE_OAK, worldPos, true);
                } else if (solidRoll < 0.75f) {
                    tileMap.addDecoration(DecoType.TREE_PINE, worldPos, true);
                } else if (solidRoll < 0.90f) {
                    tileMap.addDecoration(DecoType.ROCK, worldPos, true);
                } else {
                    tileMap.addDecoration(DecoType.ROCK_LARGE, worldPos, true);
                }
            }
        }
    }
}
