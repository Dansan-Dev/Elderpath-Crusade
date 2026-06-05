package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.path_loaders.ImagePathSpritesAndAnimations;

/**
 * ECS system that renders pieces using SpriteComponent + PositionComponent.
 * Applies stun/exhaustion tinting based on entity status.
 */
public class PieceRenderSystem extends EntitySystem {

    private static final Color STUN_TINT = new Color(1f, 0.22f, 0.71f, 1f);
    private static final Color DARKEN_TINT = new Color(0.6f, 0.6f, 0.6f, 1.0f);

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

    public void render(SpriteBatch batch, int zLevel, boolean isPaused,
                       int boardX, int boardY, int plotWidth, int plotHeight) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            SpriteComponent sprite = spriteMapper.get(entity);
            PositionComponent pos = posMapper.get(entity);

            if (sprite.renderable == null) continue;

            int absX = boardX + pos.col * plotWidth;
            int absY = boardY + pos.row * plotHeight;

            if (EntityUtils.isStunned(entity)) {
                Color original = batch.getColor().cpy();
                batch.setColor(STUN_TINT);
                sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
                batch.setColor(original);
                renderStunSymbol(batch, zLevel, absX, absY, plotWidth, plotHeight);
            } else if (EntityUtils.isExhausted(entity)) {
                PieceAlignment currentPlayer = GameContext.get().getTurnManager().getCurrentPlayer();
                if (EntityUtils.getAlignment(entity) == currentPlayer) {
                    Color original = batch.getColor().cpy();
                    batch.setColor(DARKEN_TINT);
                    sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
                    batch.setColor(original);
                } else {
                    sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
                }
            } else {
                sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
            }
        }
    }

    private void renderStunSymbol(SpriteBatch batch, int zLevel, int absX, int absY, int plotWidth, int plotHeight) {
        Texture stunTexture = GameContext.get().getTextureManager().getTexture(ImagePathSpritesAndAnimations.STUN.getPath());
        if (stunTexture == null) return;

        int symbolSize = Math.min(plotWidth, plotHeight) * 3 / 5;
        int symbolX = absX + (plotWidth - symbolSize) / 2;
        int symbolY = absY + (plotHeight - symbolSize) / 2;
        batch.draw(stunTexture, symbolX, symbolY, symbolSize, symbolSize);
    }

    public int getEntityCount() {
        return entities != null ? entities.size() : 0;
    }
}
