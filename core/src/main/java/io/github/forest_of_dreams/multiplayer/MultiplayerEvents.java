package io.github.forest_of_dreams.multiplayer;

import java.util.function.Consumer;

/**
 * Simple global access point to the current multiplayer session.
 * For now uses a LocalSession to synchronously dispatch events.
 */
public final class MultiplayerEvents {
    private static MultiplayerSession session = new LocalSession();

    private MultiplayerEvents() {}

    public static MultiplayerSession getSession() { return session; }

    public static void setSession(MultiplayerSession s) {
        if (s != null) session = s;
    }

    public static void addListener(Consumer<GameEvent> listener) { session.addListener(listener); }
    public static void removeListener(Consumer<GameEvent> listener) { session.removeListener(listener); }
    public static void emit(GameEvent event) { session.emit(event); }
}
