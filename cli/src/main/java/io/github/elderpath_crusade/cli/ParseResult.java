package io.github.elderpath_crusade.cli;

/**
 * The outcome of {@link CommandParser#parse(String)} — either a successfully parsed
 * {@link CliCommand} or a human-readable reason it wasn't. Parsing never throws; a blank line or
 * malformed input always comes back as {@link Error}, never an exception.
 */
public sealed interface ParseResult {
    record Ok(CliCommand command) implements ParseResult {}

    record Error(String message) implements ParseResult {}
}
