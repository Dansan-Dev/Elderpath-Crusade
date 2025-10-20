package io.github.forest_of_dreams.rooms.main_menu;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.utils.ColorSettings;
import io.github.forest_of_dreams.utils.FontSize;
import io.github.forest_of_dreams.path_loaders.ImagePathBackgroundAndUI;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.ui_objects.Button;
import io.github.forest_of_dreams.ui_objects.ButtonList;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.SpriteBoxPos;
import io.github.forest_of_dreams.game_objects.sprites.SpriteObject;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.rooms.DemoRoom;
import io.github.forest_of_dreams.rooms.SettingsRoom;
import io.github.forest_of_dreams.supers.HigherOrderUI;
import io.github.forest_of_dreams.utils.SpriteCreator;

import java.util.List;

public class MainMenuNavbar extends HigherOrderUI {
    private static final int[] NAVBAR_IMAGE_SIZE = {551, 831};

    private SpriteObject navbarBg;
    private ButtonList buttonList;
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
                ImagePathBackgroundAndUI.HOME_NAVBAR.getPath(),
                0, 0,
                NAVBAR_IMAGE_SIZE[0], NAVBAR_IMAGE_SIZE[1],
                NAVBAR_IMAGE_SIZE[0] / 3, NAVBAR_IMAGE_SIZE[1] / 3
            )),
            0
        );

        // Build buttons
        playButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Demo", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 60, 0)
            .withOnClick((e) -> Game.gotoRoom(DemoRoom.get()), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        settingsButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Settings", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 60, 0)
            .withOnClick((e) -> Game.gotoRoom(SettingsRoom.get()), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        exitButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Exit", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 60, 0)
            .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        // Create ButtonList and add buttons
        buttonList = new ButtonList();
        buttonList.addButton(playButton);
        buttonList.addButton(settingsButton);
        buttonList.addButton(exitButton);

        // Add children to container (background + individual buttons)
        getRenderableUIs().add(navbarBg);
        buttonList.getRenderables().forEach(r -> getRenderableUIs().add((Button) r));
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
        if (buttonList != null) {
            buttonList.getRenderables().forEach(r -> ((Button) r).setParent(getBounds()));
        }

        int navW = getBounds().getWidth();
        int navH = getBounds().getHeight();

        // Background fills whole navbar
        if (navbarBg != null) navbarBg.setBounds(new Box(0, 0, navW, navH));

        // Button layout within navbar via ButtonList
        int buttonWidth = 100;
        int buttonHeight = 60;
        int spacing = 10;
        // Ensure button sizes
        if (buttonList != null) {
            buttonList.getRenderables().forEach(r -> {
                Button b = (Button) r;
                Box bb = b.getBounds();
                bb.setWidth(buttonWidth);
                bb.setHeight(buttonHeight);
            });
            int offset = buttonHeight + spacing;
            int centerX = navW / 2;
            int centerY = navH / 2; // center the ButtonList vertically within the navbar background
            buttonList.alignButtonsAcrossYAxis(offset, centerX, centerY);
        }
    }
}
