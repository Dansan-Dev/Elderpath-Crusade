package io.github.elderpath_crusade.match;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.match.ActionResult.Reason;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** PickRouter (ARCH-020): routing by slot kind, answer validation, cancel and queueing. Headless. */
class PickRouterTest {
    private static final List<Coord> CANDIDATES = List.of(new Coord(1, 1), new Coord(1, 2));

    /** Records what it was asked; answers only when the test says so. */
    private static final class Recording implements PickResponder {
        final List<PickRequest> requested = new ArrayList<>();
        final List<PickRequest> cancelled = new ArrayList<>();

        @Override
        public void onPickRequested(PickRequest request, PickRouter router) {
            requested.add(request);
        }

        @Override
        public void onPickCancelled(PickRequest request) {
            cancelled.add(request);
        }
    }

    private PickRouter router;
    private Recording local;
    private final List<PickResponse> answers = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TypedEventBus.get().clear();
        GameContext.createHeadless();
        router = GameContext.get().getPickRouter();
        local = new Recording();
        router.setLocalResponder(local);
        answers.clear();
    }

    @AfterEach
    void tearDown() {
        TypedEventBus.get().clear();
    }

    private PickRequest ask(PieceAlignment owner, boolean cancellable) {
        PickRequest request = router.newRequest(owner, PickKind.TILE, CANDIDATES, "test", cancellable);
        router.requestPick(request, answers::add);
        return request;
    }

    @Test
    void humanSlot_routesToLocalResponder_andWaits() {
        PickRequest request = ask(PieceAlignment.P1, true);
        assertEquals(List.of(request), local.requested);
        assertTrue(router.hasPending(PieceAlignment.P1));
        assertTrue(answers.isEmpty(), "no answer until the player picks");
    }

    @Test
    void validAnswer_deliveredOnce_andClearsPending() {
        PickRequest request = ask(PieceAlignment.P1, true);
        assertEquals(Reason.OK, router.answer(PieceAlignment.P1, PickResponse.chose(request.pickId(), new Coord(1, 2))).reason());
        assertEquals(List.of(PickResponse.chose(request.pickId(), new Coord(1, 2))), answers);
        assertFalse(router.hasPending(PieceAlignment.P1));
        assertEquals(Reason.NO_PENDING_PICK,
                router.answer(PieceAlignment.P1, PickResponse.chose(request.pickId(), new Coord(1, 2))).reason(), "answered once");
    }

    @Test
    void choiceNotAmongCandidates_rejected_pickStaysPending() {
        PickRequest request = ask(PieceAlignment.P1, true);
        assertEquals(Reason.INVALID_PICK, router.answer(PieceAlignment.P1, PickResponse.chose(request.pickId(), new Coord(5, 5))).reason());
        assertTrue(router.hasPending(PieceAlignment.P1));
        assertTrue(answers.isEmpty());
    }

    @Test
    void wrongOwnerOrStaleId_rejected() {
        PickRequest request = ask(PieceAlignment.P1, true);
        assertEquals(Reason.NO_PENDING_PICK, router.answer(PieceAlignment.P2, PickResponse.chose(request.pickId(), new Coord(1, 1))).reason());
        assertEquals(Reason.NO_PENDING_PICK, router.answer(PieceAlignment.P1, PickResponse.chose(request.pickId() + 99, new Coord(1, 1))).reason());
        assertEquals(Reason.NO_PENDING_PICK, router.answer(null, PickResponse.chose(request.pickId(), new Coord(1, 1))).reason());
        assertTrue(answers.isEmpty());
    }

    @Test
    void cancel_deliveredAsCancellation_unlessNotCancellable() {
        PickRequest request = ask(PieceAlignment.P1, true);
        assertEquals(Reason.OK, router.answer(PieceAlignment.P1, PickResponse.cancelled(request.pickId())).reason());
        assertTrue(answers.get(0).isCancelled());

        PickRequest strict = ask(PieceAlignment.P2, false);
        assertEquals(Reason.INVALID_PICK, router.answer(PieceAlignment.P2, PickResponse.cancelled(strict.pickId())).reason());
        assertTrue(router.hasPending(PieceAlignment.P2));
    }

    @Test
    void cancelPending_cancelsQueuedPicks_andTellsTheResponder() {
        PickRequest first = ask(PieceAlignment.P1, true);
        ask(PieceAlignment.P1, true);
        assertEquals(1, local.requested.size(), "one active pick per owner — the second queues");

        router.cancelPending(PieceAlignment.P1);
        assertEquals(2, answers.size());
        assertTrue(answers.stream().allMatch(PickResponse::isCancelled));
        assertEquals(List.of(first), local.cancelled, "only the prompted pick needs clearing on screen");
        assertFalse(router.hasPending(PieceAlignment.P1));
    }

    @Test
    void queuedPick_dispatchedAfterTheFirstIsAnswered() {
        PickRequest first = ask(PieceAlignment.P1, true);
        PickRequest second = ask(PieceAlignment.P1, true);
        router.answer(PieceAlignment.P1, PickResponse.chose(first.pickId(), new Coord(1, 1)));
        assertEquals(List.of(first, second), local.requested);
        assertEquals(second, router.activeRequest(PieceAlignment.P1));
    }

    @Test
    void followUpPickAskedFromAnAnswer_dispatchedExactlyOnce() {
        PickRequest first = router.newRequest(PieceAlignment.P1, PickKind.PIECE, CANDIDATES, "first", true);
        router.requestPick(first, response -> ask(PieceAlignment.P1, true));
        router.answer(PieceAlignment.P1, PickResponse.chose(first.pickId(), new Coord(1, 1)));
        assertEquals(2, local.requested.size(), "the follow-up was asked exactly once");
        assertTrue(router.hasPending(PieceAlignment.P1));
    }

    @Test
    void emptyCandidatesOrNoResponder_cancelledImmediately() {
        PickRequest none = router.newRequest(PieceAlignment.P1, PickKind.TILE, List.of(), "empty", true);
        router.requestPick(none, answers::add);
        assertTrue(answers.get(0).isCancelled());

        router.setLocalResponder(null); // e.g. a headless server with nobody local to ask
        ask(PieceAlignment.P1, true);
        assertTrue(answers.get(1).isCancelled());
        assertFalse(router.hasPending(PieceAlignment.P1));
    }

    @Test
    void remoteSlot_routesToItsRemoteResponder() {
        Recording remote = new Recording();
        router.setRemoteResponder(PieceAlignment.P2, remote);
        ask(PieceAlignment.P2, true);
        ask(PieceAlignment.P1, true);
        assertEquals(1, remote.requested.size());
        assertEquals(PieceAlignment.P2, remote.requested.get(0).owner());
        assertEquals(1, local.requested.size());
    }

    @Test
    void botSlot_answeredAtOnce_neverWaits() {
        List<String> deck = new ArrayList<>(Collections.nCopies(6, "Nothing"));
        MatchSetup.prepare(MatchConfig.standard(GameMode.DEMO, deck, new ArrayList<>(deck), 1L, Map.of(PieceAlignment.P2, BotKind.SMART)));
        ask(PieceAlignment.P2, true);
        assertEquals(1, answers.size());
        assertFalse(answers.get(0).isCancelled());
        assertTrue(CANDIDATES.contains(answers.get(0).choice()));
        assertFalse(router.hasPending(PieceAlignment.P2));
        assertTrue(local.requested.isEmpty());
    }

    @Test
    void turnEnd_cancelsPendingPicks() {
        List<String> deck = new ArrayList<>(Collections.nCopies(6, "Nothing"));
        MatchSetup.start(MatchConfig.standard(GameMode.DEMO, deck, new ArrayList<>(deck), 1L, Map.of()));
        router.setLocalResponder(local); // MatchSetup keeps the responder; re-set for clarity
        ask(PieceAlignment.P1, true);
        GameContext.get().getTurnManager().endTurn();
        assertTrue(answers.get(0).isCancelled());
        assertFalse(router.hasPending(PieceAlignment.P1));
    }
}
