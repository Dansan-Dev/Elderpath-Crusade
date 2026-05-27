package io.github.elderpath_crusade.model.card;

import io.github.elderpath_crusade.model.piece.PieceStats;

import java.util.List;

/**
 * Pure model for a card. Describes what the card does without rendering.
 */
public record CardModel(
        String name,
        PieceStats pieceStats,
        List<String> abilityDescriptions
) {
    public CardModel {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Card name required");
        if (pieceStats == null) throw new IllegalArgumentException("pieceStats required");
        if (abilityDescriptions == null) abilityDescriptions = List.of();
    }

    public int cost() { return pieceStats.cost(); }
}
