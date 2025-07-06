package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.*;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.GraphicsManager;

import java.util.List;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private GraphicsManager graphicsManager;

    @Override
    public void create() {
        batch = new SpriteBatch();
        graphicsManager = new GraphicsManager();
        Board board = new Board(60, 60, 40, 40);
        for(int row = 0; row < 7; row++) {
            for(int col = 0; col < 5; col++) {
                board.replacePos(row, col, new Plot(0, 0, 40, 40));
            }
        }
        SpriteObject sprObj = new SpriteObject(60, 60, 40, 40, 1, SpriteBoxPos.CENTER);
        sprObj.setSprite("images/gobu_walk.png", 0, 8, 32, 24, 32, 24);
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
//        graphicsManager.addRenderable(new Plot(100, 100, plot, plotDirt));
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        batch.begin();
        graphicsManager.render(batch);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
    }
}
