package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the "starting a new game leaves invisible pieces occupying
 * tiles" bug: GridIndexSystem.grid is a plain HashMap<position, Entity> updated only via
 * onEntitySpawned/onEntityMoved/onEntityDied — Ashley's Engine.removeAllEntities() (what
 * BattleRoom calls between matches) does NOT notify it, so it kept resolving dangling
 * entities from the previous match at their final positions. Board.getEntityAtPos
 * delegates entirely to this index, so every piece-occupancy check (click validation,
 * move highlighting, attack/summon targeting) saw those stale tiles as occupied by a
 * piece with intact stats/alignment but no sprite (nothing re-registers rendering for
 * an entity the engine no longer iterates).
 */
class GridIndexSystemMatchResetTest {

    private Engine engine;
    private GridIndexSystem gridIndex;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        gridIndex = engine.getSystem(GridIndexSystem.class);
    }

    @Test
    void removeAllEntities_doesNotClearTheSpatialIndex_onItsOwn() {
        Entity piece = engine.createEntity();
        piece.add(new AlignmentComponent().set(PieceAlignment.P1));
        piece.add(new PositionComponent().set(3, 2));
        engine.addEntity(piece);
        gridIndex.onEntitySpawned(piece, 3, 2);

        assertSame(piece, gridIndex.getEntityAt(3, 2));

        engine.removeAllEntities(); // what BattleRoom already did between matches

        assertSame(piece, gridIndex.getEntityAt(3, 2),
                "demonstrates the bug in isolation: the index still resolves a dangling entity "
                        + "after the engine has forgotten it, unless GridIndexSystem.clear() is also called");
    }

    @Test
    void explicitClear_afterRemoveAllEntities_leavesNoStaleOccupancy() {
        Entity piece = engine.createEntity();
        piece.add(new AlignmentComponent().set(PieceAlignment.P1));
        piece.add(new PositionComponent().set(3, 2));
        engine.addEntity(piece);
        gridIndex.onEntitySpawned(piece, 3, 2);

        engine.removeAllEntities();
        gridIndex.clear(); // the fix: BattleRoom now calls this alongside removeAllEntities()

        assertNull(gridIndex.getEntityAt(3, 2));
        assertFalse(gridIndex.isOccupied(3, 2));
    }
}
