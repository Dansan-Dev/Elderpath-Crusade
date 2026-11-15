package io.github.elderpath_crusade.match;

import java.util.UUID;

/**
 * The reserved pieceId format for an Elder's board piece (ELDER-006 spawns them in
 * {@code MatchSetup.spawnElderPieces}). Same idea as {@link RelicCarrierIds}: recognized by id,
 * so a client can tell an Elder apart (UI-003's Elder badge on the hover preview card) from the
 * {@code PieceView.id} it already receives — no protocol change, no new component. Still unique
 * per piece (a UUID follows the prefix); nothing parses a pieceId as a UUID.
 */
public final class ElderPieceIds {
    private static final String PREFIX = "elder-";

    private ElderPieceIds() {
    }

    public static String newId() {
        return PREFIX + UUID.randomUUID();
    }

    public static boolean isElder(String pieceId) {
        return pieceId != null && pieceId.startsWith(PREFIX);
    }
}
