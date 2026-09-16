package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.stats.StatsModifier;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.ecs.systems.AttackSystem;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.ecs.systems.ModifierResolutionSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage: CannotAttack must actually prevent the attack from happening
 * (no damage, no PieceAttackedEvent, no chance for an ON_ATTACK reaction to fire) —
 * not merely zero out the attacker's damage while the attack still "succeeds".
 */
class CannotAttackGatingTest {

    private Engine engine;
    private GridIndexSystem gridIndex;
    private AttackSystem attackSystem;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        gridIndex = engine.getSystem(GridIndexSystem.class);
        attackSystem = engine.getSystem(AttackSystem.class);
    }

    private Entity buildPiece(PieceAlignment alignment, int row, int col, boolean cannotAttack) {
        Entity piece = engine.createEntity();
        piece.add(new IdentityComponent().set(java.util.UUID.randomUUID().toString(), "TestPiece"));
        piece.add(new AlignmentComponent().set(alignment));
        piece.add(new PositionComponent().set(row, col));
        piece.add(new StatsComponent().set(1, 3, 1, 1, 1));
        piece.add(new ModifierComponent());
        piece.add(new ComputedStatsComponent());
        engine.addEntity(piece);
        gridIndex.onEntitySpawned(piece, row, col);

        if (cannotAttack) {
            StatsModifier mod = new StatsModifier();
            mod.cannotAttack = true;
            piece.getComponent(ModifierComponent.class).accumulator.add(mod);
        }
        engine.getSystem(ModifierResolutionSystem.class).update(0f);
        return piece;
    }

    @Test
    void computedStatsComponent_reflectsCannotAttackModifier() {
        Entity piece = buildPiece(PieceAlignment.P1, 0, 0, true);
        assertFalse(EntityUtils.canAttack(piece));

        Entity free = buildPiece(PieceAlignment.P1, 5, 5, false);
        assertTrue(EntityUtils.canAttack(free));
    }

    @Test
    void attackSystem_blocksAttack_noDamageNoEvent() {
        Entity attacker = buildPiece(PieceAlignment.P1, 0, 0, true);
        Entity defender = buildPiece(PieceAlignment.P2, 0, 1, false);

        AtomicBoolean eventFired = new AtomicBoolean(false);
        Consumer<PieceAttackedEvent> listener = e -> eventFired.set(true);
        TypedEventBus.get().register(PieceAttackedEvent.class, listener);

        boolean attacked = attackSystem.executeAttack(attacker, 0, 1);

        assertFalse(attacked, "CannotAttack piece must not be able to execute an attack");
        assertEquals(3, defender.getComponent(StatsComponent.class).currentHealth, "defender must take no damage");
        assertFalse(eventFired.get(), "no PieceAttackedEvent must be emitted, so no ON_ATTACK reaction can fire");

        TypedEventBus.get().unregister(PieceAttackedEvent.class, listener);
    }

    @Test
    void attackSystem_allowsAttack_whenNotBlocked() {
        Entity attacker = buildPiece(PieceAlignment.P1, 0, 0, false);
        Entity defender = buildPiece(PieceAlignment.P2, 0, 1, false);

        boolean attacked = attackSystem.executeAttack(attacker, 0, 1);

        assertTrue(attacked);
        assertEquals(2, defender.getComponent(StatsComponent.class).currentHealth);
    }
}
