package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record CardDrawnEvent(PieceAlignment owner, String instanceId, String cardName, int handSize) implements GameEvent {}
