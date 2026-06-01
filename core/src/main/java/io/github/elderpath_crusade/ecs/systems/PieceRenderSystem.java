package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.interfaces.Renderable;

/**
 * ECS system that renders pieces using SpriteComponent + PositionComponent.
 * Board delegates piece rendering to this system.
 */
public class PieceRenderSystem extends EntitySystem {

    private final ComponentMapper<SpriteComponent> spriteMapper = ComponentMapper.getFor(SpriteComponent.class);
    private final ComponentMapper<PositionComponent> posMapper = ComponentMapper.getFor(PositionComponent.class);
    private final Family family = Family.all(SpriteComponent.class, PositionComponent.class).get();

    private ImmutableArray<Entity> entities;

    public PieceRenderSystem() {}

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        entities = engine.getEntitiesFor(family);
    }

    /**
     * Render all piece entities at their grid positions.
     * @param batch the SpriteBatch (must already be between begin/end)
     * @param zLevel the current z-level being rendered
     * @param isPaused whether the game is paused
     * @param boardX the board's absolute X offset in pixels
     * @param boardY the board's absolute Y offset in pixels
     * @param plotWidth width of a single plot in pixels
     * @param plotHeight height of a single plot in pixels
     */
    public void render(SpriteBatch batch, int zLevel, boolean isPaused,
                       int boardX, int boardY, int plotWidth, int plotHeight) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            SpriteComponent sprite = spriteMapper.get(entity);
            PositionComponent pos = posMapper.get(entity);

            if (sprite.renderable == null) continue;

            int absX = boardX + pos.col * plotWidth;
            int absY = boardY + pos.row * plotHeight;
            sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
        }
    }

    /**
     * Get the number of entities this system would render.
     */
    public int getEntityCount() {
        return entities != null ? entities.size() : 0;
    }
}
