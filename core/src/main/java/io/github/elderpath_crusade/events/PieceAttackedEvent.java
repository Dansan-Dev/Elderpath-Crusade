package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;
import java.util.List;

/**
 * Emitted when a piece attacks another.
 */
public record PieceAttackedEvent(
        String attackerId,
        PieceAlignment attackerOwner,
        int attackerRow, int attackerCol,
        String defenderId,
        int defenderRow, int defenderCol,
        int damage,
        List<String> additionalTargetIds,
        String abilityName
) implements GameEvent {

    public PieceAttackedEvent(String attackerId, PieceAlignment attackerOwner, int attackerRow, int attackerCol,
                              String defenderId, int defenderRow, int defenderCol, int damage) {
        this(attackerId, attackerOwner, attackerRow, attackerCol, defenderId, defenderRow, defenderCol, damage, null, null);
    }
}
