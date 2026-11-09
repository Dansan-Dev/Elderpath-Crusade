package io.github.elderpath_crusade.game;

import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.LocalMatchSpec;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.SyntheticCardNames;
import io.github.elderpath_crusade.run.EnemyDeckCatalog;
import io.github.elderpath_crusade.run.RelicDefinition;
import io.github.elderpath_crusade.run.RelicRunHook;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.run.RunMap;
import io.github.elderpath_crusade.run.RunNodeType;
import io.github.elderpath_crusade.run.RunState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Client-side orchestrator for a roguelike run (FEAT-006) — owns the active {@link RunState},
 * builds and launches each node's battle via {@link ClientMatchSession#startLocal}, resolves
 * that battle's outcome against RunState once it's known (advance on a win, permadeath on a
 * loss), and fires each active relic's non-battle {@link RelicRunHook}s at the right moments
 * (UPG-002) — there's no board/server concern between battles, so this class is already the
 * sole authority for them. {@code GameContext}-held, like {@code DeckManager}/{@code
 * GameModeManager}.
 *
 * <p>A modified deck instance (UPG-003) is passed to the battle under a synthetic card name
 * ({@link SyntheticCardNames}) plus its {@link RunCardModification}, keyed by instance id, via
 * {@link LocalMatchSpec#withCardMods}; the run's active relics (UPG-002) go the same way via
 * {@link LocalMatchSpec#withRelics}. The bot's deck is still the player's own deck (synthetic
 * names included), just padded out to {@link RunDifficulty#resolveForNode}'s resolved size.
 * The map is {@link RunMap#generate}'s real seeded branching generator (MAP-001).
 *
 * <p>Elders (ELDER-001 through 003, superseding UPG-004/005's reward flow): {@link
 * #promoteToElder} retires a deck card into {@link RunState#elders()}; {@link
 * #elderCardLockTurns()} (the highest mana cost among them) is passed to every future battle
 * via {@link LocalMatchSpec#withCardLockTurns} — the player's own side gains no mana and can't
 * play any card for that many of its own turns, each battle (enforced in {@code
 * PlayerManager}/{@code CardRules}). An Elder is also a real piece (ELDER-006): every battle,
 * each one spawns on the player's home row from turn 1 via {@link LocalMatchSpec#withElders} —
 * using the card's own stats/abilities, same synthetic-name scheme as a modified deck card
 * (UPG-003) when the Elder itself carries a modification.
 */
public final class RunManager {
    private RunState runState;
    private ClientMatchSession activeSession;

    public RunManager() {
    }

    /** Starts a brand-new run with a randomly seeded map: a fresh deck, nothing played yet. */
    public void startRun(RunDifficulty difficulty, List<String> startingDeckCardNames) {
        startRun(difficulty, startingDeckCardNames, new Random().nextLong());
    }

    /** Starts a brand-new run with a specific map seed (deterministic — tests, or a future "share this run" feature). */
    public void startRun(RunDifficulty difficulty, List<String> startingDeckCardNames, long mapSeed) {
        RunMap map = RunMap.generate(mapSeed);
        this.runState = RunState.start(map, difficulty, startingDeckCardNames);
        this.activeSession = null;
    }

    public RunState getRunState() {
        return runState;
    }

    public boolean hasActiveRun() {
        return runState != null && !runState.isEnded();
    }

    /** Whether a node's battle is currently in progress through this class's own session. */
    public boolean isNodeInProgress() {
        return activeSession != null;
    }

    /**
     * Builds the {@link LocalMatchSpec} for the run's current node. The player's deck is the
     * run's current deck's card names — a modified instance's name is a synthetic one encoding
     * its base card + instance id (UPG-003), with its {@link RunCardModification} carried
     * alongside via {@link LocalMatchSpec#withCardMods} so the battle can register and summon
     * it; the run's active relics go the same way via {@link LocalMatchSpec#withRelics}. The
     * opponent's bot kind and deck size come from {@link RunDifficulty#resolveForNode} (the
     * run's chosen level + this node's depth + {@link RunState#startingDeckSize()} — the run's
     * fixed starting size, not the player's current, possibly Elder-shrunk one, so the
     * opponent doesn't quietly get weaker over the run just because cards keep getting
     * promoted); its actual cards come from {@link EnemyDeckCatalog} for this node's type,
     * cycled to pad out to the resolved size — not the player's own deck (found via playtest:
     * the opponent used to just mirror it, cards and size both). The player always plays P1.
     */
    public LocalMatchSpec buildSpecForCurrentNode() {
        requireActiveRun();
        List<String> playerDeck = deckCardNames();
        RunDifficulty.ResolvedKnobs knobs = runState.difficulty().resolveForNode(
                runState.currentNodeDepth(), runState.currentNode().type(), runState.startingDeckSize());
        List<String> botDeck = padDeck(EnemyDeckCatalog.forNodeType(runState.currentNode().type()), knobs.botDeckSize());

        LocalMatchSpec spec = LocalMatchSpec.solo(playerDeck, botDeck, knobs.botKind());
        if (!runState.activeRelicIds().isEmpty()) {
            spec = spec.withRelics(PieceAlignment.P1, runState.activeRelicIds());
        }
        Map<String, RunCardModification> cardMods = new LinkedHashMap<>(deckCardModifications());
        cardMods.putAll(elderCardModifications());
        if (!cardMods.isEmpty()) {
            spec = spec.withCardMods(PieceAlignment.P1, cardMods);
        }
        List<String> elderNames = elderCardNames();
        if (!elderNames.isEmpty()) {
            spec = spec.withElders(PieceAlignment.P1, elderNames);
        }
        int lockTurns = elderCardLockTurns();
        if (lockTurns > 0) {
            spec = spec.withCardLockTurns(PieceAlignment.P1, lockTurns);
        }
        return spec;
    }

    /** A modified instance's name is synthetic (UPG-003); an unmodified one is its plain card name. */
    private List<String> deckCardNames() {
        List<String> names = new ArrayList<>();
        for (RunCardInstance instance : runState.deck()) {
            names.add(instance.isModified()
                    ? SyntheticCardNames.encode(instance.cardName(), instance.instanceId())
                    : instance.cardName());
        }
        return names;
    }

    /** Every modified deck instance's modification, keyed by instance id — see {@link LocalMatchSpec#withCardMods}. */
    private Map<String, RunCardModification> deckCardModifications() {
        Map<String, RunCardModification> mods = new LinkedHashMap<>();
        for (RunCardInstance instance : runState.deck()) {
            if (instance.isModified()) mods.put(instance.instanceId(), instance.modification());
        }
        return mods;
    }

    /** An Elder's board-spawn name — synthetic (UPG-003 scheme) if modified, else its plain card name. */
    private List<String> elderCardNames() {
        List<String> names = new ArrayList<>();
        for (RunCardInstance elder : runState.elders()) {
            names.add(elder.isModified()
                    ? SyntheticCardNames.encode(elder.cardName(), elder.instanceId())
                    : elder.cardName());
        }
        return names;
    }

    /** Every modified Elder's modification, keyed by instance id — merged into the battle's card mods (ELDER-006). */
    private Map<String, RunCardModification> elderCardModifications() {
        Map<String, RunCardModification> mods = new LinkedHashMap<>();
        for (RunCardInstance elder : runState.elders()) {
            if (elder.isModified()) mods.put(elder.instanceId(), elder.modification());
        }
        return mods;
    }

    /** Cycles through {@code deck} to build a deck of exactly {@code size} cards. */
    private static List<String> padDeck(List<String> deck, int size) {
        List<String> padded = new ArrayList<>();
        for (int i = 0; i < size; i++) padded.add(deck.get(i % deck.size()));
        return padded;
    }

    /**
     * Launches the current node's battle and keeps the session for {@link #pollActiveNode()}.
     * A room that drives its own {@code ClientMatchSession}/{@code BattleRoom} polling instead
     * can skip this and call {@link #reportNodeOutcome} directly once it knows the winner.
     */
    public ClientMatchSession startCurrentNode() {
        requireActiveRun();
        if (activeSession != null) throw new IllegalStateException("a node is already in progress");
        activeSession = ClientMatchSession.startLocal(buildSpecForCurrentNode());
        return activeSession;
    }

    public ClientMatchSession activeSession() {
        return activeSession;
    }

    /**
     * Polls the node started by {@link #startCurrentNode()} and, once its match has ended,
     * resolves the outcome against RunState and clears the session. Returns true once resolved
     * (false while still in progress) — call every frame, or in a tight loop for a headless
     * scripted run, until it returns true.
     */
    public boolean pollActiveNode() {
        if (activeSession == null) throw new IllegalStateException("no node in progress");
        activeSession.poll();
        if (!activeSession.getStatus().isOver()) return false;
        PieceAlignment winner = activeSession.getWinner();
        activeSession.close();
        activeSession = null;
        reportNodeOutcome(winner);
        return true;
    }

    /**
     * Resolves a node's battle outcome against RunState — ends the run as a loss (permadeath)
     * on anything but a P1 win (the player always plays P1, so a null/non-P1 winner, e.g. a
     * draw, counts as a loss); on a win, ends the run as won if the current node was the map's
     * terminal/Boss node, otherwise marks the current node cleared so the player can pick their
     * next one via {@link #advanceToNextNode}. Public so a room managing its own session can
     * report the result without going through {@link #startCurrentNode}/{@link #pollActiveNode}.
     */
    public void reportNodeOutcome(PieceAlignment winner) {
        requireActiveRun();
        boolean won = winner == PieceAlignment.P1;
        if (!won) {
            runState.endRun(false);
            return;
        }
        if (runState.currentNode().isTerminal()) {
            runState.endRun(true);
            return;
        }
        runState.markCurrentNodeCleared();
    }

    /**
     * Moves to the node the player picked among {@link RunState#reachableNodes()} (only valid
     * once the current node is cleared). A non-battle node (e.g. Rest) has nothing to fight, so
     * it's immediately marked cleared on arrival — the room sees it as already resolved and the
     * player can pick straight past it. Entering a Rest node also fires every active relic's
     * {@link RelicRunHook#onEnterRestNode} (UPG-002).
     */
    public void advanceToNextNode(int nodeId) {
        requireActiveRun();
        runState.advanceTo(nodeId);
        if (!runState.currentNode().type().isBattle()) {
            runState.markCurrentNodeCleared();
        }
        if (runState.currentNode().type() == RunNodeType.REST) {
            forEachRunHook(RelicRunHook::onEnterRestNode);
        }
    }

    /**
     * Up to {@code count} distinct card names drawn from {@link CardCatalog}'s draftable pool
     * (UPG-004) — a post-battle reward offer's choices. Picking one is {@link #grantNewCard};
     * which node types offer this, and how many choices, is the room's decision (UPG-005).
     */
    public List<String> offerNewCardChoices(int count) {
        return offerNewCardChoices(count, new Random());
    }

    /** Deterministic overload for tests. */
    public List<String> offerNewCardChoices(int count, Random rng) {
        requireActiveRun();
        List<String> pool = new ArrayList<>(CardCatalog.getDraftableNames());
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    /** Adds a fresh unmodified copy of {@code cardName} to the run's deck (a chosen UPG-004 reward). */
    public void grantNewCard(String cardName) {
        requireActiveRun();
        runState.addCardToDeck(RunCardInstance.unmodified("card-reward-" + UUID.randomUUID(), cardName));
    }

    /**
     * Up to {@code count} registered relic ids the run doesn't already have active (UPG-005's
     * Elite reward). Picking one is {@link #addRelic}.
     */
    public List<String> offerRelicChoices(int count) {
        return offerRelicChoices(count, new Random());
    }

    /** Deterministic overload for tests. */
    public List<String> offerRelicChoices(int count, Random rng) {
        requireActiveRun();
        List<String> pool = new ArrayList<>(RelicRegistry.getAllIds());
        pool.removeAll(runState.activeRelicIds());
        Collections.shuffle(pool, rng);
        return pool.subList(0, Math.min(count, pool.size()));
    }

    /**
     * Merges {@code delta} into {@code instanceId}'s existing modification (additive — an
     * upgrade stacks with any earlier one rather than replacing it) and applies the result
     * (UPG-005's stat-boost reward, the fallback when nothing new is left to offer).
     */
    public void upgradeCard(String instanceId, RunCardModification delta) {
        requireActiveRun();
        RunCardInstance existing = findDeckInstance(instanceId);
        runState.modifyCard(instanceId, existing.modification().plus(delta));
    }

    private RunCardInstance findDeckInstance(String instanceId) {
        for (RunCardInstance instance : runState.deck()) {
            if (instance.instanceId().equals(instanceId)) return instance;
        }
        throw new IllegalArgumentException("no deck instance with id " + instanceId);
    }

    /**
     * Moves a deck instance to Elder status (ELDER-003) — see {@link RunState#addElder}.
     * Retiring a card raises (or leaves unchanged) {@link #elderCardLockTurns()} for every
     * future battle, so promoting a cheap card is "free" while an expensive one costs turns.
     */
    public void promoteToElder(String instanceId) {
        requireActiveRun();
        runState.addElder(instanceId);
    }

    /**
     * How many of the player's own turns must pass, each battle, before mana/cards unlock —
     * the highest mana cost among the run's current Elders (ELDER-003), 0 with no Elders. A
     * modified Elder's own cost delta counts too, clamped the same way MatchSetup's synthetic
     * registration clamps a modified piece's stats.
     */
    public int elderCardLockTurns() {
        requireActiveRun();
        int max = 0;
        for (RunCardInstance elder : runState.elders()) {
            max = Math.max(max, elderCost(elder));
        }
        return max;
    }

    private static int elderCost(RunCardInstance elder) {
        int baseCost = CardCatalog.getCost(elder.cardName());
        int delta = elder.modification().statDeltas().getOrDefault("cost", 0);
        return Math.max(0, baseCost + delta);
    }

    /** Adds a relic to the run and fires its {@link RelicRunHook#onAcquired} (UPG-002), if any. */
    public void addRelic(String relicId) {
        requireActiveRun();
        runState.addRelic(relicId);
        RelicDefinition relic = RelicRegistry.get(relicId);
        if (relic != null) {
            for (RelicRunHook hook : relic.runHooks()) hook.onAcquired(runState);
        }
    }

    private void forEachRunHook(BiConsumer<RelicRunHook, RunState> call) {
        for (String relicId : runState.activeRelicIds()) {
            RelicDefinition relic = RelicRegistry.get(relicId);
            if (relic == null) continue;
            for (RelicRunHook hook : relic.runHooks()) call.accept(hook, runState);
        }
    }

    private void requireActiveRun() {
        if (runState == null) throw new IllegalStateException("no active run — call startRun first");
        if (runState.isEnded()) throw new IllegalStateException("the run has already ended");
    }
}
