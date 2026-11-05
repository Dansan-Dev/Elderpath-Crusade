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
