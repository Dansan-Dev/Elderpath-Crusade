package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record CardPlayedEvent(String cardName, PieceAlignment owner, int row, int col, String pieceId) implements GameEvent {}
