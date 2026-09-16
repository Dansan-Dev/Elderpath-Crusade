package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.ecs.systems.ModifierResolutionSystem;
import io.github.elderpath_crusade.ecs.systems.PassiveModifierSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnEndedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the "attach a timed effect to a piece that detaches itself"
 * mechanism (AttachTimedModifier + RemoveSelfAbility + PassiveModifierSystem's stale-key
 * cleanup) used by Blizzard's "-1 attack for 1 turn" and reusable by any future N-turn
 * spell/ability effect. Reuses the existing passive-modifier machinery rather than a
 * bespoke component: the modifier is a normal Self-targeted ability attached at runtime.
 */
class AttachTimedModifierTest {

    private Engine engine;
    private Entity target;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();

        target = engine.createEntity();
        target.add(new AlignmentComponent().set(PieceAlignment.P1));
        target.add(new PositionComponent().set(0, 0));
        target.add(new StatsComponent().set(1, 3, 2, 1, 1)); // damage 2
        target.add(new ModifierComponent());
        target.add(new ComputedStatsComponent());
        engine.addEntity(target);
    }

    private void recomputeStats() {
        engine.getSystem(PassiveModifierSystem.class).update(0f);
        engine.getSystem(ModifierResolutionSystem.class).update(0f);
    }

    @Test
    void attachedModifier_appliesImmediately_andDetachesAfterItsTurnsExpire() {
        EffectExecutor.execute(
                new EffectNode("AttachTimedModifier", Map.of("target", "$target", "stats", Map.of("addDamage", -1), "turns", 1)),
                List.of(target), null, new ExpressionContext(), new HashMap<>());

        recomputeStats();
        assertEquals(1, EntityUtils.getDamage(target), "damage should be reduced by 1 while the timed modifier is attached");
        assertNotNull(target.getComponent(AbilityInstanceComponent.class));
        assertEquals(1, target.getComponent(AbilityInstanceComponent.class).definitions.size());

        // End P1's turn once — decrements turnsRemaining to 0 and self-removes.
        TypedEventBus.get().emit(new TurnEndedEvent(PieceAlignment.P1));

        assertEquals(0, target.getComponent(AbilityInstanceComponent.class).definitions.size(),
                "the timed ability should have detached itself once its duration ran out");

        recomputeStats();
        assertEquals(2, EntityUtils.getDamage(target), "damage should be back to base once the timed modifier is gone");
    }

    @Test
    void attachedModifier_survivesTurnsGreaterThanOne() {
        EffectExecutor.execute(
                new EffectNode("AttachTimedModifier", Map.of("target", "$target", "stats", Map.of("addDamage", -1), "turns", 2)),
                List.of(target), null, new ExpressionContext(), new HashMap<>());

        TypedEventBus.get().emit(new TurnEndedEvent(PieceAlignment.P1));
        assertEquals(1, target.getComponent(AbilityInstanceComponent.class).definitions.size(),
                "one turn end should only decrement, not remove, a 2-turn modifier");

        TypedEventBus.get().emit(new TurnEndedEvent(PieceAlignment.P1));
        assertEquals(0, target.getComponent(AbilityInstanceComponent.class).definitions.size(),
                "the second turn end should remove it");
    }

    @Test
    void noTurnsParam_isPermanentLikeAddModifier() {
        EffectExecutor.execute(
                new EffectNode("AttachTimedModifier", Map.of("target", "$target", "stats", Map.of("addDamage", -1))),
                List.of(target), null, new ExpressionContext(), new HashMap<>());

        recomputeStats();
        assertEquals(1, EntityUtils.getDamage(target));
        assertNull(target.getComponent(AbilityInstanceComponent.class),
                "permanent path reuses AddModifier directly — no ability/component attached");

        TypedEventBus.get().emit(new TurnEndedEvent(PieceAlignment.P1));
        recomputeStats();
        assertEquals(1, EntityUtils.getDamage(target), "no turn-end reaction exists, so the permanent modifier never expires");
    }
}
