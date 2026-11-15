package io.github.elderpath_crusade.server.launcher;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StopPolicyTest {
    private static final long S = 1_000_000_000L;

    @Test
    void connectedClient_keepsServerRunning() {
        StopPolicy p = new StopPolicy(true, 60 * S, 3 * S, 0);
        assertNull(p.check(100 * S, true, false));
    }

    @Test
    void noClientForIdleTimeout_stops() {
        StopPolicy p = new StopPolicy(true, 60 * S, 3 * S, 0);
        assertNull(p.check(59 * S, false, false));
        assertEquals("idle", p.check(60 * S, false, false));
    }

    @Test
    void idleClockRestartsWhenAClientWasLastSeen() {
        StopPolicy p = new StopPolicy(true, 60 * S, 3 * S, 0);
        assertNull(p.check(50 * S, true, false));
        assertNull(p.check(100 * S, false, false)); // only 50 s since last seen
        assertEquals("idle", p.check(110 * S, false, false));
    }

    @Test
    void botVsBotServer_neverGoesIdle() {
        StopPolicy p = new StopPolicy(false, 60 * S, 3 * S, 0);
        assertNull(p.check(10_000 * S, false, false));
    }

    @Test
    void matchEnd_waitsForGraceWhileClientsAreConnected() {
        StopPolicy p = new StopPolicy(true, 60 * S, 3 * S, 0);
        assertNull(p.check(10 * S, true, true));
        assertNull(p.check(12 * S, true, true));
        assertEquals("match ended", p.check(13 * S, true, true));
    }

    @Test
    void matchEnd_stopsAtOnceWhenNobodyIsConnected() {
        StopPolicy p = new StopPolicy(true, 60 * S, 3 * S, 0);
        assertEquals("match ended", p.check(10 * S, false, true));
    }

    @Test
    void matchJustEnded_isTrueExactlyOnce() {
        StopPolicy p = new StopPolicy(true, 60 * S, 3 * S, 0);
        p.check(1 * S, true, false);
        assertFalse(p.matchJustEnded());
        p.check(2 * S, true, true);
        assertTrue(p.matchJustEnded());
        p.check(3 * S, true, true);
        assertFalse(p.matchJustEnded());
    }
}
