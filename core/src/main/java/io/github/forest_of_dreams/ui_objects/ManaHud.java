package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.enums.PieceAlignment;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.PlayerManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.LowestOrderTexture;
import io.github.forest_of_dreams.utils.FontSize;

/**
 * Mana HUD: shows P1 and P2 mana only.
 * Layout requirement:
 * - P2 at top-left
 * - P1 at bottom-left
 */
public class ManaHud extends LowestOrderTexture implements UIRenderable {
    private final Text p1Text;
    private final Text p2Text;

    public ManaHud() {
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        // Initial positions; will be updated each render to remain anchored
        p1Text = new Text("", FontType.SILKSCREEN, 16, 16, 0, Color.WHITE)
            .withFontSize(FontSize.CAPTION);
        p2Text = new Text("", FontType.SILKSCREEN, 16, screenH - 16, 0, Color.WHITE)
            .withFontSize(FontSize.CAPTION);
        setBounds(new Box(0, 0, screenW, screenH));
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();

        PlayerManager.PlayerState p1 = PlayerManager.get(PieceAlignment.P1);
        PlayerManager.PlayerState p2 = PlayerManager.get(PieceAlignment.P2);

        String p1Str = "P1 Mana: " + (p1 == null ? 0 : p1.mana);
        String p2Str = "P2 Mana: " + (p2 == null ? 0 : p2.mana);

        if (!p1Str.equals(p1Text.getText())) { p1Text.setText(p1Str); p1Text.update(); }
        if (!p2Str.equals(p2Text.getText())) { p2Text.setText(p2Str); p2Text.update(); }

        int paddingX = 16; // moved further right from 16 to 48
        int paddingY = 50;
        // P2 top-left (shifted right)
        int p2x = paddingX;
        int p2y = screenH/2 + paddingY;
        p2Text.setBounds(new Box(p2x, p2y, p2Text.getWidth(), p2Text.getHeight()));
        // P1 bottom-left (shifted right)
        int p1x = paddingX;
        int p1y = screenH/2 - paddingY - p1Text.getHeight();
        p1Text.setBounds(new Box(p1x, p1y, p1Text.getWidth(), p1Text.getHeight()));

        // Render
        p2Text.render(batch, 0, isPaused);
        p1Text.render(batch, 0, isPaused);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        renderUI(batch, isPaused);
    }
}
