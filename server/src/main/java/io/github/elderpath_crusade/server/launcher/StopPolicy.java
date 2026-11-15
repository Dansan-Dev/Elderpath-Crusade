package io.github.elderpath_crusade.server.launcher;

/**
 * When a one-match server should exit by itself: shortly after its match ended (at once if
 * nobody is connected any more), or once no human has been connected for the idle timeout.
 * Pure arithmetic on a monotonic clock so it can be tested without a process or a socket.
 */
final class StopPolicy {
    private final boolean hasHumanSeats;
    private final long idleNanos;
    private final long graceNanos;
    private long lastConnected;
    private long endedAt = -1;
    private boolean endedNow;

    StopPolicy(boolean hasHumanSeats, long idleNanos, long graceNanos, long startNanos) {
        this.hasHumanSeats = hasHumanSeats;
        this.idleNanos = idleNanos;
        this.graceNanos = graceNanos;
        this.lastConnected = startNanos;
    }

    /** Call once per tick; returns "match ended", "idle", or null to keep running. */
    String check(long now, boolean anyConnected, boolean matchEnded) {
        endedNow = false;
        if (anyConnected) lastConnected = now;
        if (matchEnded && endedAt < 0) {
            endedAt = now;
            endedNow = true;
        }
        if (endedAt >= 0 && (!anyConnected || now - endedAt >= graceNanos)) return "match ended";
        if (hasHumanSeats && !anyConnected && now - lastConnected >= idleNanos) return "idle";
        return null;
    }

    /** True only for the check() call that first saw the match end. */
    boolean matchJustEnded() {
        return endedNow;
    }
}
