package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.abilities.AbilityRelay;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.managers.*;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.tiles.MountainTile;
import io.github.elderpath_crusade.ui_objects.*;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;

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
    protected final Deck deckP1;
    protected final Deck deckP2;
    protected final PassTurnButton passTurn;
    protected final UIRenderable pauseMenuHint;
    protected final Supplier<int[]> pauseMenuPos = () -> new int[]{20, SettingsManager.screenSize.getScreenHeight() - 40};

    protected final int plot_width = 40;
    protected final int plot_height = 40;
    protected final int board_rows = 7;
    protected final int board_cols = 5;

    protected static boolean LOGGER_REGISTERED = false;

    public BattleRoom(GameMode mode) {
        super();

        // 1. Core State & Mode
        MusicManager.playLoopingMusic("Daniel_Game.mp3");
        TurnManager.reset();
        GameModeManager.setCurrent(mode);

        // 2. Board
        board = new Board(0, 0, plot_width, plot_height, board_rows, board_cols);
        board.initializePlots();
        addContent(board);

        // Common terrain setup
        board.addGamePieceToPos(4, 3, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(2, 1, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(5, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(1, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));

        // 3. UI: Pause Hint
        int[] pmPos = this.pauseMenuPos.get();
        pauseMenuHint = new Text("ESC", FontType.SILKSCREEN, pmPos[0], pmPos[1], 1, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        addUI(pauseMenuHint);

        // 4. UI: Pass Turn Button
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
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
        int centerX = SettingsManager.screenSize.getScreenCenter()[0];
        handP1 = new Hand(centerX, -80, 125, 200, 0);
        handP1.setOwner(PieceAlignment.P1);
        addContent(handP1);

        handP2 = new Hand(centerX, 0, 125, 200, 0);
        handP2.setBottomY(screenH - handP2.getHeight());
        handP2.setOwner(PieceAlignment.P2);
        addContent(handP2);

        // 6. Decks (concrete rooms will populate card lists and finalize deck config)
        deckP1 = createDeck(PieceAlignment.P1, handP1);
        deckP2 = createDeck(PieceAlignment.P2, handP2);
        addContent(deckP1);
        addContent(deckP2);

        // 7. Wiring & Startup
        PlayerManager.setHand(PieceAlignment.P1, handP1);
        PlayerManager.setDeck(PieceAlignment.P1, deckP1);
        PlayerManager.setHand(PieceAlignment.P2, handP2);
        PlayerManager.setDeck(PieceAlignment.P2, deckP2);

        TurnManager.startIfNeeded();
        AbilityRelay.startIfNeeded();

        // 8. HUDs
        addUI(new ManaHud());
        addUI(new TurnHud());
        addUI(new AbilityPopup());
        addUI(new CardPreviewPanel());

        // 9. Logger
        if (SettingsManager.debug.eventsLoggerInDemo && !LOGGER_REGISTERED) {
            registerEventsLogger();
        }

        // 10. Layout
        layoutBoard();
    }

    protected abstract void onPassTurnClicked();

    protected abstract Deck createDeck(PieceAlignment alignment, Hand hand);

    private void registerEventsLogger() {
        String logTag = this.getClass().getSimpleName() + "/Event";
        Consumer<GameEvent> eventLogger = (evt) -> {
            Logger.log(logTag, evt.getType() + " -> " + evt.getData());
        };
        for (GameEventType t : GameEventType.values()) {
            EventBus.register(t, eventLogger);
        }
        LOGGER_REGISTERED = true;
    }

    protected void layoutBoard() {
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
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

        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        if (passTurn != null) {
            passTurn.getBounds().setX(screenW - 150);
            passTurn.getBounds().setY(screenH / 2 - 20);
        }

        if (deckP1 != null) {
            deckP1.getBounds().setX(screenW - deckP1.getWidth() - 10);
            deckP1.getBounds().setY(10);
        }

        if (deckP2 != null) {
            deckP2.getBounds().setX(screenW - deckP2.getWidth() - 10);
            deckP2.getBounds().setY(screenH - deckP2.getHeight() - 10);
        }
    }
}
