package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.Timer;
import io.github.elderpath_crusade.cards.*;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.cards.Card;
import io.github.elderpath_crusade.game_objects.cards.Hand;
import io.github.elderpath_crusade.game_objects.cards.SummonCard;
import io.github.elderpath_crusade.game_objects.cards.DraftCard;
import io.github.elderpath_crusade.managers.DeckManager;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.managers.RoomManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.SoundManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;
import io.github.elderpath_crusade.utils.ColorSettings;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Room for drafting cards before entering the game.
 * Player starts with 2x Wolf and 2x WolfCub, then drafts 4 cards from random options.
 */
public class DraftRoom extends Room {
    private static final int DRAFT_PICKS = 4;
    private static final int CARDS_PER_PICK = 3;
    private static final float PICK_DELAY = 0.15f; // 150ms

    // All card types that can be drafted (excluding Wolf and WolfCub from starting deck)
    private static final List<CardType> DRAFTABLE_CARDS = Arrays.asList(
        new CardType("Rogue", (params) -> new RogueCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Fairy", (params) -> new FairyCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Wind Spirit", (params) -> new WindSpiritCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Big Toad", (params) -> new BigToadCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Sniper", (params) -> new SniperCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Barbarian", (params) -> new BarbarianCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("King", (params) -> new KingCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Charger", (params) -> new ChargerCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Crossbowman", (params) -> new CrossbowmanCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Skeleton Bomber", (params) -> new SkeletonBomberCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Warp Mage", (params) -> new WarpMageCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Commander", (params) -> new CommanderCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Hero", (params) -> new HeroCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Storm Mage", (params) -> new StormMageCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Rifleman", (params) -> new RiflemanCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Crow", (params) -> new CrowCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Shockling", (params) -> new ShocklingCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z))
    );

    private static class CardType {
        final String name;
        final Function<DeckManager.CardCreationParams, SummonCard> creator;

        CardType(String name, Function<DeckManager.CardCreationParams, SummonCard> creator) {
            this.name = name;
            this.creator = creator;
        }
    }

    // Starting deck cards
    private static final List<CardType> STARTING_DECK = Arrays.asList(
        new CardType("Wolf", (params) -> new WolfCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Wolf", (params) -> new WolfCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Wolf Cub", (params) -> new WolfCubCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z)),
        new CardType("Wolf Cub", (params) -> new WolfCubCard(params.board, params.alignment, params.x, params.y, params.width, params.height, params.z))
    );

    // Draft state
    private List<CardType> draftedDeck;
    private int currentPick;
    private List<Card> currentOptions;
    private Hand draftOptionsHand;
    private Board dummyBoard;
    private Random rng;

    // Multiplayer support
    private final PieceAlignment draftingPlayer;
    private final boolean isLocalMultiplayer;

    // UI elements
    private Text progressionText;
    private Text deckPreviewText;
    private boolean isProcessingPick;

    private DraftRoom(PieceAlignment player, boolean localMultiplayer) {
        super();
        
        // Play menu music
        SoundManager.playLoopingMusic("Evening_Harmony.mp3");
        
        this.draftingPlayer = player;
        this.isLocalMultiplayer = localMultiplayer;
        initializeDraft();
    }

    private DraftRoom() {
        this(PieceAlignment.P1, false);
    }

    private void initializeDraft() {
        // Create dummy board for card creation (not rendered, positioned off-screen)
        dummyBoard = new Board(-1000, -1000, 40, 40, 7, 5);
        dummyBoard.initializePlots();
        // Ensure it's NOT added to room content

        // Initialize starting deck
        draftedDeck = new ArrayList<>(STARTING_DECK);
        currentPick = 0;
        currentOptions = new ArrayList<>();
        rng = new Random();

        // Create UI elements
        String progressionPrefix = isLocalMultiplayer 
            ? (draftingPlayer == PieceAlignment.P1 ? "Player 1 Draft: " : "Player 2 Draft: ")
            : "";
        progressionText = new Text(progressionPrefix + "0/4", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(progressionText);

        deckPreviewText = new Text("", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.BODY_MEDIUM);
        addContent(deckPreviewText);

        // Create hand for draft options
        int[] screenCenter = SettingsManager.screenSize.getScreenCenter();
        int cardWidth = 187;  // 1.5x larger for better visibility during drafting
        int cardHeight = 300;  // 1.5x larger for better visibility during drafting
        draftOptionsHand = new Hand(screenCenter[0], screenCenter[1], cardWidth, cardHeight, 0);
        addContent(draftOptionsHand);

        isProcessingPick = false;

        // Layout UI
        layoutContents();

        // Update deck preview
        updateDeckPreview();

        // Show first pick
        showNextDraftPick();
    }

    private void layoutContents() {
        int[] screenCenter = SettingsManager.screenSize.getScreenCenter();
        int screenHeight = SettingsManager.screenSize.getScreenHeight();
        int screenWidth = SettingsManager.screenSize.getScreenWidth();

        // Progression text at top center
        MenuLayout.centerHeader(progressionText, 100);

        // Deck preview on right side - position it clearly visible
        // Fixed top position relative to screen height (top stays fixed as text expands downward)
        int previewWidth = 300;
        int previewX = screenWidth - previewWidth - 50; // Right side with margin
        int fixedTopY = (screenHeight * 2 / 3) - 100; // Fixed top Y position
        deckPreviewText.getBounds().setX(previewX);
        deckPreviewText.getBounds().setWidth(previewWidth);
        // Update to calculate text bounds first
        deckPreviewText.update();
        // Set Y position so that top stays fixed (Y is bottom, so bottom = top - height)
        int bottomY = fixedTopY - deckPreviewText.getBounds().getHeight();
        deckPreviewText.getBounds().setY(bottomY);

        // Draft options hand centered in middle
        int cardWidth = 187;  // 1.5x larger for better visibility during drafting
        int cardHeight = 300;  // 1.5x larger for better visibility during drafting
        draftOptionsHand.setCenterX(screenCenter[0]);
        draftOptionsHand.setBottomY(screenCenter[1] - 50);
        // Update hand bounds to reposition cards
        draftOptionsHand.updateBounds();
    }

    private void showNextDraftPick() {
        if (currentPick >= DRAFT_PICKS) {
            // Draft complete, transition to DemoRoom
            transitionToDemoRoom();
            return;
        }

        // Update progression text
        String progressionPrefix = isLocalMultiplayer 
            ? (draftingPlayer == PieceAlignment.P1 ? "Player 1 Draft: " : "Player 2 Draft: ")
            : "";
        progressionText.setText(progressionPrefix + currentPick + "/" + DRAFT_PICKS);
        progressionText.update();

        // Generate 3 unique random cards
        List<CardType> availableCards = new ArrayList<>(DRAFTABLE_CARDS);
        Collections.shuffle(availableCards, rng);

        currentOptions.clear();
        draftOptionsHand.getCards().forEach(card -> {
            draftOptionsHand.removeCard(card);
            InteractionManager.removeClickable(card);
        });

        for (int i = 0; i < CARDS_PER_PICK && i < availableCards.size(); i++) {
            CardType cardType = availableCards.get(i);
            DeckManager.CardCreationParams params = new DeckManager.CardCreationParams(
                dummyBoard, PieceAlignment.P1, 0, 0, 187, 300, 0  // 1.5x larger for better visibility during drafting
            );
            SummonCard realCard = cardType.creator.apply(params);
            realCard.showFront(); // Make sure card is face up
            
            // Wrap in DraftCard to bypass mana checks
            // Use array reference to allow referencing card in lambda before it's fully initialized
            final CardType finalCardType = cardType;
            final DraftCard[] cardRef = new DraftCard[1];
            DraftCard card = new DraftCard(realCard, (e) -> {
                if (!isProcessingPick && cardRef[0] != null) {
                    onCardSelected(cardRef[0], finalCardType);
                }
            });
            cardRef[0] = card; // Assign after creation
            card.showFront(); // Make sure card is face up
            
            currentOptions.add(card);
            draftOptionsHand.addCard(card);
        }

        isProcessingPick = false;
    }

    private void onCardSelected(Card selectedCard, CardType cardType) {
        if (isProcessingPick) return;
        isProcessingPick = true;

        // Add selected card to drafted deck
        draftedDeck.add(cardType);

        // Update deck preview immediately
        updateDeckPreview();

        // Clear current options
        currentOptions.forEach(card -> {
            draftOptionsHand.removeCard(card);
            InteractionManager.removeClickable(card);
        });
        currentOptions.clear();

        // Note: currentPick will be incremented in showNextDraftPick()
        // Don't increment here to avoid double increment

        // Delay before showing next pick
        Timer.schedule(new Timer.Task() {
            @Override
            public void run() {
                currentPick++;
                showNextDraftPick();
            }
        }, PICK_DELAY);
    }

    private void updateDeckPreview() {
        // Count cards by name
        Map<String, Long> cardCounts = draftedDeck.stream()
            .collect(Collectors.groupingBy(
                cardType -> cardType.name,
                Collectors.counting()
            ));

        // Format as "Nx card_name" or "card_name" for singles
        List<String> formattedCards = cardCounts.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(entry -> {
                String name = entry.getKey();
                long count = entry.getValue();
                return count > 1 ? count + "x " + name : name;
            })
            .collect(Collectors.toList());

        String previewText = "Deck:\n" + String.join("\n", formattedCards);
        deckPreviewText.setText(previewText);
        
        // Calculate fixed top position (always relative to screen height)
        int screenHeight = SettingsManager.screenSize.getScreenHeight();
        int screenWidth = SettingsManager.screenSize.getScreenWidth();
        int fixedTopY = (screenHeight * 2 / 3) - 100; // Fixed top Y position
        int previewWidth = 300;
        int previewX = screenWidth - previewWidth - 50; // Right side with margin
        
        // Set X and width before update
        deckPreviewText.getBounds().setX(previewX);
        deckPreviewText.getBounds().setWidth(previewWidth);
        
        // Update to recalculate text bounds (height will grow)
        deckPreviewText.update();
        
        // Set Y position so that top stays fixed (Y is bottom, so bottom = top - height)
        int bottomY = fixedTopY - deckPreviewText.getBounds().getHeight();
        deckPreviewText.getBounds().setY(bottomY);
    }

    private void transitionToDemoRoom() {
        // Convert CardType list to Function list for DeckManager
        List<Function<DeckManager.CardCreationParams, SummonCard>> cardCreators = new ArrayList<>();
        for (CardType cardType : draftedDeck) {
            cardCreators.add(cardType.creator);
        }

        if (isLocalMultiplayer) {
            // Store drafted deck in DeckManager for current drafting player
            DeckManager.setDraftedDeck(draftingPlayer, cardCreators);

            // If P1 just finished, transition to P2 draft
            if (draftingPlayer == PieceAlignment.P1) {
                RoomManager.gotoRoom(() -> new DraftRoom(PieceAlignment.P2, true));
            } else {
                // P2 finished, transition to LocalMatchRoom
                RoomManager.gotoRoom(LocalMatchRoom::get);
            }
        } else {
            // Store drafted deck in DeckManager (legacy method for P1)
            DeckManager.setDraftedDeck(cardCreators);

            // Transition to DemoRoom
            RoomManager.gotoRoom(DemoRoom::get);
        }
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static DraftRoom get() {
        return new DraftRoom();
    }

    public static DraftRoom getForLocalMultiplayer(PieceAlignment player) {
        return new DraftRoom(player, true);
    }
}

