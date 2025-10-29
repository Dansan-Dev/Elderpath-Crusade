package io.github.forest_of_dreams.abilities;

import io.github.forest_of_dreams.game_objects.board.Board;
import io.github.forest_of_dreams.game_objects.board.GamePiece;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.enums.GamePieceData;
import java.util.*;

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
