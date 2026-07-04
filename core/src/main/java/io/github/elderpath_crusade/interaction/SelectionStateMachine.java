package io.github.elderpath_crusade.interaction;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableEffectType;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.interfaces.CustomBox;
import io.github.elderpath_crusade.interfaces.InteractionSource;
import io.github.elderpath_crusade.utils.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * Encapsulates the multi-target selection state machine previously inlined in InteractionManager.
 * States: IDLE → SELECTING → (optionally CONFIRMING) → triggers → IDLE.
 */
public class SelectionStateMachine {
    private SelectionState currentState = SelectionState.IDLE;
    private InteractionSource source;
    private final List<CustomBox> targets = new ArrayList<>();
    private InteractionSource pendingSource;

    public boolean startSelection(InteractionSource newSource) {
        if (newSource == null) return false;
        if (isActive()) {
            pendingSource = newSource;
            return true;
        }
        source = newSource;
        ClickableEffectData data = source.getClickableEffectData();
        if (data == null) {
            source = null;
            return false;
        }
        data.setConfirmed(false);
        targets.clear();
        currentState = SelectionState.SELECTING;
        return true;
    }

    public void addTarget(CustomBox box) {
        if (currentState == SelectionState.IDLE || source == null) return;
        ClickableEffectData data = source.getClickableEffectData();
        if (data == null) return;

        if (box == source) {
            cancel();
            return;
        }
        if (!isValidTarget(box, data)) {
            // If the clicked element has its own selection effect and its type doesn't match
            // the current selection's target type, cancel the current selection and start a new one.
            // This handles the case where a user accidentally activates piece movement selection
            // and then tries to click a card (or other source).
            ClickableTargetType targetType = data.getTargetType();
            boolean typeOk = (targetType == null) || targetType.matches(box);
            if (!typeOk && box instanceof InteractionSource newSource && box != source) {
                ClickableEffectData newData = newSource.getClickableEffectData();
                if (newData != null) {
                    reset();
                    beginFromClickable(newSource);
                    return;
                }
            }
            Logger.log("SelectionStateMachine", "Ignored: target=" + box.getClass().getSimpleName()
                    + " does not satisfy required type " + data.getTargetType());
            return;
        }
        if (targets.contains(box)) {
            deselectTarget(box);
            return;
        }
        if (data.getType() == ClickableEffectType.MULTI_CHOICE_LIMITED_INTERACTION
                && targets.size() >= data.getExtraTargets()) {
            Logger.log("SelectionStateMachine", "Ignored: selection limit reached (" + data.getExtraTargets() + ")");
            return;
        }

        targets.add(box);

        switch (data.getType()) {
            case IMMEDIATE -> Logger.error("SelectionStateMachine", "Shouldn't add extra target when immediate");
            case MULTI_INTERACTION -> {
                if (targets.size() == data.getExtraTargets()) trigger();
            }
            case MULTI_CHOICE_LIMITED_INTERACTION -> {
                if (targets.size() <= data.getExtraTargets() && data.isConfirmed()) trigger();
            }
            case MULTI_CHOICE_UNLIMITED_INTERACTION -> {
                if (data.isConfirmed()) trigger();
            }
        }
    }

    public void confirm() {
        if (!isActive() || source == null) return;
        ClickableEffectData data = source.getClickableEffectData();
        if (data == null) {
            reset();
            return;
        }
        data.setConfirmed(true);
        switch (data.getType()) {
            case MULTI_CHOICE_LIMITED_INTERACTION, MULTI_CHOICE_UNLIMITED_INTERACTION -> trigger();
            case MULTI_INTERACTION, IMMEDIATE -> { }
        }
    }

    public void cancel() {
        if (isActive()) reset();
    }

    public boolean isActive() {
        return currentState != SelectionState.IDLE;
    }

    public InteractionSource getSource() {
        return source;
    }

    public List<CustomBox> getTargets() {
        return new ArrayList<>(targets);
    }

    public int getSelectedCount() {
        // Matches old semantics: 0 = idle, 1 = source selected (no targets yet), 1+N = N targets added
        if (!isActive()) return 0;
        return 1 + targets.size();
    }

    public SelectionState getCurrentState() {
        return currentState;
    }

    /**
     * Handle an initial click on a source clickable (when idle).
     */
    public void beginFromClickable(InteractionSource clickable) {
        if (isActive()) return;
        source = clickable;
        ClickableEffectData data = source.getClickableEffectData();
        if (data == null) {
            source = null;
            return;
        }
        data.setConfirmed(false);
        if (data.getType() == ClickableEffectType.IMMEDIATE) {
            triggerImmediate();
        } else {
            targets.clear();
            currentState = SelectionState.SELECTING;
        }
    }

    private void triggerImmediate() {
        HashMap<Integer, CustomBox> entities = buildEntities();
        source.triggerClickEffect(entities);
        // If triggerClickEffect started a new selection (e.g., requestPick for ChooseX abilities),
        // don't reset — the new selection state is the one we want to keep.
        if (!isActive()) {
            reset();
        }
    }

    private void trigger() {
        HashMap<Integer, CustomBox> entities = buildEntities();
        source.triggerClickEffect(entities);
        reset();
    }

    private void reset() {
        ClickableEffectData data = (source != null) ? source.getClickableEffectData() : null;
        if (data != null) data.setConfirmed(false);

        source = null;
        targets.clear();
        currentState = SelectionState.IDLE;

        if (pendingSource != null) {
            InteractionSource queued = pendingSource;
            pendingSource = null;
            startSelection(queued);
        }
    }

    private void deselectTarget(CustomBox box) {
        if (box == null || targets.isEmpty()) return;
        targets.remove(box);
    }

    private boolean isValidTarget(CustomBox box, ClickableEffectData data) {
        if (box == null || data == null) return false;
        ClickableTargetType targetType = data.getTargetType();
        boolean typeOk = (targetType == null) || targetType.matches(box);
        if (!typeOk) return false;
        return source == null || source.isValidTargetForEffect(box, getSelectedCount());
    }

    private HashMap<Integer, CustomBox> buildEntities() {
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        if (source != null) {
            entities.put(0, source);
        } else {
            Logger.error("SelectionStateMachine", "source is null when compiling selected entities");
        }
        for (int i = 0; i < targets.size(); i++) {
            entities.put(i + 1, targets.get(i));
        }
        return entities;
    }
}
