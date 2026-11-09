package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

/**
 * Choose a new Elder (ELDER-004/005) — shared by both the very first pick, right after a fresh
 * run's deck is drafted ({@link RunDraftRoom}), and every later pick, after winning a
 * non-terminal floor ({@code VictoryRoom}, replacing the old relic/new-card/stat-boost reward
 * screen entirely per the user's design). Lists every card currently in the deck as a
 * candidate; picking one calls {@link RunManager#promoteToElder}, which retires it from the
 * deck into Elder status and raises (or leaves unchanged) every future battle's card-lock wait —
 * see {@code RunManager}'s class doc. Always continues to {@link RunRoom} afterward.
 *
 * <p>A deck with only one card left has nothing eligible to promote without emptying it
 * (RunState.addElder's own guard) — skips straight to RunRoom rather than show an empty choice.
 */
public class ElderPickRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;

    private ElderPickRoom() {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Choose Your Elder", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        for (RunCardInstance instance : runManager.getRunState().deck()) {
            String instanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            addOption(label, () -> {
                runManager.promoteToElder(instanceId);
                continueRun();
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

    private static void continueRun() {
        GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
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

    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        if (runManager.getRunState().deck().size() <= 1) {
            return RunRoom.get(); // nothing eligible to promote without emptying the deck
        }
        return new ElderPickRoom();
    }
}
