package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.game_objects.Board;
import io.github.forest_of_dreams.game_objects.Plot;
import io.github.forest_of_dreams.game_objects.Room;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.ui_objects.PauseMenuHint;

import java.util.List;

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

        getContents().add(board);
        getUi().add(new PauseMenuHint());

//        GraphicsManager.addRenderable(board);
//        GraphicsManager.addUIRenderable(new PauseMenuHint());
    }

    public static StartRoom get() {
        return new StartRoom();
    }
}
