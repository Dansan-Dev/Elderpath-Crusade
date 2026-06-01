package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableEffectType;
import io.github.elderpath_crusade.enums.ClickableTargetType;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interfaces.*;
import io.github.elderpath_crusade.utils.Logger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class InteractionManager {
    @Getter
    private final List<Clickable> clickables = new ArrayList<>();
    private final io.github.elderpath_crusade.interaction.HitTestService hitTestService = new io.github.elderpath_crusade.interaction.HitTestService(clickables);
    private InteractionSource currentEffect;
    private final List<CustomBox> selected = new ArrayList<>();
    @Getter
    private int selectedCount = 0;
    private InteractionSource pendingProgrammaticSource = null;

    public InteractionManager() {}

    public boolean requestPick(
        ClickableEffectData data,
        TargetFilter filter,
        Consumer<HashMap<Integer, CustomBox>> onPicked
    ) {
        if (data == null || onPicked == null) return false;
        return startProgrammaticInteraction(
            new EphemeralSource(data, filter, onPicked)
        );
    }

    private static class EphemeralSource implements InteractionSource {
        private final ClickableEffectData effectData;
        private final Consumer<HashMap<Integer, CustomBox>> callback;
        private final TargetFilter tf;

        EphemeralSource(ClickableEffectData d, TargetFilter f, Consumer<HashMap<Integer, CustomBox>> cb) {
            this.effectData = d;
            this.tf = f;
            this.callback = cb;
        }

        @Override
        public ClickableEffectData getClickableEffectData() {
            return effectData;
        }

        @Override
        public void triggerClickEffect(HashMap<Integer, CustomBox> entities) {
            callback.accept(entities);
        }

        @Override
        public boolean isValidTargetForEffect(CustomBox box, int targetIndex) {
            if (tf == null) return true;
            return tf.isValidTargetForEffect(box, targetIndex);
        }

        @Override
        public List<Plot> getEligibleTargets(int targetIndex) {
            if (tf == null) return null;
            return tf.getEligibleTargets(targetIndex);
        }
    }

    public boolean startProgrammaticInteraction(InteractionSource source) {
        if (source == null) return false;
        if (hasActiveSelection()) {
            pendingProgrammaticSource = source;
            return true;
        }
        currentEffect = source;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) {
            currentEffect = null;
            return false;
        }
        data.setConfirmed(false);
        selected.clear();
        selectedCount = 1;
        return true;
    }

    public void processLeftClick(int mouseX, int mouseY, boolean paused) {
        if (paused && selectedCount != 0) cleanInteraction();

        Clickable hit = findHit(mouseX, mouseY, paused);

        if (hit != null) {
            if (selectedCount == 0) {
                addInitialInteraction(hit);
            } else {
                addExtraTarget(hit);
            }
        }
    }

    private Clickable findHit(int mouseX, int mouseY, boolean paused) {
        return hitTestService.findHit(mouseX, mouseY, paused);
    }

    public void addClickable(Clickable clickable) {
        if (clickable instanceof Plot) return;
        clickables.add(clickable);
    }

    public void removeClickable(Clickable clickable) {
        clickables.remove(clickable);
    }

    public void clearClickables() {
        clickables.clear();
    }

    private void addInitialInteraction(Clickable clickableEffect) {
        if (selectedCount != 0) return;
        currentEffect = clickableEffect;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) {
            currentEffect = null;
            return;
        }
        data.setConfirmed(false);
        if (data.getType().equals(ClickableEffectType.IMMEDIATE)) {
            triggerFullInteraction();
        } else {
            selectedCount++;
        }
    }

    private void addExtraTarget(CustomBox box) {
        if (selectedCount == 0) return;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return;
        if (box == currentEffect) {
            cancelSelection();
            return;
        }
        if (!isValidTarget(box, data)) {
            Logger.log("InteractionManager", "Ignored click: target does not match required type " + data.getTargetType());
            return;
        }
        if (selected.contains(box)) {
            deselectTarget(box);
            return;
        }
        if (
            data.getType() == ClickableEffectType.MULTI_CHOICE_LIMITED_INTERACTION
            && selected.size() >= data.getExtraTargets()
        ) {
            Logger.log("InteractionManager", "Ignored click: selection limit reached (" + data.getExtraTargets() + ")");
            return;
        }
        selected.add(box);
        switch (data.getType()) {
            case IMMEDIATE -> Logger.error("InteractionManager", "Shouldn't add extra target when immediate");
            case MULTI_INTERACTION -> {
                if (selectedCount == data.getExtraTargets()) triggerFullInteraction();
                else selectedCount++;
            }
            case MULTI_CHOICE_LIMITED_INTERACTION -> {
                if (
                    selectedCount <= data.getExtraTargets()
                    && data.isConfirmed()
                ) {
                    triggerFullInteraction();
                }
                else selectedCount++;
            }
            case MULTI_CHOICE_UNLIMITED_INTERACTION -> {
                if (data.isConfirmed()) triggerFullInteraction();
                else selectedCount++;
            }
        }
    }

    private void triggerFullInteraction() {
        HashMap<Integer, CustomBox> entities = getSelectedEntities();
        currentEffect.triggerClickEffect(entities);
        cleanInteraction();
    }

    private void cleanInteraction() {
        ClickableEffectData data = (currentEffect != null) ? currentEffect.getClickableEffectData() : null;
        if (data != null) data.setConfirmed(false);

        currentEffect = null;
        selected.clear();
        selectedCount = 0;
        if (pendingProgrammaticSource != null) {
            InteractionSource queued = pendingProgrammaticSource;
            pendingProgrammaticSource = null;
            startProgrammaticInteraction(queued);
        }
    }

    private void deselectTarget(CustomBox box) {
        if (box == null || selected.isEmpty()) return;
        if (selected.remove(box)) {
            if (selectedCount > 1) selectedCount--;
        }
    }

    public CustomBox getActiveSource() {
        return currentEffect;
    }

    public List<CustomBox> getActiveTargets() {
        return new ArrayList<>(selected);
    }

    public boolean hasActiveSelection() {
        return selectedCount > 0;
    }

    public void cancelSelection() {
        if (hasActiveSelection()) {
            cleanInteraction();
        }
    }

    public void confirmSelection() {
        if (!hasActiveSelection() || currentEffect == null) return;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) {
            cleanInteraction();
            return;
        }
        data.setConfirmed(true);
        switch (data.getType()) {
            case MULTI_CHOICE_LIMITED_INTERACTION, MULTI_CHOICE_UNLIMITED_INTERACTION -> triggerFullInteraction();
            case MULTI_INTERACTION, IMMEDIATE -> { }
        }
    }

    public ClickableEffectType getCurrentEffectType() {
        if (currentEffect == null) return null;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        return (data == null ? null : data.getType());
    }

    public int getRequiredTargets() {
        if (currentEffect == null) return 0;
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return 0;
        return data.getExtraTargets();
    }

    public String getOverlayText() {
        if (!hasActiveSelection() || currentEffect == null) return "";
        ClickableEffectData data = currentEffect.getClickableEffectData();
        if (data == null) return "";
        int selectedTargets = Math.max(selectedCount - 1, 0);
        String instruction = data.getType().getInstructionText(data.getExtraTargets(), selectedTargets);
        if (instruction.isEmpty()) return "";
        return instruction + " — " + data.getType().getConfirmationHint();
    }

    private boolean isValidTarget(CustomBox box, ClickableEffectData data) {
        if (box == null || data == null) return false;
        ClickableTargetType targetType = data.getTargetType();

        boolean typeOk = (targetType == null)
            || targetType.matches(box);

        if (!typeOk) return false;

        return currentEffect == null || currentEffect.isValidTargetForEffect(box, selectedCount);
    }

    private HashMap<Integer, CustomBox> getSelectedEntities() {
        HashMap<Integer, CustomBox> entities = new HashMap<>();
        if (currentEffect != null) {
            entities.put(0, currentEffect);
        } else {
            Logger.error("InteractionManager", "currentEffect is null when compiling selected entities");
        }
        for (int i = 0; i < selected.size(); i++) {
            entities.put(i + 1, selected.get(i));
        }
        return entities;
    }
}
