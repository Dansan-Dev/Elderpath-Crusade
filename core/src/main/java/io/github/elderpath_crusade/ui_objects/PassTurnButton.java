package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.managers.GameModeManager;
import io.github.elderpath_crusade.managers.TurnManager;
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
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH && TurnManager.isWaitingForNextPlayer()) {
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
        // In LOCAL_MATCH mode, button is clickable if:
        // - Waiting for next player (shows "Start Turn")
        // - Current player's turn (shows "Pass Turn")
        if (GameModeManager.getCurrent() == GameMode.LOCAL_MATCH) {
            if (TurnManager.isWaitingForNextPlayer()) {
                return super.getClickableEffectData(); // Always clickable when waiting
            }
            // During active turn, only clickable for current player
            return (TurnManager.getCurrentPlayer() == PieceAlignment.P1 ||
                    TurnManager.getCurrentPlayer() == PieceAlignment.P2)
                    ? super.getClickableEffectData() : null;
        }
        // In other modes, only P1 can click (existing behavior)
        return (TurnManager.getCurrentPlayer() == PieceAlignment.P1) ? super.getClickableEffectData() : null;
    }

}
