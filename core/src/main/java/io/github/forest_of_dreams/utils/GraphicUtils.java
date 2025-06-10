package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;

public class GraphicUtils {

    public static Texture getPixelTexture(Color color) {
        Texture pixel;
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(color);
        pixmap.fill();
        pixel = new Texture(pixmap);
        pixmap.dispose();
        return pixel;
    }
}
