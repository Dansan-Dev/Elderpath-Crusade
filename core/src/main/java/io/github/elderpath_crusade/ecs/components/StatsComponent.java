package io.github.elderpath_crusade.ecs.components;

import com.badlogic.ashley.core.Component;

/**
 * Base and current stats for a piece entity.
 */
public class StatsComponent implements Component {
    public int cost;
    public int maxHealth;
    public int damage;
    public int speed;
    public int actions;
    public int currentHealth;
    public int remainingActions;

    public StatsComponent set(int cost, int maxHealth, int damage, int speed, int actions) {
        this.cost = cost;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speed = speed;
        this.actions = actions;
        this.currentHealth = maxHealth;
        this.remainingActions = 0; // summoning sickness
        return this;
    }

    public boolean isDead() { return currentHealth <= 0; }

    public void resetActions() { remainingActions = actions; }

    public void spendAction() { remainingActions = Math.max(0, remainingActions - 1); }
}
