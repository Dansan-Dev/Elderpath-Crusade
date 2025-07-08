package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.managers.SettingsManager;

import java.util.List;

public class PauseScreen implements Renderable {
    private final Color backgroundColor = new Color(0, 0, 0, 0.6f);
    private final Color textColor = new Color(1, 1, 1, 1);
    private final TextureObject background;

    public PauseScreen() {
        background = new TextureObject(backgroundColor, 0, 0, 100, 100);
        background.setColor(backgroundColor);
        background.setZ(10);
    }

    @Override
    public Box getParent() {
        return null;
    }

    @Override
    public void setParent(Box parent) {

    }

    @Override
    public Box getBounds() {
        return null;
    }

    @Override
    public void setBounds(Box bounds) {

    }

    @Override
    public List<Integer> getZs() {
        return List.of();
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        int[] screenSize = SettingsManager.screenSize.getCurrentSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, zLevel, isPaused);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {}
}
