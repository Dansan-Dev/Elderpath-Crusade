package io.github.elderpath_crusade.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.path_loaders.ImagePathIcons;

import java.util.EnumMap;
import java.util.Map;

/**
 * Resolves {@link ImagePathIcons} to textures once and caches the answer — including "missing":
 * art is optional (gitignored local files), and {@code TextureManager.getTexture} logs an error
 * on every call for a file that isn't there, so a consumer must never ask per frame.
 */
public final class IconTextures {
    private static final Map<ImagePathIcons, Texture> cache = new EnumMap<>(ImagePathIcons.class);

    private IconTextures() {
    }

    /** The icon's texture, or null when its file is absent (the caller falls back to text). */
    public static Texture get(ImagePathIcons icon) {
        if (icon == null) return null;
        if (cache.containsKey(icon)) return cache.get(icon);
        Texture texture = Gdx.files != null && Gdx.files.internal(icon.getPath()).exists()
                ? GameContext.get().getTextureManager().getTexture(icon.getPath())
                : null;
        cache.put(icon, texture);
        return texture;
    }

    public static boolean available(ImagePathIcons icon) {
        return get(icon) != null;
    }
}
