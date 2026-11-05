package io.github.elderpath_crusade.ui_objects;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.client.ClientController;
import com.badlogic.gdx.graphics.Color;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;

/**
 * PassTurnButton specialized button: clickable during a turn this screen's acting seat may end.
 * Extends Button to reuse rendering and click plumbing directly. LocalMatchRoom's hotseat
 * handoff ("Start Turn" to reveal the next player's hand before actually ending a turn) sets
 * its own label via {@link #setText(String)} directly — this class has no handoff state itself.
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

    /** Sets the label directly — LocalMatchRoom's hotseat handoff calls this with "Start Turn". */
    public void setLabel(String text) {
        setText(text);
        if (textObj != null) {
            textObj.setText(text);
            textObj.update();
        }
    }

    // Clickable only while this screen's acting seat may end its turn, and not mid-pick.
    @Override
    public ClickableEffectData getClickableEffectData() {
        ClientController controller = GameContext.get().getClientController();
        if (controller == null || controller.hasPendingPick()) return null;
        return controller.canEndTurn() ? super.getClickableEffectData() : null;
    }

}
