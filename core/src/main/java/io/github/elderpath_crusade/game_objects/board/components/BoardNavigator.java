package io.github.elderpath_crusade.game_objects.board.components;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.match.Coord;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the logical BoardGrid's navigation results (absolute coordinates) to this Board's
 * Plots, for highlighting and click validation. All navigation logic lives in BoardGrid.
 */
public class BoardNavigator {
    private final Board board;

    public BoardNavigator(Board board) {
        this.board = board;
    }

    public List<Plot> getReachablePlots(Entity mover, int row, int col, int speed) {
        return toPlots(board.getGrid().getReachable(mover, row, col, speed));
    }

    public List<Plot> getAttackableEnemyPlots(int row, int col, PieceAlignment friendlyAlignment) {
        return toPlots(board.getGrid().getAttackableEnemies(row, col, friendlyAlignment));
    }

    public List<Plot> getAdjacentHostilePlots(int row, int col, PieceAlignment friendlyAlignment) {
        return toPlots(board.getGrid().getAdjacentHostiles(row, col, friendlyAlignment));
    }

    private List<Plot> toPlots(List<Coord> coords) {
        List<Plot> out = new ArrayList<>(coords.size());
        for (Coord c : coords) {
            Renderable r = board.getPlotAtPos(c.row(), c.col());
            if (r instanceof Plot p) out.add(p);
        }
        return out;
    }
}
