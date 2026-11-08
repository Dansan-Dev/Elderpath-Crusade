package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Solo/hotseat/host all launch ServerLauncher as a real child process (ServerProcess) — a
 * separate JVM that can't see this test's in-code registrations, so it needs real asset files
 * on disk, same as every other @RequiresAssets child-process test (see ServerProcessTest in the
 * server module). Skipped in the cloud profile; runs with -PtestProfile=local or real assets
 * present.
 */
@Timeout(30)
class CliSoloSessionTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");

    @Test
    @RequiresAssets
    void solo_reachesPlayingAgainstARealChildProcessServer() throws Exception {
        CliOptions options = CliOptions.parse(new String[]{
                "--mode", "solo", "--bot", "smart", "--seed", "1", "--assets", ASSETS});

        try (ClientMatchSession session = options.startSession()) {
            ClientMatchSession.Status status = pumpUntilReadyOrOver(session);
            assertTrue(status.isReady(), "expected PLAYING, got " + status);
            assertEquals(PieceAlignment.P1, session.getControlledSeats().get(0));
            ClientMatchState state = session.viewState();
            assertNotNull(state);
            assertEquals("BOT_SMART", state.getSlots().get(PieceAlignment.P2));
            assertFalse(state.getOwnHand().isEmpty(), "P1 drew a starting hand");
        }
    }

    @Test
    void missingAssets_reportsFailureInsteadOfHanging() throws Exception {
        CliOptions options = CliOptions.parse(new String[]{
                "--mode", "solo", "--assets", "/nonexistent-elderpath-assets-" + System.nanoTime()});

        try (ClientMatchSession session = options.startSession()) {
            ClientMatchSession.Status status = pumpUntilReadyOrOver(session);
            assertTrue(status.isOver(), "expected a terminal status, got " + status);
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
}
