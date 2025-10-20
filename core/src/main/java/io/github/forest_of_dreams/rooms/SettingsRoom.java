package io.github.forest_of_dreams.rooms;

import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.utils.FontSize;
import io.github.forest_of_dreams.ui_objects.Button;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.utils.MenuLayout;

public class SettingsRoom extends Room {
    private Text header;
    private Button toggleFullscreen;
    private Button backButton;

    private SettingsRoom() {
        super();

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
            .withOnClick((e) -> Game.gotoRoom(MainMenuRoom.get()), ClickableEffectData.getImmediate())
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
