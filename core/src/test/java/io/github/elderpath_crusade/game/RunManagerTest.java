package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.SyntheticCardNames;
import io.github.elderpath_crusade.run.RelicDefinition;
import io.github.elderpath_crusade.run.RelicRunHook;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunNode;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.run.RunState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
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

    @BeforeEach
    void setUp() {
        PieceRegistry.register(new PieceDefinition("Wolf", 2, 3, 1, 1, 1, List.of()));
        PieceRegistry.register(new PieceDefinition("WolfCub", 1, 1, 1, 1, 1, List.of()));
        PieceRegistry.register(new PieceDefinition("Rogue", 3, 2, 2, 2, 1, List.of()));
        PieceRegistry.register(new PieceDefinition("Fairy", 2, 1, 1, 1, 1, List.of()));
        PieceRegistry.register(new PieceDefinition("Hero", 5, 5, 3, 1, 2, List.of()));
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        CardCatalog.registerSummon("Wolf Cub", "WolfCub");
        CardCatalog.registerSummon("Rogue", "Rogue");
        CardCatalog.registerSummon("Fairy", "Fairy");
        CardCatalog.registerSummon("Hero", "Hero");
    }

    @AfterEach
    void tearDown() {
        RelicRegistry.clear();
        CardCatalog.clear();
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

    // --- New-card rewards (UPG-004) ---

    @Test
    void offerNewCardChoices_returnsTheRequestedCountOfDistinctDraftableNames() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        List<String> choices = manager.offerNewCardChoices(2, new Random(7));

        assertEquals(2, choices.size());
        assertEquals(Set.copyOf(choices).size(), choices.size(), "must be distinct");
        for (String choice : choices) {
            assertTrue(CardCatalog.getDraftableNames().contains(choice));
        }
    }

    @Test
    void offerNewCardChoices_neverExceedsTheDraftablePoolSize() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        List<String> choices = manager.offerNewCardChoices(100, new Random(7));

        assertEquals(CardCatalog.getDraftableNames().size(), choices.size());
    }

    @Test
    void offerNewCardChoices_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.offerNewCardChoices(2));
    }

    @Test
    void grantNewCard_addsAFreshUnmodifiedInstanceToTheDeck() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        int before = manager.getRunState().deck().size();

        manager.grantNewCard("Hero");

        List<RunCardInstance> deck = manager.getRunState().deck();
        assertEquals(before + 1, deck.size());
        RunCardInstance granted = deck.get(deck.size() - 1);
        assertEquals("Hero", granted.cardName());
        assertFalse(granted.isModified());
    }

    @Test
    void grantNewCard_eachGrantGetsAUniqueInstanceId() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        // addCardToDeck throws on a duplicate instance id — reaching here twice proves uniqueness
        assertDoesNotThrow(() -> {
            manager.grantNewCard("Hero");
            manager.grantNewCard("Hero");
        });
    }

    @Test
    void grantNewCard_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.grantNewCard("Hero"));
    }

    // --- Relic offer + card upgrade (UPG-005) ---

    @Test
    void offerRelicChoices_excludesAlreadyActiveRelics() {
        RelicRegistry.register(RelicDefinition.battleOnly("Already", "desc", List.of()));
        RelicRegistry.register(RelicDefinition.battleOnly("NotYet", "desc", List.of()));
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.addRelic("Already");

        List<String> choices = manager.offerRelicChoices(5, new Random(3));

        assertEquals(List.of("NotYet"), choices);
    }

    @Test
    void offerRelicChoices_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.offerRelicChoices(2));
    }

    @Test
    void upgradeCard_mergesWithAnyExistingModification() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.getRunState().modifyCard("run-0", new RunCardModification(Map.of("damage", 1), List.of()));

        manager.upgradeCard("run-0", new RunCardModification(Map.of("damage", 2, "health", 1), List.of()));

        RunCardInstance updated = manager.getRunState().deck().get(0);
        assertEquals(Map.of("damage", 3, "health", 1), updated.modification().statDeltas());
    }

    @Test
    void upgradeCard_rejectsAnUnknownInstanceId() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        assertThrows(IllegalArgumentException.class,
                () -> manager.upgradeCard("no-such-id", new RunCardModification(Map.of("damage", 1), List.of())));
    }

    @Test
    void upgradeCard_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class,
                () -> manager.upgradeCard("run-0", new RunCardModification(Map.of("damage", 1), List.of())));
    }

    // --- Elders (ELDER-003) ---

    @Test
    void elderCardLockTurns_withNoElders_isZero() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        assertEquals(0, manager.elderCardLockTurns());
    }

    @Test
    void promoteToElder_movesTheCardOutOfTheDeckIntoElders() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.promoteToElder("run-0");

        assertEquals(3, manager.getRunState().deck().size());
        assertEquals(1, manager.getRunState().elders().size());
        assertEquals("run-0", manager.getRunState().elders().get(0).instanceId());
    }

    @Test
    void elderCardLockTurns_isTheHighestCostAmongElders() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.promoteToElder("run-0"); // Wolf, cost 2
        manager.promoteToElder("run-2"); // Wolf Cub, cost 1

        assertEquals(2, manager.elderCardLockTurns());
    }

    @Test
    void elderCardLockTurns_risesOnlyWhenANewElderCostsMore() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        manager.promoteToElder("run-2"); // Wolf Cub, cost 1
        assertEquals(1, manager.elderCardLockTurns());

        manager.promoteToElder("run-3"); // Wolf Cub, cost 1 — no higher, lock unchanged
        assertEquals(1, manager.elderCardLockTurns());

        manager.promoteToElder("run-0"); // Wolf, cost 2 — now the highest
        assertEquals(2, manager.elderCardLockTurns());
    }

    @Test
    void elderCardLockTurns_accountsForAModifiedEldersCostDelta() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.getRunState().modifyCard("run-0", new RunCardModification(Map.of("cost", 3), List.of()));

        manager.promoteToElder("run-0"); // Wolf base cost 2 + mod's +3

        assertEquals(5, manager.elderCardLockTurns());
    }

    @Test
    void promoteToElder_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, () -> manager.promoteToElder("run-0"));
    }

    @Test
    void elderCardLockTurns_requiresAnActiveRun() {
        RunManager manager = new RunManager();
        assertThrows(IllegalStateException.class, manager::elderCardLockTurns);
    }

    @Test
    void buildSpecForCurrentNode_withElders_passesCardLockTurnsForP1Only() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);
        manager.promoteToElder("run-0"); // Wolf, cost 2

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();

        assertEquals(2, spec.cardLockTurns().get(PieceAlignment.P1));
        assertFalse(spec.cardLockTurns().containsKey(PieceAlignment.P2));
    }

    @Test
    void buildSpecForCurrentNode_withNoElders_hasNoCardLockTurns() {
        RunManager manager = new RunManager();
        manager.startRun(RunDifficulty.DEFAULT, STARTING_DECK, SEED);

        LocalMatchSpec spec = manager.buildSpecForCurrentNode();

        assertTrue(spec.cardLockTurns().isEmpty());
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
