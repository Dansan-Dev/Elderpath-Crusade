package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Emitted whenever the generic "Damage" ability/spell effect applies damage (EffectExecutor)
 * — distinct from PieceAttackedEvent, which covers a basic melee attack specifically. Covers
 * spell damage (Fireball, Blizzard, ...) and triggered-ability damage (OnSummonShock,
 * CleaveAttack, ...), none of which previously emitted any event at all.
 */
public record PieceDamagedEvent(
        String pieceId,
        PieceAlignment owner,
        int row, int col,
        int amount
) implements GameEvent {
}
