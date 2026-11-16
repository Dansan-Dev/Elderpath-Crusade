package io.github.elderpath_crusade;

import com.badlogic.gdx.ApplicationAdapter;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.enums.settings.InputKey;
import io.github.elderpath_crusade.input.InputManager;
import io.github.elderpath_crusade.utils.GraphicUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * {@link com.badlogic.gdx.ApplicationListener} implementation shared by all
 * platforms.
 */
public class Main extends ApplicationAdapter {
    // OPT-002: reusable to avoid per-frame allocation
    private final Map<InputHandlerData, Object> inputHandlerData = new HashMap<>();

    @Override
    public void create() {
        // A shipped game has no console: keep a log file (and any crash) a player can send along.
        io.github.elderpath_crusade.utils.Logger.enableFileLogging(io.github.elderpath_crusade.utils.Logger.defaultLogFile());
        io.github.elderpath_crusade.utils.Logger.log("Main", "Elderpath Crusade " + io.github.elderpath_crusade.utils.GameVersion.current() + " starting");
        Thread.setDefaultUncaughtExceptionHandler((thread, error) ->
                io.github.elderpath_crusade.utils.Logger.error("Crash", "Uncaught exception on thread " + thread.getName(), error));
        GameContext.createClient();
        // Loads the saved settings (volume included) — nothing here may overwrite them afterwards.
        GameContext.get().getGameManager().initialize();
        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        GameContext.get().getRoomManager().initialize();
    }

    private void handleInput() {
        InputManager inputManager = GameContext.get().getInputManager();
        Map<InputKey, Boolean> inputKeysPressed = inputManager.getInputKeysPressed();
        Map<InputHandlerData, Object> data = getInputHandlerData();
        inputKeysPressed.entrySet().stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> {
                    inputManager.activateInputHandler(e.getKey(), data);
                }
            );
        // PAUSE_MENU / CONFIRM_SELECTION (FEAT-015/A3): rebindable, so dispatched by function
        // rather than through a fixed InputKey — see InputManager.checkInput().
        inputManager.getRebindableFunctionsPressed().entrySet().stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> inputManager.activateFunctionHandler(e.getKey(), data));
    }

    private Map<InputHandlerData, Object> getInputHandlerData() {
        inputHandlerData.clear();
        inputHandlerData.put(InputHandlerData.IS_PAUSED, GameContext.get().getGameManager().isPaused());
        inputHandlerData.put(InputHandlerData.MOUSE_X, com.badlogic.gdx.Gdx.input.getX());
        inputHandlerData.put(InputHandlerData.MOUSE_Y, GameContext.get().getSettingsManager().screenSize.getScreenHeight() - com.badlogic.gdx.Gdx.input.getY());
        return inputHandlerData;
    }

    @Override
    public void render() {
        float delta = com.badlogic.gdx.Gdx.graphics.getDeltaTime();
        // Input (disabled when interactions are locked)
        if (!GameContext.get().getGameManager().isInteractionsLocked()) {
            GameContext.get().getInputManager().checkInput();
            handleInput();
        }

        // UPDATE
        ClientMatchSession session = GameContext.get().getClientMatchSession();
        if (session != null) {
            session.poll();
            ClientMatchState state = session.viewState();
            if (state != null) GameContext.get().getReplicaViewSync().sync(state);
            GameContext.get().getClientController().update();
        }
        GameContext.get().getEcsEngine().update(delta); // view entities, render system
        GameContext.get().getHighlightManager().update();
        GameContext.get().getGraphicsManager().update(delta);

        // RENDER
        if (GameContext.get().getGameManager().isPaused()) GameContext.get().getGraphicsManager().blurredDraw(GameContext.get().getGraphicsManager().getBatch());
        else GameContext.get().getGraphicsManager().draw(GameContext.get().getGraphicsManager().getBatch());
        GameContext.get().getGraphicsManager().drawPauseUI(GameContext.get().getGraphicsManager().getBatch());

        // SOUND
        GameContext.get().getMusicManager().update();
    }

    @Override
    public void dispose() {
        ClientMatchSession session = GameContext.get().getClientMatchSession();
        if (session != null) session.close();
        GameContext.get().getGraphicsManager().getBatch().dispose();
        GameContext.get().getTextureManager().dispose();
        GameContext.get().getSoundManager().dispose();
        GameContext.get().getAssets().dispose();
        GraphicUtils.dispose();
    }
}
