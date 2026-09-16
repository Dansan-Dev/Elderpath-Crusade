package io.github.elderpath_crusade;

import com.badlogic.ashley.core.Entity;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import io.github.elderpath_crusade.abilities.data.EffectExecutor;
import io.github.elderpath_crusade.abilities.data.EffectNode;
import io.github.elderpath_crusade.abilities.data.ExpressionContext;
import io.github.elderpath_crusade.data.PieceRegistry;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage: SummonPiece must prompt the player to choose which empty tile
 * in their zone to summon onto — it must NOT silently auto-pick one — and must fizzle
 * (no prompt, no summon) when the zone has no empty tile at all.
 */
@RequiresAssets
class EffectExecutorSummonPieceInteractiveTest {

    @BeforeEach
    void setUp() throws Exception {
        Gdx.app = mock(Application.class);
        Gdx.files = realPiecesYamlFiles();
        PieceRegistry.load();

        TypedEventBus.get().clear();
        GameContext.create();
    }

    private static Files realPiecesYamlFiles() throws Exception {
        String yaml = new String(java.nio.file.Files.readAllBytes(Path.of("../assets/data/pieces.yaml")));
        FileHandle handle = mock(FileHandle.class);
        when(handle.readString()).thenReturn(yaml);
        Files files = mock(Files.class);
        when(files.internal("data/pieces.yaml")).thenReturn(handle);
        return files;
    }

    private ExpressionContext spellContext() {
        ExpressionContext ctx = new ExpressionContext();
        ctx.set("$caster.alignment", PieceAlignment.P1.name());
        return ctx;
    }

    @Test
    void multipleEmptySlots_promptsThePlayer_doesNotAutoPlace() {
        Board board = mock(Board.class);
        when(board.getROWS()).thenReturn(1);
        when(board.getCOLS()).thenReturn(3);
        Plot slotA = mock(Plot.class);
        when(slotA.getIndices()).thenReturn(new int[]{0, 0});
        Plot slotB = mock(Plot.class);
        when(slotB.getIndices()).thenReturn(new int[]{0, 1});
        when(board.getPlotAtPos(0, 0)).thenReturn(slotA);
        when(board.getPlotAtPos(0, 1)).thenReturn(slotB);
        when(board.getPlotAtPos(0, 2)).thenReturn(null);
        when(board.isValidSummonTarget(slotA, PieceAlignment.P1)).thenReturn(true);
        when(board.isValidSummonTarget(slotB, PieceAlignment.P1)).thenReturn(true);
        GameContext.get().setActiveBoard(board);

        EffectExecutor.execute(new EffectNode("SummonPiece", Map.of("piece", "Wolf")),
                List.of(), null, spellContext(), new HashMap<>());

        assertTrue(GameContext.get().getInteractionManager().hasActiveSelection(),
                "must open a tile-picking prompt rather than choosing automatically");
        verify(board, never()).addEntityToPos(anyInt(), anyInt(), any(Entity.class), anyString());
    }

    @Test
    void noEmptySlots_fizzlesSilently_neverPrompts() {
        Board board = mock(Board.class);
        when(board.getROWS()).thenReturn(1);
        when(board.getCOLS()).thenReturn(1);
        when(board.isValidSummonTarget(any(), any())).thenReturn(false);
        GameContext.get().setActiveBoard(board);

        EffectExecutor.execute(new EffectNode("SummonPiece", Map.of("piece", "Wolf")),
                List.of(), null, spellContext(), new HashMap<>());

        assertFalse(GameContext.get().getInteractionManager().hasActiveSelection(),
                "must not prompt at all when the zone has no empty tile");
        verify(board, never()).addEntityToPos(anyInt(), anyInt(), any(Entity.class), anyString());
    }
}
