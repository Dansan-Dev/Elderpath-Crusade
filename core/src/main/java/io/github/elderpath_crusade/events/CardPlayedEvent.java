package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * A card left its owner's hand by being played. For a summon, (row, col) is where the piece
 * was summoned and pieceId is the spawned piece. For a spell, (row, col) is the targeted tile
 * (-1, -1 for an untargeted spell) and pieceId is null.
 */
public record CardPlayedEvent(String cardName, PieceAlignment owner, int row, int col, String pieceId,
                              String instanceId) implements GameEvent {}
