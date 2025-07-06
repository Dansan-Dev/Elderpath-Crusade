package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;

public class EmptyTexture extends TextureObject {
    private EmptyTexture(int x, int y, int width, int height) {
        super(
            new Color(1, 1, 1, 0f),
            x, y, width, height
        );
        setParent(new Box(x, y, width, height));
        setBounds(new Box(0, 0, width, height));
    }

    public static EmptyTexture get(int x, int y, int width, int height) {
        return new EmptyTexture(x, y, width, height);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel) {}
}
