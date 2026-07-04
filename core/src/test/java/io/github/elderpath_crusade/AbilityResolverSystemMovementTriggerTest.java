package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.AbilityDefinition;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.Reaction;
import io.github.elderpath_crusade.abilities.data.TriggerType;
import io.github.elderpath_crusade.ecs.components.AbilityInstanceComponent;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.PieceMovedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for TRIG-001: ON_MOVE reactions (e.g. RogueFreeStrike) must only fire
 * for a piece's own manual move, not when it is displaced by another ability.
 */
class AbilityResolverSystemMovementTriggerTest {

    private Engine engine;
    private Entity piece;
    private String pieceId;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();

        pieceId = UUID.randomUUID().toString();
        piece = engine.createEntity();
        piece.add(new IdentityComponent().set(pieceId, "Rogue"));
        piece.add(new AlignmentComponent().set(PieceAlignment.P1));
        piece.add(new StatsComponent().set(1, 5, 2, 1, 1));
        piece.getComponent(StatsComponent.class).remainingActions = 1;
        piece.add(new PositionComponent().set(2, 2));

        AbilityInstanceComponent aic = new AbilityInstanceComponent();
        aic.addAbility(new AbilityDefinition(
                "RogueFreeStrike", "desc", null,
                List.of(new Reaction(TriggerType.ON_MOVE, null,
                        List.of(new EffectNode("SetActions", Map.of("target", "$self", "amount", 0))))),
                null, null));
        piece.add(aic);

        engine.addEntity(piece);
    }

    @Test
    void onMoveReaction_firesForActiveMovement() {
        TypedEventBus.get().emit(new PieceMovedEvent(pieceId, PieceAlignment.P1, 1, 2, 2, 2,
                PieceMovedEvent.MovementType.ACTIVE, "PLAYER"));

        assertEquals(0, piece.getComponent(StatsComponent.class).remainingActions);
    }

    @Test
    void onMoveReaction_doesNotFireForForcedMovement() {
        TypedEventBus.get().emit(new PieceMovedEvent(pieceId, PieceAlignment.P1, 1, 2, 2, 2,
                PieceMovedEvent.MovementType.FORCED, "ABILITY", "Displace"));

        assertEquals(1, piece.getComponent(StatsComponent.class).remainingActions);
    }
}
