package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * A guest's requested action, sent to the host to be executed via GameServer. The host is
 * the sole simulation authority — a command is a request, not a state change; the host
 * validates and executes it exactly as it would a local click (see GameServer).
 */
public sealed interface NetworkCommand permits
        NetworkCommand.MovePlot,
        NetworkCommand.PlaySummonCard,
        NetworkCommand.PlaySpellCard,
        NetworkCommand.EndTurn {

    record MovePlot(int srcRow, int srcCol, int dstRow, int dstCol) implements NetworkCommand {}

    record PlaySummonCard(PieceAlignment alignment, int handIndex, int row, int col) implements NetworkCommand {}

    record PlaySpellCard(PieceAlignment alignment, int handIndex, Integer targetRow, Integer targetCol) implements NetworkCommand {}

    record EndTurn(PieceAlignment alignment) implements NetworkCommand {}
}
