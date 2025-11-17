package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.rooms.MainMenuRoom;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.SelectionOverlay;

import java.util.function.Supplier;

public class RoomManager {
    public static Room currentRoom;

    public static void initialize() {
        gotoRoom(MainMenuRoom::get);
    }

    public static void clearRoom() {
        GraphicsManager.clearRenderables();
        GraphicsManager.clearUIRenderables();
        InteractionManager.clearClickables();
    }

    /**
     * Navigate to a new room. This always performs a lazy switch: it clears the current
     * room's renderables/UI/clickables first, then constructs the next room and shows it.
     */
    public static void gotoRoom(Supplier<Room> roomSupplier) {
        clearRoom();
        currentRoom = roomSupplier.get();
        currentRoom.showContent();
        currentRoom.showUI();
        // Global overlays (persist per room instance): selection hint
        SelectionOverlay selectionOverlay = new SelectionOverlay();
        GraphicsManager.addUIRenderable(selectionOverlay);
    }
}
