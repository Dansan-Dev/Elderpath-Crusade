package io.github.elderpath_crusade.supers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * The representation of a room in the game
 */
public abstract class Room {
    @Getter private List<Renderable> contents;
    @Getter private List<UIRenderable> ui;

    public Room() {
        this.contents = new ArrayList<>();
        this.ui = new ArrayList<>();
    }

    protected void addContent(Renderable renderable) {
        contents.add(renderable);
    }

    protected void addUI(UIRenderable renderable) {
        ui.add(renderable);
    }

    public void showContent() {
        GameContext.get().getGraphicsManager().addRenderables(contents);
    }

    public void showUI() {
        ui.forEach(r -> GameContext.get().getGraphicsManager().addUIRenderable(r));
    }

    /**
     * Called when the screen size changes (e.g., toggling fullscreen) so the room can
     * recalculate positions/sizes of its contents.
     * Default implementation does nothing.
     */
    public void onScreenResize() {
        // default no-op
    }
}
