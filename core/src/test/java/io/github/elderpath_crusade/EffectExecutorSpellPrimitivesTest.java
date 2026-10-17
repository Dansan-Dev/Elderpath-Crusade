package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game.PlayerManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.data.PieceDefinition;
import io.github.elderpath_crusade.data.PieceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the spell effect primitives (DrawCard, SummonPiece, DiscardCard,
 * GenerateMana — added for spells.yaml's "Scavenge" chain). Hands and decks are pure state
 * (HandState/DeckState), so DrawCard/DiscardCard run headlessly; SummonPiece's tile prompt
 * still needs the renderable board, so only its no-op paths are covered here.
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
        GameContext.get().setActiveBoard(board);
        BoardGrid grid = new BoardGrid(2, 2);
        grid.addEntity(0, 0, GameContext.get().getEcsEngine().createEntity()); // P1's home row (0) full
        grid.addEntity(0, 1, GameContext.get().getEcsEngine().createEntity());
        GameContext.get().setActiveGrid(grid);

        ExpressionContext ctx = spellContext();
        assertDoesNotThrow(() -> EffectExecutor.execute(
                new EffectNode("SummonPiece", Map.of("piece", "Wolf")),
                List.of(), null, ctx, new HashMap<>()));

        verify(board, never()).addEntityToPos(anyInt(), anyInt(), any(Entity.class), anyString());
        assertFalse(GameContext.get().getInteractionManager().hasActiveSelection(), "no tile prompt when the zone is full");
    }

    @Test
    void summonPiece_noCasterAlignment_safelyNoOps() {
        ExpressionContext ctx = new ExpressionContext(); // no "$caster.alignment" set
        assertDoesNotThrow(() -> EffectExecutor.execute(
                new EffectNode("SummonPiece", Map.of("piece", "Wolf")),
                List.of(), null, ctx, new HashMap<>()));
    }

    @Test
    void drawCard_drawsIntoHandAndExposesDrawnCardToContext() {
        PieceRegistry.register(new PieceDefinition("Wolf", 2, 3, 1, 1, 1, List.of()));
        CardCatalog.clear();
        CardCatalog.registerSummon("Wolf", "Wolf");
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P1, List.of("Wolf"), new Random(1));
        ExpressionContext ctx = spellContext();

        EffectExecutor.execute(new EffectNode("DrawCard", Map.of()), List.of(), null, ctx, new HashMap<>());

        CardInstance drawn = playerState.hand.find("P1-0");
        assertNotNull(drawn);
        assertEquals(drawn, ctx.get("$drawn.card"));
        assertEquals(true, ctx.get("$drawn.isPiece"));
        assertEquals(2, ctx.get("$drawn.cost"));
        assertEquals("Wolf", ctx.get("$drawn.registryKey"));
        CardCatalog.clear();
    }

    @Test
    void discardCard_drawnCard_movesItFromHandToDiscard() {
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P1, List.of("Fireball"), new Random(1));
        ExpressionContext ctx = spellContext();
        EffectExecutor.execute(new EffectNode("DrawCard", Map.of()), List.of(), null, ctx, new HashMap<>());

        EffectExecutor.execute(new EffectNode("DiscardCard", Map.of("target", "$drawn")), List.of(), null, ctx, new HashMap<>());

        assertEquals(0, playerState.hand.size());
        assertEquals("P1-0", playerState.deck.getDiscardPile().get(0).instanceId());
    }

    @Test
    void drawCard_emptyDeck_isNoOp() {
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P1, List.of(), new Random(1));
        ExpressionContext ctx = spellContext();

        EffectExecutor.execute(new EffectNode("DrawCard", Map.of()), List.of(), null, ctx, new HashMap<>());

        assertEquals(0, playerState.hand.size());
        assertNull(ctx.get("$drawn.card"));
    }
}
