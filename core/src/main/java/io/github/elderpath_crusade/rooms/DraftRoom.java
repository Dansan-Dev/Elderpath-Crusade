package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.audio.MusicPath;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game_objects.cards.CardFactory;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextPanel;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;

/**
 * Room for drafting cards before entering the game: the player starts with 2x Wolf and 2x Wolf Cub, then drafts
 * 4 cards, each from 3 random options. UI-014: every pick is its own screen (a fresh instance, like
 * {@link RunDraftRoom}) with a clear header ("Pick 2 of 4"), the three cards shown by the shared
 * {@link CardPicker} with space between them, and the deck so far as a panel on the left.
 */
public class DraftRoom extends Room {
    private static final int DRAFT_PICKS = 4;
    private static final int CARDS_PER_PICK = 3;
    private static final List<String> STARTING_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");

    private static final int DECK_PANEL_X = 30;
    private static final int DECK_PANEL_WIDTH = 250;
    private static final int DECK_PANEL_BOTTOM = 60;
    private static final int DECK_PANEL_TOP_CLEARANCE = 160;
    private static final int DECK_PANEL_PADDING = 20;

    private final List<String> draftedDeck;
    private final int picksDone;
    private final PieceAlignment draftingPlayer;
    private final boolean isLocalMultiplayer;
    // What happens to the finished deck is the caller's decision, not this room's — see DeckSourceSelectRoom, which
    // is the way "Local Multiplayer"/"Host Online" reach this room (the plain DraftRoom::get() entry point for Demo
    // mode still owns its own default).
    private final Consumer<List<String>> onComplete;

    private final Text header;
    private final Text subHeader;
    private final TextPanel deckPanel = new TextPanel();
    private final Text deckTitle;
    private final Text deckList;
    private final CardPicker picker;

    private DraftRoom(PieceAlignment player, boolean localMultiplayer, Consumer<List<String>> onComplete,
                      List<String> draftedSoFar, int picksDone) {
        super();
        GameContext.get().getMusicManager().playLoopingMusic(MusicPath.EVENING_HARMONY);
        this.draftingPlayer = player;
        this.isLocalMultiplayer = localMultiplayer;
        this.onComplete = onComplete;
        this.draftedDeck = List.copyOf(draftedSoFar);
        this.picksDone = picksDone;

        String who = localMultiplayer ? (player == PieceAlignment.P1 ? "Player 1: " : "Player 2: ") : "";
        header = new Text(who + "Draft Your Deck", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        subHeader = new Text("Pick " + (picksDone + 1) + " of " + DRAFT_PICKS + " - click the card you want",
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        deckTitle = new Text("Your deck (" + draftedDeck.size() + ")", FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        deckList = new Text(deckText(draftedDeck), FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.CAPTION);

        addContent(deckPanel); // first, so it draws behind the deck text
        addContent(header);
        addContent(subHeader);
        addContent(deckTitle);
        addContent(deckList);

        List<String> available = new ArrayList<>(CardFactory.getDraftableNames());
        Collections.shuffle(available);
        List<CardPicker.Option> options = new ArrayList<>();
        for (int i = 0; i < CARDS_PER_PICK && i < available.size(); i++) {
            String cardName = available.get(i);
            options.add(new CardPicker.Option(cardName, null, cardName, () -> onPicked(cardName)));
        }
        picker = new CardPicker(this::addContent, options);
        layoutContents();
    }

    private void onPicked(String cardName) {
        List<String> updated = new ArrayList<>(draftedDeck);
        updated.add(cardName);
        int done = picksDone + 1;
        if (done >= DRAFT_PICKS) {
            onComplete.accept(updated);
        } else {
            GameContext.get().getRoomManager().gotoRoom(
                    () -> new DraftRoom(draftingPlayer, isLocalMultiplayer, onComplete, updated, done));
        }
    }

    /** "2x Wolf" / "Crow" per distinct card, alphabetical. */
    private static String deckText(List<String> deck) {
        Map<String, Integer> counts = new TreeMap<>();
        for (String card : deck) counts.merge(card, 1, Integer::sum);
        List<String> lines = new ArrayList<>();
        counts.forEach((name, count) -> lines.add(count > 1 ? count + "x " + name : name));
        return String.join("\n", lines);
    }

    private void layoutContents() {
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        MenuLayout.centerHeader(header, 100);
        MenuLayout.centerHeader(subHeader, 150);

        int panelTop = screenHeight - DECK_PANEL_TOP_CLEARANCE;
        deckPanel.setRect(DECK_PANEL_X, DECK_PANEL_BOTTOM, DECK_PANEL_WIDTH, panelTop - DECK_PANEL_BOTTOM);
        int textX = DECK_PANEL_X + DECK_PANEL_PADDING;
        deckTitle.update();
        deckTitle.getBounds().setX(textX);
        deckTitle.getBounds().setY(panelTop - DECK_PANEL_PADDING - deckTitle.getBounds().getHeight());
        deckList.update();
        deckList.getBounds().setX(textX);
        deckList.getBounds().setY(deckTitle.getBounds().getY() - 14 - deckList.getBounds().getHeight());

        picker.layout(200);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static DraftRoom get() {
        return new DraftRoom(PieceAlignment.P1, false, DraftRoom::completeDemoDraft, STARTING_DECK, 0);
    }

    private static void completeDemoDraft(List<String> deck) {
        GameContext.get().getDeckManager().setDraftedDeck(PieceAlignment.P1, deck);
        GameContext.get().getRoomManager().gotoRoom(DemoRoom::get);
    }

    /**
     * Generic entry point used by {@link DeckSourceSelectRoom}'s "Draft a Deck" option — drafts for {@code player},
     * then hands the finished deck to {@code onComplete}. Storing it in {@code DeckManager} and picking the next room
     * is entirely the caller's job; this room doesn't know or care whether it's being used for Local Multiplayer,
     * Host Online, or anything else.
     */
    public static DraftRoom forPlayer(PieceAlignment player, boolean isLocalMultiplayer, Consumer<List<String>> onComplete) {
        return new DraftRoom(player, isLocalMultiplayer, onComplete, STARTING_DECK, 0);
    }
}
