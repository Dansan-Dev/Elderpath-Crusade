package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.data.AbilityRef;
import io.github.elderpath_crusade.data.AbilityRegistry;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.data.RelicRegistry;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.ComputedStatsComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.run.RelicDefinition;
import io.github.elderpath_crusade.run.RunCardModification;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Starts a match's simulation from a MatchConfig — headless, no rendering. A local room
 * calls prepare(), builds its views (which bind to the session's events), then begin(); a
 * headless server or test can call both back to back.
 */
public final class MatchSetup {
    private MatchSetup() {}

    /** Resets the previous match and sets up board, terrain, decks and bot slots. Returns the new grid. */
    public static BoardGrid prepare(MatchConfig config) {
        GameContext ctx = GameContext.get();

        // Reset previous session: ECS entities, the grid index (a plain map that
        // removeAllEntities() doesn't notify), session-scoped listeners, pending bot steps.
        ctx.getEcsEngine().removeAllEntities();
        ctx.getEcsEngine().getSystem(GridIndexSystem.class).clear();
        TypedEventBus.get().clearGroup("session");
        ctx.getMatchClock().clear();
        ctx.getPickRouter().reset();
        ctx.getWinConditionManager().resetSession();
        ctx.getBotManager().resetSession(config.bots(), config.seed());

        ctx.getTurnManager().reset();
        ctx.getGameModeManager().setCurrent(config.mode());
        // A question left unanswered when its turn ends is dropped (cancelled: no effect, no cost).
        TypedEventBus.get().registerScoped("session", TurnEndedEvent.class, e -> ctx.getPickRouter().cancelAll());

        BoardGrid grid = new BoardGrid(config.rows(), config.cols());
        ctx.clearBoard(); // the previous match's view, if any; a room attaches a new one
        ctx.setActiveGrid(grid);
        for (Coord c : config.terrain()) placeTerrain(grid, c.row(), c.col());

        registerSyntheticCards(config.decks(), config.elders(), config.cardMods());

        Random rng = config.seed() == null ? new Random() : new Random(config.seed());
        for (PieceAlignment alignment : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            ctx.getPlayerManager().setDeck(alignment, config.deck(alignment), rng).shuffle();
            // Explicit every prepare(), not just a reset default — a run instance id's lock
            // value changes between battles (ELDER-002), and PlayerManager/PlayerState persist
            // across matches in the same process (tests only — a real battle is a fresh process).
            PlayerManager.PlayerState player = ctx.getPlayerManager().get(alignment);
            player.cardLockTurns = config.cardLockTurns(alignment);
            player.turnsTaken = 0;
        }

        spawnRelicCarriers(config);
        spawnElderPieces(config, grid);
        return grid;
    }

    /**
     * Registers a one-off PieceDefinition/CardCatalog.Summon entry for every synthetic card
     * name (UPG-003) in either side's deck, so CardRules.playSummon's by-name lookup works on
     * a run-modified card exactly like any other. Must run before decks are handed to
     * PlayerManager so the first draw already resolves — nothing about deck shuffling/dealing
     * cares about a card's name beyond that CardCatalog.get(name) succeeds.
     *
     * <p>Modifications are looked up by instance id across BOTH sides' cardMods, not just the
     * side whose deck the name was found in — a bot's deck can be padded out with cycled
     * copies of the player's own card names (RunManager/RunDifficulty), synthetic ones
     * included, and the registration a given instance id resolves to must stay the same no
     * matter which side's deck is scanned first.
     *
     * <p>Public (not just {@link #prepare}'s own concern, BUG-046): {@code ClientMatchSession}
     * calls this too, with the exact same {@code LocalMatchSpec} data a roguelike-run battle is
     * about to launch with, so this process's own {@code CardCatalog}/{@code PieceRegistry} —
     * the client's, a separate process from the one that actually runs {@code prepare} — can
     * resolve a modified card's name/cost/speed/abilities for display. Those were never sent
     * over the wire for a synthetic registry key, which the protocol otherwise assumes any
     * receiver can already resolve locally (see {@code ReplicaViewSync}'s own doc).
     */
    public static void registerSyntheticCards(Map<PieceAlignment, List<String>> decks,
                                               Map<PieceAlignment, List<String>> elders,
                                               Map<PieceAlignment, Map<String, RunCardModification>> cardMods) {
        Map<String, RunCardModification> allMods = new LinkedHashMap<>();
        allMods.putAll(cardMods.getOrDefault(PieceAlignment.P1, Map.of()));
        allMods.putAll(cardMods.getOrDefault(PieceAlignment.P2, Map.of()));
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            for (String cardName : decks.getOrDefault(side, List.of())) {
                if (SyntheticCardNames.isSynthetic(cardName)) registerSyntheticCard(cardName, allMods);
            }
            // An Elder (ELDER-006) is never in the deck — it's retired out of it — so its own
            // synthetic name (if its modification makes it one) needs its own registration pass.
            for (String cardName : elders.getOrDefault(side, List.of())) {
                if (SyntheticCardNames.isSynthetic(cardName)) registerSyntheticCard(cardName, allMods);
            }
        }
    }

    /**
     * Always (re-)registers, overwriting any entry from an earlier call in this same process —
     * a run instance id's modification can grow between battles, so a stale definition left
     * from a previous match must never win. Used to be "tests only" (a real battle server
     * launches a fresh process per match) until BUG-046 made this public: a long-lived client
     * process now calls it once per battle too, for the exact same reason.
     */
    private static void registerSyntheticCard(String syntheticName, Map<String, RunCardModification> modsByInstanceId) {
        String baseCardName = SyntheticCardNames.baseCardName(syntheticName);
        String instanceId = SyntheticCardNames.instanceId(syntheticName);
        String baseKey = CardCatalog.getRegistryKeyForSummon(baseCardName);
        PieceDefinition base = baseKey == null ? null : PieceRegistry.get(baseKey);
        if (base == null) throw new IllegalStateException("unknown base card for modification: " + baseCardName);

        RunCardModification mod = modsByInstanceId.getOrDefault(instanceId, RunCardModification.NONE);
        String syntheticKey = PieceRegistry.toRegistryKey(syntheticName);
        PieceRegistry.register(applyModification(base, syntheticKey, mod));
        CardCatalog.registerSummon(syntheticName, syntheticKey);
    }

    /** base's stats plus mod's additive deltas (clamped to 0), base's abilities plus mod's added ones. */
    private static PieceDefinition applyModification(PieceDefinition base, String newId, RunCardModification mod) {
        Map<String, Integer> deltas = mod.statDeltas();
        int cost = Math.max(0, base.cost() + deltas.getOrDefault("cost", 0));
        int health = Math.max(0, base.health() + deltas.getOrDefault("health", 0));
        int damage = Math.max(0, base.damage() + deltas.getOrDefault("damage", 0));
        int speed = Math.max(0, base.speed() + deltas.getOrDefault("speed", 0));
        int actions = Math.max(0, base.actions() + deltas.getOrDefault("actions", 0));

        List<AbilityRef> abilities = new ArrayList<>(base.abilities());
        for (String abilityName : mod.addedAbilities()) abilities.add(new AbilityRef(abilityName));

        return new PieceDefinition(newId, cost, health, damage, speed, actions, abilities);
    }

    /**
     * Spawns one invisible, off-board "relic carrier" piece per side with at least one active
     * relic granting an in-battle ability (UPG-001) — reuses PassiveModifierSystem/
     * AbilityResolverSystem/ActionableAbilityExecutor completely unchanged, since an
     * AbilityDefinition doesn't care whether its owner is a real piece. A relic's own content
     * is curated in abilities.yaml (loaded by AbilityRegistry) same as any piece ability; only
     * which relic ids are active crosses the client->server boundary (see LocalMatchSpec/
     * LaunchOptions, LAUNCH-001).
     */
    private static void spawnRelicCarriers(MatchConfig config) {
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            List<AbilityDefinition> abilities = resolveRelicAbilities(config.relics(side));
            if (!abilities.isEmpty()) spawnCarrier(side, abilities);
        }
    }

    private static List<AbilityDefinition> resolveRelicAbilities(List<String> relicIds) {
        List<AbilityDefinition> abilities = new ArrayList<>();
        for (String relicId : relicIds) {
            RelicDefinition relic = RelicRegistry.get(relicId);
            if (relic == null) {
                Logger.error("MatchSetup", "relic '" + relicId + "' not found in relics.yaml — it will have no effect this match");
                continue;
            }
            for (String abilityName : relic.battleAbilityNames()) {
                AbilityDefinition def = AbilityRegistry.get(abilityName);
                if (def != null) {
                    abilities.add(def);
                } else {
                    Logger.error("MatchSetup", "relic '" + relicId + "' references ability '" + abilityName
                            + "', which was not found in abilities.yaml — this relic's effect will not apply");
                }
            }
        }
        return abilities;
    }

    /**
     * Off-board (row -1, a column per side so the two never collide) — unreachable via
     * BoardGrid/GridIndexSystem, so it's never a move/attack target and MatchServer's
     * pieceViews()/AbilityPopup explicitly skip its reserved id (RelicCarrierIds) rather than
     * ever needing a sprite or a registered piece definition. AbilityRules/
     * LegalActionsCalculator/TurnSystem all look pieces up by ECS component, not by board
     * position, so its abilities (passive, triggered, or player-activated via RelicHud) work
     * exactly like a real piece's.
     */
    private static void spawnCarrier(PieceAlignment side, List<AbilityDefinition> abilities) {
        Engine engine = GameContext.get().getEcsEngine();
        Entity entity = engine.createEntity();
        entity.add(new IdentityComponent().set(RelicCarrierIds.idFor(side), "RelicCarrier"));
        entity.add(new AlignmentComponent().set(side));
        entity.add(new PositionComponent().set(-1, side == PieceAlignment.P1 ? 0 : 1));
        int cost = 0, health = 1, damage = 0, speed = 0, actions = 1;
        entity.add(new StatsComponent().set(cost, health, damage, speed, actions));
        entity.add(new ModifierComponent());
        // Seeded to match the base stats above, not left at ComputedStatsComponent's zero
        // defaults: TurnSystem's turn-start action reset runs synchronously inside begin(),
        // before ModifierResolutionSystem's first tick ever computes this entity — an
        // uncomputed 0 here would read as "0 actions" and make the carrier's first-turn
        // active ability PIECE_EXHAUSTED even with no action-modifying relic involved. A real
        // summoned piece never hits this because it's never on the board before turn 1 starts.
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.cost = cost;
        computed.maxHealth = health;
        computed.damage = damage;
        computed.speed = speed;
        computed.actions = actions;
        entity.add(computed);

        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        for (AbilityDefinition def : abilities) aic.addAbility(def);
        entity.add(aic);

        engine.addEntity(entity);
    }

    /**
     * Spawns every Elder (ELDER-006) as a real, visible piece on its owner's home row, before
     * the match begins — using the Elder's own {@link PieceDefinition} (stats + abilities), not
     * a hand/summon flow: it's on the board from turn 1, counts against that row's summon tiles,
     * can act, be attacked, and be killed like any other piece. Centered among the home row's
     * columns, in {@link MatchConfig#elders} order; if a side somehow has more Elders than the
     * board has columns, the excess are silently skipped rather than thrown out of bounds. Must
     * run after {@link #registerSyntheticCards} so a modified Elder's synthetic name already
     * resolves via {@link CardCatalog#getPieceDefinition}.
     */
    private static void spawnElderPieces(MatchConfig config, BoardGrid grid) {
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            List<String> elderNames = config.elders(side);
            if (elderNames.isEmpty()) continue;
            int homeRow = grid.getHomeRow(side);
            int startCol = Math.max(0, (grid.getCols() - elderNames.size()) / 2);
            for (int i = 0; i < elderNames.size(); i++) {
                int col = startCol + i;
                if (col >= grid.getCols()) break;
                PieceDefinition def = CardCatalog.getPieceDefinition(elderNames.get(i));
                if (def == null) continue;
                spawnElderPiece(grid, side, def, homeRow, col);
            }
        }
    }

    /**
     * Builds one Elder's board entity by hand, the same way {@link #spawnCarrier} does rather
     * than through {@link io.github.elderpath_crusade.ecs.factory.PieceFactory} — it must seed
     * {@link ComputedStatsComponent} from the definition's own base stats up front, matching
     * {@code spawnCarrier}'s own comment on why: this piece exists before turn 1's {@code
     * TurnSystem.resetActionsForPlayer} ever runs, which reads {@code ComputedStatsComponent
     * .actions} (falling back to base only when the component is entirely absent) — a bare
     * default-constructed one here would read as 0 actions on the very first turn, with no
     * {@code PassiveModifierSystem}/{@code ModifierResolutionSystem} tick having run yet to fix
     * it. A piece summoned mid-match never hits this: by its owner's next turn, at least one
     * engine tick has already computed it correctly.
     */
    private static void spawnElderPiece(BoardGrid grid, PieceAlignment side, PieceDefinition def, int row, int col) {
        Engine engine = GameContext.get().getEcsEngine();
        Entity entity = engine.createEntity();
        String pieceId = UUID.randomUUID().toString();
        entity.add(new IdentityComponent().set(pieceId, def.id()));
        entity.add(new AlignmentComponent().set(side));
        entity.add(new PositionComponent().set(row, col));
        entity.add(new StatsComponent().set(def.cost(), def.health(), def.damage(), def.speed(), def.actions()));
        entity.add(new ModifierComponent());

        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.cost = def.cost();
        computed.maxHealth = def.health();
        computed.damage = def.damage();
        computed.speed = def.speed();
        computed.actions = def.actions();
        entity.add(computed);

        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        for (AbilityRef ref : def.abilities()) {
            AbilityDefinition abDef = ref.params().isEmpty()
                    ? AbilityRegistry.get(ref.name())
                    : AbilityRegistry.specialize(ref.name(), ref.params());
            if (abDef != null) aic.addAbility(abDef);
        }
        if (!aic.definitions.isEmpty()) entity.add(aic);

        engine.addEntity(entity);
        grid.addEntity(row, col, entity);
        TypedEventBus.get().emit(new PieceSpawnedEvent(pieceId, side, row, col, def.id()));
    }

    /** Starts the first turn (P1). */
    public static void begin() {
        GameContext.get().getTurnManager().startIfNeeded();
    }

    /** prepare() + begin(), for callers with no views to bind in between. */
    public static BoardGrid start(MatchConfig config) {
        BoardGrid grid = prepare(config);
        begin();
        return grid;
    }

    /** Advances the simulation one step: due scheduled tasks (bot pacing), then the ECS engine. */
    public static void tick(float deltaSeconds) {
        GameContext.get().getMatchClock().tick(deltaSeconds);
        GameContext.get().getEcsEngine().update(deltaSeconds);
    }

    private static void placeTerrain(BoardGrid grid, int row, int col) {
        Engine engine = GameContext.get().getEcsEngine();
        Entity terrain = engine.createEntity();
        terrain.add(new PositionComponent().set(row, col));
        terrain.add(new TerrainComponent());
        engine.addEntity(terrain);
        grid.addEntity(row, col, terrain);
    }
}
