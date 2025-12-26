package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.game_piece.GamePiece;
import io.github.elderpath_crusade.enums.GamePieceData;

/**
 * Minimal helper context with common ability utilities. Static-only, no retained state.
 */
public final class AbilityContext {
    private AbilityContext() {}

    /** Fetch the owner's board position (if any). */
    public static Board.Position getOwnerPos(GamePiece owner) {
        Object posObj = owner.getData(GamePieceData.POSITION);
        if (posObj instanceof Board.Position pos) return pos;
        return null;
    }

    /** Fetch the board that the owner is currently on (if any). */
    public static Board getOwnerBoard(GamePiece owner) {
        Board.Position pos = getOwnerPos(owner);
        return (pos == null ? null : pos.getBoard());
    }
}
