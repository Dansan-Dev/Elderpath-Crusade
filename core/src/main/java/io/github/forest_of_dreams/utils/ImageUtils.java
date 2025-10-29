package io.github.forest_of_dreams.utils;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Pixmap;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight helpers for querying image dimensions and aspect ratios for UI sizing.
 * Caches results per internal path to avoid reloading pixmaps.
 */
public final class ImageUtils {
    private static final Map<String, int[]> SIZE_CACHE = new ConcurrentHashMap<>();

    private ImageUtils() {}

    /**
     * Returns the width and height of the image at the given LibGDX internal path.
     * Path should be relative to the assets/ root, e.g., "images/displace_ability.png".
     * Returns null if the image cannot be loaded.
     */
    public static int[] getImageSize(String internalPath) {
        if (internalPath == null || internalPath.isBlank()) return null;
        int[] cached = SIZE_CACHE.get(internalPath);
        if (cached != null) return cached.clone();
        Pixmap pm = null;
        try {
            pm = new Pixmap(Gdx.files.internal(internalPath));
            int[] size = new int[]{pm.getWidth(), pm.getHeight()};
            SIZE_CACHE.put(internalPath, size);
            return size.clone();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (pm != null) pm.dispose();
        }
    }

    /** Returns width/height as a float, or 1f if unavailable/invalid. */
    public static float getAspectRatio(String internalPath) {
        int[] sz = getImageSize(internalPath);
        if (sz == null || sz.length < 2 || sz[1] == 0) return 1f;
        return (float) sz[0] / (float) sz[1];
    }
}
