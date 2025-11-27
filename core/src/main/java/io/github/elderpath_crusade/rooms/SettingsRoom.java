package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.managers.RoomManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.MusicManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.utils.MenuLayout;

public class SettingsRoom extends Room {
    private Text header;
    private Button toggleFullscreen;
    private Button backButton;

    private SettingsRoom() {
        super();

        // Play menu music
        MusicManager.playLoopingMusic("Evening_Harmony.mp3");

        header = new Text("Settings", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        toggleFullscreen = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Toggle Fullscreen", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 200, 60, 0)
            .withOnClick((e) -> SettingsManager.screenSize.toggleFullscreen(), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(toggleFullscreen);

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, 60, 0)
            .withOnClick((e) -> RoomManager.gotoRoom(MainMenuRoom::get), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        layoutContents();
    }

    private void layoutContents() {
        int[] screenCenter = SettingsManager.screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenCenterY = screenCenter[1];
        int screenHeight = SettingsManager.screenSize.getScreenHeight();

        // Header centered at top via shared helper
        MenuLayout.centerHeader(header, 100);

        // Buttons centered beneath
        toggleFullscreen.getBounds().setX(screenCenterX - toggleFullscreen.getBounds().getWidth() / 2);
        toggleFullscreen.getBounds().setY(screenCenterY - toggleFullscreen.getBounds().getHeight() / 2 - 150);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(screenCenterY - backButton.getBounds().getHeight() / 2 - 230);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static SettingsRoom get() {
        return new SettingsRoom();
    }
}
