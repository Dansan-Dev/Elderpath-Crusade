package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.managers.InfoDataManager;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import io.github.elderpath_crusade.interfaces.UIRenderable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TextInfoRoom extends Room {
    private Text header;
    private List<UIRenderable> infoComponents = new ArrayList<>();
    private Button backButton;
    private Button upButton;
    private Button downButton;
    private int scrollOffset = 0;
    private final int SCROLL_STEP = 200;

    private TextInfoRoom(String category) {
        super();

        String title = InfoDataManager.getTitle(category);
        header = new Text(title, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        List<Map<String, String>> entries = InfoDataManager.getEntries(category);
        for (Map<String, String> entry : entries) {
            String optionalName = entry.get("name");
            String optionalRole = entry.get("role");
            String licenseFile = entry.get("license_file");

            int maxWrapWidth = (int) (SettingsManager.screenSize.getScreenWidth() * 0.7f);

            if (optionalName != null && !optionalName.isEmpty()) {
                Text nameText = new Text(optionalName, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.BUTTON_PRIMARY.getColor())
                        .withFontSize(FontSize.BODY_MEDIUM)
                        .withWrapWidth(maxWrapWidth);
                infoComponents.add(nameText);
                addContent(nameText);
            }

            if (optionalRole != null && !optionalRole.isEmpty()) {
                Text roleText = new Text(optionalRole, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                        .withFontSize(FontSize.BODY_MEDIUM)
                        .withWrapWidth(maxWrapWidth);
                infoComponents.add(roleText);
                addContent(roleText);

                if (licenseFile != null && !licenseFile.isEmpty()) {
                    String name = optionalName != null ? optionalName : "License";
                    Button licenseButton = Button
                            .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "View Full License",
                                    FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 180, 40, 0)
                            .withOnClick(
                                    (e) -> GameContext.get().getRoomManager().gotoRoom(() -> LicenseRoom.get(licenseFile, name)),
                                    ClickableEffectData.getImmediate())
                            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
                    infoComponents.add(licenseButton);
                    addContent(licenseButton);
                }
            }
        }

        backButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 50, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(InformationSelectionRoom::get),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        upButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "^", FontType.SILKSCREEN,
                        FontSize.TITLE_MEDIUM.getSize(), 0, 0, 50, 50, 0)
                .withOnClick((e) -> {
                    scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
                    layoutContents();
                }, ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        addContent(upButton);

        downButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "v", FontType.SILKSCREEN,
                        FontSize.TITLE_MEDIUM.getSize(), 0, 0, 50, 50, 0)
                .withOnClick((e) -> {
                    scrollOffset += SCROLL_STEP;
                    layoutContents();
                }, ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        addContent(downButton);

        layoutContents();
    }

    private void layoutContents() {
        int[] screenCenter = SettingsManager.screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenWidth = SettingsManager.screenSize.getScreenWidth();
        int screenHeight = SettingsManager.screenSize.getScreenHeight();

        MenuLayout.centerHeader(header, 100);

        backButton.getBounds().setX(20);
        backButton.getBounds().setY(screenHeight - 70);

        upButton.getBounds().setX(screenWidth - 80);
        upButton.getBounds().setY(screenHeight - 150);

        downButton.getBounds().setX(screenWidth - 80);
        downButton.getBounds().setY(100);

        // First pass: calculate total height to determine max scroll
        int totalHeight = 0;
        for (int i = 0; i < infoComponents.size(); i++) {
            UIRenderable comp = infoComponents.get(i);
            int compHeight = comp.getBounds().getHeight();
            totalHeight += compHeight;
            if (comp instanceof Button) {
                totalHeight += 50;
            } else if (i + 1 < infoComponents.size() && infoComponents.get(i + 1) instanceof Button) {
                totalHeight += 15;
            } else {
                totalHeight += 35;
            }
        }

        // maxScroll is how much we can move UP.
        // Bottom limit is around y=100.
        int availableSpace = (screenHeight - 200) - 100;
        int maxScroll = Math.max(0, totalHeight - availableSpace);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        // Update button visual clues
        Color primary = ColorSettings.BUTTON_PRIMARY.getColor().cpy();
        Color dimmed = primary.cpy().mul(1, 1, 1, 0.3f);

        upButton.setDisabled(scrollOffset <= 0);
        upButton.setBackgroundColor(upButton.isDisabled() ? dimmed : primary);

        downButton.setDisabled(scrollOffset >= maxScroll);
        downButton.setBackgroundColor(downButton.isDisabled() ? dimmed : primary);

        int clipTop = screenHeight - 160;
        int clipBottom = 110;

        int currentY = screenHeight - 200 + scrollOffset;
        for (int i = 0; i < infoComponents.size(); i++) {
            UIRenderable comp = infoComponents.get(i);
            comp.getBounds().setX(screenCenterX - comp.getBounds().getWidth() / 2);

            int compHeight = comp.getBounds().getHeight();
            int compY = currentY - compHeight;
            comp.getBounds().setY(compY);

            // Strict visibility clipping: hide IF ANY PART crosses a boundary
            // This prevents "peeking" into the header or footer space.
            if (compY + compHeight > clipTop || compY < clipBottom) {
                comp.getBounds().setX(-2000);
            }

            if (comp instanceof Button) {
                currentY -= (compHeight + 50);
            } else if (i + 1 < infoComponents.size() && infoComponents.get(i + 1) instanceof Button) {
                currentY -= (compHeight + 15);
            } else {
                currentY -= (compHeight + 35);
            }
        }
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static TextInfoRoom get(String category) {
        return new TextInfoRoom(category);
    }
}
