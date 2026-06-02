package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.enums.GameMode;

/**
 * Tracks the current game mode for the application.
 * Default mode is DEMO; rooms or game setup flows should set the appropriate mode.
 */
public final class GameModeManager {
    private GameMode current = GameMode.DEMO;

    public GameModeManager() {}

    public GameMode getCurrent() { return current; }
    public void setCurrent(GameMode mode) { current = mode; }
}
