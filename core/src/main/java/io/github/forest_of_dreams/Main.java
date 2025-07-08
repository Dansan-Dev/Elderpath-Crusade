package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.*;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.SoundManager;
import io.github.forest_of_dreams.utils.GraphicUtils;
import io.github.forest_of_dreams.utils.SpriteCreator;

import java.util.List;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private GraphicsManager graphicsManager;

    @Override
    public void create() {
        SettingsManager.initialize();

        SettingsManager.sound.setMusicVolume(3);
        SettingsManager.sound.setMasterVolume(7);
        SoundManager.queueMusic("Evening_Harmony.mp3");
        SoundManager.queueMusic("Forgotten_Biomes.mp3");
        SoundManager.transition();
        SoundManager.playSound("01_chest_open_1.wav");

        batch = new SpriteBatch();
        graphicsManager = new GraphicsManager();
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int[] board_size = new int[]{40*5, 40*7}; //TODO: Fix so that plots don't have spaces in between
        Board board = new Board(screen_center[0] - board_size[0]/2, screen_center[1] - board_size[1]/2, 40, 40);
        for(int row = 0; row < 7; row++) {
            for(int col = 0; col < 5; col++) {
                board.replacePos(row, col, new Plot(0, 0, 40, 40));
            }
        }

        SpriteObject sprObj = new SpriteObject(60, 60, 40, 40, 1, SpriteBoxPos.BOTTOM);

        sprObj.addAnimation("walk", List.of(
            SpriteCreator.makeSprite("images/gobu_walk.png", 0, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_walk.png", 32, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_walk.png", 64, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_walk.png", 96, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_walk.png", 128, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_walk.png", 160, 6, 32, 32, 40, 48)
        ), 6);
        sprObj.addAnimation("hurt", List.of(
            SpriteCreator.makeSprite("images/gobu_hurt.png", 0, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 32, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 64, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 96, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 128, 6, 32, 32, 40, 48),
            SpriteCreator.makeSprite("images/gobu_hurt.png", 160, 6, 32, 32, 40, 48)
        ), 6);
        graphicsManager.addRenderable(sprObj);
        graphicsManager.addRenderable(board);
//        List<Renderable> plots = List.of(
//            new TextureObject(Color.BLUE, 175, 100, 100, 100, -1),
//            new TextureObject(Color.YELLOW, 200, 150, 100, 100),
//            new TextureObject(Color.RED, 225, 100, 100, 100, 1)
//        );
//        plots.forEach(r -> {
//            TextureObject p = (TextureObject) r;
//            Color c = p.getColor().cpy().lerp(Color.BLACK, 0.5f);
//            p.setHoverColor(c);
//        });
//        graphicsManager.addRenderables(
//            plots
//        );
    }

    @Override
    public void render() {
        // RENDER
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        batch.begin();
        graphicsManager.render(batch);
        batch.end();

        // SOUND
        SoundManager.update();
    }

    @Override
    public void dispose() {
        batch.dispose();
        GraphicUtils.dispose();
    }
}
