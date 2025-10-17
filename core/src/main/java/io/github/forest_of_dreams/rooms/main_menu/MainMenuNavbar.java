package io.github.forest_of_dreams.rooms.main_menu;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.Button;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.SpriteObject;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.rooms.DemoRoom;
import io.github.forest_of_dreams.rooms.SettingsRoom;
import io.github.forest_of_dreams.supers.HigherOrderUI;
import io.github.forest_of_dreams.utils.SpriteCreator;

import java.util.List;

public class MainMenuNavbar extends HigherOrderUI {
    private static final int[] NAVBAR_IMAGE_SIZE = {551, 831};

    private SpriteObject navbarBg;
    private Button playButton;
    private Button settingsButton;
    private Button exitButton;

    public MainMenuNavbar() {
        super();
        // Build background sprite
        navbarBg = new SpriteObject(0, 0, NAVBAR_IMAGE_SIZE[0] / 3, NAVBAR_IMAGE_SIZE[1] / 3, -1, SpriteBoxPos.BOTTOM);
        navbarBg.addAnimation(
            "general",
            List.of(SpriteCreator.makeSprite(
                "images/home_navbar.png",
                0, 0,
                NAVBAR_IMAGE_SIZE[0], NAVBAR_IMAGE_SIZE[1],
                NAVBAR_IMAGE_SIZE[0] / 3, NAVBAR_IMAGE_SIZE[1] / 3
            )),
            0
        );

        // Build buttons
        playButton = Button.fromColor(Color.valueOf("#81cce3"), "Demo", FontType.SILKSCREEN, 10, 0, 0, 100, 60, 0)
            .withOnClick((e) -> Game.gotoRoom(DemoRoom.get()), ClickableEffectData.getImmediate())
            .withHoverColor(Color.valueOf("#b3d8e3"))
            .withBorderColor(Color.GRAY)
            .withHoverBorderColor(Color.WHITE);

        settingsButton = Button.fromColor(Color.valueOf("#81cce3"), "Settings", FontType.SILKSCREEN, 10, 0, 0, 100, 60, 0)
            .withOnClick((e) -> Game.gotoRoom(SettingsRoom.get()), ClickableEffectData.getImmediate())
            .withHoverColor(Color.valueOf("#b3d8e3"))
            .withBorderColor(Color.GRAY)
            .withHoverBorderColor(Color.WHITE);

        exitButton = Button.fromColor(Color.valueOf("#81cce3"), "Exit", FontType.SILKSCREEN, 10, 0, 0, 100, 60, 0)
            .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
            .withHoverColor(Color.valueOf("#b3d8e3"))
            .withBorderColor(Color.GRAY)
            .withHoverBorderColor(Color.WHITE);

        // Add children to container
        getRenderableUIs().add(navbarBg);
        getRenderableUIs().add(playButton);
        getRenderableUIs().add(settingsButton);
        getRenderableUIs().add(exitButton);
    }

    @Override
    public void setBounds(Box bounds) {
        super.setBounds(bounds);
        layoutChildren();
    }

    private void layoutChildren() {
        if (getBounds() == null) return;
        // Parent all children to this container's bounds
        if (navbarBg != null) navbarBg.setParent(getBounds());
        if (playButton != null) playButton.setParent(getBounds());
        if (settingsButton != null) settingsButton.setParent(getBounds());
        if (exitButton != null) exitButton.setParent(getBounds());

        int navW = getBounds().getWidth();
        int navH = getBounds().getHeight();

        // Background fills whole navbar
        if (navbarBg != null) navbarBg.setBounds(new Box(0, 0, navW, navH));

        // Button layout within navbar
        int buttonWidth = 100;
        int buttonHeight = 60;
        int spacing = 10;
        int leftX = (navW - buttonWidth) / 2;

        if (playButton != null) playButton.setBounds(new Box(leftX, navH - buttonHeight - 20, buttonWidth, buttonHeight));
        if (settingsButton != null) settingsButton.setBounds(new Box(leftX, navH - 2 * buttonHeight - 20 - spacing, buttonWidth, buttonHeight));
        if (exitButton != null) exitButton.setBounds(new Box(leftX, navH - 3 * buttonHeight - 20 - 2 * spacing, buttonWidth, buttonHeight));
    }
}
