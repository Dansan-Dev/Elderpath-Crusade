package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

public record ActionsResetEvent(PieceAlignment player) implements GameEvent {}
