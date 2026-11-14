package io.github.elderpath_crusade.client;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** ReplicaViewSync mirrors a ClientMatchState into ECS view entities and a client BoardGrid. */
class ReplicaViewSyncTest {
    private ClientMatchState state;
    private ReplicaViewSync sync;
    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        engine = GameContext.get().getEcsEngine();
        PieceRegistry.register(new PieceDefinition("Runner", 2, 3, 1, 2, 1, List.of()));
        state = new ClientMatchState();
        sync = new ReplicaViewSync();
    }

    private void welcome(List<Coord> terrain) {
        state.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                terrain, Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
    }

    private void spawnAndSettle(String id, PieceAlignment owner, int row, int col, int health, int damage,
            int remainingActions, boolean stunned) {
        state.apply(new ServerMessage.Event(new PieceSpawnedEvent(id, owner, row, col, "Runner")));
        state.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView(id, owner, "Runner", row, col, health, health, damage, remainingActions, stunned))));
    }

    @Test
    void sync_beforeWelcome_doesNothing() {
        sync.sync(state);
        assertNull(sync.getGrid());
        assertEquals(0, engine.getEntitiesFor(Family.all(PositionComponent.class).get()).size());
    }

    @Test
    void sync_spawnsPieceWithRegistryBaseStatsAndEffectiveLiveStats() {
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, false);

        sync.sync(state);

        Entity entity = sync.getGrid() != null ? sync.getGrid().getEntityAt(1, 2) : null;
        assertNotNull(entity);
        assertEquals("a", entity.getComponent(IdentityComponent.class).id);
        assertEquals("Runner", entity.getComponent(IdentityComponent.class).name);
        assertEquals(PieceAlignment.P1, entity.getComponent(AlignmentComponent.class).alignment);

        StatsComponent stats = entity.getComponent(StatsComponent.class);
        assertEquals(2, stats.cost, "base cost from PieceRegistry");
        assertEquals(2, stats.speed, "base speed from PieceRegistry");
        assertEquals(1, stats.actions, "base actions from PieceRegistry");
        assertEquals(3, stats.maxHealth, "effective maxHealth from the server");
        assertEquals(3, stats.currentHealth);
        assertEquals(1, stats.damage, "effective damage from the server");
        assertEquals(1, stats.remainingActions);

        assertTrue(sync.getGrid().isOccupied(1, 2));
    }

    @Test
    void sync_movesPieceOnTheGridAndUpdatesPosition() {
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, false);
        sync.sync(state);

        state.apply(new ServerMessage.Event(
                new PieceMovedEvent("a", PieceAlignment.P1, 1, 2, 4, 2,
                        PieceMovedEvent.MovementType.ACTIVE, "PLAYER")));
        state.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView("a", PieceAlignment.P1, "Runner", 4, 2, 3, 3, 1, 0, false))));

        sync.sync(state);

        assertFalse(sync.getGrid().isOccupied(1, 2));
        assertTrue(sync.getGrid().isOccupied(4, 2));
        Entity entity = sync.getGrid().getEntityAt(4, 2);
        assertEquals(4, entity.getComponent(PositionComponent.class).row);
        assertEquals(2, entity.getComponent(PositionComponent.class).col);
    }

    @Test
    void sync_twoPiecesSwappingCellsInOneTick_bothEndUpCorrectlyIndexed() {
        // BUG-050: found via the user's playtest against a Fairy opponent (a swap-on-attack
        // ability, EffectExecutor.executeSwap) — moving each piece one at a time with the old
        // single-call grid.moveEntity(old, new) let the second piece's move blindly remove the
        // first piece's freshly-placed grid entry (its old cell was still "occupied" by the
        // first piece at that exact moment), leaving the first piece with a correct
        // PositionComponent — it still rendered fine — but no grid entry at all, so clicking the
        // plot it visually sat on found no piece there and did nothing.
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, false);
        spawnAndSettle("b", PieceAlignment.P2, 4, 2, 3, 1, 1, false);
        sync.sync(state);

        state.apply(new ServerMessage.Event(
                new PieceMovedEvent("a", PieceAlignment.P1, 1, 2, 4, 2,
                        PieceMovedEvent.MovementType.FORCED, "ABILITY", "SwapOnAttack")));
        state.apply(new ServerMessage.Event(
                new PieceMovedEvent("b", PieceAlignment.P2, 4, 2, 1, 2,
                        PieceMovedEvent.MovementType.FORCED, "ABILITY", "SwapOnAttack")));
        state.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView("a", PieceAlignment.P1, "Runner", 4, 2, 3, 3, 1, 1, false),
                new MatchView.PieceView("b", PieceAlignment.P2, "Runner", 1, 2, 3, 3, 1, 1, false))));

        sync.sync(state);

        Entity atOldBCell = sync.getGrid().getEntityAt(4, 2);
        assertNotNull(atOldBCell, "piece a must still have a grid entry at its new cell");
        assertEquals("a", atOldBCell.getComponent(IdentityComponent.class).id);

        Entity atOldACell = sync.getGrid().getEntityAt(1, 2);
        assertNotNull(atOldACell, "piece b must still have a grid entry at its new cell");
        assertEquals("b", atOldACell.getComponent(IdentityComponent.class).id);
    }

    @Test
    void sync_despawnsPieceRemovedFromTheReplica() {
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, false);
        sync.sync(state);
        assertTrue(sync.getGrid().isOccupied(1, 2));

        state.apply(new ServerMessage.Event(new PieceDiedEvent("a", 1, 2)));
        sync.sync(state);

        assertFalse(sync.getGrid().isOccupied(1, 2));
        assertEquals(0, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());
    }

    @Test
    void sync_mirrorsStunOnAndOff() {
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, true);
        sync.sync(state);
        Entity entity = sync.getGrid().getEntityAt(1, 2);
        assertTrue(entity.getComponent(StunComponent.class).isStunned());

        state.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView("a", PieceAlignment.P1, "Runner", 1, 2, 3, 3, 1, 1, false))));
        sync.sync(state);

        StunComponent stun = entity.getComponent(StunComponent.class);
        assertFalse(stun != null && stun.isStunned());
    }

    @Test
    void secondMatch_leavesThePreviousOnesEntitiesIfTheEngineIsntCleared() {
        // Pins the exact failure mode of BUG-034: a ReplicaViewSync is rebuilt fresh per
        // BattleRoom with an empty id map, so it has no way to know a PREVIOUS ReplicaViewSync's
        // entities are stale. Without an explicit clear between matches, they just keep existing
        // (and rendering) on the shared Engine/GridIndexSystem — this demonstrates that, so the
        // next test can show the fix actually depends on the clear, not on ReplicaViewSync itself.
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, false);
        sync.sync(state);
        assertTrue(sync.getGrid().isOccupied(1, 2));

        ReplicaViewSync secondMatchSync = new ReplicaViewSync();
        ClientMatchState secondMatchState = new ClientMatchState();
        secondMatchState.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                List.of(), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
        secondMatchState.apply(new ServerMessage.Event(new PieceSpawnedEvent("b", PieceAlignment.P1, 0, 0, "Runner")));
        secondMatchState.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView("b", PieceAlignment.P1, "Runner", 0, 0, 3, 3, 1, 1, false))));
        secondMatchSync.sync(secondMatchState);

        assertEquals(2, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size(),
                "match 1's piece is still in the Engine — ReplicaViewSync alone can't know it's stale");
    }

    @Test
    void secondMatch_withTheEngineClearedFirst_hasOnlyItsOwnEntities() {
        // The actual fix: BattleRoom's constructor now does exactly this — Engine.removeAllEntities()
        // + GridIndexSystem.clear() — before building a new ReplicaViewSync for the new match.
        welcome(List.of());
        spawnAndSettle("a", PieceAlignment.P1, 1, 2, 3, 1, 1, false);
        sync.sync(state);

        engine.removeAllEntities();
        engine.getSystem(GridIndexSystem.class).clear();

        ReplicaViewSync secondMatchSync = new ReplicaViewSync();
        ClientMatchState secondMatchState = new ClientMatchState();
        secondMatchState.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                List.of(), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
        secondMatchState.apply(new ServerMessage.Event(new PieceSpawnedEvent("b", PieceAlignment.P1, 0, 0, "Runner")));
        secondMatchState.apply(new ServerMessage.StateSync(List.of(
                new MatchView.PieceView("b", PieceAlignment.P1, "Runner", 0, 0, 3, 3, 1, 1, false))));
        secondMatchSync.sync(secondMatchState);

        assertEquals(1, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size(),
                "only match 2's piece should remain — match 1's is gone, not just unreachable");
        assertFalse(secondMatchSync.getGrid().isOccupied(1, 2), "match 1's old occupied cell must not bleed into the new grid");
        assertTrue(secondMatchSync.getGrid().isOccupied(0, 0));
    }

    @Test
    void sync_buildsTerrainOnceAndIsIdempotent() {
        welcome(List.of(new Coord(3, 1), new Coord(3, 3)));

        sync.sync(state);
        sync.sync(state);

        assertTrue(sync.getGrid().isOccupied(3, 1));
        assertTrue(sync.getGrid().isOccupied(3, 3));
        assertEquals(2, engine.getEntitiesFor(Family.all(TerrainComponent.class).get()).size(),
                "terrain entities must not be duplicated across repeated syncs");
    }
}
