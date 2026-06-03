package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.systems.TurnSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Thin facade over TurnSystem (ECS). Preserves existing API for callers.
 * All state lives in TurnStateComponent via TurnSystem.
 */
public class TurnManager {

    public TurnManager() {}

    public PieceAlignment getCurrentPlayer() {
        return getTurnSystem().getCurrentPlayer();
    }

    public boolean isWaitingForNextPlayer() {
        return getTurnSystem().isWaitingForNextPlayer();
    }

    public void startIfNeeded() {
        getTurnSystem().startIfNeeded();
    }

    public void endTurn() {
        getTurnSystem().endTurn();
    }

    public void startNextPlayerTurn() {
        getTurnSystem().startNextPlayerTurn();
    }

    public void startTurn(PieceAlignment player) {
        // Legacy method — delegates to startIfNeeded or direct start
        getTurnSystem().startIfNeeded();
    }

    public void reset() {
        getTurnSystem().reset();
    }

    private TurnSystem getTurnSystem() {
        return GameContext.get().getEcsEngine().getSystem(TurnSystem.class);
    }
}
