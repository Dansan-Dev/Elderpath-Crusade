package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.managers.TurnManager;

/**
 * PassTurnButton specialized button: only clickable during P1 (human) turn.
 * Extends Button to reuse rendering and click plumbing directly.
 */
public class PassTurnButton extends Button {

    private PassTurnButton(Color backgroundColor,
                           String text,
                           FontType fontType,
                           int fontSize,
                           int x, int y,
                           int width, int height,
                           int z) {
        super(text, fontType, fontSize, x, y, width, height, z);
        // Configure background color via protected API on Button
        setBackgroundColor(backgroundColor);
    }

    // Factory: color background button
    public static PassTurnButton fromColor(
            Color backgroundColor,
            String text,
            FontType fontType,
            int fontSize,
            int x, int y,
            int width, int height,
            int z
    ) {
        return new PassTurnButton(backgroundColor, text, fontType, fontSize, x, y, width, height, z);
    }

    // Gate clicks based on current turn
    @Override
    public ClickableEffectData getClickableEffectData() {
        return (TurnManager.getCurrentPlayer() == PieceAlignment.P1) ? super.getClickableEffectData() : null;
    }

}
