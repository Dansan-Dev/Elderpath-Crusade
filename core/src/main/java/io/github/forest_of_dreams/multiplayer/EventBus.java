package io.github.forest_of_dreams.multiplayer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Minimal static event bus for gameplay events.
 */
public class EventBus {
    private static final Map<GameEventType, List<Consumer<GameEvent>>> listeners = new EnumMap<>(GameEventType.class);

    public static void register(GameEventType type, Consumer<GameEvent> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    public static void unregister(GameEventType type, Consumer<GameEvent> listener) {
        List<Consumer<GameEvent>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    public static void emit(GameEventType type, Map<String, Object> data) {
        List<Consumer<GameEvent>> list = listeners.get(type);
        if (list == null || list.isEmpty()) return;
        GameEvent evt = new GameEvent(type, data);
        // Copy to avoid concurrent modification if listeners change during callback
        List<Consumer<GameEvent>> snapshot = new ArrayList<>(list);
        for (Consumer<GameEvent> c : snapshot) c.accept(evt);
    }
}
