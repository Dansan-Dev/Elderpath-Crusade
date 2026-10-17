package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Engine;
import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.match.BoardGrid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Regression coverage for the hotseat view flip being view-only: it used to physically
 * mirror every PositionComponent and rebuild GridIndexSystem, so logical coordinates
 * depended on whose turn it was. Now only plot screen positions move.
 */
class BoardPerspectiveManagerTest {

    private static final int ROWS = 3, COLS = 2, PLOT_H = 40;

    private Engine engine;
    private BoardGrid grid;
    private Board board;
    private Renderable[][] layout;
    private BoardPerspectiveManager perspective;

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.create();
        engine = GameContext.get().getEcsEngine();
        grid = new BoardGrid(ROWS, COLS);

        layout = new Renderable[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Renderable plot = mock(Renderable.class);
                when(plot.getBounds()).thenReturn(new Box(c * 40, r * PLOT_H, 40, PLOT_H));
                layout[r][c] = plot;
            }
        }

        board = mock(Board.class);
        when(board.getROWS()).thenReturn(ROWS);
        when(board.getCOLS()).thenReturn(COLS);
        when(board.getPLOT_HEIGHT()).thenReturn(PLOT_H);
        when(board.getLayout()).thenReturn(layout);
        perspective = new BoardPerspectiveManager(board);
        when(board.toDisplayRow(anyInt())).thenAnswer(inv -> {
            int row = inv.getArgument(0);
            return perspective.isFlipped() ? ROWS - 1 - row : row;
        });
    }

    @Test
    void flip_leavesLogicalPositionsAndOccupancyUntouched() {
        Entity piece = engine.createEntity();
        piece.add(new PositionComponent().set(0, 1));
        piece.add(new SpriteComponent());
        engine.addEntity(piece);
        grid.addEntity(0, 1, piece);

        perspective.flipRows();

        assertTrue(perspective.isFlipped());
        assertEquals(0, piece.getComponent(PositionComponent.class).row, "logical row unchanged");
        assertSame(piece, grid.getEntityAt(0, 1), "grid occupancy unchanged");
        assertNull(grid.getEntityAt(ROWS - 1, 1));
    }

    @Test
    void flip_mirrorsPlotScreenRows_andFlipBackRestoresThem() {
        perspective.flipRows();
        assertEquals((ROWS - 1) * PLOT_H, layout[0][0].getBounds().getY(), "row 0 drawn at the top");
        assertEquals(0, layout[ROWS - 1][1].getBounds().getY(), "last row drawn at the bottom");
        assertSame(layout[0][0], board.getLayout()[0][0], "layout stays indexed by logical row");

        perspective.flipRows();
        assertFalse(perspective.isFlipped());
        assertEquals(0, layout[0][0].getBounds().getY());
        assertEquals((ROWS - 1) * PLOT_H, layout[ROWS - 1][1].getBounds().getY());
    }
}
