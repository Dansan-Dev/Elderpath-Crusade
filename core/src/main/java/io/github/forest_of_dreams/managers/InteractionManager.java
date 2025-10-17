package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.ClickableEffectType;
import io.github.forest_of_dreams.enums.settings.InputFunction;
import io.github.forest_of_dreams.interfaces.*;
import io.github.forest_of_dreams.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InteractionManager {
    private static final List<Clickable> clickables = new ArrayList<>();
    private static Clickable currentEffect;
    private static final HashMap<Integer, CustomBox> selected = new HashMap<>();
    private static int selectedCount = 0;

    public static void checkClick() {
        if (!InputManager.getFunctionActivation(InputFunction.LEFT_CLICK)) return;

        int mouseX = Gdx.input.getX();
        int mouseY = SettingsManager.screenSize.getScreenHeight() - Gdx.input.getY();
        boolean paused = GraphicsManager.isPaused();

        // If the game just became paused while interaction selection was in progress, clear it.
        if (paused && selectedCount != 0) cleanInteraction();

        for (Clickable clickable : clickables) {
            // When paused, only allow UI elements to receive clicks
            if (paused && !clickable.isPauseUIElement()) continue;

            if (clickable.inRange(mouseX, mouseY)) {
                if (selectedCount == 0) {
                    addInitialInteraction(clickable);
                } else {
                    addExtraTarget(clickable);
                }
                break;
            }
        }
    }

    public static void addClickable(Clickable clickable) {
        clickables.add(clickable);
    }

    public static void removeClickable(Clickable clickable) {
        clickables.remove(clickable);
    }

    private static void addInitialInteraction(Clickable clickableEffect) {
        if (selectedCount != 0) return;
        currentEffect = clickableEffect;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data != null && data.getType().equals(ClickableEffectType.IMMEDIATE)) {
            triggerFullInteraction();
        } else {
            selectedCount++;
        }
    }

    private static void addExtraTarget(CustomBox box) {
        if (selectedCount == 0) return;
        selected.put(selectedCount, box);
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return; //TODO: Fix how interactions work, temp fix
        switch (data.getType()) {
            case IMMEDIATE -> Logger.error("InteractionManager", "Shouldn't add extra target when immediate");
            case MULTI_INTERACTION -> {
                if (selectedCount == data.getExtraTargets()) triggerFullInteraction();
                else selectedCount++;
            }
            case MULTI_CHOICE_LIMITED_INTERACTION -> {
                if (selectedCount <= data.getExtraTargets() && data.isConfirmed()) triggerFullInteraction();
                else selectedCount++;
            }
            case MULTI_CHOICE_UNLIMITED_INTERACTION -> {
                if (data.isConfirmed()) triggerFullInteraction();
                else selectedCount++;
            }
        }
    }

    private static void triggerFullInteraction() {
        ClickableEffectData data = currentEffect.getClickableEffectData();
        ClickableEffectType type = data.getType();
        HashMap<Integer, CustomBox> entities = getSelectedEntities();
        currentEffect.triggerClickEffect(entities);
        cleanInteraction();
//        switch (type) {
//            case IMMEDIATE -> {
//                currentEffect.triggerClickEffect(entities);
//                cleanInteraction();
//
//            } case MULTI_INTERACTION -> {
//                if (data.getExtraTargets() == getSelectedTargets()) {
//                    currentEffect.triggerClickEffect(entities);
//                    cleanInteraction();
//                }
//            } case MULTI_CHOICE_LIMITED_INTERACTION -> {
//                if (data.getExtraTargets() <= getSelectedTargets()) {
//                    currentEffect.triggerClickEffect(entities);
//                    cleanInteraction();
//                }
//            } case MULTI_CHOICE_UNLIMITED_INTERACTION -> {
//                currentEffect.triggerClickEffect(entities);
//                cleanInteraction();
//            }
//        }
    }

    private static void cleanInteraction() {
        currentEffect = null;
        selected.clear();
        selectedCount = 0;
    }

    private static HashMap<Integer, CustomBox> getSelectedEntities() {
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        for(int i = 1; i <= selectedCount; i++) {
            if (selected.containsKey(i))
                entities.put(i, selected.get(i));
            else Logger.error("InteractionManager", "missing entity in slot: " + i);
        }
        return entities;
    }

    private static int getSelectedTargets() {
        return selectedCount;
    }
}
