package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import lombok.Getter;

import java.util.Map;

/**
 * Minimal turn manager: tracks current player and invokes PlayerManager
 * start/end turn hooks. P1 starts.
 * Supports waiting state for LOCAL_MATCH mode where players manually start
 * turns.
 */
public class TurnManager {
    private static boolean started = false;
    @Getter private static PieceAlignment currentPlayer = PieceAlignment.P1;
    @Getter private static boolean waitingForNextPlayer = false;

    public static void startIfNeeded() {
        if (!started) {
            started = true;
            currentPlayer = PieceAlignment.P1;
            startTurn(currentPlayer);
        }
    }

    public static void endTurn() {
        if (!started) return;
        notifyBoardTurnEnded(currentPlayer);
        PlayerManager.onEndTurn(currentPlayer);
        EventBus.emit(GameEventType.TURN_ENDED, Map.of("player", currentPlayer.name()));

        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            currentPlayer = (currentPlayer == PieceAlignment.P1)
                ? PieceAlignment.P2
                : PieceAlignment.P1;
            waitingForNextPlayer = true;
        } else {
            currentPlayer = (currentPlayer == PieceAlignment.P1)
                ? PieceAlignment.P2
                : PieceAlignment.P1;
            startTurn(currentPlayer);
        }
    }

    /**
     * Start the next player's turn (called manually in LOCAL_MATCH mode).
     */
    public static void startNextPlayerTurn() {
        if (!waitingForNextPlayer)
            return;
        waitingForNextPlayer = false;
        PlayerManager.initializeIfNeeded();
        startTurn(currentPlayer);
    }

    public static void startTurn(PieceAlignment player) {
        PlayerManager.initializeIfNeeded();

        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            flipBoardForPlayer(player);
        }

        PlayerManager.onStartTurn(player);
        notifyBoardTurnStarted(player);
        EventBus.emit(GameEventType.TURN_STARTED, Map.of("player", player.name()));
    }

    /**
     * Flip the board for the given player's perspective in LOCAL_MATCH mode.
     * P1's turn: unflipped (normal orientation)
     * P2's turn: flipped (row 0 <-> row 6)
     */
    private static void flipBoardForPlayer(PieceAlignment player) {
        Board board = BoardManager.getBoard();
        if (board != null) {
            boolean shouldBeFlipped = (player == PieceAlignment.P2);
            boolean currentlyFlipped = board.isFlipped();

            if (shouldBeFlipped != currentlyFlipped) {
                board.flipRows();
            }
        }
    }

    private static void notifyBoardTurnStarted(PieceAlignment player) {
        Board board = BoardManager.getBoard();
        if (board != null) {
            board.notifyTurnStartedForPieces(player);
        }
    }

    private static void notifyBoardTurnEnded(PieceAlignment player) {
        Board board = BoardManager.getBoard();
        if (board != null) {
            board.notifyTurnEndedForPieces(player);
        }
    }

    /**
     * Reset turn system and player state for a brand new room/session.
     * P1 will start after calling startIfNeeded() again.
     */
    public static void reset() {
        started = false;
        currentPlayer = PieceAlignment.P1;
        waitingForNextPlayer = false;
        PlayerManager.resetForNewGame();
    }
}
