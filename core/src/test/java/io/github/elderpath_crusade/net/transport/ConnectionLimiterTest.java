package io.github.elderpath_crusade.net.transport;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConnectionLimiterTest {
    private static final long S = 1_000_000_000L;
    // 2 open per address, 3 new per address per 10 s, 5 new overall per 10 s, block after 2 violations for 100 s (max 400 s)
    private static ConnectionLimiter limiter() {
        return new ConnectionLimiter(new ConnectionLimiter.Config(2, 3, 5, 10 * S, 2, 100 * S, 400 * S));
    }

    @Test
    void anAddressMayHoldOnlySoManyConnectionsOpen() {
        ConnectionLimiter l = limiter();
        assertTrue(l.allow("1.1.1.1", 0, 0));
        assertTrue(l.allow("1.1.1.1", 1, 1));
        assertFalse(l.allow("1.1.1.1", 2, 2));
        assertTrue(l.allow("2.2.2.2", 0, 3), "another address is unaffected");
    }

    @Test
    void newConnectionsPerAddressAreRateLimitedOnASlidingWindow() {
        ConnectionLimiter l = limiter();
        for (int i = 0; i < 3; i++) assertTrue(l.allow("1.1.1.1", 0, i * S), "attempt " + i);
        assertFalse(l.allow("1.1.1.1", 0, 3 * S));
        assertTrue(l.allow("1.1.1.1", 0, 10 * S), "the window slid past the first attempt");
    }

    @Test
    void anAddressThatKeepsHittingTheLimitIsBlockedOutright_withAGrowingBlock() {
        ConnectionLimiter l = limiter();
        for (int i = 0; i < 3; i++) l.allow("9.9.9.9", 0, i * S);
        assertFalse(l.allow("9.9.9.9", 0, 3 * S)); // violation 1
        assertFalse(l.isBlocked("9.9.9.9", 3 * S));
        assertFalse(l.allow("9.9.9.9", 0, 4 * S)); // violation 2 -> blocked for 100 s
        assertTrue(l.isBlocked("9.9.9.9", 4 * S));
        assertFalse(l.allow("9.9.9.9", 0, 50 * S), "even with a free window it is refused while blocked");
        assertFalse(l.isBlocked("9.9.9.9", 105 * S));
        assertTrue(l.allow("9.9.9.9", 0, 105 * S), "the block ended");

        for (int i = 0; i < 2; i++) l.allow("9.9.9.9", 0, (106 + i) * S);
        assertFalse(l.allow("9.9.9.9", 0, 108 * S)); // violation 1 again
        assertFalse(l.allow("9.9.9.9", 0, 109 * S)); // violation 2 -> second block is 200 s
        assertTrue(l.isBlocked("9.9.9.9", 109 * S + 150 * S));
        assertFalse(l.isBlocked("9.9.9.9", 109 * S + 201 * S));
    }

    @Test
    void aGlobalLimitCapsNewConnectionsFromEveryoneTogether() {
        ConnectionLimiter l = limiter();
        for (int i = 0; i < 5; i++) assertTrue(l.allow("10.0.0." + i, 0, i * S));
        assertFalse(l.allow("10.0.0.99", 0, 5 * S));
        assertTrue(l.allow("10.0.0.99", 0, 10 * S));
    }

    @Test
    void anUnknownAddressIsOnlyGloballyLimited() {
        ConnectionLimiter l = limiter();
        for (int i = 0; i < 5; i++) assertTrue(l.allow(null, 99, i * S));
        assertFalse(l.allow(null, 0, 5 * S));
    }

    @Test
    void quietAddressesAreForgotten() {
        ConnectionLimiter l = limiter();
        for (int i = 0; i < 3; i++) l.allow("1.1.1.1", 0, i * S);
        assertTrue(l.allow("2.2.2.2", 0, 1000 * S));
        assertTrue(l.allow("1.1.1.1", 0, 1001 * S));
    }
}
