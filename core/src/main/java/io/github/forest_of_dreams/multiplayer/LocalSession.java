package io.github.forest_of_dreams.multiplayer;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LocalSession implements MultiplayerSession {
    private final List<Consumer<GameEvent>> listeners = new ArrayList<>();

    @Override
    public void addListener(Consumer<GameEvent> listener) {
        if (listener == null) return;
        listeners.add(listener);
    }

    @Override
    public void removeListener(Consumer<GameEvent> listener) {
        listeners.remove(listener);
    }

    @Override
    public void emit(GameEvent event) {
        // Synchronous, immediate dispatch
        for (Consumer<GameEvent> l : new ArrayList<>(listeners)) {
            l.accept(event);
        }
    }
}
