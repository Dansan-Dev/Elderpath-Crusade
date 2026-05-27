package io.github.elderpath_crusade.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;

/**
 * Centralized asset loading and retrieval. Wraps LibGDX AssetManager
 * for lifecycle management, caching, and typed access.
 */
public class AssetService {
    private final AssetManager manager;

    public AssetService() {
        this.manager = new AssetManager();
    }

    // --- Loading ---

    public void loadTexture(String path) {
        manager.load(path, Texture.class);
    }

    public void loadAtlas(String path) {
        manager.load(path, TextureAtlas.class);
    }

    public void loadMusic(String path) {
        manager.load(path, Music.class);
    }

    public void loadSound(String path) {
        manager.load(path, Sound.class);
    }

    /** Block until all queued assets are loaded. */
    public void finishLoading() {
        manager.finishLoading();
    }

    /** Non-blocking progress update. Returns true when all assets are loaded. */
    public boolean update() {
        return manager.update();
    }

    public float getProgress() {
        return manager.getProgress();
    }

    // --- Retrieval ---

    public Texture getTexture(String path) {
        return manager.get(path, Texture.class);
    }

    public TextureAtlas getAtlas(String path) {
        return manager.get(path, TextureAtlas.class);
    }

    public Music getMusic(String path) {
        return manager.get(path, Music.class);
    }

    public Sound getSound(String path) {
        return manager.get(path, Sound.class);
    }

    public boolean isLoaded(String path) {
        return manager.isLoaded(path);
    }

    // --- Lifecycle ---

    public void unload(String path) {
        if (manager.isLoaded(path)) manager.unload(path);
    }

    public void dispose() {
        manager.dispose();
    }
}
