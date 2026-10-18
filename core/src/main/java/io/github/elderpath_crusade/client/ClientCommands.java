package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.match.Coord;

/**
 * The requests a game screen can make of its match — the client side of GameServer's actions.
 * Each sends a command to the match server on behalf of the seat the screen is acting as, and
 * returns the command's seq (echoed by the server's Ack/Rejected), or {@link #NOT_SENT} if there
 * was nothing to send it through (no seat of this screen is acting, or it isn't connected).
 *
 * Commands are requests: nothing changes on screen until the server says it happened.
 */
public interface ClientCommands {
    long NOT_SENT = -1;

    /** Moves the piece at src to dst, or attacks the enemy there. */
    long moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol);

    long playSummon(String instanceId, int row, int col);

    /** targetRow/targetCol null for an untargeted spell. */
    long playSpell(String instanceId, Integer targetRow, Integer targetCol);

    long activateAbility(String pieceId, int actionIndex);

    /** Answers this screen's pending pick with choice. */
    long answerPick(Coord choice);

    /** Cancels this screen's pending pick. */
    long cancelPick();

    long endTurn();
}
