package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.enums.GamePieceData;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class for movement operations.
 * Handles both active movement (spends action) and forced movement (no action cost).
 */
public final class MovementUtils {
    private MovementUtils() {}

    /**
     * Performs active movement (spends an action).
     * The piece moves from one position to another, spending 1 action.
     *
     * @param board The board where movement occurs
     * @param piece The piece to move
     * @param fromRow Source row
     * @param fromCol Source column
     * @param toRow Destination row
     * @param toCol Destination column
     * @param abilityName Name of the ability causing the movement (null for base movement)
     * @return true if movement was successful, false otherwise
     */
    public static boolean performActiveMovement(
            Board board,
            MonsterGamePiece piece,
            int fromRow,
            int fromCol,
            int toRow,
            int toCol,
            String abilityName
    ) {
        if (board == null || piece == null) return false;
        if (fromRow == toRow && fromCol == toCol) return false;
        if (board.isOccupied(toRow, toCol)) return false;

        // Perform the move
        board.moveGamePiece(fromRow, fromCol, toRow, toCol);
        piece.updateData(GamePieceData.POSITION, new Board.Position(board, toRow, toCol));

        // Mark cause as MANUAL for abilities that react differently to manual vs ability-driven moves
        piece.updateData(GamePieceData.MOVE_CAUSE, "MANUAL");

        // Ability notification for movement
        try { piece.notifyMoved(fromRow, fromCol, toRow, toCol); } catch (Exception ignored) {}

        // Emit ACTIVE_MOVEMENT event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("pieceId", piece.getId().toString());
        eventData.put("owner", piece.getAlignment().name());
        eventData.put("fromRow", fromRow);
        eventData.put("fromCol", fromCol);
        eventData.put("toRow", toRow);
        eventData.put("toCol", toCol);
        eventData.put("cause", "MANUAL");
        if (abilityName != null) {
            eventData.put("ability", abilityName);
        }
        EventBus.emit(GameEventType.ACTIVE_MOVEMENT, eventData);

        // Spend 1 action
        AbilityUtils.spendAction(piece);

        return true;
    }

    /**
     * Convenience method for base active movement (no ability).
     */
    public static boolean performActiveMovement(
            Board board,
            MonsterGamePiece piece,
            int fromRow,
            int fromCol,
            int toRow,
            int toCol
    ) {
        return performActiveMovement(board, piece, fromRow, fromCol, toRow, toCol, null);
    }

    /**
     * Performs forced movement (does not spend an action).
     * Used by abilities like DisplaceAbility.
     *
     * @param board The board where movement occurs
     * @param piece The piece to move
     * @param fromRow Source row
     * @param fromCol Source column
     * @param toRow Destination row
     * @param toCol Destination column
     * @param cause Cause of the forced movement (e.g., "ABILITY", "PUSH", etc.)
     * @param abilityName Name of the ability causing the movement (null if not ability-driven)
     * @return true if movement was successful, false otherwise
     */
    public static boolean performForcedMovement(
            Board board,
            MonsterGamePiece piece,
            int fromRow,
            int fromCol,
            int toRow,
            int toCol,
            String cause,
            String abilityName
    ) {
        if (board == null || piece == null) return false;
        if (fromRow == toRow && fromCol == toCol) return false;
        if (board.isOccupied(toRow, toCol)) return false;

        // Perform the move
        board.moveGamePiece(fromRow, fromCol, toRow, toCol);
        piece.updateData(GamePieceData.POSITION, new Board.Position(board, toRow, toCol));

        // Mark cause
        piece.updateData(GamePieceData.MOVE_CAUSE, cause != null ? cause : "ABILITY");

        // Ability notification for movement
        try { piece.notifyMoved(fromRow, fromCol, toRow, toCol); } catch (Exception ignored) {}

        // Emit FORCED_MOVEMENT event
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("pieceId", piece.getId().toString());
        eventData.put("owner", piece.getAlignment().name());
        eventData.put("fromRow", fromRow);
        eventData.put("fromCol", fromCol);
        eventData.put("toRow", toRow);
        eventData.put("toCol", toCol);
        eventData.put("cause", cause != null ? cause : "ABILITY");
        if (abilityName != null) {
            eventData.put("ability", abilityName);
        }
        EventBus.emit(GameEventType.FORCED_MOVEMENT, eventData);

        return true;
    }

    /**
     * Performs a swap between two pieces (atomic operation).
     * Both pieces move to each other's positions simultaneously.
     * Used by abilities like SwapOnAttackAbility.
     *
     * @param board The board where the swap occurs
     * @param piece1 First piece to swap
     * @param piece1Row Row of first piece
     * @param piece1Col Column of first piece
     * @param piece2 Second piece to swap
     * @param piece2Row Row of second piece
     * @param piece2Col Column of second piece
     * @param cause Cause of the forced movement (e.g., "ABILITY", "SWAP", etc.)
     * @param abilityName Name of the ability causing the swap (null if not ability-driven)
     * @return true if swap was successful, false otherwise
     */
    public static boolean performSwap(
            Board board,
            MonsterGamePiece piece1,
            int piece1Row,
            int piece1Col,
            MonsterGamePiece piece2,
            int piece2Row,
            int piece2Col,
            String cause,
            String abilityName
    ) {
        if (board == null || piece1 == null || piece2 == null) return false;
        if (piece1 == piece2) return false;
        if (piece1Row == piece2Row && piece1Col == piece2Col) return false;

        // Verify both pieces are at their expected positions
        if (board.getGamePieceAtPos(piece1Row, piece1Col) != piece1) return false;
        if (board.getGamePieceAtPos(piece2Row, piece2Col) != piece2) return false;

        // Perform the swap atomically by directly manipulating board positions
        // First, clear both positions
        board.setGamePiecePos(piece1Row, piece1Col, null);
        board.setGamePiecePos(piece2Row, piece2Col, null);
        // Then place pieces at swapped positions
        board.setGamePiecePos(piece2Row, piece2Col, piece1);
        board.setGamePiecePos(piece1Row, piece1Col, piece2);

        // Update position data for both pieces
        piece1.updateData(GamePieceData.POSITION, new Board.Position(board, piece2Row, piece2Col));
        piece2.updateData(GamePieceData.POSITION, new Board.Position(board, piece1Row, piece1Col));

        // Mark cause
        String swapCause = cause != null ? cause : "ABILITY";
        piece1.updateData(GamePieceData.MOVE_CAUSE, swapCause);
        piece2.updateData(GamePieceData.MOVE_CAUSE, swapCause);

        // Ability notifications for both pieces
        try { piece1.notifyMoved(piece1Row, piece1Col, piece2Row, piece2Col); } catch (Exception ignored) {}
        try { piece2.notifyMoved(piece2Row, piece2Col, piece1Row, piece1Col); } catch (Exception ignored) {}

        // Emit FORCED_MOVEMENT events for both pieces
        Map<String, Object> eventData1 = new HashMap<>();
        eventData1.put("pieceId", piece1.getId().toString());
        eventData1.put("owner", piece1.getAlignment().name());
        eventData1.put("fromRow", piece1Row);
        eventData1.put("fromCol", piece1Col);
        eventData1.put("toRow", piece2Row);
        eventData1.put("toCol", piece2Col);
        eventData1.put("cause", swapCause);
        if (abilityName != null) {
            eventData1.put("ability", abilityName);
        }
        EventBus.emit(GameEventType.FORCED_MOVEMENT, eventData1);

        Map<String, Object> eventData2 = new HashMap<>();
        eventData2.put("pieceId", piece2.getId().toString());
        eventData2.put("owner", piece2.getAlignment().name());
        eventData2.put("fromRow", piece2Row);
        eventData2.put("fromCol", piece2Col);
        eventData2.put("toRow", piece1Row);
        eventData2.put("toCol", piece1Col);
        eventData2.put("cause", swapCause);
        if (abilityName != null) {
            eventData2.put("ability", abilityName);
        }
        EventBus.emit(GameEventType.FORCED_MOVEMENT, eventData2);

        return true;
    }
}

