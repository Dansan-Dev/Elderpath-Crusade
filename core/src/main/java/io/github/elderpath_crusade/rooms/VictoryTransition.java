package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * Locks input, then shows the victory screen after a short beat. Called by
 * ClientController.update() once ClientMatchState reports the match ended — nothing on a
 * client ever emits a local GameWonEvent (the match server owns win detection).
 */
public final class VictoryTransition {
    private static final float DELAY = 0.6f;

    private VictoryTransition() {}

    public static void show(PieceAlignment winner) {
        try {
            if (GameContext.get().getInteractionManager().hasActiveSelection()) {
                GameContext.get().getInteractionManager().cancelSelection();
            }
            GameContext.get().getGameManager().lockInteractions();
        } catch (Exception ignored) {}
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                try { GameContext.get().getGameManager().unlockInteractions(); } catch (Exception ignored) {}
                GameContext.get().getRoomManager().gotoRoom(() -> VictoryRoom.get(winner));
            }
        }, DELAY);
    }
}
