package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;

import java.util.List;

public interface Renderable {
    Box getParent();
    void setParent(Box parent);
    Box getBounds();
    void setBounds(Box bounds);

    List<Integer> getZs();
    void render(SpriteBatch batch, int zLevel, boolean isPaused);
    void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y);
}
