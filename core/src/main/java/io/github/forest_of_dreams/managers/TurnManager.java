package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.enums.PieceAlignment;

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
        }
    }

    public static void startTurn(PieceAlignment player) {
        current = player;
        if (!started) started = true;
        PlayerManager.onStartTurn(current);
    }

    public static void endTurn() {
        if (!started) return;
        // End current player's turn
        PlayerManager.onEndTurn(current);
        // Switch player
        current = (current == PieceAlignment.P1) ? PieceAlignment.P2 : PieceAlignment.P1;
        // Start next player's turn
        PlayerManager.onStartTurn(current);
    }
}
