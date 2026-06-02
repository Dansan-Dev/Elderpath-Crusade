package io.github.elderpath_crusade.game;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.rooms.VictoryRoom;
import io.github.elderpath_crusade.utils.Logger;

/**
 * Centralized win condition watcher.
 * P1 wins when a P1 piece reaches the last row (ROWS-1).
 * P2 wins when a P2 piece reaches row 0.
 */
public final class WinConditionManager {
    private boolean initialized = false;
    private boolean gameWon = false;

    public WinConditionManager() {}

    public void reset() { gameWon = false; }

    public void initialize() {
        if (initialized) return;
        initialized = true;

        TypedEventBus.get().registerScoped("session", PieceMovedEvent.class, evt -> checkWin(evt.owner(), evt.toRow()));
        TypedEventBus.get().registerScoped("session", PieceSpawnedEvent.class, evt -> checkWin(evt.owner(), evt.row()));
        TypedEventBus.get().registerScoped("session", GameWonEvent.class, this::onGameWon);
    }

    private void checkWin(PieceAlignment alignment, int destRow) {
        if (gameWon) return;
        if (alignment == null || alignment == PieceAlignment.NEUTRAL) return;

        Board activeBoard = GameContext.get().getActiveBoard();
        if (activeBoard == null) return;

        int rows = activeBoard.getROWS();
        boolean flipped = activeBoard.isFlipped();
        boolean won = false;
        if (alignment == PieceAlignment.P1) {
            won = flipped ? (destRow == 0) : (destRow == rows - 1);
        } else if (alignment == PieceAlignment.P2) {
            won = flipped ? (destRow == rows - 1) : (destRow == 0);
        }

        if (won) triggerWin(alignment);
    }

    private void triggerWin(PieceAlignment winner) {
        if (gameWon) return;
        gameWon = true;
        Logger.log("Win", "VICTORY: " + winner.name());
        TypedEventBus.get().emit(new GameWonEvent(winner));
    }

    private void onGameWon(GameWonEvent event) {
        PieceAlignment winner = event.winner();
        try {
            if (GameContext.get().getInteractionManager().hasActiveSelection()) {
                GameContext.get().getInteractionManager().cancelSelection();
            }
            GameContext.get().getGameManager().lockInteractions();
        } catch (Exception ignored) {}
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                try { GameContext.get().getGameManager().unlockInteractions(); } catch (Exception ignored) {}
                GameContext.get().getRoomManager().gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, 0.6f);
    }
}
