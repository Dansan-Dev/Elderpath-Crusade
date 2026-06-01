package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.Box;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.interfaces.UIRenderable;
import io.github.elderpath_crusade.managers.SettingsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.supers.LowestOrderTexture;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.GraphicUtils;

/**
 * Turn HUD: displays the current player at center-left of the screen.
 */
public class TurnHud extends LowestOrderTexture implements UIRenderable {
    private final Text turnText;
    // Precomputed background colors for readability (semi-transparent)
    private static final Color P1_BG = ColorSettings.PLOT_PLAYER_1_ROW.getColor().cpy().mul(1f, 1f, 1f, 0.35f);
    private static final Color P2_BG = ColorSettings.PLOT_PLAYER_2_ROW.getColor().cpy().mul(1f, 1f, 1f, 0.35f);
    private static final int PAD_X = 8;
    private static final int PAD_Y = 4;

    public TurnHud() {
        int screenW = GameContext.get().getSettingsManager().screenSize.getScreenWidth();
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        turnText = new Text("", FontType.SILKSCREEN, 16, screenH / 2, 0, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        setBounds(new Box(0, 0, screenW, screenH));
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        int screenH = GameContext.get().getSettingsManager().screenSize.getScreenHeight();
        String turnStr = "Current: " + GameContext.get().getTurnManager().getCurrentPlayer();
        if (!turnStr.equals(turnText.getText())) { turnText.setText(turnStr); turnText.update(); }
        int padding = 16;
        int curX = padding;
        int curY = (screenH - turnText.getHeight()) / 2;
        // Background sized to text + padding (only render when not paused)
        if (!isPaused) {
            int bgX = curX - PAD_X;
            int bgY = curY - PAD_Y;
            int bgW = turnText.getWidth() + PAD_X * 2;
            int bgH = turnText.getHeight() + PAD_Y * 2;
            Color bg = (GameContext.get().getTurnManager().getCurrentPlayer() == PieceAlignment.P1) ? P1_BG : P2_BG;
            batch.draw(GraphicUtils.getPixelTexture(bg), bgX, bgY, bgW, bgH);
        }
        // Text on top
        turnText.setBounds(new Box(curX, curY, turnText.getWidth(), turnText.getHeight()));
        turnText.render(batch, 0, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }
}
