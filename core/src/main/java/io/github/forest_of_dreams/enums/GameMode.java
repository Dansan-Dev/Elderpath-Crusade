package io.github.forest_of_dreams.enums;

/**
 * Supported game modes for the application. The current mode is tracked
 * by GameModeManager and can be queried by UI/screens to alter behavior.
 */
public enum GameMode {
    DEMO,
    LOCAL_MATCH,
    ONLINE_MATCH,
    ROUGELIKE_RUN;
}
