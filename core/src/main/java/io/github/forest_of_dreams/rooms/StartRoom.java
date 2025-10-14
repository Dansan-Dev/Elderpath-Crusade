package io.github.forest_of_dreams.rooms;

import io.github.forest_of_dreams.characters.pieces.Goblin;
import io.github.forest_of_dreams.characters.pieces.WarpMage;
import io.github.forest_of_dreams.characters.pieces.Wolf;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.Board;
import io.github.forest_of_dreams.game_objects.Plot;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.ui_objects.PauseMenuHint;

public class StartRoom extends Room {
    private StartRoom() {
        super();

        int plot_width = 40;
        int plot_height = 40;


        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int[] board_size = new int[]{plot_width*5, plot_height*7};
        Board board = new Board(screen_center[0] - board_size[0]/2, screen_center[1] - board_size[1]/2, plot_width, plot_height);
        for(int row = 0; row < 7; row++) {
            for(int col = 0; col < 5; col++) {
                board.replacePos(row, col, new Plot(0, 0, plot_width, plot_height));
            }
        }
//        board.setGamePiecePos(2, 0, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
//        board.setGamePiecePos(3, 1, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
//        board.setGamePiecePos(1, 2, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
//        board.setGamePiecePos(5, 3, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.NEUTRAL));
//        board.setGamePiecePos(4, 4, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.NEUTRAL));
        board.setGamePiecePos(2, 0, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
        board.setGamePiecePos(1, 4, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
        board.setGamePiecePos(3, 2, new WarpMage(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
        addContent(board);
        addUI(new PauseMenuHint());

//        GraphicsManager.addRenderable(board);
//        GraphicsManager.addUIRenderable(new PauseMenuHint());
    }

    public static StartRoom get() {
        return new StartRoom();
    }
}
