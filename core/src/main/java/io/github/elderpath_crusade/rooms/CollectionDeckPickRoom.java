package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.collection.DeckCollectionEntry;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * The "Collection" half of {@link DeckSourceSelectRoom}: lets a player pick one of their
 * persistent collection's saved decks (Sprint 64's {@code CollectionManager}) instead of
 * drafting. Same scroll/summary layout as {@link CollectionRoom} (which only browses, with no
 * way to act on an entry), with one addition — each entry gets a "Select" button. Picking one
 * hands that deck's card names to {@code onDeckChosen} and this room is done; what happens next
 * (store it, which room comes after) is entirely the caller's decision, so the exact same room
 * serves Local Multiplayer (run once per player) and Host Online (run once, for the host) alike.
 */
public class CollectionDeckPickRoom extends Room {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());
    private static final int SCROLL_STEP = 200;
    private static final int SELECT_BUTTON_WIDTH = 90;
    private static final int SELECT_BUTTON_HEIGHT = 40;
    private static final int ROW_GAP = 35;

    private final Text header;
    private final List<Text> entryBlocks = new ArrayList<>();
    private final List<Button> selectButtons = new ArrayList<>();
    private final Button backButton;
    private final Button upButton;
    private final Button downButton;
    private int scrollOffset = 0;

    private CollectionDeckPickRoom(PieceAlignment player, boolean isLocalMultiplayer, Consumer<List<String>> onDeckChosen) {
        super();

        String title = isLocalMultiplayer
                ? (player == PieceAlignment.P1 ? "Player 1 — Choose a Deck" : "Player 2 — Choose a Deck")
                : "Choose a Deck";
        header = new Text(title, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        List<DeckCollectionEntry> entries = new ArrayList<>(GameContext.get().getCollectionManager().all());
        entries.sort(Comparator.comparingLong(DeckCollectionEntry::timestampMillis).reversed());

        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int entryLeft = 60;
        int maxWrapWidth = (int) (screenWidth * 0.55f);
        int selectX = entryLeft + maxWrapWidth + 30;

        if (entries.isEmpty()) {
            Text empty = new Text("No decks in your collection yet — win a roguelike run to add one, "
                    + "or go back and draft a deck instead.", FontType.SILKSCREEN, 0, 0, 0,
                    ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM).withWrapWidth(maxWrapWidth);
            entryBlocks.add(empty);
            addContent(empty);
        } else {
            for (DeckCollectionEntry entry : entries) {
                Text block = new Text(summarize(entry), FontType.SILKSCREEN, 0, 0, 0,
                        ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.CAPTION).withWrapWidth(maxWrapWidth);
                entryBlocks.add(block);
                addContent(block);

                List<String> deckNames = cardNames(entry.deck());
                Button select = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Select", FontType.SILKSCREEN,
                                FontSize.CAPTION.getSize(), 0, 0, SELECT_BUTTON_WIDTH, SELECT_BUTTON_HEIGHT, 0)
                        .withOnClick((e) -> onDeckChosen.accept(deckNames), ClickableEffectData.getImmediate())
                        .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                        .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                        .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
                selectButtons.add(select);
                addContent(select);
            }
        }

        backButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 50, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(
                                () -> DeckSourceSelectRoom.get(player, isLocalMultiplayer, onDeckChosen)),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        upButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "^", FontType.SILKSCREEN,
                        FontSize.TITLE_MEDIUM.getSize(), 0, 0, 50, 50, 0)
                .withOnClick((e) -> {
                    scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
                    layoutContents();
                }, ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        addContent(upButton);

        downButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "v", FontType.SILKSCREEN,
                        FontSize.TITLE_MEDIUM.getSize(), 0, 0, 50, 50, 0)
                .withOnClick((e) -> {
                    scrollOffset += SCROLL_STEP;
                    layoutContents();
                }, ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        addContent(downButton);

        layoutContents(entryLeft, selectX);
    }

    private static List<String> cardNames(List<RunCardInstance> deck) {
        return deck.stream().map(RunCardInstance::cardName).collect(Collectors.toList());
    }

    /** Same summary format as {@link CollectionRoom} — kept independent on purpose, since that
     *  room is read-only and shouldn't gain a dependency on this one just to share a formatter. */
    private static String summarize(DeckCollectionEntry entry) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis()));
        String duration = formatDuration(entry.runCompletionTimeMillis());
        StringBuilder out = new StringBuilder();
        out.append("Won ").append(timestamp)
                .append("  —  Difficulty ").append(entry.difficultyLevel())
                .append("  —  ").append(duration);
        out.append("\nDeck: ").append(String.join(", ", cardNames(entry.deck())));
        if (!entry.elders().isEmpty()) {
            out.append("\nElders: ").append(entry.elders().stream()
                    .map(RunCardInstance::cardName).collect(Collectors.joining(", ")));
        }
        if (!entry.relicIds().isEmpty()) out.append("\nRelics: ").append(String.join(", ", entry.relicIds()));
        return out.toString();
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void layoutContents() {
        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int entryLeft = 60;
        int maxWrapWidth = (int) (screenWidth * 0.55f);
        int selectX = entryLeft + maxWrapWidth + 30;
        layoutContents(entryLeft, selectX);
    }

    private void layoutContents(int entryLeft, int selectX) {
        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        MenuLayout.centerHeader(header, 100);

        backButton.getBounds().setX(20);
        backButton.getBounds().setY(screenHeight - 70);

        upButton.getBounds().setX(screenWidth - 80);
        upButton.getBounds().setY(screenHeight - 150);

        downButton.getBounds().setX(screenWidth - 80);
        downButton.getBounds().setY(100);

        int totalHeight = 0;
        for (Text block : entryBlocks) totalHeight += block.getBounds().getHeight() + ROW_GAP;

        int availableSpace = (screenHeight - 200) - 100;
        int maxScroll = Math.max(0, totalHeight - availableSpace);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        Color primary = ColorSettings.BUTTON_PRIMARY.getColor().cpy();
        Color dimmed = primary.cpy().mul(1, 1, 1, 0.3f);
        upButton.setDisabled(scrollOffset <= 0);
        upButton.setBackgroundColor(upButton.isDisabled() ? dimmed : primary);
        downButton.setDisabled(scrollOffset >= maxScroll);
        downButton.setBackgroundColor(downButton.isDisabled() ? dimmed : primary);

        int clipTop = screenHeight - 160;
        int clipBottom = 110;
        int currentY = screenHeight - 200 + scrollOffset;
        for (int i = 0; i < entryBlocks.size(); i++) {
            Text block = entryBlocks.get(i);
            int compHeight = block.getBounds().getHeight();
            int compY = currentY - compHeight;
            block.getBounds().setX(entryLeft);
            block.getBounds().setY(compY);

            boolean offscreen = compY + compHeight > clipTop || compY < clipBottom;
            if (offscreen) block.getBounds().setX(-2000);

            if (i < selectButtons.size()) {
                Button select = selectButtons.get(i);
                int selectY = compY + (compHeight - SELECT_BUTTON_HEIGHT) / 2;
                select.getBounds().setX(offscreen ? -2000 : selectX);
                select.getBounds().setY(selectY);
            }

            currentY -= (compHeight + ROW_GAP);
        }
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get(PieceAlignment player, boolean isLocalMultiplayer, Consumer<List<String>> onDeckChosen) {
        return new CollectionDeckPickRoom(player, isLocalMultiplayer, onDeckChosen);
    }
}
