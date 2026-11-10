package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.collection.DeckCollectionEntry;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
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
import java.util.stream.Collectors;

/**
 * Browses the player's persistent deck collection (Sprint 64) — every won run's deck/Elders/
 * relics plus its metadata, newest first. Basic by design (confirmed scope for this pass): one
 * summary block per entry, no per-card detail or filtering — a card inspector is a natural
 * follow-up, not this screen's job. Scroll layout copied from {@link TextInfoRoom}'s proven
 * up/down-button + visibility-clipping pattern rather than reinvented.
 */
public class CollectionRoom extends Room {
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    private final Text header;
    private final List<Text> entryBlocks = new ArrayList<>();
    private final Button backButton;
    private final Button upButton;
    private final Button downButton;
    private int scrollOffset = 0;
    private static final int SCROLL_STEP = 200;

    private CollectionRoom() {
        super();

        header = new Text("Deck Collection", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        List<DeckCollectionEntry> entries = new ArrayList<>(GameContext.get().getCollectionManager().all());
        entries.sort(Comparator.comparingLong(DeckCollectionEntry::timestampMillis).reversed());

        int maxWrapWidth = (int) (GameContext.get().getSettingsManager().screenSize.getScreenWidth() * 0.8f);
        if (entries.isEmpty()) {
            Text empty = new Text("No winning runs recorded yet.", FontType.SILKSCREEN, 0, 0, 0,
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

        backButton.getBounds().setX(20);
        backButton.getBounds().setY(screenHeight - 70);

        upButton.getBounds().setX(screenWidth - 80);
        upButton.getBounds().setY(screenHeight - 150);

        downButton.getBounds().setX(screenWidth - 80);
        downButton.getBounds().setY(100);

        int totalHeight = 0;
        for (Text block : entryBlocks) totalHeight += block.getBounds().getHeight() + 35;

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
        return new CollectionRoom();
    }
}
