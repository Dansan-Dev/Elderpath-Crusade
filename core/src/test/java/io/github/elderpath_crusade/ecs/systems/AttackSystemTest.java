package io.github.elderpath_crusade.ecs.systems;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.*;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceAttackedEvent;
import io.github.elderpath_crusade.events.PieceKilledEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AttackSystemTest {

    private Engine engine;
    private AttackSystem attackSystem;
    private GridIndexSystem grid;
    private final List<PieceAttackedEvent> attacked = new ArrayList<>();
    private final List<PieceKilledEvent> killed = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        attacked.clear();
        killed.clear();
        TypedEventBus.get().register(PieceAttackedEvent.class, attacked::add);
        TypedEventBus.get().register(PieceKilledEvent.class, killed::add);
        engine = new Engine();
        grid = new GridIndexSystem();
        attackSystem = new AttackSystem();
        engine.addSystem(grid);
        engine.addSystem(new CombatSystem());
        engine.addSystem(attackSystem);
    }

    private Entity piece(String id, PieceAlignment side, int row, int col, int health, int damage) {
        Entity e = new Entity();
        e.add(new IdentityComponent().set(id, id));
        e.add(new AlignmentComponent().set(side));
        e.add(new PositionComponent().set(row, col));
        e.add(new StatsComponent().set(1, health, damage, 1, 1));
        engine.addEntity(e);
        grid.onEntitySpawned(e, row, col);
        return e;
    }

    @Test
    void intent_damagesDefenderEmitsEventAndIsRemoved() {
        Entity attacker = piece("a", PieceAlignment.P1, 1, 1, 3, 2);
        Entity defender = piece("d", PieceAlignment.P2, 1, 2, 5, 1);
        attacker.add(new AttackIntentComponent().set(1, 2));
        engine.update(0f);

        assertEquals(3, defender.getComponent(StatsComponent.class).currentHealth);
        assertNull(attacker.getComponent(AttackIntentComponent.class));
        assertEquals(1, attacked.size());
        assertEquals("a", attacked.get(0).attackerId());
        assertEquals("d", attacked.get(0).defenderId());
        assertEquals(2, attacked.get(0).damage());
        assertTrue(killed.isEmpty());
    }

    @Test
    void lethalAttack_emitsKilledWithExcessDamage_butLeavesRemovalToDeathSystem() {
        Entity attacker = piece("a", PieceAlignment.P1, 1, 1, 3, 5);
        Entity defender = piece("d", PieceAlignment.P2, 1, 2, 2, 1);
        attacker.add(new AttackIntentComponent().set(1, 2));
        engine.update(0f);

        assertEquals(1, killed.size());
        assertEquals(3, killed.get(0).excessDamage());
        assertTrue(engine.getEntities().contains(defender, true), "AttackSystem never removes a dead piece; DeathSystem owns that");
    }

    @Test
    void attackOnEmptyTile_isDroppedWithoutEvent() {
        Entity attacker = piece("a", PieceAlignment.P1, 1, 1, 3, 2);
        attacker.add(new AttackIntentComponent().set(4, 4));
        engine.update(0f);

        assertNull(attacker.getComponent(AttackIntentComponent.class));
        assertTrue(attacked.isEmpty());
    }

    @Test
    void attackerThatCannotAttack_isDroppedWithoutDamage() {
        Entity attacker = piece("a", PieceAlignment.P1, 1, 1, 3, 2);
        ComputedStatsComponent computed = new ComputedStatsComponent();
        computed.cannotAttack = true;
        attacker.add(computed);
        Entity defender = piece("d", PieceAlignment.P2, 1, 2, 5, 1);
        attacker.add(new AttackIntentComponent().set(1, 2));
        engine.update(0f);

        assertEquals(5, defender.getComponent(StatsComponent.class).currentHealth);
        assertNull(attacker.getComponent(AttackIntentComponent.class));
        assertTrue(attacked.isEmpty());
    }

    @Test
    void executeAttack_resolvesImmediately_andReturnsFalseForEmptyTile() {
        Entity attacker = piece("a", PieceAlignment.P1, 1, 1, 3, 2);
        Entity defender = piece("d", PieceAlignment.P2, 1, 2, 5, 1);

        assertTrue(attackSystem.executeAttack(attacker, 1, 2));
        assertEquals(3, defender.getComponent(StatsComponent.class).currentHealth);
        assertFalse(attackSystem.executeAttack(attacker, 3, 3));
        assertEquals(1, attacked.size());
    }
}
