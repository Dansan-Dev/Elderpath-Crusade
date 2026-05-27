package io.github.elderpath_crusade.multiplayer;

import io.github.elderpath_crusade.events.GameEvent;
import java.util.function.Consumer;

public interface MultiplayerSession {
    void addListener(Consumer<GameEvent> listener);
    void removeListener(Consumer<GameEvent> listener);
    void emit(GameEvent event);
}
