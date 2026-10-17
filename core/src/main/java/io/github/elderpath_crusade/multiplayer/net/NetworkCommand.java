package io.github.elderpath_crusade.multiplayer.net;

/**
 * A guest's requested action, sent to the host to be executed via GameServer. The host is
 * the sole simulation authority — a command is a request, not a state change; the host
 * validates and executes it exactly as it would a local click (see GameServer).
 *
 * Commands deliberately carry no acting side: the host takes it from the connection the
 * command arrived on, so a client can't claim to act for the other side. (Jackson rejects
 * unknown properties, so a payload that smuggles one in fails to decode.)
 */
public sealed interface NetworkCommand permits
        NetworkCommand.MovePlot,
        NetworkCommand.PlaySummonCard,
        NetworkCommand.PlaySpellCard,
        NetworkCommand.EndTurn,
        NetworkCommand.ActivateAbility,
        NetworkCommand.AnswerPick {

    record MovePlot(int srcRow, int srcCol, int dstRow, int dstCol) implements NetworkCommand {}

    record PlaySummonCard(String instanceId, int row, int col) implements NetworkCommand {}

    record PlaySpellCard(String instanceId, Integer targetRow, Integer targetCol) implements NetworkCommand {}

    record EndTurn() implements NetworkCommand {}

    /** actionIndex: the ability's index among the piece's actionable abilities (bubble number - 1). */
    record ActivateAbility(String pieceId, int actionIndex) implements NetworkCommand {}

    /** The answer to a pick the host asked this client: a chosen tile, or cancelled (row/col ignored). */
    record AnswerPick(long pickId, boolean cancelled, int row, int col) implements NetworkCommand {}
}
