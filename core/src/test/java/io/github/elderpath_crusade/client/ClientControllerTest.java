package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.PickKind;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ClientController's own transitions (pick prompting, victory presentation, command
 * delegation) are pure and testable with no session, board, or GameContext — a fake
 * ClientCommands/PickPrompter stand in for the real network/UI side effects.
 */
class ClientControllerTest {
    private ClientMatchState state;
    private FakeCommands commands;
    private FakePrompter prompter;
    private List<PieceAlignment> victoriesShown;
    private ClientController controller;

    @BeforeEach
    void setUp() {
        state = new ClientMatchState();
        state.apply(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, PieceAlignment.P1, 7, 5,
                List.of(), Map.of(PieceAlignment.P1, "HUMAN", PieceAlignment.P2, "HUMAN")));
        commands = new FakeCommands();
        prompter = new FakePrompter();
        victoriesShown = new ArrayList<>();
        controller = new ClientController(() -> state, commands, prompter, victoriesShown::add);
    }

    private PickRequest pick(long id) {
        return new PickRequest(id, PieceAlignment.P1, PickKind.PIECE, List.of(new Coord(1, 1)), "Test", true);
    }

    @Test
    void update_withNothingPending_doesNothing() {
        controller.update();
        assertFalse(prompter.shown);
        assertFalse(controller.hasPendingPick());
        assertTrue(victoriesShown.isEmpty());
    }

    @Test
    void update_promptsOnceForANewlyRequestedPick() {
        state.apply(new ServerMessage.PickRequested(pick(1)));

        controller.update();
        controller.update(); // same pick still pending — must not re-prompt

        assertEquals(1, prompter.showCount);
        assertEquals(1L, prompter.lastRequest.pickId());
        assertTrue(controller.hasPendingPick());
    }

    @Test
    void choosingInThePrompt_sendsAnswerPickThroughCommands() {
        state.apply(new ServerMessage.PickRequested(pick(1)));
        controller.update();

        prompter.lastOnChosen.accept(new Coord(1, 1));

        assertEquals(new Coord(1, 1), commands.lastAnswer);
    }

    @Test
    void cancellingInThePrompt_sendsCancelPickThroughCommands() {
        state.apply(new ServerMessage.PickRequested(pick(1)));
        controller.update();

        prompter.lastOnCancelled.run();

        assertTrue(commands.cancelPickCalled);
    }

    @Test
    void aPickThatDisappearsWithoutOurAnswer_clearsThePrompt() {
        state.apply(new ServerMessage.PickRequested(pick(1)));
        controller.update();
        prompter.cleared = false; // the initial show() also issues a clear() first — reset to observe this one

        state.apply(new ServerMessage.PickCancelled(1));
        controller.update();

        assertTrue(prompter.cleared);
        assertFalse(controller.hasPendingPick());
    }

    @Test
    void aFollowUpPickReplacesThePromptForThePreviousOne() {
        state.apply(new ServerMessage.PickRequested(pick(1)));
        controller.update();
        state.apply(new ServerMessage.PickAnswered(1));
        state.apply(new ServerMessage.PickRequested(pick(2)));

        controller.update();

        assertEquals(2, prompter.showCount);
        assertEquals(2L, prompter.lastRequest.pickId());
    }

    @Test
    void whenThePrompterCannotShow_thePickIsCancelledInstead() {
        prompter.canShow = false;
        state.apply(new ServerMessage.PickRequested(pick(1)));

        controller.update();

        assertTrue(commands.cancelPickCalled);
    }

    @Test
    void update_showsVictoryOnceWhenTheMatchEnds() {
        state.apply(new ServerMessage.MatchEnded(PieceAlignment.P1));

        controller.update();
        controller.update();

        assertEquals(List.of(PieceAlignment.P1), victoriesShown);
    }

    @Test
    void legalActions_mirrorsTheLastUpdateFromTheServer() {
        assertNull(controller.legalActions());
        assertFalse(controller.canEndTurn());

        LegalActions actions = new LegalActions(PieceAlignment.P1, true, null, true, List.of(), List.of());
        state.apply(new ServerMessage.LegalActionsUpdate(actions));

        assertSame(actions, controller.legalActions());
        assertTrue(controller.canEndTurn());
    }

    @Test
    void commandMethods_delegateDirectlyToClientCommands() {
        controller.moveOrAttack(0, 0, 1, 1);
        assertEquals(List.of(0, 0, 1, 1), commands.lastMove);

        controller.playSummon("P1-0", 0, 2);
        assertEquals("P1-0", commands.lastSummonInstanceId);

        controller.playSpell("P1-1", 3, 4);
        assertEquals("P1-1", commands.lastSpellInstanceId);

        controller.activateAbility("piece-a", 1);
        assertEquals("piece-a", commands.lastAbilityPieceId);

        controller.endTurn();
        assertTrue(commands.endTurnCalled);
    }

    private static final class FakeCommands implements ClientCommands {
        Coord lastAnswer;
        boolean cancelPickCalled;
        boolean endTurnCalled;
        List<Integer> lastMove;
        String lastSummonInstanceId;
        String lastSpellInstanceId;
        String lastAbilityPieceId;

        @Override
        public long moveOrAttack(int srcRow, int srcCol, int dstRow, int dstCol) {
            lastMove = List.of(srcRow, srcCol, dstRow, dstCol);
            return 1;
        }

        @Override
        public long playSummon(String instanceId, int row, int col) {
            lastSummonInstanceId = instanceId;
            return 1;
        }

        @Override
        public long playSpell(String instanceId, Integer targetRow, Integer targetCol) {
            lastSpellInstanceId = instanceId;
            return 1;
        }

        @Override
        public long activateAbility(String pieceId, int actionIndex) {
            lastAbilityPieceId = pieceId;
            return 1;
        }

        @Override
        public long answerPick(Coord choice) {
            lastAnswer = choice;
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

    private static final class FakePrompter implements ClientController.PickPrompter {
        boolean canShow = true;
        boolean shown;
        boolean cleared;
        int showCount;
        PickRequest lastRequest;
        Consumer<Coord> lastOnChosen;
        Runnable lastOnCancelled;

        @Override
        public boolean show(PickRequest request, Consumer<Coord> onChosen, Runnable onCancelled) {
            if (!canShow) return false;
            shown = true;
            showCount++;
            lastRequest = request;
            lastOnChosen = onChosen;
            lastOnCancelled = onCancelled;
            return true;
        }

        @Override
        public void clear() {
            cleared = true;
            shown = false;
        }
    }
}
