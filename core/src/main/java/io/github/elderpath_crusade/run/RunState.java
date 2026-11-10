package io.github.elderpath_crusade.run;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A single roguelike run's state between battles — current position in the current floor's map,
 * the run's deck (as modifiable card instances), the deck instances retired into Elder status
 * (ELDER-001), active relics, chosen difficulty, and whether the run has ended. Pure logic/data,
 * no LibGDX — headlessly testable like {@code match/}. {@code RunManager} (client-side, {@code
 * GameContext}-held) is the only thing that drives this; nothing here talks to a battle server.
 *
 * <p>A run is {@code totalFloors} sequential floors (MULTI-001), each its own freshly generated
 * {@link RunMap} ending in its own Boss node — not one single map with one final boss. {@link
 * #map()}/{@link #currentNode()}/{@link #reachableNodes()}/{@link #currentNodeDepth()} all
 * operate on the <em>current</em> floor's map only; {@link #advanceToNextFloor} replaces it with
 * a freshly generated one once that floor's boss is cleared, resetting position but leaving the
 * deck/elders/relics untouched. {@link #isLastFloor()} is what tells the caller (RunManager)
 * whether clearing the current floor's boss should end the run instead.
 */
public class RunState {
    /** {@link #globalDepth()}'s per-floor offset — comfortably covers {@link RunMap#generate}'s default depth range (0..5). */
    private static final int FLOOR_DEPTH_SPAN = 6;

    private RunMap map;
    private final RunDifficulty difficulty;
    private final List<RunCardInstance> deck = new ArrayList<>();
    private final List<RunCardInstance> elders = new ArrayList<>();
    private final int startingDeckSize;
    private final RelicState relics = new RelicState();
    private final Set<Integer> visitedNodeIds = new LinkedHashSet<>();
    private int currentNodeId;
    private boolean currentNodeCleared;
    private int floorIndex; // 0-based
    private final int totalFloors;
    private boolean ended;
    private boolean won;
    private final long startedAtMillis;
    private long endedAtMillis;

    public RunState(RunMap firstFloorMap, RunDifficulty difficulty, List<RunCardInstance> startingDeck, int totalFloors) {
        if (firstFloorMap == null || difficulty == null) throw new IllegalArgumentException("map and difficulty are required");
        if (startingDeck == null || startingDeck.isEmpty()) throw new IllegalArgumentException("a run needs a starting deck");
        if (totalFloors < 1) throw new IllegalArgumentException("a run needs at least 1 floor");
        this.map = firstFloorMap;
        this.difficulty = difficulty;
        this.deck.addAll(startingDeck);
        this.startingDeckSize = startingDeck.size();
        this.totalFloors = totalFloors;
        this.floorIndex = 0;
        this.currentNodeId = map.startNodeId();
        this.visitedNodeIds.add(currentNodeId);
        this.startedAtMillis = System.currentTimeMillis();
    }

    /** Builds a fresh run's starting deck as unmodified instances, ids "run-0", "run-1", ... in order. */
    public static RunState start(RunMap firstFloorMap, RunDifficulty difficulty, List<String> startingDeckCardNames, int totalFloors) {
        List<RunCardInstance> startingDeck = new ArrayList<>();
        if (startingDeckCardNames != null) {
            for (String cardName : startingDeckCardNames) {
                startingDeck.add(RunCardInstance.unmodified("run-" + startingDeck.size(), cardName));
            }
        }
        return new RunState(firstFloorMap, difficulty, startingDeck, totalFloors);
    }

    /** The current floor's map — see the class doc. */
    public RunMap map() {
        return map;
    }

    /** 0-based: 0 on the first floor. */
    public int floorIndex() {
        return floorIndex;
    }

    public int totalFloors() {
        return totalFloors;
    }

    public boolean isLastFloor() {
        return floorIndex >= totalFloors - 1;
    }

    public RunDifficulty difficulty() {
        return difficulty;
    }

    public List<RunCardInstance> deck() {
        return List.copyOf(deck);
    }

    /**
     * The run's starting deck size, fixed for the whole run — what {@link
     * RunDifficulty#resolveForNode}'s bot-deck-size scaling actually uses, instead of {@link
     * #deck()}'s current (shrinking, as cards are promoted to Elder) size. Found via playtest:
     * the opponent's deck size was silently dropping over the run as more cards became Elders.
     */
    public int startingDeckSize() {
        return startingDeckSize;
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

    /** The current node's distance from the current floor's start node. */
    public int currentNodeDepth() {
        return map.depthOf(currentNodeId);
    }

    /**
     * {@link #currentNodeDepth()} plus a per-floor offset (MULTI-002), so floor 2/3 play harder
     * than floor 1 at an equivalent in-floor position — what {@link RunDifficulty#resolveForNode}
     * actually scales against now, instead of the floor-local depth alone.
     */
    public int globalDepth() {
        return floorIndex * FLOOR_DEPTH_SPAN + currentNodeDepth();
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

    /**
     * Replaces the current floor's map with a freshly generated next one (MULTI-001) — the
     * player's position resets to the new map's start, but the deck/elders/relics carry over
     * unchanged. Requires the current node to be this floor's (cleared) boss, and that this
     * isn't already the last floor — {@code RunManager.reportNodeOutcome} ends the run instead
     * of calling this once {@link #isLastFloor()} is true.
     */
    public void advanceToNextFloor(RunMap nextFloorMap) {
        requireNotEnded();
        if (nextFloorMap == null) throw new IllegalArgumentException("nextFloorMap required");
        if (!currentNodeCleared) {
            throw new IllegalStateException("the current floor's boss hasn't been cleared yet");
        }
        if (!currentNode().isTerminal()) {
            throw new IllegalStateException("not at this floor's boss");
        }
        if (isLastFloor()) {
            throw new IllegalStateException("already on the last floor — the run should end instead");
        }
        this.map = nextFloorMap;
        this.floorIndex++;
        this.currentNodeId = map.startNodeId();
        this.visitedNodeIds.clear();
        this.visitedNodeIds.add(currentNodeId);
        this.currentNodeCleared = false;
    }

    /** Ends the run — a loss (permadeath, any single defeat) or a win (the boss node was cleared). */
    public void endRun(boolean won) {
        requireNotEnded();
        this.ended = true;
        this.won = won;
        this.endedAtMillis = System.currentTimeMillis();
    }

    /**
     * Wall-clock epoch millis when this run started (the first floor's map was generated) —
     * exposed for {@link #durationMillis()}; a saved deck collection entry (Sprint 64) doesn't
     * need this directly, only the derived duration.
     */
    public long startedAtMillis() {
        return startedAtMillis;
    }

    /**
     * How long the run took, start to {@link #endRun} — a deck collection entry's
     * {@code run_completion_time} (Sprint 64). Only meaningful once {@link #isEnded()}; 0 before
     * then rather than throwing, since a caller might read it defensively mid-run.
     */
    public long durationMillis() {
        return ended ? endedAtMillis - startedAtMillis : 0L;
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
