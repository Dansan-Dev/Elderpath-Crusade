package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import java.util.Map;

/**
 * Minimal turn manager: tracks current player and invokes PlayerManager
 * start/end turn hooks. P1 starts.
 * Supports waiting state for LOCAL_MATCH mode where players manually start turns.
 */
public class TurnManager {
    private static boolean started = false;
    private static PieceAlignment current = PieceAlignment.P1;
    private static boolean waitingForNextPlayer = false;

    public static PieceAlignment getCurrentPlayer() { return current; }

    public static boolean isWaitingForNextPlayer() { return waitingForNextPlayer; }

    public static void startIfNeeded() {
        if (!started) {
            started = true;
            current = PieceAlignment.P1;
            PlayerManager.initializeIfNeeded();
            
            // Flip boards if needed for LOCAL_MATCH mode (should be unflipped for P1)
            if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
                flipBoardsForPlayer(current);
            }
            
            PlayerManager.onStartTurn(current);
            // Notify abilities on turn start (Option A)
            notifyBoardsTurnStarted(current);
            // Emit TURN_STARTED
            EventBus.emit(GameEventType.TURN_STARTED, Map.of("player", current.name()));
        }
    }

    public static void startTurn(PieceAlignment player) {
        current = player;
        if (!started) started = true;
        
        // Flip boards if needed for LOCAL_MATCH mode
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            flipBoardsForPlayer(current);
        }
        
        PlayerManager.onStartTurn(current);
        // Notify abilities on turn start
        notifyBoardsTurnStarted(current);
        EventBus.emit(GameEventType.TURN_STARTED, Map.of("player", current.name()));
    }

    public static void endTurn() {
        if (!started) return;
        // Notify abilities about turn end for the outgoing player
        notifyBoardsTurnEnded(current);
        // End current player's turn
        PlayerManager.onEndTurn(current);
        // Emit TURN_ENDED for the current player
        EventBus.emit(GameEventType.TURN_ENDED, Map.of("player", current.name()));
        
        // Check if we're in LOCAL_MATCH mode (requires manual turn start)
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            // Switch player but don't start their turn yet (waiting state)
            current = (current == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
            waitingForNextPlayer = true;
        } else {
            // Switch player and immediately start next turn (existing behavior)
            current = (current == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
            startTurnInternal(current);
        }
    }

    /**
     * Start the next player's turn (called manually in LOCAL_MATCH mode).
     */
    public static void startNextPlayerTurn() {
        if (!waitingForNextPlayer) return;
        waitingForNextPlayer = false;
        // Ensure PlayerManager is initialized
        PlayerManager.initializeIfNeeded();
        startTurnInternal(current);
    }

    private static void startTurnInternal(PieceAlignment player) {
        // Ensure PlayerManager is initialized before starting turn
        PlayerManager.initializeIfNeeded();
        
        // Flip boards if needed for LOCAL_MATCH mode
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            flipBoardsForPlayer(player);
        }
        
        PlayerManager.onStartTurn(player);
        // Notify abilities on turn start for the new player
        notifyBoardsTurnStarted(player);
        EventBus.emit(GameEventType.TURN_STARTED, Map.of("player", player.name()));
    }
    
    /**
     * Flip all boards for the given player's perspective in LOCAL_MATCH mode.
     * P1's turn: unflipped (normal orientation)
     * P2's turn: flipped (row 0 <-> row 6)
     */
    private static void flipBoardsForPlayer(PieceAlignment player) {
        for (Renderable r : io.github.elderpath_crusade.managers.GraphicsManager.getRenderables()) {
            if (r instanceof Board board) {
                // Check if board should be flipped for this player
                boolean shouldBeFlipped = (player == PieceAlignment.P2);
                // Check current state by comparing first and last row plots
                // If last row plot is at row 0 position, board is flipped
                boolean currentlyFlipped = isBoardFlipped(board);
                
                // Flip if state doesn't match desired state
                if (shouldBeFlipped != currentlyFlipped) {
                    board.flipRows();
                }
            }
        }
    }
    
    /**
     * Check if a board is currently flipped.
     * Uses the board's internal flip state tracking.
     */
    private static boolean isBoardFlipped(Board board) {
        return board.isFlipped();
    }

    private static void notifyBoardsTurnStarted(PieceAlignment player) {
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (r instanceof Board b) {
                b.notifyTurnStartedForPieces(player);
            }
        }
    }

    private static void notifyBoardsTurnEnded(PieceAlignment player) {
        for (Renderable r : GraphicsManager.getRenderables()) {
            if (r instanceof Board b) {
                b.notifyTurnEndedForPieces(player);
            }
        }
    }

    /**
     * Reset turn system and player state for a brand new room/session.
     * P1 will start after calling startIfNeeded() again.
     */
    public static void reset() {
        started = false;
        current = PieceAlignment.P1;
        waitingForNextPlayer = false;
        PlayerManager.resetForNewGame();
    }
}
