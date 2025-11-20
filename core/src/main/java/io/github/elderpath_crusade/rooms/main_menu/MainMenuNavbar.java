package io.github.elderpath_crusade.rooms.main_menu;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.managers.RoomManager;
import io.github.elderpath_crusade.rooms.DraftRoom;
import io.github.elderpath_crusade.rooms.SettingsRoom;
import io.github.elderpath_crusade.supers.HigherOrderUI;
import io.github.elderpath_crusade.utils.SpriteCreator;

import java.util.List;

public class MainMenuNavbar extends HigherOrderUI {
    private static final int[] NAVBAR_IMAGE_SIZE = {551, 831};

    private SpriteObject navbarBg;
    private ButtonList buttonList;
    private Button playButton;
    private Button localMultiplayerButton;
    private Button settingsButton;
    private Button exitButton;

    public MainMenuNavbar() {
        super();
        // Build background sprite
        // Use divisor of 2.7 instead of 3 to match the larger size in MainMenuRoom
        int bgW = (int)(NAVBAR_IMAGE_SIZE[0] / 2.7f);
        int bgH = (int)(NAVBAR_IMAGE_SIZE[1] / 2.7f);
        navbarBg = new SpriteObject(0, 0, bgW, bgH, -1, SpriteBoxPos.BOTTOM);
        navbarBg.addAnimation(
            "general",
            List.of(SpriteCreator.makeSprite(
                ImagePathBackgroundAndUI.HOME_NAVBAR.getPath(),
                0, 0,
                NAVBAR_IMAGE_SIZE[0], NAVBAR_IMAGE_SIZE[1],
                bgW, bgH
            )),
            0
        );

        // Build buttons (width increased from 100 to 120 for better appearance)
        int buttonWidth = 140;
        playButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Demo", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, buttonWidth, 60, 0)
            .withOnClick((e) -> RoomManager.gotoRoom(DraftRoom::get), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        localMultiplayerButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Local", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, buttonWidth, 60, 0)
            .withOnClick((e) -> RoomManager.gotoRoom(() -> DraftRoom.getForLocalMultiplayer(PieceAlignment.P1)), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        settingsButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Settings", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, buttonWidth, 60, 0)
            .withOnClick((e) -> RoomManager.gotoRoom(SettingsRoom::get), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        exitButton = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Exit", FontType.SILKSCREEN, FontSize.BUTTON_DEFAULT.getSize(), 0, 0, buttonWidth, 60, 0)
            .withOnClick((e) -> Gdx.app.exit(), ClickableEffectData.getImmediate())
            .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
            .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
            .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());

        // Create ButtonList and add buttons
        buttonList = new ButtonList();
        buttonList.addButton(playButton);
        buttonList.addButton(localMultiplayerButton);
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
        int buttonHeight = 60;
        int spacing = 10;
        // Ensure button sizes
        if (buttonList != null) {
            buttonList.getRenderables().forEach(r -> {
                Button b = (Button) r;
                Box bb = b.getBounds();
                // Keep existing width, only set height (update default width to match buttonWidth)
                if (bb.getWidth() == 0) bb.setWidth(140);
                bb.setHeight(buttonHeight);
            });
            int offset = buttonHeight + spacing;
            int centerX = navW / 2;
            int centerY = navH / 2; // center the ButtonList vertically within the navbar background
            buttonList.alignButtonsAcrossYAxis(offset, centerX, centerY);
        }
    }
}
