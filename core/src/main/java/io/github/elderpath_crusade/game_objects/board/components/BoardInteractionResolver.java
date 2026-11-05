package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;

import java.util.HashMap;

/**
 * Turns a completed plot-to-plot click into a move/attack command for this screen's acting
 * seat, via ClientController. The match server re-validates and is the sole judge of legality.
 */
public class BoardInteractionResolver {
    private final Board board;

    public BoardInteractionResolver(Board board) {
        this.board = board;
    }

    public void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        if (entities.get(0) instanceof Plot src && entities.get(1) instanceof Plot dst) {
            GameContext.get().getClientController().moveOrAttack(
                    src.getRow(), src.getCol(), dst.getRow(), dst.getCol());
        }
    }
}
