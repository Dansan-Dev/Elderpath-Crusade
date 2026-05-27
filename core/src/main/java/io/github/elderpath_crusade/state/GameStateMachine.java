package io.github.elderpath_crusade.state;

import io.github.elderpath_crusade.GameContext;
import lombok.Getter;

/**
 * Manages game state transitions with explicit enter/exit lifecycle.
 */
public class GameStateMachine {
    private final GameContext context;
    @Getter private GameState currentState;

    public GameStateMachine(GameContext context) {
        this.context = context;
    }

    /**
     * Transition to a new state. Calls exit() on current, then enter() on new.
     */
    public void transition(GameState newState) {
        if (currentState != null) {
            currentState.exit();
        }
        currentState = newState;
        if (currentState != null) {
            currentState.enter(context);
        }
    }

    public void update(float delta) {
        if (currentState != null) {
            currentState.update(delta);
        }
    }

    public void resize(int width, int height) {
        if (currentState != null) {
            currentState.resize(width, height);
        }
    }
}
