package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;

import java.util.HashMap;
import java.util.Map;

public class TextureManager {
    private final Map<String, Texture> textureCache = new HashMap<>();

    public TextureManager() {}

    public Texture getTexture(String path) {
        if (textureCache.containsKey(path)) {
            return textureCache.get(path);
        }
        try {
            Texture texture = new Texture(Gdx.files.internal(path));
            textureCache.put(path, texture);
            return texture;
        } catch (Exception e) {
            System.err.println("Failed to load texture: " + path + " - " + e.getMessage());
            return null;
        }
    }

    public void dispose() {
        for (Texture texture : textureCache.values()) {
            texture.dispose();
        }
        textureCache.clear();
    }
}
