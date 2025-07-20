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
import io.github.forest_of_dreams.characters.pieces.Goblin;
import io.github.forest_of_dreams.characters.sprites.GoblinSprite;
import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.enums.settings.InputKey;
import io.github.forest_of_dreams.game_objects.*;
import io.github.forest_of_dreams.managers.*;
import io.github.forest_of_dreams.ui_objects.PauseMenuHint;
import io.github.forest_of_dreams.utils.GraphicUtils;

import java.util.HashMap;
import java.util.Map;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;

    @Override
    public void create() {
        UserListResponseDto userListResponseDto = BackendService.getUsers();
        System.out.println("USERS:");
        userListResponseDto.getUsers().forEach(userResponseDto -> {
            System.out.println("> " + userResponseDto.getUsername());
        });
        GameManager.initialize();

        SettingsManager.sound.setMusicVolume(3);
        SettingsManager.sound.setMasterVolume(7);
        SoundManager.queueMusic("Evening_Harmony.mp3");
        SoundManager.queueMusic("Forgotten_Biomes.mp3");
        SoundManager.transition();

        batch = new SpriteBatch();

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

    public void blurredDraw(SpriteBatch batch) {
        ShaderProgram blurShader = ShaderManager.getBlurShader();
        FrameBuffer fboA = ShaderManager.getFboA();
        FrameBuffer fboB = ShaderManager.getFboB();

        // First pass - capture the scene
        fboA.begin();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        batch.setShader(null);
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        GraphicsManager.render(batch);
        batch.end();
        fboA.end();

        // Horizontal blur
        fboB.begin();
        batch.begin();
        batch.setShader(blurShader);
        blurShader.setUniformf("u_blurSize", 1f / SettingsManager.screenSize.getScreenConfiguredWidth());
        blurShader.setUniformf("u_direction", 1f, 0f);
        batch.draw(fboA.getColorBufferTexture(), 0, 0);
        batch.end();
        fboB.end();

        // Vertical blur (final pass)
        batch.begin();
        blurShader.setUniformf("u_blurSize", 1f / SettingsManager.screenSize.getScreenConfiguredHeight());
        blurShader.setUniformf("u_direction", 0f, 1f);
        batch.draw(fboB.getColorBufferTexture(), 0, 0);
        batch.end();

        batch.setShader(null);
    }

    public void draw(SpriteBatch batch) {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        batch.begin();
        batch.setShader(null);
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        GraphicsManager.render(batch);
        batch.end();
    }

    public void drawPauseUI(SpriteBatch batch) {
        batch.begin();
        GraphicsManager.renderPauseUI(batch);
        batch.end();
    }

    @Override
    public void render() {

        // Input
        InputManager.checkInput();
        handleInput();
        InteractionManager.checkClick();

        // RENDER
        if (GraphicsManager.isPaused()) blurredDraw(batch);
        else draw(batch);
        drawPauseUI(batch);

        // SOUND
        SoundManager.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        GraphicUtils.dispose();
    }
}
