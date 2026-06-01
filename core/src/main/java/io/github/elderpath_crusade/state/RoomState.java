package io.github.elderpath_crusade.state;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.managers.GraphicsManager;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.SelectionOverlay;

import java.util.function.Supplier;

/**
 * Adapter that wraps an existing Room as a GameState.
 * Allows incremental migration: rooms work inside the state machine unchanged.
 */
public class RoomState implements GameState {
    private final Supplier<Room> roomSupplier;
    private Room room;

    public RoomState(Supplier<Room> roomSupplier) {
        this.roomSupplier = roomSupplier;
    }

    @Override
    public void enter(GameContext context) {
        GameContext.get().getGraphicsManager().clearRenderables();
        GameContext.get().getGraphicsManager().clearUIRenderables();
        GameContext.get().getInteractionManager().clearClickables();
        GameContext.get().clearBoard();

        room = roomSupplier.get();
        GameContext.get().getRoomManager().setCurrentRoom(room);
        room.showContent();
        room.showUI();

        // Global overlays
        GameContext.get().getGraphicsManager().addUIRenderable(new SelectionOverlay());
        GameContext.get().getGraphicsManager().addRenderable(GameContext.get().getHighlightManager());
    }

    @Override
    public void update(float delta) {
        // Existing rooms don't have an update method — rendering is handled by GraphicsManager
    }

    @Override
    public void exit() {
        GameContext.get().getGraphicsManager().clearRenderables();
        GameContext.get().getGraphicsManager().clearUIRenderables();
        GameContext.get().getInteractionManager().clearClickables();
        GameContext.get().clearBoard();
        room = null;
    }

    @Override
    public void resize(int width, int height) {
        if (room != null) room.onScreenResize();
    }
}
