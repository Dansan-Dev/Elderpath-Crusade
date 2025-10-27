package io.github.forest_of_dreams.abilities;

import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;
import io.github.forest_of_dreams.multiplayer.GameEvent;

/**
 * Triggered abilities react to lifecycle or global game events. Implement any hooks you need;
 * all methods are optional and default to no-op.
 */
public interface TriggeredAbility extends Ability {
    @Override
    default AbilityType getType() { return AbilityType.TRIGGERED; }

    // Owner-centric lifecycle hooks (piece or turn)
    default void onOwnerSpawned(MonsterGamePiece owner, int row, int col) {}
    default void onOwnerMoved(MonsterGamePiece owner, int fromRow, int fromCol, int toRow, int toCol) {}
    default void onOwnerAttack(MonsterGamePiece owner, MonsterGamePiece target, int damage) {}
    default void onOwnerDamaged(MonsterGamePiece owner, int amount, MonsterGamePiece source) {}
    default void onOwnerDied(MonsterGamePiece owner) {}
    default void onTurnStarted(PieceAlignment currentPlayer) {}
    default void onTurnEnded(PieceAlignment endingPlayer) {}

    // Global event hook for flexible triggers (any spawned, enemy within N, etc.)
    default void onGameEvent(GameEvent event) {}
}
