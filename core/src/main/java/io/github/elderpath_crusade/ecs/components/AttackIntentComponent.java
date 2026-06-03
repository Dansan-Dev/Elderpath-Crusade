package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Intent component signaling that an entity wants to attack a target position.
 * Added by input/ability code, processed and removed by AttackSystem.
 */
public class AttackIntentComponent implements Component {
    public int targetRow;
    public int targetCol;

    public AttackIntentComponent set(int targetRow, int targetCol) {
        this.targetRow = targetRow;
        this.targetCol = targetCol;
        return this;
    }
}
