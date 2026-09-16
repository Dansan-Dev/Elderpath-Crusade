package io.github.elderpath_crusade.abilities.data;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage for the two selectors added for no-target/AoE spells: "AllUnits"
 * (Blizzard's "stun every piece") and "RandomUnitInRange" (Chain Lightning's "recast on
 * a random piece within 1 tile").
 */
class TargetSelectorResolverSpellSelectorsTest {

    private Engine engine;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
    }

    /** Builds an entity with a healthy StatsComponent — EntityUtils.isDead treats a missing one as dead. */
    private Entity build(PieceAlignment alignment, int row, int col) {
        Entity e = engine.createEntity();
        e.add(new AlignmentComponent().set(alignment));
        e.add(new PositionComponent().set(row, col));
        e.add(new StatsComponent().set(1, 3, 1, 1, 1));
        engine.addEntity(e);
        return e;
    }

    @Test
    void allUnits_returnsBothAlignments_excludingOwner() {
        Entity owner = build(PieceAlignment.P1, 0, 0);
        Entity friendly = build(PieceAlignment.P1, 1, 1);
        Entity enemy = build(PieceAlignment.P2, 2, 2);

        List<Entity> result = TargetSelectorResolver.resolve(new TargetSelector("AllUnits"), owner, new ExpressionContext());

        assertEquals(Set.of(friendly, enemy), Set.copyOf(result));
    }

    @Test
    void allUnits_withNullOwner_includesEveryone() {
        Entity a = build(PieceAlignment.P1, 0, 0);
        Entity b = build(PieceAlignment.P2, 1, 1);

        List<Entity> result = TargetSelectorResolver.resolve(new TargetSelector("AllUnits"), null, new ExpressionContext());

        assertEquals(Set.of(a, b), Set.copyOf(result));
    }

    @Test
    void randomUnitInRange_excludesCenterTileAndOutOfRange_picksAmongTheRest() {
        build(PieceAlignment.P2, 5, 5); // the "dead" center tile — excluded regardless
        Entity near1 = build(PieceAlignment.P2, 5, 6);
        Entity near2 = build(PieceAlignment.P1, 6, 5);
        build(PieceAlignment.P2, 8, 8); // out of range

        TargetSelector selector = new TargetSelector("RandomUnitInRange", Map.of("row", 5, "col", 5, "range", 1));
        List<Entity> result = TargetSelectorResolver.resolve(selector, null, new ExpressionContext());

        assertEquals(1, result.size());
        assertTrue(Set.of(near1, near2).contains(result.get(0)));
    }

    @Test
    void randomUnitInRange_excludesDeadEntities() {
        Entity dead = build(PieceAlignment.P2, 0, 1);
        dead.getComponent(StatsComponent.class).currentHealth = 0;
        Entity alive = build(PieceAlignment.P2, 0, 2);

        TargetSelector selector = new TargetSelector("RandomUnitInRange", Map.of("row", 0, "col", 0, "range", 2));
        List<Entity> result = TargetSelectorResolver.resolve(selector, null, new ExpressionContext());

        assertEquals(List.of(alive), result);
    }

    @Test
    void randomUnitInRange_noCandidates_returnsEmpty() {
        TargetSelector selector = new TargetSelector("RandomUnitInRange", Map.of("row", 0, "col", 0, "range", 1));
        List<Entity> result = TargetSelectorResolver.resolve(selector, null, new ExpressionContext());
        assertTrue(result.isEmpty());
    }
}
