package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
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

import io.github.elderpath_crusade.interfaces.UIRenderable;
import java.util.ArrayList;
import java.util.List;

public class LicenseRoom extends Room {
        private Text header;
        private List<UIRenderable> infoComponents = new ArrayList<>();
        private Button backButton;
        private Button upButton;
        private Button downButton;
        private int scrollOffset = 0;
        private final int SCROLL_STEP = 200;

        private LicenseRoom() {
                super();

                header = new Text("Full License Text", FontType.SILKSCREEN, 0, 0, 0,
                                ColorSettings.TEXT_DEFAULT.getColor())
                                .withFontSize(FontSize.TITLE_MEDIUM);
                addContent(header);

                String content = InfoDataManager.getRawFileContent("OFL.txt");
                int wrapWidth = (int) (SettingsManager.screenSize.getScreenWidth() * 0.7f);

                // Split by single newlines for maximum clipping granularity
                String[] lines = content.split("\n");
                for (String line : lines) {
                        String trimmed = line.trim();
                        // We keep empty lines as small vertical spacers if needed,
                        // but for now let's just add non-empty ones or specific spacing logic.
                        Text lineText = new Text(trimmed.isEmpty() ? " " : trimmed, FontType.SILKSCREEN, 0, 0, 0,
                                        ColorSettings.TEXT_DEFAULT.getColor())
                                        .withFontSize(FontSize.CAPTION)
                                        .withWrapWidth(wrapWidth);
                        infoComponents.add(lineText);
                        addContent(lineText);
                }

                backButton = Button
                                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                                                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 50, 0)
                                .withOnClick((e) -> RoomManager.gotoRoom(() -> TextInfoRoom.get("legal")),
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

                MenuLayout.centerHeader(header, 60);

                backButton.getBounds().setX(20);
                backButton.getBounds().setY(screenHeight - 70);

                upButton.getBounds().setX(screenWidth - 80);
                upButton.getBounds().setY(screenHeight - 150);

                downButton.getBounds().setX(screenWidth - 80);
                downButton.getBounds().setY(100);

                int lineSpacing = 5;
                // Calculate total content height
                int totalHeight = 0;
                for (UIRenderable comp : infoComponents) {
                        totalHeight += (comp.getBounds().getHeight() + lineSpacing);
                }

                int clipTop = screenHeight - 160;
                int clipBottom = 110;

                int availableSpace = clipTop - clipBottom;
                int maxScroll = Math.max(0, totalHeight - availableSpace);
                scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

                Color primary = ColorSettings.BUTTON_PRIMARY.getColor().cpy();
                Color dimmed = primary.cpy().mul(1, 1, 1, 0.3f);

                upButton.setDisabled(scrollOffset <= 0);
                upButton.setBackgroundColor(upButton.isDisabled() ? dimmed : primary);

                downButton.setDisabled(scrollOffset >= maxScroll);
                downButton.setBackgroundColor(downButton.isDisabled() ? dimmed : primary);

                int currentY = clipTop + scrollOffset;
                for (UIRenderable comp : infoComponents) {
                        comp.getBounds().setX(screenCenterX - comp.getBounds().getWidth() / 2);

                        int compHeight = comp.getBounds().getHeight();
                        int compY = currentY - compHeight;
                        comp.getBounds().setY(compY);

                        // Strict visibility clipping: hide IF ANY PART crosses a boundary
                        if (compY + compHeight > clipTop || compY < clipBottom) {
                                comp.getBounds().setX(-2000);
                        }

                        currentY -= (compHeight + lineSpacing);
                }
        }

        @Override
        public void onScreenResize() {
                layoutContents();
        }

        public static LicenseRoom get() {
                return new LicenseRoom();
        }
}
