package io.github.elderpath_crusade.model.piece;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.model.board.Position;

/**
 * Pure model for a game piece. No rendering, no LibGDX.
 * Holds identity, stats, alignment, and mutable game state.
 */
public class PieceModel {
    private final String id;
    private final String name;
    private final PieceAlignment alignment;
    private final PieceStats baseStats;

    private int currentHealth;
    private int remainingActions;
    private int stunTurnsRemaining;
    private Position position;

    public PieceModel(String id, String name, PieceAlignment alignment, PieceStats baseStats) {
        if (id == null || name == null || alignment == null || baseStats == null)
            throw new IllegalArgumentException("All fields required");
        this.id = id;
        this.name = name;
        this.alignment = alignment;
        this.baseStats = baseStats;
        this.currentHealth = baseStats.maxHealth();
        this.remainingActions = 0; // summoning sickness
        this.stunTurnsRemaining = 0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public PieceAlignment getAlignment() { return alignment; }
    public PieceStats getBaseStats() { return baseStats; }
    public int getCurrentHealth() { return currentHealth; }
    public int getRemainingActions() { return remainingActions; }
    public int getStunTurnsRemaining() { return stunTurnsRemaining; }
    public Position getPosition() { return position; }

    public void setPosition(Position position) { this.position = position; }
    public void setRemainingActions(int actions) { this.remainingActions = Math.max(0, actions); }

    public boolean isDead() { return currentHealth <= 0; }
    public boolean isStunned() { return stunTurnsRemaining > 0; }
    public boolean canAct() { return !isDead() && !isStunned() && remainingActions > 0; }

    public void dealDamage(int amount) {
        if (amount <= 0) return;
        currentHealth -= amount;
    }

    public boolean heal(int amount) {
        if (amount <= 0 || currentHealth >= baseStats.maxHealth()) return false;
        currentHealth = Math.min(currentHealth + amount, baseStats.maxHealth());
        return true;
    }

    public void spendAction() {
        remainingActions = Math.max(0, remainingActions - 1);
    }

    public void resetActions() {
        remainingActions = baseStats.actions();
    }

    public void stun(int turns) {
        stunTurnsRemaining = Math.max(0, turns);
    }

    public void tickStun() {
        if (stunTurnsRemaining > 0) stunTurnsRemaining--;
    }
}
