package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.game_objects.Plot;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.utils.GraphicUtils;

import java.util.List;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private GraphicsManager graphicsManager;
    private Plot plot;

    @Override
    public void create() {
        batch = new SpriteBatch();
        graphicsManager = new GraphicsManager();
        graphicsManager.addRenderable(new Plot(Color.BLUE, 175, 100, 100, 100, -1));
        graphicsManager.addRenderables(
            List.of(
                new Plot(Color.YELLOW, 200, 150, 100, 100),
                new Plot(Color.RED, 225, 100, 100, 100, 1)
            )
        );
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
        //image.dispose();
    }
}
