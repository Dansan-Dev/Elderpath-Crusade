package io.github.forest_of_dreams;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.ScreenUtils;
import io.github.forest_of_dreams.game_objects.Plot;
import io.github.forest_of_dreams.utils.GraphicUtils;

/** {@link com.badlogic.gdx.ApplicationListener} implementation shared by all platforms. */
public class Main extends ApplicationAdapter {
    private SpriteBatch batch;
    private Plot plot;

    @Override
    public void create() {
        batch = new SpriteBatch();
        plot = new Plot(null, 100, 100, 100, 100);
    }

    @Override
    public void render() {
        ScreenUtils.clear(0.15f, 0.15f, 0.2f, 1f);
        batch.begin();
        plot.render(batch);
        batch.end();
    }

    @Override
    public void dispose() {
        batch.dispose();
        //image.dispose();
    }
}
