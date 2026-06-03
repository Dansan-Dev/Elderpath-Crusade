package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;

import java.util.ArrayList;
import java.util.List;

/**
 * Processes deaths: any entity with currentHealth <= 0 is removed from board and engine.
 */
public class DeathSystem extends EntitySystem {

    private Family family;

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(StatsComponent.class).get();
    }

    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);
        List<Entity> dead = null;

        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            StatsComponent stats = entity.getComponent(StatsComponent.class);
            if (stats.currentHealth <= 0) {
                if (dead == null) dead = new ArrayList<>();
                dead.add(entity);
            }
        }

        if (dead == null) return;

        Board board = GameContext.get().getActiveBoard();
        for (Entity entity : dead) {
            SpriteComponent sprite = entity.getComponent(SpriteComponent.class);
            if (sprite != null && sprite.piece != null) {
                sprite.piece.detachAllAbilities();
            }

            PositionComponent pos = entity.getComponent(PositionComponent.class);
            IdentityComponent id = entity.getComponent(IdentityComponent.class);
            String pieceId = (id != null) ? id.id : "";
            int row = (pos != null) ? pos.row : 0;
            int col = (pos != null) ? pos.col : 0;

            TypedEventBus.get().emit(new PieceDiedEvent(pieceId, row, col));

            if (board != null && pos != null) {
                board.removeGamePieceAtPos(row, col);
            }

            if (sprite != null && sprite.piece != null) {
                sprite.piece.setEntity(null);
            }

            getEngine().removeEntity(entity);
        }
    }
}
