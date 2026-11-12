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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One {@link EventRoom} roll: "choose 1 out of 3 random relics you have to remove, you get a
 * new random relic to replace it" — the user's own design (2026-11-12). Up to 3 random owned
 * relics (fewer if the run has fewer than 3); picking one removes it ({@link
 * RunManager#removeRelic}) and grants a random relic the run doesn't already have ({@link
 * RunManager#offerRelicChoices}, rolled *before* the removal so the traded-away relic can never
 * be its own replacement), then continues to {@link TraderResultRoom} to show the swap.
 */
public class TraderRoom extends Room {
    private static final int RELIC_CHOICES = 3;
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final Text flavor;
    private final ButtonList buttonList;

    private TraderRoom(List<String> candidates) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("A Shady Trader", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("The trader will swap one of your relics for something new... and unseen.",
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        buttonList = new ButtonList();
        for (String relicId : candidates) {
            addOption(relicId, () -> {
                List<String> replacementChoices = runManager.offerRelicChoices(1);
                String replacement = replacementChoices.isEmpty() ? null : replacementChoices.get(0);
                runManager.removeRelic(relicId);
                if (replacement != null) runManager.addRelic(replacement);
                GameContext.get().getRoomManager().gotoRoom(() -> TraderResultRoom.get(relicId, replacement));
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
        MenuLayout.centerHeader(flavor, 150);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        List<String> owned = new ArrayList<>(runManager.getRunState().activeRelicIds());
        if (owned.isEmpty()) return RunRoom.get();
        Collections.shuffle(owned);
        return new TraderRoom(owned.subList(0, Math.min(RELIC_CHOICES, owned.size())));
    }
}
