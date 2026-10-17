package io.github.elderpath_crusade.match;

/**
 * Outcome of a requested game action (move/attack, card play, ability, pick answer, end turn): either accepted, or
 * rejected with the reason — so a server can tell a client why, instead of a bare false.
 */
public record ActionResult(Reason reason) {

    public enum Reason {
        OK,
        NO_ACTIVE_MATCH,
        NOT_YOUR_TURN,
        OUT_OF_BOUNDS,
        NO_PIECE,
        NOT_YOUR_PIECE,
        PIECE_STUNNED,
        PIECE_EXHAUSTED,
        UNREACHABLE,
        OUT_OF_RANGE,
        INVALID_TARGET,
        ATTACK_NOT_ALLOWED,
        CARD_NOT_IN_HAND,
        WRONG_CARD_TYPE,
        UNKNOWN_CARD,
        INVALID_SUMMON_TARGET,
        INVALID_SPELL_TARGET,
        NOT_ENOUGH_MANA,
        NO_SUCH_ABILITY,
        NO_VALID_TARGET,
        /** The actor must first answer (or cancel) the pick it has pending. */
        PICK_PENDING,
        NO_PENDING_PICK,
        INVALID_PICK,
        EXECUTION_FAILED
    }

    private static final ActionResult OK = new ActionResult(Reason.OK);

    public static ActionResult ok() {
        return OK;
    }

    public static ActionResult rejected(Reason reason) {
        return new ActionResult(reason);
    }

    public boolean isOk() {
        return reason == Reason.OK;
    }
}
