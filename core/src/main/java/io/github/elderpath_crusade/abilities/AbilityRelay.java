package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.managers.BoardManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;

import java.util.function.Consumer;

/**
 * Central relay that forwards GameEventBus events to TriggeredAbility instances
 * on all living pieces across active Boards. This avoids each ability registering
 * its own listeners unless it needs specialized behavior.
 */
public final class AbilityRelay {
    private static boolean started = false;
    private static Consumer<GameEvent> listener;

    private AbilityRelay() {}

    public static void startIfNeeded() {
        if (started) return;
        started = true;
        listener = AbilityRelay::onGameEvent;
        for (GameEventType t : GameEventType.values()) {
            EventBus.register(t, listener);
        }
    }

    public static void stop() {
        if (!started) return;
        for (GameEventType t : GameEventType.values()) {
            EventBus.unregister(t, listener);
        }
        listener = null;
        started = false;
    }

    private static void onGameEvent(GameEvent event) {
        // Resolve the active Board and deliver to TriggeredAbility on pieces
        Board board = BoardManager.getBoard();
        if (board != null) {
            for (int row = 0; row < board.getROWS(); row++) {
                for (int col = 0; col < board.getCOLS(); col++) {
                    GamePiece gp = board.getGamePieceAtPos(row, col);
                    if (gp instanceof MonsterGamePiece mgp) {
                        for (Ability a : mgp.getAbilities()) {
                            if (a instanceof TriggeredAbility trig) {
                                try { trig.onGameEvent(event); } catch (Exception ignored) {}
                            }
                        }
                    }
                }
            }
        }
    }
}
