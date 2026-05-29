package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.github.elderpath_crusade.abilities.Ability;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.GameContext;

import java.util.HashMap;
import java.util.Map;

public class PieceSyncSystem extends EntitySystem {

    private final Map<String, Entity> entityMap = new HashMap<>();

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        TypedEventBus bus = TypedEventBus.get();
        bus.register(PieceSpawnedEvent.class, this::onSpawn);
        bus.register(PieceMovedEvent.class, this::onMove);
        bus.register(PieceDiedEvent.class, this::onDeath);
    }

    private void onSpawn(PieceSpawnedEvent event) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return;

        GamePiece gp = board.getGamePieceAtPos(event.row(), event.col());
        if (!(gp instanceof MonsterGamePiece piece)) return;

        Entity entity = getEngine().createEntity();
        entity.add(new IdentityComponent().set(event.pieceId(), piece.getType().name()));
        entity.add(new AlignmentComponent().set(event.owner()));
        entity.add(new PositionComponent().set(event.row(), event.col()));
        entity.add(new StatsComponent().set(
                piece.getStats().getCost(),
                piece.getStats().getMaxHealth(),
                piece.getStats().getDamage(),
                piece.getStats().getSpeed(),
                piece.getStats().getActions()
        ));
        entity.add(new SpriteComponent().set(piece.getType().name()));

        AbilityComponent ac = new AbilityComponent();
        for (Ability a : piece.getAbilities()) {
            ac.add(a.getName());
        }
        entity.add(ac);

        getEngine().addEntity(entity);
        entityMap.put(event.pieceId(), entity);
    }

    private void onMove(PieceMovedEvent event) {
        Entity entity = entityMap.get(event.pieceId());
        if (entity == null) return;
        entity.getComponent(PositionComponent.class).set(event.toRow(), event.toCol());
    }

    private void onDeath(PieceDiedEvent event) {
        Entity entity = entityMap.remove(event.pieceId());
        if (entity == null) return;
        getEngine().removeEntity(entity);
    }
}
