package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.ecs.systems.CombatSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class CombatResolutionTest {

    private Engine engine;
    private Entity attacker;
    private Entity defender;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();

        // Attacker: 3 damage, 5 hp
        attacker = engine.createEntity();
        attacker.add(new IdentityComponent().set("attacker-id", "Attacker"));
        attacker.add(new AlignmentComponent().set(PieceAlignment.P1));
        attacker.add(new StatsComponent().set(1, 5, 3, 2, 1));
        attacker.add(new PositionComponent().set(0, 0));
        attacker.add(new ModifierComponent());
        engine.addEntity(attacker);

        // Defender: 2 damage, 4 hp
        defender = engine.createEntity();
        defender.add(new IdentityComponent().set("defender-id", "Defender"));
        defender.add(new AlignmentComponent().set(PieceAlignment.P2));
        defender.add(new StatsComponent().set(1, 4, 2, 2, 1));
        defender.add(new PositionComponent().set(1, 0));
        defender.add(new ModifierComponent());
        engine.addEntity(defender);
    }

    @Test
    void attackDealsDamageToDefender() {
        CombatSystem combat = engine.getSystem(CombatSystem.class);
        assertNotNull(combat);

        boolean died = combat.resolveAttack(attacker, defender, 3);

        assertFalse(died);
        assertEquals(1, defender.getComponent(StatsComponent.class).currentHealth); // 4 - 3 = 1
    }

    @Test
    void attackKillsDefenderWhenHealthReachesZero() {
        defender.getComponent(StatsComponent.class).currentHealth = 3; // now 3 hp

        CombatSystem combat = engine.getSystem(CombatSystem.class);
        boolean died = combat.resolveAttack(attacker, defender, 3);

        assertTrue(died);
        assertEquals(0, defender.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void applyDamageReturnsTrueWhenTargetDies() {
        CombatSystem combat = engine.getSystem(CombatSystem.class);
        boolean died = combat.applyDamage(defender, 10);

        assertTrue(died);
        assertTrue(defender.getComponent(StatsComponent.class).currentHealth <= 0);
    }

    @Test
    void applyDamageReturnsFalseWhenTargetSurvives() {
        CombatSystem combat = engine.getSystem(CombatSystem.class);
        boolean died = combat.applyDamage(defender, 1);

        assertFalse(died);
        assertEquals(3, defender.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void actionSpentEvent_emitsCorrectly() {
        StatsComponent stats = attacker.getComponent(StatsComponent.class);
        stats.remainingActions = 2;

        List<ActionSpentEvent> captured = new ArrayList<>();
        Consumer<ActionSpentEvent> listener = captured::add;
        TypedEventBus.get().register(ActionSpentEvent.class, listener);

        try {
            int left = Math.max(0, stats.remainingActions - 1);
            stats.remainingActions = left;
            TypedEventBus.get().emit(new ActionSpentEvent("attacker-id", PieceAlignment.P1, left));

            assertEquals(1, captured.size());
            assertEquals(1, captured.get(0).remaining());
        } finally {
            TypedEventBus.get().unregister(ActionSpentEvent.class, listener);
        }
    }
}
