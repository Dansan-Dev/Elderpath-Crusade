package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;

/**
 * A one-time "catch-up" message the host sends right after accepting a guest connection,
 * describing everything that already happened before the guest connected — the incremental
 * GameEvent relay only carries what happens from that point forward, so without this, a guest
 * joining a match already in progress would start from a blank board with no hand and no way
 * to learn what it missed (e.g. a piece the host already played would simply never appear).
 *
 * Not a GameEvent — this is transport-layer onboarding, not part of game history.
 */
public record GameSnapshot(
        List<PieceState> pieces,
        List<String> p1Hand,
        List<String> p2Hand,
        int p1Mana,
        int p2Mana,
        PieceAlignment currentPlayer
) {
    /** registryKey is the pieces.yaml key (IdentityComponent.name), not the card's display name. */
    public record PieceState(
            String pieceId,
            PieceAlignment owner,
            String registryKey,
            int row, int col,
            int currentHealth,
            int remainingActions
    ) {
    }
}
