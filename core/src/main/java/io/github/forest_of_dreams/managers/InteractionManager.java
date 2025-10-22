package io.github.forest_of_dreams.managers;

import com.badlogic.gdx.Gdx;
import io.github.forest_of_dreams.data_objects.ClickableEffectData;
import io.github.forest_of_dreams.enums.ClickableEffectType;
import io.github.forest_of_dreams.enums.ClickableTargetType;
import io.github.forest_of_dreams.enums.settings.InputFunction;
import io.github.forest_of_dreams.interfaces.*;
import io.github.forest_of_dreams.utils.Logger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class InteractionManager {
    @Getter
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

    public static void clearClickables() { clickables.clear(); }

    private static void addInitialInteraction(Clickable clickableEffect) {
        if (selectedCount != 0) return;
        currentEffect = clickableEffect;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        // Reset confirmation state at the start of a new interaction to avoid carryover between runs
        if (data != null) data.setConfirmed(false);
        if (data != null && data.getType().equals(ClickableEffectType.IMMEDIATE)) {
            triggerFullInteraction();
        } else {
            selectedCount++;
        }
    }

    private static void addExtraTarget(CustomBox box) {
        if (selectedCount == 0) return;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return; // Safety: no effect metadata
        // Validate target based on expected target type; ignore invalid clicks
        if (!isValidTarget(box, data)) {
            Logger.log("InteractionManager", "Ignored click: target does not match required type " + data.getTargetType());
            return;
        }
        // Prevent selecting the same target multiple times
        if (selected.containsValue(box)) {
            Logger.log("InteractionManager", "Ignored click: target already selected");
            return;
        }
        // Enforce cap for limited-choice interactions (up to N targets)
        if (data.getType() == ClickableEffectType.MULTI_CHOICE_LIMITED_INTERACTION && selected.size() >= data.getExtraTargets()) {
            Logger.log("InteractionManager", "Ignored click: selection limit reached (" + data.getExtraTargets() + ")");
            return;
        }
        // Accept the target
        selected.put(selectedCount, box);
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
    }

    private static void cleanInteraction() {
        // Reset confirmation state on the effect being cleaned up (if any)
        ClickableEffectData data = (currentEffect != null) ? currentEffect.getClickableEffectData() : null;
        if (data != null) data.setConfirmed(false);
        currentEffect = null;
        selected.clear();
        selectedCount = 0;
    }

    // Selection state helpers for confirmation/cancellation flows
    public static boolean hasActiveSelection() { return selectedCount > 0; }

    public static void cancelSelection() {
        if (hasActiveSelection()) {
            cleanInteraction();
        }
    }

    public static void confirmSelection() {
        if (!hasActiveSelection() || currentEffect == null) return;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) { cleanInteraction(); return; }
        data.setConfirmed(true);
        // For choice-based interactions, confirmation should immediately evaluate the interaction.
        switch (data.getType()) {
            case MULTI_CHOICE_LIMITED_INTERACTION, MULTI_CHOICE_UNLIMITED_INTERACTION -> triggerFullInteraction();
            case MULTI_INTERACTION, IMMEDIATE -> { /* No-op: these are auto-handled elsewhere */ }
        }
    }

    // --- Overlay helpers (read-only) ---
    public static ClickableEffectType getCurrentEffectType() {
        if (currentEffect == null) return null;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        return (data == null ? null : data.getType());
    }

    public static int getSelectedCount() { return selectedCount; }

    public static int getRequiredTargets() {
        if (currentEffect == null) return 0;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return 0;
        return data.getExtraTargets();
    }

    /**
     * Builds a user-facing hint for the selection overlay.
     * Note: selectedCount includes the initial click; selected targets = max(selectedCount - 1, 0).
     */
    public static String getOverlayText() {
        if (!hasActiveSelection() || currentEffect == null) return "";
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return "";
        int selectedTargets = Math.max(selectedCount - 1, 0);
        switch (data.getType()) {
            case MULTI_INTERACTION -> {
                int required = data.getExtraTargets();
                return "Select " + required + " target" + (required == 1 ? "" : "s") + " (" + selectedTargets + "/" + required + ") — Right-click to cancel, ESC to pause";
            }
            case MULTI_CHOICE_LIMITED_INTERACTION -> {
                int limit = data.getExtraTargets();
                return "Select up to " + limit + " target" + (limit == 1 ? "" : "s") + " (" + selectedTargets + ") — Enter to confirm, Right-click to cancel, ESC to pause";
            }
            case MULTI_CHOICE_UNLIMITED_INTERACTION -> {
                return "Select any number (" + selectedTargets + ") — Enter to confirm, Right-click to cancel, ESC to pause";
            }
            case IMMEDIATE -> {
                return "";
            }
        }
        return "";
    }

    private static boolean isValidTarget(CustomBox box, ClickableEffectData data) {
        if (box == null || data == null) return false;
        ClickableTargetType targetType = data.getTargetType();
        if (targetType == null || targetType == ClickableTargetType.NONE) return true;
        for (Class<?> allowed : targetType.getAllowedClasses()) {
            if (allowed.isInstance(box)) return true;
        }
        return false;
    }

    private static HashMap<Integer, CustomBox> getSelectedEntities() {
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        // Index 0 should always be the source of the interaction (the clickable that initiated it)
        if (currentEffect != null) {
            entities.put(0, currentEffect);
        } else {
            Logger.error("InteractionManager", "currentEffect is null when compiling selected entities");
        }
        // Subsequent indices are the selected target entities.
        // Note: selectedCount is advanced after each addition, so valid indices are 1..selected.size().
        for (int i = 1; i <= selected.size(); i++) {
            entities.put(i, selected.get(i));
        }
        return entities;
    }

    private static int getSelectedTargets() {
        return selectedCount;
    }
}
