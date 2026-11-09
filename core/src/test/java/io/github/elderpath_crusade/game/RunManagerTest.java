package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.SyntheticCardNames;
import io.github.elderpath_crusade.run.RelicDefinition;
import io.github.elderpath_crusade.run.RelicRunHook;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunNode;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.run.RunState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RunManager's own decision logic (spec building, outcome resolution, guards) — no live
 * ClientMatchSession/child-process server involved, so this needs no assets and runs in every
 * profile. The real session wiring is covered separately by an @RequiresAssets test. Uses the
 * seeded startRun overload throughout for determinism — the real map generator (MAP-001) can
 * branch, so tests reason off the map's actual shape rather than assuming a fixed id sequence.
 */
class RunManagerTest {
    private static final List<String> STARTING_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
    private static final long SEED = 42L;

    @AfterEach
    void tearDown() {
        RelicRegistry.clear();
    }

    /** The real generator can branch — finds a seed whose start node can reach a Rest node. */
    private static long seedWithReachableRestNode() {
        for (long seed = 1; seed <= 200; seed++) {
            RunMap map = RunMap.generate(seed);
            for (RunNode node : map.reachableFrom(map.startNodeId())) {
                if (node.type() == RunNodeType.REST) return seed;
            }
        }
        throw new IllegalStateException("no seed (1-200) found with a Rest node reachable from the start");
    }

    @Test
    void startRun_createsARunStateAtTheMapsStartNode() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        RunState state = manager.getRunState();
        assertNotNull(state);
        assertTrue(manager.hasActiveRun());
        assertFalse(manager.isNodeInProgress());
        assertEquals(state.map().startNodeId(), state.currentNode().id());
        assertEquals(RunNodeType.BATTLE, state.currentNode().type());
        assertFalse(state.isCurrentNodeCleared());
        assertTrue(state.map().nodes().size() > 1);
    }

    @Test
    void buildSpecForCurrentNode_mirrorsTheRunsDeckWithThePlayerAsP1() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();
        assertEquals(STARTING_DECK, spec.decks().get(PieceAlignment.P1));
        assertEquals(STARTING_DECK, spec.decks().get(PieceAlignment.P2));
        assertTrue(spec.bots().containsKey(PieceAlignment.P2));
        assertFalse(spec.bots().containsKey(PieceAlignment.P1));
    }

    @Test
    void buildSpecForCurrentNode_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, manager::buildSpecForCurrentNode);
    }

    @Test
    void reportNodeOutcome_loss_endsTheRunAsPermadeath() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.reportNodeOutcome(PieceAlignment.P2);

        RunState state = manager.getRunState();
        assertTrue(state.isEnded());
        assertFalse(state.isWon());
        assertFalse(manager.hasActiveRun());
    }

    @Test
    void reportNodeOutcome_nullWinner_isTreatedAsALoss() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.reportNodeOutcome(null);

        assertTrue(manager.getRunState().isEnded());
        assertFalse(manager.getRunState().isWon());
    }

    @Test
    void reportNodeOutcome_winOnANonTerminalNode_marksItClearedWithoutAdvancing() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        RunState state = manager.getRunState();
        int startId = state.currentNode().id();

        manager.reportNodeOutcome(PieceAlignment.P1);

        assertFalse(state.isEnded());
        assertEquals(startId, state.currentNode().id(), "a win doesn't move the player — picking the next node does");
        assertTrue(state.isCurrentNodeCleared());
    }

    @Test
    void reportNodeOutcome_winOnTheBossNode_endsTheRunAsWon() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        RunState state = manager.getRunState();
        // win/advance through every node up to (not including) the Boss
        while (!state.currentNode().isTerminal()) {
            if (!state.isCurrentNodeCleared()) manager.reportNodeOutcome(PieceAlignment.P1);
            manager.advanceToNextNode(state.reachableNodes().get(0).id());
        }

        manager.reportNodeOutcome(PieceAlignment.P1);

        assertTrue(state.isEnded());
        assertTrue(state.isWon());
    }

    @Test
    void advanceToNextNode_delegatesToRunStateOnceTheCurrentNodeIsCleared() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.reportNodeOutcome(PieceAlignment.P1); // win the start node, now cleared

        int next = manager.getRunState().reachableNodes().get(0).id();
        manager.advanceToNextNode(next);

        assertEquals(next, manager.getRunState().currentNode().id());
    }

    @Test
    void advanceToNextNode_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.advanceToNextNode(1));
    }

    @Test
    void reportNodeOutcome_rejectsAfterTheRunHasAlreadyEnded() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.reportNodeOutcome(PieceAlignment.P2); // loss, run ends

        assertThrows(IllegalStateException.class, () -> manager.reportNodeOutcome(PieceAlignment.P1));
    }

    @Test
    void pollActiveNode_requiresANodeToHaveBeenStarted() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        assertThrows(IllegalStateException.class, manager::pollActiveNode);
    }

    // --- Card modifications / relics plumbing into the battle spec (UPG-003) ---

    @Test
    void buildSpecForCurrentNode_modifiedDeckInstance_usesASyntheticNameAndCarriesTheModification() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        RunCardModification mod = new RunCardModification(Map.of("damage", 1), List.of("Frenzy"));
        manager.getRunState().modifyCard("run-0", mod); // STARTING_DECK.get(0) == "Wolf"

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();

        String expectedName = SyntheticCardNames.encode("Wolf", "run-0");
        assertTrue(spec.decks().get(PieceAlignment.P1).contains(expectedName));
        assertEquals(mod, spec.cardMods().get(PieceAlignment.P1).get("run-0"));
    }

    @Test
    void buildSpecForCurrentNode_withNoModifiedCards_hasNoCardMods() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();

        assertTrue(spec.cardMods().isEmpty());
        assertEquals(STARTING_DECK, spec.decks().get(PieceAlignment.P1));
    }

    @Test
    void buildSpecForCurrentNode_withActiveRelics_passesThemForP1Only() {
        RelicRegistry.register(RelicDefinition.battleOnly("TestRelic", "desc", List.of()));
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.addRelic("TestRelic");

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();

        assertEquals(List.of("TestRelic"), spec.relics().get(PieceAlignment.P1));
        assertFalse(spec.relics().containsKey(PieceAlignment.P2));
    }

    @Test
    void buildSpecForCurrentNode_withNoActiveRelics_hasNoRelics() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();

        assertTrue(spec.relics().isEmpty());
    }

    // --- RelicRunHook plumbing (UPG-002) ---

    @Test
    void addRelic_tracksItAndFiresOnAcquiredImmediately() {
        AtomicBoolean fired = new AtomicBoolean(false);
        RelicRegistry.register(new RelicDefinition("TestRelic", "desc", List.of(), List.of(
                hookOnAcquired(state -> fired.set(true)))));
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.addRelic("TestRelic");

        assertTrue(fired.get());
        assertEquals(List.of("TestRelic"), manager.getRunState().activeRelicIds());
    }

    @Test
    void addRelic_withNoRunHooks_isFine() {
        RelicRegistry.register(RelicDefinition.battleOnly("BattleOnlyRelic", "desc", List.of()));
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        assertDoesNotThrow(() -> manager.addRelic("BattleOnlyRelic"));
        assertEquals(List.of("BattleOnlyRelic"), manager.getRunState().activeRelicIds());
    }

    @Test
    void advanceToNextNode_enteringARestNode_firesOnEnterRestNodeHooks() {
        long seed = seedWithReachableRestNode();
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, seed);
        manager.reportNodeOutcome(PieceAlignment.P1); // clears the start node

        RunNode restNode = manager.getRunState().reachableNodes().stream()
                .filter(n -> n.type() == RunNodeType.REST).findFirst().orElseThrow();
        AtomicBoolean fired = new AtomicBoolean(false);
        RelicRegistry.register(new RelicDefinition("TestRelic", "desc", List.of(), List.of(
                hookOnEnterRestNode(state -> fired.set(true)))));
        manager.addRelic("TestRelic");

        manager.advanceToNextNode(restNode.id());

        assertTrue(fired.get());
    }

    @Test
    void advanceToNextNode_enteringANonRestNode_doesNotFireOnEnterRestNodeHooks() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.reportNodeOutcome(PieceAlignment.P1);

        RunNode nonRest = manager.getRunState().reachableNodes().stream()
                .filter(n -> n.type() != RunNodeType.REST).findFirst().orElseThrow();
        AtomicBoolean fired = new AtomicBoolean(false);
        RelicRegistry.register(new RelicDefinition("TestRelic", "desc", List.of(), List.of(
                hookOnEnterRestNode(state -> fired.set(true)))));
        manager.addRelic("TestRelic");

        manager.advanceToNextNode(nonRest.id());

        assertFalse(fired.get());
    }

    private static RelicRunHook hookOnAcquired(Consumer<RunState> onAcquired) {
        return new RelicRunHook() {
            @Override
            public void onAcquired(RunState state) {
                onAcquired.accept(state);
            }
        };
    }

    private static RelicRunHook hookOnEnterRestNode(Consumer<RunState> onEnterRestNode) {
        return new RelicRunHook() {
            @Override
            public void onEnterRestNode(RunState state) {
                onEnterRestNode.accept(state);
            }
        };
    }
}
