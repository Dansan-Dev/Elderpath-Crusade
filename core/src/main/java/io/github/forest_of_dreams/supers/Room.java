package io.github.forest_of_dreams.supers;

import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.GraphicsManager;
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
        GraphicsManager.addRenderables(contents);
    }

    public void showUI() {
        ui.forEach(GraphicsManager::addUIRenderable);
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
