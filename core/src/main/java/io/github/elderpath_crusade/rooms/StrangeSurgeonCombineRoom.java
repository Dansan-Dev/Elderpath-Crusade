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

import java.util.List;

/**
 * Second step of {@link StrangeSurgeonRoom}: a fresh roll of up to 3 eligible deck cards,
 * excluding the first pick. Picking the second subject calls {@link RunManager#combineCards}
 * (first pick kept, second folded in and removed) and continues to {@link RunRoom}.
 */
public class StrangeSurgeonCombineRoom extends Room {
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final Text flavor;
    private final ButtonList buttonList;

    private StrangeSurgeonCombineRoom(String firstInstanceId, List<RunCardInstance> candidates) {
        super();
        RunManager runManager = GameContext.get().getRunManager();

        header = new Text("Strange Surgeon", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("Choose the second subject — their stats and abilities fuse into the first.",
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        buttonList = new ButtonList();
        for (RunCardInstance instance : candidates) {
            String secondInstanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            addOption(label, () -> {
                runManager.combineCards(firstInstanceId, secondInstanceId);
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
        MenuLayout.centerHeader(flavor, 150);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    /** Skips straight back to RunRoom if nothing is left to fuse with (shouldn't happen — StrangeSurgeonRoom.get() already checked). */
    public static Room get(String firstInstanceId) {
        RunManager runManager = GameContext.get().getRunManager();
        List<RunCardInstance> candidates = StrangeSurgeonRoom.eligibleSubjects(runManager, firstInstanceId);
        if (candidates.isEmpty()) return RunRoom.get();
        return new StrangeSurgeonCombineRoom(firstInstanceId, candidates.subList(0, Math.min(StrangeSurgeonRoom.SUBJECT_CHOICES, candidates.size())));
    }
}
