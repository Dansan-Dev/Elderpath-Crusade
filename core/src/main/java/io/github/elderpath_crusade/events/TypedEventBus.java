package io.github.elderpath_crusade.events;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Type-safe event bus. Listeners subscribe by event class and receive strongly-typed events.
 */
public class TypedEventBus {
    private static final TypedEventBus INSTANCE = new TypedEventBus();

    private final Map<Class<? extends GameEvent>, List<Consumer<?>>> listeners = new HashMap<>();
    private final Map<String, List<GroupEntry>> groups = new HashMap<>();

    private record GroupEntry(Class<? extends GameEvent> type, Consumer<?> listener) {}

    public static TypedEventBus get() {
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void register(Class<T> type, Consumer<T> listener) {
        listeners.computeIfAbsent(type, k -> new ArrayList<>()).add(listener);
    }

    public <T extends GameEvent> void registerScoped(String group, Class<T> type, Consumer<T> listener) {
        register(type, listener);
        groups.computeIfAbsent(group, k -> new ArrayList<>()).add(new GroupEntry(type, listener));
    }

    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void unregister(Class<T> type, Consumer<T> listener) {
        List<Consumer<?>> list = listeners.get(type);
        if (list != null) list.remove(listener);
    }

    public void clearGroup(String group) {
        List<GroupEntry> entries = groups.remove(group);
        if (entries == null) return;
        for (GroupEntry entry : entries) {
            List<Consumer<?>> list = listeners.get(entry.type());
            if (list != null) list.remove(entry.listener());
        }
    }

    @SuppressWarnings("unchecked")
    public <T extends GameEvent> void emit(T event) {
        List<Consumer<?>> list = listeners.get(event.getClass());
        if (list == null || list.isEmpty()) return;
        List<Consumer<?>> snapshot = new ArrayList<>(list);
        for (Consumer<?> c : snapshot) {
            ((Consumer<T>) c).accept(event);
        }
    }

    /**
     * Remove all listeners and groups. Useful for testing and state resets.
     */
    public void clear() {
        listeners.clear();
        groups.clear();
    }
}
