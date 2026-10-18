package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.AbilityRules;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.MoveRules;
import io.github.elderpath_crusade.match.PickResponse;
import io.github.elderpath_crusade.match.TurnRules;
import io.github.elderpath_crusade.utils.Logger;

import java.util.function.BiConsumer;

/**
 * Single authoritative entry point for game actions (move/attack, card play, ability, pick
 * answers, end turn), taking
 * plain data (board coordinates, card instance ids) rather than live click objects, so it can
 * be called identically from a local click handler, a bot, or a network command handler.
 *
 * Every entry point takes the acting side explicitly. The caller derives it from who is
 * asking, never from the request's content: a local click uses LocalControl.actingSide(), a
 * bot its own slot, a network connection the alignment it was assigned. Everything is then
 * validated as if the request came from an untrusted client — the server accepts nothing the
 * UI wouldn't offer.
 *
 * While the actor has a pick pending (PickRouter), its other actions are rejected with
 * PICK_PENDING — it must answer or cancel the pick first. That keeps resolution strictly
 * ordered: nothing else of the actor's can happen between an effect's question and its answer.
 */
public class GameServer {
    private static final ActionResult PICK_PENDING = ActionResult.rejected(ActionResult.Reason.PICK_PENDING);
    private BiConsumer<PieceAlignment, ActionResult> rejectionObserver;

    /** Moves or attacks with actor's piece at (srcRow, srcCol) toward (dstRow, dstCol). */
    public ActionResult movePlot(PieceAlignment actor, int srcRow, int srcCol, int dstRow, int dstCol) {
        String action = "move " + srcRow + "," + srcCol + "->" + dstRow + "," + dstCol;
        if (hasPendingPick(actor)) return report(actor, action, PICK_PENDING);
        return report(actor, action, MoveRules.moveOrAttack(actor, srcRow, srcCol, dstRow, dstCol));
    }

    /** Plays the summon card instanceId from actor's own hand onto (row, col). */
    public ActionResult playSummonCard(PieceAlignment actor, String instanceId, int row, int col) {
        if (hasPendingPick(actor)) return report(actor, "summon " + instanceId, PICK_PENDING);
        return report(actor, "summon " + instanceId,
                GameContext.get().getCardRules().playSummon(actor, instanceId, row, col));
    }

    /** Plays the spell card instanceId from actor's own hand, targeting (targetRow, targetCol) — null/null for an untargeted spell. */
    public ActionResult playSpellCard(PieceAlignment actor, String instanceId, Integer targetRow, Integer targetCol) {
        if (hasPendingPick(actor)) return report(actor, "spell " + instanceId, PICK_PENDING);
        return report(actor, "spell " + instanceId,
                GameContext.get().getCardRules().playSpell(actor, instanceId, targetRow, targetCol));
    }

    /**
     * Activates actor's piece pieceId's actionable ability actionIndex (its index among the
     * piece's abilities that have an action — the ability bubble's number minus one). Any
     * target it needs is asked for through the PickRouter; costs are paid once its effects
     * resolve, and not at all if the pick is cancelled.
     */
    public ActionResult activateAbility(PieceAlignment actor, String pieceId, int actionIndex) {
        String action = "ability " + pieceId + "#" + actionIndex;
        if (hasPendingPick(actor)) return report(actor, action, PICK_PENDING);
        return report(actor, action, AbilityRules.activate(actor, pieceId, actionIndex));
    }

    /** Delivers actor's answer to the pick it has pending (see PickRouter.answer for validation). */
    public ActionResult answerPick(PieceAlignment actor, PickResponse response) {
        return report(actor, "pick " + response, GameContext.get().getPickRouter().answer(actor, response));
    }

    /** Ends actor's turn — only the current player may, and not while a hotseat handoff is pending. */
    public ActionResult endTurn(PieceAlignment actor) {
        ActionResult result = TurnRules.validateEndTurn(actor);
        if (result.isOk() && hasPendingPick(actor)) {
            result = PICK_PENDING;
        } else if (result.isOk()) {
            GameContext.get().getTurnManager().endTurn();
        }
        return report(actor, "end turn", result);
    }

    /** Whether actor has a pick pending — while it does, every action but answering it is rejected. */
    private static boolean hasPendingPick(PieceAlignment actor) {
        return GameContext.get().getPickRouter().hasPending(actor);
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
