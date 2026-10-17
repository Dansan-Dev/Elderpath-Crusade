package io.github.elderpath_crusade.match;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MatchClockTest {

    @Test
    void tick_runsDueTasksInDueOrder_andLeavesLaterOnesPending() {
        MatchClock clock = new MatchClock();
        List<String> ran = new ArrayList<>();
        clock.schedule(0.5f, () -> ran.add("b"));
        clock.schedule(0.2f, () -> ran.add("a"));
        clock.schedule(2f, () -> ran.add("c"));

        clock.tick(0.1f);
        assertTrue(ran.isEmpty());
        clock.tick(0.5f);
        assertEquals(List.of("a", "b"), ran);
        assertTrue(clock.hasPendingTasks());
        clock.tick(2f);
        assertEquals(List.of("a", "b", "c"), ran);
        assertFalse(clock.hasPendingTasks());
    }

    @Test
    void taskScheduledByARunningTask_waitsForALaterTick() {
        MatchClock clock = new MatchClock();
        List<Integer> steps = new ArrayList<>();
        clock.schedule(0f, () -> {
            steps.add(1);
            clock.schedule(0f, () -> steps.add(2));
        });

        clock.tick(0f);
        assertEquals(List.of(1), steps);
        clock.tick(0f);
        assertEquals(List.of(1, 2), steps);
    }

    @Test
    void clear_dropsPendingTasks() {
        MatchClock clock = new MatchClock();
        List<Integer> ran = new ArrayList<>();
        clock.schedule(0.1f, () -> ran.add(1));
        clock.clear();
        clock.tick(1f);
        assertTrue(ran.isEmpty());
    }
}
