package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.rooms.VictoryRoom;

public final class VictoryHandler {
    private boolean initialized = false;

    public VictoryHandler() {}

    public void initialize() {
        if (initialized) return;
        initialized = true;
        TypedEventBus.get().register(GameWonEvent.class, VictoryHandler::onGameWon);
    }

    private static void onGameWon(GameWonEvent event) {
        PieceAlignment winner = event.winner();
        try {
            if (InteractionManager.hasActiveSelection()) {
                InteractionManager.cancelSelection();
            }
            GameContext.get().getGameManager().lockInteractions();
        } catch (Exception ignored) {}
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                try { GameContext.get().getGameManager().unlockInteractions(); } catch (Exception ignored) {}
                RoomManager.gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, 0.6f);
    }
}
