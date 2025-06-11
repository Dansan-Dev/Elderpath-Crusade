package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;

import java.util.List;

public interface Renderable {
    public Box getParent();
    public void setParent(Box parent);

    public List<Integer> getZs();
    public void render(SpriteBatch batch, int zLevel);
}
