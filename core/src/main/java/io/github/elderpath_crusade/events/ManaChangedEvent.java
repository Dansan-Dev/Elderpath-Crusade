package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record ManaChangedEvent(PieceAlignment player, int newMana) implements GameEvent {}
