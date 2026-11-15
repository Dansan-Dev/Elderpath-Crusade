package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.config.SettingsManager;
import io.github.elderpath_crusade.audio.MusicManager;
import io.github.elderpath_crusade.rooms.main_menu.MainMenuNavbar;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.SpriteCreator;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

public class MainMenuRoom extends Room {
    private SpriteObject background;
    private MainMenuNavbar navbar;
    private Text title;
    private Button howToPlayButton;

    private static final int[] backgroundSize = {1536, 1024};
    private static final int[] navbarSize = {551, 831};
    private static final int HOW_TO_PLAY_BUTTON_WIDTH = 160;
    private static final int HOW_TO_PLAY_BUTTON_HEIGHT = 50;
    private static final int HOW_TO_PLAY_BUTTON_MARGIN = 20;

    private MainMenuRoom() {
        super();

        // Play menu music
        GameContext.get().getMusicManager().playLoopingMusic("Evening_Harmony.mp3");

        // Create objects

        int screen_width = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screen_height = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        background = new SpriteObject(0, 0, screen_width, screen_height, -2, SpriteBoxPos.BOTTOM_LEFT);
        background.addAnimation(
            "general",
            List.of(SpriteCreator.makeSprite(
                ImagePathBackgroundAndUI.HOME_BACKGROUND.getPath(),
                0, 0,
                backgroundSize[0], backgroundSize[1],
                screen_width, screen_height
            )),
            0
        );
        addContent(background);

        // Navbar as HigherOrderUI container
        navbar = new MainMenuNavbar();
        addUI(navbar);

        // Title remains as content
        title = new Text("Main Menu", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
            .withFontSize(FontSize.BODY_LARGE);
        addContent(title);

        // How to Play (D1, FEAT-015) — a corner button rather than a 5th navbar slot, since the
        // navbar's background art and button spacing are sized for exactly the 4 it already has.
        howToPlayButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "How to Play", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, HOW_TO_PLAY_BUTTON_WIDTH, HOW_TO_PLAY_BUTTON_HEIGHT, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(HowToPlayRoom::get),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(howToPlayButton);

        // Initial layout
        layoutContents();
    }

    private void layoutContents() {
        int[] screen_center = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screen_width = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screen_height = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        // Background covers full screen
        background.setBounds(new Box(0, 0, screen_width, screen_height));

        // Title position via shared helper
        MenuLayout.centerHeader(title, 120);

        // Top-right corner, clear of the title and the bottom-center navbar.
        howToPlayButton.getBounds().setX(screen_width - HOW_TO_PLAY_BUTTON_WIDTH - HOW_TO_PLAY_BUTTON_MARGIN);
        howToPlayButton.getBounds().setY(screen_height - HOW_TO_PLAY_BUTTON_HEIGHT - HOW_TO_PLAY_BUTTON_MARGIN);

        // Navbar alignment stays at bottom center with slightly larger scale
        // Use divisor of 2.7 instead of 3 to make navbar ~11% larger
        int navW = (int)(navbarSize[0] / 2.7f);
        int navH = (int)(navbarSize[1] / 2.7f);
        int navX = screen_center[0] - (navW / 2);
        int navY = screen_center[1] - 210; // ScreenCenterX=360 | 360-150 = 210
        navbar.setBounds(new Box(navX, navY, navW, navH));

    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static MainMenuRoom get() {
        return new MainMenuRoom();
    }
}
