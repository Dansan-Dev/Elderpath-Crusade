package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.ModifierDef;
import io.github.elderpath_crusade.abilities.data.TargetSelector;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.ModifierComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.ecs.systems.PassiveModifierSystem;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for ABS-KING: KingFriendlyAura (AllFriendlyUnits, +1 max health) and
 * KingEnemyAura (AdjacentEnemies, +1 action) should bump the target's current stat once,
 * immediately, when the modifier is first applied — and the action bump must not be
 * farmable by repeatedly leaving and re-entering aura range within the same turn.
 */
class PassiveModifierSystemAuraTest {

    private Engine engine;
    private PassiveModifierSystem system;
    private GridIndexSystem gridIndex;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        system = engine.getSystem(PassiveModifierSystem.class);
        gridIndex = engine.getSystem(GridIndexSystem.class);
    }

    private Entity buildKing(TargetSelector targetSelector, String statKey, int amount) {
        Entity king = engine.createEntity();
        king.add(new AlignmentComponent().set(PieceAlignment.P1));
        king.add(new PositionComponent().set(0, 0));
        king.add(new ModifierComponent());
        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        aic.addAbility(new AbilityDefinition("King", "desc", null, null, null,
                List.of(new ModifierDef(targetSelector, Map.of(statKey, amount)))));
        king.add(aic);
        engine.addEntity(king);
        gridIndex.onEntitySpawned(king, 0, 0);
        return king;
    }

    private Entity buildPiece(PieceAlignment alignment, int row, int col) {
        Entity piece = engine.createEntity();
        piece.add(new AlignmentComponent().set(alignment));
        piece.add(new PositionComponent().set(row, col));
        piece.add(new ModifierComponent());
        piece.add(new StatsComponent().set(1, 3, 1, 1, 1));
        engine.addEntity(piece);
        gridIndex.onEntitySpawned(piece, row, col);
        return piece;
    }

    @Test
    void kingFriendlyAura_bumpsCurrentHealthOnceWhenApplied() {
        buildKing(new TargetSelector("AllFriendlyUnits"), "addMaxHealth", 1);
        Entity friendly = buildPiece(PieceAlignment.P1, 5, 5); // far away — AllFriendlyUnits ignores position

        system.update(0f);
        assertEquals(4, friendly.getComponent(StatsComponent.class).currentHealth);

        system.update(0f); // steady state — must not bump again
        assertEquals(4, friendly.getComponent(StatsComponent.class).currentHealth);
    }

    @Test
    void kingEnemyAura_bumpsActionsOnceAndGuardsAgainstSameTurnReentry() {
        Entity king = buildKing(new TargetSelector("AdjacentEnemies"), "addActions", 1);
        Entity enemy = buildPiece(PieceAlignment.P2, 0, 1); // adjacent to king at (0,0)

        system.update(0f);
        assertEquals(1, enemy.getComponent(StatsComponent.class).remainingActions);

        // Leave adjacency, come back within the same turn — must not double-bump
        movePiece(enemy, 0, 1, 5, 5);
        system.update(0f);
        movePiece(enemy, 5, 5, 0, 1);
        system.update(0f);
        assertEquals(1, enemy.getComponent(StatsComponent.class).remainingActions);

        // New turn clears the guard — leaving and re-entering again should bump once more
        TypedEventBus.get().emit(new TurnStartedEvent(PieceAlignment.P2));
        movePiece(enemy, 0, 1, 5, 5);
        system.update(0f);
        movePiece(enemy, 5, 5, 0, 1);
        system.update(0f);
        assertEquals(2, enemy.getComponent(StatsComponent.class).remainingActions);
    }

    private void movePiece(Entity piece, int fromRow, int fromCol, int toRow, int toCol) {
        gridIndex.onEntityMoved(fromRow, fromCol, piece, toRow, toCol);
        piece.getComponent(PositionComponent.class).set(toRow, toCol);
    }
}
