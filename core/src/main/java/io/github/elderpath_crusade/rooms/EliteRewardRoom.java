package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;
import java.util.Random;

/**
 * An Elite's reward (ELITE-001, Phase 3.6) — replaces the Elder pick entirely for an Elite win
 * (only run-start and a non-final floor's boss grant an Elder pick now, see {@code
 * ElderPickRoom}). The user's words: the kind is "randomly assigned" between a relic and a card
 * modification — never both, never offered as a choice of category. If the roll picks relics
 * but none are available (e.g. every registered one is already active), falls back to a card
 * modification instead (the fixed catalog can never be empty, so no reverse fallback is needed).
 *
 * <p>A relic reward is this class's own single-step pick (reusing {@link
 * RunManager#offerRelicChoices}); a card-modification reward hands off to {@link
 * CardModTypeRoom} (pick the modification) then {@link CardModTargetRoom} (pick which deck
 * card it applies to) — the player chooses the target card, per the user's design.
 */
public class EliteRewardRoom extends Room {
    private static final int RELIC_CHOICE_COUNT = 2;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;

    private EliteRewardRoom(List<String> relicChoices) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Choose a Relic", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        for (String relicId : relicChoices) {
            addOption(relicId, () -> {
                runManager.addRelic(relicId);
                GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
            });
        }

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
        MenuLayout.centerHeader(header, 100);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    /** Rolls relic-vs-modification randomly (falling back to a modification if no relic is available). */
    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        if (new Random().nextBoolean()) {
            List<String> relicChoices = runManager.offerRelicChoices(RELIC_CHOICE_COUNT);
            if (!relicChoices.isEmpty()) return new EliteRewardRoom(relicChoices);
        }
        return CardModTypeRoom.get();
    }
}
