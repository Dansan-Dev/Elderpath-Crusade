package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Drafts a fresh run's starting deck before the map is generated (ELDER-004) — replaces the old
 * fixed starting deck. {@link #BASE_DECK} plus {@value #DRAFT_PICKS} picks of
 * {@value #CARDS_PER_PICK} random {@link CardCatalog#getDraftableNames} options each, same
 * shape as the existing (Demo/LocalMatch) {@code DraftRoom}'s pick-4-of-3, with the options drawn as real cards
 * by the shared {@link CardPicker} (UI-011). Each pick goes through {@link RoomManager}'s own {@code gotoRoom} (a
 * fresh instance per round) so clickables are torn down and rebuilt for free, same as any other
 * room transition — no incremental UI bookkeeping needed.
 * Once the draft completes, starts the run and hands off to {@link ElderPickRoom} to choose the
 * first Elder.
 */
public class RunDraftRoom extends Room {
    static final List<String> BASE_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
    private static final int DRAFT_PICKS = 4;
    private static final int CARDS_PER_PICK = 3;

    private final Text header;
    private final CardPicker picker;

    private RunDraftRoom(RunDifficulty difficulty, List<String> draftedSoFar, int picksSoFar) {
        super();

        header = new Text("Draft Your Deck: " + picksSoFar + "/" + DRAFT_PICKS,
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        List<CardPicker.Option> options = new ArrayList<>();
        List<String> pool = new ArrayList<>(CardCatalog.getDraftableNames());
        Collections.shuffle(pool, new Random());
        for (int i = 0; i < CARDS_PER_PICK && i < pool.size(); i++) {
            String cardName = pool.get(i);
            options.add(new CardPicker.Option(cardName, null, cardName, () -> onPicked(difficulty, draftedSoFar, picksSoFar, cardName)));
        }
        picker = new CardPicker(this::addContent, options);

        layoutContents();
    }

    private static void onPicked(RunDifficulty difficulty, List<String> draftedSoFar, int picksSoFar, String cardName) {
        List<String> updated = new ArrayList<>(draftedSoFar);
        updated.add(cardName);
        int nextPick = picksSoFar + 1;
        if (nextPick >= DRAFT_PICKS) {
            GameContext.get().getRunManager().startRun(difficulty, updated);
            GameContext.get().getRoomManager().gotoRoom(() -> ElderPickRoom.get(false));
        } else {
            GameContext.get().getRoomManager().gotoRoom(() -> new RunDraftRoom(difficulty, updated, nextPick));
        }
    }

    private void layoutContents() {
        MenuLayout.centerHeader(header, 100);
        picker.layout(150);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static RunDraftRoom get(RunDifficulty difficulty) {
        return new RunDraftRoom(difficulty, BASE_DECK, 0);
    }
}
