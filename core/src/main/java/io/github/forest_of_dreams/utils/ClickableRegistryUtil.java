package io.github.forest_of_dreams.utils;

import io.github.forest_of_dreams.interfaces.Clickable;
import io.github.forest_of_dreams.managers.InteractionManager;
import io.github.forest_of_dreams.supers.HigherOrderTexture;
import io.github.forest_of_dreams.supers.HigherOrderUI;

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
