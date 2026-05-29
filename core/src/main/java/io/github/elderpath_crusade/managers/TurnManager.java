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
 * Instance held by GameContext; access via GameContext.get().getTurnManager().
 */
public class TurnManager {
    private boolean started = false;
    private PieceAlignment currentPlayer = PieceAlignment.P1;
    private boolean waitingForNextPlayer = false;

    public TurnManager() {}

    public PieceAlignment getCurrentPlayer() {
        return currentPlayer;
    }

    public boolean isWaitingForNextPlayer() {
        return waitingForNextPlayer;
    }

    public void startIfNeeded() {
        if (!started) {
            started = true;
            currentPlayer = PieceAlignment.P1;
            startTurn(currentPlayer);
        }
    }

    public void endTurn() {
        if (!started) return;
        GameContext.get().getPlayerManager().onEndTurn(currentPlayer);
        TypedEventBus.get().emit(new TurnEndedEvent(currentPlayer));

        currentPlayer = (currentPlayer == PieceAlignment.P1)
            ? PieceAlignment.P2
            : PieceAlignment.P1;

        if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH) {
            waitingForNextPlayer = true;
        } else {
            startTurn(currentPlayer);
        }
    }

    public void startNextPlayerTurn() {
        if (!waitingForNextPlayer) return;
        waitingForNextPlayer = false;
        GameContext.get().getPlayerManager().initializeIfNeeded();
        startTurn(currentPlayer);
    }

    public void startTurn(PieceAlignment player) {
        GameContext.get().getPlayerManager().initializeIfNeeded();
        GameContext.get().getPlayerManager().onStartTurn(player);
        TypedEventBus.get().emit(new TurnStartedEvent(player));
    }

    public void reset() {
        started = false;
        currentPlayer = PieceAlignment.P1;
        waitingForNextPlayer = false;
        GameContext.get().getPlayerManager().resetForNewGame();
    }
}
