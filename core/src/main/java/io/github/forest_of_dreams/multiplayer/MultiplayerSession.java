package io.github.forest_of_dreams.multiplayer;

import java.util.function.Consumer;

public interface MultiplayerSession {
    void addListener(Consumer<GameEvent> listener);
    void removeListener(Consumer<GameEvent> listener);
    void emit(GameEvent event);
}
