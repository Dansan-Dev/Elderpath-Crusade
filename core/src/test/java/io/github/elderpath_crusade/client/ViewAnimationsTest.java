package io.github.elderpath_crusade.client;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.ecs.components.MoveAnimationComponent;
import io.github.elderpath_crusade.ecs.components.SpawnAnimationComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** ViewAnimations turns the replica's event stream into cosmetic view-entity animations. */
class ViewAnimationsTest {
    private ClientMatchState state;
    private ReplicaViewSync sync;
    private ViewAnimations animations;
    // StateSync always replaces the server's whole piece list (never a delta) — a spawn's own
    // StateSync must carry every piece spawned so far, or ReplicaViewSync.sync() reads an
    // earlier one's absence as a real despawn and removes its entity.
    private final Map<String, MatchView.PieceView> allPieces = new LinkedHashMap<>();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        PieceRegistry.register(new PieceDefinition("Runner", 2, 3, 1, 2, 1, List.of()));
        state = new ClientMatchState();
        sync = new ReplicaViewSync();
        animations = new ViewAnimations(sync);
        allPieces.clear();

        state.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                List.of(), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
    }

    private void spawnAndSettle(String id, PieceAlignment owner, int row, int col) {
        state.apply(new ServerMessage.Event(new PieceSpawnedEvent(id, owner, row, col, "Runner")));
        allPieces.put(id, new MatchView.PieceView(id, owner, "Runner", row, col, 3, 3, 1, 1, false));
        state.apply(new ServerMessage.StateSync(List.copyOf(allPieces.values())));
        sync.sync(state);
    }

    @Test
    void onEvent_move_attachesALinearAnimationFromTheEventsOwnCoordinates() {
        spawnAndSettle("a", PieceAlignment.P1, 1, 1);
        Entity entity = sync.getEntity("a");

        animations.onEvent(new PieceMovedEvent("a", PieceAlignment.P1, 1, 1, 3, 4,
                PieceMovedEvent.MovementType.ACTIVE, null, null));

        MoveAnimationComponent anim = entity.getComponent(MoveAnimationComponent.class);
        assertNotNull(anim);
        assertFalse(anim.pingPong);
        assertEquals(1f, anim.fromRow);
        assertEquals(1f, anim.fromCol);
        assertEquals(3f, anim.toRow);
        assertEquals(4f, anim.toCol);
    }

    @Test
    void onEvent_move_midFlight_startsTheNextAnimationFromTheCurrentVisualPositionNotTheEventsFrom() {
        spawnAndSettle("a", PieceAlignment.P1, 0, 0);
        Entity entity = sync.getEntity("a");

        // First move: (0,0) -> (0,4). Halfway through, a second move arrives reporting its own
        // "from" as (0,4) (the real logical cell at that instant) — but the piece is only
        // visually halfway there, at (0,2). The new animation must continue from (0,2), not
        // snap back to (0,0) or jump forward to the stale logical (0,4).
        animations.onEvent(new PieceMovedEvent("a", PieceAlignment.P1, 0, 0, 0, 4,
                PieceMovedEvent.MovementType.ACTIVE, null, null));
        MoveAnimationComponent firstAnim = entity.getComponent(MoveAnimationComponent.class);
        firstAnim.elapsed = MoveAnimationComponent.DURATION / 2f; // 50% through

        animations.onEvent(new PieceMovedEvent("a", PieceAlignment.P1, 0, 4, 0, 6,
                PieceMovedEvent.MovementType.ACTIVE, null, null));

        MoveAnimationComponent secondAnim = entity.getComponent(MoveAnimationComponent.class);
        assertEquals(0f, secondAnim.fromRow);
        assertEquals(2f, secondAnim.fromCol, 1e-5f, "continues from the halfway point, not the event's own \"from\"");
        assertEquals(0f, secondAnim.toRow);
        assertEquals(6f, secondAnim.toCol);
        assertEquals(0f, secondAnim.elapsed, "the new animation restarts its own timer");
    }

    @Test
    void onEvent_attack_lungesOnlyTheAttackerTowardTheDefenderAndBack() {
        spawnAndSettle("attacker", PieceAlignment.P1, 2, 2);
        spawnAndSettle("defender", PieceAlignment.P2, 2, 3);
        Entity attacker = sync.getEntity("attacker");
        Entity defender = sync.getEntity("defender");

        animations.onEvent(new PieceAttackedEvent("attacker", PieceAlignment.P1, 2, 2,
                "defender", 2, 3, 1));

        MoveAnimationComponent anim = attacker.getComponent(MoveAnimationComponent.class);
        assertNotNull(anim);
        assertTrue(anim.pingPong, "a lunge returns to its own cell, never stays at the defender's");
        assertEquals(2f, anim.fromRow);
        assertEquals(2f, anim.fromCol);
        // Midpoint is partway from (2,2) toward (2,3) — strictly between the two, never reaching
        // the defender's own cell (that would read as swapping places with it).
        assertEquals(2f, anim.toRow);
        assertTrue(anim.toCol > 2f && anim.toCol < 3f);

        assertNull(defender.getComponent(MoveAnimationComponent.class), "the defender never moves");
    }

    @Test
    void onEvent_spawn_attachesAFadeInOnlyOnce() {
        spawnAndSettle("a", PieceAlignment.P1, 0, 0);
        Entity entity = sync.getEntity("a");

        animations.onEvent(new PieceSpawnedEvent("a", PieceAlignment.P1, 0, 0, "Runner"));
        SpawnAnimationComponent first = entity.getComponent(SpawnAnimationComponent.class);
        assertNotNull(first);
        first.elapsed = 0.1f;

        animations.onEvent(new PieceSpawnedEvent("a", PieceAlignment.P1, 0, 0, "Runner"));
        assertSame(first, entity.getComponent(SpawnAnimationComponent.class),
                "a second spawn notice for the same piece never restarts an in-progress fade");
    }

    @Test
    void onEvent_unknownPieceId_doesNothing() {
        assertDoesNotThrow(() -> {
            animations.onEvent(new PieceMovedEvent("ghost", PieceAlignment.P1, 0, 0, 1, 1,
                    PieceMovedEvent.MovementType.ACTIVE, null, null));
            animations.onEvent(new PieceAttackedEvent("ghost", PieceAlignment.P1, 0, 0, "also-ghost", 1, 1, 1));
            animations.onEvent(new PieceSpawnedEvent("ghost", PieceAlignment.P1, 0, 0, "Runner"));
        });
    }
}
