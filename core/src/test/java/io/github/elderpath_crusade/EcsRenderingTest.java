package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.ecs.systems.PieceRenderSystem;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.interfaces.Renderable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class EcsRenderingTest {

    private Engine engine;
    private PieceRenderSystem renderSystem;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        renderSystem = GameContext.get().getPieceRenderSystem();
    }

    @Test
    void spriteComponent_storesRenderable() {
        Renderable mockRenderable = mock(Renderable.class);
        SpriteComponent sprite = new SpriteComponent().set("sprites/test.png").setRenderable(mockRenderable);

        assertEquals("sprites/test.png", sprite.spritePath);
        assertSame(mockRenderable, sprite.renderable);
    }

    @Test
    void pieceRenderSystem_countsEntitiesWithSpriteAndPosition() {
        Renderable mockRenderable = mock(Renderable.class);

        Entity e1 = engine.createEntity();
        e1.add(new SpriteComponent().set("a").setRenderable(mockRenderable));
        e1.add(new PositionComponent().set(0, 0));
        engine.addEntity(e1);

        Entity e2 = engine.createEntity();
        e2.add(new SpriteComponent().set("b").setRenderable(mockRenderable));
        e2.add(new PositionComponent().set(1, 1));
        engine.addEntity(e2);

        Entity e3 = engine.createEntity();
        e3.add(new PositionComponent().set(2, 2));
        engine.addEntity(e3);

        assertEquals(2, renderSystem.getEntityCount());
    }

    @Test
    void pieceRenderSystem_excludesEntitiesWithoutPosition() {
        Entity entity = engine.createEntity();
        entity.add(new SpriteComponent().set("x").setRenderable(mock(Renderable.class)));
        engine.addEntity(entity);

        assertEquals(0, renderSystem.getEntityCount());
    }

    @Test
    void pieceRenderSystem_entityCountUpdatesOnRemoval() {
        Renderable mockRenderable = mock(Renderable.class);

        Entity e1 = engine.createEntity();
        e1.add(new SpriteComponent().set("a").setRenderable(mockRenderable));
        e1.add(new PositionComponent().set(0, 0));
        engine.addEntity(e1);

        Entity e2 = engine.createEntity();
        e2.add(new SpriteComponent().set("b").setRenderable(mockRenderable));
        e2.add(new PositionComponent().set(1, 1));
        engine.addEntity(e2);

        assertEquals(2, renderSystem.getEntityCount());

        engine.removeEntity(e1);

        assertEquals(1, renderSystem.getEntityCount());
    }
}
