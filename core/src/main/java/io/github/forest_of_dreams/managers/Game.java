package io.github.forest_of_dreams.managers;

import io.github.forest_of_dreams.rooms.MainMenuRoom;
import io.github.forest_of_dreams.supers.Room;

public class Game {
    public static Room currentRoom;

    public static void initialize() {
        gotoRoom(MainMenuRoom.get());
    }

    public static void gotoRoom(Room room) {
        if (currentRoom != null) {
            currentRoom.hideContent();
            currentRoom.hideUI();
        }

        currentRoom = room;

        currentRoom.showContent();
        currentRoom.showUI();
    }
}
