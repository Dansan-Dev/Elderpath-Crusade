package io.github.elderpath_crusade.utils;

import io.github.elderpath_crusade.interfaces.Clickable;
import io.github.elderpath_crusade.managers.InteractionManager;
import io.github.elderpath_crusade.supers.HigherOrderTexture;
import io.github.elderpath_crusade.supers.HigherOrderUI;

/**
 * Utility for traversing higher-order containers to register/unregister nested Clickables.
 * This consolidates logic that previously lived in GraphicsManager.
 */
public final class ClickableRegistryUtil {

    public static void sendClickables(HigherOrderTexture texture) {
        texture.getRenderables().forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.addClickable(clickable);
            } else if (r instanceof HigherOrderTexture higherOrderTexture) {
                sendClickables(higherOrderTexture);
            }
        });
    }

    public static void retractClickables(HigherOrderTexture texture) {
        texture.getRenderables().forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.removeClickable(clickable);
            } else if (r instanceof HigherOrderTexture higherOrderTexture) {
                retractClickables(higherOrderTexture);
            }
        });
    }

    public static void sendUIClickables(HigherOrderUI ui) {
        ui.getRenderableUIs().forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.addClickable(clickable);
            } else if (r instanceof HigherOrderUI higherOrderUI) {
                sendUIClickables(higherOrderUI);
            }
        });
    }

    public static void retractUIClickables(HigherOrderUI ui) {
        ui.getRenderableUIs().forEach(r -> {
            if (r instanceof Clickable clickable) {
                InteractionManager.removeClickable(clickable);
            } else if (r instanceof HigherOrderUI higherOrderUI) {
                retractUIClickables(higherOrderUI);
            }
        });
    }
}
