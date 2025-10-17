package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.api.BackendService;
import io.github.forest_of_dreams.api.dto.UserListResponseDto;
import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.enums.settings.InputKey;
import io.github.forest_of_dreams.managers.*;
import io.github.forest_of_dreams.utils.GraphicUtils;

import java.util.HashMap;
import java.util.Map;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
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
        GameManager.initialize();

        SettingsManager.sound.setMusicVolume(3);
        SettingsManager.sound.setMasterVolume(7);
        SoundManager.queueMusic("Evening_Harmony.mp3");
        SoundManager.queueMusic("Forgotten_Biomes.mp3");
        SoundManager.transition();

        Game.initialize();
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
        data.put(InputHandlerData.IS_PAUSED, GraphicsManager.isPaused());
        return data;
    }

    @Override
    public void render() {
        // Input
        InputManager.checkInput();
        handleInput();
        InteractionManager.checkClick();

        // RENDER
        if (GraphicsManager.isPaused()) GraphicsManager.blurredDraw(GraphicsManager.getBatch());
        else GraphicsManager.draw(GraphicsManager.getBatch());
        GraphicsManager.drawPauseUI(GraphicsManager.getBatch());

        // SOUND
        SoundManager.update();
    }

    @Override
    public void dispose() {
        GraphicsManager.getBatch().dispose();
        GraphicUtils.dispose();
    }
}
