package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;

import java.util.List;

public interface UIRenderable {
    public Box getParent();
    public void setParent(Box parent);
    public Box getBounds();
    public void setBounds(Box bounds);

    public void renderUI(SpriteBatch batch, boolean isPaused);
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y);
}
