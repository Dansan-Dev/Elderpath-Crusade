package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

/** Shows {@link TraderRoom}'s outcome (the trade already happened) before continuing to {@link RunRoom}. */
public class TraderResultRoom extends Room {
    private final Text header;
    private final Text message;
    private final Button continueButton;

    private TraderResultRoom(String tradedAway, String received) {
        super();

        header = new Text("A Shady Trader", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        String text = received == null
                ? "Traded away " + tradedAway + " — the trader had nothing new to offer in return."
                : "Traded away " + tradedAway + " for " + received + ".";
        message = new Text(text, FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.BODY_MEDIUM);
        addContent(message);

        continueButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Continue", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 160, 60, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(RunRoom::get), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(continueButton);

        layoutContents();
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        MenuLayout.centerHeader(header, 100);
        MenuLayout.centerHeader(message, 160);
        continueButton.getBounds().setX(screenCenter[0] - continueButton.getBounds().getWidth() / 2);
        continueButton.getBounds().setY(screenCenter[1] - 60);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get(String tradedAway, String received) {
        return new TraderResultRoom(tradedAway, received);
    }
}
