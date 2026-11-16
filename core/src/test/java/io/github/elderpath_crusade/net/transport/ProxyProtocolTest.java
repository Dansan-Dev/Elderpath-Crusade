package io.github.elderpath_crusade.net.transport;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class ProxyProtocolTest {
    @Test
    void parsesIpv4AndIpv6Headers() throws IOException {
        assertEquals("203.0.113.7", ProxyProtocol.parse("PROXY TCP4 203.0.113.7 10.0.0.1 5555 443"));
        assertEquals("2001:db8:0:0:0:0:0:1", ProxyProtocol.parse("PROXY TCP6 2001:db8::1 fdaa::1 5555 443"));
    }

    @Test
    void unknownMeansNoAddress() throws IOException {
        assertNull(ProxyProtocol.parse("PROXY UNKNOWN"));
        assertNull(ProxyProtocol.parse("PROXY UNKNOWN ffff:f...f:ffff ffff:f...f:ffff 65535 65535"));
    }

    @Test
    void rejectsAnythingElse() {
        for (String bad : new String[]{"", "GET / HTTP/1.1", "PROXY", "PROXY TCP4", "PROXY TCP4 1.2.3.4 5.6.7.8 1",
                "PROXY UDP4 1.2.3.4 5.6.7.8 1 2", "PROXY TCP4 evil.example.com 5.6.7.8 1 2", "PROXY TCP4 1.2.3.4.5 5.6.7.8 1 2",
                "PROXY TCP4 1.2.3.4 5.6.7.8 1 2 extra", "proxy TCP4 1.2.3.4 5.6.7.8 1 2"}) {
            assertThrows(IOException.class, () -> ProxyProtocol.parse(bad), bad);
        }
    }

    @Test
    void readsExactlyTheHeaderLineAndNothingMore() throws IOException {
        ByteArrayInputStream in = new ByteArrayInputStream("PROXY TCP4 1.2.3.4 5.6.7.8 1 2\r\n{\"type\":\"LobbyHello\"}\n".getBytes(StandardCharsets.UTF_8));
        assertEquals("1.2.3.4", ProxyProtocol.read(in));
        assertEquals('{', in.read(), "the next byte is the client's own data");
    }

    @Test
    void anOverlongOrUnterminatedHeaderIsRefused() {
        assertThrows(IOException.class, () -> ProxyProtocol.read(new ByteArrayInputStream("P".repeat(500).getBytes(StandardCharsets.UTF_8))));
        assertThrows(IOException.class, () -> ProxyProtocol.read(new ByteArrayInputStream("PROXY TCP4 1.2.3.4 5.6.7.8 1 2".getBytes(StandardCharsets.UTF_8))));
        assertThrows(IOException.class, () -> ProxyProtocol.read(new ByteArrayInputStream(("A".repeat(200) + "\n").getBytes(StandardCharsets.UTF_8))));
    }
}
