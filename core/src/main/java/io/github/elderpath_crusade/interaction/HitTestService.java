package io.github.elderpath_crusade.interaction;

import io.github.elderpath_crusade.enums.ClickableTargetType;
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
        return findHit(mouseX, mouseY, paused, null);
    }

    /**
     * Find the clickable at the given screen coordinates that matches requiredType.
     * Multiple clickables can overlap the same pixel (e.g. an ability bubble sitting above a
     * plot); rather than always taking the topmost one, this collects every clickable whose
     * bounds contain the point and returns the first one whose class matches requiredType. If
     * requiredType is null, behaves like the plain topmost-wins lookup used to start a new
     * selection.
     */
    public Clickable findHit(int mouseX, int mouseY, boolean paused, ClickableTargetType requiredType) {
        List<Clickable> candidates = collectCandidates(mouseX, mouseY, paused);
        if (requiredType == null) {
            return candidates.isEmpty() ? null : candidates.get(0);
        }
        for (Clickable candidate : candidates) {
            if (requiredType.matches(candidate)) return candidate;
        }
        return null;
    }

    private List<Clickable> collectCandidates(int mouseX, int mouseY, boolean paused) {
        List<Clickable> found = new ArrayList<>();

        // Pass 1: UI elements (reverse order = top-most first)
        List<Clickable> reversed = new ArrayList<>(clickables);
        Collections.reverse(reversed);
        for (Clickable clickable : reversed) {
            if (!(clickable instanceof UIRenderable)) continue;
            if (paused && !clickable.isPauseUIElement()) continue;
            if (clickable.inRange(mouseX, mouseY)) found.add(clickable);
        }

        if (paused) return found;

        // Pass 2: Board plots via O(1) grid lookup
        Board activeBoard = GameContext.get().getActiveBoard();
        if (activeBoard != null && activeBoard.inRange(mouseX, mouseY)) {
            Plot plot = activeBoard.getPlotAtScreen(mouseX, mouseY);
            if (plot != null) found.add(plot);
        }

        // Pass 3: Remaining non-UI clickables
        for (int i = clickables.size() - 1; i >= 0; i--) {
            Clickable clickable = clickables.get(i);
            if (clickable instanceof UIRenderable) continue;
            if (clickable.inRange(mouseX, mouseY)) found.add(clickable);
        }

        return found;
    }
}
