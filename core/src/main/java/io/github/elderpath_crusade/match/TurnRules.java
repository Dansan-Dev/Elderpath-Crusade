package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.TurnManager;

/** Who may end the turn — shared by GameServer.endTurn and LegalActionsCalculator. */
public final class TurnRules {
    private TurnRules() {}

    /** Only the current player may end the turn, and not while a hotseat handoff is pending. */
    public static ActionResult validateEndTurn(PieceAlignment actor) {
        if (GameContext.get().getActiveGrid() == null) return ActionResult.rejected(ActionResult.Reason.NO_ACTIVE_MATCH);
        TurnManager turns = GameContext.get().getTurnManager();
        if (actor == null || actor != turns.getCurrentPlayer() || turns.isWaitingForNextPlayer()) {
            return ActionResult.rejected(ActionResult.Reason.NOT_YOUR_TURN);
        }
        return ActionResult.ok();
    }
}
