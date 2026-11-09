package io.github.elderpath_crusade.run;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A single roguelike run's state between battles — current position in the map, the run's deck
 * (as modifiable card instances), the deck instances retired into Elder status (ELDER-001),
 * active relics, chosen difficulty, and whether the run has ended. Pure logic/data, no LibGDX —
 * headlessly testable like {@code match/}. {@code RunManager} (client-side, {@code GameContext}-
 * held) is the only thing that drives this; nothing here talks to a battle server.
 */
public class RunState {
    private final RunMap map;
    private final RunDifficulty difficulty;
    private final List<RunCardInstance> deck = new ArrayList<>();
    private final List<RunCardInstance> elders = new ArrayList<>();
    private final RelicState relics = new RelicState();
    private final Set<Integer> visitedNodeIds = new LinkedHashSet<>();
    private int currentNodeId;
    private boolean currentNodeCleared;
    private boolean ended;
    private boolean won;

    public RunState(RunMap map, RunDifficulty difficulty, List<RunCardInstance> startingDeck) {
        if (map == null || difficulty == null) throw new IllegalArgumentException("map and difficulty are required");
        if (startingDeck == null || startingDeck.isEmpty()) throw new IllegalArgumentException("a run needs a starting deck");
        this.map = map;
        this.difficulty = difficulty;
        this.deck.addAll(startingDeck);
        this.currentNodeId = map.startNodeId();
        this.visitedNodeIds.add(currentNodeId);
    }

    /** Builds a fresh run's starting deck as unmodified instances, ids "run-0", "run-1", ... in order. */
    public static RunState start(RunMap map, RunDifficulty difficulty, List<String> startingDeckCardNames) {
        List<RunCardInstance> startingDeck = new ArrayList<>();
        if (startingDeckCardNames != null) {
            for (String cardName : startingDeckCardNames) {
                startingDeck.add(RunCardInstance.unmodified("run-" + startingDeck.size(), cardName));
            }
        }
        return new RunState(map, difficulty, startingDeck);
    }

    public RunMap map() {
        return map;
    }

    public RunDifficulty difficulty() {
        return difficulty;
    }

    public List<RunCardInstance> deck() {
        return List.copyOf(deck);
    }

    /** Cards retired from the deck into Elder status (ELDER-001) — see {@link #addElder}. */
    public List<RunCardInstance> elders() {
        return List.copyOf(elders);
    }

    public List<String> activeRelicIds() {
        return relics.activeIds();
    }

    public Set<Integer> visitedNodeIds() {
        return Set.copyOf(visitedNodeIds);
    }

    public boolean isEnded() {
        return ended;
    }

    public boolean isWon() {
        return won;
    }

    public RunNode currentNode() {
        return map.node(currentNodeId);
    }

    public List<RunNode> reachableNodes() {
        return map.reachableFrom(currentNodeId);
    }

    /** The current node's distance from the start node — what DIFF-001's difficulty formula scales against. */
    public int currentNodeDepth() {
        return map.depthOf(currentNodeId);
    }

    /** Whether the current node has been resolved (a battle won, or a non-battle node entered) — see {@link #markCurrentNodeCleared()}. */
    public boolean isCurrentNodeCleared() {
        return currentNodeCleared;
    }

    /**
     * Marks the current node as resolved, so the player may advance past it — a battle won
     * (RunManager.reportNodeOutcome), or a non-battle node (e.g. Rest) that has nothing to
     * fight. Required before {@link #advanceTo}.
     */
    public void markCurrentNodeCleared() {
        requireNotEnded();
        currentNodeCleared = true;
    }

    /**
     * Moves to a node reachable from the current one — the player's pick, once the current
     * node is cleared. A real branching map can offer more than one reachable node; which one
     * to pick is the caller's (room's) decision, not this class's.
     */
    public void advanceTo(int nodeId) {
        requireNotEnded();
        if (!currentNodeCleared) {
            throw new IllegalStateException("the current node hasn't been cleared yet");
        }
        boolean reachable = reachableNodes().stream().anyMatch(n -> n.id() == nodeId);
        if (!reachable) {
            throw new IllegalArgumentException("node " + nodeId + " is not reachable from " + currentNodeId);
        }
        currentNodeId = nodeId;
        visitedNodeIds.add(nodeId);
        currentNodeCleared = false;
    }

    /** Ends the run — a loss (permadeath, any single defeat) or a win (the boss node was cleared). */
    public void endRun(boolean won) {
        requireNotEnded();
        this.ended = true;
        this.won = won;
    }

    public void addCardToDeck(RunCardInstance instance) {
        if (instance == null) throw new IllegalArgumentException("instance required");
        for (RunCardInstance existing : deck) {
            if (existing.instanceId().equals(instance.instanceId())) {
                throw new IllegalArgumentException("instance id already in deck: " + instance.instanceId());
            }
        }
        deck.add(instance);
    }

    /** Replaces an existing deck instance's modification (e.g. a Rest-node card-mod reward). */
    public void modifyCard(String instanceId, RunCardModification modification) {
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).instanceId().equals(instanceId)) {
                deck.set(i, deck.get(i).withModification(modification));
                return;
            }
        }
        throw new IllegalArgumentException("no deck instance with id " + instanceId);
    }

    public void addRelic(String relicId) {
        relics.add(relicId);
    }

    /**
     * Moves a deck instance to Elder status (ELDER-001) — removed from the drawable deck, kept
     * in {@link #elders()}. A run's deck must never go empty, so the last remaining deck card
     * can't be promoted. The card's own mana cost (RunManager's job to read, via CardCatalog —
     * this class stays pure data) is what drives the run's card-lock mechanic.
     */
    public void addElder(String instanceId) {
        requireNotEnded();
        if (deck.size() <= 1) {
            throw new IllegalStateException("can't promote the last card in the deck to Elder status");
        }
        for (int i = 0; i < deck.size(); i++) {
            if (deck.get(i).instanceId().equals(instanceId)) {
                elders.add(deck.remove(i));
                return;
            }
        }
        throw new IllegalArgumentException("no deck instance with id " + instanceId);
    }

    private void requireNotEnded() {
        if (ended) throw new IllegalStateException("run already ended");
    }
}
