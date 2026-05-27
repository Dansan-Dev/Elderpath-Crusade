package io.github.elderpath_crusade.model.piece;

/**
 * Immutable base stats for a piece.
 */
public record PieceStats(int cost, int maxHealth, int damage, int speed, int actions) {

    public PieceStats {
        if (maxHealth < 1) throw new IllegalArgumentException("maxHealth must be >= 1");
        if (cost < 0) throw new IllegalArgumentException("cost must be >= 0");
    }
}
