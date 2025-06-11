package io.github.forest_of_dreams.game_objects;

import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.supers.HigherOrderTexture;

public class Board extends HigherOrderTexture {
    private final int ROWS = 7;
    private final int COLS = 5;
    private Renderable[][] board;

    public Board(int x, int y) {
        setBounds(new Box(x, y, 60*COLS, 60*ROWS));
        board = new Renderable[ROWS][COLS];
        for(int row = 0; row < ROWS; row++) {
            for(int col = 0; col < COLS; col++) {
                Renderable renderable = EmptyTexture.get(60*col, 60*row, 60, 60);
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
        board[row][col] = EmptyTexture.get(60*col, 60*row, 60, 60);
    }

    public void replacePos(int row, int col, Renderable newRenderable) {
        if (newRenderable.getBounds().getWidth() != 60
            || newRenderable.getBounds().getHeight() != 60) throw new IllegalArgumentException("Renderable must be 60x60");

        Renderable renderable = board[row][col];
        getRenderables().remove(renderable);

        newRenderable.setParent(getBounds());
        board[row][col] = newRenderable;
        getRenderables().add(newRenderable);
    }
}
