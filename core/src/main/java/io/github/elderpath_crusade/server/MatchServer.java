package io.github.elderpath_crusade.server;

import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.utils.ImmutableArray;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.ecs.EntityUtils;
import io.github.elderpath_crusade.ecs.components.AlignmentComponent;
import io.github.elderpath_crusade.ecs.components.IdentityComponent;
import io.github.elderpath_crusade.ecs.components.PositionComponent;
import io.github.elderpath_crusade.ecs.components.StatsComponent;
import io.github.elderpath_crusade.ecs.components.TerrainComponent;
import io.github.elderpath_crusade.ecs.systems.PlayerSystem;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.events.*;
import io.github.elderpath_crusade.match.ActionResult;
import io.github.elderpath_crusade.match.Coord;
import io.github.elderpath_crusade.match.LegalActions;
import io.github.elderpath_crusade.match.LegalActionsCalculator;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import io.github.elderpath_crusade.match.PickRequest;
import io.github.elderpath_crusade.match.PickResponder;
import io.github.elderpath_crusade.match.PickResponse;
import io.github.elderpath_crusade.match.PickRouter;
import io.github.elderpath_crusade.multiplayer.net.NetworkCommand;
import io.github.elderpath_crusade.net.protocol.ClientMessage;
import io.github.elderpath_crusade.net.protocol.MatchView;
import io.github.elderpath_crusade.net.protocol.MessageCodec;
import io.github.elderpath_crusade.net.protocol.ProtocolException;
import io.github.elderpath_crusade.net.protocol.SeatRequest;
import io.github.elderpath_crusade.net.protocol.ServerMessage;
import io.github.elderpath_crusade.net.transport.ClientChannel;
import io.github.elderpath_crusade.net.transport.ServerTransport;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs one match authoritatively for its clients — the same class whether it's a solo game's
 * child-process server on localhost, a hosted match, or a remote dedicated server. One match
 * per process (it owns this process's GameContext); everything happens on the thread calling
 * tick().
 *
 * Seats: each side is a bot (MatchConfig.bots) or a human seat that one client connection
 * occupies at a time. The match begins once every human seat has been taken (immediately for
 * bot vs bot). A client that disconnects frees its connection but its seat stays reserved —
 * a client asking for that seat by name gets it back with a fresh Snapshot (unauthenticated
 * for now; reconnect tokens come with matchmaking). A human seat with no client connected
 * simply waits on its turn, and its picks are cancelled.
 *
 * Per tick: accept connections → handshake → each client's commands, executed through
 * GameServer as that client's seat (never trusting the payload) → the simulation (bots,
 * clock, ECS) → a StateSync if any piece's public state changed → each seat's LegalActions
 * if they changed → the tick's Acks. Events are relayed as they happen, filtered per
 * recipient by HiddenInfoFilter.
 */
public final class MatchServer {
    private static final List<Class<? extends GameEvent>> RELAYED = List.of(
            TurnStartedEvent.class, TurnEndedEvent.class, CardDrawnEvent.class, CardShuffledEvent.class,
            CardDiscardedEvent.class, CardPlayedEvent.class, PieceSpawnedEvent.class, PieceMovedEvent.class,
            PieceAttackedEvent.class, PieceDamagedEvent.class, PieceHealedEvent.class, PieceDiedEvent.class,
            PieceKilledEvent.class, ManaChangedEvent.class, ActionsResetEvent.class, ActionSpentEvent.class);

    private static final Family PIECES = Family.all(IdentityComponent.class, PositionComponent.class,
            AlignmentComponent.class, StatsComponent.class).get();
    private static final Family TERRAIN = Family.all(TerrainComponent.class, PositionComponent.class).get();

    /** A human side: whichever client currently occupies it, and what that side may see. */
    private static final class Seat {
        final PieceAlignment side;
        final HiddenInfoFilter filter;
        ClientChannel channel;
        boolean taken;
        /** Commands handled this tick, acked at its end (see sendAcks). */
        final List<Long> unacked = new ArrayList<>();
        /** The LegalActions this seat's client last got — null: send them next tick. */
        LegalActions lastLegal;

        Seat(PieceAlignment side) {
            this.side = side;
            this.filter = new HiddenInfoFilter(side);
        }
    }

    private final MatchConfig config;
    private final Map<PieceAlignment, Seat> seats = new EnumMap<>(PieceAlignment.class);
    private final List<ServerTransport> transports = new CopyOnWriteArrayList<>();
    private final List<ClientChannel> awaitingHello = new ArrayList<>();
    private boolean started;
    private boolean ended;
    private GameWonEvent pendingWin;
    private List<MatchView.PieceView> lastSync = List.of();

    /** Sets up the match (not started until its human seats are taken). mode must not be hotseat. */
    public MatchServer(MatchConfig config, ServerTransport... transports) {
        if (config.mode() == GameMode.LOCAL_MATCH) {
            throw new IllegalArgumentException("A match server runs turns back to back; hotseat handoff is a client concern");
        }
        this.config = config;
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            if (!config.bots().containsKey(side)) seats.put(side, new Seat(side));
        }
        for (ServerTransport t : transports) this.transports.add(t);

        MatchSetup.prepare(config);
        TypedEventBus bus = TypedEventBus.get();
        for (Class<? extends GameEvent> type : RELAYED) registerRelay(bus, type);
        bus.registerScoped("session", GameWonEvent.class, e -> pendingWin = e);
        PickRouter router = GameContext.get().getPickRouter();
        for (Seat seat : seats.values()) router.setRemoteResponder(seat.side, new SeatPickResponder(seat));
    }

    public void addTransport(ServerTransport transport) {
        transports.add(transport);
    }

    /** One server step: connections, commands, simulation, state sync. */
    public void tick(float deltaSeconds) {
        acceptConnections();
        handshakes();
        for (Seat seat : seats.values()) drainCommands(seat);
        if (!started && seats.values().stream().allMatch(s -> s.taken)) begin();
        if (started && !ended) {
            MatchSetup.tick(deltaSeconds);
            flushWin();
        }
        dropClosedChannels();
        syncIfChanged();
        syncLegalActions();
        sendAcks();
    }

    public boolean isStarted() {
        return started;
    }

    public boolean isEnded() {
        return ended;
    }

    public PieceAlignment getWinner() {
        return GameContext.get().getWinConditionManager().getWinner();
    }

    /** Whether a client currently occupies side's seat. */
    public boolean isConnected(PieceAlignment side) {
        Seat seat = seats.get(side);
        return seat != null && seat.channel != null;
    }

    /** The authoritative state as viewer may see it — what its replica must equal after it has polled. */
    public MatchView viewFor(PieceAlignment viewer) {
        GameContext ctx = GameContext.get();
        PieceAlignment opponent = viewer == PieceAlignment.P1 ? PieceAlignment.P2 : PieceAlignment.P1;
        Seat seat = seats.get(viewer);
        HiddenInfoFilter filter = seat != null ? seat.filter : new HiddenInfoFilter(viewer);
        PlayerSystem players = ctx.getEcsEngine().getSystem(PlayerSystem.class);
        PickRequest pick = ctx.getPickRouter().activeRequest(viewer);
        return new MatchView(
                viewer,
                pieceViews(),
                terrain(),
                ctx.getPlayerManager().get(viewer).hand.getCards(),
                filter.opponentHand(ctx.getPlayerManager().get(opponent).hand.getCards()),
                players.getMana(PieceAlignment.P1),
                players.getMana(PieceAlignment.P2),
                started ? ctx.getTurnManager().getCurrentPlayer() : null,
                started,
                pick != null ? pick.pickId() : null,
                getWinner());
    }

    /**
     * What side may do right now, exactly as this server would accept it — nothing before the
     * match begins or after it ends (bot seats included: LegalActionsCalculator is seat-agnostic).
     */
    public LegalActions legalActionsFor(PieceAlignment side) {
        if (!started || ended) return LegalActions.none(side);
        return LegalActionsCalculator.compute(side);
    }

    /** Closes every client connection and transport. */
    public void close() {
        for (Seat seat : seats.values()) {
            if (seat.channel != null) seat.channel.close();
            seat.channel = null;
        }
        for (ClientChannel c : awaitingHello) c.close();
        awaitingHello.clear();
        for (ServerTransport t : transports) t.close();
    }

    // --- connections & handshake ---

    private void acceptConnections() {
        for (ServerTransport transport : transports) {
            ClientChannel channel;
            while ((channel = transport.accept()) != null) awaitingHello.add(channel);
        }
    }

    private void handshakes() {
        Iterator<ClientChannel> it = awaitingHello.iterator();
        while (it.hasNext()) {
            ClientChannel channel = it.next();
            ClientMessage message;
            try {
                message = channel.poll();
            } catch (ProtocolException e) {
                refuse(channel, ServerMessage.Error.Code.BAD_MESSAGE, e.getMessage());
                it.remove();
                continue;
            }
            if (message == null) {
                if (!channel.isOpen()) it.remove();
                continue;
            }
            it.remove();
            if (!(message instanceof ClientMessage.Hello hello)) {
                refuse(channel, ServerMessage.Error.Code.NOT_SEATED, "Send Hello first");
            } else if (hello.protocolVersion() != MessageCodec.PROTOCOL_VERSION) {
                refuse(channel, ServerMessage.Error.Code.VERSION_MISMATCH,
                        "Server speaks protocol " + MessageCodec.PROTOCOL_VERSION + ", client " + hello.protocolVersion());
            } else {
                seat(channel, hello);
            }
        }
    }

    private void seat(ClientChannel channel, ClientMessage.Hello hello) {
        Seat seat = chooseSeat(hello.seat());
        if (seat == null) {
            boolean anyFree = seats.values().stream().anyMatch(s -> s.channel == null);
            refuse(channel, anyFree ? ServerMessage.Error.Code.SEAT_TAKEN : ServerMessage.Error.Code.MATCH_FULL,
                    "No free seat for " + hello.seat());
            return;
        }
        seat.channel = channel;
        seat.taken = true;
        seat.lastLegal = null; // a (re)joined client gets its LegalActions at the end of this tick
        Logger.log("MatchServer", (hello.playerName() == null ? "A client" : hello.playerName()) + " took " + seat.side);
        channel.send(new ServerMessage.Welcome(MessageCodec.PROTOCOL_VERSION, seat.side,
                config.rows(), config.cols(), config.terrain(), slotKinds()));
        channel.send(new ServerMessage.Snapshot(viewFor(seat.side)));
        PickRequest pending = GameContext.get().getPickRouter().activeRequest(seat.side);
        if (pending != null) channel.send(new ServerMessage.PickRequested(pending));
        if (ended) channel.send(new ServerMessage.MatchEnded(getWinner()));
    }

    /**
     * A free seat for the request: the named side if no client occupies it (a never-taken
     * seat, or a reserved one being reclaimed), or for ANY the first never-taken seat, then
     * the first unoccupied one.
     */
    private Seat chooseSeat(SeatRequest request) {
        if (request == SeatRequest.P1 || request == SeatRequest.P2) {
            Seat seat = seats.get(PieceAlignment.valueOf(request.name()));
            return seat != null && seat.channel == null ? seat : null;
        }
        for (Seat seat : seats.values()) if (!seat.taken) return seat;
        for (Seat seat : seats.values()) if (seat.channel == null) return seat;
        return null;
    }

    private Map<PieceAlignment, String> slotKinds() {
        Map<PieceAlignment, String> kinds = new EnumMap<>(PieceAlignment.class);
        for (PieceAlignment side : new PieceAlignment[]{PieceAlignment.P1, PieceAlignment.P2}) {
            BotKind bot = config.bots().get(side);
            kinds.put(side, bot == null ? "HUMAN" : "BOT_" + bot.name());
        }
        return kinds;
    }

    private static void refuse(ClientChannel channel, ServerMessage.Error.Code code, String message) {
        channel.send(new ServerMessage.Error(code, message));
        channel.close();
    }

    private void dropClosedChannels() {
        for (Seat seat : seats.values()) {
            if (seat.channel != null && !seat.channel.isOpen()) {
                Logger.log("MatchServer", seat.side + " disconnected — seat reserved");
                seat.channel = null;
                seat.lastLegal = null;
            }
        }
    }

    // --- commands ---

    private void drainCommands(Seat seat) {
        while (seat.channel != null) {
            ClientMessage message;
            try {
                message = seat.channel.poll();
            } catch (ProtocolException e) {
                seat.channel.send(new ServerMessage.Error(ServerMessage.Error.Code.BAD_MESSAGE, e.getMessage()));
                continue;
            }
            if (message == null) return;
            if (message instanceof ClientMessage.Leave) {
                Logger.log("MatchServer", seat.side + " left — seat reserved");
                seat.channel.close();
                seat.channel = null;
                seat.lastLegal = null;
            } else if (message instanceof ClientMessage.Command c) {
                seat.unacked.add(c.seq());
                ActionResult result = execute(seat.side, c.command());
                if (!result.isOk()) {
                    if (seat.channel != null) seat.channel.send(new ServerMessage.Rejected(c.seq(), result.reason()));
                } else if (c.command() instanceof NetworkCommand.AnswerPick answer && seat.channel != null) {
                    seat.channel.send(new ServerMessage.PickAnswered(answer.pickId()));
                }
                flushWin();
            } else {
                seat.channel.send(new ServerMessage.Error(ServerMessage.Error.Code.BAD_MESSAGE, "Already seated"));
            }
        }
    }

    /** Acks every command handled this tick — last, so each Ack follows everything its command caused. */
    private void sendAcks() {
        for (Seat seat : seats.values()) {
            if (seat.channel != null) {
                for (long seq : seat.unacked) seat.channel.send(new ServerMessage.Ack(seq));
            }
            seat.unacked.clear();
        }
    }

    private ActionResult execute(PieceAlignment actor, NetworkCommand command) {
        if (!started || ended) return ActionResult.rejected(ActionResult.Reason.NO_ACTIVE_MATCH);
        GameServer server = GameContext.get().getGameServer();
        if (command instanceof NetworkCommand.MovePlot c) {
            return server.movePlot(actor, c.srcRow(), c.srcCol(), c.dstRow(), c.dstCol());
        } else if (command instanceof NetworkCommand.PlaySummonCard c) {
            return server.playSummonCard(actor, c.instanceId(), c.row(), c.col());
        } else if (command instanceof NetworkCommand.PlaySpellCard c) {
            return server.playSpellCard(actor, c.instanceId(), c.targetRow(), c.targetCol());
        } else if (command instanceof NetworkCommand.EndTurn) {
            return server.endTurn(actor);
        } else if (command instanceof NetworkCommand.ActivateAbility c) {
            return server.activateAbility(actor, c.pieceId(), c.actionIndex());
        } else if (command instanceof NetworkCommand.AnswerPick c) {
            PickResponse response = c.cancelled()
                    ? PickResponse.cancelled(c.pickId())
                    : PickResponse.chose(c.pickId(), new Coord(c.row(), c.col()));
            return server.answerPick(actor, response);
        }
        return ActionResult.rejected(ActionResult.Reason.EXECUTION_FAILED);
    }

    // --- simulation & relay ---

    private void begin() {
        started = true;
        Logger.log("MatchServer", "All seats taken — match begins");
        MatchSetup.begin();
        flushWin();
    }

    private <T extends GameEvent> void registerRelay(TypedEventBus bus, Class<T> type) {
        bus.registerScoped("session", type, this::relay);
    }

    private void relay(GameEvent event) {
        for (Seat seat : seats.values()) {
            // Filter even with nobody connected, so a seat's card handles stay consistent for a rejoin.
            GameEvent visible = seat.filter.filter(event);
            if (seat.channel != null) seat.channel.send(new ServerMessage.Event(visible));
        }
    }

    /**
     * The win is announced after the step that caused it has been relayed: GameWonEvent is
     * emitted from inside the winning move's own PieceMovedEvent dispatch, before this
     * server's relay sees that move, so relaying it immediately would tell clients about the
     * win before the move.
     */
    private void flushWin() {
        if (pendingWin == null || ended) return;
        ended = true;
        GameContext.get().getPickRouter().cancelAll();
        for (Seat seat : seats.values()) {
            if (seat.channel == null) continue;
            seat.channel.send(new ServerMessage.Event(pendingWin));
            seat.channel.send(new ServerMessage.MatchEnded(pendingWin.winner()));
        }
        Logger.log("MatchServer", "Match over — " + pendingWin.winner() + " wins");
    }

    private void syncIfChanged() {
        List<MatchView.PieceView> pieces = pieceViews();
        if (pieces.equals(lastSync)) return;
        lastSync = pieces;
        for (Seat seat : seats.values()) {
            if (seat.channel != null) seat.channel.send(new ServerMessage.StateSync(pieces));
        }
    }

    /**
     * Recomputed every tick rather than on events: legality also changes inside the engine
     * update with no command or event of its own (auras, deaths), and a 7x5 board makes it cheap.
     */
    private void syncLegalActions() {
        for (Seat seat : seats.values()) {
            if (seat.channel == null) continue;
            LegalActions legal = legalActionsFor(seat.side);
            if (legal.equals(seat.lastLegal)) continue;
            seat.lastLegal = legal;
            seat.channel.send(new ServerMessage.LegalActionsUpdate(legal));
        }
    }

    private static List<MatchView.PieceView> pieceViews() {
        ImmutableArray<Entity> entities = GameContext.get().getEcsEngine().getEntitiesFor(PIECES);
        List<MatchView.PieceView> out = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            Entity e = entities.get(i);
            if (EntityUtils.isDead(e)) continue; // DeathSystem removes it on the next engine update
            out.add(new MatchView.PieceView(EntityUtils.getId(e), EntityUtils.getAlignment(e), EntityUtils.getName(e),
                    EntityUtils.getRow(e), EntityUtils.getCol(e), EntityUtils.getCurrentHealth(e),
                    EntityUtils.getMaxHealth(e), EntityUtils.getDamage(e), EntityUtils.getRemainingActions(e),
                    EntityUtils.isStunned(e)));
        }
        out.sort(Comparator.comparing(MatchView.PieceView::id));
        return out;
    }

    private static List<Coord> terrain() {
        ImmutableArray<Entity> entities = GameContext.get().getEcsEngine().getEntitiesFor(TERRAIN);
        List<Coord> out = new ArrayList<>(entities.size());
        for (int i = 0; i < entities.size(); i++) {
            PositionComponent pos = entities.get(i).getComponent(PositionComponent.class);
            out.add(new Coord(pos.row, pos.col));
        }
        out.sort(Comparator.comparingInt(Coord::row).thenComparingInt(Coord::col));
        return out;
    }

    /** Sends a human seat's picks to whichever client occupies it; with nobody connected, cancels them. */
    private static final class SeatPickResponder implements PickResponder {
        private final Seat seat;

        SeatPickResponder(Seat seat) {
            this.seat = seat;
        }

        @Override
        public void onPickRequested(PickRequest request, PickRouter router) {
            if (seat.channel != null) {
                seat.channel.send(new ServerMessage.PickRequested(request));
            } else {
                router.answer(seat.side, PickResponse.cancelled(request.pickId()));
            }
        }

        @Override
        public void onPickCancelled(PickRequest request) {
            if (seat.channel != null) seat.channel.send(new ServerMessage.PickCancelled(request.pickId()));
        }
    }
}
