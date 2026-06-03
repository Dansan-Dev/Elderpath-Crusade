package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Tracks stun state on an entity. When turnsRemaining > 0, the piece cannot act.
 */
public class StunComponent implements Component {
    public int turnsRemaining = 0;

    public boolean isStunned() {
        return turnsRemaining > 0;
    }

    public void decrement() {
        if (turnsRemaining > 0) turnsRemaining--;
    }
}
