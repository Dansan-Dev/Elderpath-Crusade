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
    private final Board board;
    private final int plot_width = 40;
    private final int plot_height = 40;

    private DemoRoom() {
        super();

        board = new Board(0, 0, plot_width, plot_height, 7, 5);
        board.initializePlots();

//        board.setGamePiecePos(2, 0, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
//        board.setGamePiecePos(3, 1, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
//        board.setGamePiecePos(1, 2, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
//        board.setGamePiecePos(5, 3, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.NEUTRAL));
//        board.setGamePiecePos(4, 4, new Goblin(0, 10, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.NEUTRAL));
        board.addGamePieceToPos(2, 0, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
        board.addGamePieceToPos(1, 4, new Wolf(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.HOSTILE));
        board.addGamePieceToPos(3, 2, new WarpMage(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT(), PieceAlignment.ALLIED));
        board.addGamePieceToPos(4, 3, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(2, 1, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(5, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(1, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));

        addContent(board);
        addUI(new PauseMenuHint());

        int[] board_size = board.getPixelSize();
        layoutBoard(board_size[0], board_size[1]);
    }

    private void layoutBoard(int boardPixelWidth, int boardPixelHeight) {
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int newCenteredX = screen_center[0] - boardPixelWidth / 2;
        int newCenteredY = screen_center[1] - boardPixelHeight / 2;
        board.getBounds().setX(newCenteredX);
        board.getBounds().setY(newCenteredY);
    }

    @Override
    public void onScreenResize() {
        int boardPixelWidth = plot_width * 5;
        int boardPixelHeight = plot_height * 7;
        layoutBoard(boardPixelWidth, boardPixelHeight);
    }

    public static DemoRoom get() {
        return new DemoRoom();
    }
}
