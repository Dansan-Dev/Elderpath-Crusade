package io.github.forest_of_dreams.data_objects;

import lombok.Getter;
import lombok.Setter;

public class GamePieceStats {
    @Getter
    private int cost;
    @Getter private int maxHealth;
    @Getter private int damage;
    @Getter private int speed;
    @Getter private int actions;

    @Getter @Setter
    private int currentHealth;

    public GamePieceStats(int cost, int maxHealth, int damage, int speed, int actions) {
        this.cost = cost;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speed = speed;
        this.actions = actions;
        resetCurrentHealth();
    }

    public void resetCurrentHealth() {
        currentHealth = maxHealth;
    }

    public void dealDamage(int damage) {
        currentHealth -= damage;
    }

    public boolean isDead() {
        return currentHealth <= 0;
    }
}
