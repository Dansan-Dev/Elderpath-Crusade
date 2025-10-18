package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.rooms.MainMenuRoom;
import io.github.forest_of_dreams.supers.Room;

public class Game {
    public static Room currentRoom;

    public static void initialize() {
        gotoRoom(MainMenuRoom.get());
    }

    public static void clearRoom() {
        GraphicsManager.clearRenderables();
        GraphicsManager.clearUIRenderables();
        InteractionManager.clearClickables();
    }

    public static void gotoRoom(Room room) {
        clearRoom();
        currentRoom = room;

        currentRoom.showContent();
        currentRoom.showUI();
    }
}
