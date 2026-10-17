package io.github.elderpath_crusade.game_objects.board.components;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.server.ActionDispatcher;

import java.util.HashMap;

/**
 * Turns a completed plot-to-plot click into a move/attack request for GameServer (or the
 * host, as the online guest), acting as this screen's side (LocalControl).
 */
public class BoardInteractionResolver {
    private final Board board;

    public BoardInteractionResolver(Board board) {
        this.board = board;
    }

    public void handlePlotMove(HashMap<Integer, CustomBox> entities) {
        if (entities.get(0) instanceof Plot src && entities.get(1) instanceof Plot dst) {
            int srcRow = src.getRow(), srcCol = src.getCol(), dstRow = dst.getRow(), dstCol = dst.getCol();
            PieceAlignment actor = GameContext.get().getLocalControl().actingSide();
            ActionDispatcher.dispatch(
                    () -> new NetworkCommand.MovePlot(srcRow, srcCol, dstRow, dstCol),
                    () -> GameContext.get().getGameServer().movePlot(actor, srcRow, srcCol, dstRow, dstCol)
            );
        }
    }
}
