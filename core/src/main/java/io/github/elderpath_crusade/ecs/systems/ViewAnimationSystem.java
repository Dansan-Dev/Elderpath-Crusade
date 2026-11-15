package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.DeathFlashComponent;
import io.github.elderpath_crusade.ecs.components.MoveAnimationComponent;
import io.github.elderpath_crusade.ecs.components.SpawnAnimationComponent;

/**
 * Advances every {@link MoveAnimationComponent}/{@link SpawnAnimationComponent}/
 * {@link DeathFlashComponent}'s elapsed time each frame — the only thing that mutates them
 * outside of {@code client.ViewAnimations} starting a new one. {@code PieceRenderSystem} only
 * ever reads them, never ticks or removes them, so the render pass stays side-effect-free.
 * Move/spawn belong to a real piece's own entity, so only the component is removed once done;
 * a death flash *is* its own standalone entity (never a real piece's), so the whole entity is
 * removed instead — leaving the component alone would strand a frozen, fully-faded sprite on
 * screen forever with nothing left to clean it up. Client-only (not registered headless, same
 * as {@code PieceRenderSystem}) — purely cosmetic, never touches
 * {@code PositionComponent}/{@code GridIndexSystem}.
 */
public class ViewAnimationSystem extends EntitySystem {
    private final ComponentMapper<MoveAnimationComponent> moveMapper = ComponentMapper.getFor(MoveAnimationComponent.class);
    private final ComponentMapper<SpawnAnimationComponent> spawnMapper = ComponentMapper.getFor(SpawnAnimationComponent.class);
    private final ComponentMapper<DeathFlashComponent> deathFlashMapper = ComponentMapper.getFor(DeathFlashComponent.class);
    private final Family moveFamily = Family.all(MoveAnimationComponent.class).get();
    private final Family spawnFamily = Family.all(SpawnAnimationComponent.class).get();
    private final Family deathFlashFamily = Family.all(DeathFlashComponent.class).get();

    private Engine engine;
    private ImmutableArray<Entity> moving;
    private ImmutableArray<Entity> spawning;
    private ImmutableArray<Entity> deathFlashing;

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        this.engine = engine;
        moving = engine.getEntitiesFor(moveFamily);
        spawning = engine.getEntitiesFor(spawnFamily);
        deathFlashing = engine.getEntitiesFor(deathFlashFamily);
    }

    @Override
    public void update(float deltaTime) {
        for (int i = moving.size() - 1; i >= 0; i--) {
            Entity entity = moving.get(i);
            MoveAnimationComponent anim = moveMapper.get(entity);
            anim.elapsed += deltaTime;
            if (anim.isDone()) entity.remove(MoveAnimationComponent.class);
        }
        for (int i = spawning.size() - 1; i >= 0; i--) {
            Entity entity = spawning.get(i);
            SpawnAnimationComponent anim = spawnMapper.get(entity);
            anim.elapsed += deltaTime;
            if (anim.isDone()) entity.remove(SpawnAnimationComponent.class);
        }
        for (int i = deathFlashing.size() - 1; i >= 0; i--) {
            Entity entity = deathFlashing.get(i);
            DeathFlashComponent flash = deathFlashMapper.get(entity);
            flash.elapsed += deltaTime;
            if (flash.isDone()) engine.removeEntity(entity);
        }
    }
}
