package io.github.elderpath_crusade.rooms;

import io.github.elderpath_crusade.GameContext;
import io.github.elderpath_crusade.data_objects.ClickableEffectData;
import io.github.elderpath_crusade.enums.FontType;
import io.github.elderpath_crusade.game.RunManager;
import io.github.elderpath_crusade.match.CardCatalog;
import io.github.elderpath_crusade.run.RunCardInstance;
import io.github.elderpath_crusade.supers.Room;
import io.github.elderpath_crusade.ui_objects.Button;
import io.github.elderpath_crusade.ui_objects.ButtonList;
import io.github.elderpath_crusade.ui_objects.Text;
import io.github.elderpath_crusade.utils.ColorSettings;
import io.github.elderpath_crusade.utils.FontSize;
import io.github.elderpath_crusade.utils.MenuLayout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One {@link EventRoom} roll: "pick 1 out of 3 cards from your deck twice and combine every
 * stat and ability of the 2 cards, transforming them into 1 chimera card" — the user's own
 * design (2026-11-12). First step: up to 3 random eligible (summon-only, like {@link
 * ElderPickRoom}/{@link CardModTargetRoom} — a spell has no {@code PieceDefinition} to combine
 * stats from, BUG-045's same rule) deck cards. Picking one continues to {@link
 * StrangeSurgeonCombineRoom} for the second pick (a fresh roll, excluding this one); {@link
 * RunManager#combineCards} does the actual merge, applied once both picks are in.
 */
public class StrangeSurgeonRoom extends Room {
    static final int SUBJECT_CHOICES = 3;

    private final Text header;
    private final Text flavor;
    private final CardPicker picker;

    private StrangeSurgeonRoom(List<RunCardInstance> candidates) {
        super();

        header = new Text("Strange Surgeon", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("A wandering surgeon offers to fuse two of your fighters into one. Choose the first subject.",
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        List<CardPicker.Option> options = new ArrayList<>();
        for (RunCardInstance instance : candidates) {
            String instanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            options.add(new CardPicker.Option(label, null, CardPicker.cardNameFor(instance), () -> GameContext.get().getRoomManager().gotoRoom(
                    () -> StrangeSurgeonCombineRoom.get(instanceId))));
        }
        picker = new CardPicker(this::addContent, options);

        layoutContents();
    }

    private void layoutContents() {
        MenuLayout.centerHeader(header, 100);
        MenuLayout.centerHeader(flavor, 150);
        picker.layout(200);
    }

    @Override
    public void onScreenResize() {
        layoutContents();
    }

    public static Room get() {
        RunManager runManager = GameContext.get().getRunManager();
        List<RunCardInstance> candidates = eligibleSubjects(runManager, null);
        if (candidates.size() < 2) return RunRoom.get(); // not enough to combine — EventRoom's own check normally prevents this
        return new StrangeSurgeonRoom(candidates.subList(0, Math.min(SUBJECT_CHOICES, candidates.size())));
    }

    /** Every summon-only deck instance, shuffled, excluding {@code excludedInstanceId} (nullable). */
    static List<RunCardInstance> eligibleSubjects(RunManager runManager, String excludedInstanceId) {
        List<RunCardInstance> eligible = new ArrayList<>();
        for (RunCardInstance instance : runManager.getRunState().deck()) {
            if (!CardCatalog.isSummon(instance.cardName())) continue; // BUG-045 — no stats to combine
            if (instance.instanceId().equals(excludedInstanceId)) continue;
            eligible.add(instance);
        }
        Collections.shuffle(eligible);
        return eligible;
    }
}
