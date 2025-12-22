package io.github.elderpath_crusade.managers;

import com.badlogic.gdx.Gdx;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableEffectType;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.enums.PieceAlignment;
import io.github.elderpath_crusade.enums.settings.InputFunction;
import io.github.elderpath_crusade.game_objects.board.Board;
import io.github.elderpath_crusade.game_objects.board.GamePiece;
import io.github.elderpath_crusade.game_objects.board.MonsterGamePiece;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.*;
import io.github.elderpath_crusade.managers.GraphicsManager;
import io.github.elderpath_crusade.managers.TurnManager;
import io.github.elderpath_crusade.ui_objects.AbilityBubble;
import io.github.elderpath_crusade.utils.Logger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class InteractionManager {
    @Getter
    private static final List<Clickable> clickables = new ArrayList<>();
    private static Clickable currentEffect;
    private static final HashMap<Integer, CustomBox> selected = new HashMap<>();
    @Getter
    private static int selectedCount = 0;
    private static Clickable pendingProgrammaticSource = null;

    public static boolean requestPick(
        ClickableEffectData data,
        TargetFilter filter,
        Consumer<HashMap<Integer, CustomBox>> onPicked
    ) {
        if (data == null || onPicked == null) return false;
        // Create a lightweight source implementing Clickable and TargetFilter so existing logic works.
        class EphemeralSource implements Clickable, TargetFilter {
            private final ClickableEffectData effectData;
            private final Consumer<HashMap<Integer, CustomBox>> callback;
            private final TargetFilter tf;

            EphemeralSource(ClickableEffectData d, TargetFilter f, java.util.function.Consumer<HashMap<Integer, CustomBox>> cb) {
                this.effectData = d; this.tf = f; this.callback = cb;
            }
            @Override public void setClickableEffect(OnClick onClick, ClickableEffectData effectData) { /* not used */ }
            @Override public ClickableEffectData getClickableEffectData() { return effectData; }
            @Override public void triggerClickEffect(HashMap<Integer, CustomBox> entities) { callback.accept(entities); }
            @Override public int getX() { return 0; }
            @Override public int getY() { return 0; }
            @Override public int getWidth() { return 0; }
            @Override public int getHeight() { return 0; }
            @Override public boolean isPauseUIElement() { return false; }
            @Override public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
                if (tf == null) return true; return tf.isValidTargetForEffect(box, targetIndex);
            }
            @Override public List<Plot> getEligibleTargets(int targetIndex) {
                if (tf == null) return null; return tf.getEligibleTargets(targetIndex);
            }
        }
        return startProgrammaticInteraction(
            new EphemeralSource(data, filter, onPicked)
        );
    }

    /**
     * Begin a selection programmatically using the provided source clickable.
     * Returns true if the interaction was started. Does nothing if a selection is already active
     * or if the source has no effect data.
     */
    public static boolean startProgrammaticInteraction(Clickable source) {
        if (source == null) return false;
        // If an interaction is already active, queue this source and auto-start it after cleanup.
        if (hasActiveSelection()) {
            pendingProgrammaticSource = source;
            return true; // queued
        }
        currentEffect = source;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) { currentEffect = null; return false; }
        data.setConfirmed(false);
        selected.clear();
        selectedCount = 1; // mirror initial click state
        return true;
    }

    public static void checkClick() {
        if (!InputManager.getFunctionActivation(InputFunction.LEFT_CLICK)) return;

        int mouseX = Gdx.input.getX();
        int mouseY = SettingsManager.screenSize.getScreenHeight() - Gdx.input.getY();
        boolean paused = GraphicsManager.isPaused();

        // If the game just became paused while interaction selection was in progress, clear it.
        if (paused && selectedCount != 0) cleanInteraction();

        // Two-pass click resolution: prioritize UI clickables (buttons, text) over world elements (plots)
        // Use a snapshot to avoid concurrent modification issues if cards are removed during iteration
        List<Clickable> clickablesSnapshot = new ArrayList<>(clickables);
        // Pass 1: UI clickables
        Clickable hit = null;
        for (Clickable clickable : clickablesSnapshot) {
            if (!(clickable instanceof UIRenderable)) continue;
            // When paused, only allow UI elements explicitly marked for pause; generic UI is blocked
            if (paused && !clickable.isPauseUIElement()) continue;
            // Skip if this clickable is no longer in the active list (removed during interaction)
            if (!clickables.contains(clickable)) continue;
            if (clickable.inRange(mouseX, mouseY)) { hit = clickable; break; }
        }
        // Pass 2: Non-UI clickables (board, plots, sprites) if no UI element was hit
        if (hit == null) {
            for (Clickable clickable : clickablesSnapshot) {
                if (clickable instanceof UIRenderable) continue;
                if (paused) continue; // never allow non-UI while paused
                // Skip if this clickable is no longer in the active list (removed during interaction)
                if (!clickables.contains(clickable)) continue;
                if (clickable.inRange(mouseX, mouseY)) { hit = clickable; break; }
            }
        }
        if (hit != null) {
            if (selectedCount == 0) {
                addInitialInteraction(hit);
            } else {
                addExtraTarget(hit);
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
        // If no effect is defined for this click, ignore it (do not start selection)
        if (data == null) { currentEffect = null; return; }
        // Reset confirmation state at the start of a new interaction to avoid carryover between runs
        data.setConfirmed(false);
        if (data.getType().equals(ClickableEffectType.IMMEDIATE)) {
            triggerFullInteraction();
        } else {
            selectedCount++;
        }
    }

    private static void addExtraTarget(CustomBox box) {
        if (selectedCount == 0) return;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return; // Safety: no effect metadata
        // General rule: re-clicking the source (initial clickable) cancels the interaction
        if (box == currentEffect) {
            cancelSelection();
            return;
        }
        // Validate target based on expected target type; ignore invalid clicks
        if (!isValidTarget(box, data)) {
            Logger.log("InteractionManager", "Ignored click: target does not match required type " + data.getTargetType());
            return;
        }
        // Prevent selecting the same target multiple times
        if (selected.containsValue(box)) {
            // Toggle behavior: clicking an already-selected target will deselect it
            deselectTarget(box);
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
        HashMap<Integer, CustomBox> entities = getSelectedEntities();
        currentEffect.triggerClickEffect(entities);
        cleanInteraction();
    }

    private static void cleanInteraction() {
        // Reset confirmation state on the effect being cleaned up (if any)
        ClickableEffectData data = (currentEffect != null) ? currentEffect.getClickableEffectData() : null;
        if (data != null) data.setConfirmed(false);

        // Note: currentEffect might have been removed from clickables during triggerClickEffect
        // (e.g., when a card is consumed and removed from hand), so we don't need to remove it here

        currentEffect = null;
        selected.clear();
        selectedCount = 0;
        // If a programmatic interaction was queued during the previous interaction (e.g., triggered by an ability
        // inside currentEffect.triggerClickEffect), start it now.
        if (pendingProgrammaticSource != null) {
            Clickable queued = pendingProgrammaticSource;
            pendingProgrammaticSource = null;
            startProgrammaticInteraction(queued);
        }
    }

    // Helper: deselect an already-selected target and compact indices
    private static void deselectTarget(CustomBox box) {
        if (box == null || selected.isEmpty()) return;
        // Find the index of this box
        int idx = -1;
        for (int i = 1; i <= selected.size(); i++) {
            if (selected.get(i) == box) { idx = i; break; }
        }
        if (idx == -1) return; // not found
        // Shift elements down to keep indices contiguous: idx..size-1 move one step down
        int size = selected.size();
        for (int i = idx; i < size; i++) {
            selected.put(i, selected.get(i + 1));
        }
        // Remove the last duplicate entry
        selected.remove(size);
        // Decrement selectedCount but never below 1 (which represents the source click)
        if (selectedCount > 1) selectedCount--;
        // Note: We intentionally do not auto-trigger here; user must reconfirm or reselect as needed.
    }

    // --- Active selection query API (read-only copies) ---
    /** Returns the initiating clickable (source) of the current interaction, or null if none. */
    public static CustomBox getActiveSource() { return currentEffect; }
    /** Returns an ordered copy of currently selected targets (indices 1..n). */
    public static List<CustomBox> getActiveTargets() {
        List<CustomBox> out = new ArrayList<>();
        for (int i = 1; i <= selected.size(); i++) {
            CustomBox b = selected.get(i);
            if (b != null) out.add(b);
        }
        return out;
    }
    /** Returns a copy of the entities map following the indexing contract (0=source, 1..n=targets). */
    public static HashMap<Integer, CustomBox> getActiveEntities() {
        return new HashMap<>(getSelectedEntities());
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

    /**
     * Unified target validation:
     * 1) Coarse type check via ClickableTargetType.matches (NONE or null → allow all types)
     * 2) Optional fine-grained rules via the source's TargetFilter (if implemented)
     *    - If currentEffect is an AbilityBubble, check the ability's TargetFilter
     *    - Otherwise, check if currentEffect itself implements TargetFilter
     */
    private static boolean isValidTarget(CustomBox box, ClickableEffectData data) {
        if (box == null || data == null) return false;
        ClickableTargetType targetType = data.getTargetType();
        boolean typeOk;
        // Special-case: allow clicking a GamePiece when the effect expects a PLOT; downstream will resolve to the plot.
        if (targetType == ClickableTargetType.PLOT && box instanceof GamePiece) {
            typeOk = true;
        } else {
            typeOk = (targetType == null) || targetType.matches(box);
        }
        if (!typeOk) return false;

        // Check TargetFilter: if currentEffect is AbilityBubble, use the ability's filter
        TargetFilter filter = null;
        if (currentEffect instanceof AbilityBubble bubble) {
            var ability = bubble.getAbility();
            if (ability instanceof TargetFilter tf) {
                filter = tf;
            }
        } else if (currentEffect instanceof TargetFilter tf) {
            filter = tf;
        }

        if (filter != null) {
            return filter.isValidTargetForEffect(box, selectedCount);
        }
        return true;
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

}
