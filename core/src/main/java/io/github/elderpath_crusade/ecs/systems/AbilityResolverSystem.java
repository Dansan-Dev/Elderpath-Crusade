package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.*;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.abilities.data.*;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;

import java.util.*;

/**
 * Queue-based ability resolver. Listens for game events, finds matching
 * ability reactions on entities, evaluates conditions, executes effects.
 */
public class AbilityResolverSystem extends EntitySystem {

    private Family family;
    private final Deque<GameEvent> eventQueue = new ArrayDeque<>();
    private boolean processing = false;

    @Override
    public void addedToEngine(Engine engine) {
        family = Family.all(AbilityInstanceComponent.class).get();
        TypedEventBus bus = TypedEventBus.get();
        bus.register(PieceSpawnedEvent.class, this::enqueue);
        bus.register(PieceMovedEvent.class, this::enqueue);
        bus.register(PieceAttackedEvent.class, this::enqueue);
        bus.register(PieceDiedEvent.class, this::enqueue);
        bus.register(PieceKilledEvent.class, this::enqueue);
        bus.register(TurnStartedEvent.class, this::enqueue);
        bus.register(TurnEndedEvent.class, this::enqueue);
    }

    private void enqueue(GameEvent event) {
        eventQueue.add(event);
        processQueue();
    }

    private void processQueue() {
        if (processing) return;
        processing = true;
        try {
            int safety = 0;
            while (!eventQueue.isEmpty() && safety < 1000) {
                safety++;
                processEvent(eventQueue.poll());
            }
        } finally {
            processing = false;
        }
    }

    private void processEvent(GameEvent event) {
        TriggerType trigger = TriggerMatcher.fromEvent(event);
        if (trigger == null) return;

        // Decrement stun timers at the end of each player's turn
        if (trigger == TriggerType.ON_TURN_END) {
            PieceAlignment endingPlayer = getEventPlayer(event);
            ImmutableArray<Entity> all = getEngine().getEntitiesFor(
                Family.all(StunComponent.class, AlignmentComponent.class).get());
            for (int i = 0; i < all.size(); i++) {
                Entity e = all.get(i);
                AlignmentComponent align = e.getComponent(AlignmentComponent.class);
                if (align == null || align.alignment != endingPlayer) continue;
                StunComponent stun = e.getComponent(StunComponent.class);
                stun.decrement();
                if (!stun.isStunned()) e.remove(StunComponent.class);
            }
        }

        String eventPieceId = getEventPieceId(event);
        PieceAlignment eventPlayer = getEventPlayer(event);

        ImmutableArray<Entity> entities = getEngine().getEntitiesFor(family);
        for (int i = 0; i < entities.size(); i++) {
            Entity entity = entities.get(i);
            AbilityInstanceComponent aic = entity.getComponent(AbilityInstanceComponent.class);
            if (aic == null) continue;

            // Ownership check: ON_MOVE/ON_ATTACK/ON_SUMMON/ON_DEATH/ON_KILL fire only for the entity involved
            if (isOwnerTrigger(trigger)) {
                IdentityComponent id = entity.getComponent(IdentityComponent.class);
                if (id == null || !id.id.equals(eventPieceId)) continue;
            }
            // Turn triggers fire only for entities matching the turn player
            if (trigger == TriggerType.ON_TURN_START || trigger == TriggerType.ON_TURN_END) {
                AlignmentComponent align = entity.getComponent(AlignmentComponent.class);
                if (align == null || align.alignment != eventPlayer) continue;
            }

            for (AbilityDefinition def : aic.definitions) {
                if (def.reactions() == null) continue;
                for (Reaction reaction : def.reactions()) {
                    if (reaction.trigger() != trigger) continue;

                    ExpressionContext context = buildContext(entity, def, aic, event);

                    if (reaction.conditions() != null) {
                        boolean conditionsMet = true;
                        for (Condition cond : reaction.conditions()) {
                            if (!ConditionEvaluator.evaluate(cond, context)) {
                                conditionsMet = false;
                                break;
                            }
                        }
                        if (!conditionsMet) continue;
                    }

                    if (reaction.effects() != null) {
                        Map<String, Object> abilityState = aic.state.getOrDefault(def.id(), new HashMap<>());
                        for (EffectNode effect : reaction.effects()) {
                            List<Entity> targets = resolveEffectTargets(effect, entity, context);
                            EffectExecutor.execute(effect, targets, entity, context, abilityState);
                        }
                    }
                }
            }
        }
    }

    private ExpressionContext buildContext(Entity entity, AbilityDefinition def, AbilityInstanceComponent aic, GameEvent event) {
        ExpressionContext context = new ExpressionContext();

        StatsComponent stats = entity.getComponent(StatsComponent.class);
        if (stats != null) {
            context.set("$self.health", stats.currentHealth);
            context.set("$self.maxHealth", stats.maxHealth);
            context.set("$self.damage", stats.damage);
            context.set("$self.speed", stats.speed);
            context.set("$self.actions", stats.actions);
            context.set("$self.cost", stats.cost);
            context.set("$self.remainingActions", stats.remainingActions);
        }
        PositionComponent pos = entity.getComponent(PositionComponent.class);
        if (pos != null) {
            context.set("$self.row", pos.row);
            context.set("$self.col", pos.col);
        }
        AlignmentComponent align = entity.getComponent(AlignmentComponent.class);
        if (align != null) {
            context.set("$self.alignment", align.alignment.name());
        }

        Map<String, Object> state = aic.state.get(def.id());
        if (state != null) {
            for (Map.Entry<String, Object> entry : state.entrySet()) {
                context.set("$state." + entry.getKey(), entry.getValue());
            }
        }

        TriggerMatcher.populateEventContext(event, context);

        // Populate entity references for effect target resolution
        if (event instanceof PieceAttackedEvent attacked) {
            Entity defenderEntity = findEntityById(attacked.defenderId());
            if (defenderEntity != null) context.set("$event.defenderEntity", defenderEntity);
        }

        return context;
    }

    private Entity findEntityById(String id) {
        if (id == null || id.isEmpty()) return null;
        ImmutableArray<Entity> all = getEngine().getEntitiesFor(Family.all(IdentityComponent.class).get());
        for (int i = 0; i < all.size(); i++) {
            Entity e = all.get(i);
            IdentityComponent ic = e.getComponent(IdentityComponent.class);
            if (id.equals(ic.id)) return e;
        }
        return null;
    }

    private List<Entity> resolveEffectTargets(EffectNode effect, Entity owner, ExpressionContext context) {
        Object targetParam = effect.params().get("target");
        if (targetParam instanceof String s) {
            if (s.equals("$self")) return List.of(owner);
            if (s.equals("$event.defender")) {
                Object ref = context.get("$event.defenderEntity");
                return (ref instanceof Entity e) ? List.of(e) : List.of();
            }
            return TargetSelectorResolver.resolve(new TargetSelector(s), owner, context);
        }
        return List.of();
    }

    @Override
    public void update(float deltaTime) {
        if (!eventQueue.isEmpty()) processQueue();
    }

    private boolean isOwnerTrigger(TriggerType trigger) {
        return trigger == TriggerType.ON_MOVE || trigger == TriggerType.ON_ATTACK
                || trigger == TriggerType.ON_SUMMON || trigger == TriggerType.ON_DEATH
                || trigger == TriggerType.ON_KILL;
    }

    private String getEventPieceId(GameEvent event) {
        if (event instanceof PieceMovedEvent e) return e.pieceId();
        if (event instanceof PieceAttackedEvent e) return e.attackerId();
        if (event instanceof PieceSpawnedEvent e) return e.pieceId();
        if (event instanceof PieceDiedEvent e) return e.pieceId();
        if (event instanceof PieceKilledEvent e) return e.killerId();
        return "";
    }

    private PieceAlignment getEventPlayer(GameEvent event) {
        if (event instanceof TurnStartedEvent e) return e.player();
        if (event instanceof TurnEndedEvent e) return e.player();
        return null;
    }
}
