package io.github.forest_of_dreams.game_objects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

import java.util.Arrays;

public class Board extends HigherOrderTexture {
    private final int ROWS = 7;
    private final int COLS = 5;
    private final int PLOT_WIDTH;
    private final int PLOT_HEIGHT;
    private Renderable[][] board;

    public Board(int x, int y, int plot_width, int plot_height) {
        PLOT_WIDTH = plot_width;
        PLOT_HEIGHT = plot_height;
        setBounds(new Box(x, y, PLOT_WIDTH*COLS, PLOT_HEIGHT*ROWS));
        board = new Renderable[ROWS][COLS];
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = EmptyTexture.get(PLOT_WIDTH*col, PLOT_HEIGHT*row, 60, 60);
                board[row][col] = renderable;

                getRenderables().add(renderable);
            }
        }
    }

    public Renderable getPos(int row, int col) {
        return board[row][col];
    }

    public void removePos(int row, int col) {
        Renderable renderable = board[row][col];
        getRenderables().remove(renderable);
        board[row][col] = EmptyTexture.get(PLOT_WIDTH*col, PLOT_HEIGHT*row, PLOT_WIDTH, PLOT_HEIGHT);
    }

    public void replacePos(int row, int col, Renderable newRenderable) {
        if (newRenderable.getBounds().getWidth() != PLOT_WIDTH
            || newRenderable.getBounds().getHeight() != PLOT_HEIGHT) throw new IllegalArgumentException("Renderable must be in PLOT size");

        Renderable renderable = board[row][col];
        getRenderables().remove(renderable);

        newRenderable.setParent(getBounds());
        board[row][col] = newRenderable;
        getRenderables().add(newRenderable);
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, col*PLOT_WIDTH, row*PLOT_HEIGHT);
            }
        }
    }

    @Override
    public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = board[row][col];
                renderable.render(batch, zLevel, isPaused, x + col*(PLOT_WIDTH), y + row*(PLOT_HEIGHT));
            }
        }
    }
}
