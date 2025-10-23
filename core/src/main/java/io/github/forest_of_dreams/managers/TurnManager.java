package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.multiplayer.EventBus;
import io.github.forest_of_dreams.multiplayer.GameEventType;

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
            // Emit TURN_STARTED
            EventBus.emit(GameEventType.TURN_STARTED, java.util.Map.of("player", current.name()));
        }
    }

    public static void startTurn(PieceAlignment player) {
        current = player;
        if (!started) started = true;
        PlayerManager.onStartTurn(current);
        EventBus.emit(GameEventType.TURN_STARTED, java.util.Map.of("player", current.name()));
    }

    public static void endTurn() {
        if (!started) return;
        // End current player's turn
        PlayerManager.onEndTurn(current);
        // Emit TURN_ENDED for the current player
        EventBus.emit(GameEventType.TURN_ENDED, java.util.Map.of("player", current.name()));
        // Switch player
        current = (current == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
        // Start next player's turn
        PlayerManager.onStartTurn(current);
        EventBus.emit(GameEventType.TURN_STARTED, java.util.Map.of("player", current.name()));
    }
}
