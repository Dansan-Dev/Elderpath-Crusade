package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EcsPositionSyncTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    @Test
    void boardPosition_beforeLink_readsLocalValues() {
        Board.Position position = new Board.Position(null, 2, 3);

        assertEquals(2, position.getRow());
        assertEquals(3, position.getCol());
    }

    @Test
    void boardPosition_afterLink_readsFromPositionComponent() {
        Board.Position position = new Board.Position(null, 2, 3);
        Entity entity = engine.createEntity();
        entity.add(new PositionComponent().set(2, 3));
        engine.addEntity(entity);

        position.linkEntity(entity);
        entity.getComponent(PositionComponent.class).row = 5;

        assertEquals(5, position.getRow());
    }

    @Test
    void boardPosition_afterLink_writesGoToPositionComponent() {
        Board.Position position = new Board.Position(null, 2, 3);
        Entity entity = engine.createEntity();
        entity.add(new PositionComponent().set(2, 3));
        engine.addEntity(entity);
        position.linkEntity(entity);

        position.setRow(4);
        position.setCol(6);

        assertEquals(4, entity.getComponent(PositionComponent.class).row);
        assertEquals(6, entity.getComponent(PositionComponent.class).col);
    }
}
