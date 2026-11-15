package io.github.elderpath_crusade.client;

import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.test.RequiresAssets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ARCH-031: the real client stack — {@link ClientMatchSession} + {@link ClientController},
 * exactly as every BattleRoom builds them — against a real {@code ServerLauncher} child process,
 * for solo, hotseat, host and join. Picks go through the controller's prompt (answered the way
 * a click on the first candidate would be), every other action comes from {@link AutoPlayer}
 * through the session. Each poll checks: no command of ours was ever rejected (the client only
 * offers what LegalActions allow), and at the end the victory screen was presented exactly once,
 * with the server's own winner.
 *
 * <p>A child process can't see in-code definitions, so this needs the real assets on disk
 * (@RequiresAssets — local profile). Server bots/ticks are sped up via launcher flags.
 */
@Tag("e2e")
@Timeout(180)
class LiveClientSessionE2eTest {
    private static final String ASSETS = System.getProperty("elderpath.assetsDir", "../assets");
    private static final List<String> DECK = List.of("Rogue", "Rogue", "Charger", "Charger");
    /** Warp Mage's Displace always asks two picks (unit, then tile) — exercises the prompt path. */
    private static final List<String> PICK_DECK = List.of("Warp Mage", "Rogue", "Charger", "Charger");
    private static final String[] FAST = {"--time-scale", "20", "--tick-ms", "5", "--seed", "1"};
    private static final long MATCH_DEADLINE_NANOS = 120_000_000_000L;

    private String previousAssetsProperty;
    private final List<ClientMatchSession> opened = new ArrayList<>();

    @BeforeEach
    void pointServerAtRealAssets() {
        previousAssetsProperty = System.getProperty(ClientMatchSession.ASSETS_PROPERTY);
        System.setProperty(ClientMatchSession.ASSETS_PROPERTY, ASSETS);
    }

    @AfterEach
    void closeSessionsAndRestore() {
        for (ClientMatchSession s : opened) s.close();
        if (previousAssetsProperty == null) System.clearProperty(ClientMatchSession.ASSETS_PROPERTY);
        else System.setProperty(ClientMatchSession.ASSETS_PROPERTY, previousAssetsProperty);
    }

    @Test
    @RequiresAssets
    void solo_vsServerBot_playsToTheEnd() throws Exception {
        Screen screen = new Screen(open(ClientMatchSession.startLocal(
                LocalMatchSpec.solo(DECK, DECK, BotKind.SMART).withServerArgs(FAST))));
        playToEnd(List.of(screen));
        assertEquals(List.of(PieceAlignment.P1), screen.session.getControlledSeats());
    }

    @Test
    @RequiresAssets
    void hotseat_oneSessionPlaysBothSeats_toTheEnd() throws Exception {
        Screen screen = new Screen(open(ClientMatchSession.startLocal(
                LocalMatchSpec.twoHumans(PICK_DECK, PICK_DECK).withServerArgs(FAST))));
        playToEnd(List.of(screen));
        assertEquals(List.of(PieceAlignment.P1, PieceAlignment.P2), screen.session.getControlledSeats());
        assertTrue(screen.picksPrompted > 0, "Warp Mage's Displace should have gone through the pick prompt");
        assertTrue(screen.turnsSeen.contains(PieceAlignment.P1) && screen.turnsSeen.contains(PieceAlignment.P2),
                "both seats must have taken turns through the one session: " + screen.turnsSeen);
    }

    @Test
    @RequiresAssets
    void hostAndJoin_twoSessionsPlayTheSameChildProcessServer_toTheEnd() throws Exception {
        Screen host = new Screen(open(ClientMatchSession.host(
                LocalMatchSpec.twoHumans(DECK, DECK).withServerArgs(FAST), "127.0.0.1", 0)));
        // The host's ephemeral port is only known once its child server is up.
        long deadline = System.nanoTime() + 30_000_000_000L;
        while (host.session.getPort() <= 0) {
            host.session.poll();
            if (host.session.getStatus().isOver()) fail("host ended before listening: " + host.session.getStatus());
            if (System.nanoTime() - deadline > 0) fail("host never got a port; status=" + host.session.getStatus());
            Thread.sleep(10);
        }
        Screen guest = new Screen(open(ClientMatchSession.join("127.0.0.1", host.session.getPort(), SeatRequest.P2, "guest")));

        playToEnd(List.of(host, guest));
        assertEquals(List.of(PieceAlignment.P1), host.session.getControlledSeats());
        assertEquals(List.of(PieceAlignment.P2), guest.session.getControlledSeats());
        assertEquals(host.session.getWinner(), guest.session.getWinner(), "both screens must agree on the winner");
    }

    private ClientMatchSession open(ClientMatchSession session) {
        opened.add(session);
        return session;
    }

    /** Polls every screen (as Main does each frame) until each one's match is over. */
    private static void playToEnd(List<Screen> screens) throws InterruptedException {
        long deadline = System.nanoTime() + MATCH_DEADLINE_NANOS;
        while (true) {
            boolean allOver = true;
            for (Screen screen : screens) {
                screen.frame();
                allOver &= screen.session.getStatus().isOver();
            }
            if (allOver) break;
            if (System.nanoTime() - deadline > 0) {
                StringBuilder why = new StringBuilder("match never ended:");
                for (Screen s : screens) why.append(' ').append(s.session.getStatus()).append(" sent=").append(s.commandsSent);
                fail(why.toString());
            }
            Thread.sleep(5);
        }
        for (Screen screen : screens) screen.assertEndedCleanly();
    }

    /** One game screen: a session, the controller a BattleRoom would build over it, and a player. */
    private static final class Screen {
        final ClientMatchSession session;
        final ClientController controller;
        final AutoPlayer player = new AutoPlayer(AutoPlayer.Strategy.GREEDY, new Random(0));
        final List<PieceAlignment> victoriesPresented = new ArrayList<>();
        final List<PieceAlignment> turnsSeen = new ArrayList<>();
        int commandsSent;
        int picksPrompted;

        Screen(ClientMatchSession session) {
            this.session = session;
            this.controller = new ClientController(session::viewState, session, new ClickFirstCandidate(), victoriesPresented::add);
        }

        void frame() {
            session.poll();
            if (!session.getStatus().isReady() && !session.getStatus().isOver()) return;
            controller.update();
            for (PieceAlignment seat : session.getControlledSeats()) {
                ClientMatchState state = session.stateFor(seat);
                assertNull(state.getLastRejection(), seat + " had a command rejected: " + state.getLastRejection());
            }
            PieceAlignment current = session.currentPlayer();
            if (current != null && session.controls(current) && !turnsSeen.contains(current)) turnsSeen.add(current);
            // Picks belong to the controller's prompt; everything else to the player.
            if (session.pendingPick() == null && player.step(session)) commandsSent++;
        }

        void assertEndedCleanly() {
            assertEquals(ClientMatchSession.Phase.ENDED, session.getStatus().phase(), "status: " + session.getStatus());
            controller.update();
            assertEquals(1, victoriesPresented.size(), "the victory screen must be presented exactly once");
            assertEquals(session.getWinner(), victoriesPresented.get(0), "victory screen must show the server's winner");
            assertTrue(commandsSent > 0, "this screen should have played");
        }

        /** Stands in for LocalUiPickPrompt: "clicks" the first highlighted candidate at once. */
        private final class ClickFirstCandidate implements ClientController.PickPrompter {
            @Override
            public boolean show(PickRequest request, Consumer<Coord> onChosen, Runnable onCancelled) {
                picksPrompted++;
                onChosen.accept(request.candidates().get(0));
                return true;
            }

            @Override
            public void clear() {
            }
        }
    }
}
