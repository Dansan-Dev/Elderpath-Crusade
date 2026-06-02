package io.github.elderpath_crusade.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

import java.util.HashMap;
import java.util.Map;

public class TextureManager {
    private final Map<String, Texture> textureCache = new HashMap<>();
    private TextureAtlas atlas;

    public TextureManager() {}

    public void loadAtlas(String atlasPath) {
        try {
            if (Gdx.files != null && Gdx.files.internal(atlasPath).exists()) {
                atlas = new TextureAtlas(Gdx.files.internal(atlasPath));
            }
        } catch (Exception e) {
            System.err.println("Failed to load atlas: " + atlasPath + " - " + e.getMessage());
        }
    }

    public TextureAtlas.AtlasRegion getAtlasRegion(String name) {
        if (atlas == null) return null;
        return atlas.findRegion(name);
    }

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
        if (atlas != null) {
            atlas.dispose();
            atlas = null;
        }
    }
}
