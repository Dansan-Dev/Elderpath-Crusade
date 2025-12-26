package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.managers.InfoDataManager;
import io.github.elderpath_crusade.managers.RoomManager;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TextInfoRoom extends Room {
    private Text header;
    private List<Text> entryTexts = new ArrayList<>();
    private Button backButton;

    private TextInfoRoom(String category) {
        super();

        String title = InfoDataManager.getTitle(category);
        header = new Text(title, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        List<Map<String, String>> entries = InfoDataManager.getEntries(category);
        for (Map<String, String> entry : entries) {
            String name = entry.get("name");
            String role = entry.get("role");

            int maxWrapWidth = (int) (SettingsManager.screenSize.getScreenWidth() * 0.8f);

            if (name != null && !name.isEmpty()) {
                Text nameText = new Text(name, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.BUTTON_PRIMARY.getColor())
                        .withFontSize(FontSize.BODY_MEDIUM)
                        .withWrapWidth(maxWrapWidth);
                entryTexts.add(nameText);
                addContent(nameText);
            }

            if (role != null && !role.isEmpty()) {
                Text roleText = new Text(role, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                        .withFontSize(FontSize.BODY_MEDIUM)
                        .withWrapWidth(maxWrapWidth);
                entryTexts.add(roleText);
                addContent(roleText);
            }
        }

        backButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, 60, 0)
                .withOnClick((e) -> RoomManager.gotoRoom(InformationSelectionRoom::get),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        layoutContents();
    }

    private void layoutContents() {
        int[] screenCenter = SettingsManager.screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenHeight = SettingsManager.screenSize.getScreenHeight();

        MenuLayout.centerHeader(header, 100);

        int currentY = screenHeight - 200;
        // We iterate through entryTexts. Since they were added in pairs (name, then
        // role)
        // we can group them or just apply a consistent spacing logic.
        for (int i = 0; i < entryTexts.size(); i++) {
            Text text = entryTexts.get(i);
            text.getBounds().setX(screenCenterX - text.getBounds().getWidth() / 2);
            text.getBounds().setY(currentY - text.getBounds().getHeight()); // Y is bottom-left, adjust for top-down

            // Calculate next Y based on current text height and spacing
            if (i % 2 == 0) {
                currentY -= (text.getBounds().getHeight() + 15); // Gap between Name and Role
            } else {
                currentY -= (text.getBounds().getHeight() + 45); // Gap between full entries
            }
        }

        backButton.getBounds().setX(screenCenterX - backButton.getBounds().getWidth() / 2);
        backButton.getBounds().setY(80);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static TextInfoRoom get(String category) {
        return new TextInfoRoom(category);
    }
}
