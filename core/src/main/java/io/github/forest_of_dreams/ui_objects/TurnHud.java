package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.managers.TurnManager;
import io.github.forest_of_dreams.supers.LowestOrderTexture;
import io.github.forest_of_dreams.utils.FontSize;

/**
 * Turn HUD: displays the current player at center-left of the screen.
 */
public class TurnHud extends LowestOrderTexture implements UIRenderable {
    private final Text turnText;

    public TurnHud() {
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        turnText = new Text("", FontType.SILKSCREEN, 16, screenH / 2, 0, Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
        setBounds(new Box(0, 0, screenW, screenH));
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        int screenH = SettingsManager.screenSize.getScreenHeight();
        String turnStr = "Current: " + TurnManager.getCurrentPlayer();
        if (!turnStr.equals(turnText.getText())) { turnText.setText(turnStr); turnText.update(); }
        int padding = 16;
        int curX = padding;
        int curY = (screenH - turnText.getHeight()) / 2;
        turnText.setBounds(new Box(curX, curY, turnText.getWidth(), turnText.getHeight()));
        turnText.render(batch, 0, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }
}
