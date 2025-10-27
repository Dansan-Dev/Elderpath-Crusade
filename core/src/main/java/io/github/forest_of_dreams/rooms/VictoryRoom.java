package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.managers.Game;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.WinConditionManager;
import io.github.forest_of_dreams.managers.PlayerManager;
import io.github.forest_of_dreams.supers.Room;
import io.github.forest_of_dreams.ui_objects.Button;
import io.github.forest_of_dreams.ui_objects.Text;
import io.github.forest_of_dreams.utils.FontSize;

/**
 * Simple victory/defeat screen with two actions: Play Again and Main Menu.
 * The screen text reflects whether the local perspective (P1 human) won or lost,
 * but we show both winner and loser labels to satisfy the 2-way trigger requirement.
 */
public class VictoryRoom extends Room {
    private final Text title;
    private final Button playAgain;
    private final Button mainMenu;

    private VictoryRoom(PieceAlignment winner) {
        // Entering the VictoryRoom should reset win/loss guard state for the next match
        WinConditionManager.reset();
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        // Title shows localized victory/defeat from the local player's perspective
        PieceAlignment local = PlayerManager.getLocalPlayer();
        boolean localWon = (winner == local);
        String titleText;
        if (localWon) {
            titleText = "Victory!";
        } else {
            // Per requirements: capitalization differs depending on which side the local player is
            titleText = (local == PieceAlignment.P1) ? "Game Over!" : "Game over!";
        }
        title = new Text(titleText, FontType.SILKSCREEN, 0, 0, 5, Color.WHITE)
            .withFontSize(FontSize.TITLE_LARGE);
        addUI(title);

        // Buttons
        playAgain = Button.fromColor(
            Color.WHITE.cpy().mul(0.2f,0.2f,0.2f,1f),
            "Play Again",
            FontType.SILKSCREEN,
            FontSize.BODY_MEDIUM.getSize(),
            screenW/2 - 150,
            screenH/2 - 40,
            300,
            50,
            5
        ).withTextColors(Color.WHITE, Color.WHITE, Color.WHITE)
         .withOnClick((e) -> Game.gotoRoom(DemoRoom::get), ClickableEffectData.getImmediate());
        addUI(playAgain);

        mainMenu = Button.fromColor(
            Color.WHITE.cpy().mul(0.2f,0.2f,0.2f,1f),
            "Main Menu",
            FontType.SILKSCREEN,
            FontSize.BODY_MEDIUM.getSize(),
            screenW/2 - 150,
            screenH/2 - 100,
            300,
            50,
            5
        ).withTextColors(Color.WHITE, Color.WHITE, Color.WHITE)
         .withOnClick((e) -> Game.gotoRoom(MainMenuRoom::get), ClickableEffectData.getImmediate());
        addUI(mainMenu);

        layout();
    }

    private void layout() {
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        // Center title near top
        title.setBounds(new Box(
            (screenW - title.getWidth())/2,
            (int)(screenH * 0.65f),
            title.getWidth(),
            title.getHeight()
        ));
        // Buttons already positioned approximately around center
    }

    @Override
    public void onScreenResize() {
        layout();
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        if (playAgain != null) {
            playAgain.getBounds().setX(screenW/2 - playAgain.getWidth()/2);
            playAgain.getBounds().setY(screenH/2 - 40);
        }
        if (mainMenu != null) {
            mainMenu.getBounds().setX(screenW/2 - mainMenu.getWidth()/2);
            mainMenu.getBounds().setY(screenH/2 - 100);
        }
    }

    public static VictoryRoom get(PieceAlignment winner) {
        return new VictoryRoom(winner);
    }
}
