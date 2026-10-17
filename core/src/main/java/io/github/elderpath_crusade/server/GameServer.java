package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;

/**
 * Single authoritative entry point for game actions (move/attack, card play), taking
 * plain data (board coordinates, card instance ids) rather than live click objects, so it can
 * be called identically from a local click handler (in-process, same JVM) or a network
 * command handler (received from a remote client) — see elderpath-multiplayer-events.md.
 *
 * Every mode is meant to route action execution through this class. It does not replace
 * click detection/hit-testing (still an inherently per-machine rendering concern) — only
 * the "now actually do it" step.
 */
public class GameServer {

    /** Moves or attacks with the piece at (srcRow, srcCol) toward (dstRow, dstCol). Returns true on success. */
    public boolean movePlot(int srcRow, int srcCol, int dstRow, int dstCol) {
        Board board = GameContext.get().getActiveBoard();
        if (board == null) return false;
        return board.movePiece(srcRow, srcCol, dstRow, dstCol);
    }

    /** Plays the summon card instanceId from alignment's hand onto (row, col). Returns true on success. */
    public boolean playSummonCard(PieceAlignment alignment, String instanceId, int row, int col) {
        return GameContext.get().getCardRules().playSummon(alignment, instanceId, row, col);
    }

    /** Plays the spell card instanceId from alignment's hand, targeting (targetRow, targetCol) — null/null for an untargeted spell. */
    public boolean playSpellCard(PieceAlignment alignment, String instanceId, Integer targetRow, Integer targetCol) {
        return GameContext.get().getCardRules().playSpell(alignment, instanceId, targetRow, targetCol);
    }
}
