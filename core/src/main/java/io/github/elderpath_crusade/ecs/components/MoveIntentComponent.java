package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Intent component signaling that an entity wants to move.
 * Added by input/ability code, processed and removed by MovementSystem.
 */
public class MoveIntentComponent implements Component {
    public int targetRow;
    public int targetCol;

    public MoveIntentComponent set(int targetRow, int targetCol) {
        this.targetRow = targetRow;
        this.targetCol = targetCol;
        return this;
    }
}
