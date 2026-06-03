package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;

import java.util.HashMap;
import java.util.Map;

/**
 * Maintains a spatial index of entities by grid position.
 * Provides O(1) lookup: "what entity/piece is at (row, col)?"
 */
public class GridIndexSystem extends EntitySystem {

    private final Map<Long, Entity> grid = new HashMap<>();
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<SpriteComponent> spriteMapper = ComponentMapper.getFor(SpriteComponent.class);

    @Override
    public void addedToEngine(Engine engine) {
        // No event listeners — PieceSyncSystem notifies directly
    }

    @Override
    public void update(float deltaTime) {
        // Event-driven; no per-frame work
    }

    /**
     * Called by PieceSyncSystem after entity creation.
     */
    public void onEntitySpawned(Entity entity, int row, int col) {
        grid.put(key(row, col), entity);
    }

    /**
     * Called by PieceSyncSystem on piece move.
     */
    public void onEntityMoved(int fromRow, int fromCol, Entity entity, int toRow, int toCol) {
        grid.remove(key(fromRow, fromCol));
        grid.put(key(toRow, toCol), entity);
    }

    /**
     * Called by PieceSyncSystem on piece death.
     */
    public void onEntityDied(int row, int col) {
        grid.remove(key(row, col));
    }

    /**
     * O(1) lookup of the entity at the given position.
     */
    public Entity getEntityAt(int row, int col) {
        return grid.get(key(row, col));
    }

    /**
     * O(1) lookup of the GamePiece at the given position.
     * Returns null if no entity or no piece reference.
     */
    public GamePiece getPieceAt(int row, int col) {
        Entity entity = grid.get(key(row, col));
        if (entity == null) return null;
        SpriteComponent sc = spriteMapper.get(entity);
        return (sc != null) ? sc.piece : null;
    }

    /**
     * Check if a position is occupied.
     */
    public boolean isOccupied(int row, int col) {
        return grid.containsKey(key(row, col));
    }

    /**
     * Clear all entries. Called on session/board reset.
     */
    public void clear() {
        grid.clear();
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }
}
