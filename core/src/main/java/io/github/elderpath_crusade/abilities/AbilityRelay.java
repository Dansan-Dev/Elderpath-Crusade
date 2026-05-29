package io.github.elderpath_crusade.abilities;

import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.GameContext;

import java.util.function.Consumer;

/**
 * Central relay that forwards TypedEventBus events to TriggeredAbility instances
 * on all living pieces across active Boards.
 */
public final class AbilityRelay {
    private static boolean started = false;
    private static Consumer<GameEvent> relayAll;

    private AbilityRelay() {}

    public static void startIfNeeded() {
        if (started) return;
        started = true;
        relayAll = AbilityRelay::onGameEvent;
        // Register for all concrete event types
        TypedEventBus bus = TypedEventBus.get();
        bus.register(io.github.elderpath_crusade.events.TurnStartedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.TurnEndedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceSpawnedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceMovedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceAttackedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceDiedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.ActionSpentEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.ActionsResetEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.ManaChangedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardDrawnEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardShuffledEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardDiscardedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardPlayedEvent.class, e -> relayAll.accept(e));
    }

    public static void stop() {
        if (!started) return;
        // Clear all listeners (simple approach; in production you'd track and unregister each)
        started = false;
        relayAll = null;
    }

    private static void onGameEvent(GameEvent event) {
        Board board = GameContext.get().getActiveBoard();
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
