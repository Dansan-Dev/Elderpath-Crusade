package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.audio.MusicPath;
import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.path_loaders.ImagePathBackgroundAndUI;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.ui_objects.TitleBanner;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.SpriteBoxPos;
import io.github.elderpath_crusade.game_objects.sprites.SpriteObject;
import io.github.elderpath_crusade.rooms.main_menu.MainMenuNavbar;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.SpriteCreator;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.List;

public class MainMenuRoom extends Room {
    private SpriteObject background;
    private MainMenuNavbar navbar;
    private Text title;
    private TitleBanner titleBanner;
    private Button howToPlayButton;
    private Text versionText;

    private static final int[] backgroundSize = {1536, 1024};
    private static final int[] navbarSize = {551, 831};
    private static final int HOW_TO_PLAY_BUTTON_WIDTH = 160;
    private static final int HOW_TO_PLAY_BUTTON_HEIGHT = 50;
    private static final int HOW_TO_PLAY_BUTTON_MARGIN = 20;
    private static final int VERSION_MARGIN = 12;
    /** UI-007: gap between the window's top edge and the title banner, at the 1280x720 scale (grows with the banner). */
    private static final int BANNER_TOP_MARGIN = 24;
    /** The title lettering may fill this share of the banner's width (the brass-and-laurel ends stay clear). */
    private static final float TITLE_WIDTH_SHARE = 0.82f;
    /** Outline width in screen pixels per banner scale step (2 = one art pixel at 1280x720). */
    private static final int TITLE_OUTLINE_PER_SCALE = 1;

    private MainMenuRoom() {
        super();

        // Play menu music
        GameContext.get().getMusicManager().playLoopingMusic(MusicPath.EVENING_HARMONY);

        // Create objects

        int screen_width = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screen_height = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

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
        // The title comes from GameName (single source of truth). On the carved banner (UI-007) it is wheat
        // lettering with a dark outline, sized to fit the plaque; without the banner art it stays plain white text.
        titleBanner = new TitleBanner();
        if (titleBanner.isAvailable()) {
            addContent(titleBanner);
            title = new Text(io.github.elderpath_crusade.utils.GameName.current(), FontType.SILKSCREEN, 0, 0, 0,
                ColorSettings.TITLE_TEXT.getColor())
                .withFontSize(FontSize.BODY_LARGE);
        } else {
            title = new Text(io.github.elderpath_crusade.utils.GameName.current(), FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
                .withFontSize(FontSize.BODY_LARGE);
        }
        addContent(title);

        // How to Play (D1, FEAT-015) — a corner button rather than a 5th navbar slot, since the
        // navbar's background art and button spacing are sized for exactly the 4 it already has.
        howToPlayButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "How to Play", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, HOW_TO_PLAY_BUTTON_WIDTH, HOW_TO_PLAY_BUTTON_HEIGHT, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(HowToPlayRoom::get),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(howToPlayButton);

        // The build's version, small in the bottom-left corner — what a player quotes in a bug report.
        versionText = new Text("v" + io.github.elderpath_crusade.utils.GameVersion.current(), FontType.SILKSCREEN, 0, 0, 0, Color.WHITE)
            .withFontSize(FontSize.CAPTION);
        addContent(versionText);

        // Initial layout
        layoutContents();
    }

    private void layoutContents() {
        int[] screen_center = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screen_width = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screen_height = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        // Background covers full screen
        background.setBounds(new Box(0, 0, screen_width, screen_height));

        layoutTitle(screen_center[0], screen_width, screen_height);

        // Top-right corner, clear of the title and the bottom-center navbar.
        howToPlayButton.getBounds().setX(screen_width - HOW_TO_PLAY_BUTTON_WIDTH - HOW_TO_PLAY_BUTTON_MARGIN);
        howToPlayButton.getBounds().setY(screen_height - HOW_TO_PLAY_BUTTON_HEIGHT - HOW_TO_PLAY_BUTTON_MARGIN);

        versionText.getBounds().setX(VERSION_MARGIN);
        versionText.getBounds().setY(VERSION_MARGIN);

        // Navbar alignment stays at bottom center with slightly larger scale
        // Use divisor of 2.7 instead of 3 to make navbar ~11% larger
        int navW = (int)(navbarSize[0] / 2.7f);
        int navH = (int)(navbarSize[1] / 2.7f);
        int navX = screen_center[0] - (navW / 2);
        int navY = screen_center[1] - 210; // ScreenCenterX=360 | 360-150 = 210
        navbar.setBounds(new Box(navX, navY, navW, navH));

    }

    private void layoutTitle(int centerX, int screenWidth, int screenHeight) {
        if (!titleBanner.isAvailable()) {
            MenuLayout.centerHeader(title, 120);
            return;
        }
        int scale = TitleBanner.scaleFor(screenWidth);
        int bannerW = TitleBanner.NATIVE_WIDTH * scale;
        int bannerH = TitleBanner.NATIVE_HEIGHT * scale;
        int bannerX = centerX - bannerW / 2;
        int bannerY = screenHeight - BANNER_TOP_MARGIN * scale / 2 - bannerH;
        titleBanner.setRect(bannerX, bannerY, bannerW, bannerH);

        // Size the lettering to the plaque: label width is linear in font size, so measure at a probe size and rescale.
        float probe = 20f;
        title.withFontSize(probe);
        float width = title.getLabel().getWidth();
        if (width > 0f) title.withFontSize(probe * (bannerW * TITLE_WIDTH_SHARE) / width);
        title.withOutline(ColorSettings.TITLE_OUTLINE.getColor(), TITLE_OUTLINE_PER_SCALE * scale);
        title.update();
        title.getBounds().setX(centerX - (int) (title.getLabel().getWidth() / 2));
        title.getBounds().setY(bannerY + (bannerH - (int) title.getLabel().getHeight()) / 2);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static MainMenuRoom get() {
        return new MainMenuRoom();
    }
}
