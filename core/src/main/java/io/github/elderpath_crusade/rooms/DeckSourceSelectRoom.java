package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;
import java.util.function.Consumer;

/**
 * Asks how one player's deck for the upcoming match should be built: draft one from scratch
 * ({@link DraftRoom}), or pick an already-won deck from the persistent collection
 * ({@link CollectionDeckPickRoom}, Sprint 64's {@code CollectionManager}). Neither of those two
 * rooms decides what happens once a deck is chosen — {@code onDeckChosen} does — so this one
 * room serves every multiplayer entry point:
 * <ul>
 *   <li>{@link #startLocalMultiplayer()} — both seats are local, so P1 and P2 each get their own
 *   trip through this room in turn, before {@link LocalMatchRoom} starts. Since there is only
 *   one collection on this machine, choosing "Collection" shows the exact same list to both
 *   players (the user's own call, 2026-11-12) — nothing here needs a per-player collection.</li>
 *   <li>{@link #startHostOnline()} — only the host's own seat picks here. A remote guest's deck
 *   still can't be carried across the network before the match server even starts without a
 *   lobby (see {@link OnlineMatchRoom}'s class doc, FEAT-008) — that side keeps its existing
 *   fixed starter deck, unchanged by this feature.</li>
 * </ul>
 * "Join Online" has no entry here at all: the joining client never builds a {@code
 * LocalMatchSpec} (it only connects to an already-running server), so there's nothing on this
 * client to choose a deck for — the same architectural gap {@link OnlineMatchRoom} already
 * documents, not something this room can work around.
 */
public class DeckSourceSelectRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;
    private final Button backButton;

    private DeckSourceSelectRoom(PieceAlignment player, boolean isLocalMultiplayer, Consumer<List<String>> onDeckChosen) {
        super();

        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        String title = isLocalMultiplayer
                ? (player == PieceAlignment.P1 ? "Player 1 — Choose Your Deck" : "Player 2 — Choose Your Deck")
                : "Choose Your Deck";
        header = new Text(title, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        addOption("Draft a Deck", () -> GameContext.get().getRoomManager().gotoRoom(
                () -> DraftRoom.forPlayer(player, isLocalMultiplayer, onDeckChosen)));
        addOption("Choose from Collection", () -> GameContext.get().getRoomManager().gotoRoom(
                () -> CollectionDeckPickRoom.get(player, isLocalMultiplayer, onDeckChosen)));

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, BUTTON_HEIGHT, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(GameModeRoom::get), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        layoutContents();
    }

    private void addOption(String label, Runnable onClick) {
        Button button = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), label, FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, 0)
                .withOnClick((e) -> onClick.run(), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        buttonList.addButton(button);
        addContent(button);
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];

        MenuLayout.centerHeader(header, 100);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenterX, screenCenterY);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(screenCenterY - (buttonList.getRenderables().size() * BUTTON_SPACING) / 2 - 60);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get(PieceAlignment player, boolean isLocalMultiplayer, Consumer<List<String>> onDeckChosen) {
        return new DeckSourceSelectRoom(player, isLocalMultiplayer, onDeckChosen);
    }

    /** Local Multiplayer's whole flow: P1 picks a deck source, then P2, then into LocalMatchRoom. */
    public static Room startLocalMultiplayer() {
        return get(PieceAlignment.P1, true, deck -> onLocalMultiplayerDeckChosen(PieceAlignment.P1, deck));
    }

    private static void onLocalMultiplayerDeckChosen(PieceAlignment player, List<String> deck) {
        GameContext.get().getDeckManager().setDraftedDeck(player, deck);
        if (player == PieceAlignment.P1) {
            GameContext.get().getRoomManager().gotoRoom(() -> get(PieceAlignment.P2, true,
                    d -> onLocalMultiplayerDeckChosen(PieceAlignment.P2, d)));
        } else {
            GameContext.get().getRoomManager().gotoRoom(LocalMatchRoom::get);
        }
    }

    /** Host Online: only the host's own seat picks here — see the class doc for why the guest's can't yet. */
    public static Room startHostOnline() {
        return get(PieceAlignment.P1, false, DeckSourceSelectRoom::onHostDeckChosen);
    }

    private static void onHostDeckChosen(List<String> deck) {
        GameContext.get().getDeckManager().setDraftedDeck(PieceAlignment.P1, deck);
        GameContext.get().getRoomManager().gotoRoom(OnlineMatchRoom::host);
    }
}
