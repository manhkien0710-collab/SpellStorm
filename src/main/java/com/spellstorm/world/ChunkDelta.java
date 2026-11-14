package com.spellstorm.world;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable snapshot and tracker of mutable gameplay modifications applied to a procedural chunk.
 * Memory-efficient: records sparse deltas (tile changes, removed obstacles, persistent pickups/entities).
 */
public class ChunkDelta implements Serializable {

    private static final long serialVersionUID = 1L;

    public record SavedPickup(float localX, float localY, String pickupType, int value) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    public record SavedEntityState(String entityId, float localX, float localY, float health, String stateData) implements Serializable {
        private static final long serialVersionUID = 1L;
    }

    private final long chunkKey;
    private final Map<Short, Byte> tileModifications;
    private final BitSet removedObstacleIndices;
    private final List<SavedPickup> persistentPickups;
    private final List<SavedEntityState> persistentEntities;

    public ChunkDelta(long chunkKey) {
        this.chunkKey = chunkKey;
        this.tileModifications = new HashMap<>(4);
        this.removedObstacleIndices = new BitSet();
        this.persistentPickups = new ArrayList<>(2);
        this.persistentEntities = new ArrayList<>(1);
    }

    public long getChunkKey() {
        return chunkKey;
    }

    public boolean isEmpty() {
        return tileModifications.isEmpty()
            && removedObstacleIndices.isEmpty()
            && persistentPickups.isEmpty()
            && persistentEntities.isEmpty();
    }

    public void recordTileChange(int localX, int localY, byte newTileTypeId) {
        short idx = (short) (localY * 32 + localX);
        tileModifications.put(idx, newTileTypeId);
    }

    public void recordObstacleRemoved(int obstacleIndex) {
        removedObstacleIndices.set(obstacleIndex);
    }

    public void addPersistentPickup(SavedPickup pickup) {
        persistentPickups.add(pickup);
    }

    public void addPersistentEntity(SavedEntityState entityState) {
        persistentEntities.add(entityState);
    }

    public Map<Short, Byte> getTileModifications() {
        return Collections.unmodifiableMap(tileModifications);
    }

    public boolean isObstacleRemoved(int obstacleIndex) {
        return removedObstacleIndices.get(obstacleIndex);
    }

    public List<SavedPickup> getPersistentPickups() {
        return Collections.unmodifiableList(persistentPickups);
    }

    public List<SavedEntityState> getPersistentEntities() {
        return Collections.unmodifiableList(persistentEntities);
    }
}
