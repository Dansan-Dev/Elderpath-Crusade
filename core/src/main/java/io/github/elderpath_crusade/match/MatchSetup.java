package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.data.AbilityRegistry;
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
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.run.RelicDefinition;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
        ctx.getBotManager().resetSession(config.bots());

        ctx.getTurnManager().reset();
        ctx.getGameModeManager().setCurrent(config.mode());
        // A question left unanswered when its turn ends is dropped (cancelled: no effect, no cost).
        TypedEventBus.get().registerScoped("session", TurnEndedEvent.class, e -> ctx.getPickRouter().cancelAll());

        BoardGrid grid = new BoardGrid(config.rows(), config.cols());
        ctx.clearBoard(); // the previous match's view, if any; a room attaches a new one
        ctx.setActiveGrid(grid);
        for (Coord c : config.terrain()) placeTerrain(grid, c.row(), c.col());

        Random rng = config.seed() == null ? new Random() : new Random(config.seed());
        for (PieceAlignment alignment : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            ctx.getPlayerManager().setDeck(alignment, config.deck(alignment), rng).shuffle();
        }

        spawnRelicCarriers(config);
        return grid;
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
            if (relic == null) continue;
            for (String abilityName : relic.battleAbilityNames()) {
                AbilityDefinition def = AbilityRegistry.get(abilityName);
                if (def != null) abilities.add(def);
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
