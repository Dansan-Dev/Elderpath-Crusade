package io.github.elderpath_crusade.abilities;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.abilities.data.Cost;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Regression coverage: StormAction ("costs 1 mana") must only be activatable when the
 * caster's player has enough mana, and executing it must deduct mana rather than an
 * action point — mirrors the existing Action-cost path but for the Mana cost type,
 * which ActionableAbilityExecutor did not previously handle at all.
 */
class ActionableAbilityExecutorManaCostTest {

    private Entity piece;
    private PlayerManager.PlayerState playerState;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        Engine engine = GameContext.get().getEcsEngine();

        piece = engine.createEntity();
        piece.add(new AlignmentComponent().set(PieceAlignment.P1));
        piece.add(new StatsComponent().set(3, 1, 1, 1, 1));
        engine.addEntity(piece);

        playerState = GameContext.get().getPlayerManager().get(PieceAlignment.P1);
    }

    @Test
    void canAffordCosts_false_whenNotEnoughMana() {
        playerState.setMana(0);
        assertFalse(ActionableAbilityExecutor.canAffordCosts(piece, List.of(new Cost("Mana", 1))));
    }

    @Test
    void canAffordCosts_true_whenEnoughMana() {
        playerState.setMana(1);
        assertTrue(ActionableAbilityExecutor.canAffordCosts(piece, List.of(new Cost("Mana", 1))));
    }

    @Test
    void deductCosts_subtractsMana_leavesActionsUntouched() {
        playerState.setMana(2);
        int actionsBefore = piece.getComponent(StatsComponent.class).remainingActions;

        ActionableAbilityExecutor.deductCosts(piece, List.of(new Cost("Mana", 1)));

        assertEquals(1, playerState.getMana());
        assertEquals(actionsBefore, piece.getComponent(StatsComponent.class).remainingActions);
    }

    @Test
    void deductCosts_manaNeverGoesNegative() {
        playerState.setMana(0);
        ActionableAbilityExecutor.deductCosts(piece, List.of(new Cost("Mana", 1)));
        assertEquals(0, playerState.getMana());
    }
}
