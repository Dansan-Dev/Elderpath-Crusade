package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.game_objects.EmptyTexture;
import io.github.forest_of_dreams.game_objects.Plot;
import io.github.forest_of_dreams.game_objects.TextureObject;
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
        TextureObject plot = new TextureObject(Color.valueOf("#32943a"), 0, 0, 100, 100);
        TextureObject plotDirt = new TextureObject(Color.BROWN, 0, -50, 100, 50);
        List<Renderable> plots = List.of(
            new TextureObject(Color.BLUE, 175, 100, 100, 100, -1),
            new TextureObject(Color.YELLOW, 200, 150, 100, 100),
            new TextureObject(Color.RED, 225, 100, 100, 100, 1)
        );
        plots.forEach(r -> {
            TextureObject p = (TextureObject) r;
            Color c = p.getColor().cpy().lerp(Color.BLACK, 0.5f);
            p.setHoverColor(c);
        });
        graphicsManager.addRenderables(
            plots
        );
        graphicsManager.addRenderable(new Plot(100, 100, plot, plotDirt));
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
