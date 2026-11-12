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
    private static final int BUTTON_WIDTH = 260;
    private static final int BUTTON_HEIGHT = 60;
    private static final int BUTTON_SPACING = 70;

    private final Text header;
    private final Text flavor;
    private final ButtonList buttonList;

    private StrangeSurgeonRoom(List<RunCardInstance> candidates) {
        super();

        header = new Text("Strange Surgeon", FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor())
                .withFontSize(FontSize.TITLE_MEDIUM);
        addContent(header);

        flavor = new Text("A wandering surgeon offers to fuse two of your fighters into one. Choose the first subject.",
                FontType.SILKSCREEN, 0, 0, 0, ColorSettings.TEXT_DEFAULT.getColor()).withFontSize(FontSize.BODY_MEDIUM);
        addContent(flavor);

        buttonList = new ButtonList();
        for (RunCardInstance instance : candidates) {
            String instanceId = instance.instanceId();
            String label = instance.cardName() + (instance.isModified() ? " (modified)" : "");
            addOption(label, () -> GameContext.get().getRoomManager().gotoRoom(
                    () -> StrangeSurgeonCombineRoom.get(instanceId)));
        }

        layoutContents();
    }

    private void addOption(String label, Runnable onClick) {
        Button button = Button.fromColor(ColorSettings.BUTTON_PRIMARY.getColor(), label, FontType.SILKSCREEN,
                        FontSize.BUTTON_DEFAULT.getSize(), 0, 0, BUTTON_WIDTH, BUTTON_HEIGHT, 0)
                .withOnClick((e) -> onClick.run(), ClickableEffectData.getImmediate())
                .withHoverColor(ColorSettings.BUTTON_HOVER.getColor())
                .withBorderColor(ColorSettings.BUTTON_BORDER.getColor())
                .withHoverBorderColor(ColorSettings.BUTTON_BORDER_HOVER.getColor());
        buttonList.addButton(button);
        addContent(button);
    }

    private void layoutContents() {
        int[] screenCenter = GameContext.get().getSettingsManager().screenSize.getScreenCenter();
        MenuLayout.centerHeader(header, 100);
        MenuLayout.centerHeader(flavor, 150);
        buttonList.alignButtonsAcrossYAxis(BUTTON_SPACING, screenCenter[0], screenCenter[1]);
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
