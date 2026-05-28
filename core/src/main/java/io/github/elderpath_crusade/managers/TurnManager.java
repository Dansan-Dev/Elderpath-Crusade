package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

/**
 * Minimal turn manager: tracks current player and invokes PlayerManager
 * start/end turn hooks. P1 starts.
 * Supports waiting state for LOCAL_MATCH mode where players manually start
 * turns.
 *
 * Instance held by GameContext; static facade preserved for backward compatibility.
 */
public class TurnManager {
    private boolean started = false;
    private PieceAlignment currentPlayer = PieceAlignment.P1;
    private boolean waitingForNextPlayer = false;

    public TurnManager() {}

    // --- Instance methods ---

    public PieceAlignment getInstanceCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isInstanceWaitingForNextPlayer() {
        return waitingForNextPlayer;
    }

    public void instanceStartIfNeeded() {
        if (!started) {
            started = true;
            currentPlayer = PieceAlignment.P1;
            instanceStartTurn(currentPlayer);
        }
    }

    public void instanceEndTurn() {
        if (!started) return;
        PlayerManager.onEndTurn(currentPlayer);
        TypedEventBus.get().emit(new TurnEndedEvent(currentPlayer));

        currentPlayer = (currentPlayer == PieceAlignment.P1)
            ? PieceAlignment.P2
            : PieceAlignment.P1;

        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            waitingForNextPlayer = true;
        } else {
            instanceStartTurn(currentPlayer);
        }
    }

    public void instanceStartNextPlayerTurn() {
        if (!waitingForNextPlayer) return;
        waitingForNextPlayer = false;
        PlayerManager.initializeIfNeeded();
        instanceStartTurn(currentPlayer);
    }

    public void instanceStartTurn(PieceAlignment player) {
        PlayerManager.initializeIfNeeded();
        PlayerManager.onStartTurn(player);
        TypedEventBus.get().emit(new TurnStartedEvent(player));
    }

    public void instanceReset() {
        started = false;
        currentPlayer = PieceAlignment.P1;
        waitingForNextPlayer = false;
        PlayerManager.resetForNewGame();
    }

    // --- Static facade (delegates to instance on GameContext) ---

    private static TurnManager instance() {
        return GameContext.get().getTurnManager();
    }

    public static PieceAlignment getCurrentPlayer() {
        return instance().currentPlayer;
    }

    public static boolean isWaitingForNextPlayer() {
        return instance().waitingForNextPlayer;
    }

    public static void startIfNeeded() {
        instance().instanceStartIfNeeded();
    }

    public static void endTurn() {
        instance().instanceEndTurn();
    }

    public static void startNextPlayerTurn() {
        instance().instanceStartNextPlayerTurn();
    }

    public static void startTurn(PieceAlignment player) {
        instance().instanceStartTurn(player);
    }

    public static void reset() {
        instance().instanceReset();
    }
}
