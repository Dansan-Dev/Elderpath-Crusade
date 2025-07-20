package io.github.forest_of_dreams.game_objects.pause;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.settings.PauseScreenPage;
import io.github.forest_of_dreams.game_objects.TextureObject;
import io.github.forest_of_dreams.interfaces.Renderable;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.GraphicsManager;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.AbstractTexture;
import lombok.Getter;
import lombok.Setter;

/**
 * To add new pages:
 * - Make a new Page class under game_objects/pause/pages
 * - Add a new page in the PauseScreenPage enum
 *
 * To then go to that page onClick, reference PauseScreen.getCurrentPage.setPage(...)
 */
public class PauseScreen extends AbstractTexture implements UIRenderable {
    private static final int z = 10;
    private static final PauseScreen pauseScreen = new PauseScreen();
    private static final TextureObject background = new TextureObject(
        new Color(0, 0, 0, 0.6f),
        0, 0, 100, 100);

    @Getter private static PauseScreenPage currentPage = PauseScreenPage.MENU;

    private PauseScreen() {}

    public static PauseScreen get() {
        return pauseScreen;
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        // isPaused needs to be false to avoid standardized functionality (should not stop anything within)
        currentPage.getPage().render(batch, 0, false);
        int[] screenSize = SettingsManager.screenSize.getScreenSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, z, false);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        currentPage.getPage().render(batch, 0, false);
        int[] screenSize = SettingsManager.screenSize.getScreenSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, z, false, x, y);
    }

    public static void setCurrentPage(PauseScreenPage page) {
        GraphicsManager.retractClickables(currentPage.getPage());
        currentPage = page;
        GraphicsManager.sendClickables(currentPage.getPage());
    }
}
