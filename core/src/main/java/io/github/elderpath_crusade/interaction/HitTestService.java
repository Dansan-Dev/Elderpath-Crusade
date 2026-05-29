package io.github.elderpath_crusade.interaction;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.GameContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Spatial query service: determines what clickable element is under the cursor.
 * Extracted from InteractionManager for single-responsibility.
 */
public class HitTestService {
    private final List<Clickable> clickables;

    public HitTestService(List<Clickable> clickables) {
        this.clickables = clickables;
    }

    /**
     * Find the topmost clickable at the given screen coordinates.
     * UI elements are checked first (top layer), then board plots (O(1)), then world clickables.
     */
    public Clickable findHit(int mouseX, int mouseY, boolean paused) {
        // Pass 1: UI elements (reverse order = top-most first)
        List<Clickable> reversed = new ArrayList<>(clickables);
        Collections.reverse(reversed);
        for (Clickable clickable : reversed) {
            if (!(clickable instanceof UIRenderable)) continue;
            if (paused && !clickable.isPauseUIElement()) continue;
            if (clickable.inRange(mouseX, mouseY)) return clickable;
        }

        if (paused) return null;

        // Pass 2: Board plots via O(1) grid lookup
        Board activeBoard = GameContext.get().getActiveBoard();
        if (activeBoard != null && activeBoard.inRange(mouseX, mouseY)) {
            Plot plot = activeBoard.getPlotAtScreen(mouseX, mouseY);
            if (plot != null) return plot;
        }

        // Pass 3: Remaining non-UI clickables
        for (int i = clickables.size() - 1; i >= 0; i--) {
            Clickable clickable = clickables.get(i);
            if (clickable instanceof UIRenderable) continue;
            if (clickable.inRange(mouseX, mouseY)) return clickable;
        }

        return null;
    }
}
