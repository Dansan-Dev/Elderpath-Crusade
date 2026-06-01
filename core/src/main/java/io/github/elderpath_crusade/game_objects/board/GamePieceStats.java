package io.github.elderpath_crusade.game_objects.board;

import com.badlogic.ashley.core.Entity;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.enums.settings.GamePieceType;
import lombok.Getter;
import lombok.Setter;

public class GamePieceStats {
    @Getter GamePieceType type;

    // Local fields used before ECS entity is linked
    private int cost;
    private int maxHealth;
    private int damage;
    private int speed;
    private int actions;
    private int currentHealth;
    private int remainingActions;

    // ECS backing — when set, all reads/writes go through StatsComponent
    private StatsComponent ecsStats;

    private GamePieceStats(GamePieceType type, int cost, int maxHealth, int damage, int speed, int actions) {
        this.type = type;
        this.cost = cost;
        this.maxHealth = maxHealth;
        this.damage = damage;
        this.speed = speed;
        this.actions = actions;
        resetCurrentHealth();
        resetRemainingActions();
    }

    public static GamePieceStats getTerrainStats(int maxHealth, int damage) {
        return new GamePieceStats(GamePieceType.TERRAIN, 0, maxHealth, damage, 0, 0);
    }

    public static GamePieceStats getMonsterStats(int cost, int maxHealth, int damage, int speed, int actions) {
        return new GamePieceStats(GamePieceType.MONSTER, cost, maxHealth, damage, speed, actions);
    }

    /**
     * Link this stats object to an ECS entity's StatsComponent.
     * After linking, all reads/writes delegate to the component.
     */
    public void linkEntity(Entity entity) {
        if (entity != null) {
            this.ecsStats = entity.getComponent(StatsComponent.class);
        }
    }

    // --- Getters (delegate to ECS when linked) ---
    public int getCost() { return ecsStats != null ? ecsStats.cost : cost; }
    public int getMaxHealth() { return ecsStats != null ? ecsStats.maxHealth : maxHealth; }
    public int getDamage() { return ecsStats != null ? ecsStats.damage : damage; }
    public int getSpeed() { return ecsStats != null ? ecsStats.speed : speed; }
    public int getActions() { return ecsStats != null ? ecsStats.actions : actions; }
    public int getCurrentHealth() { return ecsStats != null ? ecsStats.currentHealth : currentHealth; }
    public int getRemainingActions() { return ecsStats != null ? ecsStats.remainingActions : remainingActions; }

    // --- Setters (delegate to ECS when linked) ---
    public void setCurrentHealth(int value) {
        if (ecsStats != null) ecsStats.currentHealth = value;
        else currentHealth = value;
    }

    public void setRemainingActions(int value) {
        if (ecsStats != null) ecsStats.remainingActions = value;
        else remainingActions = value;
    }

    public void resetCurrentHealth() {
        setCurrentHealth(getMaxHealth());
    }

    public void resetRemainingActions() {
        if (ecsStats != null) ecsStats.resetActions();
        else remainingActions = actions;
    }

    public void dealDamage(int damage) {
        setCurrentHealth(getCurrentHealth() - damage);
    }

    public boolean isDead() {
        return getCurrentHealth() <= 0;
    }

    public GamePieceStats copy() {
        return new GamePieceStats(type, getCost(), getMaxHealth(), getDamage(), getSpeed(), getActions());
    }
}
