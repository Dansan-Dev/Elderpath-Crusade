package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.MoveAnimationComponent;
import io.github.elderpath_crusade.ecs.components.SpawnAnimationComponent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Advances and removes the two cosmetic animation components; never touches anything else. */
class ViewAnimationSystemTest {
    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        engine = GameContext.createClient().getEcsEngine();
    }

    @AfterEach
    void tearDown() {
        TypedEventBus.get().clear();
    }

    @Test
    void update_advancesElapsedOnBothComponentTypes() {
        Entity entity = engine.createEntity();
        MoveAnimationComponent move = new MoveAnimationComponent().set(0, 0, 1, 1, false);
        SpawnAnimationComponent spawn = new SpawnAnimationComponent();
        entity.add(move);
        entity.add(spawn);
        engine.addEntity(entity);

        engine.update(0.05f);

        assertEquals(0.05f, move.elapsed, 1e-5f);
        assertEquals(0.05f, spawn.elapsed, 1e-5f);
    }

    @Test
    void update_removesAMoveAnimationOnceItsDurationElapses() {
        Entity entity = engine.createEntity();
        entity.add(new MoveAnimationComponent().set(0, 0, 1, 1, false));
        engine.addEntity(entity);

        engine.update(MoveAnimationComponent.DURATION + 0.01f);

        assertNull(entity.getComponent(MoveAnimationComponent.class));
    }

    @Test
    void update_removesASpawnAnimationOnceItsDurationElapses() {
        Entity entity = engine.createEntity();
        entity.add(new SpawnAnimationComponent());
        engine.addEntity(entity);

        engine.update(SpawnAnimationComponent.DURATION + 0.01f);

        assertNull(entity.getComponent(SpawnAnimationComponent.class));
    }

    @Test
    void update_onAnEntityWithNeitherComponent_doesNothing() {
        Entity entity = engine.createEntity();
        engine.addEntity(entity);

        assertDoesNotThrow(() -> engine.update(1f));
    }
}
