package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * The reserved pieceId format for a side's relic carrier (UPG-001) — an invisible, off-board
 * piece spawned by {@code MatchSetup.prepare} to hold every active relic's AbilityDefinitions
 * for that side. Recognized by name, not by any special component, so every existing system
 * that looks a piece up by id (AbilityRules.activate, LegalActionsCalculator) already works on
 * it unchanged — only the general piece-sync ({@code MatchServer.pieceViews}, which would
 * otherwise try to give it a sprite with no registered piece definition) and the board's own
 * ability-bubble UI ({@code AbilityPopup}, which would otherwise try to anchor a bubble off the
 * board) need to explicitly skip it; its actionable abilities surface through {@code RelicHud}
 * instead.
 */
public final class RelicCarrierIds {
    private static final String PREFIX = "relic-carrier-";

    private RelicCarrierIds() {
    }

    public static String idFor(PieceAlignment side) {
        return PREFIX + side.name();
    }

    public static boolean isRelicCarrier(String pieceId) {
        return pieceId != null && pieceId.startsWith(PREFIX);
    }
}
