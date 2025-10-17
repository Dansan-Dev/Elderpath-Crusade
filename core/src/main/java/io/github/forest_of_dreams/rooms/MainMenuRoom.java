package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Button;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.data_objects.Text;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.utils.SpriteCreator;

import java.util.List;

public class MainMenuRoom extends Room {
    private SpriteObject background;
    private SpriteObject navbar;
    private Text title;
    private Button playButton;
    private Button settingsButton;
    private Button exitButton;

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
                "images/home_screen_background.png",
                0, 0,
                backgroundSize[0], backgroundSize[1],
                screen_width, screen_height
            )),
            0
        );
        addContent(background);

        navbar = new SpriteObject(0, 150, navbarSize[0]/3, navbarSize[1]/3, -1, SpriteBoxPos.BOTTOM);
        navbar.addAnimation(
            "general",
            List.of(SpriteCreator.makeSprite(
                "images/home_navbar.png",
                0, 0,
                navbarSize[0], navbarSize[1],
                navbarSize[0]/3, navbarSize[1]/3
                )),
            0
        );
        addContent(navbar);

        title = new Text("Main Menu", FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
            .withFontSize(18f);
        addContent(title);

        playButton = Button.fromColor(Color.valueOf("#81cce3"), "Demo", FontType.SILKSCREEN, 10, 0, 0, 100, 60, 0)
            .withOnClick((e) -> Game.gotoRoom(DemoRoom.get()), ClickableEffectData.getImmediate())
            .withHoverColor(Color.valueOf("#b3d8e3"))
            .withBorderColor(Color.GRAY)
            .withHoverBorderColor(Color.WHITE);
        addContent(playButton);

        settingsButton = Button.fromColor(Color.valueOf("#81cce3"), "Settings", FontType.SILKSCREEN, 10, 0, 0, 100, 60, 0)
            .withOnClick((e) -> Game.gotoRoom(SettingsRoom.get()), ClickableEffectData.getImmediate())
            .withHoverColor(Color.valueOf("#b3d8e3"))
            .withBorderColor(Color.GRAY)
            .withHoverBorderColor(Color.WHITE);
        addContent(settingsButton);

        exitButton = Button.fromColor(Color.valueOf("#81cce3"), "Exit", FontType.SILKSCREEN, 10, 0, 0, 100, 60, 0)
            .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
            .withHoverColor(Color.valueOf("#b3d8e3"))
            .withBorderColor(Color.GRAY)
            .withHoverBorderColor(Color.WHITE);
        addContent(exitButton);

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
        navbar.setBounds(new Box(screen_center[0]-(navbarSize[0]/6), 150, navbarSize[0]/3, navbarSize[1]/3));

        // Buttons centered vertically spaced
        Box playBox = playButton.getBounds();
        playBox.setX(screen_center[0] - playBox.getWidth() / 2);
        playBox.setY(screen_center[1] - playBox.getHeight() / 2 + 10);

        Box settingsBox = settingsButton.getBounds();
        settingsBox.setX(screen_center[0] - settingsBox.getWidth() / 2);
        settingsBox.setY(screen_center[1] - settingsBox.getHeight() / 2 - 70);

        Box exitBox = exitButton.getBounds();
        exitBox.setX(screen_center[0] - exitBox.getWidth() / 2);
        exitBox.setY(screen_center[1] - exitBox.getHeight() / 2 - 150);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static MainMenuRoom get() {
        return new MainMenuRoom();
    }
}
