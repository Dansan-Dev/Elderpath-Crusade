package io.github.elderpath_crusade.server.launcher;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.client.ClientCommands;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchSession.Phase;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The game's side of every local mode: a ClientMatchSession launching the real ServerLauncher as
 * a child process (this test JVM's classpath carries the server module, as the game's does) and
 * playing through it — solo vs a bot, hotseat, host + join — plus startup failures, a server
 * that dies mid-match, and shutdown.
 */
class ClientMatchSessionTest {
    private static final File ASSETS = new File(System.getProperty("elderpath.assetsDir", "../assets"));
    private static final long DEADLINE_NANOS = 30_000_000_000L;
    /** All 0-cost cards, so a summon is always affordable. */
    private static final List<String> CUBS = Collections.nCopies(8, "Wolf Cub");
    private static final String[] FAST = {"--time-scale", "20", "--tick-ms", "5"};

    private final List<ClientMatchSession> sessions = new ArrayList<>();

    @AfterEach
    void tearDown() {
        sessions.forEach(ClientMatchSession::close);
    }

    @Test
    @RequiresAssets
    void solo_humanPlaysAgainstTheServersBot_andCloseStopsTheServer() {
        ClientMatchSession session = track(ClientMatchSession.startLocal(
                LocalMatchSpec.solo(CUBS, CUBS, BotKind.SMART).withSeed(1L).withAssetsDir(ASSETS).withServerArgs(FAST)));
        assertEquals(Phase.STARTING_SERVER, session.getStatus().phase(), "launching doesn't block the caller");

        awaitPhase(session, Phase.PLAYING);
        assertEquals(List.of(PieceAlignment.P1), session.getControlledSeats());
        assertEquals(PieceAlignment.P1, session.actingSeat());
        assertEquals("BOT_SMART", session.viewState().getSlots().get(PieceAlignment.P2));

        CardInstance cub = session.viewState().getOwnHand().get(0);
        long seq = session.playSummon(cub.instanceId(), 0, 0);
        assertNotEquals(ClientCommands.NOT_SENT, seq);
        await(session, () -> session.viewState().getPieceAt(0, 0) != null);
        assertEquals(PieceAlignment.P1, session.viewState().getPieceAt(0, 0).owner());

        session.endTurn();
        await(session, () -> session.currentPlayer() == PieceAlignment.P2);
        assertNull(session.actingSeat(), "the bot's turn is not this screen's to act in");
        assertEquals(ClientCommands.NOT_SENT, session.endTurn(), "nothing is sent for a seat that isn't acting");
        await(session, () -> session.currentPlayer() == PieceAlignment.P1);
        assertTrue(ownedBy(session.viewState(), PieceAlignment.P2) > 0, "the bot played its turn on the server");
        assertNull(session.viewState().getLastRejection());

        session.close();
        assertEquals(Phase.CLOSED, session.getStatus().phase());
        awaitTrue(() -> !session.isServerAlive(), "the child server stops when the session closes");
    }

    @Test
    @RequiresAssets
    void hotseat_bothSeatsOnOneScreen_commandsGoThroughTheActingSeat() {
        ClientMatchSession session = track(ClientMatchSession.startLocal(
                LocalMatchSpec.twoHumans(CUBS, CUBS).withSeed(2L).withAssetsDir(ASSETS).withServerArgs(FAST)));
        awaitPhase(session, Phase.PLAYING);
        assertEquals(List.of(PieceAlignment.P1, PieceAlignment.P2), session.getControlledSeats());

        assertEquals(PieceAlignment.P1, session.actingSeat());
        assertEquals(PieceAlignment.P1, session.viewState().getSeat(), "the screen shows the acting seat");
        session.endTurn();
        await(session, () -> session.actingSeat() == PieceAlignment.P2 && !session.hasUnackedCommands());
        assertEquals(1, session.stateFor(PieceAlignment.P1).getLastAckedSeq(), "P1's end turn went through P1's connection");
        assertEquals(0, session.stateFor(PieceAlignment.P2).getLastAckedSeq());
        assertEquals(PieceAlignment.P2, session.viewState().getSeat(), "the view follows the turn");

        CardInstance cub = session.viewState().getOwnHand().get(0);
        session.playSummon(cub.instanceId(), 6, 2);
        await(session, () -> session.viewState().getPieceAt(6, 2) != null);
        assertEquals(PieceAlignment.P2, session.viewState().getPieceAt(6, 2).owner(), "P2's summon went through P2's connection");
        session.endTurn();
        await(session, () -> session.actingSeat() == PieceAlignment.P1 && !session.hasUnackedCommands());
        assertEquals(2, session.stateFor(PieceAlignment.P2).getLastAckedSeq());
        assertNull(session.stateFor(PieceAlignment.P1).getLastRejection());
        assertNull(session.stateFor(PieceAlignment.P2).getLastRejection());
        // Each connection gets the same events at its own pace; once both have caught up they agree.
        await(session, () -> sameView(session.stateFor(PieceAlignment.P1), session.stateFor(PieceAlignment.P2)));
    }

    @Test
    @RequiresAssets
    void hostAndJoin_aGuestJoinsTheHostsServer() {
        ClientMatchSession host = track(ClientMatchSession.host(
                LocalMatchSpec.twoHumans(CUBS, CUBS).withSeed(3L).withAssetsDir(ASSETS).withPlayerName("host").withServerArgs(FAST),
                "127.0.0.1", 0));
        awaitPhase(host, Phase.WAITING_FOR_PLAYERS);
        assertEquals(List.of(PieceAlignment.P1), host.getControlledSeats());
        assertTrue(host.getPort() > 0);

        ClientMatchSession guest = track(ClientMatchSession.join("127.0.0.1", host.getPort(), SeatRequest.ANY, "guest"));
        awaitPhase(guest, Phase.PLAYING);
        awaitPhase(host, Phase.PLAYING);
        assertEquals(List.of(PieceAlignment.P2), guest.getControlledSeats());
        assertFalse(guest.isServerAlive(), "a joining client runs no server of its own");

        assertEquals(PieceAlignment.P1, host.actingSeat());
        assertNull(guest.actingSeat());
        assertEquals(ClientCommands.NOT_SENT, guest.endTurn());
        host.endTurn();
        await(guest, () -> guest.actingSeat() == PieceAlignment.P2);
        await(host, () -> host.currentPlayer() == PieceAlignment.P2);
        assertNull(host.actingSeat(), "the guest's turn is not the host's to act in");
    }

    @Test
    void startFailure_isReported() {
        ClientMatchSession session = track(ClientMatchSession.startLocal(
                LocalMatchSpec.solo(CUBS, CUBS, BotKind.BASIC).withAssetsDir(new File("/nonexistent-elderpath-assets"))));
        awaitPhase(session, Phase.FAILED);
        assertTrue(session.getStatus().detail().contains("no data/pieces.yaml"), session.getStatus().detail());
        assertTrue(session.getStatus().isOver());
    }

    @Test
    void joiningNothing_isReported() throws IOException {
        int freePort;
        try (ServerSocket probe = new ServerSocket(0)) {
            freePort = probe.getLocalPort();
        }
        ClientMatchSession session = track(ClientMatchSession.join("127.0.0.1", freePort, SeatRequest.ANY, "lost"));
        awaitPhase(session, Phase.FAILED);
        assertTrue(session.getStatus().detail().contains("can't reach the match server"), session.getStatus().detail());
    }

    @Test
    @RequiresAssets
    void serverDyingMidMatch_isReported() {
        ClientMatchSession session = track(ClientMatchSession.startLocal(
                LocalMatchSpec.solo(CUBS, CUBS, BotKind.SMART).withAssetsDir(ASSETS).withServerArgs(FAST)));
        awaitPhase(session, Phase.PLAYING);

        ProcessHandle.current().children()
                .filter(p -> p.info().commandLine().orElse("").contains("ServerLauncher"))
                .forEach(ProcessHandle::destroyForcibly);

        awaitPhase(session, Phase.SERVER_EXITED);
        assertTrue(session.getStatus().isOver());
    }

    @Test
    @RequiresAssets
    void closingWhileStarting_leavesNoServerBehind() {
        ClientMatchSession session = ClientMatchSession.startLocal(
                LocalMatchSpec.solo(CUBS, CUBS, BotKind.SMART).withAssetsDir(ASSETS));
        session.close();
        assertEquals(Phase.CLOSED, session.getStatus().phase());
        // The startup thread finishes launching, sees the session is closed and stops the server.
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        long quietSince = -1;
        while (System.nanoTime() < deadline) {
            boolean anyServer = ProcessHandle.current().children()
                    .anyMatch(p -> p.info().commandLine().orElse("").contains("ServerLauncher"));
            if (anyServer) quietSince = -1;
            else if (quietSince < 0) quietSince = System.nanoTime();
            // No server for 5 s: longer than it takes to launch one, so it isn't still on its way.
            if (quietSince > 0 && System.nanoTime() - quietSince > 5_000_000_000L) return;
            sleep();
        }
        fail("a server launched for a closed session is still running");
    }

    // ---------------------------------------------------------------- helpers

    private ClientMatchSession track(ClientMatchSession session) {
        sessions.add(session);
        return session;
    }

    private void awaitPhase(ClientMatchSession session, Phase phase) {
        await(session, () -> session.getStatus().phase() == phase);
    }

    private void await(ClientMatchSession session, BooleanSupplier condition) {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        while (true) {
            session.poll();
            if (condition.getAsBoolean()) return;
            if (System.nanoTime() > deadline) fail("condition not reached; status " + session.getStatus());
            sleep();
        }
    }

    private static void awaitTrue(BooleanSupplier condition, String what) {
        long deadline = System.nanoTime() + DEADLINE_NANOS;
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) fail(what);
            sleep();
        }
    }

    private static int ownedBy(ClientMatchState state, PieceAlignment side) {
        return (int) state.getPieces().stream().filter(p -> p.owner() == side).count();
    }

    /** Both hotseat replicas agree on the board, turn and mana (hands differ: each sees only its own). */
    private static boolean sameView(ClientMatchState a, ClientMatchState b) {
        MatchView va = a.toView();
        MatchView vb = b.toView();
        return new HashSet<>(va.pieces()).equals(new HashSet<>(vb.pieces()))
                && va.currentPlayer() == vb.currentPlayer()
                && a.getMana(PieceAlignment.P1) == b.getMana(PieceAlignment.P1)
                && a.getMana(PieceAlignment.P2) == b.getMana(PieceAlignment.P2);
    }

    private static void sleep() {
        try {
            Thread.sleep(10);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("interrupted", e);
        }
    }
}
