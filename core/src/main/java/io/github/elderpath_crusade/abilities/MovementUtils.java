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
}

