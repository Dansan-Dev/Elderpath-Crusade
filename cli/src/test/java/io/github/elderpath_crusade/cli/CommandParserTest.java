package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.match.Coord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Parsing is pure — no session, no GameContext, no server. */
class CommandParserTest {

    private static CliCommand ok(String line) {
        ParseResult result = CommandParser.parse(line);
        assertInstanceOf(ParseResult.Ok.class, result, "expected Ok but got: " + result);
        return ((ParseResult.Ok) result).command();
    }

    private static String error(String line) {
        ParseResult result = CommandParser.parse(line);
        assertInstanceOf(ParseResult.Error.class, result, "expected Error but got: " + result);
        return ((ParseResult.Error) result).message();
    }

    @Test
    void move_parsesSrcAndDst() {
        assertEquals(new CliCommand.Move(new Coord(1, 2), new Coord(3, 4)), ok("move 1,2 3,4"));
    }

    @Test
    void summon_parsesInstanceIdAndCoord() {
        assertEquals(new CliCommand.Summon("P1-3", new Coord(0, 1)), ok("summon P1-3 0,1"));
    }

    @Test
    void spell_withCoord_parsesTarget() {
        assertEquals(new CliCommand.Spell("P2-1", new Coord(2, 2)), ok("spell P2-1 2,2"));
    }

    @Test
    void spell_withoutCoord_targetIsNull() {
        assertEquals(new CliCommand.Spell("P2-1", null), ok("spell P2-1"));
    }

    @Test
    void ability_parsesPieceIdAndActionIndex() {
        assertEquals(new CliCommand.Ability("piece-7", 2), ok("ability piece-7 2"));
    }

    @Test
    void pick_withCoord_parsesChoice() {
        assertEquals(new CliCommand.Pick(new Coord(5, 1)), ok("pick 5,1"));
    }

    @Test
    void pick_cancel_isCaseInsensitive() {
        assertEquals(new CliCommand.CancelPick(), ok("pick cancel"));
        assertEquals(new CliCommand.CancelPick(), ok("pick CANCEL"));
        assertEquals(new CliCommand.CancelPick(), ok("pick Cancel"));
    }

    @Test
    void end_show_legal_board_quit_parse() {
        assertEquals(new CliCommand.EndTurn(), ok("end"));
        assertEquals(new CliCommand.Show("piece-1"), ok("show piece-1"));
        assertEquals(new CliCommand.Legal(), ok("legal"));
        assertEquals(new CliCommand.Board(), ok("board"));
        assertEquals(new CliCommand.Quit(), ok("quit"));
    }

    @Test
    void keywords_areCaseInsensitive() {
        assertEquals(new CliCommand.EndTurn(), ok("END"));
        assertEquals(new CliCommand.Board(), ok("Board"));
        assertEquals(new CliCommand.Move(new Coord(1, 1), new Coord(2, 2)), ok("MOVE 1,1 2,2"));
    }

    @Test
    void whitespace_multipleSpacesAndTabsAreTolerated() {
        assertEquals(new CliCommand.Move(new Coord(1, 2), new Coord(3, 4)), ok("move   1,2\t3,4"));
        assertEquals(new CliCommand.EndTurn(), ok("  end  "));
    }

    @Test
    void blankLine_isAnErrorNotAnException() {
        assertDoesNotThrow(() -> CommandParser.parse(""));
        assertDoesNotThrow(() -> CommandParser.parse("   "));
        assertDoesNotThrow(() -> CommandParser.parse(null));
        assertNotNull(error(""));
        assertNotNull(error("   "));
    }

    @Test
    void unknownCommand_isAnErrorNotAnException() {
        String message = error("teleport 1,1");
        assertTrue(message.toLowerCase().contains("unknown"), message);
    }

    @Test
    void wrongArgCount_isAnError() {
        assertNotNull(error("move 1,2"));
        assertNotNull(error("move 1,2 3,4 5,6"));
        assertNotNull(error("summon"));
        assertNotNull(error("ability piece-1"));
        assertNotNull(error("pick"));
        assertNotNull(error("end extra"));
    }

    @Test
    void nonNumericCoordinates_isAnError() {
        assertNotNull(error("move a,b 1,1"));
        assertNotNull(error("move 1,1 1,x"));
        assertNotNull(error("pick 1"));
        assertNotNull(error("ability piece-1 two"));
    }

    @Test
    void parsing_neverThrows_forArbitraryGarbage() {
        assertDoesNotThrow(() -> CommandParser.parse("!!! ,,, ???"));
        assertDoesNotThrow(() -> CommandParser.parse("move ,"));
        assertDoesNotThrow(() -> CommandParser.parse("move 1,2,3 4,5"));
    }
}
