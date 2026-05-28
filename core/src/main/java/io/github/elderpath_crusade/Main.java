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
        GameManager.initialize();

        SettingsManager.sound.setMusicVolume(3);
        SettingsManager.sound.setMasterVolume(7);
        MusicManager.playLoopingMusic("Evening_Harmony.mp3");

        RoomManager.initialize();
    }

    private void handleInput() {
        Map<InputKey, Boolean> inputKeysPressed = InputManager.getInputKeysPressed();
        Map<InputHandlerData, Object> data = getInputHandlerData();
        inputKeysPressed.entrySet().stream()
            .filter(Map.Entry::getValue)
            .forEach(e -> {
                    InputManager.activateInputHandler(e.getKey(), data);
                }
            );
    }

    private Map<InputHandlerData, Object> getInputHandlerData() {
        Map<InputHandlerData, Object> data = new HashMap<>();
        data.put(InputHandlerData.IS_PAUSED, GameManager.isPaused());
        data.put(InputHandlerData.MOUSE_X, com.badlogic.gdx.Gdx.input.getX());
        data.put(InputHandlerData.MOUSE_Y, SettingsManager.screenSize.getScreenHeight() - com.badlogic.gdx.Gdx.input.getY());
        return data;
    }

    @Override
    public void render() {
        float delta = com.badlogic.gdx.Gdx.graphics.getDeltaTime();
        // Input (disabled when interactions are locked)
        if (!GameManager.isInteractionsLocked()) {
            InputManager.checkInput();
            handleInput();
        }

        // UPDATE
        HighlightManager.update();
        GraphicsManager.update(delta);

        // RENDER
        if (GameManager.isPaused()) GraphicsManager.blurredDraw(GraphicsManager.getBatch());
        else GraphicsManager.draw(GraphicsManager.getBatch());
        GraphicsManager.drawPauseUI(GraphicsManager.getBatch());

        // SOUND
        MusicManager.update();
    }

    @Override
    public void dispose() {
        GraphicsManager.getBatch().dispose();
        TextureManager.dispose();
        GraphicUtils.dispose();
    }
}
