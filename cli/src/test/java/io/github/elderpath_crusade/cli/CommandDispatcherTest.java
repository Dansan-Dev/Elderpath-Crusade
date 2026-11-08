package io.github.elderpath_crusade.cli;

import io.github.elderpath_crusade.client.ClientCommands;
import io.github.elderpath_crusade.match.Coord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dispatch is a pure call to a fake ClientCommands — no session, board, or GameContext, same
 * pattern as ClientControllerTest's FakeCommands.
 */
class CommandDispatcherTest {
    private FakeCommands commands;

    @BeforeEach
    void setUp() {
        commands = new FakeCommands();
    }

    @Test
    void move_callsMoveOrAttackWithBothCoords() {
        CommandDispatcher.dispatch(new CliCommand.Move(new Coord(1, 2), new Coord(3, 4)), commands);
        assertEquals(List.of(1, 2, 3, 4), commands.lastMove);
    }

    @Test
    void summon_callsPlaySummonWithInstanceIdAndCoord() {
        CommandDispatcher.dispatch(new CliCommand.Summon("P1-3", new Coord(0, 1)), commands);
        assertEquals("P1-3", commands.lastSummonInstanceId);
        assertEquals(List.of(0, 1), commands.lastSummonCoord);
    }

    @Test
    void spell_withTarget_callsPlaySpellWithRowAndCol() {
        CommandDispatcher.dispatch(new CliCommand.Spell("P2-1", new Coord(2, 5)), commands);
        assertEquals("P2-1", commands.lastSpellInstanceId);
        assertEquals(2, commands.lastSpellRow);
        assertEquals(5, commands.lastSpellCol);
    }

    @Test
    void spell_untargeted_callsPlaySpellWithNullCoords() {
        CommandDispatcher.dispatch(new CliCommand.Spell("P2-1", null), commands);
        assertEquals("P2-1", commands.lastSpellInstanceId);
        assertNull(commands.lastSpellRow);
        assertNull(commands.lastSpellCol);
    }

    @Test
    void ability_callsActivateAbilityWithPieceIdAndIndex() {
        CommandDispatcher.dispatch(new CliCommand.Ability("piece-9", 1), commands);
        assertEquals("piece-9", commands.lastAbilityPieceId);
        assertEquals(1, commands.lastAbilityActionIndex);
    }

    @Test
    void pick_callsAnswerPickWithChoice() {
        CommandDispatcher.dispatch(new CliCommand.Pick(new Coord(4, 4)), commands);
        assertEquals(new Coord(4, 4), commands.lastAnswerPickChoice);
    }

    @Test
    void cancelPick_callsCancelPick() {
        CommandDispatcher.dispatch(new CliCommand.CancelPick(), commands);
        assertTrue(commands.cancelPickCalled);
    }

    @Test
    void endTurn_callsEndTurn() {
        CommandDispatcher.dispatch(new CliCommand.EndTurn(), commands);
        assertTrue(commands.endTurnCalled);
    }

    @Test
    void show_legal_board_quit_touchNothingOnCommands() {
        CommandDispatcher.dispatch(new CliCommand.Show("piece-1"), commands);
        CommandDispatcher.dispatch(new CliCommand.Legal(), commands);
        CommandDispatcher.dispatch(new CliCommand.Board(), commands);
        CommandDispatcher.dispatch(new CliCommand.Quit(), commands);

        assertFalse(commands.anyMethodCalled());
    }

    @Test
    void show_legal_board_quit_returnNotSent() {
        assertEquals(ClientCommands.NOT_SENT, CommandDispatcher.dispatch(new CliCommand.Show("piece-1"), commands));
        assertEquals(ClientCommands.NOT_SENT, CommandDispatcher.dispatch(new CliCommand.Legal(), commands));
        assertEquals(ClientCommands.NOT_SENT, CommandDispatcher.dispatch(new CliCommand.Board(), commands));
        assertEquals(ClientCommands.NOT_SENT, CommandDispatcher.dispatch(new CliCommand.Quit(), commands));
    }

    /** Records the last call of each kind; fails the dispatcher's "no-op for display commands" test if any of these fire. */
    private static final class FakeCommands implements ClientCommands {
        List<Integer> lastMove;
        String lastSummonInstanceId;
        List<Integer> lastSummonCoord;
        String lastSpellInstanceId;
        Integer lastSpellRow;
        Integer lastSpellCol;
        String lastAbilityPieceId;
        int lastAbilityActionIndex;
        Coord lastAnswerPickChoice;
        boolean cancelPickCalled;
        boolean endTurnCalled;

        boolean anyMethodCalled() {
            return lastMove != null || lastSummonInstanceId != null || lastSpellInstanceId != null
                    || lastAbilityPieceId != null || lastAnswerPickChoice != null || cancelPickCalled || endTurnCalled;
        }

        @Override
        public long moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol) {
            lastMove = List.of(srcRow, srcCol, dstRow, dstCol);
            return 1;
        }

        @Override
        public long playSummon(String instanceId, int row, int col) {
            lastSummonInstanceId = instanceId;
            lastSummonCoord = List.of(row, col);
            return 1;
        }

        @Override
        public long playSpell(String instanceId, Integer targetRow, Integer targetCol) {
            lastSpellInstanceId = instanceId;
            lastSpellRow = targetRow;
            lastSpellCol = targetCol;
            return 1;
        }

        @Override
        public long activateAbility(String pieceId, int actionIndex) {
            lastAbilityPieceId = pieceId;
            lastAbilityActionIndex = actionIndex;
            return 1;
        }

        @Override
        public long answerPick(Coord choice) {
            lastAnswerPickChoice = choice;
            return 1;
        }

        @Override
        public long cancelPick() {
            cancelPickCalled = true;
            return 1;
        }

        @Override
        public long endTurn() {
            endTurnCalled = true;
            return 1;
        }
    }
}
