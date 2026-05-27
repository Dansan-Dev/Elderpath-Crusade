package io.github.elderpath_crusade;

import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import lombok.Getter;

/**
 * Central service locator for the game. Holds references to all core services.
 * Provides a static accessor during migration; future phases will pass it via constructors.
 */
public class GameContext {
    private static GameContext instance;

    @Getter private final TypedEventBus eventBus;
    @Getter private Board activeBoard;

    private GameContext() {
        this.eventBus = TypedEventBus.get();
    }

    public static GameContext create() {
        instance = new GameContext();
        return instance;
    }

    /**
     * Static accessor for migration. Use this when constructor injection isn't yet available.
     * Will be removed once all callers receive GameContext via constructor.
     */
    public static GameContext get() {
        return instance;
    }

    public void setActiveBoard(Board board) {
        this.activeBoard = board;
    }

    public void clearBoard() {
        this.activeBoard = null;
    }
}
