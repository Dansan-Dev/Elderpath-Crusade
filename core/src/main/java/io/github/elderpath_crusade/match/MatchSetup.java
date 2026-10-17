package io.github.elderpath_crusade.match;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;

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
        ctx.getWinConditionManager().resetSession();
        ctx.getBotManager().resetSession(config.bots());

        ctx.getTurnManager().reset();
        ctx.getGameModeManager().setCurrent(config.mode());

        BoardGrid grid = new BoardGrid(config.rows(), config.cols());
        ctx.clearBoard(); // the previous match's view, if any; a room attaches a new one
        ctx.setActiveGrid(grid);
        for (Coord c : config.terrain()) placeTerrain(grid, c.row(), c.col());

        Random rng = config.seed() == null ? new Random() : new Random(config.seed());
        for (PieceAlignment alignment : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            ctx.getPlayerManager().setDeck(alignment, config.deck(alignment), rng).shuffle();
        }
        return grid;
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
