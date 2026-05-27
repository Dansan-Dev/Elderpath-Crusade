package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record CardShuffledEvent(PieceAlignment owner, int deckSize) implements GameEvent {}
