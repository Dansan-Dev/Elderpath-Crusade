package io.github.forest_of_dreams.game_objects.board;

import io.github.forest_of_dreams.enums.settings.GamePieceType;
import lombok.Getter;
import lombok.Setter;

public class GamePieceStats {
    @Getter GamePieceType type;

    @Getter private int cost;
    @Getter private int maxHealth;
    @Getter private int damage;
    @Getter private int speed;
    @Getter private int actions;

    @Getter @Setter
    private int currentHealth;

    private GamePieceStats(GamePieceType type, int cost, int maxHealth, int damage, int speed, int actions) {
        this.type = type;
        this.cost = cost;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speed = speed;
        this.actions = actions;
        resetCurrentHealth();
    }

    public static GamePieceStats getTerrainStats(int maxHealth, int damage) {
        return new GamePieceStats(GamePieceType.TERRAIN, 0, maxHealth, damage, 0, 0);
    }

    public static GamePieceStats getMonsterStats(int cost, int maxHealth, int damage, int speed, int actions) {
        return new GamePieceStats(GamePieceType.MONSTER, cost, maxHealth, damage, speed, actions);
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

    public GamePieceStats copy() {
        return new GamePieceStats(type, cost, maxHealth, damage, speed, actions);
    }
}
