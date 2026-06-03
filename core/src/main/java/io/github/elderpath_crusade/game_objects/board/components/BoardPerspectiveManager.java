package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.SpriteComponent;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;
import lombok.Getter;

/**
 * Handles the physical perspective of the board, including row flipping for
 * LOCAL_MATCH mode.
 */
public class BoardPerspectiveManager {
    private final Board board;
    @Getter
    private boolean physicallyFlipped = false;

    public BoardPerspectiveManager(Board board) {
        this.board = board;
    }

    /**
     * Physically flip the board by swapping rows in the layout array.
     * Updates plot bounds and rebuilds GridIndexSystem to reflect new positions.
     */
    public void flipRows() {
        int rows = board.getROWS();
        int cols = board.getCOLS();
        int plotWidth = board.getPLOT_WIDTH();
        int plotHeight = board.getPLOT_HEIGHT();
        Renderable[][] layout = board.getLayout();

        // Swap plots in layout array (visual)
        for (int row = 0; row < rows / 2; row++) {
            int swapRow = rows - 1 - row;

            Renderable[] tempRow = layout[row];
            layout[row] = layout[swapRow];
            layout[swapRow] = tempRow;

            for (int col = 0; col < cols; col++) {
                Renderable plot = layout[row][col];
                if (plot != null && plot.getBounds() != null) {
                    plot.getBounds().setX(col * plotWidth);
                    plot.getBounds().setY(row * plotHeight);
                    if (plot instanceof Plot p) p.setGridPos(row, col);
                }
                Renderable swapPlot = layout[swapRow][col];
                if (swapPlot != null && swapPlot.getBounds() != null) {
                    swapPlot.getBounds().setX(col * plotWidth);
                    swapPlot.getBounds().setY(swapRow * plotHeight);
                    if (swapPlot instanceof Plot p) p.setGridPos(swapRow, col);
                }
            }
        }

        // Flip all entity positions via ECS and rebuild GridIndexSystem
        GridIndexSystem gridIndex = GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class);
        ImmutableArray<Entity> entities = GameContext.get().getEcsEngine()
                .getEntitiesFor(Family.all(SpriteComponent.class, PositionComponent.class).get());
        if (gridIndex != null) gridIndex.clear();
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            PositionComponent pos = e.getComponent(PositionComponent.class);
            pos.row = rows - 1 - pos.row;
            if (gridIndex != null) gridIndex.onEntitySpawned(e, pos.row, pos.col);
        }

        physicallyFlipped = !physicallyFlipped;
        board.markDirtyAndNotify();
    }

    public void reset() {
        physicallyFlipped = false;
    }
}
