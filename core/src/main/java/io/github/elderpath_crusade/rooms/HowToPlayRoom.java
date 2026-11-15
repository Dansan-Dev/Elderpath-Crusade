package io.github.elderpath_crusade.rooms;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.List;

/**
 * A static "How to Play" explainer (D1, FEAT-015) — the scoped-down version of the plan's
 * tutorial idea. The plan's original suggestion (a forced first battle with inline callouts
 * anchored to the hand/mana/board/ability bubbles) needs exact on-screen positioning this
 * sandbox has no GL context to verify (`elderpath-coding-standards.md`'s "Trusting Dead Code"
 * caution applies just as much to a callout overlay as to a claim of dead code), so this ships
 * the zero-positioning-risk half instead: a plain scrollable text explainer, reachable from the
 * main menu, covering the same five things a genuinely new player is confused by in the first
 * five minutes. Content is hardcoded here rather than routed through {@code InfoDataManager}'s
 * {@code info.yml} (gitignored, not present in this sandbox, and shaped for name/role credit
 * entries, not free paragraphs) — same reasoning {@code CollectionRoom} used to justify a
 * purpose-built room instead of forcing its own data into an existing one.
 *
 * <p>Scroll/clipping layout is the same proven pattern {@code TextInfoRoom}/{@code
 * CollectionRoom} already use, copied rather than reinvented.
 */
public class HowToPlayRoom extends Room {
    private static final String[] SECTIONS = {
            "Hand & Mana",
            "Each turn you draw cards into your hand. Playing a card costs mana (shown at the "
                    + "top of the screen) — a summon card puts a new piece on the board, a spell "
                    + "card has some other effect instead.",

            "Moving & Attacking",
            "Click one of your pieces to see where it can move or attack — the board highlights "
                    + "every legal target. Click a highlighted plot to move there, or a highlighted "
                    + "enemy to attack it.",

            "Abilities",
            "Some pieces have a special ability, shown as a small bubble above them while it's "
                    + "your turn. Click the bubble to use it. Hover over an enemy you could attack "
                    + "to preview the damage before you commit to it.",

            "Ending Your Turn",
            "Each piece only has a limited number of actions per turn. Click \"Pass Turn\" once "
                    + "you're done — if an action is rejected (out of range, already used, etc.) a "
                    + "short message explains why.",

            "Winning",
            "A match ends when one side can no longer fight back. In a roguelike run, winning a "
                    + "battle also offers a reward — a new card, a stat upgrade, or a relic.",
    };

    private final Text header;
    private final List<Text> blocks = new ArrayList<>();
    private final Button backButton;
    private final Button upButton;
    private final Button downButton;
    private int scrollOffset = 0;
    private static final int SCROLL_STEP = 200;

    private HowToPlayRoom() {
        super();

        header = new Text("How to Play", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        int maxWrapWidth = (int) (GameContext.get().getSettingsManager().screenSize.getScreenWidth() * 0.7f);
        for (int i = 0; i < SECTIONS.length; i += 2) {
            Text title = new Text(SECTIONS[i], FontType.SILKSCREEN, 0, 0, 0, ColorSettings.BUTTON_PRIMARY.getColor())
                    .withFontSize(FontSize.BODY_MEDIUM).withWrapWidth(maxWrapWidth);
            blocks.add(title);
            addContent(title);

            Text body = new Text(SECTIONS[i + 1], FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                    .withFontSize(FontSize.CAPTION).withWrapWidth(maxWrapWidth);
            blocks.add(body);
            addContent(body);
        }

        backButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "Back", FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, 100, 50, 0)
                .withOnClick((e) -> GameContext.get().getRoomManager().gotoRoom(MainMenuRoom::get),
                        ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        addContent(backButton);

        upButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "^", FontType.SILKSCREEN,
                        FontSize.TITLE_MEDIUM.getSize(), 0, 0, 50, 50, 0)
                .withOnClick((e) -> {
                    scrollOffset = Math.max(0, scrollOffset - SCROLL_STEP);
                    layoutContents();
                }, ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        addContent(upButton);

        downButton = Button
                .fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), "v", FontType.SILKSCREEN,
                        FontSize.TITLE_MEDIUM.getSize(), 0, 0, 50, 50, 0)
                .withOnClick((e) -> {
                    scrollOffset += SCROLL_STEP;
                    layoutContents();
                }, ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor());
        addContent(downButton);

        layoutContents();
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        int screenCenterX = screenCenter[0];
        int screenWidth = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenHeight = GameContext.get().getSettingsManager().screenSize.getScreenHeight();

        MenuLayout.centerHeader(header, 100);

        backButton.getBounds().setX(20);
        backButton.getBounds().setY(screenHeight - 70);

        upButton.getBounds().setX(screenWidth - 80);
        upButton.getBounds().setY(screenHeight - 150);

        downButton.getBounds().setX(screenWidth - 80);
        downButton.getBounds().setY(100);

        int totalHeight = 0;
        for (int i = 0; i < blocks.size(); i++) {
            totalHeight += blocks.get(i).getBounds().getHeight();
            totalHeight += (i % 2 == 0) ? 10 : 35; // tight gap after a title, wider after a body
        }

        int availableSpace = (screenHeight - 200) - 100;
        int maxScroll = Math.max(0, totalHeight - availableSpace);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));

        Color primary = ColorSettings.BUTTON_PRIMARY.getColor().cpy();
        Color dimmed = primary.cpy().mul(1, 1, 1, 0.3f);
        upButton.setDisabled(scrollOffset <= 0);
        upButton.setBackgroundColor(upButton.isDisabled() ? dimmed : primary);
        downButton.setDisabled(scrollOffset >= maxScroll);
        downButton.setBackgroundColor(downButton.isDisabled() ? dimmed : primary);

        int clipTop = screenHeight - 160;
        int clipBottom = 110;
        int currentY = screenHeight - 200 + scrollOffset;
        for (int i = 0; i < blocks.size(); i++) {
            Text block = blocks.get(i);
            block.getBounds().setX(screenCenterX - block.getBounds().getWidth() / 2);
            int compHeight = block.getBounds().getHeight();
            int compY = currentY - compHeight;
            block.getBounds().setY(compY);

            if (compY + compHeight > clipTop || compY < clipBottom) {
                block.getBounds().setX(-2000);
            }
            currentY -= compHeight + ((i % 2 == 0) ? 10 : 35);
        }
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static HowToPlayRoom get() {
        return new HowToPlayRoom();
    }
}
