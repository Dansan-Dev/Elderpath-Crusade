package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.Reaction;
import io.github.elderpath_crusade.data.AbilityDataParsing;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceSpawnedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage: nested effects inside a ForEach's "do" list (and equally
 * Branch's then/else, Sequence's steps, Recast's effects) are only converted from raw
 * YAML maps to EffectNode at execution time (EffectExecutor.toEffectNodes), not at load
 * time — unlike top-level effects, which AbilityDataParsing.parseEffects flattens a
 * nested "params" key for. Without the same flattening at execution time, a nested
 * effect written as {type: "Damage", params: {amount: 1}} (exactly how OnSummonShock,
 * CleaveAttack, and BombAction are authored in abilities.yaml) would read a null
 * "amount" and silently no-op.
 */
class EffectExecutorNestedParamsTest {

    @Test
    void forEachDo_withNestedParamsWrapper_stillAppliesDamage() {
        TypedEventBus.get().clear();
        GameContext.create();
        Engine engine = GameContext.get().getEcsEngine();
        GridIndexSystem gridIndex = engine.getSystem(GridIndexSystem.class);

        String yamlText = """
            reactions:
              - trigger: ON_SUMMON
                effects:
                  - {type: "ForEach", params: {targets: "AdjacentEnemies", do: [{type: "Damage", params: {target: "$target", amount: 1}}]}}
            """;
        Map<String, Object> map = new Yaml().load(yamlText);
        List<Reaction> reactions = AbilityDataParsing.parseReactions(map.get("reactions"));
        AbilityDefinition def = new AbilityDefinition("OnSummonShockLike", "test", null, reactions, null, null);

        Entity shockling = engine.createEntity();
        shockling.add(new IdentityComponent().set("shockling-1", "Shockling"));
        shockling.add(new AlignmentComponent().set(PieceAlignment.P1));
        shockling.add(new PositionComponent().set(0, 0));
        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        aic.addAbility(def);
        shockling.add(aic);
        engine.addEntity(shockling);
        gridIndex.onEntitySpawned(shockling, 0, 0);

        Entity enemy = engine.createEntity();
        enemy.add(new IdentityComponent().set("enemy-1", "Wolf"));
        enemy.add(new AlignmentComponent().set(PieceAlignment.P2));
        enemy.add(new PositionComponent().set(0, 1));
        enemy.add(new StatsComponent().set(1, 3, 1, 1, 1));
        engine.addEntity(enemy);
        gridIndex.onEntitySpawned(enemy, 0, 1);

        TypedEventBus.get().emit(new PieceSpawnedEvent("shockling-1", PieceAlignment.P1, 0, 0));

        assertEquals(2, enemy.getComponent(StatsComponent.class).currentHealth,
            "adjacent enemy should have taken 1 damage from the ForEach+do Damage effect");
    }
}
