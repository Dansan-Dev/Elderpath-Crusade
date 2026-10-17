package io.github.elderpath_crusade.match;

import java.util.ArrayList;
import java.util.List;

/**
 * Delayed-task scheduler advanced explicitly by whoever owns the game loop — Main.render
 * locally, a server tick or a test headlessly. Replaces LibGDX's Timer for simulation
 * pacing (bot steps), which needs a running Gdx.app and so can't drive a headless match.
 * Single-threaded: schedule and tick from the same (main/server) thread.
 */
public class MatchClock {
    private record Task(float dueAt, Runnable action) {}

    private final List<Task> tasks = new ArrayList<>();
    private float now = 0f;

    /** Runs action once, delaySeconds of ticked time from now. */
    public void schedule(float delaySeconds, Runnable action) {
        tasks.add(new Task(now + Math.max(0f, delaySeconds), action));
    }

    /** Advances time and runs every task that became due, in due order. Tasks scheduled by a running task wait for a later tick. */
    public void tick(float deltaSeconds) {
        now += Math.max(0f, deltaSeconds);
        if (tasks.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        tasks.removeIf(t -> {
            if (t.dueAt() > now) return false;
            due.add(t);
            return true;
        });
        due.sort((a, b) -> Float.compare(a.dueAt(), b.dueAt()));
        for (Task t : due) t.action().run();
    }

    public boolean hasPendingTasks() {
        return !tasks.isEmpty();
    }

    /** Drops every pending task (e.g. between matches). */
    public void clear() {
        tasks.clear();
    }
}
