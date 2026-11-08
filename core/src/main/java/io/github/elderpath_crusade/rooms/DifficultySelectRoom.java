package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.run.RunDifficulty;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

/**
 * Shown once, before a fresh roguelike run's map is generated — the player picks a difficulty
 * level (DIFF-001), fixed for the whole run, then {@link RunManager#startRun} is called and the
 * run begins. Only reached from {@code GameModeRoom} when no run is already active; a run in
 * progress goes straight back to {@code RunRoom} instead, since its difficulty is already set.
 */
public class DifficultySelectRoom extends Room {
    private static final List<String> STARTING_DECK =
            List.of("Wolf", "Wolf", "Wolf Cub", "Wolf Cub", "Warp Mage");
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;
    private final Button backButton;

    private DifficultySelectRoom() {
        super();
        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        header = new Text("Choose Your Difficulty", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        for (int level = RunDifficulty.MIN_LEVEL; level <= RunDifficulty.MAX_LEVEL; level++) {
            int chosenLevel = level;
            addOption("Level " + level, () -> startRunAtLevel(chosenLevel));
        }

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, BUTTON_HEIGHT, 0)
            .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(GameModeRoom::get), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

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

    private void startRunAtLevel(int level) {
        GameContext.get().getRunManager().startRun(new RunDifficulty(level), STARTING_DECK);
        GameContext.get().getRoomManager().gotoRoom(RunRoom::get);
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];

        MenuLayout.centerHeader(header, 100);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenterX, screenCenterY);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(screenCenterY - (buttonList.getRenderables().size() * BUTTON_SPACING) / 2 - 60);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static DifficultySelectRoom get() {
        return new DifficultySelectRoom();
    }
}
