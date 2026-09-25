package io.github.elderpath_crusade.rendering;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.assets.AssetService;

import java.util.HashMap;
import java.util.Map;

/**
 * Serves textures/atlas regions to the rest of the app. Prefers an already
 * async-loaded asset from AssetService (queued by LoadingRoom) and falls back
 * to a synchronous load on first use otherwise — so any texture not covered
 * by LoadingRoom's manifest still works, just without the async benefit.
 *
 * Textures resolved via AssetService are NOT cached/disposed here — AssetService
 * owns their lifecycle. Only synchronously-loaded fallbacks are tracked locally.
 */
public class TextureManager {
    public static final String GAME_ATLAS_PATH = "packed/game.atlas";

    private final Map<String, Texture> locallyLoadedTextures = new HashMap<>();
    private TextureAtlas atlas;
    private boolean atlasOwnedLocally = false;

    public TextureManager() {}

    public void loadAtlas(String atlasPath) {
        try {
            if (Gdx.files != null && Gdx.files.internal(atlasPath).exists()) {
                atlas = new TextureAtlas(Gdx.files.internal(atlasPath));
                atlasOwnedLocally = true;
            }
        } catch (Exception e) {
            System.err.println("Failed to load atlas: " + atlasPath + " - " + e.getMessage());
        }
    }

    public TextureAtlas.AtlasRegion getAtlasRegion(String name) {
        TextureAtlas resolved = resolveAtlas();
        if (resolved == null) return null;
        return resolved.findRegion(name);
    }

    private TextureAtlas resolveAtlas() {
        if (atlas != null) return atlas;
        AssetService assets = GameContext.get().getAssets();
        if (assets != null && assets.isLoaded(GAME_ATLAS_PATH)) {
            atlas = assets.getAtlas(GAME_ATLAS_PATH);
        }
        return atlas;
    }

    public Texture getTexture(String path) {
        Texture cached = locallyLoadedTextures.get(path);
        if (cached != null) return cached;

        AssetService assets = GameContext.get().getAssets();
        if (assets != null && assets.isLoaded(path)) {
            return assets.getTexture(path);
        }

        try {
            Texture texture = new Texture(Gdx.files.internal(path));
            locallyLoadedTextures.put(path, texture);
            return texture;
        } catch (Exception e) {
            System.err.println("Failed to load texture: " + path + " - " + e.getMessage());
            return null;
        }
    }

    public void dispose() {
        for (Texture texture : locallyLoadedTextures.values()) {
            texture.dispose();
        }
        locallyLoadedTextures.clear();
        if (atlas != null && atlasOwnedLocally) {
            atlas.dispose();
        }
        atlas = null;
        atlasOwnedLocally = false;
    }
}
