package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.abilities.AbilityRelay;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Deck;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.managers.DeckManager;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.PlayerManager;
import io.github.elderpath_crusade.managers.SoundManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.multiplayer.EventBus;
import io.github.elderpath_crusade.multiplayer.GameEvent;
import io.github.elderpath_crusade.multiplayer.GameEventType;
import io.github.elderpath_crusade.tiles.MountainTile;
import io.github.elderpath_crusade.ui_objects.*;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.GameMode;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.Logger;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class LocalMatchRoom extends Room {
    // Guard against duplicate event logger registration within the same JVM session
    private static boolean LOGGER_REGISTERED = false;
    private final Board board;
    private final Hand handP1;
    private final Hand handP2;
    private final Deck deckP1;
    private final Deck deckP2;
    private final PassTurnButton passTurn;
    private final UIRenderable pauseMenuHint;
    private final Supplier<int[]> pauseMenuPos = () -> new int[]{20, SettingsManager.screenSize.getScreenHeight() - 40};
    private final int plot_width = 40;
    private final int plot_height = 40;

    // Store original hand positions for swapping
    private int p1HandBottomY;
    private int p2HandBottomY;

    private LocalMatchRoom() {
        super();

        // Play match music (looping)
        SoundManager.playLoopingMusic("Daniel_Game.mp3");

        // Reset turn and player state for a fresh LocalMatchRoom instance
        TurnManager.reset();
        // Set game mode to LOCAL_MATCH
        GameModeManager.setCurrent(GameMode.LOCAL_MATCH);

        board = new Board(0, 0, plot_width, plot_height, 7, 5);
        board.initializePlots();

        // Non-terrain pieces removed - board starts empty except for terrain
        board.addGamePieceToPos(4, 3, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(2, 1, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(5, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));
        board.addGamePieceToPos(1, 2, new MountainTile(0, 0, board.getPLOT_WIDTH(), board.getPLOT_HEIGHT()));

        addContent(board);

        int[] pauseMenuPos = this.pauseMenuPos.get();
        pauseMenuHint = new Text("ESC", FontType.SILKSCREEN, pauseMenuPos[0], pauseMenuPos[1], 1, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        addUI(pauseMenuHint);

        // Pass Turn button (mid-right) - will show "Start Turn" when waiting
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        passTurn = PassTurnButton.fromColor(
            Color.WHITE.cpy().mul(0.2f,0.2f,0.2f,1f),
            "Pass Turn",
            FontType.SILKSCREEN,
            (int)FontSize.BODY_MEDIUM.getSize(),
            screenW - 150, screenH/2 - 20,
            130, 40,
            2
        );
        passTurn.withTextColors(Color.WHITE, Color.WHITE, Color.WHITE);
        passTurn.withOnClick((e) -> handlePassTurnClick(), ClickableEffectData.getImmediate());
        addUI(passTurn);

        // P1 hand (bottom)
        handP1 = new Hand(
            SettingsManager.screenSize.getScreenCenter()[0],
            -80,
            125,
            200,
            0
        );
        handP1.setOwner(PieceAlignment.P1);
        addContent(handP1);
        p1HandBottomY = handP1.getBounds().getY();

        // P2 hand (top)
        handP2 = new Hand(
            SettingsManager.screenSize.getScreenCenter()[0],
            0,
            125,
            200,
            0
        );
        handP2.setBottomY(SettingsManager.screenSize.getScreenHeight() - handP2.getHeight());
        handP2.setOwner(PieceAlignment.P2);
        addContent(handP2);
        p2HandBottomY = handP2.getBounds().getY();

        // Initialize P1 deck from DeckManager
        List<Card> cardsP1 = new ArrayList<>();
        if (DeckManager.hasDraftedDeck(PieceAlignment.P1)) {
            List<java.util.function.Function<DeckManager.CardCreationParams, SummonCard>> draftedDeck = DeckManager.getDraftedDeck(PieceAlignment.P1);
            for (java.util.function.Function<DeckManager.CardCreationParams, SummonCard> cardCreator : draftedDeck) {
                DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                    board, PieceAlignment.P1, 0, 0, 125, 200, 0
                );
                cardsP1.add(cardCreator.apply(params));
            }
        }

        deckP1 = new Deck(
            cardsP1,
            0, 10,
            125, 200,
            1,
            SpriteBoxPos.BOTTOM_LEFT
        );
        deckP1.shuffle();
        deckP1.getBounds().setX(SettingsManager.screenSize.getScreenWidth() - deckP1.getWidth() - 10);
        deckP1.setOwner(PieceAlignment.P1);
        deckP1.setHand(handP1);
        addContent(deckP1);

        // Initialize P2 deck from DeckManager
        List<Card> cardsP2 = new ArrayList<>();
        if (DeckManager.hasDraftedDeck(PieceAlignment.P2)) {
            List<java.util.function.Function<DeckManager.CardCreationParams, SummonCard>> draftedDeck = DeckManager.getDraftedDeck(PieceAlignment.P2);
            for (java.util.function.Function<DeckManager.CardCreationParams, SummonCard> cardCreator : draftedDeck) {
                DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                    board, PieceAlignment.P2, 0, 0, 125, 200, 0
                );
                cardsP2.add(cardCreator.apply(params));
            }
        }

        deckP2 = new Deck(
            cardsP2,
            0, SettingsManager.screenSize.getScreenHeight() - 200,
            125, 200,
            1,
            SpriteBoxPos.BOTTOM_LEFT
        );
        deckP2.shuffle();
        deckP2.getBounds().setX(SettingsManager.screenSize.getScreenWidth() - deckP2.getWidth() - 10);
        deckP2.getBounds().setY(SettingsManager.screenSize.getScreenHeight() - deckP2.getHeight() - 10);
        deckP2.setOwner(PieceAlignment.P2);
        deckP2.setHand(handP2);
        addContent(deckP2);

        // Wire PlayerManager ownership
        PlayerManager.setHand(PieceAlignment.P1, handP1);
        PlayerManager.setDeck(PieceAlignment.P1, deckP1);
        PlayerManager.setHand(PieceAlignment.P2, handP2);
        PlayerManager.setDeck(PieceAlignment.P2, deckP2);

        // Register event listeners for turn changes
        EventBus.register(GameEventType.TURN_STARTED, this::onTurnStarted);
        EventBus.register(GameEventType.TURN_ENDED, this::onTurnEnded);

        // Start turn flow if not started yet
        TurnManager.startIfNeeded();
        // Ensure AbilityRelay is active so TriggeredAbilities receive global events
        AbilityRelay.startIfNeeded();

        // Add HUDs
        ManaHud manaHud = new ManaHud();
        addUI(manaHud);
        TurnHud turnHud = new TurnHud();
        addUI(turnHud);
        // Ability bubbles for actionable abilities
        addUI(new AbilityPopup());
        // Big hover preview on right side
        addUI(new CardPreviewPanel());

        // Optional: Register an all-events logger for debugging
        if (SettingsManager.debug.eventsLoggerInDemo) {
            if (!LOGGER_REGISTERED) {
                Consumer<GameEvent> eventLogger = (evt) -> {
                    Logger.log("LocalMatchRoom/Event", evt.getType() + " -> " + evt.getData());
                };
                for (GameEventType t : GameEventType.values()) {
                    EventBus.register(t, eventLogger);
                }
                LOGGER_REGISTERED = true;
            }
        }

        int[] board_size = board.getPixelSize();
        layoutBoard(board_size[0], board_size[1]);

        // Initially show P1's hand (face-up) since P1 starts
        showPlayerHand(PieceAlignment.P1);
        hideAllHands(); // Then immediately hide to start fresh
        showPlayerHand(TurnManager.getCurrentPlayer()); // Show current player's hand
    }

    private void handlePassTurnClick() {
        if (TurnManager.isWaitingForNextPlayer()) {
            // Start next player's turn
            TurnManager.startNextPlayerTurn();
            // UI will update via event listeners
        } else {
            // End current turn (enters waiting state)
            TurnManager.endTurn();
            // UI will update via event listeners
        }
    }

    private void onTurnStarted(GameEvent event) {
        PieceAlignment player = PieceAlignment.valueOf((String) event.getData().get("player"));
        // Update button text
        if (passTurn != null) {
            passTurn.updateButtonText();
        }
        // Swap hand positions if needed
        swapHandPositionsIfNeeded(player);
        // Show current player's hand (face-up)
        showPlayerHand(player);
    }

    private void onTurnEnded(GameEvent event) {
        PieceAlignment player = PieceAlignment.valueOf((String) event.getData().get("player"));
        // Update button text
        if (passTurn != null) {
            passTurn.updateButtonText();
        }
        // Hide all hands (face-down) when turn ends
        hideAllHands();
    }

    private void swapHandPositionsIfNeeded(PieceAlignment currentPlayer) {
        int screenH = SettingsManager.screenSize.getScreenHeight();
        int screenCenterX = SettingsManager.screenSize.getScreenCenter()[0];

        if (currentPlayer == PieceAlignment.P2) {
            // P2's turn: P1 hand to top, P2 hand to bottom
            handP1.setCenterX(screenCenterX);
            handP1.setBottomY(screenH - handP1.getHeight());
            handP1.updateBounds();
            handP2.setCenterX(screenCenterX);
            handP2.setBottomY(p1HandBottomY);
            handP2.updateBounds();
        } else {
            // P1's turn: P1 hand to bottom, P2 hand to top
            handP1.setCenterX(screenCenterX);
            handP1.setBottomY(p1HandBottomY);
            handP1.updateBounds();
            handP2.setCenterX(screenCenterX);
            handP2.setBottomY(screenH - handP2.getHeight());
            handP2.updateBounds();
        }
    }

    private void hideAllHands() {
        // Flip all cards face-down in both hands
        if (handP1 != null) {
            for (Card card : handP1.getCards()) {
                card.showBack();
            }
        }
        if (handP2 != null) {
            for (Card card : handP2.getCards()) {
                card.showBack();
            }
        }
    }

    private void showPlayerHand(PieceAlignment player) {
        Hand hand = (player == PieceAlignment.P1) ? handP1 : handP2;
        if (hand != null) {
            // Flip all cards face-up
            for (Card card : hand.getCards()) {
                card.showFront();
            }
        }
    }

    private void layoutBoard(int boardPixelWidth, int boardPixelHeight) {
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int boardCenteredX = screen_center[0] - boardPixelWidth / 2;
        int boardCenteredY = screen_center[1] - boardPixelHeight / 2;
        board.getBounds().setX(boardCenteredX);
        board.getBounds().setY(boardCenteredY);
        handP1.setCenterX(screen_center[0]);
        handP1.updateBounds();
        handP2.setCenterX(screen_center[0]);
        handP2.updateBounds();
    }

    @Override
    public void onScreenResize() {
        int boardPixelWidth = plot_width * 5;
        int boardPixelHeight = plot_height * 7;
        layoutBoard(boardPixelWidth, boardPixelHeight);

        int[] pauseMenuPos = this.pauseMenuPos.get();
        pauseMenuHint.getBounds().setX(pauseMenuPos[0]);
        pauseMenuHint.getBounds().setY(pauseMenuPos[1]);

        // Reposition UI elements and decks on resize
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

        // Update hand positions based on current player
        PieceAlignment currentPlayer = TurnManager.getCurrentPlayer();
        swapHandPositionsIfNeeded(currentPlayer);
    }

    public static LocalMatchRoom get() {
        return new LocalMatchRoom();
    }
}

