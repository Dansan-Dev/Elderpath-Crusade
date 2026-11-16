package io.github.elderpath_crusade.lobby;

import io.github.elderpath_crusade.net.transport.LineLink;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/** The lobby's TCP transport behind a PROXY-protocol proxy: the header names the client the limits see. */
class TcpLobbyProxyTest {

    private static Socket connect(int port, String header) throws IOException {
        Socket socket = new Socket("127.0.0.1", port);
        OutputStream out = socket.getOutputStream();
        out.write(header.getBytes(StandardCharsets.UTF_8));
        out.flush();
        return socket;
    }

    private static LineLink awaitAccepted(TcpLobbyTransport transport) throws InterruptedException {
        long deadline = System.nanoTime() + 5_000_000_000L;
        while (System.nanoTime() - deadline < 0) {
            LineLink link = transport.accept();
            if (link != null) return link;
            Thread.sleep(5);
        }
        return null;
    }

    @Test
    void theHeaderNamesTheClientAndTheRestOfTheStreamIsUntouched() throws Exception {
        TcpLobbyTransport transport = new TcpLobbyTransport("127.0.0.1", 0, true);
        try (Socket socket = connect(transport.getPort(), "PROXY TCP4 203.0.113.9 10.0.0.1 4444 443\r\nhello\n")) {
            LineLink link = awaitAccepted(transport);
            assertNotNull(link);
            assertEquals("203.0.113.9", link.peerAddress());
            String line = null;
            for (int i = 0; i < 500 && line == null; i++) {
                line = link.pollLine();
                if (line == null) Thread.sleep(5);
            }
            assertEquals("hello", line);
        } finally {
            transport.close();
        }
    }

    @Test
    void aConnectionWithoutAValidHeaderIsClosed() throws Exception {
        TcpLobbyTransport transport = new TcpLobbyTransport("127.0.0.1", 0, true);
        try (Socket socket = connect(transport.getPort(), "{\"type\":\"LobbyHello\"}\n")) {
            socket.setSoTimeout(5_000);
            assertEquals(-1, socket.getInputStream().read(), "the server hung up");
            assertNull(transport.accept());
        } finally {
            transport.close();
        }
    }

    @Test
    void perAddressLimitsUseTheAddressFromTheHeader() throws Exception {
        TcpLobbyTransport transport = new TcpLobbyTransport("127.0.0.1", 0, true);
        java.util.List<Socket> sockets = new java.util.ArrayList<>();
        try {
            int accepted = 0;
            for (int i = 0; i < 12; i++) {
                sockets.add(connect(transport.getPort(), "PROXY TCP4 198.51.100.4 10.0.0.1 " + (5000 + i) + " 443\r\n"));
                Thread.sleep(30);
            }
            for (int i = 0; i < 12; i++) {
                LineLink link = transport.accept();
                if (link != null) accepted++;
            }
            // a different client through the same proxy is unaffected
            sockets.add(connect(transport.getPort(), "PROXY TCP4 192.0.2.77 10.0.0.1 6000 443\r\n"));
            LineLink other = awaitAccepted(transport);
            assertNotNull(other);
            assertEquals("192.0.2.77", other.peerAddress());
            assertEquals(8, accepted, "at most 8 open connections for one client address");
        } finally {
            for (Socket s : sockets) s.close();
            transport.close();
        }
    }
}
