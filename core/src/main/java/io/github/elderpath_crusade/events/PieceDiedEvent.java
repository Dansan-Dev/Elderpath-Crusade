package io.github.elderpath_crusade.events;

public record PieceDiedEvent(String pieceId, int row, int col) implements GameEvent {}
