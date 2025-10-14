package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Sprite;

/**
 * Utility class for creating sprites.
 */
public class SpriteCreator {
    /**
     *
     * @param path the path to the image (ex. "images/image.png")
     * @param sheetX the x coordinate of the sprite in the sheet
     * @param sheetY the y coordinate of the sprite in the sheet
     * @param width the width of the sprite in the sheet
     * @param height the height of the sprite in the sheet
     * @param newWidth the width of the sprite after scaling
     * @param newHeight the height of the sprite after scaling
     * @return
     */
    public static Sprite makeSprite(String path, int sheetX, int sheetY, int width, int height, int newWidth, int newHeight) {
        Texture texture = new Texture(Gdx.files.internal(path));
        Sprite sprite = new Sprite(texture, sheetX, sheetY, width, height);
        sprite.setSize(newWidth, newHeight);
        return sprite;
    }
}
