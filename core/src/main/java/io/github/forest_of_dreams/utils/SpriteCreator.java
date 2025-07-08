package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;

public class SpriteCreator {
    public static Sprite makeSprite(String path, int sheetX, int sheetY, int width, int height, int newWidth, int newHeight) {
        Texture texture = new Texture(Gdx.files.internal(path));
        Sprite sprite = new Sprite(texture, sheetX, sheetY, width, height);
        sprite.setSize(newWidth, newHeight);
        return sprite;
    }
}
