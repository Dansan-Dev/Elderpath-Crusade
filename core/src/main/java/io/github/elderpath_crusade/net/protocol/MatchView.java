package io.github.elderpath_crusade.net.protocol;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.match.Coord;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A match as one viewer may see it: the server builds it from its authoritative state (for a
 * Snapshot, and to check replicas in tests), a client from its ClientMatchState — comparing
 * the two is how replica consistency is tested (see diff).
 *
 * @param pieces          every piece, sorted by id (terrain excluded)
 * @param ownHand         the viewer's own hand, in order
 * @param opponentHand    the opponent's hand as opaque handles, in order (no card names)
 * @param pendingPickId   the viewer's active pick, or null
 * @param winner          null while the match is being played
 */
public record MatchView(
        PieceAlignment viewer,
        List<PieceView> pieces,
        List<Coord> terrain,
        List<CardInstance> ownHand,
        List<String> opponentHand,
        int p1Mana,
        int p2Mana,
        PieceAlignment currentPlayer,
        boolean started,
        Long pendingPickId,
        PieceAlignment winner
) {
    public MatchView {
        pieces = List.copyOf(pieces);
        terrain = List.copyOf(terrain);
        ownHand = List.copyOf(ownHand);
        opponentHand = List.copyOf(opponentHand);
    }

    /** A piece's public state. registryKey is the pieces.yaml key; stats are effective (modifiers applied). */
    public record PieceView(
            String id,
            PieceAlignment owner,
            String registryKey,
            int row, int col,
            int currentHealth,
            int maxHealth,
            int damage,
            int remainingActions,
            boolean stunned
    ) {
    }

    /** Human-readable differences between an expected (server) and actual (replica) view; empty if they agree. */
    public static List<String> diff(MatchView expected, MatchView actual) {
        List<String> out = new ArrayList<>();
        check(out, "viewer", expected.viewer, actual.viewer);
        check(out, "pieces", expected.pieces, actual.pieces);
        check(out, "terrain", expected.terrain, actual.terrain);
        check(out, "ownHand", expected.ownHand, actual.ownHand);
        check(out, "opponentHand", expected.opponentHand, actual.opponentHand);
        check(out, "p1Mana", expected.p1Mana, actual.p1Mana);
        check(out, "p2Mana", expected.p2Mana, actual.p2Mana);
        check(out, "currentPlayer", expected.currentPlayer, actual.currentPlayer);
        check(out, "started", expected.started, actual.started);
        check(out, "pendingPickId", expected.pendingPickId, actual.pendingPickId);
        check(out, "winner", expected.winner, actual.winner);
        return out;
    }

    private static void check(List<String> out, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) out.add(field + ": expected " + expected + " but was " + actual);
    }
}
