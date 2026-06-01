package io.github.elderpath_crusade.managers;

import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.ClickableEffectType;
import io.github.elderpath_crusade.game_objects.board.Plot;
import io.github.elderpath_crusade.interaction.SelectionStateMachine;
import io.github.elderpath_crusade.interfaces.*;
import io.github.elderpath_crusade.utils.Logger;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

public class InteractionManager {
    @Getter
    private final List<Clickable> clickables = new ArrayList<>();
    private final io.github.elderpath_crusade.interaction.HitTestService hitTestService = new io.github.elderpath_crusade.interaction.HitTestService(clickables);
    private final SelectionStateMachine stateMachine = new SelectionStateMachine();

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
        return stateMachine.startSelection(source);
    }

    public void processLeftClick(int mouseX, int mouseY, boolean paused) {
        if (paused && stateMachine.isActive()) {
            stateMachine.cancel();
        }

        Clickable hit = findHit(mouseX, mouseY, paused);

        if (hit != null) {
            if (!stateMachine.isActive()) {
                stateMachine.beginFromClickable(hit);
            } else {
                stateMachine.addTarget(hit);
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

    public CustomBox getActiveSource() {
        return stateMachine.getSource();
    }

    public List<CustomBox> getActiveTargets() {
        return stateMachine.getTargets();
    }

    public boolean hasActiveSelection() {
        return stateMachine.isActive();
    }

    public int getSelectedCount() {
        return stateMachine.getSelectedCount();
    }

    public void cancelSelection() {
        stateMachine.cancel();
    }

    public void confirmSelection() {
        stateMachine.confirm();
    }

    public ClickableEffectType getCurrentEffectType() {
        InteractionSource source = stateMachine.getSource();
        if (source == null) return null;
        ClickableEffectData data = source.getClickableEffectData();
        return (data == null ? null : data.getType());
    }

    public int getRequiredTargets() {
        InteractionSource source = stateMachine.getSource();
        if (source == null) return 0;
        ClickableEffectData data = source.getClickableEffectData();
        if (data == null) return 0;
        return data.getExtraTargets();
    }

    public String getOverlayText() {
        if (!stateMachine.isActive() || stateMachine.getSource() == null) return "";
        ClickableEffectData data = stateMachine.getSource().getClickableEffectData();
        if (data == null) return "";
        int selectedTargets = Math.max(stateMachine.getSelectedCount() - 1, 0);
        String instruction = data.getType().getInstructionText(data.getExtraTargets(), selectedTargets);
        if (instruction.isEmpty()) return "";
        return instruction + " — " + data.getType().getConfirmationHint();
    }
}
