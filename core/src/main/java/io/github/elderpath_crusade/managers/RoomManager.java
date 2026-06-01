package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.rooms.MainMenuRoom;
import io.github.elderpath_crusade.state.RoomState;
import io.github.elderpath_crusade.supers.Room;
import lombok.Getter;
import lombok.Setter;

import java.util.function.Supplier;

/**
 * Room navigation facade. Delegates to GameStateMachine internally.
 */
public class RoomManager {
    @Getter @Setter private Room currentRoom;

    public RoomManager() {}

    public void initialize() {
        gotoRoom(MainMenuRoom::get);
    }

    public void clearRoom() {
        // No-op: state machine handles clearing in RoomState.exit()/enter()
    }

    /**
     * Navigate to a new room via the state machine.
     */
    public void gotoRoom(Supplier<Room> roomSupplier) {
        GameContext ctx = GameContext.get();
        if (ctx != null && ctx.getStateMachine() != null) {
            ctx.getStateMachine().transition(new RoomState(roomSupplier));
        }
    }
}
