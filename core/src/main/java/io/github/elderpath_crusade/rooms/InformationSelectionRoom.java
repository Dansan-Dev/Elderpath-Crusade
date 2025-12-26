package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.managers.RoomManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

public class InformationSelectionRoom extends Room {
    private Text header;
    private Button creditsButton;
    private Button legalButton;
    private Button backButton;

    private InformationSelectionRoom() {
        super();

        header = new Text("Game Information", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        int buttonWidth = 250;
        int buttonHeight = 60;

        creditsButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Roles & Contributions", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, buttonWidth, buttonHeight, 0)
                .withOnClick((e) -> RoomManager.gotoRoom(() -> TextInfoRoom.get("credits")),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(creditsButton);

        legalButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Legal Information", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, buttonWidth, buttonHeight, 0)
                .withOnClick((e) -> RoomManager.gotoRoom(() -> TextInfoRoom.get("legal")),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(legalButton);

        backButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, buttonHeight, 0)
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

        MenuLayout.centerHeader(header, 100);

        creditsButton.getBounds().setX(screenCenterX - creditsButton.getBounds().getWidth() / 2);
        creditsButton.getBounds().setY(screenCenterY - creditsButton.getBounds().getHeight() / 2 + 50);

        legalButton.getBounds().setX(screenCenterX - legalButton.getBounds().getWidth() / 2);
        legalButton.getBounds().setY(screenCenterY - legalButton.getBounds().getHeight() / 2 - 30);

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(screenCenterY - backButton.getBounds().getHeight() / 2 - 150);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static InformationSelectionRoom get() {
        return new InformationSelectionRoom();
    }
}
