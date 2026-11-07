package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientController;
import io.github.elderpath_crusade.client.ClientMatchSession;
import io.github.elderpath_crusade.client.ReplicaViewSync;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.ecs.systems.GridIndexSystem;
import io.github.elderpath_crusade.events.CardDiscardedEvent;
import io.github.elderpath_crusade.events.CardDrawnEvent;
import io.github.elderpath_crusade.events.CardPlayedEvent;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.interfaces.Renderable;
import io.github.elderpath_crusade.interfaces.Updatable;
import io.github.elderpath_crusade.match.CardInstance;
import io.github.elderpath_crusade.rendering.PieceSpriteAttacher;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import com.badlogic.gdx.graphics.Color;

import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game_objects.cards.HandSync;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.*;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;

import java.util.List;
import java.util.function.Supplier;

/**
 * Shared base class for rooms that feature a tactical battle (Demo, LocalMatch, OnlineMatch).
 * Builds its view entirely from a {@link ClientMatchSession} already connected (or connecting)
 * to a match server — see {@code plans/sprint-54-plan.md} ARCH-028. Handles common UI layout,
 * board initialization, and HUD elements; the session/decks/bots that got the match started are
 * the subclass's concern, resolved before it calls {@code super(mode, session)}.
 */
public abstract class BattleRoom extends Room {
    protected final ClientMatchSession session;
    protected final ClientController controller;
    protected final Board board;
    protected final Hand handP1;
    protected final Hand handP2;
    protected final HandSync handSyncP1;
    protected final HandSync handSyncP2;
    protected final Deck deckP1;
    protected final Deck deckP2;
    protected final PassTurnButton passTurn;
    protected final UIRenderable pauseMenuHint;
    protected final Supplier<int[]> pauseMenuPos = () -> new int[]{20, GameContext.get().getSettingsManager().screenSize.getScreenHeight() - 40};

    protected final int plot_width = 40;
    protected final int plot_height = 40;

    private static final int HAND_BOTTOM_Y = -80;

    protected BattleRoom(GameMode mode, ClientMatchSession session) {
        super();
        this.session = session;

        // 0. A previous match's view entities (pieces, terrain) would otherwise outlive this
        // room — a fresh ReplicaViewSync starts with an empty id map, so it has no way to know
        // they're stale and never despawns them. Clear the shared client Engine before building
        // the new one, same as MatchSetup.prepare() does server-side between matches.
        GameContext.get().getEcsEngine().removeAllEntities();
        GameContext.get().getEcsEngine().getSystem(GridIndexSystem.class).clear();

        // 1. Wire this session into the context Main polls every frame, and build the
        // controller that turns its state + LegalActions into gating/commands/picks/victory.
        GameContext.get().getGameModeManager().setCurrent(mode);
        ReplicaViewSync viewSync = new ReplicaViewSync();
        controller = new ClientController(session::viewState, session,
                ClientController.localUiPickPrompter(), VictoryTransition::show);
        GameContext.get().setClientMatch(session, controller, viewSync);
        GameContext.get().getMusicManager().playLoopingMusic("Daniel_Game.mp3");

        // 2. The loading room upstream already waited for this seat's Welcome (rows/cols/
        // terrain known); pump once more and build the view entities + grid before the board.
        session.poll();
        var state = session.viewState();
        if (state != null) viewSync.sync(state);

        // 3. Board view over the replica's grid; sprites for its entities (terrain now, pieces as they spawn)
        board = new Board(viewSync.getGrid(), 0, 0, plot_width, plot_height);
        board.initializePlots();
        GameContext.get().setActiveBoard(board);
        addContent(board);
        PieceSpriteAttacher.install(GameContext.get().getEcsEngine(), board);

        // 4. UI: Pause Hint
        int[] pmPos = this.pauseMenuPos.get();
        pauseMenuHint = new Text("ESC", FontType.SILKSCREEN, pmPos[0], pmPos[1], 1, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        addUI(pauseMenuHint);

        // 5. UI: Pass Turn Button
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        passTurn = PassTurnButton.fromColor(
            Color.WHITE.cpy().mul(0.2f, 0.2f, 0.2f, 1f),
            "Pass Turn",
            FontType.SILKSCREEN,
            (int) FontSize.BODY_MEDIUM.getSize(),
            screenW - 150, screenH / 2 - 20,
            130, 40,
            2
        );
        passTurn.withTextColors(Color.WHITE, Color.WHITE, Color.WHITE);
        passTurn.withOnClick((e) -> onPassTurnClicked(), ClickableEffectData.getImmediate());
        addUI(passTurn);

        // 6. Hands
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        handP1 = new Hand(centerX, HAND_BOTTOM_Y, 125, 200, 0);
        handP1.setOwner(PieceAlignment.P1);
        addContent(handP1);

        handP2 = new Hand(centerX, 0, 125, 200, 0);
        handP2.setBottomY(screenH - handP2.getHeight());
        handP2.setOwner(PieceAlignment.P2);
        addContent(handP2);

        // 7. Hand views mirror each player's hand state from the card events the replica
        // delivers. One seat's connection already sees every public card event (its own hand
        // by name, the opponent's by hidden handle) — both HandSyncs listen to it and each
        // filters to its own owner, same as the old bindToEventBus() did on the local bus.
        handSyncP1 = new HandSync(handP1);
        handSyncP2 = new HandSync(handP2);
        PieceAlignment primarySeat = session.getControlledSeats().isEmpty()
                ? null : session.getControlledSeats().get(0);
        var primaryState = primarySeat != null ? session.stateFor(primarySeat) : null;
        if (primaryState != null) {
            // Cards already drawn (e.g. this seat's opening hand) arrived before this listener
            // existed and are gone from the event stream — seed from the replica's own record.
            HandSync ownSync = primaryState.getSeat() == PieceAlignment.P1 ? handSyncP1 : handSyncP2;
            HandSync opponentSync = primaryState.getSeat() == PieceAlignment.P1 ? handSyncP2 : handSyncP1;
            for (CardInstance card : primaryState.getOwnHand()) ownSync.addCard(card);
            for (String handle : primaryState.getOpponentHandIds()) opponentSync.addHiddenCard(handle);
            primaryState.setEventListener(this::routeCardEvent);
        }

        // 8. Deck views (the decks themselves are pure state, owned by the match server)
        deckP1 = createDeckView(PieceAlignment.P1);
        deckP2 = createDeckView(PieceAlignment.P2);
        addContent(deckP1);
        addContent(deckP2);

        // 9. HUDs
        addUI(new ManaHud());
        addUI(new TurnHud());
        addUI(new AbilityPopup());
        addUI(new CardPreviewPanel());

        // 10. Re-applies perspective (and drops a half-made selection) whenever the current
        // player changes — polled every frame since there's no local TurnStartedEvent anymore.
        addContent(new PerspectiveUpdater());

        // 11. Layout, oriented for this screen's viewer (hotseat follows the current player)
        layoutBoard();
        applyPerspective();
    }

    private void routeCardEvent(GameEvent e) {
        if (e instanceof CardDrawnEvent ev) {
            handSyncP1.onCardDrawn(ev);
            handSyncP2.onCardDrawn(ev);
        } else if (e instanceof CardPlayedEvent ev) {
            handSyncP1.onCardPlayed(ev);
            handSyncP2.onCardPlayed(ev);
        } else if (e instanceof CardDiscardedEvent ev) {
            handSyncP1.onCardDiscarded(ev);
            handSyncP2.onCardDiscarded(ev);
        }
    }

    protected abstract void onPassTurnClicked();

    /** Called once per frame when the current player changes, after applyPerspective(). */
    protected void onTurnChanged(PieceAlignment newPlayer) {
    }

    /**
     * The side this screen is oriented for: its home row, hand and deck sit at the bottom. The
     * side this screen controls (vs bot: the human's side; online: its own side), P1 if it
     * controls both or neither (bot vs bot). Hotseat overrides this to follow the current player.
     */
    protected PieceAlignment viewerAlignment() {
        List<PieceAlignment> controlled = session.getControlledSeats();
        if (controlled.contains(PieceAlignment.P1)) return PieceAlignment.P1;
        if (controlled.contains(PieceAlignment.P2)) return PieceAlignment.P2;
        return PieceAlignment.P1;
    }

    /** Flips the board view and places hands/decks so the viewer's side is at the bottom. */
    protected void applyPerspective() {
        PieceAlignment viewer = viewerAlignment();
        if ((viewer == PieceAlignment.P2) != board.isFlipped()) board.flipRows();

        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        placeSide(handP1, deckP1, viewer == PieceAlignment.P1, screenH, centerX);
        placeSide(handP2, deckP2, viewer == PieceAlignment.P2, screenH, centerX);
    }

    private void placeSide(Hand hand, Deck deck, boolean atBottom, int screenH, int centerX) {
        hand.setBottomY(atBottom ? HAND_BOTTOM_Y : screenH - hand.getHeight());
        hand.setCenterX(centerX);
        hand.updateBounds();
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        deck.getBounds().setX(screenW - deck.getWidth() - 10);
        deck.getBounds().setY(atBottom ? 10 : screenH - deck.getHeight() - 10);
    }

    private Deck createDeckView(PieceAlignment alignment) {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        Deck deck = new Deck(0, 0, 125, 200, 1, SpriteBoxPos.BOTTOM_LEFT);
        deck.getBounds().setX(screenW - deck.getWidth() - 10);
        deck.getBounds().setY(alignment == PieceAlignment.P1 ? 10 : screenH - deck.getHeight() - 10);
        deck.setOwner(alignment);
        return deck;
    }

    protected void layoutBoard() {
        int[] screen_center = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int[] board_size = board.getPixelSize();
        int boardCenteredX = screen_center[0] - board_size[0] / 2;
        int boardCenteredY = screen_center[1] - board_size[1] / 2;
        board.getBounds().setX(boardCenteredX);
        board.getBounds().setY(boardCenteredY);

        handP1.setCenterX(screen_center[0]);
        handP1.updateBounds();
        handP2.setCenterX(screen_center[0]);
        handP2.updateBounds();
    }

    @Override
    public void onScreenResize() {
        layoutBoard();

        int[] pmPos = this.pauseMenuPos.get();
        pauseMenuHint.getBounds().setX(pmPos[0]);
        pauseMenuHint.getBounds().setY(pmPos[1]);

        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        applyPerspective();

        if (passTurn != null) {
            passTurn.getBounds().setX(screenW - 150);
            passTurn.getBounds().setY(screenH / 2 - 20);
        }
    }

    /** Renders nothing — exists only to receive per-frame update() calls from GraphicsManager. */
    private final class PerspectiveUpdater extends LowestOrderTexture implements Renderable, Updatable {
        private PieceAlignment lastPlayer;

        PerspectiveUpdater() {
            setBounds(new Box(0, 0, 0, 0));
        }

        @Override
        public List<Integer> getZs() {
            return List.of();
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        }

        @Override
        public void render(SpriteBatch batch, int zLevel, boolean isPaused, int x, int y) {
        }

        @Override
        public void update(float delta) {
            PieceAlignment current = session.currentPlayer();
            if (current == null || current == lastPlayer) return;
            lastPlayer = current;
            if (!session.controls(current)) {
                GameContext.get().getInteractionManager().cancelSelection();
            }
            applyPerspective();
            onTurnChanged(current);
        }
    }
}
