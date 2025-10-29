package io.github.elderpath_crusade.game_objects.pause;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.settings.PauseScreenPage;
import io.github.elderpath_crusade.game_objects.sprites.TextureObject;
import io.github.elderpath_crusade.game_objects.pause.pages.PauseMenuPage;
import io.github.elderpath_crusade.game_objects.pause.pages.PauseSettingsPage;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.utils.ClickableRegistryUtil;
import lombok.Getter;

/**
 * To add new pages:
 * - Make a new Page class under game_objects/pause/pages
 * - Add a new page in the PauseScreenPage enum
 *
 * To then go to that page onClick, reference PauseScreen.getCurrentPage.setPage(...)
 */
public class PauseScreen extends LowestOrderTexture implements UIRenderable {
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

    private void updateCurrentPageLayout() {
        if (currentPage == PauseScreenPage.MENU && currentPage.getPage() instanceof PauseMenuPage menu) {
            menu.update();
        } else if (currentPage == PauseScreenPage.SETTINGS && currentPage.getPage() instanceof PauseSettingsPage settings) {
            settings.layout();
        }
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        updateCurrentPageLayout();
        // isPaused needs to be false to avoid standardized functionality (should not stop anything within)
        currentPage.getPage().render(batch, 0, false);
        int[] screenSize = SettingsManager.screenSize.getScreenSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, z, false);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        updateCurrentPageLayout();
        currentPage.getPage().render(batch, 0, false);
        int[] screenSize = SettingsManager.screenSize.getScreenSize();
        background.setBounds(new Box(0, 0, screenSize[0], screenSize[1]));
        background.render(batch, z, false, x, y);
    }

    public static void setCurrentPage(PauseScreenPage page) {
        if (currentPage!=PauseScreenPage.NONE) ClickableRegistryUtil.retractClickables(currentPage.getPage());
        currentPage = page;
        if (currentPage!=PauseScreenPage.NONE) ClickableRegistryUtil.sendClickables(currentPage.getPage());
    }
}
