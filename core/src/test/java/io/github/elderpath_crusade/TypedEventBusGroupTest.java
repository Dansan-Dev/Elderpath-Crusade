package io.github.elderpath_crusade;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TypedEventBusGroupTest {

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
    }

    @Test
    void registerScopedListenersReceiveEvents() {
        AtomicInteger count = new AtomicInteger();
        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, e -> count.incrementAndGet());

        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P1));

        assertEquals(1, count.get());
    }

    @Test
    void clearGroupRemovesOnlyThatGroup() {
        AtomicInteger sessionCount = new AtomicInteger();
        AtomicInteger otherCount = new AtomicInteger();
        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, e -> sessionCount.incrementAndGet());
        TypedEventBus.get().registerScoped("other", TurnStartedEvent.class, e -> otherCount.incrementAndGet());

        TypedEventBus.get().clearGroup("session");
        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P1));

        assertEquals(0, sessionCount.get());
        assertEquals(1, otherCount.get());
    }

    @Test
    void ungroupedListenersSurviveClearGroup() {
        AtomicInteger ungrouped = new AtomicInteger();
        AtomicInteger grouped = new AtomicInteger();
        TypedEventBus.get().register(TurnStartedEvent.class, e -> ungrouped.incrementAndGet());
        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, e -> grouped.incrementAndGet());

        TypedEventBus.get().clearGroup("session");
        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P1));

        assertEquals(1, ungrouped.get());
        assertEquals(0, grouped.get());
    }

    @Test
    void clearRemovesEverythingIncludingGroups() {
        AtomicInteger count = new AtomicInteger();
        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, e -> count.incrementAndGet());
        TypedEventBus.get().register(TurnEndedEvent.class, e -> count.incrementAndGet());

        TypedEventBus.get().clear();
        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P1));
        TypedEventBus.get().emit(new TurnEndedEvent(PieceAlignment.P2));

        assertEquals(0, count.get());
    }

    @Test
    void clearGroupOnNonexistentGroupDoesNotThrow() {
        assertDoesNotThrow(() -> TypedEventBus.get().clearGroup("nonexistent"));
    }
}
