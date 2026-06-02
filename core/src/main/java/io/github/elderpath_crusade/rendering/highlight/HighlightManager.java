package io.github.elderpath_crusade.rendering.highlight;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;

import java.util.List;

/**
 * Sole source of truth for "what should be glowing right now."
 * Delegates state computation to HighlightState and rendering to HighlightRenderer.
 */
public class HighlightManager implements Renderable {

    private final HighlightState state = new HighlightState();
    private final HighlightRenderer renderer = new HighlightRenderer(state);

    public HighlightManager() {}

    public void update() {
        state.update();
    }

    public boolean isHighlighted(Plot p) { return state.isHighlighted(p); }
    public boolean isCandidate(Plot p) { return state.isCandidate(p); }
    public boolean isAttackCandidate(Plot p) { return state.isAttackCandidate(p); }
    public boolean isFriendlyCandidate(Plot p) { return state.isFriendlyCandidate(p); }

    @Override public List<Integer> getZs() { return renderer.getZs(); }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        renderer.render(batch, zLevel, isPaused);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        renderer.render(batch, zLevel, isPaused, x, y);
    }

    @Override public Box getParent() { return null; }
    @Override public void setParent(Box parent) {}
    @Override public Box getBounds() { return null; }
    @Override public void setBounds(Box bounds) {}
}
