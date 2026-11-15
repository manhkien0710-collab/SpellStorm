package com.spellstorm.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.spellstorm.assets.FontManager;
import com.spellstorm.math.MathUtils;
import com.spellstorm.math.Vector2D;
import com.spellstorm.world.TileMap;
import com.spellstorm.world.TileMapAdapter;
import com.spellstorm.world.TileType;
import com.spellstorm.world.WorldGrid;

import java.util.List;

/**
 * Screen-space minimap HUD widget renderer.
 *
 * Characteristics:
 * - Dimensions: 150 x 150 px positioned at (1110, 545) in standard 1280x720 UI space.
 * - Subsampling: 4x4 tiles (32x32 sample cells across the 128x128 map).
 * - Terrain styling: water (blue), dirt/bridges (brown), stone (grey).
 * - Dynamic blips: enemies (small red dots), player (pulsing cyan marker with white ring).
 */
public class MinimapRenderer {

    public static final float MINIMAP_X = 1110.0f;
    public static final float MINIMAP_Y = 545.0f; // Y-down top-left origin
    public static final float MINIMAP_SIZE = 150.0f;
    public static final int SUBSAMPLING_STEP = 4;
    public static final float LABEL_HEIGHT = 16.0f;
    public static final float LABEL_GAP = 4.0f;

    // Palette specifications matching C++ sf::Color
    public static final Color COLOR_MINIMAP_BG = new Color(12f / 255f, 16f / 255f, 24f / 255f, 215f / 255f);
    public static final Color COLOR_MINIMAP_BORDER = new Color(65f / 255f, 95f / 255f, 140f / 255f, 220f / 255f);
    public static final Color COLOR_TILE_WATER = new Color(45f / 255f, 115f / 255f, 195f / 255f, 180f / 255f);
    public static final Color COLOR_TILE_DIRT = new Color(120f / 255f, 100f / 255f, 70f / 255f, 150f / 255f);
    public static final Color COLOR_TILE_STONE = new Color(140f / 255f, 150f / 255f, 170f / 255f, 180f / 255f);
    public static final Color COLOR_TILE_GRASS = new Color(55f / 255f, 105f / 255f, 45f / 255f, 140f / 255f);
    public static final Color COLOR_TILE_SAND = new Color(210f / 255f, 190f / 255f, 120f / 255f, 180f / 255f);
    public static final float DEFAULT_RADAR_SPAN = 4096.0f; // 128 tiles span, radius = 2048 px
    public static final Color COLOR_ENEMY_BLIP = new Color(255f / 255f, 60f / 255f, 60f / 255f, 1.0f);
    public static final Color COLOR_PLAYER_MARKER = new Color(60f / 255f, 220f / 255f, 255f / 255f, 1.0f);
    public static final Color COLOR_LABEL = new Color(160f / 255f, 190f / 255f, 230f / 255f, 1.0f);
    public static final Color COLOR_LABEL_BG = new Color(12f / 255f, 16f / 255f, 24f / 255f, 220f / 255f);
    public static final Color COLOR_BOSS_MARKER = new Color(255f / 255f, 200f / 255f, 45f / 255f, 1.0f);
    public static final Color COLOR_POWERUP_MARKER = new Color(140f / 255f, 255f / 255f, 115f / 255f, 1.0f);
    public static final Color COLOR_VIEWPORT_RECT = new Color(220f / 255f, 240f / 255f, 1.0f, 0.75f);

    private final GlyphLayout glyphLayout = new GlyphLayout();

    public MinimapRenderer() {
    }

    public static Rectangle getMinimapBounds(boolean yDown) {
        float y = yDown ? MINIMAP_Y : (720.0f - MINIMAP_Y - MINIMAP_SIZE);
        return new Rectangle(MINIMAP_X, y, MINIMAP_SIZE, MINIMAP_SIZE);
    }

    public static Rectangle getMinimapBounds() {
        return getMinimapBounds(true);
    }

    /**
     * Bounds for the "MAP [OVERWORLD]" header strictly above/outside the minimap box.
     */
    public static Rectangle getMinimapLabelBounds(boolean yDown) {
        float yDownTop = MINIMAP_Y - LABEL_GAP - LABEL_HEIGHT;
        float y = yDown ? yDownTop : (720.0f - yDownTop - LABEL_HEIGHT);
        return new Rectangle(MINIMAP_X, y, MINIMAP_SIZE, LABEL_HEIGHT);
    }

    /**
     * Projects the world camera visible bounds onto minimap coordinates and clamps strictly inside minimap bounds.
     */
    public static Rectangle computeViewportRectOnMinimap(Rectangle cameraVisibleBounds,
                                                         float worldW, float worldH,
                                                         boolean yDown) {
        Rectangle bounds = getMinimapBounds(yDown);
        if (cameraVisibleBounds == null || worldW <= 0.0f || worldH <= 0.0f) {
            return new Rectangle(bounds.x, bounds.y, 0.0f, 0.0f);
        }

        float scaleX = bounds.width / worldW;
        float scaleY = bounds.height / worldH;

        float minX = bounds.x;
        float maxX = bounds.x + bounds.width;
        float minY = bounds.y;
        float maxY = bounds.y + bounds.height;

        float left = MathUtils.clamp(bounds.x + cameraVisibleBounds.x * scaleX, minX, maxX);
        float right = MathUtils.clamp(bounds.x + (cameraVisibleBounds.x + cameraVisibleBounds.width) * scaleX, minX, maxX);

        float y1 = yDown
                ? bounds.y + cameraVisibleBounds.y * scaleY
                : bounds.y + (worldH - (cameraVisibleBounds.y + cameraVisibleBounds.height)) * scaleY;
        float y2 = yDown
                ? bounds.y + (cameraVisibleBounds.y + cameraVisibleBounds.height) * scaleY
                : bounds.y + (worldH - cameraVisibleBounds.y) * scaleY;

        float bottom = MathUtils.clamp(Math.min(y1, y2), minY, maxY);
        float top = MathUtils.clamp(Math.max(y1, y2), minY, maxY);

        return new Rectangle(left, bottom, Math.max(0.0f, right - left), Math.max(0.0f, top - bottom));
    }

    /**
     * Renders the minimap with tile subsampling, player marker, and enemy dots.
     * Supports polymorphic world spatial providers: WorldGrid, TileMap, or null.
     */
    public void renderMinimap(ShapeRenderer shapeRenderer, SpriteBatch batch,
                              FontManager fontManager, Object worldGridOrTileMap,
                              Vector2D playerPos, List<Vector2D> enemyPositions,
                              boolean yDown) {
        renderMinimap(shapeRenderer, batch, fontManager, worldGridOrTileMap, playerPos,
                enemyPositions, null, null, null, yDown);
    }

    /**
     * Dual-mode minimap renderer:
     * - For bounded worlds (TileMap / TileMapAdapter), renders the legacy fixed 128x128 map.
     * - For unbounded worlds (InfiniteWorld), renders a dynamic local radar window centered on player.
     */
    public void renderMinimap(ShapeRenderer shapeRenderer, SpriteBatch batch,
                              FontManager fontManager, Object worldGridOrTileMap,
                              Vector2D playerPos, List<Vector2D> enemyPositions,
                              List<Vector2D> bossPositions, List<Vector2D> powerUpPositions,
                              Rectangle cameraVisibleBounds, boolean yDown) {
        if (worldGridOrTileMap instanceof WorldGrid worldGrid && !worldGrid.isBounded()) {
            renderRadarMinimap(shapeRenderer, batch, fontManager, worldGrid, playerPos,
                    enemyPositions, bossPositions, powerUpPositions, cameraVisibleBounds, yDown);
            return;
        }

        TileMap tileMap = null;
        if (worldGridOrTileMap instanceof TileMap tm) {
            tileMap = tm;
        } else if (worldGridOrTileMap instanceof TileMapAdapter adapter) {
            tileMap = adapter.getTileMap();
        }

        renderTileMapMinimap(shapeRenderer, batch, fontManager, tileMap, playerPos,
                enemyPositions, bossPositions, powerUpPositions, cameraVisibleBounds, yDown);
    }

    private void renderTileMapMinimap(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                      FontManager fontManager, TileMap tileMap,
                                      Vector2D playerPos, List<Vector2D> enemyPositions,
                                      List<Vector2D> bossPositions, List<Vector2D> powerUpPositions,
                                      Rectangle cameraVisibleBounds, boolean yDown) {
        Rectangle bounds = getMinimapBounds(yDown);
        Rectangle labelBounds = getMinimapLabelBounds(yDown);
        final float worldW = (tileMap != null) ? tileMap.getWorldWidth() : 4096.0f;
        final float worldH = (tileMap != null) ? tileMap.getWorldHeight() : 4096.0f;
        final float scaleX = bounds.width / worldW;
        final float scaleY = bounds.height / worldH;

        if (shapeRenderer != null) {
            // 1. Background fill
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(COLOR_MINIMAP_BG);
            shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);

            // External label backing strictly above minimap
            shapeRenderer.setColor(COLOR_LABEL_BG);
            shapeRenderer.rect(labelBounds.x, labelBounds.y, labelBounds.width, labelBounds.height);

            // 2. Subsampled terrain tiles
            if (tileMap != null) {
                final int mapW = tileMap.getWidth();
                final int mapH = tileMap.getHeight();
                final float tileSize = tileMap.getTileSize();
                final float cellW = bounds.width / ((float) mapW / SUBSAMPLING_STEP);
                final float cellH = bounds.height / ((float) mapH / SUBSAMPLING_STEP);

                for (int ty = 0; ty < mapH; ty += SUBSAMPLING_STEP) {
                    for (int tx = 0; tx < mapW; tx += SUBSAMPLING_STEP) {
                        TileType t = tileMap.tileAt(tx, ty);
                        Color tileColor = null;

                        if (t == TileType.WATER) {
                            tileColor = COLOR_TILE_WATER;
                        } else if (t == TileType.DIRT || t == TileType.BRIDGE_H || t == TileType.BRIDGE_V) {
                            tileColor = COLOR_TILE_DIRT;
                        } else if (t == TileType.STONE) {
                            tileColor = COLOR_TILE_STONE;
                        }

                        if (tileColor != null) {
                            shapeRenderer.setColor(tileColor);
                            float tileScreenX = bounds.x + tx * tileSize * scaleX;
                            float tileScreenY = yDown
                                    ? bounds.y + ty * tileSize * scaleY
                                    : bounds.y + (mapH - ty - SUBSAMPLING_STEP) * tileSize * scaleY;
                            shapeRenderer.rect(tileScreenX, tileScreenY, cellW, cellH);
                        }
                    }
                }
            }

            // 3. Enemy dots
            if (enemyPositions != null) {
                shapeRenderer.setColor(COLOR_ENEMY_BLIP);
                for (Vector2D ePos : enemyPositions) {
                    if (ePos != null) {
                        float ex = projectX(bounds, ePos.x, scaleX);
                        float ey = projectY(bounds, ePos.y, worldH, scaleY, yDown);
                        shapeRenderer.circle(ex, ey, 2.0f);
                    }
                }
            }

            // 4. Power-up markers
            if (powerUpPositions != null) {
                shapeRenderer.setColor(COLOR_POWERUP_MARKER);
                for (Vector2D pPos : powerUpPositions) {
                    if (pPos != null) {
                        float px = projectX(bounds, pPos.x, scaleX);
                        float py = projectY(bounds, pPos.y, worldH, scaleY, yDown);
                        shapeRenderer.rect(px - 2.5f, py - 2.5f, 5.0f, 5.0f);
                    }
                }
            }

            // 5. Boss markers
            if (bossPositions != null) {
                for (Vector2D bPos : bossPositions) {
                    if (bPos != null) {
                        float bx = projectX(bounds, bPos.x, scaleX);
                        float by = projectY(bounds, bPos.y, worldH, scaleY, yDown);
                        shapeRenderer.setColor(COLOR_BOSS_MARKER);
                        shapeRenderer.circle(bx, by, 4.5f);
                        shapeRenderer.setColor(COLOR_ENEMY_BLIP);
                        shapeRenderer.circle(bx, by, 2.5f);
                    }
                }
            }

            // 6. Player marker
            if (playerPos != null) {
                float px = projectX(bounds, playerPos.x, scaleX);
                float py = projectY(bounds, playerPos.y, worldH, scaleY, yDown);
                shapeRenderer.setColor(COLOR_PLAYER_MARKER);
                shapeRenderer.circle(px, py, 3.5f);
            }

            shapeRenderer.end();

            // 7. Camera viewport rectangle + outer border frames
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            if (cameraVisibleBounds != null) {
                Rectangle vpRect = computeViewportRectOnMinimap(cameraVisibleBounds, worldW, worldH, yDown);
                if (vpRect.width > 0.0f && vpRect.height > 0.0f) {
                    shapeRenderer.setColor(COLOR_VIEWPORT_RECT);
                    shapeRenderer.rect(vpRect.x, vpRect.y, vpRect.width, vpRect.height);
                }
            }

            shapeRenderer.setColor(COLOR_MINIMAP_BORDER);
            shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            shapeRenderer.rect(labelBounds.x, labelBounds.y, labelBounds.width, labelBounds.height);
            shapeRenderer.end();
        }

        // 8. Label banner strictly outside/above minimap box
        if (batch != null && fontManager != null) {
            BitmapFont font = (fontManager.fontSmall != null) ? fontManager.fontSmall : fontManager.fontRegular;
            if (font != null) {
                batch.begin();
                font.setColor(COLOR_LABEL);
                String label = "MAP [OVERWORLD]";
                glyphLayout.setText(font, label);
                float labelX = labelBounds.x + (labelBounds.width - glyphLayout.width) * 0.5f;
                float labelY = yDown
                        ? (labelBounds.y + (labelBounds.height - glyphLayout.height) * 0.5f)
                        : (labelBounds.y + (labelBounds.height + glyphLayout.height) * 0.5f);
                font.draw(batch, label, labelX, labelY);
                batch.end();
            }
        }
    }

    private void renderRadarMinimap(ShapeRenderer shapeRenderer, SpriteBatch batch,
                                    FontManager fontManager, WorldGrid worldGrid,
                                    Vector2D playerPos, List<Vector2D> enemyPositions,
                                    List<Vector2D> bossPositions, List<Vector2D> powerUpPositions,
                                    Rectangle cameraVisibleBounds, boolean yDown) {
        Rectangle bounds = getMinimapBounds(yDown);
        Rectangle labelBounds = getMinimapLabelBounds(yDown);

        final float radarSpan = DEFAULT_RADAR_SPAN;
        final float halfSpan = radarSpan * 0.5f;
        final float scaleX = bounds.width / radarSpan;
        final float scaleY = bounds.height / radarSpan;

        final float centerWorldX = playerPos != null ? playerPos.x : 0.0f;
        final float centerWorldY = playerPos != null ? playerPos.y : 0.0f;
        final float centerMinimapX = bounds.x + bounds.width * 0.5f;
        final float centerMinimapY = bounds.y + bounds.height * 0.5f;

        if (shapeRenderer != null) {
            // 1. Background fill
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(COLOR_MINIMAP_BG);
            shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            shapeRenderer.setColor(COLOR_LABEL_BG);
            shapeRenderer.rect(labelBounds.x, labelBounds.y, labelBounds.width, labelBounds.height);

            // 2. Subsampled dynamic radar terrain cells (32x32)
            final int steps = 32;
            final float cellW = bounds.width / (float) steps;
            final float cellH = bounds.height / (float) steps;
            final float worldStep = radarSpan / (float) steps;

            for (int iy = 0; iy < steps; iy++) {
                float sampleWy = (centerWorldY - halfSpan) + (iy + 0.5f) * worldStep;
                int ty = worldGrid.worldToTileY(sampleWy);
                float tileScreenY = yDown
                        ? bounds.y + iy * cellH
                        : bounds.y + (steps - 1 - iy) * cellH;

                for (int ix = 0; ix < steps; ix++) {
                    float sampleWx = (centerWorldX - halfSpan) + (ix + 0.5f) * worldStep;
                    int tx = worldGrid.worldToTileX(sampleWx);
                    TileType t = worldGrid.getTile(tx, ty);

                    Color tileColor = null;
                    if (t == TileType.WATER) {
                        tileColor = COLOR_TILE_WATER;
                    } else if (t == TileType.DIRT || t == TileType.BRIDGE_H || t == TileType.BRIDGE_V) {
                        tileColor = COLOR_TILE_DIRT;
                    } else if (t == TileType.STONE) {
                        tileColor = COLOR_TILE_STONE;
                    } else if (t == TileType.SAND) {
                        tileColor = COLOR_TILE_SAND;
                    } else if (t == TileType.GRASS) {
                        tileColor = COLOR_TILE_GRASS;
                    }

                    if (tileColor != null) {
                        shapeRenderer.setColor(tileColor);
                        float tileScreenX = bounds.x + ix * cellW;
                        shapeRenderer.rect(tileScreenX, tileScreenY, cellW, cellH);
                    }
                }
            }

            // 3. Enemy blips
            if (enemyPositions != null) {
                shapeRenderer.setColor(COLOR_ENEMY_BLIP);
                for (int i = 0; i < enemyPositions.size(); i++) {
                    Vector2D ePos = enemyPositions.get(i);
                    if (ePos == null) continue;
                    float dx = ePos.x - centerWorldX;
                    float dy = ePos.y - centerWorldY;
                    if (Math.abs(dx) <= halfSpan && Math.abs(dy) <= halfSpan) {
                        float ex = centerMinimapX + dx * scaleX;
                        float ey = yDown ? (centerMinimapY + dy * scaleY) : (centerMinimapY - dy * scaleY);
                        shapeRenderer.circle(ex, ey, 2.0f);
                    }
                }
            }

            // 4. Power-up markers
            if (powerUpPositions != null) {
                shapeRenderer.setColor(COLOR_POWERUP_MARKER);
                for (int i = 0; i < powerUpPositions.size(); i++) {
                    Vector2D pPos = powerUpPositions.get(i);
                    if (pPos == null) continue;
                    float dx = pPos.x - centerWorldX;
                    float dy = pPos.y - centerWorldY;
                    if (Math.abs(dx) <= halfSpan && Math.abs(dy) <= halfSpan) {
                        float px = centerMinimapX + dx * scaleX;
                        float py = yDown ? (centerMinimapY + dy * scaleY) : (centerMinimapY - dy * scaleY);
                        shapeRenderer.rect(px - 2.5f, py - 2.5f, 5.0f, 5.0f);
                    }
                }
            }

            // 5. Boss markers
            if (bossPositions != null) {
                for (int i = 0; i < bossPositions.size(); i++) {
                    Vector2D bPos = bossPositions.get(i);
                    if (bPos == null) continue;
                    float dx = bPos.x - centerWorldX;
                    float dy = bPos.y - centerWorldY;
                    if (Math.abs(dx) <= halfSpan && Math.abs(dy) <= halfSpan) {
                        float bx = centerMinimapX + dx * scaleX;
                        float by = yDown ? (centerMinimapY + dy * scaleY) : (centerMinimapY - dy * scaleY);
                        shapeRenderer.setColor(COLOR_BOSS_MARKER);
                        shapeRenderer.circle(bx, by, 4.5f);
                        shapeRenderer.setColor(COLOR_ENEMY_BLIP);
                        shapeRenderer.circle(bx, by, 2.5f);
                    }
                }
            }

            // 6. Player marker at exact radar center
            shapeRenderer.setColor(COLOR_PLAYER_MARKER);
            shapeRenderer.circle(centerMinimapX, centerMinimapY, 3.5f);
            shapeRenderer.setColor(Color.WHITE);
            shapeRenderer.circle(centerMinimapX, centerMinimapY, 1.5f);

            shapeRenderer.end();

            // 7. Camera viewport box
            shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
            if (cameraVisibleBounds != null) {
                float camCenterX = cameraVisibleBounds.x + cameraVisibleBounds.width * 0.5f;
                float camCenterY = cameraVisibleBounds.y + cameraVisibleBounds.height * 0.5f;
                float dx = camCenterX - centerWorldX;
                float dy = camCenterY - centerWorldY;
                float vpW = cameraVisibleBounds.width * scaleX;
                float vpH = cameraVisibleBounds.height * scaleY;
                float vpx = centerMinimapX + dx * scaleX - vpW * 0.5f;
                float vpy = yDown
                        ? (centerMinimapY + dy * scaleY - vpH * 0.5f)
                        : (centerMinimapY - dy * scaleY - vpH * 0.5f);

                float left = MathUtils.clamp(vpx, bounds.x, bounds.x + bounds.width);
                float right = MathUtils.clamp(vpx + vpW, bounds.x, bounds.x + bounds.width);
                float top = MathUtils.clamp(vpy, bounds.y, bounds.y + bounds.height);
                float bottom = MathUtils.clamp(vpy + vpH, bounds.y, bounds.y + bounds.height);
                if (right > left && bottom > top) {
                    shapeRenderer.setColor(COLOR_VIEWPORT_RECT);
                    shapeRenderer.rect(left, top, right - left, bottom - top);
                }
            }

            // Minimap border frames
            shapeRenderer.setColor(COLOR_MINIMAP_BORDER);
            shapeRenderer.rect(bounds.x, bounds.y, bounds.width, bounds.height);
            shapeRenderer.rect(labelBounds.x, labelBounds.y, labelBounds.width, labelBounds.height);
            shapeRenderer.end();
        }

        // 8. Label text banner
        if (batch != null && fontManager != null) {
            BitmapFont font = (fontManager.fontSmall != null) ? fontManager.fontSmall : fontManager.fontRegular;
            if (font != null) {
                batch.begin();
                font.setColor(COLOR_LABEL);
                String label = "RADAR [INFINITE]";
                glyphLayout.setText(font, label);
                float labelX = labelBounds.x + (labelBounds.width - glyphLayout.width) * 0.5f;
                float labelY = yDown
                        ? (labelBounds.y + (labelBounds.height - glyphLayout.height) * 0.5f)
                        : (labelBounds.y + (labelBounds.height + glyphLayout.height) * 0.5f);
                font.draw(batch, label, labelX, labelY);
                batch.end();
            }
        }
    }

    private static float projectX(Rectangle bounds, float worldX, float scaleX) {
        return MathUtils.clamp(bounds.x + worldX * scaleX, bounds.x, bounds.x + bounds.width);
    }

    private static float projectY(Rectangle bounds, float worldY, float worldH, float scaleY, boolean yDown) {
        float raw = yDown ? (bounds.y + worldY * scaleY) : (bounds.y + (worldH - worldY) * scaleY);
        return MathUtils.clamp(raw, bounds.y, bounds.y + bounds.height);
    }
}
