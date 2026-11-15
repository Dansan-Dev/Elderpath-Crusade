package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;

import java.util.List;

/**
 * A dark wooden panel with a bronze border (ART-005 follow-up) drawn behind the scrolling text of
 * text-heavy screens (info/credits, licenses, How to Play, Collection). A nine-patch at an integer
 * scale so its pixel-art border stays crisp; the room sets its rect from its own clip region
 * ({@link #setRect}) at layout time. Add it to the room <b>before</b> anything else at its z — same-z
 * renderables draw in insertion order. Draws nothing if the panel art is missing.
 */
public class TextPanel implements Renderable {
    private static final int INSET = 8;   // border + corner rivets of ui/text_panel.png
    private static final int SCALE = 2;
    private static final int Z = 0;
    private static NinePatch patch;
    private static boolean resolved;

    private int x, y, width, height;

    public void setRect(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    private static NinePatch patch() {
        if (!resolved) {
            resolved = true;
            String path = ImagePathBackgroundAndUI.TEXT_PANEL.getPath();
            if (com.badlogic.gdx.Gdx.files != null && com.badlogic.gdx.Gdx.files.internal(path).exists()) {
                Texture texture = GameContext.get().getTextureManager().getTexture(path);
                if (texture != null) {
                    patch = new NinePatch(texture, INSET, INSET, INSET, INSET);
                    patch.scale(SCALE, SCALE);
                }
            }
        }
        return patch;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(Z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        NinePatch p = patch();
        if (p == null || width <= 0 || height <= 0) return;
        p.draw(batch, x, y, width, height);
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
