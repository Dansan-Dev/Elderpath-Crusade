package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;

/**
 * Processes MoveIntentComponent: validates move, updates position,
 * syncs Board write-cache, emits PieceMovedEvent, removes intent.
 */
public class MovementSystem extends EntitySystem {

    private final ComponentMapper<MoveIntentComponent> intentMapper = ComponentMapper.getFor(MoveIntentComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private final ComponentMapper<IdentityComponent> idMapper = ComponentMapper.getFor(IdentityComponent.class);
    private Family family;

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(MoveIntentComponent.class, PositionComponent.class).get();
    }

    @Override
    public void update(float deltaTime) {
        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);
        if (entities.size() == 0) return;

        // Process all pending move intents
        for (int i = entities.size() - 1; i >= 0; i--) {
            Entity entity = entities.get(i);
            processMove(entity);
        }
    }

    private void processMove(Entity entity) {
        MoveIntentComponent intent = intentMapper.get(entity);
        PositionComponent pos = posMapper.get(entity);
        AlignmentComponent align = alignMapper.get(entity);
        IdentityComponent id = idMapper.get(entity);

        int fromRow = pos.row;
        int fromCol = pos.col;
        int toRow = intent.targetRow;
        int toCol = intent.targetCol;

        // Validate
        Board board = GameContext.get().getActiveBoard();
        if (board == null || !isValidMove(board, toRow, toCol)) {
            entity.remove(MoveIntentComponent.class);
            return;
        }

        // Update ECS position
        pos.set(toRow, toCol);

        // Sync Board write-cache
        board.moveGamePiece(fromRow, fromCol, toRow, toCol);

        // Emit event
        PieceAlignment owner = (align != null) ? align.alignment : PieceAlignment.NEUTRAL;
        String pieceId = (id != null) ? id.id : "";
        TypedEventBus.get().emit(new PieceMovedEvent(
                pieceId, owner, fromRow, fromCol, toRow, toCol,
                PieceMovedEvent.MovementType.ACTIVE, "MovementSystem"));

        // Remove intent
        entity.remove(MoveIntentComponent.class);
    }

    private boolean isValidMove(Board board, int toRow, int toCol) {
        if (toRow < 0 || toRow >= board.getROWS()) return false;
        if (toCol < 0 || toCol >= board.getCOLS()) return false;
        return !board.isOccupied(toRow, toCol);
    }
}
