package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.rooms.MainMenuRoom;
import io.github.elderpath_crusade.state.RoomState;
import io.github.elderpath_crusade.supers.Room;

import java.util.function.Supplier;

/**
 * Room navigation facade. Delegates to GameStateMachine internally.
 * Existing callers continue to use RoomManager.gotoRoom() unchanged.
 */
public class RoomManager {
    public static Room currentRoom;

    public static void initialize() {
        gotoRoom(MainMenuRoom::get);
    }

    public static void clearRoom() {
        // No-op: state machine handles clearing in RoomState.exit()/enter()
    }

    /**
     * Navigate to a new room via the state machine.
     */
    public static void gotoRoom(Supplier<Room> roomSupplier) {
        GameContext ctx = GameContext.get();
        if (ctx != null && ctx.getStateMachine() != null) {
            ctx.getStateMachine().transition(new RoomState(roomSupplier));
        }
        // Keep currentRoom reference for backward compat (e.g., onScreenResize)
        // The RoomState adapter sets it up internally via the supplier
    }
}
