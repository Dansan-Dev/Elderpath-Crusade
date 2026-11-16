package io.github.elderpath_crusade.lobby;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LobbyEndpointTest {
    @Test
    void remoteHostDefaultsToTlsOn443() {
        assertEquals(new LobbyEndpoint("elderpath-server.fly.dev", 443, true), LobbyEndpoint.parse("elderpath-server.fly.dev"));
        assertEquals(new LobbyEndpoint("example.com", 8443, true), LobbyEndpoint.parse("  example.com:8443 "));
    }

    @Test
    void localHostDefaultsToPlainTcpOn4567() {
        assertEquals(new LobbyEndpoint("localhost", 4567, false), LobbyEndpoint.parse("localhost"));
        assertEquals(new LobbyEndpoint("127.0.0.1", 9000, false), LobbyEndpoint.parse("127.0.0.1:9000"));
    }

    @Test
    void anExplicitPrefixOverridesTheDefault() {
        assertEquals(new LobbyEndpoint("example.com", 4567, false), LobbyEndpoint.parse("tcp://example.com:4567"));
        assertEquals(new LobbyEndpoint("localhost", 443, true), LobbyEndpoint.parse("TLS://localhost"));
    }

    @Test
    void theDefaultAddressParses() {
        assertEquals(LobbyEndpoint.DEFAULT_ADDRESS, LobbyEndpoint.parse(LobbyEndpoint.DEFAULT_ADDRESS).host());
    }

    @Test
    void badInputIsRefusedWithAMessage() {
        for (String bad : new String[]{null, "", "   ", "tls://", ":443", "host:", "host:abc", "host:0", "host:70000",
                "bad host", "host/path", "[::1]:80", "user@host", "host:99999999999"}) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> LobbyEndpoint.parse(bad), String.valueOf(bad));
            assertFalse(e.getMessage().isBlank());
        }
    }
}
