package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the parts of the spell effect primitives (DrawCard,
 * SummonPiece, DiscardCard, GenerateMana — added for spells.yaml's "Scavenge" chain)
 * that don't require constructing a Card/Deck/Hand: those classes are rendering-coupled
 * (extend LibGDX texture/sprite base classes) and fail even under Mockito's inline mock
 * maker — "Could not initialize class UnitCard" / "Card ... could not be initialized" —
 * confirming they're squarely in the "requires GL context, verify via ./gradlew
 * :lwjgl3:run" category per this project's testing standards, not headless-unit-testable.
 * DrawCard/SummonPiece-from-a-drawn-card/DiscardCard are verified manually instead.
 */
class EffectExecutorSpellPrimitivesTest {

    private PlayerManager.PlayerState playerState;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        playerState = GameContext.get().getPlayerManager().get(PieceAlignment.P1);
    }

    private ExpressionContext spellContext() {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$caster.alignment", PieceAlignment.P1.name());
        return ctx;
    }

    @Test
    void generateMana_addsToCasterMana() {
        playerState.setMana(2);
        ExpressionContext ctx = spellContext();

        EffectExecutor.execute(new EffectNode("GenerateMana", Map.of("amount", 3)),
                List.of(), null, ctx, new HashMap<>());

        assertEquals(5, playerState.getMana());
    }

    @Test
    void generateMana_zeroOrNegativeAmount_isNoOp() {
        playerState.setMana(2);
        ExpressionContext ctx = spellContext();

        EffectExecutor.execute(new EffectNode("GenerateMana", Map.of("amount", 0)),
                List.of(), null, ctx, new HashMap<>());

        assertEquals(2, playerState.getMana());
    }

    @Test
    void summonPiece_noEmptySlotInOwnZone_safelyNoOps() {
        Board board = mock(Board.class);
        when(board.getROWS()).thenReturn(2);
        when(board.getCOLS()).thenReturn(2);
        when(board.isValidSummonTarget(any(), any())).thenReturn(false);
        GameContext.get().setActiveBoard(board);

        ExpressionContext ctx = spellContext();
        assertDoesNotThrow(() -> EffectExecutor.execute(
                new EffectNode("SummonPiece", Map.of("piece", "Wolf")),
                List.of(), null, ctx, new HashMap<>()));

        verify(board, never()).addEntityToPos(anyInt(), anyInt(), any(Entity.class), anyString());
    }

    @Test
    void summonPiece_noCasterAlignment_safelyNoOps() {
        ExpressionContext ctx = new ExpressionContext(); // no "$caster.alignment" set
        assertDoesNotThrow(() -> EffectExecutor.execute(
                new EffectNode("SummonPiece", Map.of("piece", "Wolf")),
                List.of(), null, ctx, new HashMap<>()));
    }
}
