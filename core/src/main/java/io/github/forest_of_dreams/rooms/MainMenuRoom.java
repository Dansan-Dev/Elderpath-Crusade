package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.path_loaders.ImagePathBackgroundAndUI;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.rooms.main_menu.MainMenuNavbar;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.utils.SpriteCreator;

import java.util.List;

public class MainMenuRoom extends Room {
    private SpriteObject background;
    private MainMenuNavbar navbar;
    private Text title;

    private static final int[] backgroundSize = {1536, 1024};
    private static final int[] navbarSize = {551, 831};

    private MainMenuRoom() {
        super();
        // Create objects

        int screen_width = SettingsManager.screenSize.getScreenWidth();
        int screen_height = SettingsManager.screenSize.getScreenHeight();

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
            .withFontSize(io.github.forest_of_dreams.utils.FontSize.BODY_LARGE);
        addContent(title);

        // Initial layout
        layoutContents();
    }

    private void layoutContents() {
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int screen_width = SettingsManager.screenSize.getScreenWidth();
        int screen_height = SettingsManager.screenSize.getScreenHeight();

        // Background covers full screen
        background.setBounds(new Box(0, 0, screen_width, screen_height));

        // Title position
        Box titleBox = title.getBounds();
        // Update label to get correct width/height before centering
        title.update();
        titleBox.setX(screen_center[0] - titleBox.getWidth()/2);
        titleBox.setY(screen_height - titleBox.getHeight() - 120);

        // Navbar alignment stays at bottom center with original scale
        int navW = navbarSize[0] / 3;
        int navH = navbarSize[1] / 3;
        int navX = screen_center[0] - (navbarSize[0] / 6);
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
