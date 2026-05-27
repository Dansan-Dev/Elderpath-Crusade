package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;

/**
 * Resets remaining actions for the current player's entities when a turn starts.
 * Listens to TurnStartedEvent and processes all entities with Stats + Alignment.
 */
public class TurnSystem extends EntitySystem {
    private final ComponentMapper<StatsComponent> statsMapper = ComponentMapper.getFor(StatsComponent.class);
    private final ComponentMapper<AlignmentComponent> alignMapper = ComponentMapper.getFor(AlignmentComponent.class);
    private Family family;
    private Engine engine;

    @Override
    public void addedToEngine(Engine engine) {
        this.engine = engine;
        this.family = Family.all(StatsComponent.class, AlignmentComponent.class).get();

        TypedEventBus.get().register(TurnStartedEvent.class, this::onTurnStarted);
    }

    private void onTurnStarted(TurnStartedEvent event) {
        PieceAlignment player = event.player();
        ImmutableArray<Entity> entities = engine.getEntitiesFor(family);
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            AlignmentComponent align = alignMapper.get(e);
            if (align.alignment == player) {
                StatsComponent stats = statsMapper.get(e);
                stats.resetActions();
            }
        }
    }

    @Override
    public void update(float deltaTime) {
        // Event-driven; no per-frame work needed
    }
}
