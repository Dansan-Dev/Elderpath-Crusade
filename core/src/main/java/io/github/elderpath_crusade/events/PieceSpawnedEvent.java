package io.github.elderpath_crusade.events;

import io.github.elderpath_crusade.enums.PieceAlignment;

/**
 * A piece entered the board — from a card play or an effect (SummonPiece). registryKey is the
 * pieces.yaml key (IdentityComponent.name), so a replica can show what spawned without
 * waiting for the next state sync; null when the emitter doesn't know it.
 */
public record PieceSpawnedEvent(String pieceId, PieceAlignment owner, int row, int col, String registryKey)
        implements GameEvent {

    public PieceSpawnedEvent(String pieceId, PieceAlignment owner, int row, int col) {
        this(pieceId, owner, row, col, null);
    }
}
