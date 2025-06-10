package io.github.forest_of_dreams.interfaces;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public interface Renderable {
    public int getZ();
    public void render(SpriteBatch batch);
}
