package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;

import java.util.List;

public interface UIRenderable {
    Box getParent();
    void setParent(Box parent);
    Box getBounds();
    void setBounds(Box bounds);

    void renderUI(SpriteBatch batch, boolean isPaused);
    void renderUI(SpriteBatch batch, boolean isPaused, int x, int y);
}
