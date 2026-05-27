package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record PieceSpawnedEvent(String pieceId, PieceAlignment owner, int row, int col) implements GameEvent {}
