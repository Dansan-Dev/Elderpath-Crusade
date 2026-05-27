package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.rooms.VictoryRoom;
import com.badlogic.gdx.utils.Timer;

/**
 * Centralized win condition watcher.
 * P1 wins when a P1 piece reaches the last row (ROWS-1).
 * P2 wins when a P2 piece reaches row 0.
 */
public final class WinConditionManager {
    private static boolean initialized = false;
    private static boolean gameWon = false;

    public static void reset() { gameWon = false; }

    private WinConditionManager() {}

    public static void initialize() {
        if (initialized) return;
        initialized = true;

        TypedEventBus.get().register(PieceMovedEvent.class, evt -> {
            checkWin(evt.owner(), evt.toRow());
        });
        TypedEventBus.get().register(PieceSpawnedEvent.class, evt -> {
            checkWin(evt.owner(), evt.row());
        });
    }

    private static void checkWin(PieceAlignment alignment, int destRow) {
        if (gameWon) return;
        if (alignment == null || alignment == PieceAlignment.NEUTRAL) return;

        Integer rows = getActiveBoardRows();
        if (rows == null) return;

        Board activeBoard = BoardManager.getBoard();
        if (activeBoard == null) return;

        boolean flipped = activeBoard.isFlipped();
        boolean won = false;
        if (alignment == PieceAlignment.P1) {
            won = flipped ? (destRow == 0) : (destRow == rows - 1);
        } else if (alignment == PieceAlignment.P2) {
            won = flipped ? (destRow == rows - 1) : (destRow == 0);
        }

        if (won) triggerWin(alignment);
    }

    private static Integer getActiveBoardRows() {
        Board board = BoardManager.getBoard();
        return (board != null) ? board.getROWS() : null;
    }

    private static void triggerWin(PieceAlignment winner) {
        if (gameWon) return;
        gameWon = true;
        Logger.log("Win", "VICTORY: " + winner.name());
        try {
            if (InteractionManager.hasActiveSelection()) {
                InteractionManager.cancelSelection();
            }
            GameManager.lockInteractions();
        } catch (Exception ignored) {}
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                try { GameManager.unlockInteractions(); } catch (Exception ignored) {}
                RoomManager.gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, 0.6f);
    }
}
