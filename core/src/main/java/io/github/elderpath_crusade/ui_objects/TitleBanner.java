package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;

import java.util.List;

/**
 * The carved-oak plaque behind the main-menu title (UI-007). The file is {@value #ART_WIDTH}x{@value #ART_HEIGHT}
 * (every art pixel 2x2 at a 1280x720 window); it is drawn at an integer multiple of {@link #NATIVE_WIDTH}x{@link #NATIVE_HEIGHT}
 * so pixels stay crisp ({@link #scaleFor}). Draws nothing, and {@link #isAvailable()} is false, if the art is missing.
 */
public class TitleBanner implements Renderable {
    public static final int ART_WIDTH = 576;
    public static final int ART_HEIGHT = 144;
    /** The art's own pixel grid: the file is this, scaled 2x. */
    public static final int NATIVE_WIDTH = 288;
    public static final int NATIVE_HEIGHT = 72;
    private static final int Z = -1;

    private Texture texture;
    private int x, y, width, height;

    public TitleBanner() {
        String path = ImagePathBackgroundAndUI.TITLE_BANNER.getPath();
        if (Gdx.files != null && Gdx.files.internal(path).exists()) {
            texture = GameContext.get().getTextureManager().getTexture(path);
            if (texture != null) texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
    }

    public boolean isAvailable() {
        return texture != null;
    }

    /** Whole-number multiple of the native art for this window width: 2 at 1280 wide (576x144), 3 at 1920 (864x216). */
    public static int scaleFor(int screenWidth) {
        return Math.max(1, Math.round(screenWidth * 2f / 1280f));
    }

    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(Z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (texture == null || zLevel != Z) return;
        batch.draw(texture, x, y, width, height, 0, 0, ART_WIDTH, ART_HEIGHT, false, false);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        render(batch, zLevel, isPaused);
    }

    @Override public Box getParent() { return null; }
    @Override public void setParent(Box parent) {}
    @Override public Box getBounds() { return new Box(x, y, width, height); }
    @Override public void setBounds(Box bounds) {
        if (bounds != null) setRect(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }
}
