package io.github.forest_of_dreams.abilities;

import io.github.forest_of_dreams.game_objects.board.MonsterGamePiece;

/**
 * Base contract for any ability attached to a MonsterGamePiece.
 * Implementations should be stateless or manage their own internal state safely.
 */
public interface Ability extends AutoCloseable {
    /** Human-readable name, e.g., "Pack Hunter" */
    String getName();

    /** Short description shown on cards or UI. */
    String getDescription();

    AbilityType getType();

    /** Called when this ability is attached to a piece. */
    default void onAttach(MonsterGamePiece owner) {}

    /** Called when this ability is detached from a piece (e.g., on death). */
    default void onDetach() {}

    /** Release any resources; alias for onDetach for try-with-resources compatibility. */
    @Override
    default void close() { onDetach(); }
}
