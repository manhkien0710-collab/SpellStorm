package com.spellstorm.world;

import com.spellstorm.math.Vector2D;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Centralized, atomic floating-origin manager for SpellStorm infinite world simulation.
 *
 * <p>Maintains 64-bit global origin tracking (originShiftX, originShiftY) while strictly
 * bounding local simulation coordinates within [-3072.0f, +3072.0f] px to preserve
 * sub-pixel float precision (worst-case ULP &le; 1/2048 px) across infinite exploration.
 *
 * <p>Dispatches atomic rebase operations across all registered simulation components,
 * entities, projectiles, pickups, camera state, and active visual effects.
 */
public class FloatingOriginManager {

    public static final float DEFAULT_REBASE_THRESHOLD = 1024.0f; // Exactly 1 chunk (32x32 tiles @ 32px)
    public static final long CHUNK_SPAN_PX = 1024L;

    private long originShiftX = 0L;
    private long originShiftY = 0L;
    private int originChunkX = 0;
    private int originChunkY = 0;

    private float rebaseThreshold = DEFAULT_REBASE_THRESHOLD;
    private final CopyOnWriteArrayList<FloatingOriginAware> listeners = new CopyOnWriteArrayList<>();

    // Telemetry & metrics
    private long totalRebaseCount = 0L;
    private long lastDeltaX = 0L;
    private long lastDeltaY = 0L;

    public FloatingOriginManager() {
        this(0L, 0L, DEFAULT_REBASE_THRESHOLD);
    }

    public FloatingOriginManager(long initialOriginX, long initialOriginY) {
        this(initialOriginX, initialOriginY, DEFAULT_REBASE_THRESHOLD);
    }

    public FloatingOriginManager(long initialOriginX, long initialOriginY, float rebaseThreshold) {
        this.originShiftX = initialOriginX;
        this.originShiftY = initialOriginY;
        this.originChunkX = (int) Math.floorDiv(initialOriginX, CHUNK_SPAN_PX);
        this.originChunkY = (int) Math.floorDiv(initialOriginY, CHUNK_SPAN_PX);
        this.rebaseThreshold = Math.max(256.0f, rebaseThreshold);
    }

    // =========================================================================
    // Threshold & Rebase Evaluation
    // =========================================================================

    public boolean shouldRebase(float localX, float localY) {
        return Math.abs(localX) >= rebaseThreshold || Math.abs(localY) >= rebaseThreshold;
    }

    public boolean shouldRebase(Vector2D localPos) {
        return localPos != null && shouldRebase(localPos.x, localPos.y);
    }

    /**
     * Evaluates local anchor position (e.g. Player position or Camera center) and triggers
     * atomic origin rebasing if displacement exceeds the threshold.
     *
     * @param anchorPos local coordinate of primary focus entity
     * @return true if an origin shift occurred, false otherwise
     */
    public boolean checkAndRebase(Vector2D anchorPos) {
        if (anchorPos == null || !shouldRebase(anchorPos.x, anchorPos.y)) {
            return false;
        }

        int deltaCx = (int) Math.round(anchorPos.x / (float) CHUNK_SPAN_PX);
        int deltaCy = (int) Math.round(anchorPos.y / (float) CHUNK_SPAN_PX);

        long deltaX = (long) deltaCx * CHUNK_SPAN_PX;
        long deltaY = (long) deltaCy * CHUNK_SPAN_PX;

        if (deltaX != 0L || deltaY != 0L) {
            rebaseWorldOrigin(deltaX, deltaY);
            return true;
        }
        return false;
    }

    /**
     * Executes centralized atomic origin rebasing by (deltaX, deltaY).
     * Updates cumulative origin offsets and dispatches to all registered listeners.
     */
    public void rebaseWorldOrigin(long deltaX, long deltaY) {
        if (deltaX == 0L && deltaY == 0L) {
            return;
        }

        this.originShiftX += deltaX;
        this.originShiftY += deltaY;
        this.originChunkX = (int) Math.floorDiv(this.originShiftX, CHUNK_SPAN_PX);
        this.originChunkY = (int) Math.floorDiv(this.originShiftY, CHUNK_SPAN_PX);

        this.totalRebaseCount++;
        this.lastDeltaX = deltaX;
        this.lastDeltaY = deltaY;

        for (FloatingOriginAware listener : listeners) {
            if (listener != null) {
                listener.onOriginShift(deltaX, deltaY);
            }
        }
    }

    // =========================================================================
    // Listener Management
    // =========================================================================

    public void registerListener(FloatingOriginAware listener) {
        if (listener != null) {
            listeners.addIfAbsent(listener);
        }
    }

    public void unregisterListener(FloatingOriginAware listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    public void clearListeners() {
        listeners.clear();
    }

    public List<FloatingOriginAware> getListeners() {
        return Collections.unmodifiableList(listeners);
    }

    // =========================================================================
    // Bidirectional Coordinate Conversions
    // =========================================================================

    public long toGlobalPixelX(float localX) {
        return (long) Math.floor(localX) + originShiftX;
    }

    public long toGlobalPixelY(float localY) {
        return (long) Math.floor(localY) + originShiftY;
    }

    public double toGlobalDoubleX(float localX) {
        return (double) localX + originShiftX;
    }

    public double toGlobalDoubleY(float localY) {
        return (double) localY + originShiftY;
    }

    public float toLocalPixelX(long globalX) {
        return (float) (globalX - originShiftX);
    }

    public float toLocalPixelY(long globalY) {
        return (float) (globalY - originShiftY);
    }

    public int toGlobalChunkX(float localX) {
        return (int) Math.floorDiv((long) Math.floor(localX) + originShiftX, CHUNK_SPAN_PX);
    }

    public int toGlobalChunkY(float localY) {
        return (int) Math.floorDiv((long) Math.floor(localY) + originShiftY, CHUNK_SPAN_PX);
    }

    public int toGlobalTileX(float localX) {
        return (int) Math.floorDiv((long) Math.floor(localX) + originShiftX, (long) TileMap.TILE_SIZE);
    }

    public int toGlobalTileY(float localY) {
        return (int) Math.floorDiv((long) Math.floor(localY) + originShiftY, (long) TileMap.TILE_SIZE);
    }

    // =========================================================================
    // Getters, Setters & Telemetry
    // =========================================================================

    public long getOriginShiftX() { return originShiftX; }
    public long getOriginShiftY() { return originShiftY; }
    public int getOriginChunkX() { return originChunkX; }
    public int getOriginChunkY() { return originChunkY; }
    public float getRebaseThreshold() { return rebaseThreshold; }
    public void setRebaseThreshold(float threshold) { this.rebaseThreshold = Math.max(256.0f, threshold); }
    public long getTotalRebaseCount() { return totalRebaseCount; }
    public long getLastDeltaX() { return lastDeltaX; }
    public long getLastDeltaY() { return lastDeltaY; }

    public void reset() {
        this.originShiftX = 0L;
        this.originShiftY = 0L;
        this.originChunkX = 0;
        this.originChunkY = 0;
        this.totalRebaseCount = 0L;
        this.lastDeltaX = 0L;
        this.lastDeltaY = 0L;
    }
}
