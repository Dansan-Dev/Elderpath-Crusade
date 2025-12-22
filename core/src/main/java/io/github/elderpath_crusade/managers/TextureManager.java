package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.ObjectMap;

import java.util.HashMap;
import java.util.Map;

/**
 * Centralized manager for loading and caching textures to avoid memory leaks.
 */
public final class TextureManager {
    private static final Map<String, Texture> textureCache = new HashMap<>();

    private TextureManager() {}

    /**
     * Gets a texture from the cache, or loads it if not present.
     * @param path Internal path to the texture.
     * @return The cached or newly loaded Texture.
     */
    public static Texture getTexture(String path) {
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

    /**
     * Disposes all cached textures and clears the cache.
     */
    public static void dispose() {
        for (Texture texture : textureCache.values()) {
            texture.dispose();
        }
        textureCache.clear();
    }
}
