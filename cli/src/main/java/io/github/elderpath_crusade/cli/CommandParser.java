package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.match.Coord;

/**
 * Parses one line of the CLI's command language (see {@code headless-cli-playtest-plan.md}) into
 * a {@link CliCommand}. Command keywords are case-insensitive; runs of whitespace (spaces, tabs)
 * between tokens are collapsed. Coordinates are written {@code row,col} with no space around the
 * comma. Never throws — a blank line, unknown command or malformed argument comes back as a
 * {@link ParseResult.Error} with a readable message, same "ask, don't guess" spirit as the rest
 * of the client path: this class does no legality checking, only syntax.
 */
public final class CommandParser {
    private CommandParser() {}

    public static ParseResult parse(String line) {
        if (line == null) return new ParseResult.Error("empty command");
        String trimmed = line.trim();
        if (trimmed.isEmpty()) return new ParseResult.Error("empty command");

        String[] tokens = trimmed.split("\\s+");
        String keyword = tokens[0].toLowerCase();
        try {
            return new ParseResult.Ok(switch (keyword) {
                case "move" -> parseMove(tokens);
                case "summon" -> parseSummon(tokens);
                case "spell" -> parseSpell(tokens);
                case "ability" -> parseAbility(tokens);
                case "pick" -> parsePick(tokens);
                case "end" -> noArgs(tokens, new CliCommand.EndTurn());
                case "show" -> parseShow(tokens);
                case "legal" -> noArgs(tokens, new CliCommand.Legal());
                case "board" -> noArgs(tokens, new CliCommand.Board());
                case "quit" -> noArgs(tokens, new CliCommand.Quit());
                default -> throw new IllegalArgumentException("unknown command: " + tokens[0]);
            });
        } catch (IllegalArgumentException e) {
            return new ParseResult.Error(e.getMessage());
        }
    }

    private static CliCommand parseMove(String[] tokens) {
        requireArgCount(tokens, 2, "move <r>,<c> <r>,<c>");
        return new CliCommand.Move(parseCoord(tokens[1]), parseCoord(tokens[2]));
    }

    private static CliCommand parseSummon(String[] tokens) {
        requireArgCount(tokens, 2, "summon <instanceId> <r>,<c>");
        return new CliCommand.Summon(tokens[1], parseCoord(tokens[2]));
    }

    private static CliCommand parseSpell(String[] tokens) {
        if (tokens.length == 2) {
            return new CliCommand.Spell(tokens[1], null);
        }
        requireArgCount(tokens, 2, "spell <instanceId> [<r>,<c>]");
        return new CliCommand.Spell(tokens[1], parseCoord(tokens[2]));
    }

    private static CliCommand parseAbility(String[] tokens) {
        requireArgCount(tokens, 2, "ability <pieceId> <actionIndex>");
        return new CliCommand.Ability(tokens[1], parseInt(tokens[2], "actionIndex"));
    }

    private static CliCommand parsePick(String[] tokens) {
        requireArgCount(tokens, 1, "pick <r>,<c> | pick cancel");
        if (tokens[1].equalsIgnoreCase("cancel")) {
            return new CliCommand.CancelPick();
        }
        return new CliCommand.Pick(parseCoord(tokens[1]));
    }

    private static CliCommand parseShow(String[] tokens) {
        requireArgCount(tokens, 1, "show <pieceId>");
        return new CliCommand.Show(tokens[1]);
    }

    private static CliCommand noArgs(String[] tokens, CliCommand command) {
        if (tokens.length != 1) {
            throw new IllegalArgumentException(tokens[0] + " takes no arguments");
        }
        return command;
    }

    /** tokens[0] is the keyword; argCount is how many arguments must follow it. */
    private static void requireArgCount(String[] tokens, int argCount, String usage) {
        if (tokens.length != argCount + 1) {
            throw new IllegalArgumentException("usage: " + usage);
        }
    }

    private static Coord parseCoord(String token) {
        String[] parts = token.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("bad coordinate (want row,col): " + token);
        }
        return new Coord(parseInt(parts[0], "row"), parseInt(parts[1], "col"));
    }

    private static int parseInt(String token, String what) {
        try {
            return Integer.parseInt(token.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(what + " must be a number (was: " + token + ")");
        }
    }
}
