package io.github.elderpath_crusade.events;

public record PieceKilledEvent(
    String killerId,
    String victimId,
    int excessDamage,
    int row,
    int col
) implements GameEvent {}
