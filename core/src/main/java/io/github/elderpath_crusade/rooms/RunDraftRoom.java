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
 * shape as the existing (Demo/LocalMatch) {@code DraftRoom}'s pick-4-of-3 — but a plain
 * button-list UI like {@code DifficultySelectRoom}/{@code UpgradeOfferRoom} rather than
 * rendered {@code Card}s: this sandbox can't verify a GL-dependent draft screen any better than
 * a simpler one, and the existing {@code DraftRoom} is tightly coupled to Demo/LocalMatch's own
 * transition targets. Each pick goes through {@link RoomManager}'s own {@code gotoRoom} (a
 * fresh instance per round) so clickables are torn down and rebuilt for free, same as any other
 * room transition — no incremental UI bookkeeping needed.
 * Once the draft completes, starts the run and hands off to {@link ElderPickRoom} to choose the
 * first Elder.
 */
public class RunDraftRoom extends Room {
    static final List<String> BASE_DECK = List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub");
    private static final int DRAFT_PICKS = 4;
    private static final int CARDS_PER_PICK = 3;
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;

    private RunDraftRoom(RunDifficulty difficulty, List<String> draftedSoFar, int picksSoFar) {
        super();

        header = new Text("Draft Your Deck: " + picksSoFar + "/" + DRAFT_PICKS,
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        List<String> pool = new ArrayList<>(CardCatalog.getDraftableNames());
        Collections.shuffle(pool, new Random());
        for (int i = 0; i < CARDS_PER_PICK && i < pool.size(); i++) {
            String cardName = pool.get(i);
            addOption(cardName, () -> onPicked(difficulty, draftedSoFar, picksSoFar, cardName));
        }

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
        MenuLayout.centerHeader(header, 100);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static RunDraftRoom get(RunDifficulty difficulty) {
        return new RunDraftRoom(difficulty, BASE_DECK, 0);
    }
}
