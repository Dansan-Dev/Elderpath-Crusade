package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

public class HealIntentComponent implements Component {
    public int amount;

    public HealIntentComponent set(int amount) {
        this.amount = amount;
        return this;
    }
}
