package io.github.elderpath_crusade.net.transport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Throttles guessing: after maxFailures failures for the same key inside the window the key is
 * locked out, for a time that doubles every time the lockout is earned again (up to a cap). Keys
 * are things worth guarding — a client address, a lobby — so a guesser can neither spread
 * attempts over many connections nor wait out a fixed delay. A success clears the key's failures.
 * Not thread-safe.
 */
public final class AttemptLimiter {
    private final int maxFailures;
    private final long windowNanos;
    private final long firstLockNanos;
    private final long maxLockNanos;
    private final Map<String, Deque<Long>> failures = new HashMap<>();
    private final Map<String, Long> lockedUntil = new HashMap<>();
    private final Map<String, Long> lockLength = new HashMap<>();

    public AttemptLimiter(int maxFailures, long windowNanos, long firstLockNanos, long maxLockNanos) {
        this.maxFailures = maxFailures;
        this.windowNanos = windowNanos;
        this.firstLockNanos = firstLockNanos;
        this.maxLockNanos = maxLockNanos;
    }

    public boolean isLocked(String key, long now) {
        Long until = lockedUntil.get(key);
        return until != null && now - until < 0;
    }

    /** Seconds until key is allowed again, 0 if it is not locked. */
    public long secondsLeft(String key, long now) {
        Long until = lockedUntil.get(key);
        return until == null || now - until >= 0 ? 0 : (until - now + 999_999_999L) / 1_000_000_000L;
    }

    /** Records a failed attempt; returns true if this one locked the key out. */
    public boolean recordFailure(String key, long now) {
        Deque<Long> window = failures.computeIfAbsent(key, k -> new ArrayDeque<>());
        while (!window.isEmpty() && now - window.peekFirst() >= windowNanos) window.pollFirst();
        window.addLast(now);
        if (window.size() < maxFailures) return false;
        window.clear();
        long length = lockLength.merge(key, firstLockNanos, (old, first) -> Math.min(old * 2, maxLockNanos));
        lockedUntil.put(key, now + length);
        return true;
    }

    /** A correct guess: forget this key's failures and lock history. */
    public void recordSuccess(String key) {
        failures.remove(key);
        lockLength.remove(key);
        lockedUntil.remove(key);
    }

    /**
     * Drops keys that have been quiet long enough to start over (no recent failures, and any lock
     * ended more than twice the longest lock ago), so the maps can't grow without bound. A key
     * that earned a lock keeps its doubled length until then.
     */
    public void prune(long now) {
        failures.entrySet().removeIf(e -> {
            Deque<Long> window = e.getValue();
            while (!window.isEmpty() && now - window.peekFirst() >= windowNanos) window.pollFirst();
            return window.isEmpty();
        });
        lockedUntil.entrySet().removeIf(e -> {
            boolean decayed = now - e.getValue() >= 2 * maxLockNanos && !failures.containsKey(e.getKey());
            if (decayed) lockLength.remove(e.getKey());
            return decayed;
        });
    }
}
