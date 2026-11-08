package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.match.Coord;

/**
 * One parsed line of the CLI's command language (see {@code headless-cli-playtest-plan.md}).
 * {@link CommandParser} produces these; {@link CommandDispatcher} sends the action kinds
 * ({@link Move}, {@link Summon}, {@link Spell}, {@link Ability}, {@link Pick}, {@link CancelPick},
 * {@link EndTurn}) through a session's {@code ClientCommands}. {@link Show}, {@link Legal},
 * {@link Board} and {@link Quit} are display/control commands with no server call — CLI-004's
 * loop handles those directly against the renderer and its own exit logic.
 */
public sealed interface CliCommand {

    /** {@code move <r>,<c> <r>,<c>} — moves the piece at src to dst, or attacks what's there. */
    record Move(Coord src, Coord dst) implements CliCommand {}

    /** {@code summon <instanceId> <r>,<c>}. */
    record Summon(String instanceId, Coord at) implements CliCommand {}

    /** {@code spell <instanceId> [<r>,<c>]} — {@code at} is null for an untargeted spell. */
    record Spell(String instanceId, Coord at) implements CliCommand {}

    /** {@code ability <pieceId> <actionIndex>}. */
    record Ability(String pieceId, int actionIndex) implements CliCommand {}

    /** {@code pick <r>,<c>} — answers the pending pick with this candidate. */
    record Pick(Coord choice) implements CliCommand {}

    /** {@code pick cancel}. */
    record CancelPick() implements CliCommand {}

    /** {@code end} — ends this seat's turn. */
    record EndTurn() implements CliCommand {}

    /** {@code show <pieceId>} — reprint one piece's detail. No server call. */
    record Show(String pieceId) implements CliCommand {}

    /** {@code legal} — reprint this seat's LegalActions. No server call. */
    record Legal() implements CliCommand {}

    /** {@code board} — reprint the grid. No server call. */
    record Board() implements CliCommand {}

    /** {@code quit} — exits the CLI. No server call. */
    record Quit() implements CliCommand {}
}
