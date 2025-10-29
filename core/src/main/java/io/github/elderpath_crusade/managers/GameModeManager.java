package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.enums.GameMode;
import lombok.Getter;
import lombok.Setter;

/**
 * Tracks the current game mode for the application.
 * Default mode is DEMO; rooms or game setup flows should set the appropriate mode.
 */
public final class GameModeManager {
    @Setter
    @Getter
    private static GameMode current = GameMode.DEMO;

    private GameModeManager() {}

}
