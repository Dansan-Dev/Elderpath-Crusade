package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
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
        TypedEventBus bus = TypedEventBus.get();
        bus.register(PieceSpawnedEvent.class, this::onSpawn);
        bus.register(PieceMovedEvent.class, this::onMove);
        bus.register(PieceDiedEvent.class, this::onDeath);
    }

    @Override
    public void update(float deltaTime) {
        // Event-driven; no per-frame work
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

    private void onSpawn(PieceSpawnedEvent event) {
        // PieceSyncSystem creates the entity first (registered before us),
        // so we can find it via PieceSyncSystem
        PieceSyncSystem sync = getEngine().getSystem(PieceSyncSystem.class);
        if (sync == null) return;
        Entity entity = sync.getEntity(event.pieceId());
        if (entity == null) return;
        grid.put(key(event.row(), event.col()), entity);
    }

    private void onMove(PieceMovedEvent event) {
        grid.remove(key(event.fromRow(), event.fromCol()));
        PieceSyncSystem sync = getEngine().getSystem(PieceSyncSystem.class);
        if (sync == null) return;
        Entity entity = sync.getEntity(event.pieceId());
        if (entity != null) {
            grid.put(key(event.toRow(), event.toCol()), entity);
        }
    }

    private void onDeath(PieceDiedEvent event) {
        grid.remove(key(event.row(), event.col()));
    }

    private static long key(int row, int col) {
        return ((long) row << 32) | (col & 0xFFFFFFFFL);
    }
}
