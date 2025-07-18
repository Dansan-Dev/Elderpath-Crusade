package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Game;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.api.BackendService;
import io.github.forest_of_dreams.api.dto.UserResponseDto;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.enums.settings.InputHandlerData;
import io.github.forest_of_dreams.enums.settings.InputKey;
import io.github.forest_of_dreams.game_objects.*;
import io.github.forest_of_dreams.managers.*;
import io.github.forest_of_dreams.ui_objects.PauseMenuHint;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.utils.SpriteCreator;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;

    @Override
    public void create() {
        BackendService.post("Yxaria")
            .thenAccept(u -> {
                BackendService.get("/user/" + u.getId(), UserResponseDto.class)
                    .thenAccept(user -> System.out.println("USERNAME: " + user.getUsername()));
            });
        GameManager.initialize();

        SettingsManager.sound.setMusicVolume(3);
        SettingsManager.sound.setMasterVolume(7);
        SoundManager.queueMusic("Evening_Harmony.mp3");
        SoundManager.queueMusic("Forgotten_Biomes.mp3");
        SoundManager.transition();
        SoundManager.playSound("01_chest_open_1.wav");

        int plot_width = 40;
        int plot_height = 40;

        batch = new SpriteBatch();
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int[] board_size = new int[]{plot_width*5, plot_height*7};
        Board board = new Board(screen_center[0] - board_size[0]/2, screen_center[1] - board_size[1]/2, plot_width, plot_height);
        for(int row = 0; row < 7; row++) {
            for(int col = 0; col < 5; col++) {
                board.replacePos(row, col, new Plot(0, 0, plot_width, plot_height));
            }
        }

        SpriteObject sprObj = new SpriteObject(60, 60, plot_width, plot_height, 1, SpriteBoxPos.BOTTOM);

        sprObj.addAnimation("walk", List.of(
            SpriteCreator.makeSprite("images/gobu_walk.png", 0, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_walk.png", 32, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_walk.png", 64, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_walk.png", 96, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_walk.png", 128, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_walk.png", 160, 6, 32, 32, plot_width, (int)(plot_height * 1.2f))
        ), 6);
        sprObj.addAnimation("hurt", List.of(
            SpriteCreator.makeSprite("images/gobu_hurt.png", 0, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 32, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 64, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 96, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 128, 6, 32, 32, plot_width, (int)(plot_height * 1.2f)),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 160, 6, 32, 32, plot_width, (int)(plot_height * 1.2f))
        ), 6);
        GraphicsManager.addRenderable(sprObj);
        GraphicsManager.addRenderable(board);
        GraphicsManager.addUIRenderable(new PauseMenuHint());
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
