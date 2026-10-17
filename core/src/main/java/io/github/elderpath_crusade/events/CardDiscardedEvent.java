package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

import java.util.List;

/** Cards left a player's hand without being played (end-of-turn discard, or a discard effect). */
public record CardDiscardedEvent(PieceAlignment player, int count, List<String> instanceIds) implements GameEvent {}
