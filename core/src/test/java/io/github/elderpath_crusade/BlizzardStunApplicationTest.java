package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.Condition;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.StunComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Investigates the "Blizzard stunned everyone for 1 turn except one piece that got 2"
 * report. Blizzard's own ForEach+ApplyStatus chain (mirrored here exactly) turns out to
 * apply a uniform 1-turn Stun to every fresh unit — the divergence must come from a
 * piece that already carried a longer Stun (e.g. from StunSelfOnAttack, whose duration
 * didn't match its own description until the sibling fix in this commit) when Blizzard
 * was cast: ApplyStatus takes the max of the existing and new duration, by design,
 * rather than shortening a stronger effect already in place.
 */
class BlizzardStunApplicationTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    private Entity buildUnit(PieceAlignment alignment, int col) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        e.add(new PositionComponent().set(0, col));
        e.add(new StatsComponent().set(1, 3, 1, 1, 1));
        engine.addEntity(e);
        return e;
    }

    private EffectNode blizzardForEach() {
        return new EffectNode("ForEach", Map.of("targets", "AllUnits", "do", List.of(
                new EffectNode("Branch", Map.of(
                        "condition", new Condition("IsStunned", Map.of("target", "$target")),
                        "then", List.of(new EffectNode("AttachTimedModifier",
                                Map.of("target", "$target", "stats", Map.of("addDamage", -1), "turns", 1)))
                )),
                new EffectNode("ApplyStatus", Map.of("target", "$target", "status", "Stun", "turns", 1))
        )));
    }

    @Test
    void freshUnits_allGetExactlyOneTurnOfStun() {
        Entity[] units = {
                buildUnit(PieceAlignment.P1, 0), buildUnit(PieceAlignment.P1, 1),
                buildUnit(PieceAlignment.P2, 2), buildUnit(PieceAlignment.P2, 3),
        };

        EffectExecutor.execute(blizzardForEach(), List.of(), null, new ExpressionContext(), new HashMap<>());

        for (Entity unit : units) {
            StunComponent stun = unit.getComponent(StunComponent.class);
            assertNotNull(stun);
            assertEquals(1, stun.turnsRemaining, "Blizzard's own casting logic is uniform for every fresh unit");
        }
    }

    @Test
    void unitAlreadyStunnedLonger_keepsItsLongerDuration_notShortenedTo1() {
        Entity alreadyStunned = buildUnit(PieceAlignment.P2, 0);
        StunComponent existing = new StunComponent();
        existing.turnsRemaining = 2; // e.g. mid-way through a StunSelfOnAttack duration
        alreadyStunned.add(existing);
        Entity fresh = buildUnit(PieceAlignment.P1, 1);

        EffectExecutor.execute(blizzardForEach(), List.of(), null, new ExpressionContext(), new HashMap<>());

        assertEquals(2, alreadyStunned.getComponent(StunComponent.class).turnsRemaining,
                "a stronger pre-existing stun must not be weakened by a later, shorter one — this is the "
                        + "'one piece shows 2 turns' behavior, and it's by design, not a bug in Blizzard itself");
        assertEquals(1, fresh.getComponent(StunComponent.class).turnsRemaining);
    }
}
