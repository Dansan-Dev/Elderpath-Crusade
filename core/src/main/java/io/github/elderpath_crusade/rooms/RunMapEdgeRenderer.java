package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunNode;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.GraphicUtils;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Draws a line between every pair of nodes {@link RunMap}'s graph actually connects (BUG-041) —
 * {@code RunRoom} only ever colored individual node buttons, never showed which led to which, so
 * a player with more than one reachable node had no way to see the branching structure. Reads
 * each node button's live {@link Box} bounds every frame rather than caching positions, so it
 * stays correct through a resize ({@code RunRoom.layoutMap} repositions buttons; this just
 * follows). An edge between two already-visited nodes (the path actually taken) draws brighter
 * than one that hasn't been walked yet. Pure drawing, no state of its own, never clickable — same
 * shape as {@code HighlightRenderer} (getBounds()/setBounds() are no-ops there too).
 *
 * <p>Drawn as a thin rotated rectangle (LibGDX has no line primitive on a {@code SpriteBatch} —
 * {@code ShapeRenderer} would need its own begin()/end() outside the pipeline's single batch
 * pass) via the one 1x1 pixel texture {@link GraphicUtils#getPixelTexture} already caches for
 * the same reason {@code HighlightRenderer}'s borders/dots do, rotated around its left-center
 * edge so that pivot point lands exactly on the line's start.
 */
class RunMapEdgeRenderer implements Renderable {
    private static final float THICKNESS = 3f;
    private static final int Z = 0; // behind the node buttons (z=1, see RunRoom.buildNodeButtons)

    private final RunMap map;
    private final Map<Integer, Button> nodeButtons;
    private final Set<Integer> visitedNodeIds;

    RunMapEdgeRenderer(RunMap map, Map<Integer, Button> nodeButtons, Set<Integer> visitedNodeIds) {
        this.map = map;
        this.nodeButtons = nodeButtons;
        this.visitedNodeIds = visitedNodeIds;
    }

    @Override
    public List<Integer> getZs() {
        return List.of(Z);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        if (isPaused) return;
        for (RunNode node : map.nodes()) {
            Button from = nodeButtons.get(node.id());
            if (from == null) continue;
            for (int nextId : node.next()) {
                Button to = nodeButtons.get(nextId);
                if (to == null) continue;
                boolean traveled = visitedNodeIds.contains(node.id()) && visitedNodeIds.contains(nextId);
                Color color = traveled
                        ? ColorSettings.RUN_MAP_EDGE_TRAVELED.getColor()
                        : ColorSettings.RUN_MAP_EDGE.getColor();
                drawLine(batch, centerX(from), centerY(from), centerX(to), centerY(to), color);
            }
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        render(batch, zLevel, isPaused);
    }

    private static float centerX(Button b) {
        Box box = b.getBounds();
        return box.getX() + box.getWidth() / 2f;
    }

    private static float centerY(Button b) {
        Box box = b.getBounds();
        return box.getY() + box.getHeight() / 2f;
    }

    /** A line as a thin rectangle rotated around its own left-center edge, pinned at (x1, y1). */
    private static void drawLine(SpriteBatch batch, float x1, float y1, float x2, float y2, Color color) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.sqrt(dx * dx + dy * dy);
        if (length <= 0f) return;
        float angleDeg = (float) Math.toDegrees(Math.atan2(dy, dx));
        batch.draw(GraphicUtils.getPixelTexture(color),
                x1, y1 - THICKNESS / 2f,
                0f, THICKNESS / 2f,
                length, THICKNESS,
                1f, 1f,
                angleDeg,
                0, 0, 1, 1,
                false, false);
    }

    @Override public Box getParent() { return null; }
    @Override public void setParent(Box parent) {}
    @Override public Box getBounds() { return null; }
    @Override public void setBounds(Box bounds) {}
}
