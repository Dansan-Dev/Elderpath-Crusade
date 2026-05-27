package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class TypedEventBusTest {

    private TypedEventBus bus;

    @BeforeEach
    void setUp() {
        bus = new TypedEventBus();
    }

    @Test
    void emitDeliversToRegisteredListener() {
        List<TurnStartedEvent> received = new ArrayList<>();
        bus.register(TurnStartedEvent.class, received::add);

        bus.emit(new TurnStartedEvent(PieceAlignment.P1));

        assertEquals(1, received.size());
        assertEquals(PieceAlignment.P1, received.get(0).player());
    }

    @Test
    void emitDoesNotDeliverToWrongType() {
        List<TurnEndedEvent> received = new ArrayList<>();
        bus.register(TurnEndedEvent.class, received::add);

        bus.emit(new TurnStartedEvent(PieceAlignment.P1));

        assertTrue(received.isEmpty());
    }

    @Test
    void multipleListenersReceiveEvent() {
        AtomicInteger count = new AtomicInteger(0);
        bus.register(ManaChangedEvent.class, e -> count.incrementAndGet());
        bus.register(ManaChangedEvent.class, e -> count.incrementAndGet());

        bus.emit(new ManaChangedEvent(PieceAlignment.P2, 5));

        assertEquals(2, count.get());
    }

    @Test
    void unregisterRemovesListener() {
        List<TurnStartedEvent> received = new ArrayList<>();
        var listener = new java.util.function.Consumer<TurnStartedEvent>() {
            @Override
            public void accept(TurnStartedEvent e) { received.add(e); }
        };
        bus.register(TurnStartedEvent.class, listener);
        bus.unregister(TurnStartedEvent.class, listener);

        bus.emit(new TurnStartedEvent(PieceAlignment.P1));

        assertTrue(received.isEmpty());
    }

    @Test
    void clearRemovesAllListeners() {
        AtomicInteger count = new AtomicInteger(0);
        bus.register(TurnStartedEvent.class, e -> count.incrementAndGet());
        bus.register(TurnEndedEvent.class, e -> count.incrementAndGet());
        bus.clear();

        bus.emit(new TurnStartedEvent(PieceAlignment.P1));
        bus.emit(new TurnEndedEvent(PieceAlignment.P2));

        assertEquals(0, count.get());
    }

    @Test
    void emitWithNoListenersDoesNotThrow() {
        assertDoesNotThrow(() -> bus.emit(new TurnStartedEvent(PieceAlignment.P1)));
    }

    @Test
    void listenerCanUnregisterDuringEmit() {
        // Snapshot iteration should prevent ConcurrentModificationException
        var listener = new java.util.function.Consumer<TurnStartedEvent>() {
            @Override
            public void accept(TurnStartedEvent e) {
                bus.unregister(TurnStartedEvent.class, this);
            }
        };
        bus.register(TurnStartedEvent.class, listener);

        assertDoesNotThrow(() -> bus.emit(new TurnStartedEvent(PieceAlignment.P1)));
    }

    @Test
    void listenerCanRegisterNewListenerDuringEmit() {
        AtomicInteger secondCalled = new AtomicInteger(0);
        bus.register(TurnStartedEvent.class, e -> {
            bus.register(TurnStartedEvent.class, e2 -> secondCalled.incrementAndGet());
        });

        // First emit: registers the second listener but doesn't call it (snapshot)
        bus.emit(new TurnStartedEvent(PieceAlignment.P1));
        assertEquals(0, secondCalled.get());

        // Second emit: both listeners fire
        bus.emit(new TurnStartedEvent(PieceAlignment.P1));
        assertEquals(1, secondCalled.get());
    }

    @Test
    void eventRecordFieldsAreAccessible() {
        List<PieceMovedEvent> received = new ArrayList<>();
        bus.register(PieceMovedEvent.class, received::add);

        bus.emit(new PieceMovedEvent("uuid-1", PieceAlignment.P1, 0, 0, 3, 2,
                PieceMovedEvent.MovementType.ACTIVE, "MANUAL"));

        assertEquals(1, received.size());
        PieceMovedEvent e = received.get(0);
        assertEquals("uuid-1", e.pieceId());
        assertEquals(PieceAlignment.P1, e.owner());
        assertEquals(0, e.fromRow());
        assertEquals(0, e.fromCol());
        assertEquals(3, e.toRow());
        assertEquals(2, e.toCol());
        assertEquals(PieceMovedEvent.MovementType.ACTIVE, e.movementType());
        assertEquals("MANUAL", e.cause());
        assertNull(e.abilityName());
    }

    @Test
    void pieceAttackedEventCompactConstructor() {
        List<PieceAttackedEvent> received = new ArrayList<>();
        bus.register(PieceAttackedEvent.class, received::add);

        bus.emit(new PieceAttackedEvent("a1", PieceAlignment.P1, 1, 2, "d1", 2, 2, 3));

        PieceAttackedEvent e = received.get(0);
        assertEquals(3, e.damage());
        assertNull(e.additionalTargetIds());
        assertNull(e.abilityName());
    }

    @Test
    void actionSpentEventCarriesData() {
        List<ActionSpentEvent> received = new ArrayList<>();
        bus.register(ActionSpentEvent.class, received::add);

        bus.emit(new ActionSpentEvent("piece-1", PieceAlignment.P2, 0));

        assertEquals(1, received.size());
        assertEquals("piece-1", received.get(0).pieceId());
        assertEquals(PieceAlignment.P2, received.get(0).owner());
        assertEquals(0, received.get(0).remaining());
    }

    @Test
    void singletonInstanceWorks() {
        TypedEventBus singleton = TypedEventBus.get();
        assertNotNull(singleton);
        assertSame(singleton, TypedEventBus.get());
    }
}
