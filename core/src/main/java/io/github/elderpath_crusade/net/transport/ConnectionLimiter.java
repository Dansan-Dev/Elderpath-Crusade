package io.github.elderpath_crusade.net.transport;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * Decides whether a newly accepted connection may stay, the way public servers usually do:
 * <ul>
 *   <li>a cap on simultaneously open connections per client address,</li>
 *   <li>a sliding-window rate limit on new connections per address, and a global one,</li>
 *   <li>an escalating block — an address that keeps hitting the rate limit is refused outright
 *       for a while, and the block doubles each time it is earned again (capped).</li>
 * </ul>
 * Pure bookkeeping on a clock the caller supplies; the transport closes refused sockets at once.
 * Not thread-safe: call from the accept thread only.
 */
public final class ConnectionLimiter {

    /** Limits; the defaults suit a small public game server where several players may share an address. */
    public record Config(int maxOpenPerAddress, int maxNewPerAddressPerWindow, int maxNewPerWindow,
                         long windowNanos, int violationsBeforeBlock, long firstBlockNanos, long maxBlockNanos) {
        public static Config defaults() {
            long second = 1_000_000_000L;
            return new Config(8, 20, 300, 60 * second, 3, 5 * 60 * second, 60 * 60 * second);
        }
    }

    private final Config config;
    private final Map<String, Deque<Long>> arrivals = new HashMap<>();
    private final Deque<Long> globalArrivals = new ArrayDeque<>();
    private final Map<String, Integer> violations = new HashMap<>();
    private final Map<String, Long> blockedUntil = new HashMap<>();
    private final Map<String, Long> blockLength = new HashMap<>();

    public ConnectionLimiter(Config config) {
        this.config = config;
    }

    /**
     * Whether a connection arriving now from address, with openFromAddress connections of that
     * address already open, is allowed. Records the attempt. A null address (no way to tell
     * clients apart) is only subject to the global limit.
     */
    public boolean allow(String address, int openFromAddress, long now) {
        prune(now);
        if (!record(globalArrivals, now, config.maxNewPerWindow())) return false;
        if (address == null) return true;

        Long until = blockedUntil.get(address);
        if (until != null && now - until < 0) return false;
        if (openFromAddress >= config.maxOpenPerAddress()) return false;

        Deque<Long> mine = arrivals.computeIfAbsent(address, a -> new ArrayDeque<>());
        if (!record(mine, now, config.maxNewPerAddressPerWindow())) {
            int count = violations.merge(address, 1, Integer::sum);
            if (count >= config.violationsBeforeBlock()) block(address, now);
            return false;
        }
        return true;
    }

    /** Whether address is currently blocked outright. */
    public boolean isBlocked(String address, long now) {
        Long until = blockedUntil.get(address);
        return until != null && now - until < 0;
    }

    private void block(String address, long now) {
        long length = blockLength.merge(address, config.firstBlockNanos(), (old, first) -> Math.min(old * 2, config.maxBlockNanos()));
        blockedUntil.put(address, now + length);
        violations.remove(address);
    }

    /** Adds now to the window unless the window is already full; returns whether it was added. */
    private boolean record(Deque<Long> window, long now, int max) {
        while (!window.isEmpty() && now - window.peekFirst() >= config.windowNanos()) window.pollFirst();
        if (window.size() >= max) return false;
        window.addLast(now);
        return true;
    }

    /** Forgets addresses that have been quiet for a whole window so the maps can't grow without bound. */
    private void prune(long now) {
        for (Iterator<Map.Entry<String, Deque<Long>>> it = arrivals.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<String, Deque<Long>> entry = it.next();
            Deque<Long> window = entry.getValue();
            while (!window.isEmpty() && now - window.peekFirst() >= config.windowNanos()) window.pollFirst();
            if (window.isEmpty() && !blockedUntil.containsKey(entry.getKey())) it.remove();
        }
        blockedUntil.entrySet().removeIf(e -> now - e.getValue() >= 0 && now - e.getValue() >= config.windowNanos());
        if (blockedUntil.isEmpty()) blockLength.clear();
    }
}
