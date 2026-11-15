package io.github.elderpath_crusade.multiplayer.net;

import io.github.elderpath_crusade.net.transport.ClientChannel;
import io.github.elderpath_crusade.net.transport.TcpTransport;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The limits a connection puts on an untrusted peer: line length, and open connections per server. */
class NetworkConnectionTest {
    private static final long DEADLINE_NANOS = 5_000_000_000L;

    @Test
    void readBoundedLine_splitsOnNewlines_dropsCarriageReturns_andEndsWithNull() throws IOException {
        StringReader reader = new StringReader("one\ntwo\r\n\nlast");
        assertEquals("one", NetworkConnection.readBoundedLine(reader, 10));
        assertEquals("two", NetworkConnection.readBoundedLine(reader, 10));
        assertEquals("", NetworkConnection.readBoundedLine(reader, 10));
        assertEquals("last", NetworkConnection.readBoundedLine(reader, 10));
        assertNull(NetworkConnection.readBoundedLine(reader, 10));
    }

    @Test
    void readBoundedLine_allowsALineOfExactlyTheLimit_andThrowsOnOneMore() throws IOException {
        assertEquals("abcde", NetworkConnection.readBoundedLine(new StringReader("abcde\n"), 5));
        assertThrows(IOException.class, () -> NetworkConnection.readBoundedLine(new StringReader("abcdef\n"), 5));
        assertThrows(IOException.class, () -> NetworkConnection.readBoundedLine(new StringReader("a".repeat(100)), 5));
    }

    @Test
    void aServerClosesAConnectionThatSendsAnOverlongLine() throws Exception {
        TcpTransport transport = new TcpTransport("127.0.0.1", 0);
        try (Socket socket = new Socket("127.0.0.1", transport.getPort())) {
            OutputStream out = socket.getOutputStream();
            byte[] chunk = "x".repeat(4096).getBytes(StandardCharsets.UTF_8);
            // No newline: three chunks pass SERVER_MAX_LINE_CHARS.
            for (int i = 0; i < 3; i++) out.write(chunk);
            out.flush();

            assertTrue(readsEndOfStream(socket), "the server should have closed the connection");
        } finally {
            transport.close();
        }
    }

    @Test
    void aServerClosesAConnectionWhoseLinesPileUpUnpolled() throws Exception {
        TcpTransport transport = new TcpTransport("127.0.0.1", 0);
        try (Socket socket = new Socket("127.0.0.1", transport.getPort())) {
            OutputStream out = socket.getOutputStream();
            StringBuilder lines = new StringBuilder();
            for (int i = 0; i <= NetworkConnection.SERVER_MAX_QUEUED_LINES; i++) lines.append("{}\n");
            try {
                out.write(lines.toString().getBytes(StandardCharsets.UTF_8));
                out.flush();
            } catch (IOException closedAlready) {
                // The server may close while the tail is still being written.
            }

            assertTrue(readsEndOfStream(socket), "the server should have closed the connection");
        } finally {
            transport.close();
        }
    }

    @Test
    void aServerKeepsOnlySoManyConnectionsOpen() throws Exception {
        TcpTransport transport = new TcpTransport("127.0.0.1", 0);
        List<Socket> sockets = new ArrayList<>();
        try {
            int max = 16; // TcpTransport.MAX_OPEN_CONNECTIONS
            for (int i = 0; i < max; i++) sockets.add(new Socket("127.0.0.1", transport.getPort()));
            awaitAccepted(transport, max);

            Socket extra = new Socket("127.0.0.1", transport.getPort());
            sockets.add(extra);
            assertTrue(readsEndOfStream(extra), "a connection beyond the limit is closed at once");
            assertNull(transport.accept(), "and never reaches the match server");
        } finally {
            for (Socket socket : sockets) socket.close();
            transport.close();
        }
    }

    private static void awaitAccepted(TcpTransport transport, int count) throws InterruptedException {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        int accepted = 0;
        while (accepted < count) {
            ClientChannel channel = transport.accept();
            if (channel != null) accepted++;
            else if (System.nanoTime() - deadline > 0) fail("only " + accepted + " of " + count + " connections were accepted");
            else Thread.sleep(2);
        }
    }

    /** Whether the peer closes the socket (read returns -1 or the connection resets) within the deadline. */
    private static boolean readsEndOfStream(Socket socket) throws IOException {
        socket.setSoTimeout((int) (DEADLINE_NANOS / 1_000_000L));
        InputStream in = socket.getInputStream();
        try {
            return in.read() == -1;
        } catch (java.net.SocketTimeoutException e) {
            return false;
        } catch (IOException reset) {
            return true;
        }
    }
}
