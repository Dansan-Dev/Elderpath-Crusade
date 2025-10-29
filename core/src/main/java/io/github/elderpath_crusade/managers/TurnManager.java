package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import java.util.Map;

/**
 * Minimal turn manager: tracks current player and invokes PlayerManager
 * start/end turn hooks. P1 starts.
 */
public class TurnManager {
    private static boolean started = false;
    private static PieceAlignment current = PieceAlignment.P1;

    public static PieceAlignment getCurrentPlayer() { return current; }

    public static void startIfNeeded() {
        if (!started) {
            started = true;
            current = PieceAlignment.P1;
            PlayerManager.initializeIfNeeded();
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
        // Switch player
        current = (current == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
        // Start next player's turn
        PlayerManager.onStartTurn(current);
        // Notify abilities on turn start for the new player
        notifyBoardsTurnStarted(current);
        EventBus.emit(GameEventType.TURN_STARTED, Map.of("player", current.name()));
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
        PlayerManager.resetForNewGame();
    }
}
