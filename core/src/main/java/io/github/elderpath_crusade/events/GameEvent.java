package io.github.elderpath_crusade.events;

/**
 * Sealed base interface for all typed game events.
 */
public sealed interface GameEvent permits
        TurnStartedEvent,
        TurnEndedEvent,
        CardDrawnEvent,
        CardShuffledEvent,
        CardDiscardedEvent,
        CardPlayedEvent,
        PieceSpawnedEvent,
        PieceMovedEvent,
        PieceAttackedEvent,
        PieceDiedEvent,
        PieceKilledEvent,
        ManaChangedEvent,
        ActionsResetEvent,
        ActionSpentEvent,
        GameWonEvent {
}
