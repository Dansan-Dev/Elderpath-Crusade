package io.github.forest_of_dreams.supers;

import io.github.forest_of_dreams.game_objects.Board;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.GraphicsManager;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public abstract class Room {
    private Board board;
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

    public void hideContent() {
        GraphicsManager.clearRenderables();
    }

    public void hideUI() {
        GraphicsManager.clearUIRenderables();
    }
}
