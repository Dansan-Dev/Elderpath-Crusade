package io.github.elderpath_crusade.ui_objects;

import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import io.github.elderpath_crusade.supers.HigherOrderUI;

/**
 * Renders ability bubbles near pieces with actionable abilities.
 * Currently disabled — OOP abilities removed. Data-driven popup will be implemented later.
 */
public class AbilityPopup extends HigherOrderUI {

    public AbilityPopup() {
        super();
    }

    @Override
    public void renderUI(SpriteBatch batch, boolean isPaused) {
        // No-op: OOP ability bubbles removed. Data-driven popup coming in a future sprint.
    }
}
