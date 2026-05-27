package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.utils.AbilityUtils;

/**
 * Utility class for movement operations.
 * Handles both active movement (spends action) and forced movement (no action cost).
 */
public final class MovementUtils {
    private MovementUtils() {}

    public static boolean performActiveMovement(
            Board board, MonsterGamePiece piece,
            int fromRow, int fromCol, int toRow, int toCol, String abilityName) {
        if (board == null || piece == null) return false;
        if (fromRow == toRow && fromCol == toCol) return false;
        if (board.isOccupied(toRow, toCol)) return false;

        board.moveGamePiece(fromRow, fromCol, toRow, toCol);
        piece.updateData(GamePieceData.POSITION, new Board.Position(board, toRow, toCol));
        piece.updateData(GamePieceData.MOVE_CAUSE, "MANUAL");

        try { piece.notifyMoved(fromRow, fromCol, toRow, toCol); } catch (Exception ignored) {}

        TypedEventBus.get().emit(new PieceMovedEvent(
                piece.getId().toString(), piece.getAlignment(),
                fromRow, fromCol, toRow, toCol,
                PieceMovedEvent.MovementType.ACTIVE, "MANUAL", abilityName));

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
        if (board.isOccupied(toRow, toCol)) return false;

        board.moveGamePiece(fromRow, fromCol, toRow, toCol);
        piece.updateData(GamePieceData.POSITION, new Board.Position(board, toRow, toCol));
        piece.updateData(GamePieceData.MOVE_CAUSE, cause != null ? cause : "ABILITY");

        try { piece.notifyMoved(fromRow, fromCol, toRow, toCol); } catch (Exception ignored) {}

        TypedEventBus.get().emit(new PieceMovedEvent(
                piece.getId().toString(), piece.getAlignment(),
                fromRow, fromCol, toRow, toCol,
                PieceMovedEvent.MovementType.FORCED, cause != null ? cause : "ABILITY", abilityName));

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
        if (board.getGamePieceAtPos(piece1Row, piece1Col) != piece1) return false;
        if (board.getGamePieceAtPos(piece2Row, piece2Col) != piece2) return false;

        board.setGamePiecePos(piece1Row, piece1Col, null);
        board.setGamePiecePos(piece2Row, piece2Col, null);
        board.setGamePiecePos(piece2Row, piece2Col, piece1);
        board.setGamePiecePos(piece1Row, piece1Col, piece2);

        piece1.updateData(GamePieceData.POSITION, new Board.Position(board, piece2Row, piece2Col));
        piece2.updateData(GamePieceData.POSITION, new Board.Position(board, piece1Row, piece1Col));

        String swapCause = cause != null ? cause : "ABILITY";
        piece1.updateData(GamePieceData.MOVE_CAUSE, swapCause);
        piece2.updateData(GamePieceData.MOVE_CAUSE, swapCause);

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
