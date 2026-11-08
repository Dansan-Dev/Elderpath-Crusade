package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The two session shapes CLI-005 needs a real child-process server for — true hotseat (one
 * session, two connections to one server it launches itself) and a real {@code --mode host}
 * child process reached by a separate {@code --mode join} session — can't be substituted with
 * the in-process pattern {@code CliTwoSeatLoopTest}/{@code CliJoinSessionTest} use: hotseat's
 * {@code LocalMatchSpec.twoHumans} always launches its own server via {@code ServerProcess}, the
 * same real {@code ServerLauncher} a solo session does. Like {@code CliSoloSessionTest}, these
 * need real asset files on disk (a child process can't see this JVM's in-code registrations) —
 * @RequiresAssets-gated, skipped in the cloud profile, runs with -PtestProfile=local or real
 * assets present.
 */
@Timeout(30)
class CliHotseatAndHostTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");

    @Test
    @RequiresAssets
    void hotseat_oneSessionPlaysBothSeatsAndAnnouncesEachTurn() throws Exception {
        CliOptions options = CliOptions.parse(new String[]{"--mode", "hotseat", "--seed", "1", "--assets", ASSETS});

        try (ClientMatchSession session = options.startSession()) {
            ClientMatchSession.Status status = pumpUntilReadyOrOver(session);
            assertTrue(status.isReady(), "expected PLAYING, got " + status);
            assertEquals(List.of(PieceAlignment.P1, PieceAlignment.P2), session.getControlledSeats());

            List<String> script = List.of("end", "end", "quit");
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            PrintStream out = new PrintStream(buffer, true, StandardCharsets.UTF_8);

            CliLoop.run(session, LineSource.script(script), out);

            String transcript = buffer.toString(StandardCharsets.UTF_8);
            assertTrue(transcript.contains("=== P1's turn ==="), transcript);
            assertTrue(transcript.contains("=== P2's turn ==="), transcript);
            assertTrue(transcript.indexOf("=== P1's turn ===") < transcript.indexOf("=== P2's turn ==="), transcript);
        }
    }

    @Test
    @RequiresAssets
    void hostAndJoin_separateSessionsPlayTheSameRealChildProcessServer() throws Exception {
        CliOptions hostOptions = CliOptions.parse(new String[]{
                "--mode", "host", "--bind", "127.0.0.1", "--port", "0", "--seed", "1", "--assets", ASSETS});

        try (ClientMatchSession hostSession = hostOptions.startSession()) {
            // --port 0 binds an ephemeral port, only known once the child server has actually
            // started — wait for the host's own seat to be taken (WAITING_FOR_PLAYERS), by which
            // point hostSession.getPort() is populated, then point the guest at it.
            pumpUntil(hostSession, s -> s.getStatus().phase() == ClientMatchSession.Phase.WAITING_FOR_PLAYERS
                    || s.getStatus().isReady());

            CliOptions joinOptions = CliOptions.parse(new String[]{
                    "--mode", "join", "--host", "127.0.0.1", "--port", String.valueOf(hostSession.getPort()), "--seat", "p2"});
            try (ClientMatchSession joinSession = joinOptions.startSession()) {
                ClientMatchSession.Status hostStatus = pumpUntilReadyOrOver(hostSession);
                ClientMatchSession.Status joinStatus = pumpUntilReadyOrOver(joinSession);
                assertTrue(hostStatus.isReady(), "host session: " + hostStatus);
                assertTrue(joinStatus.isReady(), "join session: " + joinStatus);
                assertEquals(PieceAlignment.P1, hostSession.getControlledSeats().get(0));
                assertEquals(PieceAlignment.P2, joinSession.getControlledSeats().get(0));
            }
        }
    }

    private ClientMatchSession.Status pumpUntilReadyOrOver(ClientMatchSession session) throws InterruptedException {
        long deadline = System.nanoTime() + 25_000_000_000L;
        while (true) {
            session.poll();
            ClientMatchSession.Status status = session.getStatus();
            if (status.isReady() || status.isOver()) return status;
            if (System.nanoTime() - deadline > 0) fail("never reached PLAYING or a terminal status: " + status);
            Thread.sleep(20);
        }
    }

    private void pumpUntil(ClientMatchSession session, java.util.function.Predicate<ClientMatchSession> condition) throws InterruptedException {
        long deadline = System.nanoTime() + 25_000_000_000L;
        while (true) {
            session.poll();
            if (condition.test(session)) return;
            if (session.getStatus().isOver()) fail("session ended: " + session.getStatus());
            if (System.nanoTime() - deadline > 0) fail("condition never met; status=" + session.getStatus());
            Thread.sleep(20);
        }
    }
}
