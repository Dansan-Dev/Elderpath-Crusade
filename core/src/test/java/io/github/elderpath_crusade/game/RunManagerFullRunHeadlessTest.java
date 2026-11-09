package io.github.elderpath_crusade.game;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import io.github.elderpath_crusade.run.CardModificationCatalog;
import io.github.elderpath_crusade.run.RelicDefinition;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunNode;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.run.RunState;
import io.github.elderpath_crusade.server.GameServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * An actual headless playthrough of a full roguelike run — not a unit test of one method, but a
 * scripted "play the game" pass through {@link RunManager} exactly the way the rooms drive it
 * (same sequence of calls {@code RunDraftRoom}/{@code ElderPickRoom}/{@code EliteRewardRoom}/
 * {@code CardModTypeRoom}/{@code CardModTargetRoom}/{@code VictoryRoom} make), battling through
 * every floor to the end. Written to answer, with real engine state rather than a plan, three
 * questions after ELDER-006/MULTI-001-003/ELITE-001 landed: do Elders actually appear as pieces
 * on the board, do Elite rewards actually grant a relic or modification, and can a run actually
 * progress past one floor.
 *
 * <p>This sandbox has no GL context and no game assets (pieces.yaml etc. are gitignored and
 * absent here), so it can't launch the windowed game or use the real card roster — same
 * constraint as every other headless test in this suite. It substitutes a fast in-code test
 * piece ({@link #RACER}, speed 10 on a 7-row board) so a battle can be won in two of the
 * player's own turns via a single legitimate {@code movePlot} to the opponent's home row,
 * instead of actually playing out combat — the point here is exercising the run's own
 * structure/rewards/Elder-spawn code paths for real, not re-testing combat (already covered
 * elsewhere). Bypasses {@link RunManager#startCurrentNode}'s real child-process launch (also a
 * sandbox constraint — no assets on disk for a spawned server process to load) and instead
 * assembles the same {@link MatchConfig} {@code ServerLauncher} would from {@link
 * RunManager#buildSpecForCurrentNode}'s {@link LocalMatchSpec}, then drives {@link MatchSetup}/
 * {@link GameServer} directly in this process — the same engine, same code paths, just without
 * the process boundary a real battle would add.
 */
class RunManagerFullRunHeadlessTest {
    /** Cost 0 so the Elder card-lock mechanic never blocks anything here (not what this test is about). */
    private static final PieceDefinition RACER = new PieceDefinition("Racer", 0, 5, 1, 10, 2, List.of());
    private static final List<String> ENEMY_CARD_NAMES = List.of(
            "Rogue", "Fairy", "Wind Spirit", "Barbarian", "Sniper", "Crossbowman",
            "King", "Commander", "Hero", "Storm Mage");

    @BeforeEach
    void setUp() {
        GameContext.createHeadless();
        CardCatalog.clear();
        RelicRegistry.clear();
        PieceRegistry.register(RACER);
        CardCatalog.registerSummon("Racer", "Racer");
        for (String name : ENEMY_CARD_NAMES) {
            PieceRegistry.register(new PieceDefinition(name, 1, 3, 1, 1, 1, List.of()));
            CardCatalog.registerSummon(name, name);
        }
        RelicRegistry.register(RelicDefinition.battleOnly("TestRelicA", "desc", List.of()));
        RelicRegistry.register(RelicDefinition.battleOnly("TestRelicB", "desc", List.of()));
    }

    @AfterEach
    void tearDown() {
        CardCatalog.clear();
        RelicRegistry.clear();
    }

    @Test
    void fullRun_eldersSpawnAsPieces_eliteRewardsApply_multipleFloorsArePlayable() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, List.of("Racer", "Racer", "Racer", "Racer"), 777L);
        promoteFirstEligibleCard(manager); // RunDraftRoom's hand-off: pick the first Elder

        int battlesPlayed = 0, elitesHit = 0, relicsGranted = 0, modsGranted = 0;
        Set<Integer> floorsSeen = new LinkedHashSet<>();

        int guard = 0;
        while (!manager.getRunState().isEnded()) {
            if (++guard > 200) fail("run didn't terminate within 200 iterations — looks stuck");
            RunState state = manager.getRunState();
            floorsSeen.add(state.floorIndex());

            if (state.isCurrentNodeCleared()) {
                List<RunNode> reachable = state.reachableNodes();
                assertFalse(reachable.isEmpty(), "a cleared, non-ended run must always have somewhere to go");
                manager.advanceToNextNode(reachable.get(0).id());
                continue;
            }

            RunNodeType nodeType = state.currentNode().type();
            System.out.println("[run] floor " + state.floorIndex() + " node " + state.currentNode().id()
                    + " (" + nodeType + "), depth " + state.currentNodeDepth()
                    + ", elders so far: " + state.elders().size());

            PieceAlignment winner = playOneBattleToAWin(manager);
            assertEquals(PieceAlignment.P1, winner, "the player must win every scripted battle");
            battlesPlayed++;

            manager.reportNodeOutcome(winner);
            if (manager.getRunState().isEnded()) break;

            switch (nodeType) {
                case BOSS -> {
                    System.out.println("[run]   cleared floor " + state.floorIndex() + "'s boss — picking an Elder, advancing floor");
                    promoteFirstEligibleCard(manager);
                    manager.advanceToNextFloor();
                }
                case ELITE -> {
                    elitesHit++;
                    if (tryGrantRandomEliteReward(manager)) relicsGranted++; else modsGranted++;
                }
                default -> { /* ordinary Battle win: no reward (VictoryRoom/MULTI-003) */ }
            }
        }

        assertTrue(manager.getRunState().isEnded());
        assertTrue(manager.getRunState().isWon(), "the player won every battle — the run should end as won");
        assertEquals(RunManager.TOTAL_FLOORS - 1, Collections.max(floorsSeen), "the run should have reached the final floor");
        assertTrue(battlesPlayed >= RunManager.TOTAL_FLOORS, "at least one battle per floor, including every boss");
        assertTrue(elitesHit > 0, "the map generator guarantees at least one Elite per run (RunMap.generateBranching)");

        System.out.println("[run] COMPLETE — won=" + manager.getRunState().isWon()
                + ", battles=" + battlesPlayed + ", floors visited=" + floorsSeen
                + ", elites hit=" + elitesHit + ", relics granted=" + relicsGranted + ", mods granted=" + modsGranted
                + ", final elder count=" + manager.getRunState().elders().size());
    }

    // --- helpers: each mirrors exactly what the corresponding room does ---

    private static void promoteFirstEligibleCard(RunManager manager) {
        if (manager.getRunState().deck().size() <= 1) return; // ElderPickRoom.get's own guard
        String instanceId = manager.getRunState().deck().get(0).instanceId();
        manager.promoteToElder(instanceId);
        System.out.println("[run]   promoted " + instanceId + " to Elder (now " + manager.getRunState().elders().size()
                + " total, lock turns " + manager.elderCardLockTurns() + ")");
    }

    /** Mirrors EliteRewardRoom.get()'s random roll. Returns true if a relic was granted, false for a modification. */
    private static boolean tryGrantRandomEliteReward(RunManager manager) {
        if (new Random().nextBoolean()) {
            List<String> relicChoices = manager.offerRelicChoices(2);
            if (!relicChoices.isEmpty()) {
                manager.addRelic(relicChoices.get(0));
                assertTrue(manager.getRunState().activeRelicIds().contains(relicChoices.get(0)));
                System.out.println("[run]   Elite reward: relic " + relicChoices.get(0));
                return true;
            }
        }
        CardModificationCatalog.Entry entry = CardModificationCatalog.all().get(0);
        String targetInstanceId = manager.getRunState().deck().get(0).instanceId();
        manager.upgradeCard(targetInstanceId, entry.delta());
        boolean applied = manager.getRunState().deck().stream()
                .anyMatch(c -> c.instanceId().equals(targetInstanceId) && c.isModified());
        assertTrue(applied, "the chosen modification should actually be applied to the target card");
        System.out.println("[run]   Elite reward: modification " + entry.name() + " on " + targetInstanceId);
        return false;
    }

    /**
     * Builds the current node's battle exactly as a real one would (same MatchConfig a
     * ServerLauncher child process would be given), verifies every Elder is a real board piece
     * (ELDER-006), then wins it legitimately: summon a Racer at (0,0) — always free of the
     * Elders' centered columns, up to 3 of them on a 5-col board — end turn, let the bot take
     * its turn, then move the Racer straight to the opponent's home row (speed 10 on 7 rows).
     */
    private static PieceAlignment playOneBattleToAWin(RunManager manager) {
        LocalMatchSpec spec = manager.buildSpecForCurrentNode();
        MatchConfig config = MatchConfig.standard(GameMode.ROUGELIKE_RUN,
                        spec.decks().get(PieceAlignment.P1), spec.decks().get(PieceAlignment.P2),
                        spec.seed(), spec.bots())
                .withRelics(spec.relics())
                .withCardMods(spec.cardMods())
                .withCardLockTurns(spec.cardLockTurns())
                .withElders(spec.elders());

        BoardGrid grid = MatchSetup.prepare(config);
        MatchSetup.begin();

        verifyEldersOnBoard(manager, grid);

        GameServer server = GameContext.get().getGameServer();
        PlayerManager.PlayerState p1 = GameContext.get().getPlayerManager().get(PieceAlignment.P1);

        String cardId = p1.hand.getCards().get(0).instanceId();
        ActionResult summon = server.playSummonCard(PieceAlignment.P1, cardId, 0, 0);
        assertTrue(summon.isOk(), "P1's summon failed: " + summon);

        assertTrue(server.endTurn(PieceAlignment.P1).isOk());
        waitForTurn(PieceAlignment.P1, 400);

        // The bot may have summoned something in the meantime, so don't assume (goalRow, 0) is
        // still free — ask the grid which goal-row tile the Racer can actually reach.
        int goalRow = grid.getRows() - 1;
        Entity racer = grid.getEntityAt(0, 0);
        assertNotNull(racer, "the summoned Racer should still be at (0,0)");
        Coord destination = grid.getReachable(racer, 0, 0, EntityUtils.getSpeed(racer)).stream()
                .filter(c -> c.row() == goalRow)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no reachable tile on the opponent's home row (row " + goalRow + ")"));
        ActionResult move = server.movePlot(PieceAlignment.P1, 0, 0, destination.row(), destination.col());
        assertTrue(move.isOk(), "P1's winning move failed: " + move);

        return GameContext.get().getWinConditionManager().getWinner();
    }

    private static void verifyEldersOnBoard(RunManager manager, BoardGrid grid) {
        List<RunCardInstance> elders = manager.getRunState().elders();
        int homeRow = grid.getHomeRow(PieceAlignment.P1);
        int cols = grid.getCols();
        int startCol = Math.max(0, (cols - elders.size()) / 2);
        for (int i = 0; i < elders.size(); i++) {
            int col = startCol + i;
            Entity piece = grid.getEntityAt(homeRow, col);
            assertNotNull(piece, "Elder #" + i + " (" + elders.get(i).cardName() + ") should be a real piece on the home row");
            assertEquals(PieceAlignment.P1, EntityUtils.getAlignment(piece));
            assertFalse(EntityUtils.isExhausted(piece), "the Elder should have full actions from turn 1");
        }
        if (!elders.isEmpty()) {
            System.out.println("[run]   verified " + elders.size() + " Elder piece(s) on the board: "
                    + elders.stream().map(RunCardInstance::cardName).toList());
        }
    }

    private static void waitForTurn(PieceAlignment expected, int maxTicks) {
        for (int i = 0; i < maxTicks && GameContext.get().getTurnManager().getCurrentPlayer() != expected; i++) {
            MatchSetup.tick(0.05f);
        }
        assertEquals(expected, GameContext.get().getTurnManager().getCurrentPlayer(),
                "turn never came back around to " + expected);
    }
}
