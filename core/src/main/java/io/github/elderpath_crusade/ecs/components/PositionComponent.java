package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Grid position on the board.
 */
public class PositionComponent implements Component {
    public int row;
    public int col;

    public PositionComponent set(int row, int col) {
        this.row = row;
        this.col = col;
        return this;
    }
}
