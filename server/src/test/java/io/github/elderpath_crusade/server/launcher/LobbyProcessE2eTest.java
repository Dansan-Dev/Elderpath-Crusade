package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.lobby.LobbyClientMessage;
import io.github.elderpath_crusade.lobby.LobbyCodec;
import io.github.elderpath_crusade.lobby.LobbyServerMessage;
import io.github.elderpath_crusade.multiplayer.net.NetworkConnection;
import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.server.ServerProcess;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The real lobby service as a child process (--lobby true) with a tiny data set, two real TCP
 * clients, and a real child match server behind the bridge: host, join, start, then the match
 * protocol over the same connections.
 */
class LobbyProcessE2eTest {
    private static final long DEADLINE_NANOS = Duration.ofSeconds(30).toNanos();
    private static final List<String> DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");

    @TempDir
    File assets;

    private ServerProcess service;
    private final List<NetworkConnection> open = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (NetworkConnection c : open) c.close();
        if (service != null) service.stop();
    }

    private void writeAssets() throws IOException {
        File data = new File(assets, "data");
        assertTrue(data.mkdirs());
        Files.writeString(new File(data, "pieces.yaml").toPath(), """
                pieces:
                  Wolf: {cost: 1, health: 2, damage: 1, speed: 1, actions: 1}
                  WolfCub: {cost: 1, health: 1, damage: 1, speed: 1, actions: 1}
                """);
        for (String name : new String[]{"abilities", "spells", "relics"}) {
            Files.writeString(new File(data, name + ".yaml").toPath(), name + ": {}\n");
        }
    }

    private NetworkConnection connect() throws IOException {
        Socket socket = new Socket("127.0.0.1", service.getPort());
        NetworkConnection connection = new NetworkConnection(socket);
        open.add(connection);
        return connection;
    }

    private static <T> T await(NetworkConnection c, Predicate<String> accepts, java.util.function.Function<String, T> decode) {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        while (System.nanoTime() - deadline < 0) {
            String line = c.pollLine();
            if (line != null) {
                if (accepts.test(line)) return decode.apply(line);
            } else {
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    fail("interrupted");
                }
            }
        }
        return fail("timed out waiting for a message");
    }

    private static LobbyServerMessage nextLobbyMessage(NetworkConnection c, Class<? extends LobbyServerMessage> type) {
        return await(c, line -> line.contains("\"type\":\"" + type.getSimpleName() + "\""), LobbyCodec::decodeServer);
    }

    @Test
    void hostJoinStart_thenBothPlayersAreSeatedInARealMatchThroughTheBridge() throws Exception {
        writeAssets();
        service = ServerProcess.start(ServerProcess.Options.of("--lobby", "true", "--port", "0",
                "--assets", assets.getAbsolutePath(), "--max-matches", "2"));
        assertTrue(service.getPort() > 0);

        NetworkConnection host = connect();
        NetworkConnection guest = connect();
        host.send(LobbyCodec.encode(new LobbyClientMessage.LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, "host")));
        guest.send(LobbyCodec.encode(new LobbyClientMessage.LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, "guest")));
        nextLobbyMessage(host, LobbyServerMessage.LobbyWelcome.class);
        nextLobbyMessage(guest, LobbyServerMessage.LobbyWelcome.class);

        host.send(LobbyCodec.encode(new LobbyClientMessage.CreateLobby("E2E", "pw", DECK)));
        String lobbyId = ((LobbyServerMessage.LobbyJoined) nextLobbyMessage(host, LobbyServerMessage.LobbyJoined.class)).lobby().id();

        guest.send(LobbyCodec.encode(new LobbyClientMessage.ListLobbies()));
        LobbyServerMessage.LobbyList list = (LobbyServerMessage.LobbyList) nextLobbyMessage(guest, LobbyServerMessage.LobbyList.class);
        assertEquals(1, list.lobbies().size());
        assertTrue(list.lobbies().get(0).passwordProtected());

        guest.send(LobbyCodec.encode(new LobbyClientMessage.JoinLobby(lobbyId, "wrong", DECK)));
        LobbyServerMessage.LobbyError refused = (LobbyServerMessage.LobbyError) nextLobbyMessage(guest, LobbyServerMessage.LobbyError.class);
        assertEquals(LobbyServerMessage.LobbyError.Code.WRONG_PASSWORD, refused.code());
        guest.send(LobbyCodec.encode(new LobbyClientMessage.JoinLobby(lobbyId, "pw", DECK)));
        nextLobbyMessage(guest, LobbyServerMessage.LobbyJoined.class);

        host.send(LobbyCodec.encode(new LobbyClientMessage.StartMatch()));
        LobbyServerMessage.MatchStarting hostSeat = (LobbyServerMessage.MatchStarting) nextLobbyMessage(host, LobbyServerMessage.MatchStarting.class);
        LobbyServerMessage.MatchStarting guestSeat = (LobbyServerMessage.MatchStarting) nextLobbyMessage(guest, LobbyServerMessage.MatchStarting.class);
        assertEquals("P1", hostSeat.seat());
        assertEquals("P2", guestSeat.seat());

        // From here the same connections speak the match protocol, to a real child match server.
        host.send(MessageCodec.encode(new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, SeatRequest.P1, "host")));
        guest.send(MessageCodec.encode(new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, SeatRequest.P2, "guest")));
        ServerMessage.Welcome hostWelcome = await(host, l -> l.contains("\"type\":\"Welcome\""),
                l -> (ServerMessage.Welcome) MessageCodec.decodeServer(l));
        ServerMessage.Welcome guestWelcome = await(guest, l -> l.contains("\"type\":\"Welcome\""),
                l -> (ServerMessage.Welcome) MessageCodec.decodeServer(l));
        assertEquals("P1", hostWelcome.seat().name());
        assertEquals("P2", guestWelcome.seat().name());
        assertNotNull(hostWelcome.reconnectToken());
        assertNotEquals(hostWelcome.reconnectToken(), guestWelcome.reconnectToken());
        // The match begins once both seats are taken; each seat then gets its legal actions.
        ServerMessage.LegalActionsUpdate hostLegal = await(host, l -> l.contains("\"type\":\"LegalActionsUpdate\""),
                l -> (ServerMessage.LegalActionsUpdate) MessageCodec.decodeServer(l));
        ServerMessage.LegalActionsUpdate guestLegal = await(guest, l -> l.contains("\"type\":\"LegalActionsUpdate\""),
                l -> (ServerMessage.LegalActionsUpdate) MessageCodec.decodeServer(l));
        assertTrue(hostLegal.actions().yourTurn() ^ guestLegal.actions().yourTurn(), "exactly one seat has the first turn");
    }

    @Test
    void anInvalidDeckNeverReachesAMatchServer() throws Exception {
        writeAssets();
        service = ServerProcess.start(ServerProcess.Options.of("--lobby", "true", "--port", "0",
                "--assets", assets.getAbsolutePath()));
        NetworkConnection c = connect();
        c.send(LobbyCodec.encode(new LobbyClientMessage.LobbyHello(LobbyCodec.LOBBY_PROTOCOL_VERSION, "ann")));
        nextLobbyMessage(c, LobbyServerMessage.LobbyWelcome.class);
        c.send(LobbyCodec.encode(new LobbyClientMessage.CreateLobby("Bad", null, List.of("Wolf", "Wolf,Wolf:99"))));
        LobbyServerMessage.LobbyError error = (LobbyServerMessage.LobbyError) nextLobbyMessage(c, LobbyServerMessage.LobbyError.class);
        assertEquals(LobbyServerMessage.LobbyError.Code.INVALID_DECK, error.code());
    }
}
