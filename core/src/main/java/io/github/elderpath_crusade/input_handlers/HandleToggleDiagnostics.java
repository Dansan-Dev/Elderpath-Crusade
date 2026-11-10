package io.github.elderpath_crusade.input_handlers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.interfaces.InputHandler;

import java.util.Map;

/** F3 — toggles the in-game diagnostics overlay (Sprint 64, see DiagnosticsOverlay). */
public class HandleToggleDiagnostics implements InputHandler {
    @Override
    public void handleInput(Map<InputHandlerData, Object> data) {
        SettingsManager.DebugSettings debug = GameContext.get().getSettingsManager().debug;
        debug.showPerfOverlay = !debug.showPerfOverlay;
    }
}
