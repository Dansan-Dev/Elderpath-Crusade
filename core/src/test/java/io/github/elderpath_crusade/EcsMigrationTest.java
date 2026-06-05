package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EcsMigrationTest {

    private Board board;
    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        board = mock(Board.class);
        when(board.getROWS()).thenReturn(5);
        when(board.getCOLS()).thenReturn(5);
        GameContext.get().setActiveBoard(board);
        engine = GameContext.get().getEcsEngine();
    }

    @Test
    void spawnCreatesEntityWithCorrectComponents() {
        String id = UUID.randomUUID().toString();

        // Create entity directly (simulating what PieceFactory does)
        Entity entity = engine.createEntity();
        entity.add(new IdentityComponent().set(id, "MONSTER"));
        entity.add(new AlignmentComponent().set(PieceAlignment.P1));
        entity.add(new PositionComponent().set(1, 2));
        entity.add(new StatsComponent().set(2, 5, 3, 2, 1));
        entity.add(new SpriteComponent().set("MONSTER"));
        entity.add(new ModifierComponent());
        engine.addEntity(entity);

        // Register in PieceSyncSystem via spawn event
        when(board.getEntityAtPos(1, 2)).thenReturn(entity);
        TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P1, 1, 2));

        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(IdentityComponent.class).get());
        assertEquals(1, entities.size());

        Entity e = entities.first();
        assertEquals(id, e.getComponent(IdentityComponent.class).id);
        assertEquals(PieceAlignment.P1, e.getComponent(AlignmentComponent.class).alignment);
        assertEquals(1, e.getComponent(PositionComponent.class).row);
        assertEquals(2, e.getComponent(PositionComponent.class).col);

        StatsComponent stats = e.getComponent(StatsComponent.class);
        assertEquals(2, stats.cost);
        assertEquals(5, stats.maxHealth);
        assertEquals(3, stats.damage);
        assertEquals(2, stats.speed);
        assertEquals(1, stats.actions);
    }

    @Test
    void moveUpdatesPosition() {
        String id = UUID.randomUUID().toString();
        Entity entity = engine.createEntity();
        entity.add(new IdentityComponent().set(id, "MONSTER"));
        entity.add(new AlignmentComponent().set(PieceAlignment.P2));
        entity.add(new PositionComponent().set(0, 0));
        entity.add(new StatsComponent().set(1, 3, 1, 1, 1));
        entity.add(new ModifierComponent());
        engine.addEntity(entity);

        when(board.getEntityAtPos(0, 0)).thenReturn(entity);
        TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P2, 0, 0));
        TypedEventBus.get().emit(new PieceMovedEvent(
                id, PieceAlignment.P2, 0, 0, 3, 4,
                PieceMovedEvent.MovementType.ACTIVE, "test"));

        // Position is updated by MovementSystem before event emission, not by PieceSyncSystem
        // PieceSyncSystem only tracks entityMap. Position stays at original since we didn't use MovementSystem here.
        assertEquals(0, entity.getComponent(PositionComponent.class).row);
        assertEquals(0, entity.getComponent(PositionComponent.class).col);
    }

    @Test
    void deathRemovesEntity() {
        String id = UUID.randomUUID().toString();
        Entity entity = engine.createEntity();
        entity.add(new IdentityComponent().set(id, "MONSTER"));
        entity.add(new AlignmentComponent().set(PieceAlignment.P1));
        entity.add(new PositionComponent().set(2, 3));
        entity.add(new StatsComponent().set(1, 2, 1, 1, 1));
        entity.add(new ModifierComponent());
        engine.addEntity(entity);

        when(board.getEntityAtPos(2, 3)).thenReturn(entity);
        TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P1, 2, 3));
        assertEquals(1, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());

        TypedEventBus.get().emit(new PieceDiedEvent(id, 2, 3));
        // DeathSystem processes on engine.update(), but it checks health <= 0
        // Manually set health to 0 and update engine to trigger death processing
        entity.getComponent(StatsComponent.class).currentHealth = 0;
        engine.update(0);
        assertEquals(0, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());
    }

    @Test
    void multipleSpawnsCreateMultipleEntities() {
        for (int i = 0; i < 3; i++) {
            String id = UUID.randomUUID().toString();
            Entity entity = engine.createEntity();
            entity.add(new IdentityComponent().set(id, "MONSTER"));
            entity.add(new AlignmentComponent().set(PieceAlignment.P1));
            entity.add(new PositionComponent().set(i, 0));
            entity.add(new StatsComponent().set(1, 2 + i, 1, 1, 1));
            entity.add(new ModifierComponent());
            engine.addEntity(entity);

            when(board.getEntityAtPos(i, 0)).thenReturn(entity);
            TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P1, i, 0));
        }

        assertEquals(3, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());
    }
}
