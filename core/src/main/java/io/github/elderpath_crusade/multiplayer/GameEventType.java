package io.github.elderpath_crusade.multiplayer;

public enum GameEventType {
    // Turn lifecycle
    TURN_STARTED,
    TURN_ENDED,

    // Cards
    CARD_DRAWN,
    CARD_SHUFFLED,
    CARD_DISCARDED,
    CARD_PLAYED,

    // Board / pieces
    PIECE_SPAWNED,
    ACTIVE_MOVEMENT,
    FORCED_MOVEMENT,
    PIECE_ATTACKED,
    PIECE_DIED,

    // Resources / state
    MANA_CHANGED,
    ACTIONS_RESET,
    ACTION_SPENT
}
