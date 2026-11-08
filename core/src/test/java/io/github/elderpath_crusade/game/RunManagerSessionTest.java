package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RunManager.startCurrentNode() launches ServerLauncher as a real child process
 * (ClientMatchSession.startLocal), a separate JVM that needs real asset files on disk, same as
 * every other @RequiresAssets child-process test (see server/ServerProcessTest,
 * cli/CliSoloSessionTest). Skipped in the cloud profile; runs with -PtestProfile=local or real
 * assets present.
 *
 * <p>Proves the wiring from RunManager through ClientMatchSession to a real server reaches
 * PLAYING. A full "win several battles in a row" scripted run needs something to actually act
 * as the human (P1) seat against the bot — no such driver exists in core yet (the cli module's
 * RemoteBotDriver does this, but core can't depend on cli) — tracked as a backlog follow-up
 * rather than attempted here.
 */
@Timeout(30)
class RunManagerSessionTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");
    private static final List<String> STARTING_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");

    private String previousAssetsProperty;

    @BeforeEach
    void pointServerAtRealAssets() {
        previousAssetsProperty = System.getProperty(ClientMatchSession.ASSETS_PROPERTY);
        System.setProperty(ClientMatchSession.ASSETS_PROPERTY, ASSETS);
    }

    @AfterEach
    void restoreAssetsProperty() {
        if (previousAssetsProperty == null) System.clearProperty(ClientMatchSession.ASSETS_PROPERTY);
        else System.setProperty(ClientMatchSession.ASSETS_PROPERTY, previousAssetsProperty);
    }

    @Test
    @RequiresAssets
    void startCurrentNode_reachesPlayingAgainstARealChildProcessServer() throws Exception {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK);

        manager.startCurrentNode();
        ClientMatchSession session = manager.activeSession();
        try {
            ClientMatchSession.Status status = pumpUntilReadyOrOver(session);
            assertTrue(status.isReady(), "expected PLAYING, got " + status);
            assertTrue(manager.isNodeInProgress());
            assertThrows(IllegalStateException.class, manager::startCurrentNode,
                    "starting a second node while one is already in progress must be rejected");
        } finally {
            session.close();
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
