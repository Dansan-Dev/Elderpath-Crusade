package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.GameMode;

/**
 * Tracks the current game mode for the application.
 * Default mode is DEMO; rooms or game setup flows should set the appropriate mode.
 */
public final class GameModeManager {
    private GameMode current = GameMode.DEMO;

    public GameModeManager() {}

    // Static facade
    private static GameModeManager instance() { return GameContext.get().getGameModeManager(); }
    public static GameMode getCurrent() { return instance().current; }
    public static void setCurrent(GameMode mode) { instance().current = mode; }
}
