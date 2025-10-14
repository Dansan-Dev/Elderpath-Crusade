package io.github.forest_of_dreams.rooms;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Sprite;
import io.github.forest_of_dreams.data_objects.Box;
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
    private MainMenuRoom() {
        super();
        int[] screen_center = SettingsManager.screenSize.getScreenCenter();
        int screen_width = SettingsManager.screenSize.getScreenWidth();
        int screen_height = SettingsManager.screenSize.getScreenHeight();

        int[] backgroundSize = {1536, 1024};
        SpriteObject background = new SpriteObject(0, 0, screen_width, screen_height, -1, SpriteBoxPos.BOTTOM);
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

        Text title = new Text("Main Menu", FontType.DEFAULT, 0, 0, 0, Color.WHITE);
        Box titleBox = title.getBounds();
        titleBox.setX(screen_center[0] - titleBox.getWidth()/2);
        titleBox.setY(screen_height - titleBox.getHeight() - 50);
        addContent(title);

        Text playButton = new Text("Play Demo", FontType.DEFAULT, 0, 0, 0, Color.BLUE)
            .withOnClick((e) -> Game.gotoRoom(StartRoom.get()), ClickableEffectData.getImmediate());
        Box playButtonBox = playButton.getBounds();
        playButtonBox.setX(screen_center[0] - playButtonBox.getWidth()/2);
        playButtonBox.setY(screen_center[1] - playButtonBox.getHeight()/2);
        addContent(playButton);
    }

    public static MainMenuRoom get() {
        return new MainMenuRoom();
    }
}
