package io.github.forest_of_dreams.rooms;

import io.github.forest_of_dreams.characters.pieces.monster.WarpMage;
import io.github.forest_of_dreams.characters.pieces.monster.Wolf;
import io.github.forest_of_dreams.characters.pieces.tiles.MountainTile;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.Board;
import io.github.forest_of_dreams.game_objects.Plot;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.ui_objects.PauseMenuHint;

public class DemoRoom extends Room {
    private Board board;

    private DemoRoom() {
        super();

        int plot_width = 40;
        int plot_height = 40;

        int[] board_size = new int[]{plot_width*5, plot_height*7};
        board = new Board(0, 0, plot_width, plot_height);
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
        board.setGamePiecePos(4, 3, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.setGamePiecePos(2, 1, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.setGamePiecePos(5, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.setGamePiecePos(1, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        addContent(board);
        addUI(new PauseMenuHint());

        layoutBoard(board_size[0], board_size[1]);
    }

    private void layoutBoard(int boardPixelWidth, int boardPixelHeight) {
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int newX = screen_center[0] - boardPixelWidth / 2;
        int newY = screen_center[1] - boardPixelHeight / 2;
        board.getBounds().setX(newX);
        board.getBounds().setY(newY);
    }

    @Override
    public void onScreenResize() {
        int plotWidth = board.getPLOT_WIDTH();
        int plotHeight = board.getPLOT_HEIGHT();
        int boardPixelWidth = plotWidth * 5;
        int boardPixelHeight = plotHeight * 7;
        layoutBoard(boardPixelWidth, boardPixelHeight);
    }

    public static DemoRoom get() {
        return new DemoRoom();
    }
}
