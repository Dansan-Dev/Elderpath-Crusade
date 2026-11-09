package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.CardModificationCatalog;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

/**
 * Second step of an Elite's card-modification reward (ELITE-001) — the player already picked
 * which {@link CardModificationCatalog.Entry} to apply ({@link CardModTypeRoom}); this lists
 * every card in the current deck as a target. Picking one calls
 * {@link RunManager#upgradeCard}, then continues to {@link RunRoom}.
 */
public class CardModTargetRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;

    private CardModTargetRoom(CardModificationCatalog.Entry chosenMod) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Apply " + chosenMod.name() + " To:", FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        for (RunCardInstance instance : runManager.getRunState().deck()) {
            String instanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            addOption(label, () -> {
                runManager.upgradeCard(instanceId, chosenMod.delta());
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

    public static CardModTargetRoom get(CardModificationCatalog.Entry chosenMod) {
        return new CardModTargetRoom(chosenMod);
    }
}
