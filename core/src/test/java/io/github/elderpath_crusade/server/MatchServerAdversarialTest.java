package io.github.elderpath_crusade.server;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.MatchClient;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.net.transport.InProcessTransport;
import io.github.elderpath_crusade.net.transport.LineLink;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static io.github.elderpath_crusade.server.MatchFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * MatchServer against clients that don't follow the protocol: unreadable lines, silence,
 * floods, and commands no legal client would send. None of it may throw out of tick(), cost
 * the other seat its match, or change the game state.
 */
class MatchServerAdversarialTest {
    private InProcessTransport transport;
    private MatchServer server;

    @BeforeEach
    void setUp() {
        setUpContext();
        transport = new InProcessTransport();
        server = new MatchServer(config(deckOf("Zapper", 12), deckOf("Runner", 12), Map.of()), transport);
    }

    @AfterEach
    void tearDown() {
        server.close();
        tearDownContext();
    }

    private static List<ServerMessage> drain(LineLink link) {
        List<ServerMessage> out = new ArrayList<>();
        String line;
        while ((line = link.pollLine()) != null) out.add(MessageCodec.decodeServer(line));
        return out;
    }

    private static ServerMessage.Error onlyError(List<ServerMessage> messages) {
        List<ServerMessage.Error> errors = new ArrayList<>();
        for (ServerMessage m : messages) if (m instanceof ServerMessage.Error e) errors.add(e);
        assertEquals(1, errors.size(), "expected exactly one Error in " + messages);
        return errors.get(0);
    }

    private LineLink seatRaw(SeatRequest seat) {
        LineLink link = transport.connectRaw();
        link.send(MessageCodec.encode(new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, seat, "raw")));
        return link;
    }

    private static void sendCommand(LineLink link, long seq, NetworkCommand command) {
        link.send(MessageCodec.encode(new ClientMessage.Command(seq, command)));
    }

    @Test
    void anUnreadableFirstLine_isRefusedWithBadMessage_andDisconnected() {
        LineLink link = transport.connectRaw();
        link.send("this is not json");

        server.tick(TICK);

        assertEquals(ServerMessage.Error.Code.BAD_MESSAGE, onlyError(drain(link)).code());
        assertFalse(link.isOpen());
    }

    @Test
    void aCommandBeforeHello_isRefusedWithNotSeated() {
        LineLink link = transport.connectRaw();
        sendCommand(link, 1, new NetworkCommand.EndTurn());

        server.tick(TICK);

        assertEquals(ServerMessage.Error.Code.NOT_SEATED, onlyError(drain(link)).code());
        assertFalse(link.isOpen());
    }

    @Test
    void aConnectionThatNeverSaysHello_isDroppedAtTheDeadline_notBefore() {
        AtomicLong now = new AtomicLong(1_000);
        server.setNanoClock(now::get);
        LineLink silent = transport.connectRaw();

        server.tick(TICK);
        now.addAndGet(MatchServer.HELLO_TIMEOUT_NANOS - 1);
        server.tick(TICK);
        assertTrue(silent.isOpen(), "still inside the handshake window");

        now.addAndGet(1);
        server.tick(TICK);
        assertFalse(silent.isOpen());
        assertEquals(ServerMessage.Error.Code.NOT_SEATED, onlyError(drain(silent)).code());
    }

    @Test
    void pendingHandshakes_areCapped_andAHelloStillGetsThroughOnceTheyClear() {
        List<LineLink> silent = new ArrayList<>();
        for (int i = 0; i < MatchServer.MAX_PENDING_HANDSHAKES; i++) silent.add(transport.connectRaw());
        LineLink overflow = transport.connectRaw();

        server.tick(TICK);

        for (LineLink link : silent) assertTrue(link.isOpen());
        assertFalse(overflow.isOpen(), "a connection beyond the cap is closed at once");

        for (LineLink link : silent) link.close();
        server.tick(TICK);
        MatchClient late = new MatchClient(transport.connect(), SeatRequest.P1, "late");
        server.tick(TICK);
        late.poll();
        assertEquals(PieceAlignment.P1, late.getState().getSeat());
    }

    @Test
    void aSeatSendingGarbage_getsAnErrorEach_thenIsDisconnected_andItsSeatStaysReserved() {
        LineLink p1 = seatRaw(SeatRequest.P1);
        MatchClient p2 = new MatchClient(transport.connect(), SeatRequest.P2, "b");
        server.tick(TICK);
        String token = drain(p1).stream().filter(m -> m instanceof ServerMessage.Welcome)
                .map(m -> ((ServerMessage.Welcome) m).reconnectToken()).findFirst().orElseThrow();

        for (int i = 0; i < MatchServer.MAX_BAD_MESSAGES; i++) p1.send("{garbage " + i);
        server.tick(TICK);

        List<ServerMessage> replies = drain(p1);
        long errors = replies.stream().filter(m -> m instanceof ServerMessage.Error).count();
        assertEquals(MatchServer.MAX_BAD_MESSAGES, errors);
        assertFalse(p1.isOpen());
        assertFalse(server.isConnected(PieceAlignment.P1));

        // The other seat's match is unaffected, and P1 can come back.
        server.tick(TICK);
        p2.poll();
        assertConsistent(server, p2);
        MatchClient back = new MatchClient(transport.connect(), SeatRequest.P1, "back", token);
        server.tick(TICK);
        back.poll();
        assertEquals(PieceAlignment.P1, back.getState().getSeat());
    }

    @Test
    void anErrorNeverEchoesMoreThanAShortPrefixOfTheBadLine() {
        LineLink link = transport.connectRaw();
        link.send("x".repeat(5_000));

        server.tick(TICK);

        String message = onlyError(drain(link)).message();
        assertTrue(message.length() < 300, "echoed " + message.length() + " characters");
    }

    @Test
    void aFloodOfCommands_isHandledAFixedNumberPerTick_andEveryOneIsStillAnswered() {
        LineLink p1 = seatRaw(SeatRequest.P1);
        MatchClient p2 = new MatchClient(transport.connect(), SeatRequest.P2, "b");
        server.tick(TICK);
        drain(p1);

        int flood = MatchServer.MAX_COMMANDS_PER_TICK * 3;
        for (int seq = 1; seq <= flood; seq++) sendCommand(p1, seq, new NetworkCommand.MovePlot(6, 4, 6, 3));

        server.tick(TICK);
        long ackedFirstTick = drain(p1).stream().filter(m -> m instanceof ServerMessage.Ack).count();
        assertEquals(MatchServer.MAX_COMMANDS_PER_TICK, ackedFirstTick);
        p2.poll();
        assertConsistent(server, p2);

        long acked = ackedFirstTick;
        for (int i = 0; i < 5; i++) {
            server.tick(TICK);
            acked += drain(p1).stream().filter(m -> m instanceof ServerMessage.Ack).count();
        }
        assertEquals(flood, acked);
    }

    @Test
    void commandsNoLegalClientSends_areAllRejected_andChangeNothing() {
        MatchClient p1 = new MatchClient(transport.connect(), SeatRequest.P1, "a");
        LineLink p2 = seatRaw(SeatRequest.P2);
        for (int i = 0; i < 3; i++) server.tick(TICK);
        p1.poll();
        drain(p2);
        var before = server.viewFor(PieceAlignment.P1);

        int[] values = {Integer.MIN_VALUE, -1, 0, 6, 7, Integer.MAX_VALUE};
        String[] ids = {null, "", "P1-0", "nope", "hidden-1"};
        List<NetworkCommand> commands = new ArrayList<>();
        for (int a : values) {
            for (int b : values) {
                commands.add(new NetworkCommand.MovePlot(a, b, b, a));
                commands.add(new NetworkCommand.AnswerPick(a, false, a, b));
                for (String id : ids) {
                    commands.add(new NetworkCommand.PlaySummonCard(id, a, b));
                    commands.add(new NetworkCommand.PlaySpellCard(id, a, b));
                    commands.add(new NetworkCommand.ActivateAbility(id, a));
                }
            }
        }
        for (String id : ids) commands.add(new NetworkCommand.PlaySpellCard(id, null, 1));
        commands.add(new NetworkCommand.EndTurn()); // not P2's turn

        long seq = 1;
        long rejected = 0;
        for (int from = 0; from < commands.size(); from += MatchServer.MAX_COMMANDS_PER_TICK) {
            int to = Math.min(commands.size(), from + MatchServer.MAX_COMMANDS_PER_TICK);
            for (NetworkCommand command : commands.subList(from, to)) sendCommand(p2, seq++, command);
            assertDoesNotThrow(() -> server.tick(TICK));
            rejected += drain(p2).stream().filter(m -> m instanceof ServerMessage.Rejected).count();
        }

        assertEquals(commands.size(), rejected, "every one of them is rejected, none silently accepted");
        assertEquals(before, server.viewFor(PieceAlignment.P1));
        p1.poll();
        assertConsistent(server, p1);
    }

    @Test
    void aRuleThatThrows_isAnsweredAsARejection_andTheServerKeepsTicking() {
        MatchClient p1 = new MatchClient(transport.connect(), SeatRequest.P1, "a");
        LineLink p2 = seatRaw(SeatRequest.P2);
        for (int i = 0; i < 3; i++) server.tick(TICK);
        drain(p2);
        GameContext.get().getGameServer().setRejectionObserver((actor, result) -> {
            throw new IllegalStateException("a bug inside command handling");
        });

        sendCommand(p2, 1, new NetworkCommand.EndTurn()); // not P2's turn: rejected, so the observer runs
        assertDoesNotThrow(() -> server.tick(TICK));

        ServerMessage.Rejected rejection = null;
        for (ServerMessage m : drain(p2)) if (m instanceof ServerMessage.Rejected r) rejection = r;
        assertNotNull(rejection);
        assertEquals(io.github.elderpath_crusade.match.ActionResult.Reason.EXECUTION_FAILED, rejection.reason());
        assertDoesNotThrow(() -> server.tick(TICK));
        p1.poll();
        assertConsistent(server, p1);
    }

    @Test
    void loggableName_stripsControlCharacters_andIsBounded() {
        assertEquals("A client", MatchServer.loggableName(null));
        assertEquals("A client", MatchServer.loggableName("  "));
        assertEquals("ann?[server] fake line", MatchServer.loggableName("ann\n[server] fake line"));
        assertEquals(32, MatchServer.loggableName("n".repeat(500)).length());
    }

    // --- seat reclaim needs the reconnect token (audit S13 / AUDIT-001) ---

    private static String tokenOf(List<ServerMessage> messages) {
        return messages.stream().filter(m -> m instanceof ServerMessage.Welcome)
                .map(m -> ((ServerMessage.Welcome) m).reconnectToken()).findFirst().orElse(null);
    }

    private LineLink helloRaw(SeatRequest seat, String token) {
        LineLink link = transport.connectRaw();
        link.send(MessageCodec.encode(new ClientMessage.Hello(MessageCodec.PROTOCOL_VERSION, seat, "x", token)));
        return link;
    }

    private static boolean refusedAsTaken(List<ServerMessage> replies) {
        return replies.stream().anyMatch(m -> m instanceof ServerMessage.Error e
                && e.code() == ServerMessage.Error.Code.SEAT_TAKEN);
    }

    /** P1 joins and drops; returns P1's token. P2 is still connected. */
    private String p1JoinsAndDrops() {
        LineLink p1 = seatRaw(SeatRequest.P1);
        MatchClient p2 = new MatchClient(transport.connect(), SeatRequest.P2, "b");
        server.tick(TICK);
        String token = tokenOf(drain(p1));
        assertNotNull(token, "a seated client is given a reconnect token");
        p1.close();
        server.tick(TICK);
        server.tick(TICK);
        assertFalse(server.isConnected(PieceAlignment.P1));
        return token;
    }

    @Test
    void aReservedSeat_cannotBeTakenWithoutItsToken() {
        p1JoinsAndDrops();
        LineLink thief = helloRaw(SeatRequest.P1, null);
        server.tick(TICK);
        assertTrue(refusedAsTaken(drain(thief)));
        assertFalse(server.isConnected(PieceAlignment.P1));
    }

    @Test
    void aReservedSeat_cannotBeTakenWithAWrongOrOtherSeatsToken() {
        String p1Token = p1JoinsAndDrops();
        LineLink wrong = helloRaw(SeatRequest.P1, "0".repeat(p1Token.length()));
        LineLink empty = helloRaw(SeatRequest.P1, "");
        server.tick(TICK);
        assertTrue(refusedAsTaken(drain(wrong)));
        assertTrue(refusedAsTaken(drain(empty)));
        assertFalse(server.isConnected(PieceAlignment.P1));
    }

    @Test
    void anAnyRequest_cannotStealAReservedSeat() {
        p1JoinsAndDrops();
        LineLink thief = helloRaw(SeatRequest.ANY, null);
        server.tick(TICK);
        assertTrue(refusedAsTaken(drain(thief)));
        assertFalse(server.isConnected(PieceAlignment.P1));
    }

    @Test
    void theRightToken_takesTheSeatBack_forNamedAndAnyRequests() {
        String token = p1JoinsAndDrops();
        LineLink back = helloRaw(SeatRequest.ANY, token);
        server.tick(TICK);
        List<ServerMessage> replies = drain(back);
        assertTrue(replies.stream().anyMatch(m -> m instanceof ServerMessage.Welcome w && w.seat() == PieceAlignment.P1));
        assertTrue(server.isConnected(PieceAlignment.P1));
        assertEquals(token, tokenOf(replies), "the seat keeps the same token across reconnects");
    }

    @Test
    void aSeatThatIsConnected_cannotBeTakenEvenWithItsToken() {
        LineLink p1 = seatRaw(SeatRequest.P1);
        server.tick(TICK);
        String token = tokenOf(drain(p1));
        LineLink second = helloRaw(SeatRequest.P1, token);
        server.tick(TICK);
        assertTrue(refusedAsTaken(drain(second)));
        assertTrue(p1.isOpen());
    }

    @Test
    void eachSeatHasItsOwnToken_andNeverReachesTheOtherSeat() {
        LineLink p1 = seatRaw(SeatRequest.P1);
        LineLink p2 = seatRaw(SeatRequest.P2);
        server.tick(TICK);
        server.tick(TICK);
        List<ServerMessage> p1Messages = drain(p1);
        List<ServerMessage> p2Messages = drain(p2);
        String t1 = tokenOf(p1Messages);
        String t2 = tokenOf(p2Messages);
        assertNotNull(t1);
        assertNotNull(t2);
        assertNotEquals(t1, t2);
        assertFalse(MessageCodecLines.anyContains(p2Messages, t1), "P2 never sees P1's token");
        assertFalse(MessageCodecLines.anyContains(p1Messages, t2), "P1 never sees P2's token");
    }

    private static final class MessageCodecLines {
        static boolean anyContains(List<ServerMessage> messages, String needle) {
            for (ServerMessage m : messages) if (MessageCodec.encode(m).contains(needle)) return true;
            return false;
        }
    }
}
