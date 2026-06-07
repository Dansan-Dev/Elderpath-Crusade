package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;

import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.events.TypedEventBus;
import io.github.elderpath_crusade.events.GameEvent;
import io.github.elderpath_crusade.supers.Room;
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
    protected final Supplier<int[]> pauseMenuPos = () -> new int[]{20, GameContext.get().getSettingsManager().screenSize.getScreenHeight() - 40};

    protected final int plot_width = 40;
    protected final int plot_height = 40;
    protected final int board_rows = 7;
    protected final int board_cols = 5;

    protected static boolean LOGGER_REGISTERED = false;

    public BattleRoom(GameMode mode) {
        super();

        // 0. Reset previous game session: clear ECS entities and session-scoped event listeners
        GameContext.get().getEcsEngine().removeAllEntities();
        TypedEventBus.get().clearGroup("session");
        GameContext.get().getWinConditionManager().resetSession();
        GameContext.get().getBotManager().resetSession();

        // 1. Core State & Mode
        GameContext.get().getMusicManager().playLoopingMusic("Daniel_Game.mp3");
        GameContext.get().getTurnManager().reset();
        GameContext.get().getGameModeManager().setCurrent(mode);

        // 2. Board
        board = new Board(0, 0, plot_width, plot_height, board_rows, board_cols);
        board.initializePlots();
        GameContext.get().setActiveBoard(board);
        addContent(board);

        // Terrain setup — place blocking terrain entities
        placeTerrain(board, 2, 1);
        placeTerrain(board, 2, 3);

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
        GameContext.get().getPlayerManager().setHand(PieceAlignment.P1, handP1);
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P1, deckP1);
        GameContext.get().getPlayerManager().setHand(PieceAlignment.P2, handP2);
        GameContext.get().getPlayerManager().setDeck(PieceAlignment.P2, deckP2);

        GameContext.get().getTurnManager().startIfNeeded();


        // 8. HUDs
        addUI(new ManaHud());
        addUI(new TurnHud());
        addUI(new AbilityPopup());
        addUI(new CardPreviewPanel());

        // 9. Logger
        if (GameContext.get().getSettingsManager().debug.eventsLoggerInDemo && !LOGGER_REGISTERED) {
            registerEventsLogger();
        }

        // 10. Layout
        layoutBoard();
    }

    protected abstract void onPassTurnClicked();

    protected abstract Deck createDeck(PieceAlignment alignment, Hand hand);

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

        // Reposition hands
        if (handP1 != null) {
            handP1.setCenterX(centerX);
            handP1.updateBounds();
        }
        if (handP2 != null) {
            handP2.setBottomY(screenH - handP2.getHeight());
            handP2.setCenterX(centerX);
            handP2.updateBounds();
        }

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

    private void placeTerrain(Board board, int row, int col) {
        com.badlogic.ashley.core.Engine engine = GameContext.get().getEcsEngine();
        com.badlogic.ashley.core.Entity terrain = engine.createEntity();
        terrain.add(new io.github.elderpath_crusade.ecs.components.PositionComponent().set(row, col));
        terrain.add(new io.github.elderpath_crusade.ecs.components.TerrainComponent());
        terrain.add(new io.github.elderpath_crusade.ecs.components.SpriteComponent()
                .set("MountainTerrain")
                .setRenderable(new io.github.elderpath_crusade.characters.sprites.terrain_sprites.MountainSprite(
                        0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT())));
        engine.addEntity(terrain);
        board.addEntityToPos(row, col, terrain, "terrain_" + row + "_" + col);
    }
}
