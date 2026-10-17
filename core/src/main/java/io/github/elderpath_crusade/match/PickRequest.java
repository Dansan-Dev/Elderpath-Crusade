package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;

/**
 * A question game logic asks a player mid-resolution — "which of these?" — e.g. ChooseTarget,
 * a SummonPiece tile, or an actionable ability's target. Candidates are absolute board
 * coordinates; a ROW pick lists every tile of every eligible row and the answer's row counts.
 * source names what asked (ability/effect id), for prompts and logs.
 */
public record PickRequest(
        long pickId,
        PieceAlignment owner,
        PickKind kind,
        List<Coord> candidates,
        String source,
        boolean cancellable
) {
    public PickRequest {
        candidates = List.copyOf(candidates);
    }
}
