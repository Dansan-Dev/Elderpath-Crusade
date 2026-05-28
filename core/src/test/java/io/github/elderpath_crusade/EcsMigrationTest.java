package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePieceStats;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.managers.BoardManager;
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
        BoardManager.setBoard(board);
        engine = GameContext.get().getEcsEngine();
    }

    @Test
    void spawnCreatesEntityWithCorrectComponents() {
        String id = UUID.randomUUID().toString();
        MonsterGamePiece piece = new MonsterGamePiece(
                GamePieceStats.getMonsterStats(2, 5, 3, 2, 1),
                GamePieceType.MONSTER, PieceAlignment.P1, UUID.fromString(id), null);

        when(board.getGamePieceAtPos(1, 2)).thenReturn(piece);
        TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P1, 1, 2));

        ImmutableArray<Entity> entities = engine.getEntitiesFor(Family.all(IdentityComponent.class).get());
        assertEquals(1, entities.size());

        Entity entity = entities.first();
        assertNotNull(entity.getComponent(IdentityComponent.class));
        assertNotNull(entity.getComponent(AlignmentComponent.class));
        assertNotNull(entity.getComponent(PositionComponent.class));
        assertNotNull(entity.getComponent(StatsComponent.class));
        assertNotNull(entity.getComponent(SpriteComponent.class));
        assertNotNull(entity.getComponent(AbilityComponent.class));

        assertEquals(id, entity.getComponent(IdentityComponent.class).id);
        assertEquals(PieceAlignment.P1, entity.getComponent(AlignmentComponent.class).alignment);
        assertEquals(1, entity.getComponent(PositionComponent.class).row);
        assertEquals(2, entity.getComponent(PositionComponent.class).col);

        StatsComponent stats = entity.getComponent(StatsComponent.class);
        assertEquals(2, stats.cost);
        assertEquals(5, stats.maxHealth);
        assertEquals(3, stats.damage);
        assertEquals(2, stats.speed);
        assertEquals(1, stats.actions);

        // BaseMoveAbility + BaseAttackAbility are always attached
        assertTrue(entity.getComponent(AbilityComponent.class).abilityNames.size() >= 2);
    }

    @Test
    void moveUpdatesPosition() {
        String id = UUID.randomUUID().toString();
        MonsterGamePiece piece = new MonsterGamePiece(
                GamePieceStats.getMonsterStats(1, 3, 1, 1, 1),
                GamePieceType.MONSTER, PieceAlignment.P2, UUID.fromString(id), null);

        when(board.getGamePieceAtPos(0, 0)).thenReturn(piece);
        TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P2, 0, 0));

        TypedEventBus.get().emit(new PieceMovedEvent(
                id, PieceAlignment.P2, 0, 0, 3, 4,
                PieceMovedEvent.MovementType.ACTIVE, "test"));

        Entity entity = engine.getEntitiesFor(Family.all(PositionComponent.class).get()).first();
        assertEquals(3, entity.getComponent(PositionComponent.class).row);
        assertEquals(4, entity.getComponent(PositionComponent.class).col);
    }

    @Test
    void deathRemovesEntity() {
        String id = UUID.randomUUID().toString();
        MonsterGamePiece piece = new MonsterGamePiece(
                GamePieceStats.getMonsterStats(1, 2, 1, 1, 1),
                GamePieceType.MONSTER, PieceAlignment.P1, UUID.fromString(id), null);

        when(board.getGamePieceAtPos(2, 3)).thenReturn(piece);
        TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P1, 2, 3));
        assertEquals(1, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());

        TypedEventBus.get().emit(new PieceDiedEvent(id, 2, 3));
        assertEquals(0, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());
    }

    @Test
    void multipleSpawnsCreateMultipleEntities() {
        for (int i = 0; i < 3; i++) {
            String id = UUID.randomUUID().toString();
            MonsterGamePiece piece = new MonsterGamePiece(
                    GamePieceStats.getMonsterStats(1, 2 + i, 1, 1, 1),
                    GamePieceType.MONSTER, PieceAlignment.P1, UUID.fromString(id), null);
            when(board.getGamePieceAtPos(i, 0)).thenReturn(piece);
            TypedEventBus.get().emit(new PieceSpawnedEvent(id, PieceAlignment.P1, i, 0));
        }

        assertEquals(3, engine.getEntitiesFor(Family.all(IdentityComponent.class).get()).size());
    }
}
