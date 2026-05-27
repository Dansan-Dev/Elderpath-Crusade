package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Emitted when a piece moves on the board.
 * @param movementType ACTIVE (costs action) or FORCED (ability-driven, free)
 */
public record PieceMovedEvent(
        String pieceId,
        PieceAlignment owner,
        int fromRow, int fromCol,
        int toRow, int toCol,
        MovementType movementType,
        String cause,
        String abilityName
) implements GameEvent {

    public enum MovementType { ACTIVE, FORCED }

    public PieceMovedEvent(String pieceId, PieceAlignment owner, int fromRow, int fromCol, int toRow, int toCol, MovementType movementType, String cause) {
        this(pieceId, owner, fromRow, fromCol, toRow, toCol, movementType, cause, null);
    }
}
