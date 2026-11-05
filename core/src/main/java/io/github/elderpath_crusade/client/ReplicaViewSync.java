package io.github.elderpath_crusade.client;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.net.protocol.MatchView.PieceView;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Mirrors a {@link ClientMatchState} into local ECS view entities (and a client-side
 * {@link BoardGrid}) so the existing renderers (PieceRenderSystem, PieceSpriteAttacher,
 * HighlightManager's board queries, CardPreviewPanel, ability popup) keep working against
 * a replica exactly as they do against the simulation today. Holds no game rules — it only
 * ever copies what the server already said.
 *
 * <p>Stats mirrored from {@link PieceView} (currentHealth, maxHealth, damage, remainingActions)
 * are the server's effective values. Base stats the protocol doesn't carry (cost, speed, base
 * actions) are filled in from {@link PieceRegistry} by registryKey, same as {@code PieceFactory}
 * does when building a simulated piece — view-only reads of these (e.g. CardPreviewPanel) see
 * the right numbers; nothing recomputes rules from them.
 */
public final class ReplicaViewSync {
    private final Map<String, Entity> entitiesById = new LinkedHashMap<>();
    private BoardGrid grid;
    private boolean terrainBuilt;

    /** The client-side board grid, built from the Welcome's dimensions on first sync, or null before that. */
    public BoardGrid getGrid() {
        return grid;
    }

    /** Mirrors the current replica into view entities. A no-op until the seat is welcomed. */
    public void sync(ClientMatchState state) {
        if (!state.isWelcomed()) return;
        Engine engine = GameContext.get().getEcsEngine();

        if (grid == null) {
            grid = new BoardGrid(state.getRows(), state.getCols());
            GameContext.get().setActiveGrid(grid);
        }
        if (!terrainBuilt) {
            buildTerrain(engine, state);
            terrainBuilt = true;
        }

        Set<String> seen = new HashSet<>();
        for (PieceView pieceView : state.getPieces()) {
            seen.add(pieceView.id());
            Entity entity = entitiesById.get(pieceView.id());
            if (entity == null) {
                entitiesById.put(pieceView.id(), spawn(engine, pieceView));
            } else {
                update(entity, pieceView);
            }
        }

        entitiesById.entrySet().removeIf(entry -> {
            if (seen.contains(entry.getKey())) return false;
            despawn(engine, entry.getValue());
            return true;
        });
    }

    private void buildTerrain(Engine engine, ClientMatchState state) {
        for (Coord c : state.getTerrain()) {
            Entity terrain = engine.createEntity();
            terrain.add(new PositionComponent().set(c.row(), c.col()));
            terrain.add(new TerrainComponent());
            engine.addEntity(terrain);
            grid.addEntity(c.row(), c.col(), terrain);
        }
    }

    private Entity spawn(Engine engine, PieceView pieceView) {
        Entity entity = engine.createEntity();
        entity.add(new IdentityComponent().set(pieceView.id(), pieceView.registryKey()));
        entity.add(new AlignmentComponent().set(pieceView.owner()));
        entity.add(new PositionComponent().set(pieceView.row(), pieceView.col()));
        entity.add(buildStats(pieceView));
        applyStun(entity, pieceView.stunned());
        engine.addEntity(entity);
        grid.addEntity(pieceView.row(), pieceView.col(), entity);
        return entity;
    }

    private void update(Entity entity, PieceView pieceView) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        if (pos.row != pieceView.row() || pos.col != pieceView.col()) {
            grid.moveEntity(pos.row, pos.col, entity, pieceView.row(), pieceView.col());
            pos.set(pieceView.row(), pieceView.col());
        }
        StatsComponent stats = entity.getComponent(StatsComponent.class);
        stats.maxHealth = pieceView.maxHealth();
        stats.currentHealth = pieceView.currentHealth();
        stats.damage = pieceView.damage();
        stats.remainingActions = pieceView.remainingActions();
        applyStun(entity, pieceView.stunned());
    }

    private void despawn(Engine engine, Entity entity) {
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        if (pos != null) grid.removeEntity(pos.row, pos.col);
        engine.removeEntity(entity);
    }

    private StatsComponent buildStats(PieceView pieceView) {
        StatsComponent stats = new StatsComponent();
        PieceDefinition def = PieceRegistry.get(pieceView.registryKey());
        if (def != null) {
            stats.set(def.cost(), def.health(), def.damage(), def.speed(), def.actions());
        }
        stats.maxHealth = pieceView.maxHealth();
        stats.currentHealth = pieceView.currentHealth();
        stats.damage = pieceView.damage();
        stats.remainingActions = pieceView.remainingActions();
        return stats;
    }

    private void applyStun(Entity entity, boolean stunned) {
        StunComponent stun = entity.getComponent(StunComponent.class);
        if (stunned) {
            if (stun == null) {
                stun = new StunComponent();
                entity.add(stun);
            }
            stun.turnsRemaining = Math.max(stun.turnsRemaining, 1);
        } else if (stun != null) {
            entity.remove(StunComponent.class);
        }
    }
}
