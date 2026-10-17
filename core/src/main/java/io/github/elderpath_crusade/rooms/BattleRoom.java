package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.bot.BotKind;
import io.github.elderpath_crusade.events.GameWonEvent;
import io.github.elderpath_crusade.match.BoardGrid;
import io.github.elderpath_crusade.match.MatchConfig;
import io.github.elderpath_crusade.match.MatchSetup;
import io.github.elderpath_crusade.rendering.PieceSpriteAttacher;
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
import io.github.elderpath_crusade.events.TurnStartedEvent;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.*;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Shared base class for rooms that feature a tactical battle (Demo and LocalMatch).
 * Handles common UI layout, board initialization, and HUD elements.
 */
public abstract class BattleRoom extends Room {
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

    protected static boolean LOGGER_REGISTERED = false;
    private static final int HAND_BOTTOM_Y = -80;

    public BattleRoom(GameMode mode) {
        super();

        // 0. Simulation: reset the previous match and set up this one headlessly (board grid,
        // terrain, decks, bot slots). The first turn starts at the end, once the views below
        // have bound to the session's events.
        GameContext.get().getMusicManager().playLoopingMusic("Daniel_Game.mp3");
        MatchConfig config = MatchConfig.standard(mode,
                deckCardNames(PieceAlignment.P1), deckCardNames(PieceAlignment.P2), null, botSlots());
        BoardGrid grid = MatchSetup.prepare(config);
        TypedEventBus.get().registerScoped("session", GameWonEvent.class, e -> VictoryTransition.show(e.winner()));

        // 1. Board view over the match's grid; sprites for its entities (terrain now, pieces as they spawn)
        board = new Board(grid, 0, 0, plot_width, plot_height);
        board.initializePlots();
        GameContext.get().setActiveBoard(board);
        addContent(board);
        PieceSpriteAttacher.install(GameContext.get().getEcsEngine(), board);

        // 3. UI: Pause Hint
        int[] pmPos = this.pauseMenuPos.get();
        pauseMenuHint = new Text("ESC", FontType.SILKSCREEN, pmPos[0], pmPos[1], 1, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        addUI(pauseMenuHint);

        // 4. UI: Pass Turn Button
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

        // 5. Hands
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];
        handP1 = new Hand(centerX, HAND_BOTTOM_Y, 125, 200, 0);
        handP1.setOwner(PieceAlignment.P1);
        addContent(handP1);

        handP2 = new Hand(centerX, 0, 125, 200, 0);
        handP2.setBottomY(screenH - handP2.getHeight());
        handP2.setOwner(PieceAlignment.P2);
        addContent(handP2);

        // 6. Hand views mirror each player's hand state from the card events — from the
        // local simulation here, or (online guest) relayed by ReplicaEventApplier instead.
        handSyncP1 = new HandSync(handP1);
        handSyncP2 = new HandSync(handP2);
        if (!mirrorsRemoteMatch()) {
            handSyncP1.bindToEventBus();
            handSyncP2.bindToEventBus();
        }

        // 7. Deck views (the decks themselves are pure state, built by MatchSetup)
        deckP1 = createDeckView(PieceAlignment.P1);
        deckP2 = createDeckView(PieceAlignment.P2);
        addContent(deckP1);
        addContent(deckP2);

        // 8. Startup
        MatchSetup.begin();

        // 9. HUDs
        addUI(new ManaHud());
        addUI(new TurnHud());
        addUI(new AbilityPopup());
        addUI(new CardPreviewPanel());

        // 10. Logger
        if (GameContext.get().getSettingsManager().debug.eventsLoggerInDemo && !LOGGER_REGISTERED) {
            registerEventsLogger();
        }

        // 11. Layout, oriented for this screen's viewer (re-applied whenever the turn changes,
        // since in hotseat the viewer is whoever's turn it is)
        TypedEventBus.get().registerScoped("session", TurnStartedEvent.class, e -> applyPerspective());
        layoutBoard();
        applyPerspective();
    }

    protected abstract void onPassTurnClicked();

    /** The card names (CardCatalog display names) that make up this player's deck for the match. */
    protected abstract List<String> deckCardNames(PieceAlignment alignment);

    /**
     * Whether this room only mirrors a match simulated elsewhere (the online guest): its hand
     * views are then driven by relayed events rather than the local event bus.
     */
    protected boolean mirrorsRemoteMatch() {
        return false;
    }

    /** The match's bot-controlled slots — none by default (all human). */
    protected Map<PieceAlignment, BotKind> botSlots() {
        return Map.of();
    }

    /**
     * The side this screen is oriented for: its home row, hand and deck sit at the bottom.
     * P1 by default (vs bot); hotseat follows the current player, an online client its own side.
     */
    protected PieceAlignment viewerAlignment() {
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

    private void registerEventsLogger() {
        String logTag = this.getClass().getSimpleName() + "/Event";
        java.util.function.Consumer<GameEvent> eventLogger = (evt) -> {
            Logger.log(logTag, evt.toString());
        };
        TypedEventBus bus = TypedEventBus.get();
        bus.register(io.github.elderpath_crusade.events.TurnStartedEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.TurnEndedEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.PieceSpawnedEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.PieceMovedEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.PieceAttackedEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.PieceDiedEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.ActionSpentEvent.class, eventLogger::accept);
        bus.register(io.github.elderpath_crusade.events.CardPlayedEvent.class, eventLogger::accept);
        LOGGER_REGISTERED = true;
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
        int centerX = GameContext.get().getSettingsManager().screenSize.getScreenCenter()[0];

        applyPerspective();

        if (passTurn != null) {
            passTurn.getBounds().setX(screenW - 150);
            passTurn.getBounds().setY(screenH / 2 - 20);
        }
    }
}
