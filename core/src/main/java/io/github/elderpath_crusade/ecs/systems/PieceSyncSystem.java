package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.GameContext;

import java.util.HashMap;
import java.util.Map;

public class PieceSyncSystem extends EntitySystem {

    private final Map<String, Entity> entityMap = new HashMap<>();

    public Entity getEntity(String pieceId) {
        return entityMap.get(pieceId);
    }

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        TypedEventBus bus = TypedEventBus.get();
        bus.register(PieceSpawnedEvent.class, this::onSpawn);
        bus.register(PieceMovedEvent.class, this::onMove);
        bus.register(PieceDiedEvent.class, this::onDeath);
    }

    private void onSpawn(PieceSpawnedEvent event) {
        // Entity already created by PieceFactory before spawn event — just index it
        // Look up existing entity from engine by pieceId
        Entity existing = entityMap.get(event.pieceId());
        if (existing != null) return; // already tracked

        // Find the entity that was just added (by PieceFactory) via GridIndexSystem
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;
        Entity entity = board.getEntityAtPos(event.row(), event.col());
        if (entity == null) return;

        entityMap.put(event.pieceId(), entity);
    }

    private void onMove(PieceMovedEvent event) {
        Entity entity = entityMap.get(event.pieceId());
        if (entity == null) return;
        // PositionComponent already updated by MovementSystem
        // GridIndexSystem already updated by Board.moveEntity
        // Just keep entityMap in sync (no-op, pieceId unchanged)
    }

    private void onDeath(PieceDiedEvent event) {
        // Just remove from our tracking map — DeathSystem handles engine removal and GridIndex
        entityMap.remove(event.pieceId());
    }
}
