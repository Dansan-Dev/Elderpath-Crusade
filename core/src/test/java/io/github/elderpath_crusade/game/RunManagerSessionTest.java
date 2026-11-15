package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.run.RunState;
import io.github.elderpath_crusade.run.RunNode;
import io.github.elderpath_crusade.client.AutoPlayer;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ClientMatchState;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Random;
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
 * PLAYING, and (ARCH-032) that a scripted run — {@link AutoPlayer} acting as the human P1 seat —
 * plays real battles to their end, with RunState following each outcome.
 */
class RunManagerSessionTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");
    private static final int MAX_BATTLES = 3;
    private static final long BATTLE_DEADLINE_NANOS = 60_000_000_000L;
    /** Strong enough for greedy auto-play to beat the run's bot at match seed 1 (see the test). */
    private static final List<String> SCRIPTED_RUN_DECK = List.of("Rogue", "Rogue", "Charger", "Charger");
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
    @Timeout(30)
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

    @Test
    @RequiresAssets
    @Timeout(400)
    void scriptedRun_autoPlayedBattlesResolve_andRunStateFollowsEachOutcome() throws Exception {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, SCRIPTED_RUN_DECK, 42L);
        // Bot paced for tests, not eyes; fixed match seed so the outcome is reproducible.
        manager.setServerArgs("--time-scale", "20", "--tick-ms", "5", "--seed", "1");
        AutoPlayer player = new AutoPlayer(AutoPlayer.Strategy.GREEDY, new Random(1));

        int battles = 0, wins = 0;
        while (manager.hasActiveRun() && battles < MAX_BATTLES) {
            if (manager.getRunState().isCurrentNodeCleared()) {
                moveToNextBattleNode(manager);
                if (!manager.hasActiveRun()) break;
                if (manager.getRunState().isCurrentNodeCleared()) continue; // non-battle node, auto-cleared
            }
            long start = System.nanoTime();
            manager.startCurrentNode();
            playUntilResolved(manager, player);
            battles++;
            if (manager.getRunState().isCurrentNodeCleared() || manager.getRunState().isWon()) wins++;
            System.err.printf("[ARCH-032] battle %d resolved in %.1f s: %s%n", battles,
                    (System.nanoTime() - start) / 1e9,
                    manager.getRunState().isEnded() ? (manager.getRunState().isWon() ? "run won" : "run lost") : "node cleared");

            // RunManager's own contract: a battle either clears its node (a win) or ends the run.
            assertFalse(manager.isNodeInProgress(), "a resolved node must not leave its session behind");
            assertTrue(manager.getRunState().isEnded() || manager.getRunState().isCurrentNodeCleared(),
                    "after battle " + battles + " the node must be cleared or the run over");
        }
        assertTrue(battles >= 1, "at least one real battle must have been played to its end");
        // Covers the win path too (clear node -> pick next -> fight again). This deck + match seed
        // won 3/3 battles in every run on 2026-11-15; if a balance change (pieces.yaml/
        // abilities.yaml/bot tuning) flips that, pick another deck/seed — not a code bug by itself.
        assertTrue(wins >= 1, "expected the pinned deck/seed to win at least one battle; battles=" + battles);
    }

    /** Picks the next node (a battle when one is reachable), advancing floors past a cleared boss. */
    private static void moveToNextBattleNode(RunManager manager) {
        RunState state = manager.getRunState();
        if (state.currentNode().isTerminal()) {
            manager.advanceToNextFloor();
            return;
        }
        List<RunNode> reachable = state.reachableNodes();
        RunNode next = reachable.stream().filter(n -> n.type().isBattle()).findFirst().orElse(reachable.get(0));
        manager.advanceToNextNode(next.id());
    }

    private static void playUntilResolved(RunManager manager, AutoPlayer player) throws InterruptedException {
        long deadline = System.nanoTime() + BATTLE_DEADLINE_NANOS;
        int commandsSent = 0, turnChanges = 0;
        PieceAlignment lastPlayer = null;
        while (!manager.pollActiveNode()) {
            ClientMatchSession session = manager.activeSession();
            if (session.getStatus().isReady() && player.step(session)) commandsSent++;
            PieceAlignment current = session.currentPlayer();
            if (current != lastPlayer) { turnChanges++; lastPlayer = current; }
            if (System.nanoTime() - deadline > 0) {
                ClientMatchState st = session.viewState();
                fail("battle never ended; status=" + session.getStatus() + ", commands sent=" + commandsSent
                        + ", turn changes=" + turnChanges + ", current=" + current
                        + ", unacked=" + session.hasUnackedCommands()
                        + ", pieces=" + (st == null ? "?" : st.getPieces().size())
                        + ", legal=" + (st == null ? "?" : st.getLegalActions()));
            }
            Thread.sleep(5);
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
