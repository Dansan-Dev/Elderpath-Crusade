package io.github.forest_of_dreams.game_objects.pause;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.TextureObject;
import io.github.forest_of_dreams.game_objects.pause.pages.PauseMenuPage;
import io.github.forest_of_dreams.game_objects.pause.pages.PauseSettingsPage;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * To add new pages:
 * - Make a new Page class under game_objects/pause/pages
 * - Add a new page in the PauseScreenPage enum
 *
 * To then go to that page onClick, reference PauseScreen.getCurrentPage.setPage(...)
 */
public class PauseScreen extends AbstractTexture {
    private static final TextureObject background = new TextureObject(
        new Color(0, 0, 0, 0.6f),
        0, 0, 100, 100);

    @Getter @Setter private static PauseScreenPage currentPage = PauseScreenPage.MENU;

    public static void initialize() {
        background.setZ(10);
    }

    public static void renderPauseUI(SpriteBatch batch) {
        // isPaused needs to be false to avoid standardized functionality (should not stop anything within)
        currentPage.getPage().render(batch, 0, false);
    }

    public static void render(SpriteBatch batch, int zLevel, boolean isPaused) {
        int[] screenSize = SettingsManager.screenSize.getCurrentSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, zLevel, isPaused);
    }
}
