package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.board.Board;

/**
 * Centralized manager for tracking the active Board.
 * Delegates to GameContext. Static API preserved for backward compatibility.
 */
public final class BoardManager {
    private BoardManager() {}

    public static void setBoard(Board board) {
        GameContext ctx = GameContext.get();
        if (ctx != null) ctx.setActiveBoard(board);
    }

    public static Board getBoard() {
        GameContext ctx = GameContext.get();
        return ctx != null ? ctx.getActiveBoard() : null;
    }

    public static void clear() {
        GameContext ctx = GameContext.get();
        if (ctx != null) ctx.clearBoard();
    }
}
