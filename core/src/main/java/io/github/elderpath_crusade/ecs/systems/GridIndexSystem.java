package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a spatial index of entities by grid position.
 * Provides O(1) lookup: "what entity is at (row, col)?"
 */
public class GridIndexSystem extends EntitySystem {

    private final Map<Long, Entity> grid = new HashMap<>();

    @Override
    public void addedToEngine(Engine engine) {
        // No event listeners — PieceSyncSystem notifies directly
    }

    @Override
    public void update(float deltaTime) {
        // Event-driven; no per-frame work
    }

    public void onEntitySpawned(Entity entity, int row, int col) {
        grid.put(key(row, col), entity);
    }

    public void onEntityMoved(int fromRow, int fromCol, Entity entity, int toRow, int toCol) {
        grid.remove(key(fromRow, fromCol));
        grid.put(key(toRow, toCol), entity);
    }

    public void onEntityDied(int row, int col) {
        grid.remove(key(row, col));
    }

    public Entity getEntityAt(int row, int col) {
        return grid.get(key(row, col));
    }

    public boolean isOccupied(int row, int col) {
        return grid.containsKey(key(row, col));
    }

    public void clear() {
        grid.clear();
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }
}
