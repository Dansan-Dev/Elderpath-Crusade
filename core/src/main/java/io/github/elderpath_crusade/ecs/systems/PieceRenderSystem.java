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
import io.github.elderpath_crusade.ecs.components.DeathFlashComponent;
import io.github.elderpath_crusade.ecs.components.MoveAnimationComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpawnAnimationComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
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
    private final ComponentMapper<MoveAnimationComponent> moveAnimMapper = ComponentMapper.getFor(MoveAnimationComponent.class);
    private final ComponentMapper<SpawnAnimationComponent> spawnAnimMapper = ComponentMapper.getFor(SpawnAnimationComponent.class);
    private final ComponentMapper<DeathFlashComponent> deathFlashMapper = ComponentMapper.getFor(DeathFlashComponent.class);
    private final Family family = Family.all(SpriteComponent.class, PositionComponent.class).get();

    private ImmutableArray<Entity> entities;

    public PieceRenderSystem() {}

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        entities = engine.getEntitiesFor(family);
    }

    /**
     * Draws every piece at its board position. boardRows/flipped mirror the logical row onto
     * the screen row (hotseat view flip) — positions themselves are never flipped.
     *
     * <p>A {@link MoveAnimationComponent} (FEAT-013) offsets where a piece is drawn without
     * ever touching {@code pos} itself — {@code PositionComponent} stays the instant,
     * authoritative cell the whole time, this only changes where its sprite is painted this
     * frame. A {@link SpawnAnimationComponent} likewise only fades the sprite's alpha in, and a
     * {@link DeathFlashComponent} fades it out — the latter only ever appears on a standalone
     * effect entity cloned from a real piece's sprite at the moment it died, never a real
     * piece's own entity, so it's exempt from the stun/exhaustion checks below (it isn't a
     * piece the current player's turn state applies to).
     */
    public void render(SpriteBatch batch, int zLevel, boolean isPaused,
                       int boardX, int boardY, int plotWidth, int plotHeight,
                       int boardRows, boolean flipped) {
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            SpriteComponent sprite = spriteMapper.get(entity);
            PositionComponent pos = posMapper.get(entity);

            if (sprite.renderable == null) continue;

            float renderRow = pos.row;
            float renderCol = pos.col;
            MoveAnimationComponent moveAnim = moveAnimMapper.get(entity);
            if (moveAnim != null) {
                float t = moveAnim.shapedProgress();
                renderRow = moveAnim.fromRow + (moveAnim.toRow - moveAnim.fromRow) * t;
                renderCol = moveAnim.fromCol + (moveAnim.toCol - moveAnim.fromCol) * t;
            }

            int absX = boardX + Math.round(renderCol * plotWidth);
            float displayRow = flipped ? boardRows - 1 - renderRow : renderRow;
            int absY = boardY + Math.round(displayRow * plotHeight);

            DeathFlashComponent deathFlash = deathFlashMapper.get(entity);
            boolean stunned = deathFlash == null && EntityUtils.isStunned(entity);
            // Always shows real exhaustion state regardless of whose turn it currently is — an
            // exhausted piece never looks "refreshed" just because the other player's turn
            // began, even though it won't actually regain actions until its own next turn.
            boolean exhausted = !stunned && deathFlash == null && EntityUtils.isExhausted(entity);
            SpawnAnimationComponent spawnAnim = spawnAnimMapper.get(entity);

            if (!stunned && !exhausted && spawnAnim == null && deathFlash == null) {
                sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
                continue;
            }

            Color original = batch.getColor().cpy();
            Color draw = stunned ? STUN_TINT.cpy() : exhausted ? DARKEN_TINT.cpy() : original.cpy();
            if (spawnAnim != null) draw.a *= spawnAnim.alpha();
            if (deathFlash != null) draw.a *= deathFlash.alpha();
            batch.setColor(draw);
            sprite.renderable.render(batch, zLevel, isPaused, absX, absY);
            batch.setColor(original);
            if (stunned) renderStunSymbol(batch, zLevel, absX, absY, plotWidth, plotHeight);
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
