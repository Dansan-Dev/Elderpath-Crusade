package io.github.elderpath_crusade.abilities.data;

import io.github.elderpath_crusade.events.*;

/**
 * Maps GameEvent instances to TriggerType enum values and populates event context.
 */
public class TriggerMatcher {

    public static TriggerType fromEvent(GameEvent event) {
        if (event instanceof PieceSpawnedEvent) return TriggerType.ON_SUMMON;
        if (event instanceof PieceMovedEvent) return TriggerType.ON_MOVE;
        if (event instanceof PieceAttackedEvent) return TriggerType.ON_ATTACK;
        if (event instanceof PieceDiedEvent) return TriggerType.ON_DEATH;
        if (event instanceof PieceKilledEvent) return TriggerType.ON_KILL;
        if (event instanceof TurnStartedEvent) return TriggerType.ON_TURN_START;
        if (event instanceof TurnEndedEvent) return TriggerType.ON_TURN_END;
        return null;
    }

    public static void populateEventContext(GameEvent event, ExpressionContext context) {
        if (event instanceof PieceMovedEvent e) {
            context.set("$event.pieceId", e.pieceId());
            context.set("$event.fromRow", e.fromRow());
            context.set("$event.fromCol", e.fromCol());
            context.set("$event.toRow", e.toRow());
            context.set("$event.toCol", e.toCol());
        } else if (event instanceof PieceAttackedEvent e) {
            context.set("$event.attackerId", e.attackerId());
            context.set("$event.defenderId", e.defenderId());
            context.set("$event.damage", e.damage());
            context.set("$event.defenderRow", e.defenderRow());
            context.set("$event.defenderCol", e.defenderCol());
        } else if (event instanceof PieceSpawnedEvent e) {
            context.set("$event.pieceId", e.pieceId());
            context.set("$event.row", e.row());
            context.set("$event.col", e.col());
        } else if (event instanceof PieceDiedEvent e) {
            context.set("$event.pieceId", e.pieceId());
            context.set("$event.row", e.row());
            context.set("$event.col", e.col());
        } else if (event instanceof PieceKilledEvent e) {
            context.set("$event.killerId", e.killerId());
            context.set("$event.victimId", e.victimId());
            context.set("$event.excessDamage", e.excessDamage());
            context.set("$event.row", e.row());
            context.set("$event.col", e.col());
        } else if (event instanceof TurnStartedEvent e) {
            context.set("$event.player", e.player().name());
        } else if (event instanceof TurnEndedEvent e) {
            context.set("$event.player", e.player().name());
        }
    }
}
