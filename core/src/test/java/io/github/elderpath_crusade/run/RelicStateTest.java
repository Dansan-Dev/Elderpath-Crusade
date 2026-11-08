package io.github.elderpath_crusade.run;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RelicStateTest {

    @Test
    void startsEmpty() {
        RelicState state = new RelicState();
        assertTrue(state.activeIds().isEmpty());
        assertFalse(state.isActive("PackLeader"));
    }

    @Test
    void add_makesARelicActive() {
        RelicState state = new RelicState();
        state.add("PackLeader");
        assertTrue(state.isActive("PackLeader"));
        assertEquals(List.of("PackLeader"), state.activeIds());
    }

    @Test
    void add_isIdempotent() {
        RelicState state = new RelicState();
        state.add("PackLeader");
        state.add("PackLeader");
        assertEquals(List.of("PackLeader"), state.activeIds());
    }

    @Test
    void add_rejectsBlank() {
        RelicState state = new RelicState();
        assertThrows(IllegalArgumentException.class, () -> state.add(""));
        assertThrows(IllegalArgumentException.class, () -> state.add(null));
    }

    @Test
    void constructor_seedsFromInitialList() {
        RelicState state = new RelicState(List.of("A", "B"));
        assertEquals(List.of("A", "B"), state.activeIds());
    }
}
