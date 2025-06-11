package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;

public class EmptyTexture extends TextureObject {
    private EmptyTexture(int x, int y, int width, int height) {
        super(Color.WHITE, x, y, width, height);
    }

    public static EmptyTexture get(int x, int y, int width, int height) {
        return new EmptyTexture(x, y, width, height);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel) {}
}
