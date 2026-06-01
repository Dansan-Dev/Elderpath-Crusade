package io.github.elderpath_crusade;

import com.badlogic.gdx.ApplicationAdapter;
import io.github.elderpath_crusade.api.BackendService;
import io.github.elderpath_crusade.api.dto.UserListResponseDto;
import io.github.elderpath_crusade.enums.settings.InputHandlerData;
import io.github.elderpath_crusade.enums.settings.InputKey;
import io.github.elderpath_crusade.managers.*;
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
        boolean isOnline = BackendService.isUp();
        if (isOnline) {
            UserListResponseDto userListResponseDto = BackendService.getUsers();
            System.out.println("USERS:");
            userListResponseDto.getUsers().forEach(userResponseDto -> {
                System.out.println("> " + userResponseDto.getUsername());
            });
        }
        GameContext.create();
        GameContext.get().getGameManager().initialize();

        SettingsManager.sound.setMusicVolume(3);
        SettingsManager.sound.setMasterVolume(7);
        MusicManager.playLoopingMusic("Evening_Harmony.mp3");

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
    }

    private Map<InputHandlerData, Object> getInputHandlerData() {
        inputHandlerData.clear();
        inputHandlerData.put(InputHandlerData.IS_PAUSED, GameContext.get().getGameManager().isPaused());
        inputHandlerData.put(InputHandlerData.MOUSE_X, com.badlogic.gdx.Gdx.input.getX());
        inputHandlerData.put(InputHandlerData.MOUSE_Y, SettingsManager.screenSize.getScreenHeight() - com.badlogic.gdx.Gdx.input.getY());
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
        GameContext.get().getHighlightManager().update();
        GameContext.get().getGraphicsManager().update(delta);

        // RENDER
        if (GameContext.get().getGameManager().isPaused()) GameContext.get().getGraphicsManager().blurredDraw(GameContext.get().getGraphicsManager().getBatch());
        else GameContext.get().getGraphicsManager().draw(GameContext.get().getGraphicsManager().getBatch());
        GameContext.get().getGraphicsManager().drawPauseUI(GameContext.get().getGraphicsManager().getBatch());

        // SOUND
        MusicManager.update();
    }

    @Override
    public void dispose() {
        GameContext.get().getGraphicsManager().getBatch().dispose();
        TextureManager.dispose();
        GraphicUtils.dispose();
    }
}
