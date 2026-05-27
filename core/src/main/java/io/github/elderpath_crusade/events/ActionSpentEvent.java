package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record ActionSpentEvent(String pieceId, PieceAlignment owner, int remaining) implements GameEvent {}
