package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

/**
 * Lists the ways to play, one level below the main menu's "Game Mode" button — Demo, Local
 * Multiplayer, Host/Join Online today; Roguelike Run joins this list once FEAT-006 exists,
 * intended as the primary way to play once it does (see .claude/plans/roguelike-run-plan.md).
 */
public class GameModeRoom extends Room {
    private static final int BUTTON_WIDTH = 220;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final ButtonList buttonList;
    private final Button backButton;

    private GameModeRoom() {
        super();

        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        header = new Text("Game Mode", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
            .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        buttonList = new ButtonList();
        addOption("Demo", () -> GameContext.get().getRoomManager().gotoRoom(DraftRoom::get));
        addOption("Local Multiplayer",
                () -> GameContext.get().getRoomManager().gotoRoom(() -> DraftRoom.getForLocalMultiplayer(PieceAlignment.P1)));
        addOption("Host Online", () -> GameContext.get().getRoomManager().gotoRoom(OnlineMatchRoom::host));
        addOption("Join Online (localhost)", () -> GameContext.get().getRoomManager().gotoRoom(() -> OnlineMatchRoom.join("localhost")));

        backButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 120, BUTTON_HEIGHT, 0)
            .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get), ClickableEffectData.getImmediate())
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

    public static GameModeRoom get() {
        return new GameModeRoom();
    }
}
