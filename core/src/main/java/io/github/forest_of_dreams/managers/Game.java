package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.rooms.MainMenuRoom;
import io.github.forest_of_dreams.supers.Room;

import java.util.function.Supplier;

public class Game {
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
    }
}
