package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.systems.MovementSystem;
import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.utils.AbilityUtils;

/**
 * Utility class for movement operations.
 * Routes through MovementSystem for ECS-backed movement.
 */
public final class MovementUtils {
    private MovementUtils() {}

    public static boolean performActiveMovement(
            Board board, MonsterGamePiece piece,
            int fromRow, int fromCol, int toRow, int toCol, String abilityName) {
        if (board == null || piece == null) return false;
        if (fromRow == toRow && fromCol == toCol) return false;
        if (piece.getEntity() == null) return false;

        MovementSystem ms = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
        if (ms == null) return false;
        if (!ms.executeMove(piece.getEntity(), toRow, toCol)) return false;

        try { piece.notifyMoved(fromRow, fromCol, toRow, toCol); } catch (Exception ignored) {}
        AbilityUtils.spendAction(piece);
        return true;
    }

    public static boolean performActiveMovement(
            Board board, MonsterGamePiece piece,
            int fromRow, int fromCol, int toRow, int toCol) {
        return performActiveMovement(board, piece, fromRow, fromCol, toRow, toCol, null);
    }

    public static boolean performForcedMovement(
            Board board, MonsterGamePiece piece,
            int fromRow, int fromCol, int toRow, int toCol,
            String cause, String abilityName) {
        if (board == null || piece == null) return false;
        if (fromRow == toRow && fromCol == toCol) return false;
        if (piece.getEntity() == null) return false;

        MovementSystem ms = GameContext.get().getEcsEngine().getSystem(MovementSystem.class);
        if (ms == null) return false;
        if (!ms.executeForcedMove(piece.getEntity(), toRow, toCol, cause, abilityName)) return false;

        try { piece.notifyMoved(fromRow, fromCol, toRow, toCol); } catch (Exception ignored) {}
        return true;
    }

    public static boolean performSwap(
            Board board,
            MonsterGamePiece piece1, int piece1Row, int piece1Col,
            MonsterGamePiece piece2, int piece2Row, int piece2Col,
            String cause, String abilityName) {
        if (board == null || piece1 == null || piece2 == null) return false;
        if (piece1 == piece2) return false;
        if (piece1Row == piece2Row && piece1Col == piece2Col) return false;

        // Swap via Board (which updates GridIndexSystem)
        board.setGamePiecePos(piece1Row, piece1Col, null);
        board.setGamePiecePos(piece2Row, piece2Col, null);
        board.setGamePiecePos(piece2Row, piece2Col, piece1);
        board.setGamePiecePos(piece1Row, piece1Col, piece2);

        // Update ECS positions
        if (piece1.getEntity() != null) {
            piece1.getEntity().getComponent(io.github.elderpath_crusade.ecs.components.PositionComponent.class)
                    .set(piece2Row, piece2Col);
        }
        if (piece2.getEntity() != null) {
            piece2.getEntity().getComponent(io.github.elderpath_crusade.ecs.components.PositionComponent.class)
                    .set(piece1Row, piece1Col);
        }

        String swapCause = cause != null ? cause : "ABILITY";

        try { piece1.notifyMoved(piece1Row, piece1Col, piece2Row, piece2Col); } catch (Exception ignored) {}
        try { piece2.notifyMoved(piece2Row, piece2Col, piece1Row, piece1Col); } catch (Exception ignored) {}

        TypedEventBus.get().emit(new PieceMovedEvent(
                piece1.getId().toString(), piece1.getAlignment(),
                piece1Row, piece1Col, piece2Row, piece2Col,
                PieceMovedEvent.MovementType.FORCED, swapCause, abilityName));
        TypedEventBus.get().emit(new PieceMovedEvent(
                piece2.getId().toString(), piece2.getAlignment(),
                piece2Row, piece2Col, piece1Row, piece1Col,
                PieceMovedEvent.MovementType.FORCED, swapCause, abilityName));

        return true;
    }
}
