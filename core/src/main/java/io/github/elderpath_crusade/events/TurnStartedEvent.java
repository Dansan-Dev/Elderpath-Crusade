package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record TurnStartedEvent(PieceAlignment player) implements GameEvent {}
