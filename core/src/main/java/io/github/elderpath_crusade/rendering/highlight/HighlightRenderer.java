package io.github.elderpath_crusade.rendering.highlight;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.utils.GraphicUtils;

import java.util.List;
import java.util.Map;

/**
 * Renders highlight borders and dots based on HighlightState animation data.
 */
class HighlightRenderer implements Renderable {

    private final HighlightState state;
    private final Color tempColor = new Color();

    HighlightRenderer(HighlightState state) {
        this.state = state;
    }

    @Override
    public List<Integer> getZs() { return List.of(1, 2); }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (isPaused) return;

        for (Map.Entry<Plot, HighlightState.AnimState> entry : state.getStates().entrySet()) {
            Plot plot = entry.getKey();
            HighlightState.AnimState s = entry.getValue();
            int[] pos = plot.calculatePos();
            int x = pos[0];
            int y = pos[1];
            int w = plot.getWidth();
            int h = plot.getHeight();

            if (zLevel == 1) {
                if (s.selectedProgress > 0f) {
                    drawBorder(batch, x, y, w, h, Color.WHITE, s.selectedProgress);
                }
            } else if (zLevel == 2) {
                if (s.candidateProgress > 0f) {
                    drawDot(batch, x, y, w, h, Color.WHITE, s.candidateProgress);
                }
                if (s.attackProgress > 0f) {
                    drawBorder(batch, x, y, w, h, Color.RED, s.attackProgress);
                }
                if (s.friendlyProgress > 0f) {
                    drawBorder(batch, x, y, w, h, Color.GREEN, s.friendlyProgress);
                }
            }
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        render(batch, zLevel, isPaused);
    }

    private void drawBorder(SpriteBatch batch, int absX, int absY, int w, int h, Color color, float progress) {
        int maxThickness = Math.max(2, Math.round(Math.min(w, h) * 0.08f));
        int t = Math.max(1, Math.round(maxThickness * progress));
        batch.draw(GraphicUtils.getPixelTexture(color), absX, absY + h - t, w, t);
        batch.draw(GraphicUtils.getPixelTexture(color), absX, absY, w, t);
        batch.draw(GraphicUtils.getPixelTexture(color), absX, absY, t, h);
        batch.draw(GraphicUtils.getPixelTexture(color), absX + w - t, absY, t, h);
    }

    private void drawDot(SpriteBatch batch, int absX, int absY, int w, int h, Color color, float progress) {
        int baseSize = Math.max(2, Math.round(Math.min(w, h) * 0.25f));
        int s = Math.max(1, Math.round(baseSize * progress));
        int cx = absX + (w - s) / 2;
        int cy = absY + (h - s) / 2;

        tempColor.set(color);
        tempColor.a *= progress;
        batch.draw(GraphicUtils.getPixelTexture(tempColor), cx, cy, s, s);
    }

    @Override public Box getParent() { return null; }
    @Override public void setParent(Box parent) {}
    @Override public Box getBounds() { return null; }
    @Override public void setBounds(Box bounds) {}
}
