package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record CardDiscardedEvent(PieceAlignment player, int count) implements GameEvent {}
