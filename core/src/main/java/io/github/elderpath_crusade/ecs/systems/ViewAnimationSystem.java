package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.EntitySystem;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.MoveAnimationComponent;
import io.github.elderpath_crusade.ecs.components.SpawnAnimationComponent;

/**
 * Advances every {@link MoveAnimationComponent}/{@link SpawnAnimationComponent}'s elapsed time
 * each frame and removes it once done — the only thing that mutates them outside of
 * {@code client.ViewAnimations} starting a new one. {@code PieceRenderSystem} only ever reads
 * them, never ticks or removes them, so the render pass stays side-effect-free. Client-only
 * (not registered headless, same as {@code PieceRenderSystem}) — purely cosmetic, never
 * touches {@code PositionComponent}/{@code GridIndexSystem}.
 */
public class ViewAnimationSystem extends EntitySystem {
    private final ComponentMapper<MoveAnimationComponent> moveMapper = ComponentMapper.getFor(MoveAnimationComponent.class);
    private final ComponentMapper<SpawnAnimationComponent> spawnMapper = ComponentMapper.getFor(SpawnAnimationComponent.class);
    private final Family moveFamily = Family.all(MoveAnimationComponent.class).get();
    private final Family spawnFamily = Family.all(SpawnAnimationComponent.class).get();

    private ImmutableArray<Entity> moving;
    private ImmutableArray<Entity> spawning;

    @Override
    public void addedToEngine(Engine engine) {
        super.addedToEngine(engine);
        moving = engine.getEntitiesFor(moveFamily);
        spawning = engine.getEntitiesFor(spawnFamily);
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
    }
}
