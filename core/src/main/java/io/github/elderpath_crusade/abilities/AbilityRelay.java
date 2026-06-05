package io.github.elderpath_crusade.abilities;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.ecs.components.AbilityComponent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.GameContext;

import java.util.function.Consumer;

/**
 * Central relay that forwards TypedEventBus events to TriggeredAbility instances
 * on all living entities with AbilityComponent via ECS Family queries.
 */
public final class AbilityRelay {
    private static boolean started = false;
    private static Consumer<GameEvent> relayAll;
    private static Family abilityFamily;

    private AbilityRelay() {}

    public static void startIfNeeded() {
        if (started) return;
        started = true;
        abilityFamily = Family.all(AbilityComponent.class).get();
        relayAll = AbilityRelay::onGameEvent;
        // Register for all concrete event types
        TypedEventBus bus = TypedEventBus.get();
        bus.register(io.github.elderpath_crusade.events.TurnStartedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.TurnEndedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceSpawnedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceMovedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceAttackedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.PieceDiedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.ActionSpentEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.ActionsResetEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.ManaChangedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardDrawnEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardShuffledEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardDiscardedEvent.class, e -> relayAll.accept(e));
        bus.register(io.github.elderpath_crusade.events.CardPlayedEvent.class, e -> relayAll.accept(e));
    }

    public static void stop() {
        if (!started) return;
        started = false;
        relayAll = null;
    }

    private static void onGameEvent(GameEvent event) {
        Engine engine = GameContext.get().getEcsEngine();
        ImmutableArray<Entity> entities = engine.getEntitiesFor(abilityFamily);
        for (int i = 0; i < entities.size(); i++) {
            AbilityComponent ac = entities.get(i).getComponent(AbilityComponent.class);
            for (Ability a : ac.getAbilities()) {
                if (a instanceof TriggeredAbility trig) {
                    try { trig.onGameEvent(event); } catch (Exception ignored) {}
                    if (event instanceof TurnStartedEvent tse) {
                        try { trig.onTurnStarted(tse.player()); } catch (Exception ignored) {}
                    } else if (event instanceof TurnEndedEvent tee) {
                        try { trig.onTurnEnded(tee.player()); } catch (Exception ignored) {}
                    }
                }
            }
        }
    }
}
