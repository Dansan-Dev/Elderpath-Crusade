package io.github.forest_of_dreams.ui_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.forest_of_dreams.data_objects.Box;
import io.github.forest_of_dreams.enums.FontType;
import io.github.forest_of_dreams.interfaces.UIRenderable;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.managers.SettingsManager;
import io.github.forest_of_dreams.supers.LowestOrderTexture;
import io.github.forest_of_dreams.utils.FontSize;

/**
 * Minimal overlay that displays multi-selection hints/counters.
 * It is always registered as a UI element and only renders when a multi-selection is active.
 */
public class SelectionOverlay extends LowestOrderTexture implements UIRenderable {
    private final Text text;

    public SelectionOverlay() {
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        int padding = 16;
        // Initial placement near the top-center; will update each frame to center text width.
        this.text = new Text("", FontType.SILKSCREEN,
                screenW / 2, screenH - padding - 24,
                0,
                Color.WHITE)
            .withFontSize(FontSize.BODY_MEDIUM);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        if (isPaused) return; // no overlay while paused
        if (!InteractionManager.hasActiveSelection()) return;
        String msg = InteractionManager.getOverlayText();
        if (msg.isEmpty()) return;

        // Update text content if changed
        if (!msg.equals(text.getText())) {
            text.setText(msg);
            text.update();
        }

        // Center horizontally at top using current text width
        int screenW = SettingsManager.screenSize.getScreenWidth();
        int screenH = SettingsManager.screenSize.getScreenHeight();
        int padding = 16;
        int x = (screenW - text.getWidth()) / 2;
        int y = screenH - padding - text.getHeight();
        text.setBounds(new Box(x, y, text.getWidth(), text.getHeight()));

        text.render(batch, 0, false);
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused, int x, int y) {
        // This overlay ignores parent positioning; delegate to default renderUI
        renderUI(batch, isPaused);
    }
}
