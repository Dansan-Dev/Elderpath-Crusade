package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.match.MoveRules;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;

import java.util.HashMap;

/**
 * Orchestrates the resolution of board interactions (plot clicks) into
 * movement or attack actions via ECS systems.
 */
public class BoardInteractionResolver {
    private final Board board;

    public BoardInteractionResolver(Board board) {
        this.board = board;
    }

    public void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        if (entities.get(0) instanceof Plot src && entities.get(1) instanceof Plot dst) {
            int srcRow = src.getRow(), srcCol = src.getCol(), dstRow = dst.getRow(), dstCol = dst.getCol();
            ActionDispatcher.dispatch(
                    () -> new NetworkCommand.MovePlot(srcRow, srcCol, dstRow, dstCol),
                    () -> movePlot(srcRow, srcCol, dstRow, dstCol)
            );
        }
    }

    /** Authoritative move/attack by coordinates — see MoveRules. */
    public boolean movePlot(int srcRow, int srcCol, int dstRow, int dstCol) {
        return MoveRules.moveOrAttack(srcRow, srcCol, dstRow, dstCol);
    }
}
