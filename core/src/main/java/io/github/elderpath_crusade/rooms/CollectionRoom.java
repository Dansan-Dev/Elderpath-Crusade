package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.collection.DeckCollectionEntry;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TextPanel;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Browses the player's persistent deck collection (Sprint 64) — every won run's deck/Elders/
 * relics plus its metadata, newest first. One summary block per entry, no per-card detail (a
 * card inspector is a natural follow-up, not this screen's job) — but filterable by difficulty
 * level (D2, FEAT-015) since that's the one dimension a player is likely to want to narrow down
 * by once they have more than a couple of winning runs recorded.
 *
 * <p>Filtering re-enters the room via {@link #get(int)} rather than mutating this instance's
 * content in place — {@code Room} has no API to remove already-shown renderables, only
 * {@code RoomManager.gotoRoom}'s full clear-and-rebuild (see {@code elderpath-rooms.md}), so a
 * filter change is just another room transition, same as any other button. Scroll layout
 * copied from {@link TextInfoRoom}'s proven up/down-button + visibility-clipping pattern rather
 * than reinvented.
 */
public class CollectionRoom extends Room {
    /** Framed backdrop behind the scrolling text (added first so it draws behind it). */
    private final TextPanel panel = new TextPanel();
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /** Sentinel for {@link #get(int)}/{@link #difficultyFilter}: no filter, show every entry. */
    private static final int NO_FILTER = -1;

    private static final int FILTER_ROW_TOP_OFFSET = 165;
    private static final int FILTER_BUTTON_WIDTH = 60;
    private static final int FILTER_BUTTON_HEIGHT = 36;
    private static final int FILTER_BUTTON_SPACING = 10;
    private static final int LIST_TOP_SHIFT = 60;

    private final Text header;
    private final List<Text> entryBlocks = new ArrayList<>();
    private final Button backButton;
    private final Button upButton;
    private final Button downButton;
    private final List<Button> filterButtons = new ArrayList<>();
    private int scrollOffset = 0;
    private static final int SCROLL_STEP = 200;

    private CollectionRoom(int difficultyFilter) {
        super();
        addContent(panel);

        header = new Text("Deck Collection", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        addFilterButton("All", NO_FILTER, difficultyFilter);
        for (int level = RunDifficulty.MIN_LEVEL; level <= RunDifficulty.MAX_LEVEL; level++) {
            addFilterButton(String.valueOf(level), level, difficultyFilter);
        }

        List<DeckCollectionEntry> entries = new ArrayList<>(GameContext.get().getCollectionManager().all());
        if (difficultyFilter != NO_FILTER) {
            entries.removeIf(entry -> entry.difficultyLevel() != difficultyFilter);
        }
        entries.sort(Comparator.comparingLong(DeckCollectionEntry::timestampMillis).reversed());

        int maxWrapWidth = (int) (GameContext.get().getSettingsManager().screenSize.getScreenWidth() * 0.8f);
        if (entries.isEmpty()) {
            String emptyMessage = difficultyFilter == NO_FILTER
                    ? "No winning runs recorded yet."
                    : "No winning runs recorded at this difficulty.";
            Text empty = new Text(emptyMessage, FontType.SILKSCREEN, 0, 0, 0,
                    ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM).withWrapWidth(maxWrapWidth);
            entryBlocks.add(empty);
            addContent(empty);
        } else {
            for (DeckCollectionEntry entry : entries) {
                Text block = new Text(summarize(entry), FontType.SILKSCREEN, 0, 0, 0,
                        ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.CAPTION).withWrapWidth(maxWrapWidth);
                entryBlocks.add(block);
                addContent(block);
            }
        }

        backButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 50, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get),
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

        layoutContents();
    }

    /** "Won 2026-11-10 14:22 — Difficulty 3 — 12:34\nDeck: Wolf, Wolf, ...\nElders: ...\nRelics: ..." */
    private static String summarize(DeckCollectionEntry entry) {
        String timestamp = TIMESTAMP_FORMAT.format(Instant.ofEpochMilli(entry.timestampMillis()));
        String duration = formatDuration(entry.runCompletionTimeMillis());
        StringBuilder out = new StringBuilder();
        out.append("Won ").append(timestamp)
                .append("  —  Difficulty ").append(entry.difficultyLevel())
                .append("  —  ").append(duration);
        out.append("\nDeck: ").append(cardNames(entry.deck()));
        if (!entry.elders().isEmpty()) out.append("\nElders: ").append(cardNames(entry.elders()));
        if (!entry.relicIds().isEmpty()) out.append("\nRelics: ").append(String.join(", ", entry.relicIds()));
        return out.toString();
    }

    private static String cardNames(List<RunCardInstance> cards) {
        return cards.stream().map(RunCardInstance::cardName).collect(Collectors.joining(", "));
    }

    /** One difficulty-filter pill; disabled + dimmed when it's the filter already applied (same
     *  "disabled means already in that state" convention the up/down scroll buttons use). */
    private void addFilterButton(String label, int value, int currentFilter) {
        boolean selected = value == currentFilter;
        Color primary = ColorSettings.BUTTON_PRIMARY.getColor().cpy();
        Color dimmed = primary.cpy().mul(1, 1, 1, 0.3f);
        Button button = Button
                .fromColor(selected ? dimmed : primary, label, FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, FILTER_BUTTON_WIDTH, FILTER_BUTTON_HEIGHT, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(() -> CollectionRoom.get(value)),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        button.setDisabled(selected);
        filterButtons.add(button);
        addContent(button);
    }

    private static String formatDuration(long millis) {
        long totalSeconds = millis / 1000;
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        MenuLayout.centerHeader(header, 100);

        int filterTotalWidth = filterButtons.size() * FILTER_BUTTON_WIDTH
                + (filterButtons.size() - 1) * FILTER_BUTTON_SPACING;
        int filterStartX = screenCenterX - filterTotalWidth / 2;
        int filterY = screenHeight - FILTER_ROW_TOP_OFFSET;
        for (int i = 0; i < filterButtons.size(); i++) {
            Button filterButton = filterButtons.get(i);
            filterButton.getBounds().setX(filterStartX + i * (FILTER_BUTTON_WIDTH + FILTER_BUTTON_SPACING));
            filterButton.getBounds().setY(filterY);
        }

        backButton.getBounds().setX(20);
        backButton.getBounds().setY(screenHeight - 70);

        upButton.getBounds().setX(screenWidth - 80);
        upButton.getBounds().setY(screenHeight - 150);

        downButton.getBounds().setX(screenWidth - 80);
        downButton.getBounds().setY(100);

        int totalHeight = 0;
        for (Text block : entryBlocks) totalHeight += block.getBounds().getHeight() + 35;

        int availableSpace = (screenHeight - 200 - LIST_TOP_SHIFT) - 100;
        int maxScroll = Math.max(0, totalHeight - availableSpace);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        Color primary = ColorSettings.BUTTON_PRIMARY.getColor().cpy();
        Color dimmed = primary.cpy().mul(1, 1, 1, 0.3f);
        upButton.setDisabled(scrollOffset <= 0);
        upButton.setBackgroundColor(upButton.isDisabled() ? dimmed : primary);
        downButton.setDisabled(scrollOffset >= maxScroll);
        downButton.setBackgroundColor(downButton.isDisabled() ? dimmed : primary);

        int clipTop = screenHeight - 160 - LIST_TOP_SHIFT;
        int clipBottom = 110;
        int panelWidth = (int) (GameContext.get().getSettingsManager().screenSize.getScreenWidth() * 0.8f) + 64;
        panel.setRect(GameContext.get().getSettingsManager().screenSize.getScreenWidth() / 2 - panelWidth / 2, clipBottom - 16, panelWidth, clipTop - clipBottom + 32);
        int currentY = screenHeight - 200 - LIST_TOP_SHIFT + scrollOffset;
        for (Text block : entryBlocks) {
            block.getBounds().setX(screenCenterX - block.getBounds().getWidth() / 2);
            int compHeight = block.getBounds().getHeight();
            int compY = currentY - compHeight;
            block.getBounds().setY(compY);

            if (compY + compHeight > clipTop || compY < clipBottom) {
                block.getBounds().setX(-2000);
            }
            currentY -= (compHeight + 35);
        }
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static CollectionRoom get() {
        return new CollectionRoom(NO_FILTER);
    }

    /** @param difficultyFilter a run difficulty level (1..{@link RunDifficulty#MAX_LEVEL}), or
     *                          {@link #NO_FILTER} to show every entry. */
    public static CollectionRoom get(int difficultyFilter) {
        return new CollectionRoom(difficultyFilter);
    }
}
