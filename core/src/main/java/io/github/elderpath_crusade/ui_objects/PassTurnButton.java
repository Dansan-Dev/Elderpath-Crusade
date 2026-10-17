package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.GameContext;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.game.TurnManager;
import io.github.elderpath_crusade.enums.GameMode;

/**
 * PassTurnButton specialized button: clickable during current player's turn (or waiting state in LOCAL_MATCH).
 * Extends Button to reuse rendering and click plumbing directly.
 * In LOCAL_MATCH mode, shows "Start Turn" when waiting between turns.
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

    /**
     * Update button text based on current game state.
     * Should be called when turn state changes.
     */
    public void updateButtonText() {
        String newText;
        if (GameContext.get().getGameModeManager().getCurrent() == GameMode.LOCAL_MATCH && GameContext.get().getTurnManager().isWaitingForNextPlayer()) {
            newText = "Start Turn";
        } else {
            newText = "Pass Turn";
        }
        setText(newText);
        // Update the internal Text object
        if (textObj != null) {
            textObj.setText(newText);
            textObj.update();
        }
    }

    /**
     * Override render to update button text every frame based on current game state.
     */
    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        // Update button text before rendering to ensure it's always current
        updateButtonText();
        super.renderUI(batch, isPaused);
    }

    // Gate clicks based on current turn or waiting state
    @Override
    public ClickableEffectData getClickableEffectData() {
        // Hotseat handoff ("Start Turn") is always clickable; otherwise only on the turn of a
        // side this screen controls — never during a bot's or the online opponent's turn.
        if (GameContext.get().getTurnManager().isWaitingForNextPlayer()) {
            return super.getClickableEffectData();
        }
        return GameContext.get().getLocalControl().actingSide() != null ? super.getClickableEffectData() : null;
    }

}
