package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.MoveRules;
import io.github.elderpath_crusade.utils.Logger;

import java.util.function.BiConsumer;

/**
 * Single authoritative entry point for game actions (move/attack, card play, end turn), taking
 * plain data (board coordinates, card instance ids) rather than live click objects, so it can
 * be called identically from a local click handler, a bot, or a network command handler.
 *
 * Every entry point takes the acting side explicitly. The caller derives it from who is
 * asking, never from the request's content: a local click uses LocalControl.actingSide(), a
 * bot its own slot, a network connection the alignment it was assigned. Everything is then
 * validated as if the request came from an untrusted client — the server accepts nothing the
 * UI wouldn't offer.
 */
public class GameServer {
    private BiConsumer<PieceAlignment, ActionResult> rejectionObserver;

    /** Moves or attacks with actor's piece at (srcRow, srcCol) toward (dstRow, dstCol). */
    public ActionResult movePlot(PieceAlignment actor, int srcRow, int srcCol, int dstRow, int dstCol) {
        return report(actor, "move " + srcRow + "," + srcCol + "->" + dstRow + "," + dstCol,
                MoveRules.moveOrAttack(actor, srcRow, srcCol, dstRow, dstCol));
    }

    /** Plays the summon card instanceId from actor's own hand onto (row, col). */
    public ActionResult playSummonCard(PieceAlignment actor, String instanceId, int row, int col) {
        return report(actor, "summon " + instanceId,
                GameContext.get().getCardRules().playSummon(actor, instanceId, row, col));
    }

    /** Plays the spell card instanceId from actor's own hand, targeting (targetRow, targetCol) — null/null for an untargeted spell. */
    public ActionResult playSpellCard(PieceAlignment actor, String instanceId, Integer targetRow, Integer targetCol) {
        return report(actor, "spell " + instanceId,
                GameContext.get().getCardRules().playSpell(actor, instanceId, targetRow, targetCol));
    }

    /** Ends actor's turn — only the current player may, and not while a hotseat handoff is pending. */
    public ActionResult endTurn(PieceAlignment actor) {
        TurnManager turns = GameContext.get().getTurnManager();
        ActionResult result;
        if (GameContext.get().getActiveGrid() == null) {
            result = ActionResult.rejected(ActionResult.Reason.NO_ACTIVE_MATCH);
        } else if (actor == null || actor != turns.getCurrentPlayer() || turns.isWaitingForNextPlayer()) {
            result = ActionResult.rejected(ActionResult.Reason.NOT_YOUR_TURN);
        } else {
            turns.endTurn();
            result = ActionResult.ok();
        }
        return report(actor, "end turn", result);
    }

    /** Observes every rejected action (actor, result) — for tests and, later, telling clients why. */
    public void setRejectionObserver(BiConsumer<PieceAlignment, ActionResult> rejectionObserver) {
        this.rejectionObserver = rejectionObserver;
    }

    private ActionResult report(PieceAlignment actor, String action, ActionResult result) {
        if (!result.isOk()) {
            Logger.log("GameServer", "Rejected " + action + " by " + actor + ": " + result.reason());
            if (rejectionObserver != null) rejectionObserver.accept(actor, result);
        }
        return result;
    }
}
