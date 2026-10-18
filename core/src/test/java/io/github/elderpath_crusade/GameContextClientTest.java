package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.ecs.systems.PieceRenderSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.ActionsResetEvent;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.events.PieceDiedEvent;
import io.github.elderpath_crusade.events.PieceKilledEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** A client context renders a match a server runs — it must never simulate one itself. */
class GameContextClientTest {
    private GameContext context;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        context = GameContext.createClient();
    }

    @AfterEach
    void tearDown() {
        TypedEventBus.get().clear();
    }

    @Test
    void engine_hasOnlyTheSpatialIndexAndTheRenderSystem() {
        Set<Class<?>> systems = new HashSet<>();
        for (EntitySystem system : context.getEcsEngine().getSystems()) systems.add(system.getClass());
        assertEquals(Set.of(GridIndexSystem.class, PieceRenderSystem.class), systems);
        assertTrue(context.isClient());
        assertFalse(context.isHeadless());
    }

    @Test
    void simulationServices_areAbsent() {
        assertNull(context.getGameServer());
        assertNull(context.getCardRules());
        assertNull(context.getLocalControl());
        assertNull(context.getTurnManager());
        assertNull(context.getPlayerManager());
        assertNull(context.getBotManager());
        assertNull(context.getWinConditionManager());
        assertNull(context.getPickRouter());
        assertNull(context.getMatchClock());
        assertNull(context.getCombatSystem());
        assertNull(context.getOnlineMatch());
        assertNotNull(context.getPieceRenderSystem(), "a client still renders pieces");
    }

    @Test
    void viewEntities_areNeverActedOn() {
        List<Object> events = new ArrayList<>();
        TypedEventBus bus = TypedEventBus.get();
        bus.register(PieceDiedEvent.class, events::add);
        bus.register(PieceKilledEvent.class, events::add);
        bus.register(GameWonEvent.class, events::add);
        bus.register(TurnStartedEvent.class, events::add);
        bus.register(ActionsResetEvent.class, events::add);

        Engine engine = context.getEcsEngine();
        // A piece the server says is at 0 health on the enemy's home row — a simulating context
        // would kill it or end the match; a client must leave it exactly as mirrored.
        Entity piece = engine.createEntity();
        piece.add(new IdentityComponent().set("p-1", "Runner"));
        piece.add(new AlignmentComponent().set(PieceAlignment.P1));
        piece.add(new PositionComponent().set(6, 2));
        StatsComponent stats = new StatsComponent().set(1, 3, 1, 2, 1);
        stats.currentHealth = 0;
        stats.remainingActions = 0;
        piece.add(stats);
        engine.addEntity(piece);

        for (int i = 0; i < 10; i++) engine.update(1f);

        assertTrue(engine.getEntities().contains(piece, true));
        assertEquals(0, stats.currentHealth);
        assertEquals(0, stats.remainingActions);
        assertEquals(6, piece.getComponent(PositionComponent.class).row);
        assertEquals(List.of(), events);
    }
}
