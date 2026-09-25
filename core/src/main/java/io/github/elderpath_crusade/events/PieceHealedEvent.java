package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

/** Emitted whenever the generic "Heal" ability/spell effect applies healing (EffectExecutor). */
public record PieceHealedEvent(
        String pieceId,
        PieceAlignment owner,
        int row, int col,
        int amount
) implements GameEvent {
}
