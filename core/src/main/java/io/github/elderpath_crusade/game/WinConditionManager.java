package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.utils.Logger;

/**
 * Centralized win condition watcher — headless: it only emits GameWonEvent. Showing the
 * victory screen is the view's job (see VictoryTransition).
 * P1 wins when a P1 piece reaches the last row (ROWS-1).
 * P2 wins when a P2 piece reaches row 0.
 */
public final class WinConditionManager {
    private boolean initialized = false;
    private boolean gameWon = false;
    private PieceAlignment winner = null;

    public WinConditionManager() {}

    public void reset() { gameWon = false; winner = null; }

    /** The match's winner, or null while it's still being played. */
    public PieceAlignment getWinner() { return winner; }

    public void resetSession() {
        initialized = false;
        gameWon = false;
        winner = null;
        initialize();
    }

    public void initialize() {
        if (initialized) return;
        initialized = true;

        TypedEventBus.get().registerScoped("session", PieceMovedEvent.class, evt -> checkWin(evt.owner(), evt.toRow()));
        TypedEventBus.get().registerScoped("session", PieceSpawnedEvent.class, evt -> checkWin(evt.owner(), evt.row()));
    }

    private void checkWin(PieceAlignment alignment, int destRow) {
        if (gameWon) return;
        if (alignment == null || alignment == PieceAlignment.NEUTRAL) return;

        BoardGrid grid = GameContext.get().getActiveGrid();
        if (grid == null) return;

        if (destRow == grid.getGoalRow(alignment)) triggerWin(alignment);
    }

    private void triggerWin(PieceAlignment winner) {
        if (gameWon) return;
        gameWon = true;
        this.winner = winner;
        Logger.log("Win", "VICTORY: " + winner.name());
        TypedEventBus.get().emit(new GameWonEvent(winner));
    }
}
