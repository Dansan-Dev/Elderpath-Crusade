package io.github.elderpath_crusade.net.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AttemptLimiterTest {
    private static final long S = 1_000_000_000L;

    // 3 failures within 60 s lock for 30 s, doubling up to 120 s
    private static AttemptLimiter limiter() {
        return new AttemptLimiter(3, 60 * S, 30 * S, 120 * S);
    }

    @Test
    void failuresBelowTheLimitDoNotLock() {
        AttemptLimiter l = limiter();
        assertFalse(l.recordFailure("k", 0));
        assertFalse(l.recordFailure("k", S));
        assertFalse(l.isLocked("k", 2 * S));
    }

    @Test
    void thirdFailureInTheWindowLocksTheKeyForAWhile() {
        AttemptLimiter l = limiter();
        l.recordFailure("k", 0);
        l.recordFailure("k", S);
        assertTrue(l.recordFailure("k", 2 * S));
        assertTrue(l.isLocked("k", 10 * S));
        assertEquals(22, l.secondsLeft("k", 10 * S));
        assertFalse(l.isLocked("k", 33 * S));
        assertFalse(l.isLocked("other", 10 * S));
    }

    @Test
    void failuresOutsideTheWindowDoNotCount() {
        AttemptLimiter l = limiter();
        l.recordFailure("k", 0);
        l.recordFailure("k", 1 * S);
        assertFalse(l.recordFailure("k", 100 * S));
    }

    @Test
    void eachRepeatedLockoutIsLonger_untilTheCap() {
        AttemptLimiter l = limiter();
        long t = 0;
        long[] expected = {30, 60, 120, 120};
        for (long length : expected) {
            for (int i = 0; i < 3; i++) l.recordFailure("k", t + i);
            assertTrue(l.isLocked("k", t + 3));
            assertEquals(length, l.secondsLeft("k", t + 3));
            t += (length + 1) * S;
            assertFalse(l.isLocked("k", t));
        }
    }

    @Test
    void aSuccessClearsFailuresAndTheLockHistory() {
        AttemptLimiter l = limiter();
        l.recordFailure("k", 0);
        l.recordFailure("k", 1);
        l.recordSuccess("k");
        assertFalse(l.recordFailure("k", 2), "the count started over");
        l.recordFailure("k", 3);
        assertTrue(l.recordFailure("k", 4));
        assertTrue(l.secondsLeft("k", 4) <= 30, "back to the first lock length");
    }

    @Test
    void pruneForgetsLongQuietKeys() {
        AttemptLimiter l = limiter();
        for (int i = 0; i < 3; i++) l.recordFailure("k", i);
        l.prune(1000 * S);
        for (int i = 0; i < 3; i++) l.recordFailure("k", 1001 * S + i);
        assertTrue(l.secondsLeft("k", 1001 * S + 3) <= 30, "history was forgotten: first lock length again");
    }
}
