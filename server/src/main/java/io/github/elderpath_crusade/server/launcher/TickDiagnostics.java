package io.github.elderpath_crusade.server.launcher;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Rolling match-server tick-timing stats (Sprint 64) — how long each {@code
 * MatchServer.tick(...)} call actually took, against the loop's own tick budget ({@code
 * --tick-ms}), so a slow/overloaded server is visible from the outside (the match-server half
 * of "performance, delays" — see {@code HealthServer}'s {@code /diag} endpoint and {@code
 * deploy/README.md}; client-side frame/music diagnostics are a separate, UI-side concern with
 * nothing to do with this class).
 *
 * <p>Thread-safe (the fields it touches can in principle be read from the HTTP health-check
 * thread while {@link #record} is called from the tick loop thread) but intentionally simple —
 * counters, not a real histogram; good enough to answer "is this instance keeping up" without
 * pulling in a metrics library the way ARCH-034's health check avoided one for the same reason.
 */
final class TickDiagnostics {
    private final long budgetNanos;
    private final AtomicLong tickCount = new AtomicLong();
    private final AtomicLong totalTickNanos = new AtomicLong();
    private final AtomicLong maxTickNanos = new AtomicLong();
    private final AtomicLong overBudgetCount = new AtomicLong();
    private final AtomicLong lastTickNanos = new AtomicLong();

    TickDiagnostics(long budgetNanos) {
        this.budgetNanos = budgetNanos;
    }

    void record(long tickNanos) {
        tickCount.incrementAndGet();
        totalTickNanos.addAndGet(tickNanos);
        maxTickNanos.accumulateAndGet(tickNanos, Math::max);
        if (tickNanos > budgetNanos) overBudgetCount.incrementAndGet();
        lastTickNanos.set(tickNanos);
    }

    /** {"tickCount":N,"budgetMs":B,"lastTickMs":L,"avgTickMs":A,"maxTickMs":M,"overBudgetCount":O} */
    String toJson() {
        long count = tickCount.get();
        double avgMs = count == 0 ? 0.0 : (totalTickNanos.get() / (double) count) / 1_000_000.0;
        return String.format(java.util.Locale.ROOT,
                "{\"tickCount\":%d,\"budgetMs\":%.3f,\"lastTickMs\":%.3f,\"avgTickMs\":%.3f,\"maxTickMs\":%.3f,\"overBudgetCount\":%d}",
                count, budgetNanos / 1_000_000.0, lastTickNanos.get() / 1_000_000.0, avgMs,
                maxTickNanos.get() / 1_000_000.0, overBudgetCount.get());
    }
}
